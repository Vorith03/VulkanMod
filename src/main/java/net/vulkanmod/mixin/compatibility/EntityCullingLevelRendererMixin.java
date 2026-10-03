package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.vulkanmod.compatibility.EntityCullingCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// HEAD insertion by the later/lower-priority mixin precedes existing HEAD hooks.
@Mixin(value = LevelRenderer.class, priority = 500)
public abstract class EntityCullingLevelRendererMixin {
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void vulkanmod$preserveEntityCullingView(Entity entity, double x, double y, double z,
            float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        EntityCullingCompat.prepareEntityRender(entity);
    }
}
