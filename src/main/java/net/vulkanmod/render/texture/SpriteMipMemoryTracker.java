package net.vulkanmod.render.texture;

import net.vulkanmod.Initializer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks static sprite mip bytes that are intentionally not resident during
 * resource preparation. This is diagnostic accounting only; it never owns the
 * corresponding NativeImages.
 */
public final class SpriteMipMemoryTracker {
    private static final long MIB = 1024L * 1024L;
    private static final AtomicLong CURRENT_BYTES = new AtomicLong();
    private static final AtomicLong PEAK_BYTES = new AtomicLong();
    private static final AtomicLong CURRENT_SPRITES = new AtomicLong();

    private SpriteMipMemoryTracker() {
    }

    public static void updateDeferred(long oldBytes, long newBytes) {
        long delta = newBytes - oldBytes;
        if(delta != 0L) {
            long current = CURRENT_BYTES.addAndGet(delta);
            PEAK_BYTES.accumulateAndGet(current, Math::max);
        }

        if(oldBytes == 0L && newBytes > 0L) {
            CURRENT_SPRITES.incrementAndGet();
        } else if(oldBytes > 0L && newBytes == 0L) {
            CURRENT_SPRITES.updateAndGet(value -> Math.max(0L, value - 1L));
        }
    }

    public static long getCurrentBytes() {
        return CURRENT_BYTES.get();
    }

    public static void logSnapshot(String reason) {
        Initializer.LOGGER.info(
                "Static sprite mip deferral [{}]: pending={} MiB across {} sprites, peak={} MiB",
                reason,
                CURRENT_BYTES.get() / MIB,
                CURRENT_SPRITES.get(),
                PEAK_BYTES.get() / MIB);
    }
}
