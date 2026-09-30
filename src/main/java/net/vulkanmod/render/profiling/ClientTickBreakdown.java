package net.vulkanmod.render.profiling;

import java.util.Arrays;
import java.util.Locale;

/**
 * Low-overhead leaf attribution for work inside Minecraft.tick().
 *
 * <p>This intentionally complements {@link PerformanceProfiler}'s broader client tick
 * buckets. It only becomes active when the parent CLIENT_TICK stage is actually being
 * captured, keeps all hot-path data in primitive arrays, and emits its summary only
 * after the automated benchmark's final measured frame has ended.</p>
 */
public final class ClientTickBreakdown {
    private static final int MAX_SAMPLES = 8192;
    private static final Stage[] STAGES = Stage.values();
    private static final int STAGE_COUNT = STAGES.length;

    private static final long[] currentStageNanos = new long[STAGE_COUNT];
    private static final long[] stageSums = new long[STAGE_COUNT];
    private static final long[] stageMax = new long[STAGE_COUNT];
    private static final long[][] stageSamples = new long[STAGE_COUNT][MAX_SAMPLES];
    private static final long[] tickSamples = new long[MAX_SAMPLES];
    private static final long[] leafSamples = new long[MAX_SAMPLES];
    private static final long[] sortScratch = new long[MAX_SAMPLES];

    private static boolean tickActive;
    private static long tickStartNanos;
    private static int ticks;
    private static int sampledTicks;
    private static int overlapTicks;
    private static long tickSumNanos;
    private static long tickMaxNanos;
    private static long leafSumNanos;
    private static long leafMaxNanos;

    private ClientTickBreakdown() {
    }

    public static void beginTick() {
        if (tickActive) return;
        Arrays.fill(currentStageNanos, 0L);
        tickStartNanos = System.nanoTime();
        tickActive = true;
    }

    public static long begin(Stage stage) {
        return tickActive && stage != null ? System.nanoTime() : 0L;
    }

    public static void end(Stage stage, long startNanos) {
        if (!tickActive || stage == null || startNanos == 0L) return;
        currentStageNanos[stage.ordinal()] += Math.max(0L, System.nanoTime() - startNanos);
    }

    public static void endTick() {
        if (!tickActive) return;
        long elapsed = Math.max(0L, System.nanoTime() - tickStartNanos);
        tickActive = false;

        long leaf = 0L;
        for (Stage stage : STAGES) {
            int ordinal = stage.ordinal();
            long value = currentStageNanos[ordinal];
            leaf += value;
            stageSums[ordinal] += value;
            stageMax[ordinal] = Math.max(stageMax[ordinal], value);
            if (sampledTicks < MAX_SAMPLES) stageSamples[ordinal][sampledTicks] = value;
        }
        if (leaf > elapsed) overlapTicks++;
        ticks++;
        tickSumNanos += elapsed;
        tickMaxNanos = Math.max(tickMaxNanos, elapsed);
        leafSumNanos += leaf;
        leafMaxNanos = Math.max(leafMaxNanos, leaf);
        if (sampledTicks < MAX_SAMPLES) {
            tickSamples[sampledTicks] = elapsed;
            leafSamples[sampledTicks] = leaf;
            sampledTicks++;
        }
    }

    /** Emit after the final measured frame so diagnostic I/O cannot perturb the capture. */
    public static void emitSummary() {
        if (tickActive) endTick();
        if (ticks == 0) return;

        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "client_tick_breakdown ticks=%d sampled_ticks=%d sample_cap=%d overlap_ticks=%d tick_ms_avg=%.3f tick_ms_p95=%.3f tick_ms_max=%.3f leaf_ms_avg=%.3f leaf_ms_p95=%.3f leaf_ms_max=%.3f",
                ticks, sampledTicks, MAX_SAMPLES, overlapTicks,
                millis(tickSumNanos / ticks), millis(percentile(tickSamples, sampledTicks, 0.95D)), millis(tickMaxNanos),
                millis(leafSumNanos / ticks), millis(percentile(leafSamples, sampledTicks, 0.95D)), millis(leafMaxNanos)));

        StringBuilder avg = new StringBuilder("client_tick_leaf_avg_ms");
        StringBuilder p95 = new StringBuilder("client_tick_leaf_p95_ms");
        StringBuilder max = new StringBuilder("client_tick_leaf_max_ms");
        for (Stage stage : STAGES) {
            int ordinal = stage.ordinal();
            append(avg, stage.label, stageSums[ordinal] / ticks);
            append(p95, stage.label, percentile(stageSamples[ordinal], sampledTicks, 0.95D));
            append(max, stage.label, stageMax[ordinal]);
        }
        PerformanceProfiler.benchmarkEvent(avg.toString());
        PerformanceProfiler.benchmarkEvent(p95.toString());
        PerformanceProfiler.benchmarkEvent(max.toString());
        reset();
    }

    public static void verifyForCi() {
        if (MAX_SAMPLES < 4096 || STAGE_COUNT < 10) {
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
        tickStartNanos = 0L;
        ticks = sampledTicks = overlapTicks = 0;
        tickSumNanos = tickMaxNanos = leafSumNanos = leafMaxNanos = 0L;
        Arrays.fill(currentStageNanos, 0L);
        Arrays.fill(stageSums, 0L);
        Arrays.fill(stageMax, 0L);
    }

    private static void append(StringBuilder builder, String label, long nanos) {
        builder.append(' ').append(label).append('=')
                .append(String.format(Locale.ROOT, "%.3f", millis(nanos)));
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
        GUI("gui"),
        PICK("pick"),
        GAME_MODE("game_mode"),
        TEXTURES("textures"),
        TUTORIAL("tutorial"),
        LEVEL_RENDERER("level_renderer"),
        WEATHER("weather"),
        AMBIENT_WORLD("ambient_world"),
        PARTICLES("particles"),
        MUSIC("music"),
        SOUND("sound"),
        KEYBINDS("keybinds");

        private final String label;

        Stage(String label) {
            this.label = label;
        }
    }
}
