package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.vulkanmod.interfaces.SpriteAnimationTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.InterpolationData.class)
public abstract class SpriteInterpolationMixin {
    @Shadow @Final
    private NativeImage[] activeFrame;
    @Inject(method = "uploadInterpolatedFrame", at = @At("HEAD"), cancellable = true)
    private void vulkanmod$skipHiddenPixelWork(int x, int y, SpriteContents.Ticker ticker, CallbackInfo ci) {
        SpriteAnimationTicker animationTicker = (SpriteAnimationTicker)ticker;
        if(!animationTicker.vulkanmod$materialize()
                || animationTicker.vulkanmod$tryGpuInterpolation(x, y)
                || animationTicker.vulkanmod$tryCpuInterpolation(x, y, activeFrame)) {
            ci.cancel();
        }
    }
}
