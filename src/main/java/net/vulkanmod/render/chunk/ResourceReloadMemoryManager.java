package net.vulkanmod.render.chunk;

import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryDiagnostics;
import net.vulkanmod.vulkan.memory.MemoryManager;

/**
 * Retires world-terrain GPU allocations before a full client resource reload.
 *
 * Minecraft keeps the old resource generation usable until replacement resources
 * have been prepared. That is normally desirable, but the Vulkan terrain renderer
 * can add more than a GiB of device-local buffers to the transient F3+T peak. The
 * keyboard reload is requested while GLFW is polling events, after VulkanMod has
 * submitted the current frame. At that boundary no primary frame is still being
 * recorded, so we can quiesce chunk production and the GPU, release terrain, and
 * drain deferred frees before replacement sprites/models begin decoding.
 */
public final class ResourceReloadMemoryManager {
    private static SectionGrid retiredGrid;

    private ResourceReloadMemoryManager() {
    }

    /**
     * @return true when terrain was retired early for this reload.
     */
    public static synchronized boolean prepareForReload() {
        Minecraft minecraft = Minecraft.getInstance();
        WorldRenderer worldRenderer = WorldRenderer.getInstance();
        Renderer renderer = Renderer.getInstance();
        MemoryManager memoryManager = MemoryManager.getInstance();

        if(minecraft == null || minecraft.level == null || worldRenderer == null
                || renderer == null || memoryManager == null) {
            return false;
        }

        SectionGrid sectionGrid = worldRenderer.getSectionGrid();
        if(sectionGrid == null || retiredGrid != null) {
            return false;
        }

        // Never destroy resources referenced by an unsubmitted primary command
        // buffer. F3+T reaches this method from Window.updateDisplay after endFrame,
        // but other callers of reloadResourcePacks are allowed to occur elsewhere.
        if(renderer.isRecordingFrame()) {
            Initializer.LOGGER.warn(
                    "Resource reload began while a Vulkan frame was still recording; " +
                            "keeping terrain buffers on normal deferred retirement");
            return false;
        }

        retiredGrid = sectionGrid;
        MemoryDiagnostics.logSnapshot("resource reload before terrain retirement");

        TaskDispatcher taskDispatcher = worldRenderer.getTaskDispatcher();
        taskDispatcher.stopThreads();

        if(AreaUploadManager.INSTANCE != null) {
            AreaUploadManager.INSTANCE.waitAllUploads();
        }

        // stopThreads() prevents new chunk work, waitAllUploads() finishes transfer
        // command buffers, and device idle covers every submitted graphics/helper
        // reference before any allocation below is destroyed.
        Vulkan.waitIdle();
        sectionGrid.releaseAllBuffers();
        Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();

        int deviceBeforeMiB = memoryManager.getDeviceMemoryMB();
        int hostBeforeMiB = memoryManager.getNativeMemoryMB();
        memoryManager.freeAllBuffers();
        int deviceAfterMiB = memoryManager.getDeviceMemoryMB();
        int hostAfterMiB = memoryManager.getNativeMemoryMB();

        Initializer.LOGGER.info(
                "Retired terrain/deferred Vulkan buffers before resource reload: " +
                        "device {} -> {} MiB, host {} -> {} MiB",
                deviceBeforeMiB, deviceAfterMiB, hostBeforeMiB, hostAfterMiB);
        MemoryDiagnostics.logSnapshot("resource reload after terrain retirement");
        return true;
    }

    /**
     * A successful resource reload normally invokes LevelRenderer.allChanged(),
     * which replaces the retired SectionGrid. If reload recovery completed without
     * reaching that listener, rebuild once before returning to gameplay so early
     * retirement can never leave the world renderer pointing at freed buffers.
     */
    public static synchronized void ensureTerrainReadyAfterReload() {
        SectionGrid expectedRetiredGrid = retiredGrid;
        retiredGrid = null;
        if(expectedRetiredGrid == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        WorldRenderer worldRenderer = WorldRenderer.getInstance();
        if(minecraft == null || minecraft.level == null || worldRenderer == null) {
            return;
        }

        if(worldRenderer.getSectionGrid() == expectedRetiredGrid) {
            Initializer.LOGGER.warn(
                    "Resource reload completed without replacing retired terrain; forcing LevelRenderer rebuild");
            minecraft.levelRenderer.allChanged();
        }
    }
}
