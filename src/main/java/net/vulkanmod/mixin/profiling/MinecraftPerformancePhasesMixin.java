package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Minecraft;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Splits the broad Minecraft.runTick() CPU path around stable vanilla call boundaries.
 *
 * <p>Display-update timing lives in VulkanMod's Window.updateDisplay() overwrite:
 * injections at the same runTick call site cannot reliably enclose only the call
 * when another mixin injects Vulkan submission immediately before it.</p>
 */
@Mixin(value = Minecraft.class, priority = 500)
public class MinecraftPerformancePhasesMixin {
    @Unique private long vulkanmod$clientTickStart;
    @Unique private long vulkanmod$gameRenderStart;
    @Unique private long vulkanmod$frameLimitStart;

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;tick()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void vulkanmod$beginClientTick(boolean tick, CallbackInfo ci) {
        vulkanmod$clientTickStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.CLIENT_TICK);
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanmod$endClientTick(boolean tick, CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.CLIENT_TICK, vulkanmod$clientTickStart);
        vulkanmod$clientTickStart = 0L;
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void vulkanmod$beginGameRender(boolean tick, CallbackInfo ci) {
        vulkanmod$gameRenderStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.GAME_RENDER);
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanmod$endGameRender(boolean tick, CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.GAME_RENDER, vulkanmod$gameRenderStart);
        vulkanmod$gameRenderStart = 0L;
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void vulkanmod$beginFrameLimit(boolean tick, CallbackInfo ci) {
        vulkanmod$frameLimitStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.FRAME_LIMIT);
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanmod$endFrameLimit(boolean tick, CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.FRAME_LIMIT, vulkanmod$frameLimitStart);
        vulkanmod$frameLimitStart = 0L;
    }
}
