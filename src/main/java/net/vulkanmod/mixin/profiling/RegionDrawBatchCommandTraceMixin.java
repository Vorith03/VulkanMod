package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexedIndirect;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;

/** Captures the persistent terrain batch commands used by the production path. */
@Mixin(targets = "net.vulkanmod.render.chunk.RegionDrawBatch", priority = 850, remap = false)
public abstract class RegionDrawBatchCommandTraceMixin {
    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBindVertexBuffers(Lorg/lwjgl/vulkan/VkCommandBuffer;ILjava/nio/LongBuffer;Ljava/nio/LongBuffer;)V",
                    remap = false))
    private void vulkanmod$recordVertexBuffers(VkCommandBuffer commandBuffer, int firstBinding,
                                               LongBuffer buffers, LongBuffer offsets) {
        VulkanCommandTrace.bindVertexBuffers(commandBuffer, firstBinding, buffers, offsets);
        vkCmdBindVertexBuffers(commandBuffer, firstBinding, buffers, offsets);
    }

    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdPushConstants(Lorg/lwjgl/vulkan/VkCommandBuffer;JIILjava/nio/FloatBuffer;)V",
                    remap = false))
    private void vulkanmod$recordPushConstants(VkCommandBuffer commandBuffer, long layout,
                                               int stageFlags, int offset, FloatBuffer values) {
        VulkanCommandTrace.pushConstants(commandBuffer, layout, stageFlags, offset,
                values == null ? 0 : values.remaining() * Float.BYTES);
        vkCmdPushConstants(commandBuffer, layout, stageFlags, offset, values);
    }

    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdDrawIndexedIndirect(Lorg/lwjgl/vulkan/VkCommandBuffer;JJII)V",
                    remap = false))
    private void vulkanmod$recordIndirectDraw(VkCommandBuffer commandBuffer, long buffer,
                                              long offset, int drawCount, int stride) {
        VulkanCommandTrace.drawIndexedIndirect(commandBuffer, buffer, offset, drawCount, stride);
        vkCmdDrawIndexedIndirect(commandBuffer, buffer, offset, drawCount, stride);
    }
}
