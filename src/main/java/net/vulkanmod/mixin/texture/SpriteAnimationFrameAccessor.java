package net.vulkanmod.mixin.texture;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SpriteContents.FrameInfo.class)
public interface SpriteAnimationFrameAccessor {
    @Accessor("index") int vulkanmod$frameIndex();
    @Accessor("time") int vulkanmod$frameTime();
}
