package net.vulkanmod.mixin.render;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelRegistry;
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
    }
}
