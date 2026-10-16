package net.vulkanmod.render.profiling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** JFR has no frame/draw hooks; decoding/ranking happens after capture stops. */
public final class CpuGraphicsProfiler {
    private static Capture capture;
    private CpuGraphicsProfiler() {}

    public static void start(Path log, boolean automated, Consumer<String> output) {
        cancel();
        boolean requested = Boolean.parseBoolean(System.getProperty(
                "vulkanmod.performanceProfiler.cpuGraphicsSamples", Boolean.toString(automated)));
        if (!requested) {
            output.accept("cpu_graphics_recording requested=false status=disabled");
            return;
        }
        try {
            // Keep jdk.jfr references behind a lazy class boundary. A stripped
            // runtime must report unavailable rather than break the renderer.
            capture = new JfrCapture();
            capture.start(log, Thread.currentThread().getId(), output);
        } catch (IOException | RuntimeException | LinkageError failure) {
            cancel();
            output.accept("cpu_graphics_recording requested=true status=unavailable error="
                    + failure.getClass().getSimpleName());
        }
    }

    public static void finish(Consumer<String> output) {
        Capture owned = capture;
        capture = null;
        if (owned == null) return;
        try {
            owned.finish(output);
        } catch (IOException | RuntimeException | LinkageError failure) {
            output.accept("cpu_graphics_samples status=failed error=" + failure.getClass().getSimpleName());
        } finally {
            owned.close();
        }
    }

    public static void cancel() {
        Capture owned = capture;
        capture = null;
        if (owned != null) owned.close();
    }

    private interface Capture {
        void start(Path log, long renderThreadId, Consumer<String> output) throws IOException;
        void finish(Consumer<String> output) throws IOException;
        void close();
    }

    private static final class JfrCapture implements Capture {
        private static final long MAX_BYTES = 64L * 1024 * 1024;
        private static final int MAX_EVENTS = 1_000_000;
        private jdk.jfr.Recording recording;
        private Path path;
        private long renderThreadId;
        private Instant admittedStart;

        @Override
        public void start(Path log, long renderThreadId, Consumer<String> output) throws IOException {
            this.renderThreadId = renderThreadId;
            int period = Math.max(5, Math.min(100, Integer.getInteger(
                    "vulkanmod.performanceProfiler.cpuGraphicsPeriodMs", 10)));
            if (!jdk.jfr.FlightRecorder.isAvailable()) throw new IllegalStateException("JFR unavailable");
            recording = new jdk.jfr.Recording();
            recording.setName("VulkanMod CPU graphics");
            recording.setToDisk(true);
            recording.setMaxSize(MAX_BYTES);
            recording.setDumpOnExit(true);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(period)).withStackTrace();
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(period)).withStackTrace();
            path = Files.createTempFile(log.toAbsolutePath().getParent(),
                    log.getFileName().toString().replaceAll("[^a-zA-Z0-9_.-]", "_")
                            + ".cpu-graphics-", ".jfr");
            recording.setDestination(path);
            recording.start();
            admittedStart = Instant.now();
            output.accept("cpu_graphics_recording requested=true status=active requested_period_ms=" + period
                    + " max_retained_bytes=" + MAX_BYTES + " retention=rolling_jfr_chunks"
                    + " raw_file=" + path.getFileName()
                    + " scope=all_threads_classified_after_capture setup_excluded=true");
        }

        @Override
        public void finish(Consumer<String> output) throws IOException {
            Instant end = Instant.now();
            recording.stop(); // Stop BEFORE log formatting, dumping/decoding and save/quit.
            CpuGraphicsSamples samples = new CpuGraphicsSamples(renderThreadId);
            int events = 0;
            long outside = 0L;
            Instant first = null, last = null;
            boolean limit = false;
            try (jdk.jfr.consumer.RecordingFile file = new jdk.jfr.consumer.RecordingFile(path)) {
                while (file.hasMoreEvents()) {
                    if (events++ >= MAX_EVENTS) { limit = true; break; }
                    jdk.jfr.consumer.RecordedEvent event = file.readEvent();
                    String name = event.getEventType().getName();
                    if (!name.equals("jdk.ExecutionSample") && !name.equals("jdk.NativeMethodSample")) continue;
                    Instant time = event.getStartTime();
                    if (time.isBefore(admittedStart) || time.isAfter(end)) { outside++; continue; }
                    if (first == null || time.isBefore(first)) first = time;
                    if (last == null || time.isAfter(last)) last = time;
                    jdk.jfr.consumer.RecordedThread thread = event.getThread("sampledThread");
                    jdk.jfr.consumer.RecordedStackTrace trace = event.getStackTrace();
                    List<String> frames = new ArrayList<>();
                    if (trace != null) {
                        for (jdk.jfr.consumer.RecordedFrame frame : trace.getFrames()) {
                            // Keep full JFR frames for classification; the log stack is capped later.
                            var method = frame.getMethod();
                            frames.add(method.getType().getName() + "." + method.getName()
                                    + ":" + frame.getLineNumber());
                        }
                    }
                    samples.accept(name.equals("jdk.NativeMethodSample"),
                            thread == null ? -1L : thread.getJavaThreadId(),
                            thread == null ? "unknown" : thread.getJavaName(), frames,
                            trace != null && trace.isTruncated());
                }
            }
            output.accept("cpu_graphics_recording status=stopped raw_file=" + path.getFileName()
                    + " retained_bytes=" + Files.size(path) + " decoded_events=" + events
                    + " decode_event_limit=" + limit + " excluded_outside_capture=" + outside
                    + " admitted_start=" + admittedStart + " admitted_end=" + end
                    + " first_retained_sample=" + first + " last_retained_sample=" + last
                    + " retention_may_drop_oldest=true native_symbols=java_native_boundary_only");
            samples.emit(output);
        }

        @Override
        public void close() {
            if (recording != null) {
                try { recording.close(); }
                catch (RuntimeException ignored) { /* Optional diagnostic cleanup must not crash gameplay. */ }
                recording = null;
            }
        }
    }
}
