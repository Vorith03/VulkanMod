package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;

/** Records legacy and dynamic rendering boundaries in the raw Vulkan command stream. */
@Mixin(value = RenderPass.class, priority = 850, remap = false)
public abstract class RenderPassCommandTraceMixin {
    @Redirect(
            method = "beginRenderPass",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBeginRenderPass(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderPassBeginInfo;I)V",
                    remap = false))
    private void vulkanmod$recordBeginRenderPass(VkCommandBuffer commandBuffer,
                                                  VkRenderPassBeginInfo beginInfo,
                                                  int contents) {
        VulkanCommandTrace.beginRenderPass(commandBuffer, beginInfo);
        vkCmdBeginRenderPass(commandBuffer, beginInfo, contents);
    }

    @Redirect(
            method = "endRenderPass",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdEndRenderPass(Lorg/lwjgl/vulkan/VkCommandBuffer;)V",
                    remap = false))
    private void vulkanmod$recordEndRenderPass(VkCommandBuffer commandBuffer) {
        VulkanCommandTrace.endRenderPass(commandBuffer);
        vkCmdEndRenderPass(commandBuffer);
    }

    @Redirect(
            method = "beginDynamicRendering",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdBeginRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V",
                    remap = false))
    private void vulkanmod$recordBeginRendering(VkCommandBuffer commandBuffer, VkRenderingInfo renderingInfo) {
        VulkanCommandTrace.beginRendering(commandBuffer, renderingInfo);
        KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);
    }

    @Redirect(
            method = "endDynamicRendering",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdEndRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;)V",
                    remap = false))
    private void vulkanmod$recordEndRendering(VkCommandBuffer commandBuffer) {
        VulkanCommandTrace.endRendering(commandBuffer);
        KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
    }
}
