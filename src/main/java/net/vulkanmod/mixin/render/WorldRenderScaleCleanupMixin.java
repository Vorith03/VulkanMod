package net.vulkanmod.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.render.scale.WorldRenderScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class WorldRenderScaleCleanupMixin {
    @Redirect(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"))
    private void vulkanmod$finishWorldCapture(GameRenderer renderer, float partialTick, long deadline,
            boolean renderLevel) {
        try {
            renderer.render(partialTick, deadline, renderLevel);
        } finally {
            if(WorldRenderScale.active()) WorldRenderScale.endCapture(false);
        }
    }
}
