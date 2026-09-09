package net.vulkanmod.vulkan.memory;

import net.vulkanmod.Initializer;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Best-effort native allocator cache reclamation used at the unusually expensive
 * in-world full resource-reload boundary.
 *
 * NativeImage.close() can correctly free hundreds of MiB while process RSS barely
 * changes because jemalloc/glibc retain the freed pages for later reuse. That is
 * normally desirable, but during F3+T the replacement resource generation is about
 * to allocate another GiB-scale set of decoded images immediately. Purging only at
 * this explicit reload boundary gives Linux the option to reclaim those old pages
 * before the replacement decode begins without adding allocator churn to gameplay.
 */
public final class NativeAllocatorPurger {
    private static final long KIB_PER_MIB = 1024L;

    private NativeAllocatorPurger() {
    }

    public static void purgeForResourceReload() {
        if(!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return;
        }

        long rssBefore = readProcMiB(Path.of("/proc/self/status"), "VmRSS");
        long availableBefore = readProcMiB(Path.of("/proc/meminfo"), "MemAvailable");

        String allocatorName;
        try {
            Object allocator = MemoryUtil.getAllocator();
            allocatorName = allocator == null ? "null" : allocator.getClass().getName();
        } catch(Throwable throwable) {
            allocatorName = "unavailable:" + throwable.getClass().getSimpleName();
        }

        String jemallocResult = purgeJemallocIfActive(allocatorName);
        String libcResult = trimLibcHeap();

        long rssAfter = readProcMiB(Path.of("/proc/self/status"), "VmRSS");
        long availableAfter = readProcMiB(Path.of("/proc/meminfo"), "MemAvailable");

        Initializer.LOGGER.info(
                "Native allocator purge: allocator={}, jemalloc={}, libc={}, " +
                        "process RSS {} -> {} MiB, system available {} -> {} MiB",
                allocatorName, jemallocResult, libcResult,
                rssBefore, rssAfter, availableBefore, availableAfter);
    }

    private static String purgeJemallocIfActive(String allocatorName) {
        if(allocatorName == null || !allocatorName.toLowerCase(Locale.ROOT).contains("jemalloc")) {
            return "not-active";
        }

        try {
            Class<?> jemalloc = Class.forName("org.lwjgl.system.jemalloc.JEmalloc");
            Method mallctl = null;
            for(Method method : jemalloc.getMethods()) {
                if(method.getName().equals("je_mallctl")
                        && method.getParameterCount() == 4
                        && method.getParameterTypes()[0] == CharSequence.class) {
                    mallctl = method;
                    break;
                }
            }

            if(mallctl == null) {
                return "mallctl-unavailable";
            }

            int tcacheFlush = ((Number)mallctl.invoke(
                    null, "thread.tcache.flush", null, null, null)).intValue();
            // LWJGL's JEmalloc.MALLCTL_ARENAS_ALL is 0x1000 / 4096. Avoid a
            // compile-time dependency on the optional jemalloc module so Forge's
            // VulkanMod module does not require a new JPMS read edge merely for a
            // Linux reload-time optimization.
            int arenaPurge = ((Number)mallctl.invoke(
                    null, "arena.4096.purge", null, null, null)).intValue();

            return "tcache=" + tcacheFlush + ",arenas=" + arenaPurge;
        } catch(Throwable throwable) {
            return "unavailable:" + throwable.getClass().getSimpleName();
        }
    }

    private static String trimLibcHeap() {
        try {
            Class<?> nativeLibraryClass = Class.forName("com.sun.jna.NativeLibrary");
            Class<?> functionClass = Class.forName("com.sun.jna.Function");

            Object libc = nativeLibraryClass.getMethod("getInstance", String.class)
                    .invoke(null, "c");
            Object mallocTrim = nativeLibraryClass.getMethod("getFunction", String.class)
                    .invoke(libc, "malloc_trim");
            int result = ((Number)functionClass.getMethod("invokeInt", Object[].class)
                    .invoke(mallocTrim, (Object)new Object[] {0})).intValue();
            return result == 0 ? "no-pages" : "trimmed";
        } catch(Throwable throwable) {
            return "unavailable:" + throwable.getClass().getSimpleName();
        }
    }

    private static long readProcMiB(Path path, String key) {
        if(!Files.isReadable(path)) {
            return -1L;
        }

        try(BufferedReader reader = Files.newBufferedReader(path)) {
            String prefix = key + ":";
            String line;
            while((line = reader.readLine()) != null) {
                if(!line.startsWith(prefix)) {
                    continue;
                }

                String tail = line.substring(prefix.length()).trim();
                int space = tail.indexOf(' ');
                String number = space >= 0 ? tail.substring(0, space) : tail;
                return Long.parseLong(number) / KIB_PER_MIB;
            }
        } catch(Throwable ignored) {
        }

        return -1L;
    }
}
