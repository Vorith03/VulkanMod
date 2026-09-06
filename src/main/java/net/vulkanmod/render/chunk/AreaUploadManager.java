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

        Device.getTransferQueue().submitCommands(commandBuffer, true);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer, true);

        // Graphics submission waits on the transfer semaphore, so these ranges are
        // safe to reference while recording this same frame's draw commands. The
        // command buffer itself is recycled only after that graphics frame retires.
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
            this.commandBuffers[currentFrame] = Device.getTransferQueue().beginCommands();
//            this.commandBuffers[currentFrame] = Device.getGraphicsQueue().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(this.currentFrame);
        stagingBuffer.copyBuffer((int) bufferSize, src);

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
            this.commandBuffers[currentFrame] = Device.getTransferQueue().beginCommands();

        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], src.getId(), 0, dst.getId(), 0, src.getBufferSize());
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

        // A synchronous flush may reach a copy-only or still-recording transfer
        // command buffer. Submit it fence-only so no binary semaphore is left
        // signaled without a corresponding graphics wait.
        if(!commandBuffer.isSubmitted()) {
            Device.getTransferQueue().submitCommands(commandBuffer);
        }
        Synchronization.waitFence(commandBuffer.getFence());

        markUploadsReady(frame);

        this.commandBuffers[frame].reset();
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
                "up(rdy/size/avg):%.1fms/%.0fKiB/%.1fms stg:%.1f/%.1fMiB r:%d",
                lastReadyMs, lastKiB, averageReadyMs,
                stagingHighWater / 1048576.0D, stagingCapacity / 1048576.0D, stagingResizes);
    }

}
