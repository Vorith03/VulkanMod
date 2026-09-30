package net.vulkanmod.render.profiling;

import jdk.jfr.Category;
import jdk.jfr.Configuration;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import net.vulkanmod.Initializer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

/**
 * Raw, query-later performance capture for VulkanMod profiling runs.
 *
 * <p>This deliberately does not know about terrain, entities, textures, ticks, or
 * any other subsystem whose importance would have to be predicted in advance.
 * JFR continuously samples the JVM and records runtime events across all threads.
 * VulkanMod adds only universal correlation boundaries: Minecraft frames and the
 * Vulkan API operations through which CPU/GPU synchronization and submissions pass.</p>
 *
 * <p>The existing hand-instrumented {@link PerformanceProfiler} summaries remain
 * useful as cheap derived views, but this recording is the source artifact for
 * questions that were not anticipated when the benchmark was run.</p>
 */
public final class FlightRecorderCapture {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.parseBoolean(System.getProperty(
                    "vulkanmod.performanceProfiler.flightRecorder", "true"));
    private static final String OUTPUT_PROPERTY = "vulkanmod.performanceProfiler.flightRecorderOutput";
    private static final long JAVA_SAMPLE_MS = longProperty("flightRecorderJavaSampleMillis", 2L, 1L, 100L);
    private static final long NATIVE_SAMPLE_MS = longProperty("flightRecorderNativeSampleMillis", 5L, 1L, 100L);
    private static final ThreadMXBean THREAD_BEAN = ENABLED ? ManagementFactory.getThreadMXBean() : null;
    private static final boolean THREAD_CPU_SUPPORTED = ENABLED && THREAD_BEAN.isCurrentThreadCpuTimeSupported();
    private static final AtomicLong SUBMISSION_SEQUENCE = new AtomicLong();

    private static volatile Recording recording;
    private static volatile boolean capturing;
    private static volatile Path outputPath;
    private static FrameEvent frameEvent;
    private static long frameSequence;
    private static long frameCpuStart = -1L;
    private static boolean failed;

    private FlightRecorderCapture() {
    }

    /** Start the raw recording on first use. Repeated calls are intentionally cheap. */
    public static synchronized boolean startIfNeeded() {
        if (!ENABLED || failed) return false;
        if (capturing) return true;

        try {
            Configuration configuration = Configuration.getConfiguration("profile");
            Recording next = new Recording(configuration);
            next.setName("VulkanMod performance flight recorder");
            next.setToDisk(true);

            // The stock profile configuration already captures JVM/GC/allocation/JIT
            // telemetry. Tighten only generic evidence needed to explain short frame
            // stalls; these are not application-specific subsystem metrics.
            next.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(JAVA_SAMPLE_MS));
            next.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(NATIVE_SAMPLE_MS));
            next.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1L)).withStackTrace();
            next.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1L)).withStackTrace();
            next.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1L)).withStackTrace();
            next.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1L)).withStackTrace();
            next.enable("jdk.ThreadCPULoad").withPeriod(Duration.ofMillis(10L));
            next.enable(FrameEvent.class).withoutStackTrace();
            next.enable(VulkanSubmissionEvent.class).withStackTrace().withoutThreshold();
            next.enable(VulkanFenceWaitEvent.class).withStackTrace().withoutThreshold();
            next.enable(VulkanQueueIdleEvent.class).withStackTrace().withoutThreshold();
            next.enable(VulkanApiEvent.class).withStackTrace().withoutThreshold();
            next.enable(GpuCommandBufferEvent.class).withoutStackTrace();

            Path nextOutput = resolveOutputPath();
            Path parent = nextOutput.getParent();
            if (parent != null) Files.createDirectories(parent);

            // Let JFR own persistence. Calling Recording.dump() from our own JVM
            // shutdown hook races JFR's shutdown cleanup and can observe its temporary
            // chunk after it has already been removed. A destination is written when
            // an explicit stop completes, while dump-on-exit covers interrupted/manual
            // runs without competing with JFR's internal shutdown hooks.
            next.setDestination(nextOutput);
            next.setDumpOnExit(true);
            next.start();
            outputPath = nextOutput;
            recording = next;
            capturing = true;
            Initializer.LOGGER.info(
                    "VulkanMod raw performance flight recorder enabled; output: {}; Java sample={} ms; native sample={} ms",
                    nextOutput.toAbsolutePath(), JAVA_SAMPLE_MS, NATIVE_SAMPLE_MS);
            return true;
        } catch (IOException | ParseException | RuntimeException failure) {
            failed = true;
            capturing = false;
            Initializer.LOGGER.error("VulkanMod could not start the raw JFR performance capture", failure);
            return false;
        }
    }

    /** Begin one generic Minecraft runTick envelope. Called only by the render thread. */
    public static void beginFrame(boolean tickRequested) {
        if (!ENABLED) return;
        if (!capturing && !startIfNeeded()) return;
        if (frameEvent != null) return;

        FrameEvent event = new FrameEvent();
        event.sequence = ++frameSequence;
        event.tickRequested = tickRequested;
        frameCpuStart = currentThreadCpuNanos();
        event.begin();
        frameEvent = event;
    }

    /** End the current generic frame envelope. Called only by the render thread. */
    public static void endFrame() {
        if (!ENABLED) return;
        FrameEvent event = frameEvent;
        frameEvent = null;
        if (event == null) return;
        long cpuEnd = currentThreadCpuNanos();
        event.cpuNanos = frameCpuStart >= 0L && cpuEnd >= frameCpuStart ? cpuEnd - frameCpuStart : -1L;
        frameCpuStart = -1L;
        event.commit();
    }

    /** Begin a universal Vulkan command-buffer submission event. */
    public static VulkanSubmissionEvent beginVulkanSubmission(
            long commandBuffer, long queue, long fence, boolean signalsSemaphore) {
        if (!capturing) return null;
        VulkanSubmissionEvent event = new VulkanSubmissionEvent();
        event.sequence = SUBMISSION_SEQUENCE.incrementAndGet();
        event.commandBuffer = commandBuffer;
        event.queue = queue;
        event.fence = fence;
        event.signalsSemaphore = signalsSemaphore;
        event.begin();
        return event;
    }

    public static void endVulkanSubmission(VulkanSubmissionEvent event, int result) {
        if (event == null) return;
        event.result = result;
        if (result == VK_SUCCESS && event.commandBuffer != 0L) {
            GpuTimestampRecorder.submitted(
                    event.commandBuffer, event.sequence, event.queue, event.fence);
        }
        event.commit();
    }

    /** Begin a fence wait. A zero fence denotes a batched wait. */
    public static VulkanFenceWaitEvent beginVulkanFenceWait(long fence, int fenceCount) {
        if (!capturing) return null;
        VulkanFenceWaitEvent event = new VulkanFenceWaitEvent();
        event.fence = fence;
        event.fenceCount = fenceCount;
        event.begin();
        return event;
    }

    public static void endVulkanFenceWait(VulkanFenceWaitEvent event, int result) {
        if (event == null) return;
        event.result = result;
        event.commit();
    }

    public static VulkanQueueIdleEvent beginVulkanQueueIdle(long queue) {
        if (!capturing) return null;
        VulkanQueueIdleEvent event = new VulkanQueueIdleEvent();
        event.queue = queue;
        event.begin();
        return event;
    }

    public static void endVulkanQueueIdle(VulkanQueueIdleEvent event, int result) {
        if (event == null) return;
        event.result = result;
        event.commit();
    }

    /** Generic duration event for other universal Vulkan API boundaries. */
    public static VulkanApiEvent beginVulkanApi(String operation, long object) {
        if (!capturing) return null;
        VulkanApiEvent event = new VulkanApiEvent();
        event.operation = operation;
        event.object = object;
        event.begin();
        return event;
    }

    public static void endVulkanApi(VulkanApiEvent event, int result) {
        if (event == null) return;
        event.result = result;
        event.commit();
    }

    /** Emit completed raw GPU timestamps correlated to a Vulkan submission sequence. */
    public static void recordGpuCommandBuffer(long commandBuffer, long submissionSequence,
                                               long queue, long fence, long startTicks,
                                               long endTicks, long elapsedTicks,
                                               long gpuNanos, int validBits) {
        if (!capturing) return;
        GpuCommandBufferEvent event = new GpuCommandBufferEvent();
        event.commandBuffer = commandBuffer;
        event.submissionSequence = submissionSequence;
        event.queue = queue;
        event.fence = fence;
        event.startTicks = startTicks;
        event.endTicks = endTicks;
        event.elapsedTicks = elapsedTicks;
        event.gpuNanos = gpuNanos;
        event.timestampValidBits = validBits;
        event.commit();
    }

    /**
     * Stop and persist the raw recording. When a destination is configured JFR
     * writes the recording as part of stop(); dump-on-exit handles JVM shutdown.
     */
    public static synchronized boolean stop(String reason) {
        if (!ENABLED) return true;
        endFrame();
        capturing = false;
        Recording current = recording;
        recording = null;
        if (current == null) return !failed;

        try {
            current.stop();
            Initializer.LOGGER.info("VulkanMod raw performance flight recorder saved: {} ({})",
                    outputPath.toAbsolutePath(), reason);
            return true;
        } catch (RuntimeException failure) {
            failed = true;
            Initializer.LOGGER.error("VulkanMod could not save the raw JFR performance capture", failure);
            return false;
        } finally {
            current.close();
        }
    }

    public static Path outputPath() {
        return outputPath;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static boolean isCapturing() {
        return capturing;
    }

    private static long currentThreadCpuNanos() {
        if (!THREAD_CPU_SUPPORTED) return -1L;
        return THREAD_BEAN.getCurrentThreadCpuTime();
    }

    private static Path resolveOutputPath() {
        String configured = System.getProperty(OUTPUT_PROPERTY, "").trim();
        if (!configured.isEmpty()) return Path.of(configured).toAbsolutePath().normalize();
        return Path.of("logs", "vulkanmod-performance-flight-" + UUID.randomUUID() + ".jfr")
                .toAbsolutePath().normalize();
    }

    private static long longProperty(String suffix, long fallback, long min, long max) {
        try {
            long value = Long.parseLong(System.getProperty(
                    "vulkanmod.performanceProfiler." + suffix, Long.toString(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Name("net.vulkanmod.RunTickFrame")
    @Label("Minecraft runTick frame")
    @Category({"VulkanMod", "Profiler"})
    @Description("Generic frame boundary used to correlate sampled execution with Minecraft frames")
    @StackTrace(false)
    private static final class FrameEvent extends Event {
        @Label("Frame sequence")
        long sequence;
        @Label("Tick requested")
        boolean tickRequested;
        @Label("Render-thread CPU time (ns)")
        long cpuNanos;
    }

    @Name("net.vulkanmod.VulkanSubmission")
    @Label("Vulkan command submission")
    @Category({"VulkanMod", "Vulkan"})
    @Description("CPU-side command-buffer finalization and queue submission at the universal Vulkan submission boundary")
    @StackTrace(true)
    public static final class VulkanSubmissionEvent extends Event {
        @Label("Submission sequence")
        long sequence;
        @Label("Command buffer")
        long commandBuffer;
        @Label("Queue")
        long queue;
        @Label("Fence")
        long fence;
        @Label("Signals semaphore")
        boolean signalsSemaphore;
        @Label("VkResult")
        int result;
    }

    @Name("net.vulkanmod.VulkanFenceWait")
    @Label("Vulkan fence wait")
    @Category({"VulkanMod", "Vulkan"})
    @Description("CPU wall time blocked at vkWaitForFences")
    @StackTrace(true)
    public static final class VulkanFenceWaitEvent extends Event {
        @Label("Fence")
        long fence;
        @Label("Fence count")
        int fenceCount;
        @Label("VkResult")
        int result;
    }

    @Name("net.vulkanmod.VulkanQueueIdle")
    @Label("Vulkan queue idle wait")
    @Category({"VulkanMod", "Vulkan"})
    @Description("CPU wall time blocked at vkQueueWaitIdle")
    @StackTrace(true)
    public static final class VulkanQueueIdleEvent extends Event {
        @Label("Queue")
        long queue;
        @Label("VkResult")
        int result;
    }

    @Name("net.vulkanmod.VulkanApi")
    @Label("Vulkan API duration")
    @Category({"VulkanMod", "Vulkan"})
    @Description("Generic duration and caller for Vulkan API operations that may block or delimit presentation")
    @StackTrace(true)
    public static final class VulkanApiEvent extends Event {
        @Label("Operation")
        String operation;
        @Label("Vulkan object")
        long object;
        @Label("VkResult")
        int result;
    }

    @Name("net.vulkanmod.GpuCommandBuffer")
    @Label("GPU command-buffer execution")
    @Category({"VulkanMod", "GPU"})
    @Description("Raw Vulkan timestamp-query result for a complete submitted command buffer")
    @StackTrace(false)
    private static final class GpuCommandBufferEvent extends Event {
        @Label("Command buffer")
        long commandBuffer;
        @Label("Submission sequence")
        long submissionSequence;
        @Label("Queue")
        long queue;
        @Label("Fence")
        long fence;
        @Label("GPU start timestamp")
        long startTicks;
        @Label("GPU end timestamp")
        long endTicks;
        @Label("GPU elapsed ticks")
        long elapsedTicks;
        @Label("GPU elapsed time (ns)")
        long gpuNanos;
        @Label("Timestamp valid bits")
        int timestampValidBits;
    }
}
