package net.vulkanmod.mixin.texture;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(SpriteContents.InterpolationData.class)
public interface SpriteInterpolationInvoker {
    @Invoker("uploadInterpolatedFrame") void vulkanmod$interpolate(int x, int y, SpriteContents.Ticker ticker);
}
