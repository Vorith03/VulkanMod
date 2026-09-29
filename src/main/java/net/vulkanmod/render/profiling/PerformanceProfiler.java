package net.vulkanmod.render.profiling;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.WorldRenderer;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/**
 * Low-overhead, opt-in render critical-path sampler.
 *
 * <p>The hot path uses only primitive fixed-size arrays and System.nanoTime().
 * Logging, percentile sorting, JVM telemetry, and terrain/debug string creation
 * happen only at the configured summary boundary.</p>
 */
public final class PerformanceProfiler {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler");
    private static final int MAX_SAMPLES = ENABLED
            ? intProperty("vulkanmod.performanceProfiler.maxSamples", 4096, 128, 8192)
            : 0;
    private static final double SUMMARY_SECONDS = ENABLED
            ? doubleProperty("vulkanmod.performanceProfiler.summarySeconds", 5.0D, 1.0D, 60.0D)
            : 5.0D;
    private static final double SLOW_FRAME_MS = ENABLED
            ? doubleProperty("vulkanmod.performanceProfiler.slowFrameMs", 25.0D, 1.0D, 1000.0D)
            : 25.0D;
    private static final long SUMMARY_NANOS = (long) (SUMMARY_SECONDS * 1_000_000_000.0D);
    private static final long SLOW_FRAME_NANOS = (long) (SLOW_FRAME_MS * 1_000_000.0D);

    private static final Stage[] STAGES = Stage.values();
    private static final int STAGE_COUNT = STAGES.length;

    private static final long[] currentStageNanos = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] stageSums = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] stageMax = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[][] stageSamples = ENABLED ? new long[STAGE_COUNT][MAX_SAMPLES] : null;
    private static final long[] frameSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] worstStageNanos = ENABLED ? new long[STAGE_COUNT] : null;

    private static boolean frameActive;
    private static boolean announced;
    private static long frameStartNanos;
    private static long lastSummaryNanos;
    private static long frameSequence;
    private static int sampleCount;
    private static int slowFrames;
    private static long frameSumNanos;
    private static long frameMaxNanos;
    private static long worstFrameId;
    private static long worstFrameNanos;
    private static long lastGcCount;
    private static long lastGcMillis;

    static {
        if (Boolean.getBoolean("vulkanmod.smokeTest")) {
            verifyForCi();
        }
    }

    private PerformanceProfiler() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** Start one Minecraft runTick sample. Safe to call more than once before endFrame(). */
    public static void beginFrame() {
        if (!ENABLED || frameActive) {
            return;
        }

        long now = System.nanoTime();
        Arrays.fill(currentStageNanos, 0L);
        frameStartNanos = now;
        frameActive = true;

        if (!announced) {
            announced = true;
            lastSummaryNanos = now;
            lastGcCount = totalGcCount();
            lastGcMillis = totalGcMillis();
            Initializer.LOGGER.info(
                    "[VulkanModPerf] enabled summary_seconds={} slow_frame_ms={} max_samples={} (CPU wall-clock critical path; no GPU timestamps yet)",
                    SUMMARY_SECONDS, SLOW_FRAME_MS, MAX_SAMPLES);
        }
    }

    public static long begin(Stage stage) {
        if (!ENABLED || !frameActive || stage == null) {
            return 0L;
        }
        return System.nanoTime();
    }

    public static void end(Stage stage, long startNanos) {
        if (!ENABLED || !frameActive || stage == null || startNanos == 0L) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        currentStageNanos[stage.ordinal()] += elapsed;
    }

    /** Finish one runTick sample and emit a bounded periodic summary when due. */
    public static void endFrame() {
        if (!ENABLED || !frameActive) {
            return;
        }

        long now = System.nanoTime();
        long frameNanos = Math.max(0L, now - frameStartNanos);
        frameActive = false;
        frameSequence++;

        if (sampleCount >= MAX_SAMPLES) {
            emitSummary(now);
        }

        int index = sampleCount++;
        frameSamples[index] = frameNanos;
        frameSumNanos += frameNanos;
        frameMaxNanos = Math.max(frameMaxNanos, frameNanos);
        if (frameNanos >= SLOW_FRAME_NANOS) {
            slowFrames++;
        }

        for (Stage stage : STAGES) {
            int ordinal = stage.ordinal();
            long value = currentStageNanos[ordinal];
            stageSamples[ordinal][index] = value;
            stageSums[ordinal] += value;
            stageMax[ordinal] = Math.max(stageMax[ordinal], value);
        }

        if (frameNanos > worstFrameNanos) {
            worstFrameNanos = frameNanos;
            worstFrameId = frameSequence;
            System.arraycopy(currentStageNanos, 0, worstStageNanos, 0, STAGE_COUNT);
        }

        if (sampleCount >= MAX_SAMPLES || now - lastSummaryNanos >= SUMMARY_NANOS) {
            emitSummary(now);
        }
    }

    private static void emitSummary(long now) {
        int count = sampleCount;
        if (count <= 0) {
            lastSummaryNanos = now;
            return;
        }

        long frameAvg = frameSumNanos / count;
        long frameP50 = percentile(frameSamples, count, 0.50D);
        long frameP95 = percentile(frameSamples, count, 0.95D);
        long frameP99 = percentile(frameSamples, count, 0.99D);

        Initializer.LOGGER.info(String.format(Locale.ROOT,
                "[VulkanModPerf] window frames=%d frame_ms avg=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f slow_threshold_ms=%.3f slow_frames=%d",
                count, millis(frameAvg), millis(frameP50), millis(frameP95), millis(frameP99),
                millis(frameMaxNanos), SLOW_FRAME_MS, slowFrames));

        long accountedAvg = 0L;
        StringBuilder avg = new StringBuilder("[VulkanModPerf] stage_avg_ms");
        StringBuilder p95 = new StringBuilder("[VulkanModPerf] stage_p95_ms");
        StringBuilder max = new StringBuilder("[VulkanModPerf] stage_max_ms");
        for (Stage stage : STAGES) {
            int ordinal = stage.ordinal();
            long average = stageSums[ordinal] / count;
            if (!stage.nested) {
                accountedAvg += average;
            }
            appendMetric(avg, stage.label, average);
            appendMetric(p95, stage.label, percentile(stageSamples[ordinal], count, 0.95D));
            appendMetric(max, stage.label, stageMax[ordinal]);
        }
        appendMetric(avg, "unaccounted", Math.max(0L, frameAvg - accountedAvg));
        Initializer.LOGGER.info(avg.toString());
        Initializer.LOGGER.info(p95.toString());
        Initializer.LOGGER.info(max.toString());

        long worstAccounted = 0L;
        Stage worstKnownStage = null;
        long worstKnownNanos = 0L;
        for (Stage stage : STAGES) {
            long value = worstStageNanos[stage.ordinal()];
            if (!stage.nested) {
                worstAccounted += value;
            }
            if (value > worstKnownNanos) {
                worstKnownNanos = value;
                worstKnownStage = stage;
            }
        }
        Initializer.LOGGER.info(String.format(Locale.ROOT,
                "[VulkanModPerf] worst frame_id=%d total_ms=%.3f top_known=%s top_known_ms=%.3f unaccounted_ms=%.3f",
                worstFrameId, millis(worstFrameNanos),
                worstKnownStage == null ? "none" : worstKnownStage.label,
                millis(worstKnownNanos), millis(Math.max(0L, worstFrameNanos - worstAccounted))));

        long gcCount = totalGcCount();
        long gcMillis = totalGcMillis();
        long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        Initializer.LOGGER.info(String.format(Locale.ROOT,
                "[VulkanModPerf] jvm gc_count_delta=%d gc_ms_delta=%d heap_used_mib=%.1f",
                Math.max(0L, gcCount - lastGcCount), Math.max(0L, gcMillis - lastGcMillis),
                heapUsed / (1024.0D * 1024.0D)));
        lastGcCount = gcCount;
        lastGcMillis = gcMillis;

        try {
            WorldRenderer renderer = WorldRenderer.getInstance();
            if (renderer != null && renderer.getLevel() != null) {
                Initializer.LOGGER.info("[VulkanModPerf] terrain {}", renderer.getChunkStatistics());
            }
        } catch (RuntimeException diagnosticFailure) {
            Initializer.LOGGER.debug("[VulkanModPerf] terrain snapshot unavailable", diagnosticFailure);
        }

        sampleCount = 0;
        slowFrames = 0;
        frameSumNanos = 0L;
        frameMaxNanos = 0L;
        worstFrameId = 0L;
        worstFrameNanos = 0L;
        Arrays.fill(stageSums, 0L);
        Arrays.fill(stageMax, 0L);
        Arrays.fill(worstStageNanos, 0L);
        lastSummaryNanos = now;
    }

    private static void appendMetric(StringBuilder builder, String label, long nanos) {
        builder.append(' ').append(label).append('=')
                .append(String.format(Locale.ROOT, "%.3f", millis(nanos)));
    }

    private static long percentile(long[] values, int count, double percentile) {
        System.arraycopy(values, 0, sortScratch, 0, count);
        Arrays.sort(sortScratch, 0, count);
        int index = (int) Math.ceil(percentile * count) - 1;
        index = Math.max(0, Math.min(count - 1, index));
        return sortScratch[index];
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    private static long totalGcCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionCount();
            if (value > 0L) {
                total += value;
            }
        }
        return total;
    }

    private static long totalGcMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionTime();
            if (value > 0L) {
                total += value;
            }
        }
        return total;
    }

    private static int intProperty(String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(System.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double doubleProperty(String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(System.getProperty(key, Double.toString(fallback)));
            if (!Double.isFinite(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Lightweight startup contract exercised automatically by smoke-test launches. */
    public static void verifyForCi() {
        if (Stage.fromLegacyName("Frame_fence") != Stage.FRAME_FENCE_WAIT
                || Stage.fromLegacyName("Frame_ops") != Stage.FRAME_OPS
                || Stage.fromLegacyName("Setup_Renderer") != Stage.TERRAIN_SETUP
                || Stage.fromLegacyName("reposition") != Stage.TERRAIN_REPOSITION
                || Stage.fromLegacyName("Uploads") != Stage.TERRAIN_UPLOADS
                || Stage.fromLegacyName("submitRender") != Stage.SUBMIT_RENDER
                || !Stage.TERRAIN_REPOSITION.nested
                || Stage.FRAME_FENCE_WAIT.nested) {
            throw new IllegalStateException("Performance profiler stage contract is invalid");
        }
    }

    public enum Stage {
        FRAME_SLOT_WAIT("frame_slot_wait", false),
        FRAME_FENCE_WAIT("frame_fence_wait", false),
        FRAME_OPS("frame_ops", false),
        TERRAIN_SETUP("terrain_setup", false),
        TERRAIN_REPOSITION("terrain_reposition", true),
        TERRAIN_UPLOADS("terrain_uploads", false),
        SUBMIT_RENDER("submit_render", false);

        private final String label;
        private final boolean nested;

        Stage(String label, boolean nested) {
            this.label = label;
            this.nested = nested;
        }

        public static Stage fromLegacyName(String name) {
            return switch (name) {
                case "Frame_fence" -> FRAME_FENCE_WAIT;
                case "Frame_ops" -> FRAME_OPS;
                case "Setup_Renderer" -> TERRAIN_SETUP;
                case "reposition" -> TERRAIN_REPOSITION;
                case "Uploads" -> TERRAIN_UPLOADS;
                case "submitRender" -> SUBMIT_RENDER;
                default -> null;
            };
        }
    }
}
