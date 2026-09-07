package net.vulkanmod.vulkan.memory;

import net.vulkanmod.Initializer;

public final class StagingBufferSmokeTest {
    private static final int MIB = 1024 * 1024;
    private static boolean verified;

    private StagingBufferSmokeTest() {
    }

    public static synchronized void verify() {
        if(verified)
            return;

        int limit = 128 * MIB;

        if(StagingBuffer.wouldExceedUsageLimit(64 * MIB, 32 * MIB, 4, limit))
            throw new IllegalStateException("Texture staging policy flushed below its usage limit");

        if(!StagingBuffer.wouldExceedUsageLimit(120 * MIB, 9 * MIB, 4, limit))
            throw new IllegalStateException("Texture staging policy failed to flush above its usage limit");

        if(StagingBuffer.wouldExceedUsageLimit(0, 256 * MIB, 4, limit))
            throw new IllegalStateException("Texture staging policy cannot flush an empty buffer");

        int cappedGrowth = StagingBuffer.calculateGrowthSize(120 * MIB, 122 * MIB, limit);
        if(cappedGrowth != limit)
            throw new IllegalStateException("Texture staging growth exceeded its cap: " + cappedGrowth);

        int oversizedSingleUpload = StagingBuffer.calculateGrowthSize(128 * MIB, 192 * MIB, limit);
        if(oversizedSingleUpload != 192 * MIB)
            throw new IllegalStateException("Oversized staging upload was geometrically overallocated: " + oversizedSingleUpload);

        verified = true;
        Initializer.LOGGER.info("Bounded texture staging smoke test passed");
    }
}
