package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineRenderPerformanceMixin {
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", at = @At("HEAD"))
    private void vulkanmod$beginRender(LightTexture lightTexture, Camera camera, float partialTick,
                                       Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.begin(WorldRenderAttribution.Category.PARTICLE_RENDER);
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", at = @At("RETURN"))
    private void vulkanmod$endRender(LightTexture lightTexture, Camera camera, float partialTick,
                                     Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.end(WorldRenderAttribution.Category.PARTICLE_RENDER);
    }
}
