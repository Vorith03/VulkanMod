package net.vulkanmod.render.profiling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.platform.Window;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.GpuTerrainDiagnostics;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.vulkan.Vulkan;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
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
    private static final long[] tickFrameSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] renderOnlyFrameSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] loopGapSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final int[] tickCallsPerFrame = ENABLED ? new int[MAX_SAMPLES] : null;
    private static final long[] tickDetailUnionSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] worstStageNanos = ENABLED ? new long[STAGE_COUNT] : null;

    private static boolean active = ENABLED;
    private static boolean frameActive;
    private static boolean clientTickActive;
    private static boolean announced;
    private static long frameStartNanos;
    private static ClientLevel frameStartLevel;
    private static ClientLevel windowLevel;
    // 0 = menu, 1 = world, 2 = transition (the level changed within a frame).
    private static int windowContext = -1;
    private static long windowStartNanos;
    private static long lastRecordedFrameEndNanos;
    private static long frameGapOriginNanos;
    private static long currentFrameGapNanos;
    private static long captureStartEpochMillis;
    private static long windowSequence;
    private static int currentTickCalls;
    private static int tickDetailDepth;
    private static long tickDetailUnionStartNanos;
    private static long currentTickDetailUnionNanos;
    private static int tickFrames;
    private static int renderOnlyFrames;
    private static int tickCalls;
    private static int tickSlowFrames;
    private static int renderOnlySlowFrames;
    private static long tickFrameSumNanos;
    private static long renderOnlyFrameSumNanos;
    private static long tickFrameMaxNanos;
    private static long renderOnlyFrameMaxNanos;
    private static long loopGapSumNanos;
    private static long loopGapMaxNanos;
    private static int loopGapSamplesCount;
    private static int tickDetailOverlapFrames;
    private static int tickDetailUnbalancedFrames;
    private static int worldDetailOverlapFrames;
    private static int topLevelOverlapFrames;
    private static long topLevelExcessNanos;
    private static long topLevelMaxExcessNanos;
    private static double positionFirstX, positionFirstY, positionFirstZ;
    private static double positionLastX, positionLastY, positionLastZ;
    private static float rotationLastYaw, rotationLastPitch;
    private static int positionSamples, poseChangedFrames;
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
    private static long lastRenderThreadCpuNanos = -1L;
    private static long lastRenderThreadAllocatedBytes = -1L;
    private static WorldRenderer lastTerrainRenderer;
    private static WorldRenderer.PerformanceCounters lastTerrainCounters;
    private static long lastStagingRejected;
    private static long lastPreflightFull;
    private static long lastPublishRejected;
    private static long lastCpuRecovery;
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
        if (!announced) {
            if (!startCapture(now)) return;
            // Opening/flushing the capture file is setup, not part of the first frame.
            now = System.nanoTime();
        }

        Arrays.fill(currentStageNanos, 0L);
        currentTickCalls = 0;
        tickDetailDepth = 0;
        currentTickDetailUnionNanos = 0L;
        currentFrameGapNanos = frameGapOriginNanos == 0L ? -1L
                : Math.max(0L, now - frameGapOriginNanos);
        frameStartNanos = now;
        Minecraft minecraft = Minecraft.getInstance();
        frameStartLevel = minecraft == null ? null : minecraft.level;
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
        long startNanos = System.nanoTime();
        if (stage == Stage.CLIENT_TICK) {
            clientTickActive = true;
            currentTickCalls++;
        }
        if (stage.tickDetail && tickDetailDepth++ == 0)
            tickDetailUnionStartNanos = startNanos;
        return startNanos;
    }

    public static void end(Stage stage, long startNanos) {
        if (!active || !frameActive || stage == null || startNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        long elapsed = Math.max(0L, now - startNanos);
        currentStageNanos[stage.ordinal()] += elapsed;
        if (stage.tickDetail && tickDetailDepth > 0 && --tickDetailDepth == 0)
            currentTickDetailUnionNanos += Math.max(0L, now - tickDetailUnionStartNanos);
        if (stage == Stage.CLIENT_TICK) clientTickActive = false;
    }

    /** Finish one runTick sample and emit a bounded periodic summary when due. */
    public static void endFrame() {
        if (!active || !frameActive) {
            return;
        }

        long now = System.nanoTime();
        long frameNanos = Math.max(0L, now - frameStartNanos);
        boolean unbalancedTickDetail = tickDetailDepth > 0;
        if (unbalancedTickDetail) {
            currentTickDetailUnionNanos += Math.max(0L, now - tickDetailUnionStartNanos);
            tickDetailDepth = 0;
        }
        frameActive = false;
        clientTickActive = false;
        frameSequence++;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel endLevel = minecraft == null ? null : minecraft.level;
        int context = frameStartLevel != endLevel ? 2 : endLevel == null ? 0 : 1;
        if (sampleCount > 0 && (context != windowContext || context == 1 && endLevel != windowLevel)) {
            emitSummary(lastRecordedFrameEndNanos);
            // Do not charge a cross-context gap to either steady-state window.
            currentFrameGapNanos = -1L;
        }

        if (sampleCount >= MAX_SAMPLES) {
            emitSummary(now);
        }

        if (sampleCount == 0) {
            windowContext = context;
            windowLevel = context == 1 ? endLevel : null;
            windowStartNanos = frameStartNanos;
        }

        // Sample both boundaries: the launcher size may change before world entry,
        // and a resize can also occur during the measured frame itself.
        recordFramebufferSample(frameStartWidth, frameStartHeight);
        Window window = minecraft == null ? null : minecraft.getWindow();
        recordFramebufferSample(window == null ? -1 : window.getWidth(),
                window == null ? -1 : window.getHeight());

        int index = sampleCount++;
        if (unbalancedTickDetail) tickDetailUnbalancedFrames++;
        lastRecordedFrameEndNanos = now;
        frameSamples[index] = frameNanos;
        if (currentFrameGapNanos >= 0L) {
            loopGapSamples[loopGapSamplesCount++] = currentFrameGapNanos;
            loopGapSumNanos += currentFrameGapNanos;
            loopGapMaxNanos = Math.max(loopGapMaxNanos, currentFrameGapNanos);
        }
        tickCallsPerFrame[index] = currentTickCalls;
        tickDetailUnionSamples[index] = currentTickDetailUnionNanos;
        if (currentTickCalls > 0) {
            tickFrameSamples[tickFrames++] = frameNanos;
            tickCalls += currentTickCalls;
            tickFrameSumNanos += frameNanos;
            tickFrameMaxNanos = Math.max(tickFrameMaxNanos, frameNanos);
            if (frameNanos >= SLOW_FRAME_NANOS) tickSlowFrames++;
        } else {
            renderOnlyFrameSamples[renderOnlyFrames++] = frameNanos;
            renderOnlyFrameSumNanos += frameNanos;
            renderOnlyFrameMaxNanos = Math.max(renderOnlyFrameMaxNanos, frameNanos);
            if (frameNanos >= SLOW_FRAME_NANOS) renderOnlySlowFrames++;
        }
        if (context == 1 && minecraft.player != null) {
            double x = minecraft.player.getX();
            double y = minecraft.player.getY();
            double z = minecraft.player.getZ();
            float yaw = minecraft.player.getYRot();
            float pitch = minecraft.player.getXRot();
            if (positionSamples++ == 0) {
                positionFirstX = x;
                positionFirstY = y;
                positionFirstZ = z;
            } else if (x != positionLastX || y != positionLastY || z != positionLastZ
                    || yaw != rotationLastYaw || pitch != rotationLastPitch) {
                poseChangedFrames++;
            }
            positionLastX = x;
            positionLastY = y;
            positionLastZ = z;
            rotationLastYaw = yaw;
            rotationLastPitch = pitch;
        }
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
        long tickDetailNanos = currentStageNanos[Stage.CLIENT_LEVEL_TICK.ordinal()]
                + currentStageNanos[Stage.CLIENT_ENTITIES_TICK.ordinal()]
                + currentStageNanos[Stage.CLIENT_RENDERER_TICK.ordinal()]
                + currentStageNanos[Stage.CLIENT_CONNECTION_TICK.ordinal()];
        if (tickDetailNanos > currentTickDetailUnionNanos) tickDetailOverlapFrames++;
        long worldDetailNanos = currentStageNanos[Stage.TERRAIN_SETUP.ordinal()]
                + currentStageNanos[Stage.TERRAIN_UPLOADS.ordinal()]
                + currentStageNanos[Stage.TERRAIN_DRAW.ordinal()]
                + currentStageNanos[Stage.BLOCK_ENTITY_RENDER.ordinal()];
        if (worldDetailNanos > currentStageNanos[Stage.WORLD_RENDER.ordinal()]) worldDetailOverlapFrames++;
        long topLevelNanos = 0L;
        for (Stage stage : STAGES) if (!stage.nested) topLevelNanos += currentStageNanos[stage.ordinal()];
        if (topLevelNanos > frameNanos) {
            topLevelOverlapFrames++;
            long excess = topLevelNanos - frameNanos;
            topLevelExcessNanos += excess;
            topLevelMaxExcessNanos = Math.max(topLevelMaxExcessNanos, excess);
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
        // Exclude both per-frame bookkeeping and periodic I/O from the next gap.
        frameGapOriginNanos = System.nanoTime();
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
        captureStartEpochMillis = System.currentTimeMillis();
        lastSummaryNanos = now;
        lastGcCount = totalGcCount();
        lastGcMillis = totalGcMillis();
        lastRenderThreadCpuNanos = renderThreadCpuNanos();
        lastRenderThreadAllocatedBytes = renderThreadAllocatedBytes();

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
        String deviceName = Vulkan.getDeviceInfo() == null ? "unknown"
                : Vulkan.getDeviceInfo().deviceName.replace(' ', '_');
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] environment java=%s os=%s cpus=%d vulkan_gpu=%s voxel_staging=%s gpu_mesher_prop=%s cpu_bypass_prop=%s draw_handoff_prop=%s hybrid_prop=%s",
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown").replace(' ', '_'),
                Runtime.getRuntime().availableProcessors(), deviceName,
                RegionVoxelStore.ENABLED,
                Boolean.getBoolean("vulkanmod.experimentalGpuTerrainMesher"),
                Boolean.getBoolean("vulkanmod.experimentalGpuTerrainCpuBypass"),
                Boolean.getBoolean("vulkanmod.experimentalGpuTerrainDrawHandoff"),
                Boolean.getBoolean("vulkanmod.experimentalGpuTerrainHybrid")));
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

        String contextName = windowContext == 1 ? "world" : windowContext == 0 ? "menu" : "transition";
        String dimension = windowLevel == null ? "none" : windowLevel.dimension().location().toString();
        Minecraft minecraft = Minecraft.getInstance();
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] window id=%d context=%s dimension=%s start_epoch_ms=%d end_epoch_ms=%d since_capture_s=%.3f duration_s=%.3f frames=%d frame_ms avg=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f slow_threshold_ms=%.3f slow_frames=%d framebuffer_first_px=%dx%d framebuffer_last_px=%dx%d framebuffer_width_range=%d-%d framebuffer_height_range=%d-%d framebuffer_changes=%d render_distance=%d simulation_distance=%d vsync=%s fps_cap=%d",
                ++windowSequence, contextName, dimension,
                captureStartEpochMillis + (windowStartNanos - captureStartNanos) / 1_000_000L,
                captureStartEpochMillis + (now - captureStartNanos) / 1_000_000L,
                (now - captureStartNanos) / 1_000_000_000.0D,
                (now - windowStartNanos) / 1_000_000_000.0D,
                count, millis(frameAvg), millis(frameP50), millis(frameP95), millis(frameP99),
                millis(frameMaxNanos), SLOW_FRAME_MS, slowFrames,
                framebufferFirstWidth, framebufferFirstHeight,
                framebufferLastWidth, framebufferLastHeight,
                framebufferMinWidth, framebufferMaxWidth,
                framebufferMinHeight, framebufferMaxHeight, framebufferChanges,
                minecraft == null ? -1 : minecraft.options.getEffectiveRenderDistance(),
                minecraft == null ? -1 : minecraft.options.simulationDistance().get(),
                minecraft != null && minecraft.options.enableVsync().get(),
                minecraft == null ? -1 : minecraft.options.framerateLimit().get()));

        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] frame_classes tick_frames=%d tick_calls=%d tick_frame_ms avg=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f slow=%d render_only_frames=%d render_only_frame_ms avg=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f slow=%d",
                tickFrames, tickCalls, millis(tickFrames == 0 ? 0L : tickFrameSumNanos / tickFrames),
                millis(percentile(tickFrameSamples, tickFrames, 0.50D)),
                millis(percentile(tickFrameSamples, tickFrames, 0.95D)),
                millis(percentile(tickFrameSamples, tickFrames, 0.99D)),
                millis(tickFrameMaxNanos), tickSlowFrames,
                renderOnlyFrames, millis(renderOnlyFrames == 0 ? 0L : renderOnlyFrameSumNanos / renderOnlyFrames),
                millis(percentile(renderOnlyFrameSamples, renderOnlyFrames, 0.50D)),
                millis(percentile(renderOnlyFrameSamples, renderOnlyFrames, 0.95D)),
                millis(percentile(renderOnlyFrameSamples, renderOnlyFrames, 0.99D)),
                millis(renderOnlyFrameMaxNanos), renderOnlySlowFrames));
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] loop_gap samples=%d gap_ms avg=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f summary_io_excluded=true",
                loopGapSamplesCount, millis(loopGapSamplesCount == 0 ? 0L : loopGapSumNanos / loopGapSamplesCount),
                millis(percentile(loopGapSamples, loopGapSamplesCount, 0.50D)),
                millis(percentile(loopGapSamples, loopGapSamplesCount, 0.95D)),
                millis(percentile(loopGapSamples, loopGapSamplesCount, 0.99D)),
                millis(loopGapMaxNanos)));
        if (positionSamples > 0) {
            writeLine(String.format(Locale.ROOT,
                    "[VulkanModPerf] player_pose first_xyz=%.3f,%.3f,%.3f last_xyz=%.3f,%.3f,%.3f last_yaw_pitch=%.2f,%.2f changed_frames=%d samples=%d",
                    positionFirstX, positionFirstY, positionFirstZ,
                    positionLastX, positionLastY, positionLastZ,
                    rotationLastYaw, rotationLastPitch, poseChangedFrames, positionSamples));
        }

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
        for (int i = 0; i < count; i++) tickDetail += tickDetailUnionSamples[i];
        appendMetric(avg, "client_tick_other", Math.max(0L,
                (stageSums[Stage.CLIENT_TICK.ordinal()] - tickDetail) / count));
        appendMetric(avg, "game_render_other", Math.max(0L,
                (stageSums[Stage.GAME_RENDER.ordinal()]
                        - stageSums[Stage.WORLD_RENDER.ordinal()]
                        - stageSums[Stage.HUD_RENDER.ordinal()]) / count));
        appendMetric(avg, "world_render_other", Math.max(0L,
                (stageSums[Stage.WORLD_RENDER.ordinal()]
                        - stageSums[Stage.TERRAIN_SETUP.ordinal()]
                        - stageSums[Stage.TERRAIN_UPLOADS.ordinal()]
                        - stageSums[Stage.TERRAIN_DRAW.ordinal()]
                        - stageSums[Stage.BLOCK_ENTITY_RENDER.ordinal()]) / count));
        writeLine(avg.toString());
        writeLine(p95.toString());
        writeLine(max.toString());

        StringBuilder tickAvg = new StringBuilder("[VulkanModPerf] tick_stage_avg_ms");
        StringBuilder tickP95 = new StringBuilder("[VulkanModPerf] tick_stage_p95_ms");
        for (Stage stage : STAGES) {
            if (stage != Stage.CLIENT_TICK && !stage.tickDetail) continue;
            long sum = 0L;
            for (int i = 0; i < count; i++) if (tickCallsPerFrame[i] > 0)
                sum += stageSamples[stage.ordinal()][i];
            appendMetric(tickAvg, stage.label, tickFrames == 0 ? 0L : sum / tickFrames);
            appendMetric(tickP95, stage.label, tickPercentile(stage, count, 0.95D));
        }
        long otherSum = 0L;
        int otherCount = 0;
        for (int i = 0; i < count; i++) {
            if (tickCallsPerFrame[i] == 0) continue;
            long other = stageSamples[Stage.CLIENT_TICK.ordinal()][i];
            other -= tickDetailUnionSamples[i];
            other = Math.max(0L, other);
            otherSum += other;
            sortScratch[otherCount++] = other;
        }
        appendMetric(tickAvg, "client_tick_other", tickFrames == 0 ? 0L : otherSum / tickFrames);
        Arrays.sort(sortScratch, 0, otherCount);
        appendMetric(tickP95, "client_tick_other", sortedPercentile(sortScratch, otherCount, 0.95D));
        writeLine(tickAvg.toString());
        writeLine(tickP95.toString());
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] accounting_overlap_frames top_level=%d top_level_excess_ms=%.3f top_level_max_excess_ms=%.3f tick_detail=%d world_detail=%d tick_detail_unbalanced=%d",
                topLevelOverlapFrames, millis(topLevelExcessNanos), millis(topLevelMaxExcessNanos),
                tickDetailOverlapFrames, worldDetailOverlapFrames,
                tickDetailUnbalancedFrames));

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
        long threadCpuNanos = renderThreadCpuNanos();
        long threadAllocatedBytes = renderThreadAllocatedBytes();
        long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] jvm gc_count_delta=%d gc_ms_delta=%d heap_used_mib=%.1f render_thread_cpu_ms_delta=%.3f render_thread_alloc_mib_delta=%.3f",
                Math.max(0L, gcCount - lastGcCount), Math.max(0L, gcMillis - lastGcMillis),
                heapUsed / (1024.0D * 1024.0D),
                threadCpuNanos < 0L || lastRenderThreadCpuNanos < 0L ? -1.0D
                        : millis(Math.max(0L, threadCpuNanos - lastRenderThreadCpuNanos)),
                threadAllocatedBytes < 0L || lastRenderThreadAllocatedBytes < 0L ? -1.0D
                        : Math.max(0L, threadAllocatedBytes - lastRenderThreadAllocatedBytes) / (1024.0D * 1024.0D)));
        lastGcCount = gcCount;
        lastGcMillis = gcMillis;
        lastRenderThreadCpuNanos = threadCpuNanos;
        lastRenderThreadAllocatedBytes = threadAllocatedBytes;

        try {
            WorldRenderer renderer = WorldRenderer.getInstance();
            if (windowContext == 1 && renderer != null && renderer.getLevel() == windowLevel) {
                writeLine("[VulkanModPerf] terrain " + renderer.getChunkStatistics());
                emitTerrainCounters(renderer);
            } else {
                lastTerrainRenderer = null;
                lastTerrainCounters = null;
            }
        } catch (RuntimeException diagnosticFailure) {
            writeLine("[VulkanModPerf] terrain_unavailable exception=" + diagnosticFailure.getClass().getSimpleName());
            lastTerrainRenderer = null;
            lastTerrainCounters = null;
        }

        flushOutput();

        sampleCount = 0;
        slowFrames = 0;
        tickFrames = renderOnlyFrames = tickCalls = tickSlowFrames = renderOnlySlowFrames = 0;
        tickFrameSumNanos = renderOnlyFrameSumNanos = 0L;
        tickFrameMaxNanos = renderOnlyFrameMaxNanos = 0L;
        loopGapSumNanos = loopGapMaxNanos = 0L;
        loopGapSamplesCount = 0;
        tickDetailOverlapFrames = tickDetailUnbalancedFrames = worldDetailOverlapFrames = topLevelOverlapFrames = 0;
        topLevelExcessNanos = topLevelMaxExcessNanos = 0L;
        positionSamples = poseChangedFrames = 0;
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

    private static void emitTerrainCounters(WorldRenderer renderer) {
        WorldRenderer.PerformanceCounters current = renderer.performanceCounters();
        TaskDispatcher.PerformanceCounters workers = current.workers();
        RegionVoxelStore.StagingCounters staging = RegionVoxelStore.stagingCounters();
        long preflightFull = GpuTerrainDiagnostics.count("worker_preflight", "voxel_staging_budget_full");
        long publishRejected = GpuTerrainDiagnostics.count("input_publish", "voxel_snapshot_store_rejected");
        long cpuRecovery = GpuTerrainDiagnostics.count("recovery", "cpu_rebuild_requested");

        boolean sameRenderer = renderer == lastTerrainRenderer && lastTerrainCounters != null;
        WorldRenderer.PerformanceCounters prior = lastTerrainCounters;
        TaskDispatcher.PerformanceCounters priorWorkers = sameRenderer ? prior.workers() : null;
        boolean reset = sameRenderer && (current.dirtyNotices() < prior.dirtyNotices()
                || current.scheduled() < prior.scheduled()
                || workers.builds() < priorWorkers.builds()
                || workers.published() < priorWorkers.published()
                || workers.accepted() < priorWorkers.accepted()
                || workers.dropped() < priorWorkers.dropped());
        boolean deltaValid = sameRenderer && !reset;

        writeLine(String.format(Locale.ROOT,
                "[VulkanModPerf] terrain_window delta_valid=%s counters_reset=%s gpu_diagnostics_enabled=%s voxel_staging_enabled=%s visible_sections=%d dirty_total=%d dirty_delta=%d scheduled_total=%d scheduled_delta=%d builds_total=%d builds_delta=%d published_total=%d published_delta=%d accepted_total=%d accepted_delta=%d dropped_total=%d dropped_delta=%d queued_high=%d queued_low=%d active=%d pub_waiters=%d pub_queue=%d staging_entries=%d/%d staging_kib=%d/%d staging_rejected_total=%d staging_rejected_delta=%d preflight_full_total=%d preflight_full_delta=%d publish_rejected_total=%d publish_rejected_delta=%d cpu_recovery_total=%d cpu_recovery_delta=%d",
                deltaValid, reset, GpuTerrainDiagnostics.enabled(), RegionVoxelStore.ENABLED,
                current.nonEmptySections(),
                current.dirtyNotices(), deltaValid ? current.dirtyNotices() - prior.dirtyNotices() : -1L,
                current.scheduled(), deltaValid ? current.scheduled() - prior.scheduled() : -1L,
                workers.builds(), deltaValid ? workers.builds() - priorWorkers.builds() : -1,
                workers.published(), deltaValid ? workers.published() - priorWorkers.published() : -1,
                workers.accepted(), deltaValid ? workers.accepted() - priorWorkers.accepted() : -1,
                workers.dropped(), deltaValid ? workers.dropped() - priorWorkers.dropped() : -1,
                workers.queuedHigh(), workers.queuedLow(), workers.active(),
                workers.publicationWaiters(), workers.publicationQueue(),
                staging.entries(), staging.maxEntries(), staging.bytes() / 1024, staging.maxBytes() / 1024,
                staging.rejected(), deltaValid ? staging.rejected() - lastStagingRejected : -1L,
                preflightFull, deltaValid ? preflightFull - lastPreflightFull : -1L,
                publishRejected, deltaValid ? publishRejected - lastPublishRejected : -1L,
                cpuRecovery, deltaValid ? cpuRecovery - lastCpuRecovery : -1L));

        lastTerrainRenderer = renderer;
        lastTerrainCounters = current;
        lastStagingRejected = staging.rejected();
        lastPreflightFull = preflightFull;
        lastPublishRejected = publishRejected;
        lastCpuRecovery = cpuRecovery;
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
        if (count == 0) return 0L;
        System.arraycopy(values, 0, sortScratch, 0, count);
        Arrays.sort(sortScratch, 0, count);
        return sortedPercentile(sortScratch, count, percentile);
    }

    private static long sortedPercentile(long[] sorted, int count, double percentile) {
        if (count == 0) return 0L;
        int index = (int) Math.ceil(percentile * count) - 1;
        index = Math.max(0, Math.min(count - 1, index));
        return sorted[index];
    }

    private static long tickPercentile(Stage stage, int frameCount, double percentile) {
        int count = 0;
        for (int i = 0; i < frameCount; i++) if (tickCallsPerFrame[i] > 0)
            sortScratch[count++] = stageSamples[stage.ordinal()][i];
        Arrays.sort(sortScratch, 0, count);
        return sortedPercentile(sortScratch, count, percentile);
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

    private static long renderThreadCpuNanos() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled()
                ? bean.getCurrentThreadCpuTime() : -1L;
    }

    private static long renderThreadAllocatedBytes() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean allocationBean
                && allocationBean.isThreadAllocatedMemorySupported()
                && allocationBean.isThreadAllocatedMemoryEnabled()) {
            return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        }
        return -1L;
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
                || Stage.IMAGE_ACQUIRE.nested
                || !Stage.TERRAIN_DRAW.nested
                || !Stage.BLOCK_ENTITY_RENDER.nested
                || !Stage.HUD_RENDER.nested
                || !Stage.QUEUE_SUBMIT.nested
                || !Stage.PRESENT.nested
                || Stage.GAME_RENDER.nested
                || Stage.FRAME_FENCE_WAIT.nested) {
            throw new IllegalStateException("Performance profiler stage contract is invalid");
        }
    }

    public enum Stage {
        FRAME_SLOT_WAIT("frame_slot_wait", false),
        FRAME_FENCE_WAIT("frame_fence_wait", false),
        IMAGE_ACQUIRE("image_acquire", false),
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
        TERRAIN_DRAW("terrain_draw", true),
        BLOCK_ENTITY_RENDER("block_entity_render", true),
        HUD_RENDER("hud_render", true),
        SUBMIT_RENDER("submit_render", false),
        QUEUE_SUBMIT("queue_submit", true),
        PRESENT("present", true),
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
