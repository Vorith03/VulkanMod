package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {
    long id;

    private final List<CommandBuffer> commandBuffers = new ObjectArrayList<>();
    private final java.util.Queue<CommandBuffer> availableCmdBuffers = new ArrayDeque<>();

    CommandPool(int queueFamilyIndex) {
        this.createCommandPool(queueFamilyIndex);
    }

    public void createCommandPool(int familyIndex) {

        try(MemoryStack stack = stackPush()) {

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(familyIndex);
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);

            if (vkCreateCommandPool(Vulkan.getDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            this.id = pCommandPool.get(0);
        }
    }

    public CommandBuffer beginCommands() {

        try(MemoryStack stack = stackPush()) {
            final int size = 10;

            if(availableCmdBuffers.size() == 0) {

                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
                allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
                allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
                allocInfo.commandPool(id);
                allocInfo.commandBufferCount(size);

                PointerBuffer pCommandBuffer = stack.mallocPointer(size);
                int result = vkAllocateCommandBuffers(Vulkan.getDevice(), allocInfo, pCommandBuffer);
                if(result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to allocate command buffers: " + result);
                }

                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
                fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
                fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

                VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack);
                semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

                for(int i = 0; i < size; ++i) {
                    LongBuffer pFence = stack.mallocLong(1);
                    if(vkCreateFence(Vulkan.getDevice(), fenceInfo, null, pFence) != VK_SUCCESS) {
                        throw new RuntimeException("Failed to create command buffer fence");
                    }

                    LongBuffer pSemaphore = stack.mallocLong(1);
                    if(vkCreateSemaphore(Vulkan.getDevice(), semaphoreInfo, null, pSemaphore) != VK_SUCCESS) {
                        vkDestroyFence(Vulkan.getDevice(), pFence.get(0), null);
                        throw new RuntimeException("Failed to create command buffer semaphore");
                    }

                    CommandBuffer commandBuffer = new CommandBuffer(
                            new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getDevice()),
                            pFence.get(0),
                            pSemaphore.get(0));
                    commandBuffers.add(commandBuffer);
                    availableCmdBuffers.add(commandBuffer);
                }

            }

            CommandBuffer commandBuffer = availableCmdBuffers.poll();

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            int result = vkBeginCommandBuffer(commandBuffer.handle, beginInfo);
            if(result != VK_SUCCESS) {
                availableCmdBuffers.add(commandBuffer);
                throw new RuntimeException("Failed to begin command buffer: " + result);
            }
            commandBuffer.recording = true;

            return commandBuffer;
        }
    }

    public synchronized long submitCommands(CommandBuffer commandBuffer, VkQueue queue) {
        return submitCommands(commandBuffer, queue, false);
    }

    public synchronized long submitCommands(CommandBuffer commandBuffer, VkQueue queue, boolean useSemaphore) {

        try(MemoryStack stack = stackPush()) {
            long fence = commandBuffer.fence;

            int result = vkEndCommandBuffer(commandBuffer.handle);
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to end command buffer: " + result);
            }

            result = vkResetFences(Vulkan.getDevice(), commandBuffer.fence);
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to reset command buffer fence: " + result);
            }

            VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer.handle));

            if(useSemaphore) {
                submitInfo.pSignalSemaphores(stack.longs(commandBuffer.semaphore));
            }

            result = vkQueueSubmit(queue, submitInfo, fence);
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit command buffer: " + result);
            }

            commandBuffer.recording = false;
            commandBuffer.submitted = true;
            return fence;
        }
    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
        for(CommandBuffer commandBuffer : commandBuffers) {
            vkDestroyFence(Vulkan.getDevice(), commandBuffer.fence, null);
            vkDestroySemaphore(Vulkan.getDevice(), commandBuffer.semaphore, null);
        }
        vkResetCommandPool(Vulkan.getDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getDevice(), id, null);
    }

    public class CommandBuffer {
        VkCommandBuffer handle;
        long fence;
        long semaphore;
        boolean submitted;
        boolean recording;

        public CommandBuffer(VkCommandBuffer handle, long fence, long semaphore) {
            this.handle = handle;
            this.fence = fence;
            this.semaphore = semaphore;
        }

        public VkCommandBuffer getHandle() {
            return handle;
        }

        public long getFence() {
            return fence;
        }

        public long getSemaphore() {
            return semaphore;
        }

        public boolean isSubmitted() {
            return submitted;
        }

        public boolean isRecording() {
            return recording;
        }

        public void reset() {
            this.submitted = false;
            this.recording = false;
            addToAvailable(this);
        }
    }
}
