package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.vulkan.VRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bridges the remaining raw GL calls in IP's framebuffer compatibility renderer. */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.RendererUsingFrameBuffer", remap = false)
public abstract class ImmersivePortalsFramebufferRendererMixin {
    @Redirect(
            method = {"prepareRendering()V", "doRenderPortal"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false)
    )
    private void vulkanmod$skipStencilDisable(int capability) {
        // The framebuffer compatibility renderer does not use stencil. VulkanMod
        // has no OpenGL context, so the redundant fixed-function disable is a no-op.
    }

    @Redirect(
            method = "doRenderPortal",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clearDepth(D)V", remap = false)
    )
    private void vulkanmod$setClearDepth(double depth) {
        VRenderSystem.clearDepth = (float) depth;
    }
}
