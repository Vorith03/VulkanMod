package net.vulkanmod.render.profiling;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Query-later GPU timing at the command-buffer boundary.
 *
 * <p>Every command buffer that crosses this recorder receives a pair of timestamp
 * queries spanning its GPU execution. No semantic category is assigned. Once the
 * command buffer's fence/lifetime proves completion, the raw timestamps and their
 * submission identity are emitted into the JFR flight recording.</p>
 */
public final class GpuTimestampRecorder {
    private static final int MAX_TRACKED_COMMAND_BUFFERS = intProperty(
            "flightRecorderGpuCommandBuffers", 4096, 64, 32768);
    private static final int QUERIES_PER_COMMAND_BUFFER = 2;

    private static final ConcurrentHashMap<Long, Slot> SLOTS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();
    private static final AtomicBoolean CAPACITY_WARNING = new AtomicBoolean();

    private static volatile long queryPool;
    private static volatile float timestampPeriodNanos;
    private static volatile int[] timestampValidBits;
    private static volatile boolean initialized;
    private static volatile boolean unavailable;

    private GpuTimestampRecorder() {
    }

    public static void begin(VkCommandBuffer commandBuffer, int queueFamilyIndex) {
        if (!FlightRecorderCapture.isCapturing() || commandBuffer == null || !ensureInitialized()) return;
        int validBits = validBits(queueFamilyIndex);
        if (validBits <= 0) return;

        Slot slot = slot(commandBuffer.address());
        if (slot == null) return;
        synchronized (slot) {
            if (slot.pending || slot.recording) return;
            slot.validBits = validBits;
            slot.ended = false;
            slot.recording = true;
            int firstQuery = slot.index * QUERIES_PER_COMMAND_BUFFER;
            vkCmdResetQueryPool(commandBuffer, queryPool, firstQuery, QUERIES_PER_COMMAND_BUFFER);
            vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPool, firstQuery);
        }
    }

    public static void end(VkCommandBuffer commandBuffer) {
        if (!FlightRecorderCapture.isCapturing() || commandBuffer == null || !initialized) return;
        Slot slot = SLOTS.get(commandBuffer.address());
        if (slot == null) return;
        synchronized (slot) {
            if (!slot.recording) return;
            int endQuery = slot.index * QUERIES_PER_COMMAND_BUFFER + 1;
            vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPool, endQuery);
            slot.recording = false;
            slot.ended = true;
        }
    }

    public static void submitted(long commandBuffer, long submissionSequence, long queue, long fence) {
        if (!FlightRecorderCapture.isCapturing() || commandBuffer == 0L || !initialized) return;
        Slot slot = SLOTS.get(commandBuffer);
        if (slot == null) return;
        synchronized (slot) {
            if (!slot.ended) return;
            slot.submissionSequence = submissionSequence;
            slot.queue = queue;
            slot.fence = fence;
            slot.pending = true;
        }
    }

    /** Call only after synchronization proves this command buffer's GPU work completed. */
    public static void complete(VkCommandBuffer commandBuffer) {
        if (commandBuffer == null || !initialized) return;
        complete(commandBuffer.address());
    }

    /** Call only after synchronization proves this command buffer's GPU work completed. */
    public static void complete(long commandBuffer) {
        if (commandBuffer == 0L || !initialized) return;
        Slot slot = SLOTS.get(commandBuffer);
        if (slot == null) return;

        synchronized (slot) {
            if (!slot.pending) return;
            int firstQuery = slot.index * QUERIES_PER_COMMAND_BUFFER;
            try (MemoryStack stack = stackPush()) {
                LongBuffer values = stack.mallocLong(2);
                int result = vkGetQueryPoolResults(Device.device, queryPool, firstQuery,
                        QUERIES_PER_COMMAND_BUFFER, values, Long.BYTES, VK_QUERY_RESULT_64_BIT);
                if (result == VK_SUCCESS) {
                    long start = values.get(0);
                    long end = values.get(1);
                    long ticks = timestampDelta(start, end, slot.validBits);
                    long nanos = Math.max(0L, Math.round(ticks * (double) timestampPeriodNanos));
                    FlightRecorderCapture.recordGpuCommandBuffer(
                            commandBuffer, slot.submissionSequence, slot.queue, slot.fence,
                            start, end, ticks, nanos, slot.validBits);
                } else if (result != VK_NOT_READY) {
                    Initializer.LOGGER.warn("VulkanMod GPU timestamp query failed: VkResult {}", result);
                }
            }
            slot.pending = false;
            slot.ended = false;
            slot.submissionSequence = 0L;
            slot.queue = 0L;
            slot.fence = 0L;
        }
    }

    /**
     * Drain all submissions already pending at profiler shutdown. This runs after
     * the measured frame has ended, so the required device-idle wait cannot pollute
     * that frame's benchmark timing, but it keeps the raw GPU tail in the JFR file.
     */
    public static void flushPending() {
        if (!initialized || Device.device == null || !hasPending()) return;
        FlightRecorderCapture.VulkanApiEvent event =
                FlightRecorderCapture.beginVulkanApi("vkDeviceWaitIdle", Device.device.address());
        int result = vkDeviceWaitIdle(Device.device);
        FlightRecorderCapture.endVulkanApi(event, result);
        if (result != VK_SUCCESS) {
            Initializer.LOGGER.warn("VulkanMod flight recorder could not drain pending GPU timestamps: VkResult {}", result);
            return;
        }
        SLOTS.forEach((commandBuffer, slot) -> complete(commandBuffer));
    }

    /** Device must already be idle when this is called. */
    public static synchronized void cleanUp() {
        if (queryPool != 0L && Device.device != null) {
            SLOTS.forEach((commandBuffer, slot) -> complete(commandBuffer));
            vkDestroyQueryPool(Device.device, queryPool, null);
        }
        queryPool = 0L;
        timestampValidBits = null;
        SLOTS.clear();
        NEXT_SLOT.set(0);
        CAPACITY_WARNING.set(false);
        initialized = false;
        unavailable = false;
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) return true;
        if (unavailable || Device.device == null || Device.physicalDevice == null) return false;

        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, null);
            VkQueueFamilyProperties.Buffer properties = VkQueueFamilyProperties.calloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, properties);

            int[] validBits = new int[count.get(0)];
            boolean anyTimestamps = false;
            for (int i = 0; i < validBits.length; i++) {
                validBits[i] = properties.get(i).timestampValidBits();
                anyTimestamps |= validBits[i] > 0;
            }
            if (!anyTimestamps) {
                unavailable = true;
                Initializer.LOGGER.warn("VulkanMod flight recorder: selected Vulkan device exposes no queue timestamp bits");
                return false;
            }

            VkQueryPoolCreateInfo createInfo = VkQueryPoolCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO);
            createInfo.queryType(VK_QUERY_TYPE_TIMESTAMP);
            createInfo.queryCount(MAX_TRACKED_COMMAND_BUFFERS * QUERIES_PER_COMMAND_BUFFER);
            LongBuffer pool = stack.mallocLong(1);
            int result = vkCreateQueryPool(Device.device, createInfo, null, pool);
            if (result != VK_SUCCESS) {
                unavailable = true;
                Initializer.LOGGER.warn("VulkanMod flight recorder: vkCreateQueryPool failed with VkResult {}", result);
                return false;
            }

            queryPool = pool.get(0);
            timestampPeriodNanos = Device.deviceProperties.limits().timestampPeriod();
            timestampValidBits = validBits;
            initialized = true;
            Initializer.LOGGER.info(
                    "VulkanMod GPU command-buffer timestamps enabled; period={} ns, capacity={} command buffers",
                    timestampPeriodNanos, MAX_TRACKED_COMMAND_BUFFERS);
            return true;
        }
    }

    private static boolean hasPending() {
        for (Slot slot : SLOTS.values()) {
            synchronized (slot) {
                if (slot.pending) return true;
            }
        }
        return false;
    }

    private static Slot slot(long commandBuffer) {
        Slot existing = SLOTS.get(commandBuffer);
        if (existing != null) return existing;

        int index = NEXT_SLOT.getAndIncrement();
        if (index >= MAX_TRACKED_COMMAND_BUFFERS) {
            if (CAPACITY_WARNING.compareAndSet(false, true)) {
                Initializer.LOGGER.warn(
                        "VulkanMod flight recorder exhausted {} GPU timestamp command-buffer slots; later buffers will still have CPU/JFR evidence",
                        MAX_TRACKED_COMMAND_BUFFERS);
            }
            return null;
        }
        Slot created = new Slot(index);
        Slot raced = SLOTS.putIfAbsent(commandBuffer, created);
        if (raced != null) return raced;
        return created;
    }

    private static int validBits(int familyIndex) {
        int[] bits = timestampValidBits;
        return bits != null && familyIndex >= 0 && familyIndex < bits.length ? bits[familyIndex] : 0;
    }

    private static long timestampDelta(long start, long end, int validBits) {
        if (validBits >= 64) return end - start;
        long mask = (1L << validBits) - 1L;
        return (end - start) & mask;
    }

    private static int intProperty(String suffix, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(System.getProperty(
                    "vulkanmod.performanceProfiler." + suffix, Integer.toString(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class Slot {
        final int index;
        int validBits;
        boolean recording;
        boolean ended;
        boolean pending;
        long submissionSequence;
        long queue;
        long fence;

        Slot(int index) {
            this.index = index;
        }
    }
}
