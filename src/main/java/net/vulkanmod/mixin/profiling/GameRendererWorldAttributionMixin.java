package net.vulkanmod.mixin.profiling;

import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererWorldAttributionMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vulkanmod$beginWorldAttribution(CallbackInfo ci) {
        WorldRenderAttribution.beginWorldRender();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void vulkanmod$endWorldAttribution(CallbackInfo ci) {
        WorldRenderAttribution.endWorldRender();
    }
}
