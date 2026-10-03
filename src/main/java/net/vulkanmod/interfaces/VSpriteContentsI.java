package net.vulkanmod.interfaces;

public interface VSpriteContentsI {
    boolean vulkanmod$isStaticSprite();

    long vulkanmod$getCpuBytes();
    net.vulkanmod.render.texture.SpriteAnimationState vulkanmod$animationState();
}
