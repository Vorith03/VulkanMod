package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep Immersive Portals' clipping-plane bookkeeping and shader uniform updates
 * while suppressing the legacy fixed-function GL_CLIP_PLANE0 toggles. The
 * converted portal shaders consume the same clipping equation under Vulkan.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.FrontClipping", remap = false)
public abstract class ImmersivePortalsFrontClippingMixin {
    @Redirect(method = "enableClipping()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private static void vulkanmod$skipFixedClipEnable(int capability) {
    }

    @Redirect(method = "disableClipping()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private static void vulkanmod$skipFixedClipDisable(int capability) {
    }

    @Inject(method = "enableClipping()V", at = @At("RETURN"))
    private static void vulkanmod$syncTerrainClipPlane(CallbackInfo ci) {
        ImmersivePortalsShaderCompat.refreshTerrainClipPlane();
    }

    @Inject(method = "disableClipping()V", at = @At("RETURN"))
    private static void vulkanmod$clearTerrainClipPlane(CallbackInfo ci) {
        ImmersivePortalsShaderCompat.clearTerrainClipPlane();
    }
}
