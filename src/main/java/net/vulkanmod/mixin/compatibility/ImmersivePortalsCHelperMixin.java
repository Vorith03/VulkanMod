package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.vulkan.shader.DepthClampState;
import org.lwjgl.opengl.GL32;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid raw OpenGL state/error calls while preserving the equivalent Vulkan state. */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.CHelper", remap = false)
public abstract class ImmersivePortalsCHelperMixin {
    @Inject(method = {"checkGlError()V", "doCheckGlError()V"},
            at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$skipUnsupportedGlErrorCheck(CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Preserve IP's own enableClippingMechanism guard while translating its
     * fixed-function GL_DEPTH_CLAMP request into Vulkan pipeline state.
     */
    @Redirect(method = "enableDepthClamp()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private static void vulkanmod$enableDepthClamp(int capability) {
        if(capability != GL32.GL_DEPTH_CLAMP) {
            throw new IllegalStateException("Unexpected Immersive Portals GL capability: " + capability);
        }
        DepthClampState.enable();
    }

    @Redirect(method = "disableDepthClamp()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private static void vulkanmod$disableDepthClamp(int capability) {
        if(capability != GL32.GL_DEPTH_CLAMP) {
            throw new IllegalStateException("Unexpected Immersive Portals GL capability: " + capability);
        }
        DepthClampState.disable();
    }
}
