package net.vulkanmod.render.profiling;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Locale;

import static org.lwjgl.vulkan.VK10.*;

/** Bounded, separately scoped timing of explicit graphics-queue texture upload batches. */
public final class TextureUploadGpuProfiler {
    private static final int CAPACITY = 64;
    private static final int SAMPLE_CAP = 8192;
    private static final byte FREE = 0, RECORDING = 1, PENDING = 2, FAILED = 3;
    private static final byte[] slots = new byte[CAPACITY];
    private static final int[] epochs = new int[CAPACITY];
    private static int captureEpoch;
    private static final long[] samples = GpuTimestampProfiler.requested() ? new long[SAMPLE_CAP] : null;
    private static final long[] scratch = GpuTimestampProfiler.requested() ? new long[SAMPLE_CAP] : null;
    private static long pool;
    private static int validBits;
    private static double period;
    private static long submitted, measured, capacityDrops, sampleDrops, readFailures, sum, maximum;
    private static int sampleCount;
    private static String status = "disabled";

    private TextureUploadGpuProfiler() {}

    /** Called by the main profiler after graphics timestamp capability qualification. */
    static void create(int bits, double periodNanos) {
        validBits = bits;
        period = periodNanos;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = VkQueryPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
                    .queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(CAPACITY * 2);
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateQueryPool(Device.device, info, null, handle);
            if (result == VK_SUCCESS) {
                pool = handle.get(0);
                status = "active";
            } else {
                status = "create_failed_" + result;
            }
        } catch (RuntimeException failure) {
            status = "create_exception";
            Initializer.LOGGER.warn("VulkanMod texture upload GPU timestamps unavailable", failure);
        }
    }

    /** Only admitted capture batches reserve queries; pending ranges are never reset. */
    public static int begin(VkCommandBuffer commands) {
        if (pool == VK_NULL_HANDLE || !GpuTimestampProfiler.capturing()) return -1;
        int token = reserve(slots);
        if (token < 0) {
            capacityDrops++;
            return -1;
        }
        epochs[token] = captureEpoch;
        vkCmdResetQueryPool(commands, pool, token * 2, 2);
        vkCmdWriteTimestamp(commands, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, pool, token * 2);
        return token;
    }

    public static void end(VkCommandBuffer commands, int token) {
        if (valid(token, RECORDING)) {
            vkCmdWriteTimestamp(commands, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, pool, token * 2 + 1);
        }
    }

    /** Called only after the batch's real queue submission succeeds. */
    public static void submitted(int token) {
        if (valid(token, RECORDING)) {
            slots[token] = PENDING;
            if (epochs[token] == captureEpoch) submitted++;
        }
    }

    /** Once per existing frame retirement, never waits and never reads recording ranges. */
    static void collect(boolean wait) {
        if (pool == VK_NULL_HANDLE) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer values = stack.mallocLong(2);
            for (int token = 0; token < CAPACITY; token++) {
                if (slots[token] != PENDING) continue;
                int result = vkGetQueryPoolResults(Device.device, pool, token * 2, 2, values,
                        Long.BYTES, VK_QUERY_RESULT_64_BIT | (wait ? VK_QUERY_RESULT_WAIT_BIT : 0));
                if (result == VK_NOT_READY) continue;
                if (result != VK_SUCCESS) {
                    // A read error is not proof of completion. Quarantine the range until idle destruction.
                    slots[token] = FAILED;
                    if (epochs[token] == captureEpoch) readFailures++;
                    status = "read_failed_" + result;
                    continue;
                }
                slots[token] = FREE;
                if (epochs[token] != captureEpoch) continue;
                long nanos = duration(values.get(0), values.get(1), validBits, period);
                if (nanos < 0) {
                    readFailures++;
                    continue;
                }
                measured++;
                sum += nanos;
                maximum = Math.max(maximum, nanos);
                if (sampleCount < SAMPLE_CAP) samples[sampleCount++] = nanos;
                else sampleDrops++;
            }
        }
    }

    static void emitSummary() {
        collect(true); // Final submitted tail only, outside the measured frame interval.
        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "texture_upload_gpu requested=true active=%s status=%s scope=explicit_graphics_texture_upload_batches queue=graphics includes_cpu_staging=false includes_copy_compute_and_dependencies=true includes_main_graphics=false submitted_batches=%d measured_batches=%d unresolved_batches=%d query_capacity=%d capacity_drops=%d sampled_batches=%d sample_cap=%d sample_drops=%d read_failures=%d batch_ms_avg=%.3f batch_ms_p95=%.3f batch_ms_max=%.3f batch_ms_sum=%.3f",
                pool != VK_NULL_HANDLE, status, submitted, measured, submitted - measured,
                CAPACITY, capacityDrops, sampleCount, SAMPLE_CAP, sampleDrops, readFailures,
                measured == 0 ? 0.0D : sum / (double)measured / 1_000_000.0D,
                percentile95() / 1_000_000.0D, maximum / 1_000_000.0D, sum / 1_000_000.0D));
    }

    static void resetMeasurements() {
        // Slots remain GPU-owned across capture boundaries. Old results are discarded by epoch.
        // Arming must never introduce a wait into the first measured frame.
        captureEpoch++;
        submitted = measured = capacityDrops = sampleDrops = readFailures = sum = maximum = 0;
        sampleCount = 0;
    }

    /** The caller already established device idleness before query-pool destruction. */
    static void destroy() {
        if (pool != VK_NULL_HANDLE) vkDestroyQueryPool(Device.device, pool, null);
        pool = VK_NULL_HANDLE;
        Arrays.fill(slots, FREE);
        status = "destroyed";
    }

    private static int reserve(byte[] states) {
        for (int i = 0; i < states.length; i++) {
            if (states[i] == FREE) {
                states[i] = RECORDING;
                return i;
            }
        }
        return -1;
    }

    private static boolean valid(int token, byte state) {
        return pool != VK_NULL_HANDLE && token >= 0 && token < CAPACITY && slots[token] == state;
    }

    private static long duration(long start, long end, int bits, double periodNanos) {
        if (bits <= 0 || bits > 64 || !(periodNanos > 0) || !Double.isFinite(periodNanos)) return -1;
        long delta = end - start;
        if (bits < 64) delta &= (1L << bits) - 1;
        double nanos = delta * periodNanos;
        return delta < 0 || !Double.isFinite(nanos) || nanos > Long.MAX_VALUE ? -1 : Math.round(nanos);
    }

    private static long percentile95() {
        if (sampleCount == 0) return 0;
        System.arraycopy(samples, 0, scratch, 0, sampleCount);
        Arrays.sort(scratch, 0, sampleCount);
        return scratch[(int)Math.ceil(sampleCount * 0.95D) - 1];
    }

    static void verifyForCi() {
        byte[] states = {RECORDING, PENDING, FAILED, FREE};
        if (reserve(states) != 3 || reserve(states) != -1 || states[0] != RECORDING
                || states[1] != PENDING || states[2] != FAILED
                || duration(250, 5, 8, 10) != 110 || duration(10, 10, 64, 1) != 0
                || duration(20, 10, 64, 1) != -1 || duration(1, 2, 0, 1) != -1) {
            throw new IllegalStateException("Texture upload GPU timestamp ownership/arithmetic contract failed");
        }
    }

    /** Native CI uses its existing idle boundary; production capture adds no idle wait. */
    public static void verifyNativeForCi() {
        if (!Boolean.getBoolean("vulkanmod.ciScreenshotSmoke")) throw new IllegalStateException("CI-only probe");
        verifyForCi();
        Vulkan.waitIdle();
        collect(false);
        if (pool == VK_NULL_HANDLE || measured == 0 || sum <= 0 || readFailures != 0) {
            throw new IllegalStateException("Texture upload native timestamps were not resolved");
        }
        long before = measured, dropsBefore = capacityDrops;
        // Hold every query range pending without collection, even though the queue can finish them.
        // The 65th submission must still run normally and skip timestamp recording.
        for (int i = 0; i <= CAPACITY; i++) {
            Device.getGraphicsQueue().startRecording();
            Device.getGraphicsQueue().endRecordingAndSubmit();
        }
        Vulkan.waitIdle();
        collect(false);
        if (measured != before + CAPACITY || capacityDrops != dropsBefore + 1 || readFailures != 0) {
            throw new IllegalStateException("Texture upload native query capacity/reuse contract failed");
        }
        // Successful readback releases a slot for the next real batch.
        Device.getGraphicsQueue().startRecording();
        Device.getGraphicsQueue().endRecordingAndSubmit();
        Vulkan.waitIdle();
        collect(false);
        if (measured != before + CAPACITY + 1 || submitted != measured) {
            throw new IllegalStateException("Texture upload native query retirement contract failed");
        }
        Initializer.LOGGER.info("VULKANMOD_TEXTURE_UPLOAD_GPU_SMOKE_OK measured_batches={} capacity={} (real copy/compute batches, bounded overflow, query retirement/reuse)", measured, CAPACITY);
    }
}
