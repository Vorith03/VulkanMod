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

    private long semaphoreRegistrations;
    private long fenceRegistrations;
    private long fenceWaitCalls;
    private long fenceWaitedCount;
    private long fenceWaitNanos;

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
        if(this.fenceCommandBuffers.contains(commandBuffer) || this.semaphoreCommandBuffers.contains(commandBuffer))
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
        if(this.semaphoreCommandBuffers.isEmpty()) return;

        final ObjectArrayList<CommandPool.CommandBuffer> frameCommandBuffers = this.semaphoreCommandBuffers.clone();
        MemoryManager.getInstance().addFrameOp(
                () -> frameCommandBuffers.forEach(CommandPool.CommandBuffer::reset)
        );
        this.semaphoreCommandBuffers.clear();
    }

    public synchronized String getStats() {
        double waitMs = this.fenceWaitNanos / 1_000_000.0D;
        return String.format(Locale.ROOT, "sync(s/f/w):%d/%d/%d(%.1fms)",
                this.semaphoreRegistrations, this.fenceRegistrations,
                this.fenceWaitedCount, waitMs);
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
