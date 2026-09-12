package net.vulkanmod.vulkan.memory;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Vulkan;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight accounting for the memory classes that matter during a large
 * resource reload. This deliberately combines JVM/native counters with Linux
 * /proc, per-client DRM fdinfo and AMDGPU sysfs data when those interfaces are
 * available. All platform reads are best-effort so diagnostics never become a
 * startup requirement.
 */
public final class MemoryDiagnostics {
    private static final long MIB = 1024L * 1024L;
    private static final long NATIVE_IMAGE_REPORT_STEP = 512L * MIB;
    private static final long SYSTEM_MEMORY_CHECK_INTERVAL_NANOS = 250_000_000L;
    private static final long SYSTEM_AVAILABLE_SAFETY_LIMIT_MIB = Math.max(
            1024L, Long.getLong("vulkanmod.systemAvailableSafetyLimitMiB", 4096L));
    private static final long PROCESS_RSS_SAFETY_LIMIT_MIB = Math.max(
            4096L, Long.getLong("vulkanmod.processRssSafetyLimitMiB", 12288L));
    private static final long PROCESS_RSS_PRESSURE_AVAILABLE_MIB = Math.max(
            SYSTEM_AVAILABLE_SAFETY_LIMIT_MIB,
            Long.getLong("vulkanmod.processRssPressureAvailableMiB", 8192L));

    private static final AtomicLong NATIVE_IMAGE_LIVE = new AtomicLong();
    private static final AtomicLong NATIVE_IMAGE_PEAK = new AtomicLong();
    private static final AtomicLong NEXT_NATIVE_IMAGE_REPORT = new AtomicLong(NATIVE_IMAGE_REPORT_STEP);
    private static final AtomicLong VULKAN_IMAGE_LIVE = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_PEAK = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_VMA_LIVE = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_VMA_PEAK = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_COUNT = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_COUNT_PEAK = new AtomicLong();
    private static final AtomicLong NEXT_SYSTEM_MEMORY_CHECK_NANOS = new AtomicLong();
    private static final AtomicBoolean DIAGNOSTIC_FAILURE_LOGGED = new AtomicBoolean();

    private MemoryDiagnostics() {
    }

    public static void onNativeImageAllocated(long bytes) {
        if(bytes <= 0L)
            return;

        long live = NATIVE_IMAGE_LIVE.addAndGet(bytes);
        updatePeak(NATIVE_IMAGE_PEAK, live);
        maybeReportNativeImageGrowth(live);
    }

    public static void onNativeImageFreed(long bytes) {
        if(bytes <= 0L)
            return;

        NATIVE_IMAGE_LIVE.updateAndGet(value -> Math.max(0L, value - bytes));
    }

    public static void onVulkanImageAllocated(long estimatedBytes, long vmaBytes) {
        if(estimatedBytes > 0L) {
            long live = VULKAN_IMAGE_LIVE.addAndGet(estimatedBytes);
            updatePeak(VULKAN_IMAGE_PEAK, live);
        }
        if(vmaBytes > 0L) {
            long live = VULKAN_IMAGE_VMA_LIVE.addAndGet(vmaBytes);
            updatePeak(VULKAN_IMAGE_VMA_PEAK, live);
        }

        long count = VULKAN_IMAGE_COUNT.incrementAndGet();
        updatePeak(VULKAN_IMAGE_COUNT_PEAK, count);
    }

    public static void onVulkanImageFreed(long estimatedBytes, long vmaBytes) {
        if(estimatedBytes > 0L) {
            VULKAN_IMAGE_LIVE.updateAndGet(value -> Math.max(0L, value - estimatedBytes));
        }
        if(vmaBytes > 0L) {
            VULKAN_IMAGE_VMA_LIVE.updateAndGet(value -> Math.max(0L, value - vmaBytes));
        }
        VULKAN_IMAGE_COUNT.updateAndGet(value -> Math.max(0L, value - 1L));
    }

    public static long getNativeImageLiveBytes() {
        return NATIVE_IMAGE_LIVE.get();
    }

    public static long getVulkanImageLiveBytes() {
        return VULKAN_IMAGE_LIVE.get();
    }

    /**
     * Resource reload can accumulate memory outside the Java heap: decoded
     * NativeImages, mapped staging buffers, VMA allocations and driver/GTT
     * bookkeeping all contribute to process/system pressure. A JVM heap limit
     * therefore does not protect the desktop from global OOM. Sample Linux /proc
     * at a low cadence and fail the reload before the kernel has to invoke the OOM
     * killer.
     *
     * A high process RSS by itself is not sufficient evidence of system pressure:
     * large resource packs legitimately push this modpack beyond 12 GiB RSS while
     * Linux can still have many GiB available. Treat the RSS threshold as an early
     * warning that only becomes fatal when system availability is also declining.
     */
    public static void enforceSystemMemorySafety(String reason) {
        long now = System.nanoTime();
        long next = NEXT_SYSTEM_MEMORY_CHECK_NANOS.get();
        if(now < next)
            return;
        if(!NEXT_SYSTEM_MEMORY_CHECK_NANOS.compareAndSet(next, now + SYSTEM_MEMORY_CHECK_INTERVAL_NANOS))
            return;

        try {
            Map<String, Long> process = readKbValues(Path.of("/proc/self/status"));
            Map<String, Long> system = readKbValues(Path.of("/proc/meminfo"));

            long rssMiB = kbToMiB(process.get("VmRSS"));
            long availableMiB = kbToMiB(system.get("MemAvailable"));
            boolean rssTooHigh = rssMiB >= 0L && rssMiB > PROCESS_RSS_SAFETY_LIMIT_MIB;
            boolean systemTooLow = availableMiB >= 0L && availableMiB < SYSTEM_AVAILABLE_SAFETY_LIMIT_MIB;
            boolean rssUnderSystemPressure = rssTooHigh && availableMiB >= 0L
                    && availableMiB < PROCESS_RSS_PRESSURE_AVAILABLE_MIB;
            if(!rssUnderSystemPressure && !systemTooLow)
                return;

            logSnapshot("system memory safety trip: " + reason);
            throw new OutOfMemoryError(String.format(
                    "VulkanMod stopped resource loading before global OOM: process RSS=%d MiB " +
                            "(soft limit=%d MiB; enforced below %d MiB available), system MemAvailable=%d MiB " +
                            "(hard minimum=%d MiB). Override with -Dvulkanmod.processRssSafetyLimitMiB=<MiB>, " +
                            "-Dvulkanmod.processRssPressureAvailableMiB=<MiB>, or " +
                            "-Dvulkanmod.systemAvailableSafetyLimitMiB=<MiB> only for diagnosis.",
                    rssMiB, PROCESS_RSS_SAFETY_LIMIT_MIB, PROCESS_RSS_PRESSURE_AVAILABLE_MIB,
                    availableMiB, SYSTEM_AVAILABLE_SAFETY_LIMIT_MIB));
        } catch (OutOfMemoryError error) {
            throw error;
        } catch (Throwable throwable) {
            if(DIAGNOSTIC_FAILURE_LOGGED.compareAndSet(false, true)) {
                Initializer.LOGGER.warn("System memory safety diagnostics are unavailable: {}", throwable.toString());
            }
        }
    }

    public static void logSnapshot(String reason) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            long heapCommitted = runtime.totalMemory();
            long heapMax = runtime.maxMemory();

            Map<String, Long> process = readKbValues(Path.of("/proc/self/status"));
            Map<String, Long> system = readKbValues(Path.of("/proc/meminfo"));
            DrmClientMemory drmClient = readDrmClientMemory();
            AmdGpuMemory gpu = readAmdGpuMemory();

            MemoryManager memoryManager = MemoryManager.getInstance();
            int hostBufferMiB = memoryManager != null ? memoryManager.getNativeMemoryMB() : -1;
            int deviceBufferMiB = memoryManager != null ? memoryManager.getDeviceMemoryMB() : -1;

            Initializer.LOGGER.info(
                    "Memory snapshot [{}]: heap used/committed/max={}/{}/{} MiB; " +
                            "process rss/anon={}/{} MiB; NativeImage live/peak={}/{} MiB; " +
                            "VulkanImage est-live/peak={}/{} MiB VMA-live/peak={}/{} MiB count={}/{}; " +
                            "tracked buffers host/device={}/{} MiB; staging={}; " +
                            "process DRM clients={} resident VRAM/GTT={}/{} MiB; " +
                            "system available={} MiB GPUActive={} MiB GPUReclaim={} MiB SwapFree={} MiB; " +
                            "amdgpu global VRAM used/total={}/{} MiB GTT used/total={}/{} MiB",
                    reason,
                    toMiB(heapUsed), toMiB(heapCommitted), toMiB(heapMax),
                    kbToMiB(process.get("VmRSS")), kbToMiB(process.get("RssAnon")),
                    toMiB(NATIVE_IMAGE_LIVE.get()), toMiB(NATIVE_IMAGE_PEAK.get()),
                    toMiB(VULKAN_IMAGE_LIVE.get()), toMiB(VULKAN_IMAGE_PEAK.get()),
                    toMiB(VULKAN_IMAGE_VMA_LIVE.get()), toMiB(VULKAN_IMAGE_VMA_PEAK.get()),
                    VULKAN_IMAGE_COUNT.get(), VULKAN_IMAGE_COUNT_PEAK.get(),
                    hostBufferMiB, deviceBufferMiB,
                    Vulkan.describeStagingBuffers(),
                    drmClient.clients, bytesToMiB(drmClient.vramResident), bytesToMiB(drmClient.gttResident),
                    kbToMiB(system.get("MemAvailable")), kbToMiB(system.get("GPUActive")),
                    kbToMiB(system.get("GPUReclaim")), kbToMiB(system.get("SwapFree")),
                    bytesToMiB(gpu.vramUsed), bytesToMiB(gpu.vramTotal),
                    bytesToMiB(gpu.gttUsed), bytesToMiB(gpu.gttTotal));
        } catch (Throwable throwable) {
            if(DIAGNOSTIC_FAILURE_LOGGED.compareAndSet(false, true)) {
                Initializer.LOGGER.warn("Memory diagnostics are unavailable on this system: {}", throwable.toString());
            }
        }
    }

    private static void maybeReportNativeImageGrowth(long live) {
        while(true) {
            long threshold = NEXT_NATIVE_IMAGE_REPORT.get();
            if(live < threshold)
                return;

            if(NEXT_NATIVE_IMAGE_REPORT.compareAndSet(threshold, threshold + NATIVE_IMAGE_REPORT_STEP)) {
                logSnapshot("NativeImage live crossed " + (threshold / MIB) + " MiB");
                return;
            }
        }
    }

    private static void updatePeak(AtomicLong peak, long candidate) {
        peak.accumulateAndGet(candidate, Math::max);
    }

    private static long toMiB(long bytes) {
        return bytes < 0L ? -1L : bytes / MIB;
    }

    private static long kbToMiB(Long kb) {
        return kb == null ? -1L : kb / 1024L;
    }

    private static long bytesToMiB(long bytes) {
        return bytes < 0L ? -1L : bytes / MIB;
    }

    private static Map<String, Long> readKbValues(Path path) throws IOException {
        Map<String, Long> values = new HashMap<>();
        if(!Files.isReadable(path))
            return values;

        try(BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if(colon <= 0)
                    continue;

                String key = line.substring(0, colon);
                String tail = line.substring(colon + 1).trim();
                if(tail.isEmpty())
                    continue;

                int space = tail.indexOf(' ');
                String number = space >= 0 ? tail.substring(0, space) : tail;
                try {
                    values.put(key, Long.parseLong(number));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return values;
    }

    /**
     * Linux DRM fdinfo exposes memory owned by each DRM client in the current
     * process. AMDGPU's device-wide mem_info_gtt_used counter can include the
     * compositor, browser and other GPU clients, so it cannot by itself tell us
     * whether a reload-time GTT surge belongs to Minecraft. Deduplicate duplicated
     * file descriptors using drm-client-id (scoped by drm-pdev when present), then
     * sum this process' resident VRAM/GTT buffer objects.
     */
    private static DrmClientMemory readDrmClientMemory() {
        Path fdinfoDir = Path.of("/proc/self/fdinfo");
        if(!Files.isDirectory(fdinfoDir))
            return DrmClientMemory.UNAVAILABLE;

        long vramResident = 0L;
        long gttResident = 0L;
        int clients = 0;
        Set<String> seenClients = new HashSet<>();

        try(DirectoryStream<Path> entries = Files.newDirectoryStream(fdinfoDir)) {
            for(Path entry : entries) {
                String name = entry.getFileName().toString();
                if(!name.matches("\\d+"))
                    continue;

                Map<String, String> values = readStringValues(entry);
                String driver = values.get("drm-driver");
                if(driver == null)
                    continue;

                String clientId = values.get("drm-client-id");
                String pdev = values.get("drm-pdev");
                String identity = clientId != null
                        ? (pdev != null ? pdev : driver) + "#" + clientId
                        : entry.toString();
                if(!seenClients.add(identity))
                    continue;

                long clientVram = readDrmRegionBytes(values, "vram");
                long clientGtt = readDrmRegionBytes(values, "gtt");
                if(clientVram < 0L && clientGtt < 0L)
                    continue;

                ++clients;
                if(clientVram >= 0L)
                    vramResident += clientVram;
                if(clientGtt >= 0L)
                    gttResident += clientGtt;
            }
        } catch (IOException ignored) {
            return DrmClientMemory.UNAVAILABLE;
        }

        return clients > 0
                ? new DrmClientMemory(clients, vramResident, gttResident)
                : DrmClientMemory.UNAVAILABLE;
    }

    private static Map<String, String> readStringValues(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        if(!Files.isReadable(path))
            return values;

        try(BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if(colon <= 0)
                    continue;

                String key = line.substring(0, colon);
                String value = line.substring(colon + 1).trim();
                if(!value.isEmpty())
                    values.put(key, value);
            }
        }
        return values;
    }

    private static long readDrmRegionBytes(Map<String, String> values, String region) {
        String value = values.get("drm-resident-" + region);
        if(value == null) {
            // AMDGPU kernels also expose the older drm-memory-* alias.
            value = values.get("drm-memory-" + region);
        }
        return parseSizedBytes(value);
    }

    private static long parseSizedBytes(String value) {
        if(value == null || value.isBlank())
            return -1L;

        String[] parts = value.trim().split("\\s+");
        if(parts.length == 0)
            return -1L;

        try {
            long amount = Long.parseLong(parts[0]);
            long multiplier = 1L;
            if(parts.length > 1) {
                multiplier = switch(parts[1]) {
                    case "KiB" -> 1024L;
                    case "MiB" -> MIB;
                    case "GiB" -> 1024L * MIB;
                    default -> 1L;
                };
            }
            return Math.multiplyExact(amount, multiplier);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return -1L;
        }
    }

    private static AmdGpuMemory readAmdGpuMemory() {
        Path drm = Path.of("/sys/class/drm");
        if(!Files.isDirectory(drm))
            return AmdGpuMemory.UNAVAILABLE;

        long vramUsed = 0L;
        long vramTotal = 0L;
        long gttUsed = 0L;
        long gttTotal = 0L;
        boolean found = false;
        Set<Path> visitedDevices = new HashSet<>();

        try(DirectoryStream<Path> cards = Files.newDirectoryStream(drm, "card*")) {
            for(Path card : cards) {
                String cardName = card.getFileName().toString();
                if(!cardName.matches("card\\d+"))
                    continue;

                Path device = card.resolve("device");
                if(!Files.exists(device))
                    continue;

                Path canonical;
                try {
                    canonical = device.toRealPath();
                } catch (IOException ignored) {
                    canonical = device.toAbsolutePath().normalize();
                }
                if(!visitedDevices.add(canonical))
                    continue;

                long cardVramTotal = readLong(device.resolve("mem_info_vram_total"));
                long cardVramUsed = readLong(device.resolve("mem_info_vram_used"));
                long cardGttTotal = readLong(device.resolve("mem_info_gtt_total"));
                long cardGttUsed = readLong(device.resolve("mem_info_gtt_used"));

                if(cardVramTotal >= 0L || cardVramUsed >= 0L || cardGttTotal >= 0L || cardGttUsed >= 0L) {
                    found = true;
                    vramTotal += Math.max(0L, cardVramTotal);
                    vramUsed += Math.max(0L, cardVramUsed);
                    gttTotal += Math.max(0L, cardGttTotal);
                    gttUsed += Math.max(0L, cardGttUsed);
                }
            }
        } catch (IOException ignored) {
            return AmdGpuMemory.UNAVAILABLE;
        }

        return found ? new AmdGpuMemory(vramUsed, vramTotal, gttUsed, gttTotal) : AmdGpuMemory.UNAVAILABLE;
    }

    private static long readLong(Path path) {
        if(!Files.isReadable(path))
            return -1L;

        try {
            return Long.parseLong(Files.readString(path).trim());
        } catch (IOException | NumberFormatException ignored) {
            return -1L;
        }
    }

    private record DrmClientMemory(int clients, long vramResident, long gttResident) {
        private static final DrmClientMemory UNAVAILABLE = new DrmClientMemory(-1, -1L, -1L);
    }

    private record AmdGpuMemory(long vramUsed, long vramTotal, long gttUsed, long gttTotal) {
        private static final AmdGpuMemory UNAVAILABLE = new AmdGpuMemory(-1L, -1L, -1L, -1L);
    }
}
