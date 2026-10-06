package net.vulkanmod.render.profiling;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Locale;

/**
 * Bounded automated-benchmark attribution for particle ownership and cost.
 *
 * <p>This is evidence gathering for GPU offload, not a replacement particle
 * engine. It keeps exact call/churn counts in fixed primitive storage and samples
 * one out of every eight calls per particle class for timing/allocation. Source,
 * provider and render-type objects are retained only as identity labels and are
 * converted to text after capture, so the hot path does not allocate strings.</p>
 */
public final class ParticleAttribution {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");
    private static final int MAX_CLASSES = 96;
    private static final int TABLE_SIZE = 256;
    private static final int TABLE_MASK = TABLE_SIZE - 1;
    private static final int SAMPLE_STRIDE = 8;
    private static final int MAX_REPORTED_CLASSES = 24;

    private static final ThreadMXBean THREAD_BEAN = ENABLED ? ManagementFactory.getThreadMXBean() : null;
    private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN =
            THREAD_BEAN instanceof com.sun.management.ThreadMXBean bean ? bean : null;
    private static final boolean ALLOCATION_SUPPORTED = ENABLED && ALLOCATION_BEAN != null
            && ALLOCATION_BEAN.isThreadAllocatedMemorySupported()
            && ALLOCATION_BEAN.isThreadAllocatedMemoryEnabled();

    private static final Class<?>[] hashKeys = ENABLED ? new Class<?>[TABLE_SIZE] : null;
    private static final int[] hashSlots = ENABLED ? new int[TABLE_SIZE] : null;
    private static final Class<?>[] classes = ENABLED ? new Class<?>[MAX_CLASSES] : null;
    private static final Object[] sourceKeys = ENABLED ? new Object[MAX_CLASSES] : null;
    private static final Class<?>[] providerClasses = ENABLED ? new Class<?>[MAX_CLASSES] : null;
    private static final Object[] renderTypes = ENABLED ? new Object[MAX_CLASSES] : null;

    private static final long[] tickCalls = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] tickSamples = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] tickSampleNanos = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] tickSampleAllocatedBytes = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] removals = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] additions = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] creations = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] sourceMismatches = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] providerMismatches = ENABLED ? new long[MAX_CLASSES] : null;
    private static final long[] renderTypeMismatches = ENABLED ? new long[MAX_CLASSES] : null;
    private static int classCount;
    private static long engineTicks;
    private static long renderPasses;
    private static long overflowTickCalls;
    private static long overflowAdditions;
    private static long overflowCreations;

    private ParticleAttribution() {
    }

    public static void beginEngineTick() {
        if(ENABLED && PerformanceProfiler.isClientTickCapturing()) {
            engineTicks++;
        }
    }

    public static void beginRenderPass() {
        if(ENABLED && PerformanceProfiler.isFrameCapturing()) {
            renderPasses++;
        }
    }

    /**
     * Returns 0 when outside capture/full, a positive slot token when this call
     * should be sampled, or a negative slot token when only exact call/removal
     * counters should be updated.
     */
    public static int beginParticleTick(Object particle) {
        if(!ENABLED || !PerformanceProfiler.isClientTickCapturing() || particle == null) {
            return 0;
        }

        int slot = slotFor(particle.getClass(), true);
        if(slot < 0) {
            overflowTickCalls++;
            return 0;
        }

        long call = ++tickCalls[slot];
        int token = slot + 1;
        return ((call - 1L) & (SAMPLE_STRIDE - 1L)) == 0L ? token : -token;
    }

    public static void endParticleTick(int token, long elapsedNanos, long allocatedBytes, boolean removed) {
        if(!ENABLED || token == 0) {
            return;
        }
        int slot = Math.abs(token) - 1;
        if(slot < 0 || slot >= classCount) {
            return;
        }
        if(removed) {
            removals[slot]++;
        }
        if(token > 0) {
            tickSamples[slot]++;
            tickSampleNanos[slot] += Math.max(0L, elapsedNanos);
            if(allocatedBytes >= 0L) {
                tickSampleAllocatedBytes[slot] += allocatedBytes;
            }
        }
    }

    public static void recordAdded(Object particle, Object renderType) {
        if(!ENABLED || !PerformanceProfiler.isClientTickCapturing() || particle == null) {
            return;
        }
        int slot = slotFor(particle.getClass(), true);
        if(slot < 0) {
            overflowAdditions++;
            return;
        }
        additions[slot]++;
        rememberRenderType(slot, renderType);
    }

    public static void recordCreated(Object particle, Object sourceKey, Object provider) {
        if(!ENABLED || !PerformanceProfiler.isClientTickCapturing() || particle == null) {
            return;
        }
        int slot = slotFor(particle.getClass(), true);
        if(slot < 0) {
            overflowCreations++;
            return;
        }

        creations[slot]++;
        if(sourceKey != null) {
            Object prior = sourceKeys[slot];
            if(prior == null) sourceKeys[slot] = sourceKey;
            else if(!prior.equals(sourceKey)) sourceMismatches[slot]++;
        }
        if(provider != null) {
            Class<?> providerClass = provider.getClass();
            Class<?> prior = providerClasses[slot];
            if(prior == null) providerClasses[slot] = providerClass;
            else if(prior != providerClass) providerMismatches[slot]++;
        }
    }

    /** Allocation counter for sampled tick calls only. */
    public static long allocatedBytes() {
        return ALLOCATION_SUPPORTED
                ? ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId())
                : -1L;
    }

    public static void emitSummary() {
        if(!ENABLED || (engineTicks == 0L && renderPasses == 0L)) {
            return;
        }

        long totalTickCalls = sum(tickCalls, classCount) + overflowTickCalls;
        long totalAdditions = sum(additions, classCount) + overflowAdditions;
        long totalCreations = sum(creations, classCount) + overflowCreations;
        long totalRemovals = sum(removals, classCount);
        long totalTickSamples = sum(tickSamples, classCount);
        long sampledTickNanos = sum(tickSampleNanos, classCount);
        long sampledAllocBytes = sum(tickSampleAllocatedBytes, classCount);

        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "particle_attribution engine_ticks=%d render_passes=%d class_slots=%d class_cap=%d sample_stride=%d allocation_available=%s tick_calls=%d tick_samples=%d additions=%d creations=%d removals=%d overflow_tick_calls=%d overflow_additions=%d overflow_creations=%d sampled_tick_ms=%.3f sampled_tick_allocation_kib=%.3f render_cost_scope=aggregate_world_attribution",
                engineTicks, renderPasses, classCount, MAX_CLASSES, SAMPLE_STRIDE, ALLOCATION_SUPPORTED,
                totalTickCalls, totalTickSamples, totalAdditions, totalCreations, totalRemovals,
                overflowTickCalls, overflowAdditions, overflowCreations,
                millis(sampledTickNanos), sampledAllocBytes / 1024.0D));

        Integer[] order = new Integer[classCount];
        for(int i = 0; i < classCount; ++i) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(score(b), score(a)));

        int reportCount = Math.min(classCount, MAX_REPORTED_CLASSES);
        for(int rank = 0; rank < reportCount; ++rank) {
            int slot = order[rank];
            long tickEstimate = estimate(tickSampleNanos[slot], tickCalls[slot], tickSamples[slot]);
            double tickMsPerEngineTick = engineTicks == 0L ? 0.0D
                    : tickEstimate / (double)engineTicks / 1_000_000.0D;
            double tickUsPerSample = tickSamples[slot] == 0L ? 0.0D
                    : tickSampleNanos[slot] / (double)tickSamples[slot] / 1_000.0D;
            double allocKiBPerSample = tickSamples[slot] == 0L ? 0.0D
                    : tickSampleAllocatedBytes[slot] / (double)tickSamples[slot] / 1024.0D;

            PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                    "particle_class rank=%d class=%s tick_calls=%d tick_samples=%d tick_ms_per_engine_tick_est=%.3f tick_us_per_sample=%.3f tick_alloc_kib_per_sample=%.3f additions=%d creations=%d removals=%d source=%s provider=%s render_type=%s source_mismatches=%d provider_mismatches=%d render_type_mismatches=%d",
                    rank + 1, token(classes[slot]), tickCalls[slot], tickSamples[slot],
                    tickMsPerEngineTick, tickUsPerSample, allocKiBPerSample,
                    additions[slot], creations[slot], removals[slot],
                    token(sourceKeys[slot]), token(providerClasses[slot]), token(renderTypes[slot]),
                    sourceMismatches[slot], providerMismatches[slot], renderTypeMismatches[slot]));
        }

        reset();
    }

    private static int slotFor(Class<?> key, boolean create) {
        int index = System.identityHashCode(key) & TABLE_MASK;
        for(int probe = 0; probe < TABLE_SIZE; ++probe) {
            Class<?> existing = hashKeys[index];
            if(existing == key) {
                return hashSlots[index] - 1;
            }
            if(existing == null) {
                if(!create || classCount >= MAX_CLASSES) {
                    return -1;
                }
                int slot = classCount++;
                hashKeys[index] = key;
                hashSlots[index] = slot + 1;
                classes[slot] = key;
                return slot;
            }
            index = (index + 1) & TABLE_MASK;
        }
        return -1;
    }

    private static void rememberRenderType(int slot, Object renderType) {
        if(renderType == null) return;
        Object prior = renderTypes[slot];
        if(prior == null) renderTypes[slot] = renderType;
        else if(prior != renderType) renderTypeMismatches[slot]++;
    }

    private static long estimate(long sampledNanos, long calls, long samples) {
        if(sampledNanos <= 0L || calls <= 0L || samples <= 0L) return 0L;
        double estimate = sampledNanos * (calls / (double)samples);
        return estimate >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(estimate);
    }

    private static double score(int slot) {
        double tick = engineTicks == 0L ? 0.0D
                : estimate(tickSampleNanos[slot], tickCalls[slot], tickSamples[slot]) / (double)engineTicks;
        return tick;
    }

    private static long sum(long[] values, int count) {
        long total = 0L;
        for(int i = 0; i < count; ++i) total += values[i];
        return total;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    private static String token(Object value) {
        if(value == null) return "unknown";
        String text = value instanceof Class<?> clazz ? clazz.getName() : String.valueOf(value);
        if(text.isEmpty()) return "unknown";
        StringBuilder builder = null;
        for(int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if(Character.isWhitespace(c)) {
                if(builder == null) builder = new StringBuilder(text);
                builder.setCharAt(i, '_');
            }
        }
        return builder == null ? text : builder.toString();
    }

    private static void reset() {
        classCount = 0;
        engineTicks = renderPasses = 0L;
        overflowTickCalls = overflowAdditions = overflowCreations = 0L;
        Arrays.fill(hashKeys, null);
        Arrays.fill(hashSlots, 0);
        Arrays.fill(classes, null);
        Arrays.fill(sourceKeys, null);
        Arrays.fill(providerClasses, null);
        Arrays.fill(renderTypes, null);
        Arrays.fill(tickCalls, 0L);
        Arrays.fill(tickSamples, 0L);
        Arrays.fill(tickSampleNanos, 0L);
        Arrays.fill(tickSampleAllocatedBytes, 0L);
        Arrays.fill(removals, 0L);
        Arrays.fill(additions, 0L);
        Arrays.fill(creations, 0L);
        Arrays.fill(sourceMismatches, 0L);
        Arrays.fill(providerMismatches, 0L);
        Arrays.fill(renderTypeMismatches, 0L);
    }
}
