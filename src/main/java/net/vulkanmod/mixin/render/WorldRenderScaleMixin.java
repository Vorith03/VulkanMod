package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.render.scale.WorldRenderScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class WorldRenderScaleMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void vulkanmod$scaleWorld(GameRenderer renderer, float partialTick, long deadline,
            PoseStack pose) {
        boolean captured = WorldRenderScale.begin(Minecraft.getInstance());
        try {
            renderer.renderLevel(partialTick, deadline, pose);
        } catch(RuntimeException | Error failure) {
            if(captured) WorldRenderScale.endCapture(false);
            throw failure;
        }
    }

    // Camera post effects run AFTER renderLevel. Keep their main target scaled
    // until the native GUI boundary; Minecraft's wrapper owns exception cleanup.
    @Inject(method = "render", at = @At(value = "NEW", target = "net/minecraft/client/gui/GuiGraphics"))
    private void vulkanmod$composeBeforeGui(float partialTick, long deadline, boolean renderLevel, CallbackInfo ci) {
        if(WorldRenderScale.active()) WorldRenderScale.endCapture(true);
    }
}
