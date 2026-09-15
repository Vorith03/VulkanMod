package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;

import java.util.concurrent.atomic.AtomicBoolean;

/** Restart-time gate for live sparse-lighting capture. CPU terrain remains authoritative. */
public final class GpuSparseLightingMode {
    public static final String PROPERTY = "vulkanmod.experimentalGpuSparseLighting";
    public static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final AtomicBoolean CAPTURE_FAILURE_REPORTED = new AtomicBoolean();

    private GpuSparseLightingMode() {}

    /** Fail closed to the already-built CPU terrain and avoid flooding logs from workers. */
    public static void onCaptureFailure(RuntimeException exception) {
        if (CAPTURE_FAILURE_REPORTED.compareAndSet(false, true)) {
            Initializer.LOGGER.warn("Sparse GPU lighting capture failed; keeping CPU terrain fallback", exception);
        }
    }
}
