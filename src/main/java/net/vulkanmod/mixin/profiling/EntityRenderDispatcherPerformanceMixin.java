package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.vulkanmod.render.profiling.WorldRenderAttribution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherPerformanceMixin {
    @Unique private long vulkanmod$entityRenderStart;

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void vulkanmod$beginEntityRender(Entity entity, double x, double y, double z,
                                             float yaw, float partialTick, PoseStack poseStack,
                                             MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        vulkanmod$entityRenderStart = WorldRenderAttribution.begin(WorldRenderAttribution.Category.ENTITY_RENDER);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void vulkanmod$endEntityRender(Entity entity, double x, double y, double z,
                                           float yaw, float partialTick, PoseStack poseStack,
                                           MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        WorldRenderAttribution.end(WorldRenderAttribution.Category.ENTITY_RENDER, vulkanmod$entityRenderStart);
        vulkanmod$entityRenderStart = 0L;
    }
}
