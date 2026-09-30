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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.UUID;

/**
 * Raw, query-later performance capture for VulkanMod profiling runs.
 *
 * <p>This deliberately does not know about terrain, entities, textures, ticks, or
 * any other subsystem whose importance would have to be predicted in advance.
 * JFR continuously samples the JVM and records runtime events across all threads.
 * VulkanMod adds only universal correlation boundaries: Minecraft frames and the
 * Vulkan queue/fence operations through which GPU work must pass.</p>
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

    private static Recording recording;
    private static Path outputPath;
    private static FrameEvent frameEvent;
    private static long frameSequence;
    private static long vulkanSubmissionSequence;
    private static boolean shutdownHookInstalled;
    private static boolean failed;

    private FlightRecorderCapture() {
    }

    /** Start the raw recording on first use. Repeated calls are intentionally cheap. */
    public static synchronized boolean startIfNeeded() {
        if (!ENABLED || failed) return false;
        if (recording != null) return true;

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
            next.enable("jdk.ThreadPark").withThreshold(Duration.ZERO).withStackTrace();
            next.enable("jdk.ThreadSleep").withThreshold(Duration.ZERO).withStackTrace();
            next.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ZERO).withStackTrace();
            next.enable("jdk.JavaMonitorWait").withThreshold(Duration.ZERO).withStackTrace();
            next.enable(FrameEvent.class).withoutStackTrace();
            next.enable(VulkanSubmissionEvent.class).withStackTrace().withoutThreshold();
            next.enable(VulkanFenceWaitEvent.class).withStackTrace().withoutThreshold();
            next.enable(VulkanQueueIdleEvent.class).withStackTrace().withoutThreshold();

            outputPath = resolveOutputPath();
            Path parent = outputPath.getParent();
            if (parent != null) Files.createDirectories(parent);

            next.start();
            recording = next;
            installShutdownHook();
            Initializer.LOGGER.info(
                    "VulkanMod raw performance flight recorder enabled; output: {}; Java sample={} ms; native sample={} ms",
                    outputPath.toAbsolutePath(), JAVA_SAMPLE_MS, NATIVE_SAMPLE_MS);
            return true;
        } catch (IOException | ParseException | RuntimeException failure) {
            failed = true;
            Initializer.LOGGER.error("VulkanMod could not start the raw JFR performance capture", failure);
            return false;
        }
    }

    /** Begin one generic Minecraft runTick envelope. */
    public static synchronized void beginFrame(boolean tickRequested) {
        if (!startIfNeeded() || frameEvent != null) return;

        FrameEvent event = new FrameEvent();
        event.sequence = ++frameSequence;
        event.tickRequested = tickRequested;
        event.begin();
        frameEvent = event;
    }

    /** End the current generic frame envelope. */
    public static synchronized void endFrame() {
        FrameEvent event = frameEvent;
        frameEvent = null;
        if (event != null) event.commit();
    }

    /** Begin a universal Vulkan command-buffer submission event. */
    public static synchronized VulkanSubmissionEvent beginVulkanSubmission(
            long commandBuffer, long queue, long fence, boolean signalsSemaphore) {
        if (recording == null) return null;
        VulkanSubmissionEvent event = new VulkanSubmissionEvent();
        event.sequence = ++vulkanSubmissionSequence;
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
        event.commit();
    }

    /** Begin a fence wait. A zero fence denotes a batched wait. */
    public static synchronized VulkanFenceWaitEvent beginVulkanFenceWait(long fence, int fenceCount) {
        if (recording == null) return null;
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

    public static synchronized VulkanQueueIdleEvent beginVulkanQueueIdle(long queue) {
        if (recording == null) return null;
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

    /**
     * Stop and persist the raw recording. The benchmark may call this before JVM
     * shutdown; the shutdown hook is only a safety net for interrupted/manual runs.
     */
    public static synchronized boolean stop(String reason) {
        endFrame();
        Recording current = recording;
        recording = null;
        if (current == null) return !ENABLED || failed;

        try {
            current.stop();
            current.dump(outputPath);
            Initializer.LOGGER.info("VulkanMod raw performance flight recorder saved: {} ({})",
                    outputPath.toAbsolutePath(), reason);
            return true;
        } catch (IOException | RuntimeException failure) {
            failed = true;
            Initializer.LOGGER.error("VulkanMod could not save the raw JFR performance capture", failure);
            return false;
        } finally {
            current.close();
        }
    }

    public static synchronized Path outputPath() {
        return outputPath;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    private static Path resolveOutputPath() {
        String configured = System.getProperty(OUTPUT_PROPERTY, "").trim();
        if (!configured.isEmpty()) return Path.of(configured).toAbsolutePath().normalize();
        return Path.of("logs", "vulkanmod-performance-flight-" + UUID.randomUUID() + ".jfr")
                .toAbsolutePath().normalize();
    }

    private static void installShutdownHook() {
        if (shutdownHookInstalled) return;
        shutdownHookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> stop("jvm_shutdown"), "VulkanMod performance flight recorder shutdown"));
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
}
