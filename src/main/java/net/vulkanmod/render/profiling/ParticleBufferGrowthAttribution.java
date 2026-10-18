package net.vulkanmod.render.profiling;

import java.util.Locale;

/**
 * Bounded diagnosis of the BufferBuilder finalization hot path, not an
 * optimization. Counts actual native MemoryTracker.resize calls separately
 * from ordinary ensureCapacity checks while the Forge particle render runs.
 *
 * Only the Render thread participates, and all hooks are disabled outside
 * opt-in automated performance captures. No per-vertex allocation or logging.
 */
public final class ParticleBufferGrowthAttribution {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");

    private static Thread activeRenderThread;
    private static int renderDepth;
    private static long finalizedBatches;
    private static long finalizationCapacityChecks;
    private static long resizeCalls;
    private static long finalizationResizes;
    private static long vertexResizes;
    private static long resizeNanos;
    private static long finalizationResizeNanos;
    private static long oldCapacityBytes;
    private static long requestedCapacityBytes;
    private static long newCapacityBytes;
    private static long capacityDeltaBytes;
    private static long maximumNewCapacityBytes;

    private ParticleBufferGrowthAttribution() {
    }

    /** Nesting is possible if portal rendering re-enters particle rendering. */
    public static void beginRender() {
        if (!ENABLED || !PerformanceProfiler.isFrameCapturing()) return;
        Thread current = Thread.currentThread();
        if (activeRenderThread == null) {
            activeRenderThread = current;
            renderDepth = 1;
        } else if (activeRenderThread == current) {
            renderDepth++;
        }
    }

    public static void endRender() {
        if (!ENABLED || activeRenderThread != Thread.currentThread()) return;
        if (--renderDepth == 0) activeRenderThread = null;
    }

    public static boolean isActive() {
        return ENABLED && activeRenderThread == Thread.currentThread()
                && PerformanceProfiler.isFrameCapturing();
    }

    /** Called only on the once-per-batch storeRenderedBuffer path. */
    public static boolean beginFinalization() {
        if (!isActive()) return false;
        finalizedBatches++;
        return true;
    }

    public static void finalizationCapacityCheck() {
        if (isActive()) finalizationCapacityChecks++;
    }

    /** Invoked at the actual MemoryTracker.resize call, never at a no-growth check. */
    public static void resized(int oldCapacity, int requestedCapacity, int newCapacity,
                               long elapsedNanos, boolean finalizing) {
        if (!isActive()) return;
        resizeCalls++;
        if (finalizing) {
            finalizationResizes++;
            finalizationResizeNanos += Math.max(0L, elapsedNanos);
        } else {
            vertexResizes++;
        }
        resizeNanos += Math.max(0L, elapsedNanos);
        oldCapacityBytes += oldCapacity;
        requestedCapacityBytes += requestedCapacity;
        newCapacityBytes += newCapacity;
        capacityDeltaBytes += Math.max(0L, (long)newCapacity - oldCapacity);
        maximumNewCapacityBytes = Math.max(maximumNewCapacityBytes, newCapacity);
    }

    public static void emitSummary() {
        if (!ENABLED) return;
        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "particle_buffer_growth scope=measured_particle_render_thread finalizations=%d finalization_capacity_checks=%d resize_calls=%d finalization_resize_calls=%d vertex_resize_calls=%d resize_wall_ms=%.3f finalization_resize_wall_ms=%.3f sum_old_capacity_bytes=%d sum_requested_capacity_bytes=%d sum_new_capacity_bytes=%d positive_capacity_delta_bytes=%d max_new_capacity_bytes=%d copied_bytes_measured=false",
                finalizedBatches, finalizationCapacityChecks, resizeCalls, finalizationResizes,
                vertexResizes, resizeNanos / 1_000_000.0D,
                finalizationResizeNanos / 1_000_000.0D,
                oldCapacityBytes, requestedCapacityBytes, newCapacityBytes,
                capacityDeltaBytes, maximumNewCapacityBytes));
        finalizedBatches = finalizationCapacityChecks = resizeCalls = 0L;
        finalizationResizes = vertexResizes = resizeNanos = finalizationResizeNanos = 0L;
        oldCapacityBytes = requestedCapacityBytes = newCapacityBytes = 0L;
        capacityDeltaBytes = maximumNewCapacityBytes = 0L;
        activeRenderThread = null;
        renderDepth = 0;
    }
}
