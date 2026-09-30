package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Minecraft;
import net.vulkanmod.render.profiling.AutomatedBenchmark;
import net.vulkanmod.render.profiling.FlightRecorderCapture;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defines the wall-clock frame boundary used by the opt-in performance logger. */
@Mixin(value = Minecraft.class, priority = 2000)
public class PerformanceProfilerMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void vulkanmod$beginPerformanceFrame(boolean tick, CallbackInfo ci) {
        AutomatedBenchmark.onFrameStart(Minecraft.getInstance());
        FlightRecorderCapture.beginFrame(tick);
        PerformanceProfiler.beginFrame();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void vulkanmod$endPerformanceFrame(boolean tick, CallbackInfo ci) {
        PerformanceProfiler.endFrame();
        FlightRecorderCapture.endFrame();
        AutomatedBenchmark.onFrameEnd(Minecraft.getInstance());
        if (!PerformanceProfiler.isEnabled()) {
            FlightRecorderCapture.stop("profiler_complete");
        }
    }
}
