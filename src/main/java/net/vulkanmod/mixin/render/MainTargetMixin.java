package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MainTarget.class)
public class MainTargetMixin extends RenderTarget {

    public MainTargetMixin(boolean useDepth) {
        super(useDepth);
    }

    /**
     * MainTarget renders into the rotating swapchain rather than owning a fixed
     * OpenGL framebuffer. Cancel vanilla allocation while deliberately leaving
     * the original method body in the transformed class so compatibility mixins
     * can still resolve their vanilla GL call sites. This is important for mods
     * such as Immersive Portals which wrap the framebuffer attachment calls even
     * though VulkanMod never executes those calls at runtime.
     */
    @Inject(method = "createFrameBuffer", at = @At("HEAD"), cancellable = true)
    private void vulkanmod$createFrameBuffer(int width, int height, CallbackInfo ci) {
        this.viewWidth = width;
        this.viewHeight = height;
        this.width = width;
        this.height = height;

        // Keep one stable synthetic GL name and remap it to the acquired
        // swapchain image whenever a sampler asks for it.
        if(this.colorTextureId <= 0)
            this.colorTextureId = GlTexture.genTextureId();

        ci.cancel();
    }

    /**
     * Resolve Minecraft's stable MainTarget texture name to the swapchain image
     * acquired for the current frame. EffectInstance sampler suppliers call this
     * method directly, so refreshing here is required in addition to bindRead().
     */
    @Override
    public int getColorTextureId() {
        SwapChain swapChain = Vulkan.getSwapChain();
        if(!swapChain.supportsColorSampling()) {
            throw new UnsupportedOperationException(
                    "This Vulkan surface does not support sampling the main swapchain target");
        }

        if(this.colorTextureId <= 0)
            this.colorTextureId = GlTexture.genTextureId();
        GlTexture.setVulkanImage(this.colorTextureId, swapChain.getColorAttachment());
        return this.colorTextureId;
    }

    /** Depth aux suppliers also need a stable name remapped after recreation. */
    @Override
    public int getDepthTextureId() {
        if(this.depthBufferId <= 0)
            this.depthBufferId = GlTexture.genTextureId();
        GlTexture.setVulkanImage(this.depthBufferId, Vulkan.getSwapChain().getDepthAttachment());
        return this.depthBufferId;
    }

    /** Make explicit MainTarget texture binds obey the same layout/pass rules. */
    @Override
    public void bindRead() {
        int textureId = this.getColorTextureId();
        RenderTargetManager.bindRead(Vulkan.getSwapChain(), textureId);
    }

    /** Resume the swapchain render pass without discarding the scene. */
    @Override
    public void bindWrite(boolean updateViewport) {
        RenderTargetManager.bindMain(updateViewport, this.viewWidth, this.viewHeight);
    }

    @Override
    public void unbindWrite() {
        RenderTargetManager.bindMain(false, this.viewWidth, this.viewHeight);
    }
}
