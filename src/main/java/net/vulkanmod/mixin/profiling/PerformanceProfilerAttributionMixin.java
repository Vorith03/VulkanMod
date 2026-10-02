package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.PerformanceProfiler;
import net.vulkanmod.render.profiling.TextureTickAttribution;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PerformanceProfiler.class, remap = false)
public abstract class PerformanceProfilerAttributionMixin {
    @Inject(method = "finishAutomatedCapture", at = @At("HEAD"), remap = false)
    private static void vulkanmod$emitAttribution(String reason, CallbackInfoReturnable<Boolean> cir) {
        TextureTickAttribution.emitSummary();
        WorldRenderAttribution.emitSummary();
    }
}
