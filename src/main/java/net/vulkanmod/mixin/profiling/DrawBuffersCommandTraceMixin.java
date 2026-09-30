package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.chunk.DrawBuffers;
import net.vulkanmod.render.profiling.VulkanCommandTrace;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexedIndirect;

/** Covers the fallback/direct terrain draw paths as well as the persistent path mixin. */
@Mixin(value = DrawBuffers.class, priority = 850, remap = false)
public abstract class DrawBuffersCommandTraceMixin {
    @Redirect(
            method = "buildDrawBatchesIndirect",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdDrawIndexedIndirect(Lorg/lwjgl/vulkan/VkCommandBuffer;JJII)V",
                    remap = false))
    private void vulkanmod$recordIndirectDraw(VkCommandBuffer commandBuffer, long buffer,
                                              long offset, int drawCount, int stride) {
        VulkanCommandTrace.drawIndexedIndirect(commandBuffer, buffer, offset, drawCount, stride);
        vkCmdDrawIndexedIndirect(commandBuffer, buffer, offset, drawCount, stride);
    }

    @Redirect(
            method = {"fakeIndirectCmd", "buildDrawBatchesDirect"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdDrawIndexed(Lorg/lwjgl/vulkan/VkCommandBuffer;IIIII)V",
                    remap = false))
    private void vulkanmod$recordDirectDraw(VkCommandBuffer commandBuffer, int indexCount,
                                            int instanceCount, int firstIndex,
                                            int vertexOffset, int firstInstance) {
        VulkanCommandTrace.drawIndexed(commandBuffer, indexCount, instanceCount,
                firstIndex, vertexOffset, firstInstance);
        vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
}
