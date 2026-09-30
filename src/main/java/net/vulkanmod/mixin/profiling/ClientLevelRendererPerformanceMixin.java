package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class ClientLevelRendererPerformanceMixin {
    @Unique private long vulkanmod$tickStart;
    @Unique private long vulkanmod$weatherStart;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void vulkanmod$beginTick(CallbackInfo ci) {
        vulkanmod$tickStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.LEVEL_RENDERER);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void vulkanmod$endTick(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.LEVEL_RENDERER, vulkanmod$tickStart);
        vulkanmod$tickStart = 0L;
    }

    @Inject(method = "tickRain(Lnet/minecraft/client/Camera;)V", at = @At("HEAD"))
    private void vulkanmod$beginWeather(Camera camera, CallbackInfo ci) {
        vulkanmod$weatherStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.WEATHER);
    }

    @Inject(method = "tickRain(Lnet/minecraft/client/Camera;)V", at = @At("RETURN"))
    private void vulkanmod$endWeather(Camera camera, CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.WEATHER, vulkanmod$weatherStart);
        vulkanmod$weatherStart = 0L;
    }
}
