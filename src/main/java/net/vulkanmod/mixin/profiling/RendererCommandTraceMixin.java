package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.nvkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;

/** Captures shared graphics-pipeline state changes on the main command buffer. */
@Mixin(value = Renderer.class, priority = 850, remap = false)
public abstract class RendererCommandTraceMixin {
    @Redirect(
            method = "bindGraphicsPipeline",
            at = @At(value = "INVOKE",
                    target = "Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;getHandle(Lnet/vulkanmod/vulkan/shader/PipelineState;)J",
                    remap = false))
    private long vulkanmod$registerPipeline(GraphicsPipeline pipeline, PipelineState state) {
        long handle = pipeline.getHandle(state);
        VulkanCommandTrace.registerPipeline(handle, pipeline.name, pipeline.getLayout());
        return handle;
    }

    @Redirect(
            method = "bindGraphicsPipeline",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBindPipeline(Lorg/lwjgl/vulkan/VkCommandBuffer;IJ)V",
                    remap = false))
    private void vulkanmod$recordPipelineBind(VkCommandBuffer commandBuffer, int bindPoint, long pipeline) {
        VulkanCommandTrace.bindPipeline(commandBuffer, bindPoint, pipeline);
        vkCmdBindPipeline(commandBuffer, bindPoint, pipeline);
    }

    @Redirect(
            method = "pushConstants",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;nvkCmdPushConstants(Lorg/lwjgl/vulkan/VkCommandBuffer;JIIIJ)V",
                    remap = false))
    private void vulkanmod$recordPushConstants(VkCommandBuffer commandBuffer, long layout,
                                               int stageFlags, int offset, int size, long values) {
        VulkanCommandTrace.pushConstants(commandBuffer, layout, stageFlags, offset, size);
        nvkCmdPushConstants(commandBuffer, layout, stageFlags, offset, size, values);
    }
}
