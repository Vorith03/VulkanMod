package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.util.DrawUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD;

@Mixin(RenderTarget.class)
public class RenderTargetMixin {

    @Shadow public int viewWidth;
    @Shadow public int viewHeight;
    @Shadow public int width;
    @Shadow public int height;
    @Shadow public int colorTextureId;
    @Shadow public int depthBufferId;
    @Shadow @Final protected boolean useDepth;
    @Shadow @Final private float[] clearChannels;
    @Shadow private int filterMode;

    private Framebuffer framebuffer;
    private RenderPass vulkanmod$renderPass;

    /**
     * @author
     * @reason Clear the Vulkan attachments backing this off-screen target while
     * preserving RenderTarget's bind/clear/unbind contract and per-target clear
     * color rather than whatever global clear color the previous pass left set.
     */
    @Overwrite
    public void clear(boolean getError) {
        if(this.framebuffer == null || Renderer.getCommandBuffer() == null)
            return;

        RenderSystem.clearColor(this.clearChannels[0], this.clearChannels[1],
                this.clearChannels[2], this.clearChannels[3]);
        if(this.useDepth)
            VRenderSystem.clearDepth = 1.0f;

        this.bindWrite(true);
        Renderer.clearAttachments(this.useDepth ? 0x4100 : 0x4000);
        this.unbindWrite();
    }

    /**
     * @author
     * @reason Allocate real sampled Vulkan color/depth images for generic
     * RenderTargets instead of leaving them unbacked.
     */
    @Overwrite
    public void resize(int width, int height, boolean getError) {
        // MainTarget is owned by the swapchain. Window resize already schedules
        // swapchain recreation, so do not allocate an unused generic framebuffer
        // merely because MainTarget inherits RenderTarget.resize.
        if((Object)this instanceof MainTarget) {
            this.viewWidth = width;
            this.viewHeight = height;
            this.width = width;
            this.height = height;
            return;
        }

        this.vulkanmod$destroyBacking();

        this.viewWidth = width;
        this.viewHeight = height;
        this.width = width;
        this.height = height;

        if(width <= 0 || height <= 0)
            return;

        this.framebuffer = new Framebuffer.Builder(width, height, 1, this.useDepth)
                .setLinearFiltering(this.filterMode == GL_LINEAR)
                .build();
        this.vulkanmod$renderPass = new RenderPass.Builder(this.framebuffer)
                .setLoadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                .build();

        if(this.colorTextureId <= 0)
            this.colorTextureId = GlTexture.genTextureId();
        GlTexture.setVulkanImage(this.colorTextureId, this.framebuffer.getColorAttachment());

        if(this.useDepth) {
            if(this.depthBufferId <= 0)
                this.depthBufferId = GlTexture.genTextureId();
            GlTexture.setVulkanImage(this.depthBufferId, this.framebuffer.getDepthAttachment());
        }
    }

    /**
     * @author
     * @reason RenderTarget filter changes normally become glTexParameter calls,
     * which VulkanMod intentionally does not emulate globally. Apply the supported
     * framebuffer NEAREST/LINEAR choice directly to the Vulkan color sampler.
     */
    @Overwrite
    public void setFilterMode(int filterMode) {
        if(filterMode != GL_NEAREST && filterMode != GL_LINEAR)
            throw new IllegalArgumentException("Unsupported RenderTarget filter mode: " + filterMode);

        this.filterMode = filterMode;
        if(this.framebuffer != null && this.framebuffer.getColorAttachment() != null) {
            this.framebuffer.getColorAttachment().updateTextureSampler(
                    filterMode == GL_LINEAR, true, false);
        }
    }

    /**
     * @author
     * @reason Release off-screen Vulkan backing and synthetic texture names.
     */
    @Overwrite
    public void destroyBuffers() {
        this.vulkanmod$destroyBacking();

        if(this.colorTextureId > 0) {
            GlTexture.glDeleteTextures(this.colorTextureId);
            this.colorTextureId = -1;
        }

        if(this.depthBufferId > 0) {
            GlTexture.glDeleteTextures(this.depthBufferId);
            this.depthBufferId = -1;
        }
    }

    /**
     * @author
     * @reason Implement Minecraft's framebuffer depth blit directly on the
     * Vulkan images. The legacy GL framebuffer shim does not emulate separate
     * READ_FRAMEBUFFER/DRAW_FRAMEBUFFER bindings used by vanilla copyDepthFrom.
     */
    @Overwrite
    public void copyDepthFrom(RenderTarget otherTarget) {
        VulkanImage sourceDepth = vulkanmod$getDepthAttachment(otherTarget);
        VulkanImage destinationDepth = vulkanmod$getDepthAttachment((RenderTarget)(Object)this);
        RenderTargetManager.copyDepth(sourceDepth, destinationDepth);
    }

    /**
     * @author
     * @reason Switch the primary command buffer to this target's Vulkan render
     * pass, preserving its existing color/depth contents across rebinds.
     */
    @Overwrite
    public void bindWrite(boolean updateViewport) {
        if(this.framebuffer == null || this.vulkanmod$renderPass == null)
            return;

        RenderTargetManager.bind(this.framebuffer, this.vulkanmod$renderPass,
                updateViewport, this.viewWidth, this.viewHeight);
    }

    /**
     * @author
     * @reason OpenGL unbinds to framebuffer 0; the Vulkan equivalent is to
     * resume the load-preserving swapchain pass.
     */
    @Overwrite
    public void unbindWrite() {
        RenderTargetManager.bindMain(false, this.viewWidth, this.viewHeight);
    }

    /**
     * @author
     * @reason Expose the off-screen color attachment through VulkanMod's
     * synthetic texture-name table after making the image shader-readable.
     */
    @Overwrite
    public void bindRead() {
        if(this.framebuffer != null && this.colorTextureId > 0)
            RenderTargetManager.bindRead(this.framebuffer, this.colorTextureId);
    }

    /**
     * @author
     */
    @Overwrite
    public void unbindRead() {
        RenderTargetManager.unbindRead();
    }

    /**
     * @author
     * @reason Match RenderTarget's screen-blit state contract while drawing the
     * sampled Vulkan color attachment through the Vulkan blit shader.
     */
    @Overwrite
    private void _blitToScreen(int width, int height, boolean disableBlend) {
        if(this.framebuffer == null)
            return;

        RenderSystem.assertOnRenderThread();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.viewport(0, 0, width, height);
        if(disableBlend)
            RenderSystem.disableBlend();

        DrawUtil.drawFramebuffer(this.framebuffer);

        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }

    private static VulkanImage vulkanmod$getDepthAttachment(RenderTarget target) {
        // VulkanMod renders MainTarget directly into the swapchain rather than
        // allocating the synthetic off-screen backing used by generic targets.
        if(target instanceof MainTarget)
            return Vulkan.getSwapChain().getDepthAttachment();

        int textureId = target.getDepthTextureId();
        return textureId > 0 ? GlTexture.getVulkanImage(textureId) : null;
    }

    private void vulkanmod$destroyBacking() {
        if(this.framebuffer != null) {
            this.framebuffer.cleanUp();
            this.framebuffer = null;
        }

        if(this.vulkanmod$renderPass != null) {
            this.vulkanmod$renderPass.cleanUp();
            this.vulkanmod$renderPass = null;
        }
    }
}
