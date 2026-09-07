package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class RenderPass {
    Framebuffer framebuffer;
    long id;

    final int attachmentCount;
    AttachmentInfo colorAttachmentInfo;
    AttachmentInfo depthAttachmentInfo;

    int finalColorLayout;
    int finalDepthLayout;

    public RenderPass(Framebuffer framebuffer, AttachmentInfo colorAttachmentInfo, AttachmentInfo depthAttachmentInfo) {
        this.framebuffer = framebuffer;
        this.colorAttachmentInfo = colorAttachmentInfo;
        this.depthAttachmentInfo = depthAttachmentInfo;

        int count = 0;
        if(colorAttachmentInfo != null)
            count++;
        if(depthAttachmentInfo != null)
            count++;

        this.attachmentCount = count;

        if(!Vulkan.DYNAMIC_RENDERING) {
            framebuffer.addRenderPass(this);

            createRenderPass();
        }

    }

    private void createRenderPass() {

        try(MemoryStack stack = MemoryStack.stackPush()) {

            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);
            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.calloc(attachmentCount, stack);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);

            int i = 0;

            // Color attachment
            if(colorAttachmentInfo != null) {
                VkAttachmentDescription colorAttachment = attachments.get(i);
                colorAttachment.format(colorAttachmentInfo.format);
                colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
                colorAttachment.loadOp(colorAttachmentInfo.loadOp);
                colorAttachment.storeOp(colorAttachmentInfo.storeOp);
                colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
                colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);

                colorAttachment.initialLayout(colorAttachmentInfo.initialLayout);
                colorAttachment.finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                VkAttachmentReference colorAttachmentRef = attachmentRefs.get(i);
                colorAttachmentRef.attachment(i);
                colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                subpass.colorAttachmentCount(1);
                subpass.pColorAttachments(VkAttachmentReference.calloc(1, stack).put(0, colorAttachmentRef));

                ++i;
            }


            // Depth-Stencil attachment
            if(depthAttachmentInfo != null) {
                VkAttachmentDescription depthAttachment = attachments.get(i);
                depthAttachment.format(depthAttachmentInfo.format);
                depthAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
                depthAttachment.loadOp(depthAttachmentInfo.loadOp);
                depthAttachment.storeOp(depthAttachmentInfo.storeOp);
                depthAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
                depthAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
                depthAttachment.initialLayout(depthAttachmentInfo.initialLayout);
                depthAttachment.finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

                // The depth attachment follows however many color attachments were
                // actually emitted. In a depth-only framebuffer that is slot 0,
                // while a color+depth framebuffer places it in slot 1.
                VkAttachmentReference depthAttachmentRef = attachmentRefs.get(i);
                depthAttachmentRef.attachment(i);
                depthAttachmentRef.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

                subpass.pDepthStencilAttachment(depthAttachmentRef);
            }

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType$Default();
            renderPassInfo.pAttachments(attachments);
            renderPassInfo.pSubpasses(subpass);

            LongBuffer pRenderPass = stack.mallocLong(1);

            if(vkCreateRenderPass(Vulkan.getDevice(), renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render pass");
            }

            id = pRenderPass.get(0);

            finalColorLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            finalDepthLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

        }
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, long framebufferId, MemoryStack stack) {

        if(colorAttachmentInfo != null && colorAttachmentInfo.initialLayout != VK_IMAGE_LAYOUT_UNDEFINED
                && colorAttachmentInfo.initialLayout != framebuffer.getColorAttachment().getCurrentLayout())
//            throw new RuntimeException("current layout does not match expected initial layout");
            framebuffer.getColorAttachment().transitionImageLayout(stack, commandBuffer, colorAttachmentInfo.initialLayout);
        if(depthAttachmentInfo != null && depthAttachmentInfo.initialLayout != VK_IMAGE_LAYOUT_UNDEFINED
                && depthAttachmentInfo.initialLayout != framebuffer.getDepthAttachment().getCurrentLayout())
//            throw new RuntimeException("current layout does not match expected initial layout");
            framebuffer.getDepthAttachment().transitionImageLayout(stack, commandBuffer, depthAttachmentInfo.initialLayout);

        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.callocStack(stack);
        renderPassInfo.sType$Default();
        renderPassInfo.renderPass(this.id);
        renderPassInfo.framebuffer(framebufferId);

        VkRect2D renderArea = VkRect2D.calloc(stack);
        renderArea.offset(VkOffset2D.calloc(stack).set(0, 0));
        renderArea.extent(VkExtent2D.calloc(stack).set(framebuffer.getWidth(), framebuffer.getHeight()));
        renderPassInfo.renderArea(renderArea);

        // VkRenderPassBeginInfo clear values are indexed by attachment number,
        // not by attachment type. Build them in the same order as createRenderPass
        // so color-only, depth-only and color+depth framebuffers all line up.
        VkClearValue.Buffer clearValues = VkClearValue.calloc(attachmentCount, stack);
        int clearIndex = 0;
        if(colorAttachmentInfo != null) {
            clearValues.get(clearIndex++).color().float32(VRenderSystem.clearColor);
        }
        if(depthAttachmentInfo != null) {
            clearValues.get(clearIndex).depthStencil().set(VRenderSystem.clearDepth, 0);
        }

        renderPassInfo.pClearValues(clearValues);

        vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        Renderer.getInstance().setBoundRenderPass(this);
    }

    public void endRenderPass(VkCommandBuffer commandBuffer) {
        vkCmdEndRenderPass(commandBuffer);

        if(colorAttachmentInfo != null)
            framebuffer.getColorAttachment().setCurrentLayout(finalColorLayout);

        if(depthAttachmentInfo != null)
            framebuffer.getDepthAttachment().setCurrentLayout(finalDepthLayout);

        Renderer.getInstance().setBoundRenderPass(null);
    }

    public void beginDynamicRendering(VkCommandBuffer commandBuffer, MemoryStack stack) {
        VkRect2D renderArea = VkRect2D.calloc(stack);
        renderArea.offset(VkOffset2D.calloc(stack).set(0, 0));
        renderArea.extent(VkExtent2D.calloc(stack).set(framebuffer.getWidth(), framebuffer.getHeight()));

        VkClearValue.Buffer clearValues;
        clearValues = VkClearValue.calloc(2, stack);
        clearValues.get(0).color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
        clearValues.get(1).depthStencil().set(1.0f, 0);

        VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack);
        renderingInfo.sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_INFO_KHR);
        renderingInfo.renderArea(renderArea);
        renderingInfo.layerCount(1);

        // Color attachment
        if(colorAttachmentInfo != null) {
            VkRenderingAttachmentInfo.Buffer colorAttachment = VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachment.sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR);
            colorAttachment.imageView(framebuffer.getColorAttachment().getImageView());
            colorAttachment.imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            colorAttachment.loadOp(colorAttachmentInfo.loadOp);
            colorAttachment.storeOp(colorAttachmentInfo.storeOp);
            colorAttachment.clearValue(clearValues.get(0));

            renderingInfo.pColorAttachments(colorAttachment);
        }

        //Depth attachment
        if(depthAttachmentInfo != null) {
            VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack);
            depthAttachment.sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR);
            depthAttachment.imageView(framebuffer.getDepthAttachment().getImageView());
            depthAttachment.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            depthAttachment.loadOp(depthAttachmentInfo.loadOp);
            depthAttachment.storeOp(depthAttachmentInfo.storeOp);
            depthAttachment.clearValue(clearValues.get(1));

            renderingInfo.pDepthAttachment(depthAttachment);
        }

        KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);
    }

    public void endDynamicRendering(VkCommandBuffer commandBuffer) {
        KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
    }

    public Framebuffer getFramebuffer() {
        return framebuffer;
    }

    public void cleanUp() {
        if(!Vulkan.DYNAMIC_RENDERING && this.id != VK_NULL_HANDLE) {
            // A RenderTarget may be closed after recording commands against this
            // pass but before that frame is submitted/retired. Destroying the pass
            // immediately invalidates those recorded commands, so retire it behind
            // the same frame fence as its framebuffer and attachment images.
            final long renderPassId = this.id;
            this.id = VK_NULL_HANDLE;
            MemoryManager.getInstance().addFrameOp(
                    () -> vkDestroyRenderPass(Vulkan.getDevice(), renderPassId, null)
            );
        }
    }

    public long getId() {
        return id;
    }

    public static class AttachmentInfo {
        final Type type;
        final int format;
        VulkanImage attachment;
        int initialLayout;
        int loadOp;
        int storeOp;

        public AttachmentInfo(Type type, int format) {
            this.type = type;
            this.format = format;
            this.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

            this.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            this.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        }

        public AttachmentInfo setOps(int loadOp, int storeOp) {
            this.loadOp = loadOp;
            this.storeOp = storeOp;

            return this;
        }

        public AttachmentInfo setLoadOp(int loadOp) {
            this.loadOp = loadOp;

            return this;
        }

        public AttachmentInfo setInitialLayout(int initialLayout) {
            this.initialLayout = initialLayout;

            return this;
        }

        public enum Type {
            COLOR,
            DEPTH
        }
    }

    public static class Builder {
        Framebuffer framebuffer;
        AttachmentInfo colorAttachmentInfo;
        AttachmentInfo depthAttachmentInfo;

        public Builder(Framebuffer framebuffer) {
            this.framebuffer = framebuffer;

            if(framebuffer.hasColorAttachment)
                colorAttachmentInfo = new AttachmentInfo(AttachmentInfo.Type.COLOR, framebuffer.format);
            if(framebuffer.hasDepthAttachment)
                depthAttachmentInfo = new AttachmentInfo(AttachmentInfo.Type.DEPTH, framebuffer.depthFormat);

            // The swapchain can be left and re-entered by Minecraft RenderTarget
            // operations in the middle of one command buffer. Preserve its existing
            // color/depth contents on every begin; DefaultMainPass still performs
            // the explicit frame-start clear immediately after the first begin.
            if(framebuffer instanceof SwapChain)
                this.setLoadOp(VK_ATTACHMENT_LOAD_OP_LOAD);
        }

        public RenderPass build() {
            return new RenderPass(framebuffer, colorAttachmentInfo, depthAttachmentInfo);
        }

        public Builder setLoadOp(int loadOp) {
            if(colorAttachmentInfo != null) {
                colorAttachmentInfo.setLoadOp(loadOp);
                colorAttachmentInfo.setInitialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            if(depthAttachmentInfo != null) {
                depthAttachmentInfo.setLoadOp(loadOp);
                depthAttachmentInfo.setInitialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }


            return this;
        }

        public AttachmentInfo getColorAttachmentInfo() {
            return colorAttachmentInfo;
        }

        public AttachmentInfo getDepthAttachmentInfo() {
            return depthAttachmentInfo;
        }
    }
}
