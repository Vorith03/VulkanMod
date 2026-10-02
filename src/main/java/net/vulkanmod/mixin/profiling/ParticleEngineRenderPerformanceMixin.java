package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineRenderPerformanceMixin {
    // Forge 1.20.1 adds this six-argument overload itself, so there is no vanilla
    // obfuscation mapping for the target method name. Keep the exact descriptor and
    // disable remapping for the injection target rather than falling back to the
    // deprecated five-argument vanilla entry point.
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void vulkanmod$beginRender(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                       LightTexture lightTexture, Camera camera, float partialTick,
                                       Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.begin(WorldRenderAttribution.Category.PARTICLE_RENDER);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void vulkanmod$endRender(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                     LightTexture lightTexture, Camera camera, float partialTick,
                                     Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.end(WorldRenderAttribution.Category.PARTICLE_RENDER);
    }
}
