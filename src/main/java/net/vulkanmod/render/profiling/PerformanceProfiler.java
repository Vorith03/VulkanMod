package net.vulkanmod.render.profiling;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.Window;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.WorldRenderer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * Low-overhead, opt-in render critical-path sampler.
 *
 * <p>The hot path uses only primitive fixed-size arrays and System.nanoTime().
 * File output, percentile sorting, JVM telemetry, and terrain/debug string creation
 * happen only at the configured summary boundary.</p>
 */
public final class PerformanceProfiler {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler");
    private static final String DEFAULT_OUTPUT = "logs/vulkanmod-performance.log";
    private static final String OUTPUT_FILE = ENABLED
            ? stringProperty("vulkanmod.performanceProfiler.output", DEFAULT_OUTPUT)
            : DEFAULT_OUTPUT;
    private static final int MAX_SAMPLES = ENABLED
            ? intProperty("vulkanmod.performanceProfiler.maxSamples", 4096, 128, 8192)
            : 0;
    private static final double SUMMARY_SECONDS = ENABLED
            ? doubleProperty("vulkanmod.performanceProfiler.summarySeconds", 5.0D, 0.25D, 300.0D)
            : 5.0D;
    private static final double DURATION_SECONDS = ENABLED
            ? doubleProperty("vulkanmod.performanceProfiler.durationSeconds", 0.0D, 0.0D, 86400.0D)
            : 0.0D;
    private static final double SLOW_FRAME_MS = ENABLED
            ? doubleProperty("vulkanmod.performanceProfiler.slowFrameMs", 25.0D, 1.0D, 1000.0D)
            : 25.0D;
    private static final long SUMMARY_NANOS = (long) (SUMMARY_SECONDS * 1_000_000_000.0D);
    private static final long DURATION_NANOS = (long) (DURATION_SECONDS * 1_000_000_000.0D);
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

    private static boolean active = ENABLED;
    private static boolean frameActive;
    private static boolean clientTickActive;
    private static boolean announced;
    private static long frameStartNanos;
    private static int frameStartWidth;
    private static int frameStartHeight;
    private static int framebufferFirstWidth;
    private static int framebufferFirstHeight;
    private static int framebufferLastWidth;
    private static int framebufferLastHeight;
    private static int framebufferMinWidth;
    private static int framebufferMaxWidth;
    private static int framebufferMinHeight;
    private static int framebufferMaxHeight;
    private static int framebufferChanges;
    private static boolean framebufferSampled;
    private static long captureStartNanos;
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
    private static BufferedWriter outputWriter;
    private static Path outputPath;

    static {
        if (Boolean.getBoolean("vulkanmod.smokeTest")) {
            verifyForCi();
        }
    }

    private PerformanceProfiler() {
    }

    public static boolean isEnabled() {
        return active;
    }

    /** Start one Minecraft runTick sample. Safe to call more than once before endFrame(). */
    public static void beginFrame() {
        if (!active || frameActive) {
            return;
        }

        long now = System.nanoTime();
        if (!announced && !startCapture(now)) {
            return;
        }

        Arrays.fill(currentStageNanos, 0L);
        frameStartNanos = now;
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft == null ? null : minecraft.getWindow();
        frameStartWidth = window == null ? -1 : window.getWidth();
        frameStartHeight = window == null ? -1 : window.getHeight();
        frameActive = true;
    }

    public static long begin(Stage stage) {
        if (!active || !frameActive || stage == null) {
            return 0L;
        }
        if (stage.tickDetail && !clientTickActive) return 0L;
        if (stage == Stage.CLIENT_TICK) clientTickActive = true;
        return System.nanoTime();
    }

    public static void end(Stage stage, long startNanos) {
        if (!active || !frameActive || stage == null || startNanos == 0L) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        currentStageNanos[stage.ordinal()] += elapsed;
        if (stage == Stage.CLIENT_TICK) clientTickActive = false;
    }

    /** Finish one runTick sample and emit a bounded periodic summary when due. */
    public static void endFrame() {
        if (!active || !frameActive) {
            return;
        }

        long now = System.nanoTime();
        long frameNanos = Math.max(0L, now - frameStartNanos);
        frameActive = false;
        clientTickActive = false;
        frameSequence++;

        if (sampleCount >= MAX_SAMPLES) {
            emitSummary(now);
        }

        // Sample both boundaries: the launcher size may change before world entry,
        // and a resize can also occur during the measured frame itself.
        recordFramebufferSample(frameStartWidth, frameStartHeight);
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft == null ? null : minecraft.getWindow();
        recordFramebufferSample(window == null ? -1 : window.getWidth(),
                window == null ? -1 : window.getHeight());

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

        boolean durationReached = DURATION_NANOS > 0L && now - captureStartNanos >= DURATION_NANOS;
        if (sampleCount >= MAX_SAMPLES || now - lastSummaryNanos >= SUMMARY_NANOS || durationReached) {
            emitSummary(now);
        }

        if (durationReached && active) {
            writeLine(String.format(Locale.ROOT,
                    "[VulkanModPerf] capture_complete duration_seconds=%.3f frames=%d",
                    (now - captureStartNanos) / 1_000_000_000.0D, frameSequence));
            flushOutput();
            closeOutput();
            active = false;
        }
    }

    private static boolean startCapture(long now) {
        try {
            outputPath = resolveOutputPath();
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            outputWriter = Files.newBufferedWriter(
                    outputPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException failure) {
            active = false;
            Initializer.LOGGER.error("VulkanMod performance profiling could not open output file '{}'; profiling disabled",
                    OUTPUT_FILE, failure);
            return false;
        }

        announced = true;
        captureStartNanos = now;
        lastSummaryNanos = now;
        lastGcCount = totalGcCount();
        lastGcMillis = totalGcMillis();

        String duration = DURATION_SECONDS > 0.0D
                ? String.format(Locale.ROOT, "%.3f", DURATION_SECONDS)
                : "unlimited";
        Minecraft minecraft = Minecraft.getInstance();
        int framebufferWidth = minecraft != null && minecraft.getWindow() != null
                ? minecraft.getWindow().getWidth()
                : -1;
        int framebufferHeight = minecraft != null && minecraft.getWindow() != null
                ? minecraft.getWindow().getHeight()
                : -1;
        Initializer.LOGGER.info("VulkanMod performance profiling enabled; output: {}", outputPath.toAbsolutePath());
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] capture_start summary_seconds=%.3f duration_seconds=%s slow_frame_ms=%.3f max_samples=%d initial_framebuffer_px=%dx%d cpu_wall_clock=true gpu_timestamps=false",
                SUMMARY_SECONDS, duration, SLOW_FRAME_MS, MAX_SAMPLES, framebufferWidth, framebufferHeight));
        flushOutput();
        return active;
    }

    private static Path resolveOutputPath() {
        Path configured = Path.of(OUTPUT_FILE);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Minecraft minecraft = Minecraft.getInstance();
        Path gameDirectory = minecraft != null && minecraft.gameDirectory != null
                ? minecraft.gameDirectory.toPath()
                : Path.of("").toAbsolutePath();
        return gameDirectory.resolve(configured).normalize();
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

        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] window frames=%d frame_ms avg=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f slow_threshold_ms=%.3f slow_frames=%d framebuffer_first_px=%dx%d framebuffer_last_px=%dx%d framebuffer_width_range=%d-%d framebuffer_height_range=%d-%d framebuffer_changes=%d",
                count, millis(frameAvg), millis(frameP50), millis(frameP95), millis(frameP99),
                millis(frameMaxNanos), SLOW_FRAME_MS, slowFrames,
                framebufferFirstWidth, framebufferFirstHeight,
                framebufferLastWidth, framebufferLastHeight,
                framebufferMinWidth, framebufferMaxWidth,
                framebufferMinHeight, framebufferMaxHeight, framebufferChanges));

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
        long tickDetail = 0L;
        for (Stage stage : STAGES) if (stage.tickDetail) tickDetail += stageSums[stage.ordinal()];
        appendMetric(avg, "client_tick_other", Math.max(0L,
                (stageSums[Stage.CLIENT_TICK.ordinal()] - tickDetail) / count));
        appendMetric(avg, "game_render_other", Math.max(0L,
                (stageSums[Stage.GAME_RENDER.ordinal()]
                        - stageSums[Stage.WORLD_RENDER.ordinal()]) / count));
        appendMetric(avg, "world_render_other", Math.max(0L,
                (stageSums[Stage.WORLD_RENDER.ordinal()]
                        - stageSums[Stage.TERRAIN_SETUP.ordinal()]
                        - stageSums[Stage.TERRAIN_UPLOADS.ordinal()]) / count));
        writeLine(avg.toString());
        writeLine(p95.toString());
        writeLine(max.toString());

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
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] worst frame_id=%d total_ms=%.3f top_known=%s top_known_ms=%.3f unaccounted_ms=%.3f",
                worstFrameId, millis(worstFrameNanos),
                worstKnownStage == null ? "none" : worstKnownStage.label,
                millis(worstKnownNanos), millis(Math.max(0L, worstFrameNanos - worstAccounted))));

        long gcCount = totalGcCount();
        long gcMillis = totalGcMillis();
        long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] jvm gc_count_delta=%d gc_ms_delta=%d heap_used_mib=%.1f",
                Math.max(0L, gcCount - lastGcCount), Math.max(0L, gcMillis - lastGcMillis),
                heapUsed / (1024.0D * 1024.0D)));
        lastGcCount = gcCount;
        lastGcMillis = gcMillis;

        try {
            WorldRenderer renderer = WorldRenderer.getInstance();
            if (renderer != null && renderer.getLevel() != null) {
                writeLine("[VulkanModPerf] terrain " + renderer.getChunkStatistics());
            }
        } catch (RuntimeException diagnosticFailure) {
            writeLine("[VulkanModPerf] terrain_unavailable exception=" + diagnosticFailure.getClass().getSimpleName());
        }

        flushOutput();

        sampleCount = 0;
        slowFrames = 0;
        frameSumNanos = 0L;
        frameMaxNanos = 0L;
        worstFrameId = 0L;
        worstFrameNanos = 0L;
        Arrays.fill(stageSums, 0L);
        Arrays.fill(stageMax, 0L);
        Arrays.fill(worstStageNanos, 0L);
        framebufferSampled = false;
        framebufferChanges = 0;
        lastSummaryNanos = now;
    }

    private static void recordFramebufferSample(int width, int height) {
        if (!framebufferSampled) {
            framebufferFirstWidth = framebufferLastWidth = framebufferMinWidth = framebufferMaxWidth = width;
            framebufferFirstHeight = framebufferLastHeight = framebufferMinHeight = framebufferMaxHeight = height;
            framebufferSampled = true;
            return;
        }
        if (width != framebufferLastWidth || height != framebufferLastHeight)
            framebufferChanges++;
        framebufferLastWidth = width;
        framebufferLastHeight = height;
        framebufferMinWidth = Math.min(framebufferMinWidth, width);
        framebufferMaxWidth = Math.max(framebufferMaxWidth, width);
        framebufferMinHeight = Math.min(framebufferMinHeight, height);
        framebufferMaxHeight = Math.max(framebufferMaxHeight, height);
    }

    private static void writeLine(String line) {
        if (!active || outputWriter == null) {
            return;
        }
        try {
            outputWriter.write(line);
            outputWriter.newLine();
        } catch (IOException failure) {
            disableAfterOutputFailure(failure);
        }
    }

    private static void flushOutput() {
        if (!active || outputWriter == null) {
            return;
        }
        try {
            outputWriter.flush();
        } catch (IOException failure) {
            disableAfterOutputFailure(failure);
        }
    }

    private static void disableAfterOutputFailure(IOException failure) {
        active = false;
        frameActive = false;
        Initializer.LOGGER.error("VulkanMod performance profiling output failed; profiling disabled", failure);
        closeOutput();
    }

    private static void closeOutput() {
        BufferedWriter writer = outputWriter;
        outputWriter = null;
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        }
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

    private static String stringProperty(String key, String fallback) {
        String value = System.getProperty(key, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Lightweight startup contract exercised automatically by smoke-test launches. */
    public static void verifyForCi() {
        if (Stage.fromLegacyName("Frame_fence") != Stage.FRAME_FENCE_WAIT
                || Stage.fromLegacyName("Frame_ops") != Stage.FRAME_OPS
                || Stage.fromLegacyName("Setup_Renderer") != Stage.TERRAIN_SETUP
                || Stage.fromLegacyName("reposition") != Stage.TERRAIN_REPOSITION
                || Stage.fromLegacyName("Uploads") != Stage.TERRAIN_UPLOADS
                || Stage.fromLegacyName("submitRender") != Stage.SUBMIT_RENDER
                || !Stage.TERRAIN_SETUP.nested
                || !Stage.TERRAIN_REPOSITION.nested
                || !Stage.TERRAIN_UPLOADS.nested
                || !Stage.CLIENT_LEVEL_TICK.nested
                || !Stage.CLIENT_LEVEL_TICK.tickDetail
                || Stage.CLIENT_TICK.tickDetail
                || Stage.GAME_RENDER.nested
                || Stage.FRAME_FENCE_WAIT.nested) {
            throw new IllegalStateException("Performance profiler stage contract is invalid");
        }
    }

    public enum Stage {
        FRAME_SLOT_WAIT("frame_slot_wait", false),
        FRAME_FENCE_WAIT("frame_fence_wait", false),
        FRAME_OPS("frame_ops", false),
        CLIENT_TICK("client_tick", false),
        CLIENT_LEVEL_TICK("client_level_tick", true, true),
        CLIENT_ENTITIES_TICK("client_entities_tick", true, true),
        CLIENT_RENDERER_TICK("client_renderer_tick", true, true),
        CLIENT_CONNECTION_TICK("client_connection_tick", true, true),
        GAME_RENDER("game_render", false),
        WORLD_RENDER("world_render", true),
        TERRAIN_SETUP("terrain_setup", true),
        TERRAIN_REPOSITION("terrain_reposition", true),
        TERRAIN_UPLOADS("terrain_uploads", true),
        SUBMIT_RENDER("submit_render", false),
        DISPLAY_UPDATE("display_update", false),
        FRAME_LIMIT("frame_limit", false);

        private final String label;
        private final boolean nested;
        private final boolean tickDetail;

        Stage(String label, boolean nested) {
            this(label, nested, false);
        }

        Stage(String label, boolean nested, boolean tickDetail) {
            this.label = label;
            this.nested = nested;
            this.tickDetail = tickDetail;
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
