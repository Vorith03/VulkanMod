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
    private static final long PARTITION_TOLERANCE_NANOS = 5_000L;
    private static final long TERRAIN_SUM_TOLERANCE_NANOS = 64L;

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

    private enum BreakdownFailure {
        NONE,
        MISSING_FIXED_MARKERS,
        MARKER_ORDER,
        RECONSTRUCTION,
        TERRAIN_INCOMPLETE,
        TERRAIN_CONTAINMENT
    }

    private static final class BreakdownFailureCounters {
        long invalidFrames;
        long missingFixedMarkerFrames;
        long missingWorldBeginFrames;
        long missingWorldEndFrames;
        long missingHudBeginFrames;
        long missingHudEndFrames;
        long markerOrderFailures;
        long reconstructionFailures;
        long terrainIncompleteFailures;
        long terrainContainmentFailures;

        void reset() {
            invalidFrames = 0L;
            missingFixedMarkerFrames = 0L;
            missingWorldBeginFrames = 0L;
            missingWorldEndFrames = 0L;
            missingHudBeginFrames = 0L;
            missingHudEndFrames = 0L;
            markerOrderFailures = 0L;
            reconstructionFailures = 0L;
            terrainIncompleteFailures = 0L;
            terrainContainmentFailures = 0L;
        }
    }

    private static final long[] samples = REQUESTED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = REQUESTED ? new long[MAX_SAMPLES] : null;
    private static final long[][] passSamples = REQUESTED
            ? new long[PassSeries.values().length][MAX_SAMPLES] : null;
    private static final int[] passSampleCounts = REQUESTED ? new int[PassSeries.values().length] : null;
    private static final long[] passSampleSums = REQUESTED ? new long[PassSeries.values().length] : null;
    private static final long[] passSampleMax = REQUESTED ? new long[PassSeries.values().length] : null;
    private static final long[] breakdownScratch = REQUESTED ? new long[PassSeries.values().length] : null;
    private static final BreakdownFailureCounters breakdownFailures = new BreakdownFailureCounters();

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
    private static boolean passSmokeResultAnnounced;
    private static int timestampValidBits;
    private static double timestampPeriodNanos;
    private static int sampledFrames;
    private static long measuredFrames;
    private static long droppedSamples;
    private static long readFailures;
    private static long breakdownFrames;
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

    static boolean capturing() {
        return active && captureActive;
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

        if (Boolean.getBoolean("vulkanmod.smokeTest")) {
            verifyForCi();
            Initializer.LOGGER.info("VULKANMOD_GPU_TIMESTAMP_CONTRACT_OK");
        }

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
        TextureUploadGpuProfiler.create(timestampValidBits, timestampPeriodNanos);
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
        TextureUploadGpuProfiler.collect(false);
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
        TextureUploadGpuProfiler.emitSummary();

        long avg = average(samples, sampledFrames);
        long max = maximum(samples, sampledFrames);
        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "gpu_timestamps requested=true active=%s status=%s scope=%s includes_helper_submissions=false includes_present=false measured_frames=%d sampled_frames=%d sample_cap=%d dropped_samples=%d read_failures=%d timestamp_valid_bits=%d timestamp_period_ns=%.6f main_graphics_ms_avg=%.3f main_graphics_ms_p50=%.3f main_graphics_ms_p95=%.3f main_graphics_ms_p99=%.3f main_graphics_ms_max=%.3f",
                active, status, SCOPE, measuredFrames, sampledFrames, MAX_SAMPLES, droppedSamples, readFailures,
                timestampValidBits, timestampPeriodNanos,
                millis(avg), millis(percentile(samples, sampledFrames, 0.50D)),
                millis(percentile(samples, sampledFrames, 0.95D)),
                millis(percentile(samples, sampledFrames, 0.99D)), millis(max)));

        long unaccountedFrames = Math.max(0L,
                measuredFrames - breakdownFrames - breakdownFailures.invalidFrames);
        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "gpu_passes scope=%s breakdown_frames=%d invalid_frames=%d unaccounted_frames=%d missing_fixed_marker_frames=%d missing_world_begin_frames=%d missing_world_end_frames=%d missing_hud_begin_frames=%d missing_hud_end_frames=%d marker_order_failures=%d reconstruction_failures=%d terrain_incomplete_failures=%d terrain_containment_failures=%d terrain_segments=%d terrain_segment_drops=%d max_terrain_segments_per_frame=%d %s %s %s %s %s %s %s",
                SCOPE, breakdownFrames, breakdownFailures.invalidFrames, unaccountedFrames,
                breakdownFailures.missingFixedMarkerFrames,
                breakdownFailures.missingWorldBeginFrames, breakdownFailures.missingWorldEndFrames,
                breakdownFailures.missingHudBeginFrames, breakdownFailures.missingHudEndFrames,
                breakdownFailures.markerOrderFailures, breakdownFailures.reconstructionFailures,
                breakdownFailures.terrainIncompleteFailures, breakdownFailures.terrainContainmentFailures,
                terrainSegments, terrainSegmentDrops, MAX_TERRAIN_SEGMENTS,
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
        TextureUploadGpuProfiler.verifyForCi();
        if (!initialCaptureActive(true, false)
                || initialCaptureActive(true, true)
                || initialCaptureActive(false, false)
                || initialCaptureActive(false, true)) {
            throw new IllegalStateException("GPU timestamp capture-boundary contract is invalid");
        }
        if (MAX_SAMPLES < 8192 || FRAME_START_QUERY != 0
                || Boundary.WORLD_BEGIN.query != 1 || Boundary.WORLD_BEGIN.bit != 1
                || Boundary.WORLD_END.query != 2 || Boundary.WORLD_END.bit != 2
                || Boundary.HUD_BEGIN.query != 3 || Boundary.HUD_BEGIN.bit != 4
                || Boundary.HUD_END.query != 4 || Boundary.HUD_END.bit != 8
                || FRAME_END_QUERY != 5 || FIXED_QUERY_COUNT != 6 || QUERY_COUNT != 70
                || MAX_TERRAIN_SEGMENTS != 32 || SCOPE.isBlank()) {
            throw new IllegalStateException("GPU timestamp profiler query layout/capacity contract is invalid");
        }
        if (deltaTicks(100L, 150L, 64) != 50L
                || deltaTicks(250L, 5L, 8) != 11L
                || deltaTicks(5L, 5L, 8) != 0L) {
            throw new IllegalStateException("GPU timestamp wrap arithmetic is invalid");
        }

        long[] complete = new long[FIXED_QUERY_COUNT + 4];
        complete[FRAME_START_QUERY] = 100L;
        complete[WORLD_BEGIN_QUERY] = 110L;
        complete[WORLD_END_QUERY] = 160L;
        complete[HUD_BEGIN_QUERY] = 170L;
        complete[HUD_END_QUERY] = 180L;
        complete[FRAME_END_QUERY] = 200L;
        complete[FIXED_QUERY_COUNT] = 120L;
        complete[FIXED_QUERY_COUNT + 1] = 130L;
        complete[FIXED_QUERY_COUNT + 2] = 140L;
        complete[FIXED_QUERY_COUNT + 3] = 145L;
        long[] decoded = new long[PassSeries.values().length];
        long frameNanos = durationNanos(complete[FRAME_START_QUERY], complete[FRAME_END_QUERY], 64, 10.0D);
        BreakdownFailure result = analyzeBreakdown(LongBuffer.wrap(complete), ALL_BOUNDARIES_MASK,
                2, 0b11, 0, frameNanos, 64, 10.0D, decoded);
        if (result != BreakdownFailure.NONE
                || decoded[PassSeries.PRE_WORLD.ordinal()] != 100L
                || decoded[PassSeries.WORLD.ordinal()] != 500L
                || decoded[PassSeries.TERRAIN.ordinal()] != 150L
                || decoded[PassSeries.WORLD_OTHER.ordinal()] != 350L
                || decoded[PassSeries.BETWEEN_WORLD_HUD.ordinal()] != 100L
                || decoded[PassSeries.HUD.ordinal()] != 100L
                || decoded[PassSeries.TAIL.ordinal()] != 200L) {
            throw new IllegalStateException("GPU timestamp synthetic pass decoding is invalid");
        }

        BreakdownFailureCounters counters = new BreakdownFailureCounters();
        int missingHudMask = Boundary.WORLD_BEGIN.bit | Boundary.WORLD_END.bit;
        BreakdownFailure missing = analyzeBreakdown(LongBuffer.wrap(complete), missingHudMask,
                2, 0b11, 0, frameNanos, 64, 10.0D, decoded);
        accountBreakdownFailure(missing, missingHudMask, counters);
        if (missing != BreakdownFailure.MISSING_FIXED_MARKERS
                || counters.invalidFrames != 1L || counters.missingFixedMarkerFrames != 1L
                || counters.missingWorldBeginFrames != 0L || counters.missingWorldEndFrames != 0L
                || counters.missingHudBeginFrames != 1L || counters.missingHudEndFrames != 1L) {
            throw new IllegalStateException("GPU timestamp missing-marker accounting is invalid");
        }

        long[] outOfOrder = complete.clone();
        outOfOrder[WORLD_END_QUERY] = 105L;
        if (analyzeBreakdown(LongBuffer.wrap(outOfOrder), ALL_BOUNDARIES_MASK,
                2, 0b11, 0, frameNanos, 64, 10.0D, decoded) != BreakdownFailure.MARKER_ORDER) {
            throw new IllegalStateException("GPU timestamp marker-order rejection is invalid");
        }

        long[] reconstruction = complete.clone();
        reconstruction[WORLD_BEGIN_QUERY] = 20L;
        reconstruction[WORLD_END_QUERY] = 10L;
        reconstruction[HUD_BEGIN_QUERY] = 20L;
        reconstruction[HUD_END_QUERY] = 30L;
        long wrappedFrameNanos = durationNanos(250L, 60L, 8, 100.0D);
        reconstruction[FRAME_START_QUERY] = 250L;
        reconstruction[FRAME_END_QUERY] = 60L;
        if (analyzeBreakdown(LongBuffer.wrap(reconstruction), ALL_BOUNDARIES_MASK,
                0, 0, 0, wrappedFrameNanos, 8, 100.0D, decoded) != BreakdownFailure.RECONSTRUCTION) {
            throw new IllegalStateException("GPU timestamp reconstruction rejection is invalid");
        }

        if (analyzeBreakdown(LongBuffer.wrap(complete), ALL_BOUNDARIES_MASK,
                2, 0b01, 0, frameNanos, 64, 10.0D, decoded) != BreakdownFailure.TERRAIN_INCOMPLETE
                || analyzeBreakdown(LongBuffer.wrap(complete), ALL_BOUNDARIES_MASK,
                2, 0b11, 1, frameNanos, 64, 10.0D, decoded) != BreakdownFailure.TERRAIN_INCOMPLETE) {
            throw new IllegalStateException("GPU timestamp terrain completeness rejection is invalid");
        }

        long[] terrainOutsideWorld = complete.clone();
        terrainOutsideWorld[FIXED_QUERY_COUNT] = 1000L;
        terrainOutsideWorld[FIXED_QUERY_COUNT + 1] = 1010L;
        if (analyzeBreakdown(LongBuffer.wrap(terrainOutsideWorld), ALL_BOUNDARIES_MASK,
                2, 0b11, 0, frameNanos, 64, 10.0D, decoded) != BreakdownFailure.TERRAIN_CONTAINMENT) {
            throw new IllegalStateException("GPU timestamp terrain containment rejection is invalid");
        }
    }

    private static boolean initialCaptureActive(boolean requested, boolean automatedBenchmark) {
        return requested && !automatedBenchmark;
    }

    private static void resetMeasurements() {
        TextureUploadGpuProfiler.resetMeasurements();
        sampledFrames = 0;
        measuredFrames = 0L;
        droppedSamples = 0L;
        readFailures = 0L;
        breakdownFrames = 0L;
        terrainSegments = 0L;
        terrainSegmentDrops = 0L;
        breakdownFailures.reset();
        if (passSampleCounts != null) Arrays.fill(passSampleCounts, 0);
        if (passSampleSums != null) Arrays.fill(passSampleSums, 0L);
        if (passSampleMax != null) Arrays.fill(passSampleMax, 0L);
    }

    private static boolean usable(int slot) {
        return active && queryPools != null && slot >= 0 && slot < queryPools.length;
    }

    private static void resolvePending(boolean wait) {
        TextureUploadGpuProfiler.collect(wait);
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
        terrainSegments += Integer.bitCount(completedMask);

        BreakdownFailure failure = analyzeBreakdown(values, boundaryMask, segmentCount,
                completedMask, terrainOverflow, frameNanos, timestampValidBits,
                timestampPeriodNanos, breakdownScratch);
        if (failure == BreakdownFailure.NONE) {
            breakdownFrames++;
            for (PassSeries series : PassSeries.values()) {
                recordPass(series, breakdownScratch[series.ordinal()]);
            }
            if (!passSmokeResultAnnounced && Boolean.getBoolean("vulkanmod.smokeTest")) {
                passSmokeResultAnnounced = true;
                Initializer.LOGGER.info("VULKANMOD_GPU_PASS_SMOKE_OK scope={} world_ms={} terrain_ms={} hud_ms={}",
                        SCOPE,
                        String.format(Locale.ROOT, "%.3f", millis(breakdownScratch[PassSeries.WORLD.ordinal()])),
                        String.format(Locale.ROOT, "%.3f", millis(breakdownScratch[PassSeries.TERRAIN.ordinal()])),
                        String.format(Locale.ROOT, "%.3f", millis(breakdownScratch[PassSeries.HUD.ordinal()])));
            }
        } else {
            accountBreakdownFailure(failure, boundaryMask, breakdownFailures);
        }

        if (!smokeResultAnnounced && Boolean.getBoolean("vulkanmod.smokeTest")) {
            smokeResultAnnounced = true;
            Initializer.LOGGER.info("VULKANMOD_GPU_TIMESTAMP_SMOKE_OK scope={} main_graphics_ms={}",
                    SCOPE, String.format(Locale.ROOT, "%.3f", millis(frameNanos)));
        }
    }

    private static BreakdownFailure analyzeBreakdown(LongBuffer values, int boundaryMask,
                                                     int segmentCount, int completedMask,
                                                     int terrainOverflow, long frameNanos,
                                                     int validBits, double periodNanos,
                                                     long[] decoded) {
        if (boundaryMask != ALL_BOUNDARIES_MASK) return BreakdownFailure.MISSING_FIXED_MARKERS;

        long preWorld = durationNanos(values.get(FRAME_START_QUERY), values.get(WORLD_BEGIN_QUERY),
                validBits, periodNanos);
        long world = durationNanos(values.get(WORLD_BEGIN_QUERY), values.get(WORLD_END_QUERY),
                validBits, periodNanos);
        long between = durationNanos(values.get(WORLD_END_QUERY), values.get(HUD_BEGIN_QUERY),
                validBits, periodNanos);
        long hud = durationNanos(values.get(HUD_BEGIN_QUERY), values.get(HUD_END_QUERY),
                validBits, periodNanos);
        long tail = durationNanos(values.get(HUD_END_QUERY), values.get(FRAME_END_QUERY),
                validBits, periodNanos);
        if (preWorld < 0L || world < 0L || between < 0L || hud < 0L || tail < 0L) {
            return BreakdownFailure.MARKER_ORDER;
        }

        long upper = frameNanos + PARTITION_TOLERANCE_NANOS;
        if (preWorld > upper || world > upper || between > upper || hud > upper || tail > upper) {
            return BreakdownFailure.RECONSTRUCTION;
        }
        long partition = preWorld + world + between + hud + tail;
        if (Math.abs(partition - frameNanos) > PARTITION_TOLERANCE_NANOS) {
            return BreakdownFailure.RECONSTRUCTION;
        }

        if (segmentCount < 0 || segmentCount > MAX_TERRAIN_SEGMENTS) {
            return BreakdownFailure.TERRAIN_INCOMPLETE;
        }
        int completeSegments = Integer.bitCount(completedMask);
        if (terrainOverflow != 0 || completeSegments != segmentCount) {
            return BreakdownFailure.TERRAIN_INCOMPLETE;
        }

        long terrain = 0L;
        long worldBeginTimestamp = values.get(WORLD_BEGIN_QUERY);
        for (int segment = 0; segment < segmentCount; segment++) {
            int bit = 1 << segment;
            if ((completedMask & bit) == 0) return BreakdownFailure.TERRAIN_INCOMPLETE;

            int query = FIXED_QUERY_COUNT + segment * 2;
            long segmentStart = values.get(query);
            long segmentEnd = values.get(query + 1);
            long duration = durationNanos(segmentStart, segmentEnd, validBits, periodNanos);
            long startOffset = durationNanos(worldBeginTimestamp, segmentStart, validBits, periodNanos);
            long endOffset = durationNanos(worldBeginTimestamp, segmentEnd, validBits, periodNanos);
            if (duration < 0L || startOffset < 0L || endOffset < 0L
                    || startOffset > world
                    || endOffset > world
                    || endOffset < startOffset) {
                return BreakdownFailure.TERRAIN_CONTAINMENT;
            }
            terrain += duration;
            if (terrain > world + TERRAIN_SUM_TOLERANCE_NANOS) {
                return BreakdownFailure.TERRAIN_CONTAINMENT;
            }
        }

        decoded[PassSeries.PRE_WORLD.ordinal()] = preWorld;
        decoded[PassSeries.WORLD.ordinal()] = world;
        decoded[PassSeries.TERRAIN.ordinal()] = terrain;
        decoded[PassSeries.WORLD_OTHER.ordinal()] = Math.max(0L, world - terrain);
        decoded[PassSeries.BETWEEN_WORLD_HUD.ordinal()] = between;
        decoded[PassSeries.HUD.ordinal()] = hud;
        decoded[PassSeries.TAIL.ordinal()] = tail;
        return BreakdownFailure.NONE;
    }

    private static void accountBreakdownFailure(BreakdownFailure failure, int boundaryMask,
                                                BreakdownFailureCounters counters) {
        if (failure == BreakdownFailure.NONE) return;
        counters.invalidFrames++;
        switch (failure) {
            case MISSING_FIXED_MARKERS -> {
                counters.missingFixedMarkerFrames++;
                if ((boundaryMask & Boundary.WORLD_BEGIN.bit) == 0) counters.missingWorldBeginFrames++;
                if ((boundaryMask & Boundary.WORLD_END.bit) == 0) counters.missingWorldEndFrames++;
                if ((boundaryMask & Boundary.HUD_BEGIN.bit) == 0) counters.missingHudBeginFrames++;
                if ((boundaryMask & Boundary.HUD_END.bit) == 0) counters.missingHudEndFrames++;
            }
            case MARKER_ORDER -> counters.markerOrderFailures++;
            case RECONSTRUCTION -> counters.reconstructionFailures++;
            case TERRAIN_INCOMPLETE -> counters.terrainIncompleteFailures++;
            case TERRAIN_CONTAINMENT -> counters.terrainContainmentFailures++;
        }
    }

    private static long durationNanos(long start, long end) {
        return durationNanos(start, end, timestampValidBits, timestampPeriodNanos);
    }

    private static long durationNanos(long start, long end, int validBits, double periodNanos) {
        long ticks = deltaTicks(start, end, validBits);
        if (ticks < 0L) return -1L;
        double nanosDouble = ticks * periodNanos;
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
        if (count >= MAX_SAMPLES) return;
        passSamples[ordinal][count] = nanos;
        passSampleCounts[ordinal] = count + 1;
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
        TextureUploadGpuProfiler.destroy();
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
