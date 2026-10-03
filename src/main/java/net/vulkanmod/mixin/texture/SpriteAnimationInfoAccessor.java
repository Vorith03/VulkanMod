package net.vulkanmod.mixin.texture;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;
@Mixin(SpriteContents.AnimatedTexture.class)
public interface SpriteAnimationInfoAccessor {
    @Accessor("frames") List<SpriteContents.FrameInfo> vulkanmod$frames();
    @Invoker("uploadFrame") void vulkanmod$uploadFrame(int x, int y, int frame);
}
