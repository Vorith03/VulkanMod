package net.vulkanmod.render.chunk.build;

import net.vulkanmod.Initializer;

/** Render-thread frame feedback; workers only read the smoothed duration. */
public final class ChunkFrameTiming {
    private static long frame;
    private static long start;
    private static volatile double frameMs = 16.6667;
    private ChunkFrameTiming() {}
    public static void begin() { frame++; start = System.nanoTime(); }
    public static void end() {
        long elapsed = System.nanoTime() - start;
        if(start != 0 && elapsed > 0 && elapsed < 1_000_000_000L)
            frameMs += (elapsed / 1_000_000.0 - frameMs) * 0.125;
    }
    public static long frame() { return frame; }
    public static double frameMs() { return frameMs; }
    public static boolean enabled() {
        return Initializer.CONFIG != null && Initializer.CONFIG.adaptiveChunkScheduling;
    }
    public static double targetMs() {
        double value = Initializer.CONFIG.chunkTargetFrameMs;
        return Double.isFinite(value) ? Math.max(4, Math.min(100, value)) : 16.6667;
    }
}
