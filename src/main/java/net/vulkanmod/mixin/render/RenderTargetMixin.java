package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.util.DrawUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

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

    private Framebuffer framebuffer;
    private RenderPass vulkanmod$renderPass;

    /**
     * @author
     * @reason Clear the Vulkan attachments backing this off-screen target while
     * preserving RenderTarget's bind/clear/unbind contract.
     */
    @Overwrite
    public void clear(boolean getError) {
        if(this.framebuffer == null || Renderer.getCommandBuffer() == null)
            return;

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
        this.vulkanmod$destroyBacking();

        this.viewWidth = width;
        this.viewHeight = height;
        this.width = width;
        this.height = height;

        if(width <= 0 || height <= 0)
            return;

        this.framebuffer = new Framebuffer.Builder(width, height, 1, this.useDepth)
                .setLinearFiltering(false)
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
