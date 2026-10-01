package net.vulkanmod.render.profiling;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Locale;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Optional asynchronous Vulkan timestamp profiler for the main graphics command buffer.
 *
 * <p>Query pools are owned per Renderer frame slot. Normal result collection happens only
 * after that slot's existing frame fence has been waited, so enabling this profiler adds no
 * new GPU/CPU synchronization point to steady-state rendering. Automated-benchmark shutdown
 * may use VK_QUERY_RESULT_WAIT_BIT after the final measured frame so the tail of the capture
 * is not silently dropped; that wait occurs outside the measured runTick interval.</p>
 *
 * <p>The fixed coarse boundaries deliberately follow stable high-level recording scopes:
 * outer world render, HUD render, and the frame ends. Terrain draw calls additionally use a
 * bounded set of dynamic timestamp pairs so world GPU time can be separated into terrain and
 * non-terrain residual without instrumenting every draw. Missing boundaries and overflow are
 * reported explicitly rather than being converted into fabricated pass timings.</p>
 */
public final class GpuTimestampProfiler {
    private static final boolean REQUESTED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.gpuTimestamps");
    private static final boolean AUTOMATED_BENCHMARK = REQUESTED
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");

    private static final int FRAME_START_QUERY = 0;
    private static final int WORLD_BEGIN_QUERY = 1;
    private static final int WORLD_END_QUERY = 2;
    private static final int HUD_BEGIN_QUERY = 3;
    private static final int HUD_END_QUERY = 4;
    private static final int FRAME_END_QUERY = 5;
    private static final int FIXED_QUERY_COUNT = 6;
    private static final int MAX_TERRAIN_SEGMENTS = 32;
    private static final int QUERY_COUNT = FIXED_QUERY_COUNT + MAX_TERRAIN_SEGMENTS * 2;
    private static final int ALL_BOUNDARIES_MASK = (1 << 4) - 1;
    private static final int MAX_SAMPLES = 65_536;

    public static final String SCOPE = "main_graphics_command_buffer";

    public enum Boundary {
        WORLD_BEGIN(WORLD_BEGIN_QUERY, 1 << 0),
        WORLD_END(WORLD_END_QUERY, 1 << 1),
        HUD_BEGIN(HUD_BEGIN_QUERY, 1 << 2),
        HUD_END(HUD_END_QUERY, 1 << 3);

        private final int query;
        private final int bit;

        Boundary(int query, int bit) {
            this.query = query;
            this.bit = bit;
        }
    }

    private enum PassSeries {
        PRE_WORLD,
        WORLD,
        TERRAIN,
        WORLD_OTHER,
        BETWEEN_WORLD_HUD,
        HUD,
        TAIL
    }

    private static final long[] samples = REQUESTED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = REQUESTED ? new long[MAX_SAMPLES] : null;
    private static final long[][] passSamples = REQUESTED
            ? new long[PassSeries.values().length][MAX_SAMPLES] : null;
    private static final int[] passSampleCounts = REQUESTED ? new int[PassSeries.values().length] : null;
    private static final long[] passSampleSums = REQUESTED ? new long[PassSeries.values().length] : null;
    private static final long[] passSampleMax = REQUESTED ? new long[PassSeries.values().length] : null;

    private static long[] queryPools;
    private static boolean[] armed;
    private static boolean[] ended;
    private static boolean[] pending;
    private static int[] boundaryMasks;
    private static int[] terrainSegmentCounts;
    private static int[] terrainCompletedMasks;
    private static int[] terrainOverflowCounts;
    private static int[] submittedBoundaryMasks;
    private static int[] submittedTerrainSegmentCounts;
    private static int[] submittedTerrainCompletedMasks;
    private static int[] submittedTerrainOverflowCounts;
    private static boolean initialized;
    private static boolean active;
    private static boolean captureActive = initialCaptureActive(REQUESTED, AUTOMATED_BENCHMARK);
    private static boolean smokeResultAnnounced;
    private static int timestampValidBits;
    private static double timestampPeriodNanos;
    private static int sampledFrames;
    private static long measuredFrames;
    private static long droppedSamples;
    private static long readFailures;
    private static long breakdownFrames;
    private static long breakdownInvalidFrames;
    private static long terrainSegments;
    private static long terrainSegmentDrops;
    private static String status = REQUESTED ? "not_initialized" : "disabled";

    private GpuTimestampProfiler() {
    }

    public static boolean requested() {
        return REQUESTED;
    }

    public static boolean active() {
        return active;
    }

    /**
     * Arm exactly when the automated CPU capture is armed. Startup/menu/settling frames
     * are deliberately excluded so the final GPU distribution describes the same measured
     * stationary interval as the CPU/tick profiler.
     */
    public static void armAutomatedCapture() {
        if (!REQUESTED || !AUTOMATED_BENCHMARK) return;
        resetMeasurements();
        captureActive = true;
    }

    /** Called after the Vulkan device and Renderer frame-slot count are known. */
    public static void create(int frames) {
        if (!REQUESTED || initialized) return;
        initialized = true;

        if (frames <= 0) {
            status = "invalid_frame_count";
            return;
        }

        timestampValidBits = graphicsTimestampValidBits();
        timestampPeriodNanos = Device.deviceProperties == null
                ? 0.0D : Device.deviceProperties.limits().timestampPeriod();
        if (timestampValidBits <= 0 || !(timestampPeriodNanos > 0.0D)
                || !Double.isFinite(timestampPeriodNanos)) {
            status = timestampValidBits <= 0 ? "graphics_timestamps_unsupported" : "invalid_timestamp_period";
            Initializer.LOGGER.warn("VulkanMod GPU timestamp profiler unavailable: status={} validBits={} periodNs={}",
                    status, timestampValidBits, timestampPeriodNanos);
            return;
        }

        queryPools = new long[frames];
        armed = new boolean[frames];
        ended = new boolean[frames];
        pending = new boolean[frames];
        boundaryMasks = new int[frames];
        terrainSegmentCounts = new int[frames];
        terrainCompletedMasks = new int[frames];
        terrainOverflowCounts = new int[frames];
        submittedBoundaryMasks = new int[frames];
        submittedTerrainSegmentCounts = new int[frames];
        submittedTerrainCompletedMasks = new int[frames];
        submittedTerrainOverflowCounts = new int[frames];

        VkDevice device = Device.device;
        try (MemoryStack stack = stackPush()) {
            VkQueryPoolCreateInfo createInfo = VkQueryPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
                    .queryType(VK_QUERY_TYPE_TIMESTAMP)
                    .queryCount(QUERY_COUNT);
            LongBuffer handle = stack.mallocLong(1);
            for (int i = 0; i < frames; i++) {
                handle.put(0, VK_NULL_HANDLE);
                int result = vkCreateQueryPool(device, createInfo, null, handle);
                if (result != VK_SUCCESS) {
                    status = "query_pool_create_failed_" + result;
                    destroyPools();
                    Initializer.LOGGER.warn("VulkanMod GPU timestamp profiler disabled: vkCreateQueryPool returned {}", result);
                    return;
                }
                queryPools[i] = handle.get(0);
            }
        } catch (RuntimeException failure) {
            status = "query_pool_create_exception";
            destroyPools();
            Initializer.LOGGER.warn("VulkanMod GPU timestamp profiler disabled while creating query pools", failure);
            return;
        }

        active = true;
        status = "active";
        Initializer.LOGGER.info(
                "VulkanMod GPU timestamp profiler enabled: scope={} frameSlots={} validBits={} timestampPeriodNs={} coarsePasses=true maxTerrainSegments={}",
                SCOPE, frames, timestampValidBits, String.format(Locale.ROOT, "%.6f", timestampPeriodNanos),
                MAX_TERRAIN_SEGMENTS);
    }

    /** Rebuild frame-slot-owned pools after a device-idle swapchain image-count change. */
    public static void recreate(int frames) {
        if (!REQUESTED) return;
        resolvePending(true);
        destroyPools();
        initialized = false;
        active = false;
        status = "recreating";
        create(frames);
    }

    /** Record the beginning of the main graphics command buffer. */
    public static void beginFrame(int slot, VkCommandBuffer commandBuffer) {
        if (!captureActive || !usable(slot) || pending[slot]) return;
        long pool = queryPools[slot];
        vkCmdResetQueryPool(commandBuffer, pool, 0, QUERY_COUNT);
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, pool, FRAME_START_QUERY);
        boundaryMasks[slot] = 0;
        terrainSegmentCounts[slot] = 0;
        terrainCompletedMasks[slot] = 0;
        terrainOverflowCounts[slot] = 0;
        armed[slot] = true;
        ended[slot] = false;
    }

    /** Record a fixed coarse boundary once per frame. */
    public static void boundary(int slot, VkCommandBuffer commandBuffer, Boundary boundary) {
        if (!captureActive || boundary == null || !usable(slot) || !armed[slot]) return;
        if ((boundaryMasks[slot] & boundary.bit) != 0) return;
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                queryPools[slot], boundary.query);
        boundaryMasks[slot] |= boundary.bit;
    }

    /**
     * Begin one terrain-layer draw segment. Returns an opaque token, or -1 when profiling is
     * inactive/capped. The bounded cap prevents portal recursion from growing query usage.
     */
    public static int beginTerrainSegment(int slot, VkCommandBuffer commandBuffer) {
        if (!captureActive || !usable(slot) || !armed[slot]) return -1;
        int segment = terrainSegmentCounts[slot];
        if (segment >= MAX_TERRAIN_SEGMENTS) {
            terrainOverflowCounts[slot]++;
            return -1;
        }
        int query = FIXED_QUERY_COUNT + segment * 2;
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPools[slot], query);
        terrainSegmentCounts[slot] = segment + 1;
        return query;
    }

    /** Complete a terrain segment started by beginTerrainSegment. */
    public static void endTerrainSegment(int slot, VkCommandBuffer commandBuffer, int token) {
        if (!captureActive || !usable(slot) || !armed[slot] || token < FIXED_QUERY_COUNT) return;
        int segment = (token - FIXED_QUERY_COUNT) / 2;
        if (segment < 0 || segment >= terrainSegmentCounts[slot] || segment >= MAX_TERRAIN_SEGMENTS) return;
        int bit = 1 << segment;
        if ((terrainCompletedMasks[slot] & bit) != 0) return;
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPools[slot], token + 1);
        terrainCompletedMasks[slot] |= bit;
    }

    /** Record the end after the final swapchain layout transition but before vkEndCommandBuffer. */
    public static void endFrame(int slot, VkCommandBuffer commandBuffer) {
        if (!captureActive || !usable(slot) || !armed[slot]) return;
        long pool = queryPools[slot];

        // Every query in the contiguous readback range must be written. Preserve the actual
        // boundary mask so these fallback timestamps can never masquerade as pass coverage.
        for (Boundary boundary : Boundary.values()) {
            if ((boundaryMasks[slot] & boundary.bit) == 0) {
                vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, pool, boundary.query);
            }
        }
        int segmentCount = terrainSegmentCounts[slot];
        int completedMask = terrainCompletedMasks[slot];
        for (int segment = 0; segment < segmentCount; segment++) {
            int bit = 1 << segment;
            if ((completedMask & bit) == 0) {
                int query = FIXED_QUERY_COUNT + segment * 2 + 1;
                vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, pool, query);
            }
        }

        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, pool, FRAME_END_QUERY);
        submittedBoundaryMasks[slot] = boundaryMasks[slot];
        submittedTerrainSegmentCounts[slot] = segmentCount;
        submittedTerrainCompletedMasks[slot] = completedMask;
        submittedTerrainOverflowCounts[slot] = terrainOverflowCounts[slot];
        ended[slot] = true;
    }

    /** Mark timestamp data as GPU-owned only after the graphics submit succeeds. */
    public static void markSubmitted(int slot) {
        if (!usable(slot)) return;
        if (captureActive && armed[slot] && ended[slot]) pending[slot] = true;
        armed[slot] = false;
        ended[slot] = false;
    }

    /**
     * Resolve one slot after Renderer has already waited its frame fence. This must remain
     * non-blocking: the fence is the proof that all timestamp writes have completed.
     */
    public static void retireFrame(int slot) {
        if (!usable(slot) || !pending[slot]) return;
        readSlot(slot, false);
    }

    /**
     * Flush outstanding tail queries and emit capture-wide summaries. Intended for the
     * automated benchmark after PerformanceProfiler.endFrame(), outside measured frame time.
     */
    public static void emitCaptureSummary() {
        if (!REQUESTED) return;
        resolvePending(true);

        long avg = average(samples, sampledFrames);
        long max = maximum(samples, sampledFrames);
        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "gpu_timestamps requested=true active=%s status=%s scope=%s includes_helper_submissions=false includes_present=false measured_frames=%d sampled_frames=%d sample_cap=%d dropped_samples=%d read_failures=%d timestamp_valid_bits=%d timestamp_period_ns=%.6f main_graphics_ms_avg=%.3f main_graphics_ms_p50=%.3f main_graphics_ms_p95=%.3f main_graphics_ms_p99=%.3f main_graphics_ms_max=%.3f",
                active, status, SCOPE, measuredFrames, sampledFrames, MAX_SAMPLES, droppedSamples, readFailures,
                timestampValidBits, timestampPeriodNanos,
                millis(avg), millis(percentile(samples, sampledFrames, 0.50D)),
                millis(percentile(samples, sampledFrames, 0.95D)),
                millis(percentile(samples, sampledFrames, 0.99D)), millis(max)));

        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "gpu_passes scope=%s breakdown_frames=%d invalid_frames=%d terrain_segments=%d terrain_segment_drops=%d max_terrain_segments_per_frame=%d %s %s %s %s %s %s %s",
                SCOPE, breakdownFrames, breakdownInvalidFrames, terrainSegments, terrainSegmentDrops,
                MAX_TERRAIN_SEGMENTS,
                passStats("pre_world", PassSeries.PRE_WORLD),
                passStats("world", PassSeries.WORLD),
                passStats("terrain", PassSeries.TERRAIN),
                passStats("world_other", PassSeries.WORLD_OTHER),
                passStats("between_world_hud", PassSeries.BETWEEN_WORLD_HUD),
                passStats("hud", PassSeries.HUD),
                passStats("tail", PassSeries.TAIL)));

        if (AUTOMATED_BENCHMARK) captureActive = false;
    }

    /** Called only after device idleness has been established. */
    public static void destroy() {
        destroyPools();
        active = false;
        initialized = false;
        captureActive = false;
        if (REQUESTED && "active".equals(status)) status = "destroyed";
    }

    public static void verifyForCi() {
        if (!initialCaptureActive(true, false)
                || initialCaptureActive(true, true)
                || initialCaptureActive(false, false)
                || initialCaptureActive(false, true)) {
            throw new IllegalStateException("GPU timestamp capture-boundary contract is invalid");
        }
        if (MAX_SAMPLES < 8192 || FIXED_QUERY_COUNT != 6 || QUERY_COUNT != 70
                || MAX_TERRAIN_SEGMENTS != 32 || SCOPE.isBlank()) {
            throw new IllegalStateException("GPU timestamp profiler capacity/scope contract is invalid");
        }
        if (deltaTicks(100L, 150L, 64) != 50L
                || deltaTicks(250L, 5L, 8) != 11L
                || deltaTicks(5L, 5L, 8) != 0L) {
            throw new IllegalStateException("GPU timestamp wrap arithmetic is invalid");
        }
    }

    private static boolean initialCaptureActive(boolean requested, boolean automatedBenchmark) {
        return requested && !automatedBenchmark;
    }

    private static void resetMeasurements() {
        sampledFrames = 0;
        measuredFrames = 0L;
        droppedSamples = 0L;
        readFailures = 0L;
        breakdownFrames = 0L;
        breakdownInvalidFrames = 0L;
        terrainSegments = 0L;
        terrainSegmentDrops = 0L;
        if (passSampleCounts != null) Arrays.fill(passSampleCounts, 0);
        if (passSampleSums != null) Arrays.fill(passSampleSums, 0L);
        if (passSampleMax != null) Arrays.fill(passSampleMax, 0L);
    }

    private static boolean usable(int slot) {
        return active && queryPools != null && slot >= 0 && slot < queryPools.length;
    }

    private static void resolvePending(boolean wait) {
        if (!active || pending == null) return;
        for (int slot = 0; slot < pending.length; slot++) {
            if (pending[slot]) readSlot(slot, wait);
        }
    }

    private static void readSlot(int slot, boolean wait) {
        int segmentCount = submittedTerrainSegmentCounts[slot];
        int queryCount = FIXED_QUERY_COUNT + segmentCount * 2;
        try (MemoryStack stack = stackPush()) {
            LongBuffer values = stack.mallocLong(queryCount);
            int flags = VK_QUERY_RESULT_64_BIT | (wait ? VK_QUERY_RESULT_WAIT_BIT : 0);
            int result = vkGetQueryPoolResults(Device.device, queryPools[slot], 0, queryCount,
                    values, Long.BYTES, flags);
            if (result == VK_NOT_READY && !wait) {
                // A signaled frame fence should make this impossible, but preserve the
                // data rather than discarding it if a driver reports late availability.
                return;
            }
            if (result != VK_SUCCESS) {
                pending[slot] = false;
                readFailures++;
                status = "query_read_failed_" + result;
                Initializer.LOGGER.warn("VulkanMod GPU timestamp query read failed with {}", result);
                return;
            }

            pending[slot] = false;
            record(values, submittedBoundaryMasks[slot], segmentCount,
                    submittedTerrainCompletedMasks[slot], submittedTerrainOverflowCounts[slot]);
        }
    }

    private static void record(LongBuffer values, int boundaryMask, int segmentCount,
                               int completedMask, int terrainOverflow) {
        long frameNanos = durationNanos(values.get(FRAME_START_QUERY), values.get(FRAME_END_QUERY));
        if (frameNanos <= 0L) {
            readFailures++;
            return;
        }

        measuredFrames++;
        if (sampledFrames < MAX_SAMPLES) samples[sampledFrames++] = frameNanos;
        else droppedSamples++;

        terrainSegmentDrops += terrainOverflow;
        int completeSegments = Integer.bitCount(completedMask);
        terrainSegments += completeSegments;

        if (boundaryMask == ALL_BOUNDARIES_MASK) {
            long preWorld = durationNanos(values.get(FRAME_START_QUERY), values.get(WORLD_BEGIN_QUERY));
            long world = durationNanos(values.get(WORLD_BEGIN_QUERY), values.get(WORLD_END_QUERY));
            long between = durationNanos(values.get(WORLD_END_QUERY), values.get(HUD_BEGIN_QUERY));
            long hud = durationNanos(values.get(HUD_BEGIN_QUERY), values.get(HUD_END_QUERY));
            long tail = durationNanos(values.get(HUD_END_QUERY), values.get(FRAME_END_QUERY));

            long terrain = 0L;
            boolean valid = preWorld >= 0L && world >= 0L && between >= 0L && hud >= 0L && tail >= 0L;
            for (int segment = 0; valid && segment < segmentCount; segment++) {
                int bit = 1 << segment;
                if ((completedMask & bit) == 0) continue;
                int query = FIXED_QUERY_COUNT + segment * 2;
                long duration = durationNanos(values.get(query), values.get(query + 1));
                if (duration < 0L) valid = false;
                else terrain += duration;
            }

            // Terrain segments are disjoint and recorded inside the outer world span. Allow a
            // tiny rounding tolerance because each timestamp delta is converted independently.
            if (valid && terrain <= world + 1_000L) {
                breakdownFrames++;
                recordPass(PassSeries.PRE_WORLD, preWorld);
                recordPass(PassSeries.WORLD, world);
                recordPass(PassSeries.TERRAIN, terrain);
                recordPass(PassSeries.WORLD_OTHER, Math.max(0L, world - terrain));
                recordPass(PassSeries.BETWEEN_WORLD_HUD, between);
                recordPass(PassSeries.HUD, hud);
                recordPass(PassSeries.TAIL, tail);
            } else {
                breakdownInvalidFrames++;
            }
        }

        if (!smokeResultAnnounced && Boolean.getBoolean("vulkanmod.smokeTest")) {
            smokeResultAnnounced = true;
            Initializer.LOGGER.info("VULKANMOD_GPU_TIMESTAMP_SMOKE_OK scope={} main_graphics_ms={}",
                    SCOPE, String.format(Locale.ROOT, "%.3f", millis(frameNanos)));
        }
    }

    private static long durationNanos(long start, long end) {
        long ticks = deltaTicks(start, end, timestampValidBits);
        if (ticks < 0L) return -1L;
        double nanosDouble = ticks * timestampPeriodNanos;
        if (!(nanosDouble >= 0.0D) || !Double.isFinite(nanosDouble)) return -1L;
        return Math.round(nanosDouble);
    }

    private static long deltaTicks(long start, long end, int validBits) {
        long delta = end - start;
        if (validBits <= 0 || validBits > 64) return -1L;
        if (validBits < 64) {
            long mask = (1L << validBits) - 1L;
            delta &= mask;
        }
        return delta < 0L ? -1L : delta;
    }

    private static void recordPass(PassSeries series, long nanos) {
        int ordinal = series.ordinal();
        int count = passSampleCounts[ordinal];
        if (count < MAX_SAMPLES) {
            passSamples[ordinal][count] = nanos;
            passSampleCounts[ordinal] = count + 1;
        }
        passSampleSums[ordinal] += nanos;
        passSampleMax[ordinal] = Math.max(passSampleMax[ordinal], nanos);
    }

    private static String passStats(String name, PassSeries series) {
        int ordinal = series.ordinal();
        int count = passSampleCounts[ordinal];
        long avg = count == 0 ? 0L : passSampleSums[ordinal] / count;
        long p95 = percentile(passSamples[ordinal], count, 0.95D);
        long max = passSampleMax[ordinal];
        return String.format(Locale.ROOT, "%s_samples=%d %s_ms_avg=%.3f %s_ms_p95=%.3f %s_ms_max=%.3f",
                name, count, name, millis(avg), name, millis(p95), name, millis(max));
    }

    private static long average(long[] values, int count) {
        if (count == 0) return 0L;
        long sum = 0L;
        for (int i = 0; i < count; i++) sum += values[i];
        return sum / count;
    }

    private static long maximum(long[] values, int count) {
        long max = 0L;
        for (int i = 0; i < count; i++) max = Math.max(max, values[i]);
        return max;
    }

    private static long percentile(long[] values, int count, double percentile) {
        if (count == 0) return 0L;
        System.arraycopy(values, 0, sortScratch, 0, count);
        Arrays.sort(sortScratch, 0, count);
        int index = (int)Math.ceil(percentile * count) - 1;
        index = Math.max(0, Math.min(count - 1, index));
        return sortScratch[index];
    }

    private static int graphicsTimestampValidBits() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, null);
            VkQueueFamilyProperties.Buffer properties = VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, properties);
            int family = Queue.getQueueFamilies().graphicsFamily;
            return family >= 0 && family < properties.capacity() ? properties.get(family).timestampValidBits() : 0;
        }
    }

    private static void destroyPools() {
        long[] pools = queryPools;
        queryPools = null;
        armed = null;
        ended = null;
        pending = null;
        boundaryMasks = null;
        terrainSegmentCounts = null;
        terrainCompletedMasks = null;
        terrainOverflowCounts = null;
        submittedBoundaryMasks = null;
        submittedTerrainSegmentCounts = null;
        submittedTerrainCompletedMasks = null;
        submittedTerrainOverflowCounts = null;
        if (pools == null || Device.device == null) return;
        for (long pool : pools) {
            if (pool != VK_NULL_HANDLE) vkDestroyQueryPool(Device.device, pool, null);
        }
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }
}
