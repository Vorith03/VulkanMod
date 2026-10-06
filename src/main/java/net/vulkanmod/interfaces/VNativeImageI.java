package net.vulkanmod.interfaces;

import java.nio.ByteBuffer;

public interface VNativeImageI {
    long vulkanmod$getTrackedNativeBytes();

    /**
     * Internal read-only view used to seed immutable GPU animation residency.
     * Returns null after close. The returned view owns only position/limit state.
     */
    ByteBuffer vulkanmod$getReadOnlyBuffer();

    /** Begin/observe mutation tracking for an admitted animation source. */
    long vulkanmod$getMutationGeneration();

    boolean vulkanmod$isRgba();
}
