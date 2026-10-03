package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanmod.compatibility.EntityCullingCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityRenderDispatcher.class, priority = 500)
public abstract class EntityCullingBlockEntityMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At("HEAD"))
    private void vulkanmod$preserveBlockEntityCullingView(BlockEntity entity, float partialTick,
            PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        EntityCullingCompat.prepareBlockEntityRender(entity);
    }
}
