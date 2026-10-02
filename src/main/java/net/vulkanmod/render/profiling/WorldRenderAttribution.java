package net.vulkanmod.render.profiling;

import java.util.Arrays;
import java.util.Locale;

/**
 * Automated-benchmark-only attribution for CPU work currently folded into
 * PerformanceProfiler's world_render_other residual.
 *
 * <p>Categories are deliberately exclusive: if a Forge callback invokes entity or
 * particle rendering, the outer callback owns that time and the nested category is
 * suppressed. A fixed primitive nesting stack keeps that rule correct under portal
 * or mod recursion without allocating on the render path.</p>
 */
public final class WorldRenderAttribution {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");
    private static final int MAX_SAMPLES = 8192;
    private static final int MAX_NESTING = 32;
    private static final Category[] CATEGORIES = Category.values();
    private static final int CATEGORY_COUNT = CATEGORIES.length;

    private static final long[] currentCategoryNanos = ENABLED ? new long[CATEGORY_COUNT] : null;
    private static final long[] categorySums = ENABLED ? new long[CATEGORY_COUNT] : null;
    private static final long[] categoryMax = ENABLED ? new long[CATEGORY_COUNT] : null;
    private static final long[] categoryCalls = ENABLED ? new long[CATEGORY_COUNT] : null;
    private static final long[][] categorySamples = ENABLED ? new long[CATEGORY_COUNT][MAX_SAMPLES] : null;
    private static final long[] worldSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] addedDetailSamples = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = ENABLED ? new long[MAX_SAMPLES] : null;
    private static final int[] categoryStack = ENABLED ? new int[MAX_NESTING] : null;
    private static final long[] categoryStartStack = ENABLED ? new long[MAX_NESTING] : null;

    private static int worldDepth;
    private static long worldStartNanos;
    private static int categoryStackDepth;
    private static int categoryOverflowDepth;
    private static int nestedCategorySuppressions;
    private static int categoryStackErrors;
    private static int worldCalls;
    private static int sampledWorldCalls;
    private static long worldSumNanos;
    private static long addedDetailSumNanos;
    private static long addedDetailMaxNanos;

    private WorldRenderAttribution() {
    }

    public static void beginWorldRender() {
        if (!ENABLED) return;
        if (worldDepth++ == 0) {
            Arrays.fill(currentCategoryNanos, 0L);
            categoryStackDepth = 0;
            categoryOverflowDepth = 0;
            worldStartNanos = System.nanoTime();
        }
    }

    public static void endWorldRender() {
        if (!ENABLED || worldDepth <= 0) return;
        if (--worldDepth != 0) return;

        if (categoryStackDepth != 0 || categoryOverflowDepth != 0) {
            categoryStackErrors++;
            categoryStackDepth = 0;
            categoryOverflowDepth = 0;
        }

        long elapsed = Math.max(0L, System.nanoTime() - worldStartNanos);
        long addedDetail = 0L;
        for (Category category : CATEGORIES) {
            int ordinal = category.ordinal();
            long value = currentCategoryNanos[ordinal];
            addedDetail += value;
            categorySums[ordinal] += value;
            categoryMax[ordinal] = Math.max(categoryMax[ordinal], value);
            if (sampledWorldCalls < MAX_SAMPLES) categorySamples[ordinal][sampledWorldCalls] = value;
        }

        worldCalls++;
        worldSumNanos += elapsed;
        addedDetailSumNanos += addedDetail;
        addedDetailMaxNanos = Math.max(addedDetailMaxNanos, addedDetail);
        if (sampledWorldCalls < MAX_SAMPLES) {
            worldSamples[sampledWorldCalls] = elapsed;
            addedDetailSamples[sampledWorldCalls] = addedDetail;
            sampledWorldCalls++;
        }
        worldStartNanos = 0L;
    }

    public static void begin(Category category) {
        if (!ENABLED || worldDepth <= 0 || category == null) return;
        if (categoryStackDepth >= MAX_NESTING) {
            categoryOverflowDepth++;
            nestedCategorySuppressions++;
            return;
        }

        boolean measured = categoryStackDepth == 0;
        int index = categoryStackDepth++;
        // Non-negative entries own time; negative entries are nested and suppressed.
        categoryStack[index] = measured ? category.ordinal() : -category.ordinal() - 1;
        categoryStartStack[index] = measured ? System.nanoTime() : 0L;
        if (measured) categoryCalls[category.ordinal()]++;
        else nestedCategorySuppressions++;
    }

    public static void end(Category category) {
        if (!ENABLED || worldDepth <= 0 || category == null) return;
        if (categoryOverflowDepth > 0) {
            categoryOverflowDepth--;
            return;
        }
        if (categoryStackDepth <= 0) {
            categoryStackErrors++;
            return;
        }

        int index = --categoryStackDepth;
        int encoded = categoryStack[index];
        int ordinal = encoded >= 0 ? encoded : -encoded - 1;
        if (ordinal != category.ordinal()) {
            categoryStackErrors++;
            categoryStackDepth = 0;
            return;
        }
        if (encoded >= 0) {
            currentCategoryNanos[ordinal] += Math.max(0L, System.nanoTime() - categoryStartStack[index]);
        }
    }

    public static void emitSummary() {
        if (!ENABLED || worldCalls == 0) return;
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                "world_render_attribution world_calls=%d sampled_calls=%d sample_cap=%d nested_category_suppressions=%d category_stack_errors=%d world_ms_avg=%.3f world_ms_p95=%.3f added_detail_ms_avg=%.3f added_detail_ms_p95=%.3f added_detail_ms_max=%.3f correction=stage_avg_ms.world_render_other-minus-added_detail_ms_avg",
                worldCalls, sampledWorldCalls, MAX_SAMPLES, nestedCategorySuppressions, categoryStackErrors,
                millis(worldSumNanos / worldCalls), millis(percentile(worldSamples, sampledWorldCalls, 0.95D)),
                millis(addedDetailSumNanos / worldCalls), millis(percentile(addedDetailSamples, sampledWorldCalls, 0.95D)),
                millis(addedDetailMaxNanos)));
        for (Category category : CATEGORIES) {
            int ordinal = category.ordinal();
            line.append(' ').append(category.label).append("_calls=").append(categoryCalls[ordinal]);
            line.append(' ').append(category.label).append("_ms_avg=")
                    .append(String.format(Locale.ROOT, "%.3f", millis(categorySums[ordinal] / worldCalls)));
            line.append(' ').append(category.label).append("_ms_p95=")
                    .append(String.format(Locale.ROOT, "%.3f", millis(percentile(categorySamples[ordinal], sampledWorldCalls, 0.95D))));
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
        worldDepth = 0;
        worldStartNanos = 0L;
        categoryStackDepth = categoryOverflowDepth = 0;
        nestedCategorySuppressions = categoryStackErrors = 0;
        worldCalls = sampledWorldCalls = 0;
        worldSumNanos = addedDetailSumNanos = addedDetailMaxNanos = 0L;
        Arrays.fill(currentCategoryNanos, 0L);
        Arrays.fill(categorySums, 0L);
        Arrays.fill(categoryMax, 0L);
        Arrays.fill(categoryCalls, 0L);
    }

    public enum Category {
        FORGE_RENDER_STAGE("forge_render_stage"),
        ENTITY_RENDER("entity_render"),
        PARTICLE_RENDER("particle_render"),
        CLOUD_RENDER("cloud_render");

        private final String label;

        Category(String label) {
            this.label = label;
        }
    }
}
