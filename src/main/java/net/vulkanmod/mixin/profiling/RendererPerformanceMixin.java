package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.PerformanceProfiler;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times frame-slot recycling separately from the rest of Minecraft.runTick(). */
@Mixin(value = Renderer.class, remap = false)
public class RendererPerformanceMixin {
    @Unique
    private long vulkanmod$frameSlotWaitStart;

    @Inject(method = "resetBuffers", at = @At("HEAD"), remap = false)
    private void vulkanmod$beginFrameSlotWait(CallbackInfo ci) {
        vulkanmod$frameSlotWaitStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.FRAME_SLOT_WAIT);
    }

    @Inject(method = "resetBuffers", at = @At("RETURN"), remap = false)
    private void vulkanmod$endFrameSlotWait(CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.FRAME_SLOT_WAIT, vulkanmod$frameSlotWaitStart);
        vulkanmod$frameSlotWaitStart = 0L;
    }
}
