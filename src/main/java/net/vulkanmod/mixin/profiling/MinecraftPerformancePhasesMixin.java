package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Minecraft;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Splits the broad Minecraft.runTick() CPU path around stable vanilla/Forge call boundaries.
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
    @Unique private long vulkanmod$keybindStart;
    @Unique private long vulkanmod$forgeClientPreStart;
    @Unique private long vulkanmod$forgeClientPostStart;
    @Unique private long vulkanmod$forgeLevelPreStart;
    @Unique private long vulkanmod$forgeLevelPostStart;

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
        if (vulkanmod$clientTickStart != 0L) ClientTickBreakdown.beginTick();
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
        if (vulkanmod$clientTickStart != 0L) ClientTickBreakdown.endTick();
        PerformanceProfiler.end(PerformanceProfiler.Stage.CLIENT_TICK, vulkanmod$clientTickStart);
        vulkanmod$clientTickStart = 0L;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreClientTick()V", shift = At.Shift.BEFORE))
    private void vulkanmod$beginForgeClientPre(CallbackInfo ci) {
        vulkanmod$forgeClientPreStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.FORGE_CLIENT_PRE);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreClientTick()V", shift = At.Shift.AFTER))
    private void vulkanmod$endForgeClientPre(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.FORGE_CLIENT_PRE, vulkanmod$forgeClientPreStart);
        vulkanmod$forgeClientPreStart = 0L;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.BEFORE))
    private void vulkanmod$beginForgeLevelPre(CallbackInfo ci) {
        vulkanmod$forgeLevelPreStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.FORGE_LEVEL_PRE);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.AFTER))
    private void vulkanmod$endForgeLevelPre(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.FORGE_LEVEL_PRE, vulkanmod$forgeLevelPreStart);
        vulkanmod$forgeLevelPreStart = 0L;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.BEFORE))
    private void vulkanmod$beginForgeLevelPost(CallbackInfo ci) {
        vulkanmod$forgeLevelPostStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.FORGE_LEVEL_POST);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.AFTER))
    private void vulkanmod$endForgeLevelPost(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.FORGE_LEVEL_POST, vulkanmod$forgeLevelPostStart);
        vulkanmod$forgeLevelPostStart = 0L;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostClientTick()V", shift = At.Shift.BEFORE))
    private void vulkanmod$beginForgeClientPost(CallbackInfo ci) {
        vulkanmod$forgeClientPostStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.FORGE_CLIENT_POST);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostClientTick()V", shift = At.Shift.AFTER))
    private void vulkanmod$endForgeClientPost(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.FORGE_CLIENT_POST, vulkanmod$forgeClientPostStart);
        vulkanmod$forgeClientPostStart = 0L;
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void vulkanmod$beginKeybinds(CallbackInfo ci) {
        vulkanmod$keybindStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.KEYBINDS);
    }

    @Inject(method = "handleKeybinds", at = @At("RETURN"))
    private void vulkanmod$endKeybinds(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.KEYBINDS, vulkanmod$keybindStart);
        vulkanmod$keybindStart = 0L;
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
