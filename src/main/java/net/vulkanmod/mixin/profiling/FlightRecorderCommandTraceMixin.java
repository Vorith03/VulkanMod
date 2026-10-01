package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.FlightRecorderCapture;
import net.vulkanmod.render.profiling.VulkanCommandTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Persists the command sidecar at the same explicit capture boundary as the JFR. */
@Mixin(value = FlightRecorderCapture.class, priority = 850, remap = false)
public abstract class FlightRecorderCommandTraceMixin {
    @Inject(method = "stop", at = @At("HEAD"))
    private static void vulkanmod$stopCommandTrace(String reason, CallbackInfoReturnable<Boolean> cir) {
        VulkanCommandTrace.stop(reason);
    }
}
