package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.QueryManager", remap = false)
public abstract class ImmersivePortalsQueryManagerMixin {
    @Inject(method = "renderAndGetDoesAnySamplePass(Ljava/lang/Runnable;)Z", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$assumeVisibleWithoutGlBooleanQuery(Runnable renderingFunc,
                                                                     CallbackInfoReturnable<Boolean> cir) {
        renderingFunc.run();
        cir.setReturnValue(true);
    }

    @Inject(method = "renderAndGetSampleCountPassed(Ljava/lang/Runnable;)I", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$assumeVisibleWithoutGlSampleQuery(Runnable renderingFunc,
                                                                    CallbackInfoReturnable<Integer> cir) {
        renderingFunc.run();
        cir.setReturnValue(1);
    }
}
