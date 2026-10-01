package net.vulkanmod.render.profiling;

import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderingInfo;

/**
 * Vulkan command-recording facade for code owned by VulkanMod.
 *
 * <p>Every command exposed here records its raw arguments in the query-later
 * {@link VulkanCommandTrace} before forwarding to LWJGL. Keeping the capture
 * boundary in ordinary Java code avoids runtime Mixin redirects against our own
 * renderer and gives CI one mechanically enforceable path for new vkCmd* calls.</p>
 *
 * <p>The trace methods are cheap no-ops when command tracing is disabled, so
 * normal gameplay still reaches the same Vulkan call with only the facade call
 * itself on the Java side.</p>
 */
public final class TracedVulkanCommands {
    private TracedVulkanCommands() {
    }

    public static void nvkCmdBindVertexBuffers(VkCommandBuffer commandBuffer, int firstBinding,
                                               int bindingCount, long pBuffers, long pOffsets) {
        VulkanCommandTrace.bindVertexBuffersNative(commandBuffer, firstBinding, bindingCount, pBuffers, pOffsets);
        org.lwjgl.vulkan.VK10.nvkCmdBindVertexBuffers(commandBuffer, firstBinding, bindingCount, pBuffers, pOffsets);
    }

    public static void vkCmdBindIndexBuffer(VkCommandBuffer commandBuffer, long buffer,
                                            long offset, int indexType) {
        VulkanCommandTrace.bindIndexBuffer(commandBuffer, buffer, offset, indexType);
        org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer(commandBuffer, buffer, offset, indexType);
    }

    public static void vkCmdDraw(VkCommandBuffer commandBuffer, int vertexCount,
                                 int instanceCount, int firstVertex, int firstInstance) {
        VulkanCommandTrace.draw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
        org.lwjgl.vulkan.VK10.vkCmdDraw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    public static void vkCmdDrawIndexed(VkCommandBuffer commandBuffer, int indexCount,
                                        int instanceCount, int firstIndex,
                                        int vertexOffset, int firstInstance) {
        VulkanCommandTrace.drawIndexed(commandBuffer, indexCount, instanceCount,
                firstIndex, vertexOffset, firstInstance);
        org.lwjgl.vulkan.VK10.vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount,
                firstIndex, vertexOffset, firstInstance);
    }

    public static void vkCmdCopyBuffer(VkCommandBuffer commandBuffer, long srcBuffer,
                                       long dstBuffer, VkBufferCopy.Buffer regions) {
        VulkanCommandTrace.copyBuffer(commandBuffer, srcBuffer, dstBuffer, regions);
        org.lwjgl.vulkan.VK10.vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, regions);
    }

    public static void vkCmdCopyBufferToImage(VkCommandBuffer commandBuffer, long srcBuffer,
                                              long dstImage, int dstImageLayout,
                                              VkBufferImageCopy.Buffer regions) {
        VulkanCommandTrace.copyBufferToImage(commandBuffer, srcBuffer, dstImage, dstImageLayout, regions);
        org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage(
                commandBuffer, srcBuffer, dstImage, dstImageLayout, regions);
    }

    public static void vkCmdPipelineBarrier(VkCommandBuffer commandBuffer,
                                            int srcStageMask, int dstStageMask,
                                            int dependencyFlags,
                                            VkMemoryBarrier.Buffer memoryBarriers,
                                            VkBufferMemoryBarrier.Buffer bufferBarriers,
                                            VkImageMemoryBarrier.Buffer imageBarriers) {
        VulkanCommandTrace.pipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
        org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask,
                dependencyFlags, memoryBarriers, bufferBarriers, imageBarriers);
    }

    public static void vkCmdBeginRenderPass(VkCommandBuffer commandBuffer,
                                            VkRenderPassBeginInfo beginInfo,
                                            int contents) {
        VulkanCommandTrace.beginRenderPass(commandBuffer, beginInfo);
        org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass(commandBuffer, beginInfo, contents);
    }

    public static void vkCmdEndRenderPass(VkCommandBuffer commandBuffer) {
        VulkanCommandTrace.endRenderPass(commandBuffer);
        org.lwjgl.vulkan.VK10.vkCmdEndRenderPass(commandBuffer);
    }

    public static void vkCmdBeginRenderingKHR(VkCommandBuffer commandBuffer,
                                              VkRenderingInfo renderingInfo) {
        VulkanCommandTrace.beginRendering(commandBuffer, renderingInfo);
        KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);
    }

    public static void vkCmdEndRenderingKHR(VkCommandBuffer commandBuffer) {
        VulkanCommandTrace.endRendering(commandBuffer);
        KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
    }
}
