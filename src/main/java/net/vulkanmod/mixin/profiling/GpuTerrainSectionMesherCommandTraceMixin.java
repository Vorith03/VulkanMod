package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.chunk.voxel.GpuTerrainSectionMesher;
import net.vulkanmod.render.profiling.VulkanCommandTrace;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;
import static org.lwjgl.vulkan.VK10.vkCmdFillBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;

/** Captures every work-bearing command in the optional GPU terrain mesher. */
@Mixin(value = GpuTerrainSectionMesher.class, priority = 850, remap = false)
public abstract class GpuTerrainSectionMesherCommandTraceMixin {
    @Redirect(
            method = "submitDispatch",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdFillBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;JJJI)V",
                    remap = false))
    private void vulkanmod$recordFillBuffer(VkCommandBuffer commandBuffer, long buffer,
                                             long offset, long size, int data) {
        VulkanCommandTrace.fillBuffer(commandBuffer, buffer, offset, size, data);
        vkCmdFillBuffer(commandBuffer, buffer, offset, size, data);
    }

    @Redirect(
            method = "submitDispatch",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBindPipeline(Lorg/lwjgl/vulkan/VkCommandBuffer;IJ)V",
                    remap = false))
    private void vulkanmod$recordPipeline(VkCommandBuffer commandBuffer, int bindPoint, long pipeline) {
        VulkanCommandTrace.bindPipeline(commandBuffer, bindPoint, pipeline);
        vkCmdBindPipeline(commandBuffer, bindPoint, pipeline);
    }

    @Redirect(
            method = "submitDispatch",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdBindDescriptorSets(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILjava/nio/LongBuffer;Ljava/nio/IntBuffer;)V",
                    remap = false))
    private void vulkanmod$recordDescriptorSets(VkCommandBuffer commandBuffer, int bindPoint,
                                                 long layout, int firstSet, LongBuffer sets,
                                                 IntBuffer dynamicOffsets) {
        VulkanCommandTrace.bindDescriptorSets(commandBuffer, bindPoint, layout, firstSet, sets, dynamicOffsets);
        vkCmdBindDescriptorSets(commandBuffer, bindPoint, layout, firstSet, sets, dynamicOffsets);
    }

    @Redirect(
            method = "submitDispatch",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdPushConstants(Lorg/lwjgl/vulkan/VkCommandBuffer;JIILjava/nio/ByteBuffer;)V",
                    remap = false))
    private void vulkanmod$recordPushConstants(VkCommandBuffer commandBuffer, long layout,
                                                int stageFlags, int offset, ByteBuffer values) {
        VulkanCommandTrace.pushConstants(commandBuffer, layout, stageFlags, offset,
                values == null ? 0 : values.remaining());
        vkCmdPushConstants(commandBuffer, layout, stageFlags, offset, values);
    }

    @Redirect(
            method = "submitDispatch",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdDispatch(Lorg/lwjgl/vulkan/VkCommandBuffer;III)V",
                    remap = false))
    private void vulkanmod$recordDispatch(VkCommandBuffer commandBuffer, int x, int y, int z) {
        VulkanCommandTrace.dispatch(commandBuffer, x, y, z);
        vkCmdDispatch(commandBuffer, x, y, z);
    }

    @Redirect(
            method = {"barrierInputsToCompute", "barrierComputeWritesToConsumers", "barrierReadbackToHost"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V",
                    remap = false))
    private void vulkanmod$recordBarrier(VkCommandBuffer commandBuffer, int srcStageMask,
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
