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
import java.util.UUID;

/**
 * Raw, query-later performance capture for VulkanMod profiling runs.
 *
 * <p>This deliberately does not know about terrain, entities, textures, ticks, or
 * any other subsystem whose importance would have to be predicted in advance.
 * JFR's profile configuration continuously samples the JVM and records runtime
 * events across all threads. VulkanMod adds only a generic runTick frame envelope
 * so arbitrary samples can later be correlated to individual frames.</p>
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

    private static Recording recording;
    private static Path outputPath;
    private static FrameEvent frameEvent;
    private static long frameSequence;
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
            next.enable(FrameEvent.class).withoutStackTrace();

            outputPath = resolveOutputPath();
            Path parent = outputPath.getParent();
            if (parent != null) Files.createDirectories(parent);

            next.start();
            recording = next;
            installShutdownHook();
            Initializer.LOGGER.info("VulkanMod raw performance flight recorder enabled; output: {}",
                    outputPath.toAbsolutePath());
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
}
