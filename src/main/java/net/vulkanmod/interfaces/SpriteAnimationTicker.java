package net.vulkanmod.interfaces;

public interface SpriteAnimationTicker {
    boolean vulkanmod$materialize();
    boolean vulkanmod$tryGpuInterpolation(int x, int y);
    void vulkanmod$refreshFrame(int x, int y);
    long vulkanmod$clock();
}
