package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

/** Records transfer-queue buffer copies into the query-later Vulkan command stream. */
@Mixin(value = TransferQueue.class, priority = 850, remap = false)
public abstract class TransferQueueCommandTraceMixin {
    @Redirect(
            method = {"copyBufferCmd", "uploadBufferImmediate"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdCopyBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;JJLorg/lwjgl/vulkan/VkBufferCopy$Buffer;)V",
                    remap = false))
    private void vulkanmod$recordInstanceCopyBuffer(VkCommandBuffer commandBuffer, long srcBuffer,
                                                     long dstBuffer, VkBufferCopy.Buffer regions) {
        VulkanCommandTrace.copyBuffer(commandBuffer, srcBuffer, dstBuffer, regions);
        vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, regions);
    }

    @Redirect(
            method = "uploadBufferCmd",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdCopyBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;JJLorg/lwjgl/vulkan/VkBufferCopy$Buffer;)V",
                    remap = false))
    private static void vulkanmod$recordStaticCopyBuffer(VkCommandBuffer commandBuffer, long srcBuffer,
                                                          long dstBuffer, VkBufferCopy.Buffer regions) {
        VulkanCommandTrace.copyBuffer(commandBuffer, srcBuffer, dstBuffer, regions);
        vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, regions);
    }
}
