package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flywheel 0.6 is an OpenGL renderer. Create still needs Flywheel's API classes,
 * but its backend must not probe or issue GL work once VulkanMod owns the window.
 * Keep Flywheel installed and force its renderer into the vanilla fallback path.
 */
@Pseudo
@Mixin(targets = "com.jozufozu.flywheel.backend.Backend", remap = false)
public abstract class FlywheelBackendMixin {

    @Inject(method = "refresh()V", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$skipBackendProbe(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "isOn()Z", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$forceBackendOff(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
