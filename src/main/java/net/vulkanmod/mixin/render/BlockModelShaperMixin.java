package net.vulkanmod.mixin.render;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.GpuTerrainOutputComputeSmokeTest;
import net.vulkanmod.render.chunk.GpuTerrainOutputStoreSmokeTest;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelRegistry;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelTableSmokeTest;
import net.vulkanmod.render.chunk.voxel.GpuTerrainPersistentVertexSmokeTest;
import net.vulkanmod.render.chunk.voxel.GpuTerrainSectionMesherSmokeTest;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Publishes one immutable GPU-terrain qualification snapshot per baked-model generation. */
@Mixin(BlockModelShaper.class)
public abstract class BlockModelShaperMixin {
    @Inject(method = "replaceCache", at = @At("TAIL"))
    private void vulkanmod$replaceGpuTerrainModelRegistry(Map<BlockState, BakedModel> models, CallbackInfo ci) {
        GpuTerrainModelRegistry.replaceModels(models);
        if (Boolean.getBoolean("vulkanmod.smokeTest")) {
            // Model baking can finish after the renderer advances a frame but before
            // beginFrame gives terrain uploads their normal frame-domain callback.
            AreaUploadManager.INSTANCE.updateFrame(Renderer.getCurrentFrame());
            GpuTerrainOutputStoreSmokeTest.verify();
            GpuTerrainOutputComputeSmokeTest.verify();
            GpuTerrainModelTableSmokeTest.verify();
            GpuTerrainPersistentVertexSmokeTest.verify();
            GpuTerrainSectionMesherSmokeTest.verify();
            Initializer.LOGGER.info("Vulkan smoke test passed");
            System.exit(0);
        }
    }
}
