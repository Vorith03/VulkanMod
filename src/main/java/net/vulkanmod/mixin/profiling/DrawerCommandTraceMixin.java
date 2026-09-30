package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.Drawer;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.*;

/** Captures the ordinary immediate draw vocabulary without creating per-draw JFR events. */
@Mixin(value = Drawer.class, priority = 850, remap = false)
public abstract class DrawerCommandTraceMixin {
    @Redirect(
            method = {"drawIndexed(Lnet/vulkanmod/vulkan/memory/VertexBuffer;Lnet/vulkanmod/vulkan/memory/IndexBuffer;II)V", "draw(Lnet/vulkanmod/vulkan/memory/VertexBuffer;I)V"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;nvkCmdBindVertexBuffers(Lorg/lwjgl/vulkan/VkCommandBuffer;IIJJ)V",
                    remap = false))
    private void vulkanmod$recordVertexBuffers(VkCommandBuffer commandBuffer, int firstBinding,
                                               int bindingCount, long buffers, long offsets) {
        VulkanCommandTrace.bindVertexBuffersNative(commandBuffer, firstBinding, bindingCount, buffers, offsets);
        nvkCmdBindVertexBuffers(commandBuffer, firstBinding, bindingCount, buffers, offsets);
    }

    @Redirect(
            method = {"drawIndexed(Lnet/vulkanmod/vulkan/memory/VertexBuffer;Lnet/vulkanmod/vulkan/memory/IndexBuffer;II)V", "bindAutoIndexBuffer"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBindIndexBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;JJI)V",
                    remap = false))
    private void vulkanmod$recordIndexBuffer(VkCommandBuffer commandBuffer, long buffer,
                                             long offset, int indexType) {
        VulkanCommandTrace.bindIndexBuffer(commandBuffer, buffer, offset, indexType);
        vkCmdBindIndexBuffer(commandBuffer, buffer, offset, indexType);
    }

    @Redirect(
            method = "drawIndexed(Lnet/vulkanmod/vulkan/memory/VertexBuffer;Lnet/vulkanmod/vulkan/memory/IndexBuffer;II)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdDrawIndexed(Lorg/lwjgl/vulkan/VkCommandBuffer;IIIII)V",
                    remap = false))
    private void vulkanmod$recordDrawIndexed(VkCommandBuffer commandBuffer, int indexCount,
                                             int instanceCount, int firstIndex,
                                             int vertexOffset, int firstInstance) {
        VulkanCommandTrace.drawIndexed(commandBuffer, indexCount, instanceCount,
                firstIndex, vertexOffset, firstInstance);
        vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    @Redirect(
            method = "draw(Lnet/vulkanmod/vulkan/memory/VertexBuffer;I)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdDraw(Lorg/lwjgl/vulkan/VkCommandBuffer;IIII)V",
                    remap = false))
    private void vulkanmod$recordDraw(VkCommandBuffer commandBuffer, int vertexCount,
                                      int instanceCount, int firstVertex, int firstInstance) {
        VulkanCommandTrace.draw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
        vkCmdDraw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
    }
}
