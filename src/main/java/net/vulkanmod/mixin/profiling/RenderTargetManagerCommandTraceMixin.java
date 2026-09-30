package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;

/** Captures RenderTarget transfer/host and depth-layout synchronization commands. */
@Mixin(value = RenderTargetManager.class, priority = 850, remap = false)
public abstract class RenderTargetManagerCommandTraceMixin {
    @Redirect(
            method = {"copyColorToBuffer", "transitionDepth"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V",
                    remap = false))
    private static void vulkanmod$recordBarrier(VkCommandBuffer commandBuffer, int srcStageMask,
                                                 int dstStageMask, int dependencyFlags,
                                                 VkMemoryBarrier.Buffer memoryBarriers,
                                                 VkBufferMemoryBarrier.Buffer bufferBarriers,
                                                 VkImageMemoryBarrier.Buffer imageBarriers) {
        VulkanCommandTrace.pipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
        vkCmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
    }
}
