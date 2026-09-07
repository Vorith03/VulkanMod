package net.vulkanmod.vulkan.passes;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class DefaultMainPass implements MainPass {

    public static DefaultMainPass PASS = new DefaultMainPass();

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        SwapChain swapChain = Vulkan.getSwapChain();
        swapChain.colorAttachmentLayout(stack, commandBuffer, Renderer.getCurrentImage());
        // colorAttachmentLayout owns the external swapchain-image barrier. Keep
        // the VulkanImage wrapper in sync so a LOAD render pass does not record a
        // second transition from a stale/undefined layout.
        swapChain.getColorAttachment().setCurrentLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        Framebuffer framebuffer = swapChain;
        swapChain.beginRenderPass(commandBuffer, stack);
        Renderer.clearAttachments(0x4100, swapChain.getWidth(), swapChain.getHeight());
//            Framebuffer framebuffer = this.hdrFinalFramebuffer;
//        framebuffer.getColorAttachment().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
//
//        framebuffer.beginRenderPass(commandBuffer, renderPass, stack);

//        this.boundFramebuffer = framebuffer;
        Renderer.getInstance().setBoundFramebuffer(framebuffer);

        VkViewport.Buffer pViewport = framebuffer.viewport(stack);
        vkCmdSetViewport(commandBuffer, 0, pViewport);

        VkRect2D.Buffer pScissor = framebuffer.scissor(stack);
        vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {
//        Framebuffer.endRenderPass(commandBuffer);
//
//        try(MemoryStack stack = stackPush()) {
//            this.hdrFinalFramebuffer.getColorAttachment()
//                    .transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
//
//            SwapChain swapChain = Vulkan.getSwapChain();
//
////            getSwapChain().colorAttachmentLayout(stack, commandBuffer, currentFrame);
//
////            swapChain.beginDynamicRendering(commandBuffer, stack);
//            swapChain.beginRenderPass(commandBuffer, stack);
//
//            clearAttachments(0x100, swapChain.width, swapChain.height);
//            VRenderSystem.disableDepthTest();
//        }
//
////        VRenderSystem.disableDepthTest();
//        VRenderSystem.disableCull();
//        RenderSystem.disableBlend();
//
//        DrawUtil.drawFramebuffer(this.blitGammaShader, this.hdrFinalFramebuffer.getColorAttachment());

        SwapChain swapChain = Vulkan.getSwapChain();
        // A modded render path may leave an off-screen target bound. Presentation
        // must always finish from the swapchain, so restore it defensively.
        RenderTargetManager.bindMain(false, swapChain.getWidth(), swapChain.getHeight());
        Framebuffer.endRenderPass(commandBuffer);

        try(MemoryStack stack = MemoryStack.stackPush()) {
            swapChain.presentLayout(stack, commandBuffer, Renderer.getCurrentImage());
        }

        int result = vkEndCommandBuffer(commandBuffer);
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer:" + result);
        }
    }
}
