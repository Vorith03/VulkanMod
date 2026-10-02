package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeHooksClient.class, remap = false)
public class ForgeHooksClientPerformanceMixin {
    @Inject(method = "dispatchRenderStage", at = @At("HEAD"), remap = false)
    private static void vulkanmod$beginRenderStage(RenderLevelStageEvent.Stage stage, LevelRenderer levelRenderer,
                                                   PoseStack poseStack, Matrix4f projectionMatrix, int ticks,
                                                   Camera camera, Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.begin(WorldRenderAttribution.Category.FORGE_RENDER_STAGE);
    }

    @Inject(method = "dispatchRenderStage", at = @At("RETURN"), remap = false)
    private static void vulkanmod$endRenderStage(RenderLevelStageEvent.Stage stage, LevelRenderer levelRenderer,
                                                 PoseStack poseStack, Matrix4f projectionMatrix, int ticks,
                                                 Camera camera, Frustum frustum, CallbackInfo ci) {
        WorldRenderAttribution.end(WorldRenderAttribution.Category.FORGE_RENDER_STAGE);
    }
}
