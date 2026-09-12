package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.apache.commons.lang3.Validate;

import java.nio.ByteBuffer;
import java.util.Locale;

public class AreaUploadManager {
    public static AreaUploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new AreaUploadManager();
    }

    ObjectArrayList<AreaBuffer.Segment>[] recordedUploads;
    ObjectArrayList<DrawBuffers.ParametersUpdate>[] updatedParameters;
    ObjectArrayList<Runnable>[] frameOps;
    CommandPool.CommandBuffer[] commandBuffers;
    long[] firstUploadNanos;
    long[] recordedUploadBytes;

    long completedUploadBatches;
    long totalReadyNanos;
    long lastReadyNanos;
    long lastReadyBytes;
    long stagingCopyCount;
    long stagingCopyBytes;
    long stagingCopyNanos;

    int currentFrame;

    public void createLists(int frames) {
        this.commandBuffers = new CommandPool.CommandBuffer[frames];
        this.recordedUploads = new ObjectArrayList[frames];
        this.updatedParameters = new ObjectArrayList[frames];
        this.frameOps = new ObjectArrayList[frames];
        this.firstUploadNanos = new long[frames];
        this.recordedUploadBytes = new long[frames];

        this.completedUploadBatches = 0L;
        this.totalReadyNanos = 0L;
        this.lastReadyNanos = 0L;
        this.lastReadyBytes = 0L;
        this.stagingCopyCount = 0L;
        this.stagingCopyBytes = 0L;
        this.stagingCopyNanos = 0L;

        for (int i = 0; i < frames; i++) {
            this.recordedUploads[i] = new ObjectArrayList<>();
            this.updatedParameters[i] = new ObjectArrayList<>();
            this.frameOps[i] = new ObjectArrayList<>();
        }
    }

    public synchronized void submitUploads() {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());

        int frame = this.currentFrame;
        CommandPool.CommandBuffer commandBuffer = this.commandBuffers[frame];
        if(commandBuffer == null || commandBuffer.isSubmitted())
            return;

        // Terrain buffers are consumed by the graphics queue. Transfer-capable
        // buffers use concurrent family sharing when necessary, but that only
        // handles queue-family ownership; it does not order an older graphics read
        // against a transfer-queue overwrite of the same persistent slice. Submit
        // terrain copies on graphics instead so queue order is old draw -> write ->
        // new draw, with no cross-queue semaphore in the hot path.
        Device.getGraphicsQueue().submitCommands(commandBuffer);

        // This helper submission is ordered before the later main graphics submit.
        // The main frame fence therefore owns command-buffer/staging retirement.
        markUploadsReady(frame);
        this.commandBuffers[frame] = null;
    }

    public void uploadAsync(AreaBuffer.Segment uploadSegment, long bufferId, long dstOffset, long bufferSize, ByteBuffer src) {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());

        if(this.recordedUploads[this.currentFrame].isEmpty()) {
            this.firstUploadNanos[this.currentFrame] = System.nanoTime();
            this.recordedUploadBytes[this.currentFrame] = 0L;
        }
        this.recordedUploadBytes[this.currentFrame] += bufferSize;

        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = Device.getGraphicsQueue().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(this.currentFrame);
        long copyStart = System.nanoTime();
        stagingBuffer.copyBuffer((int) bufferSize, src);
        this.stagingCopyNanos += Math.max(0L, System.nanoTime() - copyStart);
        this.stagingCopyBytes += bufferSize;
        this.stagingCopyCount++;

        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);

        this.recordedUploads[this.currentFrame].add(uploadSegment);
    }

    public void enqueueParameterUpdate(DrawBuffers.ParametersUpdate parametersUpdate) {
        this.updatedParameters[this.currentFrame].add(parametersUpdate);
    }

    public void enqueueFrameOp(Runnable runnable) {
        this.frameOps[this.currentFrame].add(runnable);
    }

    public void copy(Buffer src, Buffer dst) {
        if(dst.getBufferSize() < src.getBufferSize()) {
            throw new IllegalArgumentException("dst buffer is smaller than src buffer.");
        }

        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = Device.getGraphicsQueue().beginCommands();

        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], src.getId(), 0, dst.getId(), 0, src.getBufferSize());
    }

    /**
     * Synchronous region-buffer growth copy. Keeping this on the graphics queue
     * orders it after all older terrain draws and earlier terrain helper uploads,
     * then the explicit fence wait makes it safe to retire the source allocation.
     */
    public void copyImmediate(Buffer src, Buffer dst) {
        if(dst.getBufferSize() < src.getBufferSize()) {
            throw new IllegalArgumentException("dst buffer is smaller than src buffer.");
        }

        CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
        TransferQueue.uploadBufferCmd(commandBuffer, src.getId(), 0, dst.getId(), 0, src.getBufferSize());
        Device.getGraphicsQueue().submitCommands(commandBuffer);
        Synchronization.waitFence(commandBuffer.getFence());
        Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);
    }

    public void updateFrame(int frame) {
        this.currentFrame = frame;
        waitUploads(this.currentFrame);
        executeFrameOps(frame);
    }

    private void executeFrameOps(int frame) {
        for(DrawBuffers.ParametersUpdate parametersUpdate : this.updatedParameters[frame]) {
            parametersUpdate.setDrawParameters();
        }

        for(Runnable runnable : this.frameOps[frame]) {
            runnable.run();
        }

        this.updatedParameters[frame].clear();
        this.frameOps[frame].clear();
    }

    private void markUploadsReady(int frame) {
        if(!this.recordedUploads[frame].isEmpty() && this.firstUploadNanos[frame] != 0L) {
            long readyNanos = Math.max(0L, System.nanoTime() - this.firstUploadNanos[frame]);
            this.lastReadyNanos = readyNanos;
            this.lastReadyBytes = this.recordedUploadBytes[frame];
            this.totalReadyNanos += readyNanos;
            this.completedUploadBatches++;
        }

        for(AreaBuffer.Segment uploadSegment : this.recordedUploads[frame]) {
            uploadSegment.setReady();
        }

        for(DrawBuffers.ParametersUpdate parametersUpdate : this.updatedParameters[frame]) {
            parametersUpdate.setDrawParameters();
        }

        this.recordedUploads[frame].clear();
        this.updatedParameters[frame].clear();
        this.firstUploadNanos[frame] = 0L;
        this.recordedUploadBytes[frame] = 0L;
    }

    private void waitUploads(int frame) {
        CommandPool.CommandBuffer commandBuffer = commandBuffers[frame];
        if(commandBuffer == null)
            return;

        // Synchronous callers (notably area-buffer growth) need all recorded
        // copies complete now. Submit on the graphics queue, wait this helper's
        // fence, then remove it from normal frame retirement before recycling it.
        if(!commandBuffer.isSubmitted()) {
            Device.getGraphicsQueue().submitCommands(commandBuffer);
        }
        Synchronization.waitFence(commandBuffer.getFence());

        markUploadsReady(frame);
        Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);
        this.commandBuffers[frame] = null;
    }

    public synchronized void waitAllUploads() {
        for(int i = 0; i < this.commandBuffers.length; ++i) {
            waitUploads(i);
        }
    }

    public String getStats() {
        double lastReadyMs = this.lastReadyNanos / 1_000_000.0D;
        double averageReadyMs = this.completedUploadBatches == 0L
                ? 0.0D
                : (this.totalReadyNanos / 1_000_000.0D) / this.completedUploadBatches;
        double lastKiB = this.lastReadyBytes / 1024.0D;
        double stagingCopyMiB = this.stagingCopyBytes / 1048576.0D;
        double stagingCopyMs = this.stagingCopyNanos / 1_000_000.0D;
        double averageStagingCopyMs = this.stagingCopyCount == 0L
                ? 0.0D
                : stagingCopyMs / this.stagingCopyCount;

        int stagingHighWater = 0;
        int stagingCapacity = 0;
        int stagingResizes = 0;
        for(int i = 0; i < Renderer.getFramesNum(); ++i) {
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(i);
            stagingHighWater = Math.max(stagingHighWater, stagingBuffer.getHighWaterMark());
            stagingCapacity = Math.max(stagingCapacity, stagingBuffer.getBufferSize());
            stagingResizes += stagingBuffer.getResizeCount();
        }

        return String.format(Locale.ROOT,
                "up(rdy/size/avg):%.1fms/%.0fKiB/%.1fms stageCpu:%d/%.1fMiB/%.1fms avg:%.3fms stg:%.1f/%.1fMiB r:%d %s",
                lastReadyMs, lastKiB, averageReadyMs,
                this.stagingCopyCount, stagingCopyMiB, stagingCopyMs, averageStagingCopyMs,
                stagingHighWater / 1048576.0D, stagingCapacity / 1048576.0D, stagingResizes,
                Synchronization.INSTANCE.getStats());
    }

}
