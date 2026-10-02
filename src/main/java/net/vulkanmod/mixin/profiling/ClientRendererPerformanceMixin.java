package net.vulkanmod.mixin.profiling;

import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import net.vulkanmod.render.profiling.GpuTimestampProfiler;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GameRenderer.tick is client-side tick work, distinct from GameRenderer.render. */
@Mixin(GameRenderer.class)
public class ClientRendererPerformanceMixin {
    @Unique private long vulkanmod$rendererTickStart;
    @Unique private long vulkanmod$pickStart;
    @Unique private long vulkanmod$worldRenderStart;
    @Unique private long vulkanmod$hudRenderStart;
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

    @Inject(method = "pick(F)V", at = @At("HEAD"))
    private void vulkanmod$beginPick(float partialTick, CallbackInfo ci) {
        vulkanmod$pickStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.PICK);
    }

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void vulkanmod$endPick(float partialTick, CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.PICK, vulkanmod$pickStart);
        vulkanmod$pickStart = 0L;
    }

    // Portal rendering can recurse into renderLevel. Count only the outer call so
    // nested worlds remain included without double-counting their CPU or GPU time.
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vulkanmod$beginWorldRender(CallbackInfo ci) {
        if (vulkanmod$worldRenderDepth++ == 0) {
            vulkanmod$worldRenderStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.WORLD_RENDER);
            GpuTimestampProfiler.boundary(Renderer.getCurrentFrame(), Renderer.getCommandBuffer(),
                    GpuTimestampProfiler.Boundary.WORLD_BEGIN);
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void vulkanmod$endWorldRender(CallbackInfo ci) {
        if (--vulkanmod$worldRenderDepth == 0) {
            GpuTimestampProfiler.boundary(Renderer.getCurrentFrame(), Renderer.getCommandBuffer(),
                    GpuTimestampProfiler.Boundary.WORLD_END);
            PerformanceProfiler.end(PerformanceProfiler.Stage.WORLD_RENDER, vulkanmod$worldRenderStart);
            vulkanmod$worldRenderStart = 0L;
        }
    }

    // Forge installs ForgeGui, which fully overrides Gui.render(). Bracket the
    // GameRenderer call site so virtual dispatch to either implementation is timed.
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void vulkanmod$beginHudRender(CallbackInfo ci) {
        GpuTimestampProfiler.boundary(Renderer.getCurrentFrame(), Renderer.getCommandBuffer(),
                GpuTimestampProfiler.Boundary.HUD_BEGIN);
        vulkanmod$hudRenderStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.HUD_RENDER);
    }

    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            shift = At.Shift.AFTER))
    private void vulkanmod$endHudRender(CallbackInfo ci) {
        GpuTimestampProfiler.boundary(Renderer.getCurrentFrame(), Renderer.getCommandBuffer(),
                GpuTimestampProfiler.Boundary.HUD_END);
        PerformanceProfiler.end(PerformanceProfiler.Stage.HUD_RENDER, vulkanmod$hudRenderStart);
        vulkanmod$hudRenderStart = 0L;
    }
}
