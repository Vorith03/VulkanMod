package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.client.ForgeHooksClient;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeHooksClient.class, remap = false)
public class ForgeHooksClientPerformanceMixin {
    @Unique private static long vulkanmod$renderStageStart;

    @Inject(method = "dispatchRenderStage", at = @At("HEAD"), remap = false)
    private static void vulkanmod$beginRenderStage(RenderType renderType, LevelRenderer levelRenderer,
                                                   PoseStack poseStack, Matrix4f projectionMatrix, int ticks,
                                                   Camera camera, Frustum frustum, CallbackInfo ci) {
        vulkanmod$renderStageStart = WorldRenderAttribution.begin(WorldRenderAttribution.Category.FORGE_RENDER_STAGE);
    }

    @Inject(method = "dispatchRenderStage", at = @At("RETURN"), remap = false)
    private static void vulkanmod$endRenderStage(RenderType renderType, LevelRenderer levelRenderer,
                                                 PoseStack poseStack, Matrix4f projectionMatrix, int ticks,
                                                 Camera camera, Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.end(WorldRenderAttribution.Category.FORGE_RENDER_STAGE, vulkanmod$renderStageStart);
        vulkanmod$renderStageStart = 0L;
    }
}
