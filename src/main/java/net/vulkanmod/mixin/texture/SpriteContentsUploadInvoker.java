package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Separate refmap namespace from MSpriteContents' injection selectors. */
@Mixin(SpriteContents.class)
public interface SpriteContentsUploadInvoker {
    @Invoker("upload")
    void vulkanmod$uploadFramePixels(int x, int y, int sourceX, int sourceY, NativeImage[] images);
}
