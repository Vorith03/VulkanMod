package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.chunk.DrawBuffers;
import net.vulkanmod.render.profiling.VulkanCommandTrace;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.nvkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexedIndirect;

/** Captures the complete fallback/direct terrain command state and draw work. */
@Mixin(value = DrawBuffers.class, priority = 850, remap = false)
public abstract class DrawBuffersCommandTraceMixin {
    @Redirect(
            method = {"buildDrawBatchesIndirect", "buildDrawBatchesDirect"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBindIndexBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;JJI)V",
                    remap = false))
    private void vulkanmod$recordIndexBuffer(VkCommandBuffer commandBuffer, long buffer,
                                             long offset, int indexType) {
        VulkanCommandTrace.bindIndexBuffer(commandBuffer, buffer, offset, indexType);
        vkCmdBindIndexBuffer(commandBuffer, buffer, offset, indexType);
    }

    @Redirect(
            method = {"buildDrawBatchesIndirect", "buildDrawBatchesDirect"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBindVertexBuffers(Lorg/lwjgl/vulkan/VkCommandBuffer;ILjava/nio/LongBuffer;Ljava/nio/LongBuffer;)V",
                    remap = false))
    private void vulkanmod$recordVertexBuffers(VkCommandBuffer commandBuffer, int firstBinding,
                                               LongBuffer buffers, LongBuffer offsets) {
        VulkanCommandTrace.bindVertexBuffers(commandBuffer, firstBinding, buffers, offsets);
        vkCmdBindVertexBuffers(commandBuffer, firstBinding, buffers, offsets);
    }

    @Redirect(
            method = {"fakeIndirectCmd", "buildDrawBatchesDirect"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;nvkCmdPushConstants(Lorg/lwjgl/vulkan/VkCommandBuffer;JIIIJ)V",
                    remap = false))
    private static void vulkanmod$recordPushConstants(VkCommandBuffer commandBuffer, long layout,
                                                       int stageFlags, int offset, int size, long values) {
        VulkanCommandTrace.pushConstants(commandBuffer, layout, stageFlags, offset, size);
        nvkCmdPushConstants(commandBuffer, layout, stageFlags, offset, size, values);
    }

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
            method = "fakeIndirectCmd",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdDrawIndexed(Lorg/lwjgl/vulkan/VkCommandBuffer;IIIII)V",
                    remap = false))
    private static void vulkanmod$recordFakeIndirectDraw(VkCommandBuffer commandBuffer, int indexCount,
                                                         int instanceCount, int firstIndex,
                                                         int vertexOffset, int firstInstance) {
        VulkanCommandTrace.drawIndexed(commandBuffer, indexCount, instanceCount,
                firstIndex, vertexOffset, firstInstance);
        vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    @Redirect(
            method = "buildDrawBatchesDirect",
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
