package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Captures the direct dynamic-rendering end path used by Framebuffer.endRenderPass. */
@Mixin(value = Framebuffer.class, priority = 850, remap = false)
public abstract class FramebufferCommandTraceMixin {
    @Redirect(
            method = "endRenderPass",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdEndRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;)V",
                    remap = false))
    private static void vulkanmod$recordEndRendering(VkCommandBuffer commandBuffer) {
        VulkanCommandTrace.endRendering(commandBuffer);
        KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
    }
}
