package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/**
 * Coordinates Minecraft RenderTarget switches on the single primary Vulkan
 * command buffer. RenderTarget's OpenGL contract allows framebuffer binds and
 * texture reads to happen independently; Vulkan requires render passes to end
 * before their attachments can change layout or be sampled.
 */
public final class RenderTargetManager {
    private RenderTargetManager() {}

    public static void bind(Framebuffer framebuffer, RenderPass renderPass,
                            boolean updateViewport, int viewWidth, int viewHeight) {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if(commandBuffer == null || Renderer.skipRendering)
            return;

        Renderer renderer = Renderer.getInstance();
        RenderPass currentPass = renderer.getBoundRenderPass();

        if(currentPass != renderPass) {
            if(currentPass != null) {
                Framebuffer previous = currentPass.getFramebuffer();
                renderer.endRenderPass();
                transitionColorToRead(previous, commandBuffer);
            }

            try(MemoryStack stack = stackPush()) {
                framebuffer.beginRenderPass(commandBuffer, renderPass, stack);
            }
        }

        if(updateViewport)
            Renderer.setViewport(0, 0, viewWidth, viewHeight);
    }

    public static void bindMain(boolean updateViewport, int viewWidth, int viewHeight) {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if(commandBuffer == null || Renderer.skipRendering)
            return;

        Renderer renderer = Renderer.getInstance();
        SwapChain swapChain = Vulkan.getSwapChain();
        RenderPass currentPass = renderer.getBoundRenderPass();

        if(currentPass == null || currentPass.getFramebuffer() != swapChain) {
            if(currentPass != null) {
                Framebuffer previous = currentPass.getFramebuffer();
                renderer.endRenderPass();
                transitionColorToRead(previous, commandBuffer);
            }

            transitionColorToAttachment(swapChain.getColorAttachment(), commandBuffer);

            try(MemoryStack stack = stackPush()) {
                swapChain.beginRenderPass(commandBuffer, stack);
            }
        }

        if(updateViewport)
            Renderer.setViewport(0, 0, viewWidth, viewHeight);
    }

    public static void bindRead(Framebuffer framebuffer, int textureId) {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        Renderer renderer = Renderer.getInstance();
        RenderPass currentPass = renderer.getBoundRenderPass();
        VulkanImage colorAttachment = framebuffer.getColorAttachment();

        if(commandBuffer != null && colorAttachment != null
                && colorAttachment.getCurrentLayout() != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            Framebuffer outputFramebuffer = null;
            RenderPass outputPass = null;

            if(currentPass != null) {
                outputFramebuffer = currentPass.getFramebuffer();
                outputPass = currentPass;
                renderer.endRenderPass();
            }

            transitionColorToRead(framebuffer, commandBuffer);

            // If reading this texture interrupted a different output pass, resume
            // that exact pass before the draw. Off-screen RenderTarget passes and
            // the swapchain pass are all LOAD-preserving, so this does not destroy
            // previously rendered contents.
            if(outputPass != null && outputFramebuffer != framebuffer) {
                if(outputFramebuffer instanceof SwapChain) {
                    transitionColorToAttachment(outputFramebuffer.getColorAttachment(), commandBuffer);
                    try(MemoryStack stack = stackPush()) {
                        ((SwapChain) outputFramebuffer).beginRenderPass(commandBuffer, stack);
                    }
                } else {
                    try(MemoryStack stack = stackPush()) {
                        outputFramebuffer.beginRenderPass(commandBuffer, outputPass, stack);
                    }
                }
            }
        }

        GlTexture.bindTexture(textureId);
    }

    public static void unbindRead() {
        GlTexture.bindTexture(0);
    }

    private static void transitionColorToRead(Framebuffer framebuffer, VkCommandBuffer commandBuffer) {
        if(framebuffer == null)
            return;

        VulkanImage colorAttachment = framebuffer.getColorAttachment();
        if(colorAttachment == null || colorAttachment.getCurrentLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            return;

        try(MemoryStack stack = stackPush()) {
            colorAttachment.transitionImageLayout(stack, commandBuffer,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
    }

    private static void transitionColorToAttachment(VulkanImage colorAttachment, VkCommandBuffer commandBuffer) {
        if(colorAttachment == null || colorAttachment.getCurrentLayout() == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            return;

        try(MemoryStack stack = stackPush()) {
            colorAttachment.transitionImageLayout(stack, commandBuffer,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }
    }
}
