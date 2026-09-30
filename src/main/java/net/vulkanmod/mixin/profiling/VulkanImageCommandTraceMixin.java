package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;

/** Captures texture upload copies and image-layout barriers without semantic bucketing. */
@Mixin(value = VulkanImage.class, priority = 850, remap = false)
public abstract class VulkanImageCommandTraceMixin {
    @Redirect(
            method = {"transferDstLayout", "readOnlyLayout(Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;)V"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V",
                    remap = false))
    private void vulkanmod$recordInstanceBarrier(VkCommandBuffer commandBuffer, int srcStageMask,
                                                  int dstStageMask, int dependencyFlags,
                                                  VkMemoryBarrier.Buffer memoryBarriers,
                                                  VkBufferMemoryBarrier.Buffer bufferBarriers,
                                                  VkImageMemoryBarrier.Buffer imageBarriers) {
        VulkanCommandTrace.pipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
        vkCmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
    }

    @Redirect(
            method = "transitionImageLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;JIIII)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V",
                    remap = false))
    private static void vulkanmod$recordStaticBarrier(VkCommandBuffer commandBuffer, int srcStageMask,
                                                       int dstStageMask, int dependencyFlags,
                                                       VkMemoryBarrier.Buffer memoryBarriers,
                                                       VkBufferMemoryBarrier.Buffer bufferBarriers,
                                                       VkImageMemoryBarrier.Buffer imageBarriers) {
        VulkanCommandTrace.pipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
        vkCmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
    }

    @Redirect(
            method = "copyBufferToImageCmd",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdCopyBufferToImage(Lorg/lwjgl/vulkan/VkCommandBuffer;JJILorg/lwjgl/vulkan/VkBufferImageCopy$Buffer;)V",
                    remap = false))
    private void vulkanmod$recordCopyBufferToImage(VkCommandBuffer commandBuffer, long srcBuffer,
                                                    long dstImage, int dstImageLayout,
                                                    VkBufferImageCopy.Buffer regions) {
        VulkanCommandTrace.copyBufferToImage(commandBuffer, srcBuffer, dstImage, dstImageLayout, regions);
        vkCmdCopyBufferToImage(commandBuffer, srcBuffer, dstImage, dstImageLayout, regions);
    }
}
