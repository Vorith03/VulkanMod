package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Immersive Portals' framebuffer compatibility renderer still performs two
 * fixed-function OpenGL stencil disables directly through LWJGL. VulkanMod's
 * game window is GLFW_NO_API, so either call aborts the JVM before Minecraft's
 * Vulkan state bridge can participate.
 *
 * The compatibility renderer already disables stencil ownership on its target
 * framebuffer. VulkanMod does not expose OpenGL stencil state, so these raw
 * GL_STENCIL_TEST toggles have no Vulkan-side work to perform.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.RendererUsingFrameBuffer", remap = false)
public abstract class ImmersivePortalsFramebufferRendererMixin {
    @Redirect(method = "prepareRendering()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private void vulkanmod$skipPrepareStencilDisable(int capability) {
    }

    @Redirect(method = "doRenderPortal",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private void vulkanmod$skipPortalStencilDisable(int capability) {
    }
}
