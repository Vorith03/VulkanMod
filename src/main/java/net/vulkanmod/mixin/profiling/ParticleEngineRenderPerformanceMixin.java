package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.vulkanmod.render.profiling.ParticleAttribution;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineRenderPerformanceMixin {
    private static final String VULKANMOD$FORGE_RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;" +
            "Lnet/minecraft/client/renderer/LightTexture;" +
            "Lnet/minecraft/client/Camera;F" +
            "Lnet/minecraft/client/renderer/culling/Frustum;)V";

    // Forge 1.20.1 adds this six-argument overload itself, so there is no vanilla
    // obfuscation mapping for the target method name. Keep the exact descriptor.
    @Inject(method = VULKANMOD$FORGE_RENDER, at = @At("HEAD"), remap = false)
    private void vulkanmod$beginRender(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                       LightTexture lightTexture, Camera camera, float partialTick,
                                       Frustum frustum, CallbackInfo ci) {
        ParticleAttribution.beginRenderPass();
        WorldRenderAttribution.begin(WorldRenderAttribution.Category.PARTICLE_RENDER);
    }

    @Redirect(
            method = VULKANMOD$FORGE_RENDER,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
                    remap = true
            ),
            remap = false
    )
    private void vulkanmod$profileParticleRender(Particle particle, VertexConsumer consumer,
                                                  Camera camera, float partialTick) {
        Object renderType = particle.getRenderType();
        int token = ParticleAttribution.beginParticleRender(particle, renderType);
        long start = token > 0 ? System.nanoTime() : 0L;
        try {
            particle.render(consumer, camera, partialTick);
        } finally {
            ParticleAttribution.endParticleRender(token,
                    token > 0 ? Math.max(0L, System.nanoTime() - start) : 0L);
        }
    }

    @Inject(method = VULKANMOD$FORGE_RENDER, at = @At("RETURN"), remap = false)
    private void vulkanmod$endRender(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                     LightTexture lightTexture, Camera camera, float partialTick,
                                     Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.end(WorldRenderAttribution.Category.PARTICLE_RENDER);
    }
}
