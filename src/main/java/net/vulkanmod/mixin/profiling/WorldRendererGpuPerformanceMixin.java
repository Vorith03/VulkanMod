package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.profiling.GpuTimestampProfiler;
import net.vulkanmod.vulkan.Renderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU-only timing around VulkanMod terrain-layer command recording. */
@Mixin(value = WorldRenderer.class, remap = false)
public class WorldRendererGpuPerformanceMixin {
    @Unique private int vulkanmod$terrainGpuToken = -1;

    @Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void vulkanmod$beginTerrainGpu(RenderType renderType, PoseStack poseStack,
                                            double camX, double camY, double camZ,
                                            Matrix4f projection, CallbackInfo ci) {
        vulkanmod$terrainGpuToken = GpuTimestampProfiler.beginTerrainSegment(
                Renderer.getCurrentFrame(), Renderer.getCommandBuffer());
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void vulkanmod$endTerrainGpu(RenderType renderType, PoseStack poseStack,
                                          double camX, double camY, double camZ,
                                          Matrix4f projection, CallbackInfo ci) {
        GpuTimestampProfiler.endTerrainSegment(Renderer.getCurrentFrame(), Renderer.getCommandBuffer(),
                vulkanmod$terrainGpuToken);
        vulkanmod$terrainGpuToken = -1;
    }
}
