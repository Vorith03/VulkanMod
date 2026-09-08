package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

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
                resumePass(outputFramebuffer, outputPass, commandBuffer);
            }
        }

        GlTexture.bindTexture(textureId);
    }

    public static void unbindRead() {
        GlTexture.bindTexture(0);
    }

    /**
     * Attachment writes and their shader-read barriers must share the primary
     * frame command buffer. A helper submission would run BEFORE this frame's
     * writes, even on the same queue. End/resume the LOAD output pass once for
     * all effect inputs; descriptor updates then see already-readable images.
     */
    public static void prepareSampledImages(VulkanImage[] images) {
        Renderer renderer = Renderer.getInstance();
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        RenderPass outputPass = renderer.getBoundRenderPass();
        if(commandBuffer == null || outputPass == null)
            throw new IllegalStateException("Effect sampling requires an active output pass");
        Framebuffer output = outputPass.getFramebuffer();

        boolean transition = false;
        for(VulkanImage image : images) {
            if(image == output.getColorAttachment() || image == output.getDepthAttachment())
                throw new UnsupportedOperationException("Post effect cannot sample its own output attachment");
            transition |= image.getCurrentLayout() != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }
        if(!transition)
            return;

        renderer.endRenderPass();
        try(MemoryStack stack = stackPush()) {
            for(VulkanImage image : images)
                image.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
        resumePass(output, outputPass, commandBuffer);
    }

    /**
     * Copy one RenderTarget depth attachment to another without routing through
     * the incomplete OpenGL framebuffer emulation layer. Minecraft's depth-copy
     * targets are normally equal-sized; fail explicitly rather than silently
     * changing the GL blit contract if a mod requests scaled depth blitting.
     */
    public static void copyDepth(VulkanImage source, VulkanImage destination) {
        if(source == null || destination == null)
            throw new IllegalArgumentException("Depth copy requires source and destination depth attachments");
        if(source == destination)
            return;
        if(source.format != destination.format) {
            throw new IllegalArgumentException("Depth copy requires matching formats: "
                    + source.format + " != " + destination.format);
        }
        if(source.width != destination.width || source.height != destination.height) {
            throw new UnsupportedOperationException("Scaled RenderTarget depth copies are not supported: source="
                    + source.width + "x" + source.height + " destination="
                    + destination.width + "x" + destination.height);
        }

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if(commandBuffer == null || Renderer.skipRendering)
            return;

        Renderer renderer = Renderer.getInstance();
        RenderPass interruptedPass = renderer.getBoundRenderPass();
        Framebuffer interruptedFramebuffer = interruptedPass != null
                ? interruptedPass.getFramebuffer() : null;

        // Image copies and their layout barriers must be recorded outside a
        // traditional render pass. Preserve the exact LOAD pass and resume it
        // after the transfer so copyDepthFrom behaves like an inline GL blit.
        if(interruptedPass != null)
            renderer.endRenderPass();

        int sourceLayout = source.getCurrentLayout();
        int destinationLayout = destination.getCurrentLayout();

        try(MemoryStack stack = stackPush()) {
            transitionDepth(source, commandBuffer, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, stack);
            transitionDepth(destination, commandBuffer, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, stack);

            VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
            region.srcSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.srcOffset().set(0, 0, 0);
            region.dstSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.dstOffset().set(0, 0, 0);
            region.extent(VkExtent3D.calloc(stack).set(source.width, source.height, 1));

            vkCmdCopyImage(commandBuffer,
                    source.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    destination.getId(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region);

            // UNDEFINED cannot be restored after a write. A newly-created target
            // should become a normal depth attachment after receiving the copy.
            int sourceRestore = sourceLayout == VK_IMAGE_LAYOUT_UNDEFINED
                    ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL : sourceLayout;
            int destinationRestore = destinationLayout == VK_IMAGE_LAYOUT_UNDEFINED
                    ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL : destinationLayout;

            transitionDepth(source, commandBuffer, sourceRestore, stack);
            transitionDepth(destination, commandBuffer, destinationRestore, stack);
        }

        if(interruptedPass != null)
            resumePass(interruptedFramebuffer, interruptedPass, commandBuffer);
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

    private static void transitionDepth(VulkanImage image, VkCommandBuffer commandBuffer,
                                        int newLayout, MemoryStack stack) {
        int oldLayout = image.getCurrentLayout();
        if(oldLayout == newLayout)
            return;

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        barrier.oldLayout(oldLayout);
        barrier.newLayout(newLayout);
        barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        barrier.image(image.getId());
        barrier.subresourceRange()
                .aspectMask(VulkanImage.aspectMaskForFormat(image.format))
                .baseMipLevel(0)
                .levelCount(image.mipLevels)
                .baseArrayLayer(0)
                .layerCount(1);

        int sourceStage;
        switch(oldLayout) {
            case VK_IMAGE_LAYOUT_UNDEFINED -> {
                barrier.srcAccessMask(0);
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            }
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> {
                barrier.srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            }
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL -> {
                barrier.srcAccessMask(VK_ACCESS_SHADER_READ_BIT);
                sourceStage = VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            }
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            }
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            }
            default -> throw new IllegalStateException("Unsupported depth source layout: " + oldLayout);
        }

        int destinationStage;
        switch(newLayout) {
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> {
                barrier.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            }
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL -> {
                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                destinationStage = VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            }
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> {
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            }
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            }
            default -> throw new IllegalStateException("Unsupported depth destination layout: " + newLayout);
        }

        vkCmdPipelineBarrier(commandBuffer,
                sourceStage, destinationStage, 0,
                null, null, barrier);
        image.setCurrentLayout(newLayout);
    }

    private static void resumePass(Framebuffer framebuffer, RenderPass renderPass,
                                   VkCommandBuffer commandBuffer) {
        if(framebuffer == null || renderPass == null)
            return;

        if(framebuffer instanceof SwapChain swapChain) {
            transitionColorToAttachment(swapChain.getColorAttachment(), commandBuffer);
            try(MemoryStack stack = stackPush()) {
                swapChain.beginRenderPass(commandBuffer, stack);
            }
        } else {
            try(MemoryStack stack = stackPush()) {
                framebuffer.beginRenderPass(commandBuffer, renderPass, stack);
            }
        }
    }
}
