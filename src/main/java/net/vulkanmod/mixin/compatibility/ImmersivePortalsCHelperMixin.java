package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid fixed-function OpenGL state/error calls made by IP's compatibility renderer. */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.CHelper", remap = false)
public abstract class ImmersivePortalsCHelperMixin {
    @Inject(method = {"checkGlError()V", "doCheckGlError()V", "enableDepthClamp()V", "disableDepthClamp()V"},
            at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$skipUnsupportedGlState(CallbackInfo ci) {
        ci.cancel();
    }
}
