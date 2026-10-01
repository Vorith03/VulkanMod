package net.vulkanmod.render.profiling;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Locale;

/**
 * Low-overhead leaf attribution for work inside Minecraft.tick() during automated benchmarks.
 *
 * <p>This intentionally complements {@link PerformanceProfiler}'s broader client tick
 * buckets. It is inactive for normal gameplay and manual profiling, only begins when
 * the automated benchmark's parent CLIENT_TICK stage is actually being captured, keeps
 * hot-path data in primitive arrays, and emits its summary only after the final measured
 * benchmark frame has ended.</p>
 */
public final class ClientTickBreakdown {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");
    private static final int MAX_SAMPLES = 8192;
    private static final Stage[] STAGES = Stage.values();
    private static final int STAGE_COUNT = STAGES.length;
    private static final ThreadMXBean THREAD_BEAN = ENABLED ? ManagementFactory.getThreadMXBean() : null;
    private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN =
            THREAD_BEAN instanceof com.sun.management.ThreadMXBean bean ? bean : null;
    private static final boolean ALLOCATION_SUPPORTED = ENABLED && ALLOCATION_BEAN != null
            && ALLOCATION_BEAN.isThreadAllocatedMemorySupported()
            && ALLOCATION_BEAN.isThreadAllocatedMemoryEnabled();

    private static final long[] currentStageNanos = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] currentStageAllocatedBytes = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] stageAllocationStart = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] stageSums = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] stageMax = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] stageAllocationSums = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[] stageAllocationMax = ENABLED ? new long[STAGE_COUNT] : null;
    private static final long[][] stageSamples = ENABLED ? new long[STAGE_COUNT][MAX_SAMPLES] : null;
    private static final long[][] stageAllocationSamples = ENABLED ? new long[STAGE_COUNT][MAX_SAMPLES] : null;
    private static final long[] tickSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] leafSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] textureSpriteUploadSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] textureNonUploadSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = ENABLED ? new long[MAX_SAMPLES] : null;

    private static boolean tickActive;
    private static boolean textureStageActive;
    private static long tickStartNanos;
    private static long currentTextureSpriteUploadNanos;
    private static int currentTextureSpriteUploadCalls;
    private static int currentTextureSubUploadCalls;
    private static int ticks;
    private static int sampledTicks;
    private static int overlapTicks;
    private static int textureUploadActiveTicks;
    private static int textureUploadOverlapTicks;
    private static long tickSumNanos;
    private static long tickMaxNanos;
    private static long leafSumNanos;
    private static long leafMaxNanos;
    private static long textureSpriteUploadSumNanos;
    private static long textureSpriteUploadMaxNanos;
    private static long textureNonUploadSumNanos;
    private static long textureSpriteUploadCalls;
    private static long textureSubUploadCalls;

    private ClientTickBreakdown() {
    }

    public static void beginTick() {
        if (!ENABLED || tickActive) return;
        Arrays.fill(currentStageNanos, 0L);
        Arrays.fill(currentStageAllocatedBytes, 0L);
        Arrays.fill(stageAllocationStart, -1L);
        textureStageActive = false;
        currentTextureSpriteUploadNanos = 0L;
        currentTextureSpriteUploadCalls = 0;
        currentTextureSubUploadCalls = 0;
        tickStartNanos = System.nanoTime();
        tickActive = true;
    }

    public static long begin(Stage stage) {
        if (!ENABLED || !tickActive || stage == null) return 0L;
        if (stage == Stage.TEXTURES) textureStageActive = true;
        stageAllocationStart[stage.ordinal()] = currentThreadAllocatedBytes();
        return System.nanoTime();
    }

    public static void end(Stage stage, long startNanos) {
        if (!ENABLED || !tickActive || stage == null || startNanos == 0L) return;
        long now = System.nanoTime();
        long allocationEnd = currentThreadAllocatedBytes();
        int ordinal = stage.ordinal();
        currentStageNanos[ordinal] += Math.max(0L, now - startNanos);
        long allocationStart = stageAllocationStart[ordinal];
        if (allocationStart >= 0L && allocationEnd >= allocationStart)
            currentStageAllocatedBytes[ordinal] += allocationEnd - allocationStart;
        stageAllocationStart[ordinal] = -1L;
        if (stage == Stage.TEXTURES) textureStageActive = false;
    }

    /**
     * Time the complete SpriteContents.upload() body while TextureManager.tick() is
     * the active leaf. This deliberately brackets one sprite update rather than every
     * mip copy so the diagnostic itself stays cheap even for very large animated atlases.
     */
    public static long beginTextureSpriteUpload(int subUploadCalls) {
        if (!ENABLED || !tickActive || !textureStageActive) return 0L;
        currentTextureSpriteUploadCalls++;
        currentTextureSubUploadCalls += Math.max(0, subUploadCalls);
        return System.nanoTime();
    }

    public static void endTextureSpriteUpload(long startNanos) {
        if (!ENABLED || !tickActive || !textureStageActive || startNanos == 0L) return;
        currentTextureSpriteUploadNanos += Math.max(0L, System.nanoTime() - startNanos);
    }

    public static void endTick() {
        if (!ENABLED || !tickActive) return;
        long elapsed = Math.max(0L, System.nanoTime() - tickStartNanos);
        tickActive = false;
        textureStageActive = false;

        long leaf = 0L;
        for (Stage stage : STAGES) {
            int ordinal = stage.ordinal();
            long value = currentStageNanos[ordinal];
            long allocated = currentStageAllocatedBytes[ordinal];
            leaf += value;
            stageSums[ordinal] += value;
            stageMax[ordinal] = Math.max(stageMax[ordinal], value);
            stageAllocationSums[ordinal] += allocated;
            stageAllocationMax[ordinal] = Math.max(stageAllocationMax[ordinal], allocated);
            if (sampledTicks < MAX_SAMPLES) {
                stageSamples[ordinal][sampledTicks] = value;
                stageAllocationSamples[ordinal][sampledTicks] = allocated;
            }
        }

        long textureNanos = currentStageNanos[Stage.TEXTURES.ordinal()];
        long textureNonUploadNanos = Math.max(0L, textureNanos - currentTextureSpriteUploadNanos);
        if (currentTextureSpriteUploadNanos > textureNanos) textureUploadOverlapTicks++;
        if (currentTextureSpriteUploadCalls > 0) textureUploadActiveTicks++;
        textureSpriteUploadSumNanos += currentTextureSpriteUploadNanos;
        textureSpriteUploadMaxNanos = Math.max(textureSpriteUploadMaxNanos, currentTextureSpriteUploadNanos);
        textureNonUploadSumNanos += textureNonUploadNanos;
        textureSpriteUploadCalls += currentTextureSpriteUploadCalls;
        textureSubUploadCalls += currentTextureSubUploadCalls;

        if (leaf > elapsed) overlapTicks++;
        ticks++;
        tickSumNanos += elapsed;
        tickMaxNanos = Math.max(tickMaxNanos, elapsed);
        leafSumNanos += leaf;
        leafMaxNanos = Math.max(leafMaxNanos, leaf);
        if (sampledTicks < MAX_SAMPLES) {
            tickSamples[sampledTicks] = elapsed;
            leafSamples[sampledTicks] = leaf;
            textureSpriteUploadSamples[sampledTicks] = currentTextureSpriteUploadNanos;
            textureNonUploadSamples[sampledTicks] = textureNonUploadNanos;
            sampledTicks++;
        }
    }

    /** Emit after the final measured frame so diagnostic I/O cannot perturb the capture. */
    public static void emitSummary() {
        if (!ENABLED) return;
        if (tickActive) endTick();
        if (ticks == 0) return;

        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "client_tick_breakdown ticks=%d sampled_ticks=%d sample_cap=%d overlap_ticks=%d tick_ms_avg=%.3f tick_ms_p95=%.3f tick_ms_max=%.3f leaf_ms_avg=%.3f leaf_ms_p95=%.3f leaf_ms_max=%.3f leaf_allocation_available=%s",
                ticks, sampledTicks, MAX_SAMPLES, overlapTicks,
                millis(tickSumNanos / ticks), millis(percentile(tickSamples, sampledTicks, 0.95D)), millis(tickMaxNanos),
                millis(leafSumNanos / ticks), millis(percentile(leafSamples, sampledTicks, 0.95D)), millis(leafMaxNanos),
                ALLOCATION_SUPPORTED));

        StringBuilder avg = new StringBuilder("client_tick_leaf_avg_ms");
        StringBuilder p95 = new StringBuilder("client_tick_leaf_p95_ms");
        StringBuilder max = new StringBuilder("client_tick_leaf_max_ms");
        for (Stage stage : STAGES) {
            int ordinal = stage.ordinal();
            appendNanos(avg, stage.label, stageSums[ordinal] / ticks);
            appendNanos(p95, stage.label, percentile(stageSamples[ordinal], sampledTicks, 0.95D));
            appendNanos(max, stage.label, stageMax[ordinal]);
        }
        PerformanceProfiler.benchmarkEvent(avg.toString());
        PerformanceProfiler.benchmarkEvent(p95.toString());
        PerformanceProfiler.benchmarkEvent(max.toString());

        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "client_tick_texture_detail ticks=%d active_upload_ticks=%d overlap_ticks=%d sprite_upload_calls=%d sub_upload_calls=%d sprite_calls_per_tick=%.3f sub_upload_calls_per_tick=%.3f texture_ms_avg=%.3f sprite_upload_ms_avg=%.3f sprite_upload_ms_p95=%.3f sprite_upload_ms_max=%.3f non_upload_ms_avg=%.3f non_upload_ms_p95=%.3f",
                ticks, textureUploadActiveTicks, textureUploadOverlapTicks,
                textureSpriteUploadCalls, textureSubUploadCalls,
                textureSpriteUploadCalls / (double) ticks, textureSubUploadCalls / (double) ticks,
                millis(stageSums[Stage.TEXTURES.ordinal()] / ticks),
                millis(textureSpriteUploadSumNanos / ticks),
                millis(percentile(textureSpriteUploadSamples, sampledTicks, 0.95D)),
                millis(textureSpriteUploadMaxNanos),
                millis(textureNonUploadSumNanos / ticks),
                millis(percentile(textureNonUploadSamples, sampledTicks, 0.95D))));

        if (ALLOCATION_SUPPORTED) {
            StringBuilder allocationAvg = new StringBuilder("client_tick_leaf_allocation_avg_kib");
            StringBuilder allocationP95 = new StringBuilder("client_tick_leaf_allocation_p95_kib");
            StringBuilder allocationMax = new StringBuilder("client_tick_leaf_allocation_max_kib");
            for (Stage stage : STAGES) {
                int ordinal = stage.ordinal();
                appendKib(allocationAvg, stage.label, stageAllocationSums[ordinal] / ticks);
                appendKib(allocationP95, stage.label,
                        percentile(stageAllocationSamples[ordinal], sampledTicks, 0.95D));
                appendKib(allocationMax, stage.label, stageAllocationMax[ordinal]);
            }
            PerformanceProfiler.benchmarkEvent(allocationAvg.toString());
            PerformanceProfiler.benchmarkEvent(allocationP95.toString());
            PerformanceProfiler.benchmarkEvent(allocationMax.toString());
        }
        GpuTimestampProfiler.emitCaptureSummary();
        reset();
    }

    public static void verifyForCi() {
        if (MAX_SAMPLES < 4096 || STAGE_COUNT < 14) {
            throw new IllegalStateException("Client tick breakdown capacity/stage contract is invalid");
        }
        for (int i = 0; i < STAGES.length; i++) {
            if (STAGES[i].label == null || STAGES[i].label.isBlank()) {
                throw new IllegalStateException("Client tick breakdown contains an unlabeled stage");
            }
            for (int j = i + 1; j < STAGES.length; j++) {
                if (STAGES[i].label.equals(STAGES[j].label)) {
                    throw new IllegalStateException("Duplicate client tick breakdown label: " + STAGES[i].label);
                }
            }
        }
    }

    private static void reset() {
        tickActive = false;
        textureStageActive = false;
        tickStartNanos = 0L;
        currentTextureSpriteUploadNanos = 0L;
        currentTextureSpriteUploadCalls = 0;
        currentTextureSubUploadCalls = 0;
        ticks = sampledTicks = overlapTicks = 0;
        textureUploadActiveTicks = textureUploadOverlapTicks = 0;
        tickSumNanos = tickMaxNanos = leafSumNanos = leafMaxNanos = 0L;
        textureSpriteUploadSumNanos = textureSpriteUploadMaxNanos = textureNonUploadSumNanos = 0L;
        textureSpriteUploadCalls = textureSubUploadCalls = 0L;
        Arrays.fill(currentStageNanos, 0L);
        Arrays.fill(currentStageAllocatedBytes, 0L);
        Arrays.fill(stageAllocationStart, -1L);
        Arrays.fill(stageSums, 0L);
        Arrays.fill(stageMax, 0L);
        Arrays.fill(stageAllocationSums, 0L);
        Arrays.fill(stageAllocationMax, 0L);
    }

    private static long currentThreadAllocatedBytes() {
        return ALLOCATION_SUPPORTED ? ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId()) : -1L;
    }

    private static void appendNanos(StringBuilder builder, String label, long nanos) {
        builder.append(' ').append(label).append('=')
                .append(String.format(Locale.ROOT, "%.3f", millis(nanos)));
    }

    private static void appendKib(StringBuilder builder, String label, long bytes) {
        builder.append(' ').append(label).append('=')
                .append(String.format(Locale.ROOT, "%.3f", bytes / 1024.0D));
    }

    private static long percentile(long[] values, int count, double percentile) {
        if (count <= 0) return 0L;
        System.arraycopy(values, 0, sortScratch, 0, count);
        Arrays.sort(sortScratch, 0, count);
        int index = (int) Math.ceil(percentile * count) - 1;
        index = Math.max(0, Math.min(count - 1, index));
        return sortScratch[index];
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    public enum Stage {
        FORGE_CLIENT_PRE("forge_client_pre"),
        GUI("gui"),
        PICK("pick"),
        GAME_MODE("game_mode"),
        TEXTURES("textures"),
        TUTORIAL("tutorial"),
        FORGE_LEVEL_PRE("forge_level_pre"),
        LEVEL_RENDERER("level_renderer"),
        WEATHER("weather"),
        AMBIENT_WORLD("ambient_world"),
        PARTICLES("particles"),
        MUSIC("music"),
        SOUND("sound"),
        KEYBINDS("keybinds"),
        FORGE_LEVEL_POST("forge_level_post"),
        FORGE_CLIENT_POST("forge_client_post");

        private final String label;

        Stage(String label) {
            this.label = label;
        }
    }
}
