package net.vulkanmod.render.profiling;

import java.util.Arrays;
import java.util.Locale;

/**
 * Automated-benchmark-only attribution for texture-tick work that sits outside
 * individual SpriteContents.upload() timers.
 *
 * <p>The existing ClientTickBreakdown remains authoritative for total texture and
 * sprite-upload time. This helper measures only the outer Vulkan upload-batch
 * lifecycle so the reported non-upload residual can be corrected without changing
 * texture cadence, bytes, synchronization, or command ownership.</p>
 */
public final class TextureTickAttribution {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");
    private static final int MAX_SAMPLES = 8192;
    private static final Phase[] PHASES = Phase.values();
    private static final int PHASE_COUNT = PHASES.length;

    private static final long[] currentPhaseNanos = ENABLED ? new long[PHASE_COUNT] : null;
    private static final long[] phaseSums = ENABLED ? new long[PHASE_COUNT] : null;
    private static final long[] phaseMax = ENABLED ? new long[PHASE_COUNT] : null;
    private static final long[] phaseCalls = ENABLED ? new long[PHASE_COUNT] : null;
    private static final long[][] phaseSamples = ENABLED ? new long[PHASE_COUNT][MAX_SAMPLES] : null;
    private static final long[] tickSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] outerBatchSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = ENABLED ? new long[MAX_SAMPLES] : null;

    private static boolean tickActive;
    private static int activePhase = -1;
    private static boolean currentOverlap;
    private static int ticks;
    private static int sampledTicks;
    private static int uploadTicks;
    private static int overlapTicks;
    private static long tickSumNanos;
    private static long tickMaxNanos;
    private static long outerBatchSumNanos;
    private static long outerBatchMaxNanos;

    private TextureTickAttribution() {
    }

    public static long beginTick() {
        if (!ENABLED || tickActive) return 0L;
        Arrays.fill(currentPhaseNanos, 0L);
        activePhase = -1;
        currentOverlap = false;
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
            outerBatch += value;
            phaseSums[ordinal] += value;
            phaseMax[ordinal] = Math.max(phaseMax[ordinal], value);
            if (sampledTicks < MAX_SAMPLES) phaseSamples[ordinal][sampledTicks] = value;
        }

        ticks++;
        if (outerBatch > 0L) uploadTicks++;
        if (currentOverlap) overlapTicks++;
        tickSumNanos += elapsed;
        tickMaxNanos = Math.max(tickMaxNanos, elapsed);
        outerBatchSumNanos += outerBatch;
        outerBatchMaxNanos = Math.max(outerBatchMaxNanos, outerBatch);
        if (sampledTicks < MAX_SAMPLES) {
            tickSamples[sampledTicks] = elapsed;
            outerBatchSamples[sampledTicks] = outerBatch;
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
                "texture_outer_batch_attribution ticks=%d sampled_ticks=%d sample_cap=%d upload_ticks=%d overlap_ticks=%d texture_tick_ms_avg=%.3f texture_tick_ms_p95=%.3f outer_batch_ms_avg=%.3f outer_batch_ms_p95=%.3f outer_batch_ms_max=%.3f outer_batch_ms_per_upload_tick=%.3f correction=client_tick_texture_detail.non_upload_ms_avg-minus-outer_batch_ms_avg",
                ticks, sampledTicks, MAX_SAMPLES, uploadTicks, overlapTicks,
                millis(tickSumNanos / ticks), millis(percentile(tickSamples, sampledTicks, 0.95D)),
                millis(outerBatchSumNanos / ticks), millis(percentile(outerBatchSamples, sampledTicks, 0.95D)),
                millis(outerBatchMaxNanos), uploadTicks == 0 ? 0.0D : millis(outerBatchSumNanos / uploadTicks)));
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
        tickSumNanos = tickMaxNanos = outerBatchSumNanos = outerBatchMaxNanos = 0L;
        Arrays.fill(currentPhaseNanos, 0L);
        Arrays.fill(phaseSums, 0L);
        Arrays.fill(phaseMax, 0L);
        Arrays.fill(phaseCalls, 0L);
    }

    public enum Phase {
        BATCH_START("batch_start"),
        BATCH_DRAIN("batch_drain"),
        LAYOUT_TRANSITIONS("layout_transitions"),
        QUEUE_SUBMIT("queue_submit");

        private final String label;

        Phase(String label) {
            this.label = label;
        }
    }
}
