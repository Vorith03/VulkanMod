package net.vulkanmod.interfaces;

public interface SpriteAnimationTicker {
    boolean vulkanmod$materialize();
    boolean vulkanmod$tryGpuInterpolation(int x, int y);
    boolean vulkanmod$tryCpuInterpolation(int x, int y,
            com.mojang.blaze3d.platform.NativeImage[] output);
    void vulkanmod$refreshFrame(int x, int y);
    long vulkanmod$clock();
}
