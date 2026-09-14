package net.vulkanmod.vulkan.memory;

import net.vulkanmod.Initializer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Opt-in lifecycle snapshots for diagnosing ownership that survives world/reload
 * transitions. This class is deliberately observational: it never drains queues,
 * waits for the device, frees resources, or changes renderer scheduling.
 */
public final class LifecycleMemoryTelemetry {
    public static final String PROPERTY = "vulkanmod.debugLifecycleMemory";
    private static final long MIB = 1024L * 1024L;
    // Exercise the same snapshot path in the existing CI startup fixture without
    // adding another workflow/script surface. Ordinary gameplay remains opt-in.
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY)
            || Boolean.getBoolean("vulkanmod.smokeTest");
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private LifecycleMemoryTelemetry() {
    }

    public static void snapshot(String event) {
        if(!ENABLED)
            return;

        long sequence = SEQUENCE.incrementAndGet();
        MemoryManager memoryManager = MemoryManager.getInstance();
        int hostMiB = memoryManager != null ? memoryManager.getNativeMemoryMB() : -1;
        int deviceMiB = memoryManager != null ? memoryManager.getDeviceMemoryMB() : -1;
        MemoryManager.DeferredResourceStats deferred = memoryManager != null
                ? memoryManager.getDeferredResourceStats()
                : new MemoryManager.DeferredResourceStats(-1, -1, -1);

        Initializer.LOGGER.info(
                "VULKANMOD_LIFECYCLE_RESOURCES seq={} event={} " +
                        "tracked buffers/images={}/{} host/device={}/{} MiB " +
                        "deferred buffers/images/frameOps={}/{}/{} " +
                        "NativeImage-live={} MiB VulkanImage-live={} MiB",
                sequence, event,
                MemoryManager.getTrackedBufferCount(), MemoryManager.getTrackedImageCount(),
                hostMiB, deviceMiB,
                deferred.buffers(), deferred.images(), deferred.frameOps(),
                MemoryDiagnostics.getNativeImageLiveBytes() / MIB,
                MemoryDiagnostics.getVulkanImageLiveBytes() / MIB);

        MemoryDiagnostics.logSnapshot("lifecycle #" + sequence + " " + event);
    }
}
