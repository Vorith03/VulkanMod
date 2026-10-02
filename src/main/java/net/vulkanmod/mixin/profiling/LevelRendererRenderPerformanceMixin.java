package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererRenderPerformanceMixin {
    @Unique private long vulkanmod$cloudRenderStart;

    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void vulkanmod$beginCloudRender(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick,
                                            double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        vulkanmod$cloudRenderStart = WorldRenderAttribution.begin(WorldRenderAttribution.Category.CLOUD_RENDER);
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void vulkanmod$endCloudRender(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick,
                                          double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        WorldRenderAttribution.end(WorldRenderAttribution.Category.CLOUD_RENDER, vulkanmod$cloudRenderStart);
        vulkanmod$cloudRenderStart = 0L;
    }
}
