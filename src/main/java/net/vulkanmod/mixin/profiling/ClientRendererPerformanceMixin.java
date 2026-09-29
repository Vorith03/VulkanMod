package net.vulkanmod.mixin.profiling;

import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GameRenderer.tick is client-side tick work, distinct from GameRenderer.render. */
@Mixin(GameRenderer.class)
public class ClientRendererPerformanceMixin {
    @Unique private long vulkanmod$rendererTickStart;
    @Unique private long vulkanmod$worldRenderStart;
    @Unique private int vulkanmod$worldRenderDepth;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void vulkanmod$beginRendererTick(CallbackInfo ci) {
        vulkanmod$rendererTickStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.CLIENT_RENDERER_TICK);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void vulkanmod$endRendererTick(CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.CLIENT_RENDERER_TICK, vulkanmod$rendererTickStart);
        vulkanmod$rendererTickStart = 0L;
    }

    // Portal rendering can recurse into renderLevel. Count only the outer call so
    // nested worlds remain included without double-counting their CPU time.
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vulkanmod$beginWorldRender(CallbackInfo ci) {
        if (vulkanmod$worldRenderDepth++ == 0)
            vulkanmod$worldRenderStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.WORLD_RENDER);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void vulkanmod$endWorldRender(CallbackInfo ci) {
        if (--vulkanmod$worldRenderDepth == 0) {
            PerformanceProfiler.end(PerformanceProfiler.Stage.WORLD_RENDER, vulkanmod$worldRenderStart);
            vulkanmod$worldRenderStart = 0L;
        }
    }
}
