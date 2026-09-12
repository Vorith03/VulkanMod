package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.Locale;

import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization(ALLOCATION_SIZE);

    private final LongBuffer fences;
    private int idx = 0;

    private final ObjectArrayList<CommandPool.CommandBuffer> fenceCommandBuffers = new ObjectArrayList<>();

    private final LongArrayList semaphores = new LongArrayList();
    private final ObjectArrayList<CommandPool.CommandBuffer> semaphoreCommandBuffers = new ObjectArrayList<>();
    private final ObjectArrayList<CommandPool.CommandBuffer> sameQueueCommandBuffers = new ObjectArrayList<>();

    private long semaphoreRegistrations;
    private long sameQueueRegistrations;
    private long fenceRegistrations;
    private long fenceWaitCalls;
    private long fenceWaitedCount;
    private long fenceWaitNanos;
    private long mainFrameSubmissions;

    Synchronization(int allocSize) {
        this.fences = MemoryUtil.memAllocLong(allocSize);
    }

    public void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        addCommandBuffer(commandBuffer, false);
    }

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer, boolean useSemaphore) {
        // Shared texture-upload command buffers are still being recorded and have
        // not been submitted yet. Registering their fence/semaphore at this point
        // can make the renderer wait on synchronization that cannot be signaled.
        if(Device.getGraphicsQueue().isRecording(commandBuffer))
            return;

        // Some legacy callers register a command buffer after queue submission while newer
        // queue helpers already registered it. Never track the same submission twice.
        if(this.fenceCommandBuffers.contains(commandBuffer)
                || this.semaphoreCommandBuffers.contains(commandBuffer)
                || this.sameQueueCommandBuffers.contains(commandBuffer))
            return;

        if(useSemaphore) {
            this.semaphores.add(commandBuffer.getSemaphore());
            this.semaphoreCommandBuffers.add(commandBuffer);
            this.semaphoreRegistrations++;
        } else {
            this.addFence(commandBuffer.getFence());
            this.fenceCommandBuffers.add(commandBuffer);
        }
    }

    public synchronized void addSameQueueCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        // Same-queue helper submissions execute before the later main graphics
        // submission by queue order alone. They need no semaphore wait, but their
        // command buffers must stay alive until the main frame fence retires or an
        // explicit graphics-queue idle point proves they are already complete.
        if(Device.getGraphicsQueue().isRecording(commandBuffer))
            return;

        if(this.fenceCommandBuffers.contains(commandBuffer)
                || this.semaphoreCommandBuffers.contains(commandBuffer)
                || this.sameQueueCommandBuffers.contains(commandBuffer))
            return;

        this.sameQueueCommandBuffers.add(commandBuffer);
        this.sameQueueRegistrations++;
    }

    public synchronized void addFence(long fence) {
        if(idx == ALLOCATION_SIZE)
            waitFences();

        fences.put(idx, fence);
        idx++;
        this.fenceRegistrations++;
    }

    public synchronized void waitFences() {

        if(idx == 0) return;

        VkDevice device = Vulkan.getDevice();
        int waitCount = idx;
        long startNanos = System.nanoTime();

        fences.limit(waitCount);
        vkWaitForFences(device, fences, true, VUtil.UINT64_MAX);

        this.fenceWaitNanos += Math.max(0L, System.nanoTime() - startNanos);
        this.fenceWaitCalls++;
        this.fenceWaitedCount += waitCount;

        this.fenceCommandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.fenceCommandBuffers.clear();

        fences.limit(ALLOCATION_SIZE);
        idx = 0;
    }

    public synchronized void addWaitSemaphore(long semaphore) {
        this.semaphores.add(semaphore);
        this.semaphoreRegistrations++;
    }

    public synchronized int getWaitSemaphoreCount() {
        return this.semaphores.size();
    }

    public synchronized void getWaitSemaphores(LongBuffer buffer) {
        buffer.put(this.semaphores.elements(), 0, this.semaphores.size());
        this.semaphores.clear();
    }

    public synchronized void scheduleCbReset() {
        // Renderer invokes this only after the main graphics vkQueueSubmit has
        // succeeded. Expose that boundary to upload paths which need to distinguish
        // outstanding helper work from submissions already ordered behind a frame fence.
        this.mainFrameSubmissions++;

        if(this.semaphoreCommandBuffers.isEmpty() && this.sameQueueCommandBuffers.isEmpty()) return;

        final ObjectArrayList<CommandPool.CommandBuffer> frameCommandBuffers = new ObjectArrayList<>(
                this.semaphoreCommandBuffers.size() + this.sameQueueCommandBuffers.size());
        frameCommandBuffers.addAll(this.semaphoreCommandBuffers);
        frameCommandBuffers.addAll(this.sameQueueCommandBuffers);

        MemoryManager.getInstance().addFrameOp(
                () -> frameCommandBuffers.forEach(CommandPool.CommandBuffer::reset)
        );
        this.semaphoreCommandBuffers.clear();
        this.sameQueueCommandBuffers.clear();
    }

    public synchronized long getMainFrameSubmissionCount() {
        return this.mainFrameSubmissions;
    }

    /**
     * A caller that explicitly waited this helper's fence may recycle only that
     * command buffer immediately. Remove it from the normal main-frame retirement
     * list first so a later frame does not reset/enqueue the same command buffer twice.
     */
    public synchronized void retireSameQueueCommandBufferAfterFence(CommandPool.CommandBuffer commandBuffer) {
        if(!this.sameQueueCommandBuffers.remove(commandBuffer)) {
            throw new IllegalStateException("Fenced graphics helper was not registered for same-queue retirement");
        }
        commandBuffer.reset();
    }

    /**
     * The caller has already waited for the graphics queue to become idle, so
     * same-queue helper command buffers can be recycled immediately instead of
     * being retained until a later main-frame fence. Semaphore-backed transfer
     * submissions remain tracked separately and are not touched here.
     */
    public synchronized void retireSameQueueCommandBuffersAfterQueueIdle() {
        this.sameQueueCommandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.sameQueueCommandBuffers.clear();
    }

    public synchronized String getStats() {
        double waitMs = this.fenceWaitNanos / 1_000_000.0D;
        return String.format(Locale.ROOT, "sync sem:%d same:%d fence:%d waits:%d/%d %.1fms",
                this.semaphoreRegistrations, this.sameQueueRegistrations, this.fenceRegistrations,
                this.fenceWaitCalls, this.fenceWaitedCount, waitMs);
    }

    public static void waitFence(long fence) {
        VkDevice device = Vulkan.getDevice();

        vkWaitForFences(device, fence, true, VUtil.UINT64_MAX);
    }

    public static boolean checkFenceStatus(long fence) {
        VkDevice device = Vulkan.getDevice();
        return vkGetFenceStatus(device, fence) == VK_SUCCESS;
    }

}
