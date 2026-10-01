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
 */
public final class GpuTimestampProfiler {
    private static final boolean REQUESTED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.gpuTimestamps");
    private static final boolean AUTOMATED_BENCHMARK = REQUESTED
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");
    private static final int QUERY_COUNT = 2;
    private static final int MAX_SAMPLES = 65_536;
    public static final String SCOPE = "main_graphics_command_buffer";

    private static final long[] samples = REQUESTED ? new long[MAX_SAMPLES] : null;
    private static final long[] sortScratch = REQUESTED ? new long[MAX_SAMPLES] : null;

    private static long[] queryPools;
    private static boolean[] armed;
    private static boolean[] ended;
    private static boolean[] pending;
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
                "VulkanMod GPU timestamp profiler enabled: scope={} frameSlots={} validBits={} timestampPeriodNs={}",
                SCOPE, frames, timestampValidBits, String.format(Locale.ROOT, "%.6f", timestampPeriodNanos));
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
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, pool, 0);
        armed[slot] = true;
        ended[slot] = false;
    }

    /** Record the end after the final swapchain layout transition but before vkEndCommandBuffer. */
    public static void endFrame(int slot, VkCommandBuffer commandBuffer) {
        if (!captureActive || !usable(slot) || !armed[slot]) return;
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPools[slot], 1);
        ended[slot] = true;
    }

    /** Mark a timestamp pair as GPU-owned only after the graphics submit succeeds. */
    public static void markSubmitted(int slot) {
        if (!usable(slot)) return;
        if (captureActive && armed[slot] && ended[slot]) pending[slot] = true;
        armed[slot] = false;
        ended[slot] = false;
    }

    /**
     * Resolve one slot after Renderer has already waited its frame fence. This must remain
     * non-blocking: the fence is the proof that both timestamp writes have completed.
     */
    public static void retireFrame(int slot) {
        if (!usable(slot) || !pending[slot]) return;
        readSlot(slot, false);
    }

    /**
     * Flush outstanding tail queries and emit one capture-wide summary. Intended for the
     * automated benchmark after PerformanceProfiler.endFrame(), outside measured frame time.
     */
    public static void emitCaptureSummary() {
        if (!REQUESTED) return;
        resolvePending(true);

        long avg = 0L;
        long max = 0L;
        for (int i = 0; i < sampledFrames; i++) {
            avg += samples[i];
            max = Math.max(max, samples[i]);
        }
        avg = sampledFrames == 0 ? 0L : avg / sampledFrames;

        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "gpu_timestamps requested=true active=%s status=%s scope=%s includes_helper_submissions=false includes_present=false measured_frames=%d sampled_frames=%d sample_cap=%d dropped_samples=%d read_failures=%d timestamp_valid_bits=%d timestamp_period_ns=%.6f main_graphics_ms_avg=%.3f main_graphics_ms_p50=%.3f main_graphics_ms_p95=%.3f main_graphics_ms_p99=%.3f main_graphics_ms_max=%.3f",
                active, status, SCOPE, measuredFrames, sampledFrames, MAX_SAMPLES, droppedSamples, readFailures,
                timestampValidBits, timestampPeriodNanos,
                millis(avg), millis(percentile(0.50D)), millis(percentile(0.95D)),
                millis(percentile(0.99D)), millis(max)));

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
        if (MAX_SAMPLES < 8192 || QUERY_COUNT != 2 || SCOPE.isBlank()) {
            throw new IllegalStateException("GPU timestamp profiler capacity/scope contract is invalid");
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
        try (MemoryStack stack = stackPush()) {
            LongBuffer values = stack.mallocLong(QUERY_COUNT);
            int flags = VK_QUERY_RESULT_64_BIT | (wait ? VK_QUERY_RESULT_WAIT_BIT : 0);
            int result = vkGetQueryPoolResults(Device.device, queryPools[slot], 0, QUERY_COUNT,
                    values, Long.BYTES, flags);
            if (result == VK_NOT_READY && !wait) {
                // A signaled frame fence should make this impossible, but preserve the
                // pair rather than discarding it if a driver reports late availability.
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
            record(values.get(0), values.get(1));
        }
    }

    private static void record(long start, long end) {
        long delta = end - start;
        if (timestampValidBits < 64) {
            long mask = (1L << timestampValidBits) - 1L;
            delta &= mask;
        }
        if (delta <= 0L) {
            readFailures++;
            return;
        }

        double nanosDouble = delta * timestampPeriodNanos;
        if (!(nanosDouble >= 0.0D) || !Double.isFinite(nanosDouble)) {
            readFailures++;
            return;
        }

        long nanos = Math.round(nanosDouble);
        measuredFrames++;
        if (sampledFrames < MAX_SAMPLES) samples[sampledFrames++] = nanos;
        else droppedSamples++;

        if (!smokeResultAnnounced && Boolean.getBoolean("vulkanmod.smokeTest")) {
            smokeResultAnnounced = true;
            Initializer.LOGGER.info("VULKANMOD_GPU_TIMESTAMP_SMOKE_OK scope={} main_graphics_ms={}",
                    SCOPE, String.format(Locale.ROOT, "%.3f", millis(nanos)));
        }
    }

    private static long percentile(double percentile) {
        if (sampledFrames == 0) return 0L;
        System.arraycopy(samples, 0, sortScratch, 0, sampledFrames);
        Arrays.sort(sortScratch, 0, sampledFrames);
        int index = (int)Math.ceil(percentile * sampledFrames) - 1;
        index = Math.max(0, Math.min(sampledFrames - 1, index));
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
        if (pools == null || Device.device == null) return;
        for (long pool : pools) {
            if (pool != VK_NULL_HANDLE) vkDestroyQueryPool(Device.device, pool, null);
        }
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }
}
