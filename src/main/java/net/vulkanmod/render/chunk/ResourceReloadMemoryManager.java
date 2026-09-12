package net.vulkanmod.render.chunk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.VTextureManagerI;
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryDiagnostics;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.NativeAllocatorPurger;

/**
 * Retires memory that is safe to discard before a full in-world client resource reload.
 *
 * Minecraft keeps the old resource generation usable until replacement resources
 * have been prepared. That is normally desirable, but the Vulkan terrain renderer
 * can add more than a GiB of device-local buffers and high-resolution atlases can
 * retain another GiB-plus of decoded CPU sprite pixels. The keyboard reload is
 * requested while GLFW is polling events, after VulkanMod has submitted the current
 * frame. At that boundary no primary frame is still being recorded, so we can
 * quiesce chunk production and the GPU, release terrain, discard static CPU pixels
 * from the old atlas generation, and return allocator-cached pages made free by that
 * retirement before replacement sprites/models begin decoding. A second allocator
 * purge after successful apply reclaims temporary replacement-generation allocations
 * before terrain reconstruction.
 */
public final class ResourceReloadMemoryManager {
    private static final long MIB = 1024L * 1024L;
    private static final long NO_RESOURCE_RELOAD = 0L;
    private static final ReloadGenerationGate RELOAD_GENERATIONS = new ReloadGenerationGate();
    private static SectionGrid retiredGrid;

    private ResourceReloadMemoryManager() {
    }

    /**
     * Starts a generation only when the early retirement work actually took place.
     * A reload that occurs before a client world exists, during a recording frame, or
     * while another retired generation is still active receives no completion token.
     */
    public static synchronized long beginResourceReload() {
        return RELOAD_GENERATIONS.begin(prepareForReload());
    }

    /**
     * Completes one real resource-reload generation on the client thread.
     *
     * A normal completion means all reload listeners, including atlas application,
     * have successfully run. The post-apply allocator purge remains useful after the
     * pre-decode purge because reload preparation itself creates temporary native
     * allocations that may become allocator-cached when the new generation applies.
     */
    public static void completeResourceReload(long generation, Throwable failure) {
        boolean accepted = RELOAD_GENERATIONS.complete(
                generation,
                failure == null,
                () -> {
                    Initializer.LOGGER.info(
                            "Resource reload generation {} applied successfully; purging native allocator pages before terrain reconstruction",
                            generation);
                    MemoryDiagnostics.logSnapshot(
                            "resource reload generation " + generation + " after apply before allocator purge");
                    NativeAllocatorPurger.purgeForResourceReload();
                    MemoryDiagnostics.logSnapshot(
                            "resource reload generation " + generation + " after allocator purge");
                },
                () -> {
                    if(failure != null) {
                        Initializer.LOGGER.warn(
                                "Resource reload generation {} failed; skipping post-apply native allocator purge: {}",
                                generation, failure);
                    }
                    ensureTerrainReadyAfterReload();
                });

        if(!accepted && generation != NO_RESOURCE_RELOAD) {
            Initializer.LOGGER.warn(
                    "Ignoring completion for resource reload generation {}; it is no longer active",
                    generation);
        }
    }

    /**
     * @return true when terrain/resource memory was retired early for this reload.
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
                            "keeping terrain buffers and old atlas CPU pixels on normal retirement");
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

        retireOldAtlasCpuPixels(minecraft);
        return true;
    }

    private static void retireOldAtlasCpuPixels(Minecraft minecraft) {
        TextureManager textureManager = minecraft.getTextureManager();
        if(!(textureManager instanceof VTextureManagerI vulkanTextureManager)) {
            Initializer.LOGGER.warn(
                    "Could not retire old atlas CPU pixels before resource reload; TextureManager bridge is unavailable");
            return;
        }

        long nativeBefore = MemoryDiagnostics.getNativeImageLiveBytes();
        int retiredStaticSprites = vulkanTextureManager.vulkanmod$retireStaticAtlasCpuDataForReload();
        long nativeAfter = MemoryDiagnostics.getNativeImageLiveBytes();
        long freed = Math.max(0L, nativeBefore - nativeAfter);

        Initializer.LOGGER.info(
                "Retired old atlas static CPU sprite pixels before resource reload: " +
                        "sprites={}, NativeImage {} -> {} MiB (freed {} MiB); animated sprites preserved",
                retiredStaticSprites, nativeBefore / MIB, nativeAfter / MIB, freed / MIB);
        MemoryDiagnostics.logSnapshot("resource reload after atlas CPU retirement");

        if(freed > 0L) {
            // The sprite NativeImages above are already closed. Purging an allocator
            // cache cannot invalidate the still-live animated sprites or reload
            // recovery state; it only asks the native allocator/OS to reclaim pages
            // that are already free. Doing this before replacement decode is the
            // point at which the reclaimed headroom can reduce the F3+T peak.
            Initializer.LOGGER.info(
                    "Purging native allocator pages released by old atlas retirement before replacement resource decode");
            NativeAllocatorPurger.purgeForResourceReload();
            MemoryDiagnostics.logSnapshot("resource reload after pre-decode native allocator purge");
        }
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

    /**
     * Small dependency-free state machine shared by production completion handling
     * and its regression test. It claims one real generation and guarantees that a
     * successful-generation action runs before common recovery.
     */
    static final class ReloadGenerationGate {
        private static final long NONE = 0L;
        private long nextGeneration;
        private long activeGeneration;

        long begin(boolean realReload) {
            synchronized(this) {
                if(!realReload || this.activeGeneration != NONE) {
                    return NONE;
                }

                long generation = ++this.nextGeneration;
                if(generation == NONE) {
                    generation = ++this.nextGeneration;
                }
                this.activeGeneration = generation;
                return generation;
            }
        }

        boolean complete(
                long generation,
                boolean successful,
                Runnable successAction,
                Runnable completionAction) {
            synchronized(this) {
                if(generation == NONE || generation != this.activeGeneration) {
                    return false;
                }
                this.activeGeneration = NONE;
            }

            try {
                if(successful) {
                    successAction.run();
                }
            } finally {
                completionAction.run();
            }
            return true;
        }
    }
}
