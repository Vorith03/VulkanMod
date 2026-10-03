package net.vulkanmod.mixin.texture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.vulkanmod.render.texture.SpriteAnimationUsage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(TextureAtlasSprite.class)
public abstract class SpriteUsageMixin {
    @Inject(method = {"getU0", "getU1", "getV0", "getV1", "getU", "getV"}, at = @At("HEAD"))
    private void vulkanmod$used(CallbackInfoReturnable<Float> ci) {
        SpriteAnimationUsage.use((TextureAtlasSprite)(Object)this);
    }
}
