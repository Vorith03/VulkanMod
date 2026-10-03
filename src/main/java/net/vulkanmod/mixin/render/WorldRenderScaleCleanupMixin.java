package net.vulkanmod.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.render.scale.WorldRenderScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class WorldRenderScaleCleanupMixin {
    @WrapOperation(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"))
    private void vulkanmod$finishWorldCapture(GameRenderer renderer, float partialTick, long deadline,
            boolean renderLevel, Operation<Void> original) {
        try {
            original.call(renderer, partialTick, deadline, renderLevel);
        } finally {
            if(WorldRenderScale.active()) WorldRenderScale.endCapture(false);
        }
    }
}
