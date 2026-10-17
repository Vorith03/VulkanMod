package net.vulkanmod.interfaces;

public interface VSpriteContentsI {
    boolean vulkanmod$isStaticSprite();

    long vulkanmod$getCpuBytes();
    void vulkanmod$uploadFramePixels(int x, int y, int sourceX, int sourceY,
                                    com.mojang.blaze3d.platform.NativeImage[] images);
    net.vulkanmod.render.texture.SpriteAnimationState vulkanmod$animationState();
}
