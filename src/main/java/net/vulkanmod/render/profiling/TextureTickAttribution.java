package net.vulkanmod.render.profiling;

import java.util.Arrays;
import java.util.Locale;

/**
 * Automated-benchmark-only attribution for texture-tick work that sits outside
 * individual SpriteContents.upload() timers.
 *
 * <p>The existing ClientTickBreakdown remains authoritative for total texture and
 * sprite-upload time. This helper measures the outer Vulkan upload-batch lifecycle,
 * the complete tickable-texture loop, and actual batched copy flushes so the old
 * non-upload residual can be separated without changing texture behavior.</p>
 */
public final class TextureTickAttribution {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");
    private static final int MAX_SAMPLES = 8192;
    private static final Phase[] PHASES = Phase.values();
    private static final int PHASE_COUNT = PHASES.length;

    private static final long[] currentPhaseNanos = ENABLED ? new long[PHASE_COUNT] : null;
    private static final long[] phaseSums = ENABLED ? new long[PHASE_COUNT] : null;
    private static final long[] phaseCalls = ENABLED ? new long[PHASE_COUNT] : null;
    private static final long[][] phaseSamples = ENABLED ? new long[PHASE_COUNT][MAX_SAMPLES] : null;
    private static final long[] tickSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] outerBatchSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] copyFlushSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] outerCopyFlushSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = ENABLED ? new long[MAX_SAMPLES] : null;

    private static boolean tickActive;
    private static int activePhase = -1;
    private static boolean currentOverlap;
    private static int ticks;
    private static int sampledTicks;
    private static int uploadTicks;
    private static int overlapTicks;
    private static long tickSumNanos;
    private static long outerBatchSumNanos;

    private static long currentCopyFlushNanos;
    private static long currentOuterCopyFlushNanos;
    private static int currentCopyFlushes;
    private static int currentCopyRegions;
    private static int currentOuterCopyFlushes;
    private static int currentOuterCopyRegions;
    private static long copyFlushNanosSum;
    private static long outerCopyFlushNanosSum;
    private static long copyFlushes;
    private static long copyRegions;
    private static long outerCopyFlushes;
    private static long outerCopyRegions;
    private static long residentCopyCalls;
    private static long residentCopyRegions;
    private static long residentCopyBytes;
    private static long residentInterpolationCalls;
    private static long residentInterpolationDispatches;
    private static long residentInterpolationPixels;
    private static long residentInterpolationBytes;
    private static long residentCurrentBytes;
    private static long residentPeakBytes;
    private static long residentAdmitted;
    private static long residentCapacityRejected;
    private static long residentSourceRejected;
    private static long residentStagingRejected;
    private static long residentInvalidated;
    private static long residentSourceBytes;
    private static long residentSourcePeakBytes;
    private static long residentScratchBytes;
    private static long residentScratchPeakBytes;
    private static long spriteUploadRequests;
    private static long cpuSpriteUploads;
    private static long gpuSpriteUploads;
    private static long standardInterpolationUpdates;
    private static long standardInterpolationGpuUpdates;

    /** Materialized upload routes only; hidden/catch-up suppression is not a fallback. */
    public static void recordSpriteUploadRoute(boolean gpu) {
        if (!ENABLED || !tickActive) return;
        spriteUploadRequests++;
        if (gpu) gpuSpriteUploads++;
        else cpuSpriteUploads++;
    }

    /** Standard ticker, distinct resolved frame indices, positive interpolation progress only. */
    public static void recordStandardInterpolationRoute(boolean gpu) {
        if (!ENABLED || !tickActive) return;
        standardInterpolationUpdates++;
        if (gpu) standardInterpolationGpuUpdates++;
    }

    private TextureTickAttribution() {
    }

    public static long beginTick() {
        if (!ENABLED || tickActive || !PerformanceProfiler.isClientTickCapturing()) return 0L;
        Arrays.fill(currentPhaseNanos, 0L);
        activePhase = -1;
        currentOverlap = false;
        currentCopyFlushNanos = 0L;
        currentOuterCopyFlushNanos = 0L;
        currentCopyFlushes = 0;
        currentCopyRegions = 0;
        currentOuterCopyFlushes = 0;
        currentOuterCopyRegions = 0;
        tickActive = true;
        return System.nanoTime();
    }

    public static long begin(Phase phase) {
        if (!ENABLED || !tickActive || phase == null) return 0L;
        if (activePhase != -1) {
            currentOverlap = true;
            return 0L;
        }
        activePhase = phase.ordinal();
        phaseCalls[activePhase]++;
        return System.nanoTime();
    }

    public static void end(Phase phase, long startNanos) {
        if (!ENABLED || !tickActive || phase == null || startNanos == 0L) return;
        int ordinal = phase.ordinal();
        if (activePhase != ordinal) {
            currentOverlap = true;
            return;
        }
        currentPhaseNanos[ordinal] += Math.max(0L, System.nanoTime() - startNanos);
        activePhase = -1;
    }

    /** Begin timing one non-empty vkCmdCopyBufferToImage region flush. */
    public static long beginSpriteCopyFlush() {
        return ENABLED && tickActive ? System.nanoTime() : 0L;
    }

    /** Record one non-empty flush and the number of VkBufferImageCopy regions it emitted. */
    public static void endSpriteCopyFlush(long startNanos, int regions) {
        if (!ENABLED || !tickActive || startNanos == 0L || regions <= 0) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        currentCopyFlushNanos += elapsed;
        currentCopyFlushes++;
        currentCopyRegions += regions;
        if (activePhase == Phase.BATCH_DRAIN.ordinal()) {
            currentOuterCopyFlushNanos += elapsed;
            currentOuterCopyFlushes++;
            currentOuterCopyRegions += regions;
        }
    }

    public static void recordResidentCopy(int regions, long bytes) {
        if(!ENABLED || !tickActive || regions <= 0 || bytes < 0L) return;
        residentCopyCalls++;
        residentCopyRegions += regions;
        residentCopyBytes += bytes;
    }

    public static void recordResidentInterpolation(int dispatches, long pixels, long bytes) {
        if(!ENABLED || !tickActive || dispatches <= 0 || pixels <= 0L || bytes < 0L) return;
        residentInterpolationCalls++;
        residentInterpolationDispatches += dispatches;
        residentInterpolationPixels += pixels;
        residentInterpolationBytes += bytes;
    }

    public static void residentAdmitted(long bytes) {
        if(!ENABLED || bytes <= 0L) return;
        residentCurrentBytes += bytes;
        residentPeakBytes = Math.max(residentPeakBytes, residentCurrentBytes);
        residentAdmitted++;
    }

    public static void residentReleased(long bytes) {
        if(!ENABLED || bytes <= 0L) return;
        residentCurrentBytes = Math.max(0L, residentCurrentBytes - bytes);
    }

    public static void residentSourceAdmitted(long bytes) {
        if (!ENABLED || bytes <= 0L) return;
        residentSourceBytes += bytes;
        residentSourcePeakBytes = Math.max(residentSourcePeakBytes, residentSourceBytes);
    }

    public static void residentSourceReleased(long bytes) {
        if (ENABLED && bytes > 0L) residentSourceBytes = Math.max(0L, residentSourceBytes - bytes);
    }

    public static void residentScratchAllocated(long bytes) {
        if (!ENABLED || bytes <= 0L) return;
        residentScratchBytes += bytes;
        residentScratchPeakBytes = Math.max(residentScratchPeakBytes, residentScratchBytes);
    }

    public static void residentScratchReleased(long bytes) {
        if (ENABLED && bytes > 0L) residentScratchBytes = Math.max(0L, residentScratchBytes - bytes);
    }

    public static void residentCapacityRejected() {
        if(ENABLED) residentCapacityRejected++;
    }

    public static void residentSourceRejected() {
        if(ENABLED) residentSourceRejected++;
    }

    public static void residentStagingRejected() {
        if(ENABLED) residentStagingRejected++;
    }

    public static void residentInvalidated() {
        if(ENABLED) residentInvalidated++;
    }

    public static void endTick(long startNanos) {
        if (!ENABLED || !tickActive || startNanos == 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        if (activePhase != -1) {
            currentOverlap = true;
            activePhase = -1;
        }

        long outerBatch = 0L;
        for (Phase phase : PHASES) {
            int ordinal = phase.ordinal();
            long value = currentPhaseNanos[ordinal];
            if (phase.residualCorrection) outerBatch += value;
            phaseSums[ordinal] += value;
            if (sampledTicks < MAX_SAMPLES) phaseSamples[ordinal][sampledTicks] = value;
        }

        ticks++;
        if (outerBatch > 0L) uploadTicks++;
        if (currentOverlap) overlapTicks++;
        tickSumNanos += elapsed;
        outerBatchSumNanos += outerBatch;
        copyFlushNanosSum += currentCopyFlushNanos;
        outerCopyFlushNanosSum += currentOuterCopyFlushNanos;
        copyFlushes += currentCopyFlushes;
        copyRegions += currentCopyRegions;
        outerCopyFlushes += currentOuterCopyFlushes;
        outerCopyRegions += currentOuterCopyRegions;
        if (sampledTicks < MAX_SAMPLES) {
            tickSamples[sampledTicks] = elapsed;
            outerBatchSamples[sampledTicks] = outerBatch;
            copyFlushSamples[sampledTicks] = currentCopyFlushNanos;
            outerCopyFlushSamples[sampledTicks] = currentOuterCopyFlushNanos;
            sampledTicks++;
        }
        tickActive = false;
    }

    public static void emitSummary() {
        if (!ENABLED || ticks == 0) return;
        if (tickActive) {
            tickActive = false;
            activePhase = -1;
        }

        StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                "texture_outer_batch_attribution ticks=%d sampled_ticks=%d sample_cap=%d upload_ticks=%d overlap_ticks=%d texture_tick_ms_avg=%.3f texture_tick_ms_p95=%.3f outer_batch_ms_avg=%.3f outer_batch_ms_p95=%.3f copy_flushes=%d copy_regions=%d copy_flushes_per_tick=%.3f copy_regions_per_tick=%.3f regions_per_flush=%.3f copy_flush_cpu_ms_avg=%.3f copy_flush_cpu_ms_p95=%.3f outer_copy_flushes=%d outer_copy_regions=%d outer_copy_flush_cpu_ms_avg=%.3f outer_copy_flush_cpu_ms_p95=%.3f corrected_non_upload=client_tick_texture_detail.non_upload_ms_avg-minus-outer_batch_ms_avg animation_iteration=tickable_loop_ms_avg-minus-client_tick_texture_detail.sprite_upload_ms_avg",
                ticks, sampledTicks, MAX_SAMPLES, uploadTicks, overlapTicks,
                millis(tickSumNanos / ticks), millis(percentile(tickSamples, sampledTicks, 0.95D)),
                millis(outerBatchSumNanos / ticks), millis(percentile(outerBatchSamples, sampledTicks, 0.95D)),
                copyFlushes, copyRegions, copyFlushes / (double)ticks, copyRegions / (double)ticks,
                copyFlushes == 0L ? 0.0D : copyRegions / (double)copyFlushes,
                millis(copyFlushNanosSum / ticks), millis(percentile(copyFlushSamples, sampledTicks, 0.95D)),
                outerCopyFlushes, outerCopyRegions, millis(outerCopyFlushNanosSum / ticks),
                millis(percentile(outerCopyFlushSamples, sampledTicks, 0.95D))));
        line.append(" sprite_upload_requests=").append(spriteUploadRequests)
                .append(" gpu_sprite_uploads=").append(gpuSpriteUploads)
                .append(" cpu_sprite_uploads=").append(cpuSpriteUploads)
                .append(" standard_interpolation_updates=").append(standardInterpolationUpdates)
                .append(" standard_interpolation_gpu_updates=").append(standardInterpolationGpuUpdates)
                .append(" standard_interpolation_cpu_updates=")
                .append(standardInterpolationUpdates - standardInterpolationGpuUpdates)
                .append(" resident_copy_calls=").append(residentCopyCalls)
                .append(" resident_copy_regions=").append(residentCopyRegions)
                .append(" resident_copy_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentCopyBytes / 1024.0D))
                .append(" resident_interpolation_calls=").append(residentInterpolationCalls)
                .append(" resident_interpolation_dispatches=").append(residentInterpolationDispatches)
                .append(" resident_interpolation_pixels=").append(residentInterpolationPixels)
                .append(" resident_interpolation_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentInterpolationBytes / 1024.0D))
                .append(" resident_current_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentCurrentBytes / 1024.0D))
                .append(" resident_peak_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentPeakBytes / 1024.0D))
                .append(" resident_memory_scope=renderer_lifetime_including_warmup_and_pending_retirement")
                .append(" resident_source_current_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentSourceBytes / 1024.0D))
                .append(" resident_source_peak_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentSourcePeakBytes / 1024.0D))
                .append(" resident_scratch_current_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentScratchBytes / 1024.0D))
                .append(" resident_scratch_peak_kib=")
                .append(String.format(Locale.ROOT, "%.3f", residentScratchPeakBytes / 1024.0D))
                .append(" resident_admitted=").append(residentAdmitted)
                .append(" resident_capacity_rejected=").append(residentCapacityRejected)
                .append(" resident_source_rejected=").append(residentSourceRejected)
                .append(" resident_staging_rejected=").append(residentStagingRejected)
                .append(" resident_invalidated=").append(residentInvalidated);
        for (Phase phase : PHASES) {
            int ordinal = phase.ordinal();
            line.append(' ').append(phase.label).append("_calls=").append(phaseCalls[ordinal]);
            line.append(' ').append(phase.label).append("_ms_avg=")
                    .append(String.format(Locale.ROOT, "%.3f", millis(phaseSums[ordinal] / ticks)));
            line.append(' ').append(phase.label).append("_ms_p95=")
                    .append(String.format(Locale.ROOT, "%.3f", millis(percentile(phaseSamples[ordinal], sampledTicks, 0.95D))));
        }
        PerformanceProfiler.benchmarkEvent(line.toString());
        reset();
    }

    private static long percentile(long[] values, int count, double percentile) {
        if (count <= 0) return 0L;
        System.arraycopy(values, 0, sortScratch, 0, count);
        Arrays.sort(sortScratch, 0, count);
        int index = (int)Math.ceil(percentile * count) - 1;
        return sortScratch[Math.max(0, Math.min(count - 1, index))];
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    private static void reset() {
        tickActive = false;
        activePhase = -1;
        currentOverlap = false;
        ticks = sampledTicks = uploadTicks = overlapTicks = 0;
        tickSumNanos = outerBatchSumNanos = 0L;
        currentCopyFlushNanos = currentOuterCopyFlushNanos = 0L;
        currentCopyFlushes = currentCopyRegions = currentOuterCopyFlushes = currentOuterCopyRegions = 0;
        copyFlushNanosSum = outerCopyFlushNanosSum = 0L;
        copyFlushes = copyRegions = outerCopyFlushes = outerCopyRegions = 0L;
        residentCopyCalls = residentCopyRegions = residentCopyBytes = 0L;
        spriteUploadRequests = cpuSpriteUploads = gpuSpriteUploads = 0L;
        standardInterpolationUpdates = standardInterpolationGpuUpdates = 0L;
        residentInterpolationCalls = residentInterpolationDispatches =
                residentInterpolationPixels = residentInterpolationBytes = 0L;
        Arrays.fill(currentPhaseNanos, 0L);
        Arrays.fill(phaseSums, 0L);
        Arrays.fill(phaseCalls, 0L);
    }

    public enum Phase {
        BATCH_START("batch_start", true),
        TICKABLE_LOOP("tickable_loop", false),
        BATCH_DRAIN("batch_drain", true),
        LAYOUT_TRANSITIONS("layout_transitions", true),
        QUEUE_SUBMIT("queue_submit", true);

        private final String label;
        private final boolean residualCorrection;

        Phase(String label, boolean residualCorrection) {
            this.label = label;
            this.residualCorrection = residualCorrection;
        }
    }
}
