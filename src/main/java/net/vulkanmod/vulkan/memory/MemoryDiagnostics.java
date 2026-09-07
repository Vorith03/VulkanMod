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
 * /proc and AMDGPU sysfs data when those interfaces are available. All platform
 * reads are best-effort so diagnostics never become a startup requirement.
 */
public final class MemoryDiagnostics {
    private static final long MIB = 1024L * 1024L;

    private static final AtomicLong NATIVE_IMAGE_LIVE = new AtomicLong();
    private static final AtomicLong NATIVE_IMAGE_PEAK = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_LIVE = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_PEAK = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_COUNT = new AtomicLong();
    private static final AtomicLong VULKAN_IMAGE_COUNT_PEAK = new AtomicLong();
    private static final AtomicBoolean DIAGNOSTIC_FAILURE_LOGGED = new AtomicBoolean();

    private MemoryDiagnostics() {
    }

    public static void onNativeImageAllocated(long bytes) {
        if(bytes <= 0L)
            return;

        long live = NATIVE_IMAGE_LIVE.addAndGet(bytes);
        updatePeak(NATIVE_IMAGE_PEAK, live);
    }

    public static void onNativeImageFreed(long bytes) {
        if(bytes <= 0L)
            return;

        NATIVE_IMAGE_LIVE.updateAndGet(value -> Math.max(0L, value - bytes));
    }

    public static void onVulkanImageAllocated(long estimatedBytes) {
        if(estimatedBytes > 0L) {
            long live = VULKAN_IMAGE_LIVE.addAndGet(estimatedBytes);
            updatePeak(VULKAN_IMAGE_PEAK, live);
        }

        long count = VULKAN_IMAGE_COUNT.incrementAndGet();
        updatePeak(VULKAN_IMAGE_COUNT_PEAK, count);
    }

    public static void onVulkanImageFreed(long estimatedBytes) {
        if(estimatedBytes > 0L) {
            VULKAN_IMAGE_LIVE.updateAndGet(value -> Math.max(0L, value - estimatedBytes));
        }
        VULKAN_IMAGE_COUNT.updateAndGet(value -> Math.max(0L, value - 1L));
    }

    public static long getNativeImageLiveBytes() {
        return NATIVE_IMAGE_LIVE.get();
    }

    public static long getVulkanImageLiveBytes() {
        return VULKAN_IMAGE_LIVE.get();
    }

    public static void logSnapshot(String reason) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            long heapCommitted = runtime.totalMemory();
            long heapMax = runtime.maxMemory();

            Map<String, Long> process = readKbValues(Path.of("/proc/self/status"));
            Map<String, Long> system = readKbValues(Path.of("/proc/meminfo"));
            AmdGpuMemory gpu = readAmdGpuMemory();

            MemoryManager memoryManager = MemoryManager.getInstance();
            int hostBufferMiB = memoryManager != null ? memoryManager.getNativeMemoryMB() : -1;
            int deviceBufferMiB = memoryManager != null ? memoryManager.getDeviceMemoryMB() : -1;

            Initializer.LOGGER.info(
                    "Memory snapshot [{}]: heap used/committed/max={}/{}/{} MiB; " +
                            "process rss/anon={}/{} MiB; NativeImage live/peak={}/{} MiB; " +
                            "VulkanImage est-live/peak={}/{} MiB count={}/{}; " +
                            "tracked buffers host/device={}/{} MiB; staging={}; " +
                            "system available={} MiB GPUActive={} MiB GPUReclaim={} MiB SwapFree={} MiB; " +
                            "amdgpu VRAM used/total={}/{} MiB GTT used/total={}/{} MiB",
                    reason,
                    toMiB(heapUsed), toMiB(heapCommitted), toMiB(heapMax),
                    kbToMiB(process.get("VmRSS")), kbToMiB(process.get("RssAnon")),
                    toMiB(NATIVE_IMAGE_LIVE.get()), toMiB(NATIVE_IMAGE_PEAK.get()),
                    toMiB(VULKAN_IMAGE_LIVE.get()), toMiB(VULKAN_IMAGE_PEAK.get()),
                    VULKAN_IMAGE_COUNT.get(), VULKAN_IMAGE_COUNT_PEAK.get(),
                    hostBufferMiB, deviceBufferMiB,
                    Vulkan.describeStagingBuffers(),
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

    private record AmdGpuMemory(long vramUsed, long vramTotal, long gttUsed, long gttTotal) {
        private static final AmdGpuMemory UNAVAILABLE = new AmdGpuMemory(-1L, -1L, -1L, -1L);
    }
}
