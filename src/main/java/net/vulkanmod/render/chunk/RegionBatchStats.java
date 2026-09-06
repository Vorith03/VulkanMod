package net.vulkanmod.render.chunk;

/** Render-thread counters, exposed through the existing F3 chunk statistics. */
final class RegionBatchStats {
    static int sections, calls, uploads, bytes;

    static void reset() { sections = calls = uploads = bytes = 0; }

    static String describe() {
        return String.format(" Region: %d sections/%d calls, %d updates/%d B", sections, calls, uploads, bytes);
    }
}
