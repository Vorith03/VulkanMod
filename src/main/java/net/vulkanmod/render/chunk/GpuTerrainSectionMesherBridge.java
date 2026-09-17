package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelGpuStore;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelRegistry;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelTable;
import net.vulkanmod.render.chunk.voxel.GpuTerrainSectionMesher;
import net.vulkanmod.render.chunk.voxel.RegionVoxelGpuStore;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.memory.StorageBuffer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production bridge from generation-owned section inputs to persistent GPU terrain
 * output. The ordinary path still builds CPU terrain. A separate stricter property
 * may let an already-meshed section skip repeated CPU block-model tessellation while
 * retaining its previous CPU draw as the fail-closed fallback until current GPU work
 * publishes successfully.
 *
 * <p>The bridge accepts only sections whose visible block-model geometry consists
 * entirely of qualified GPU full cubes (plus invisible states). For ordinary shadow
 * dispatch it cross-checks the independently planned GPU face count against the new
 * CPU mesh. For the rebuild-only CPU-bypass experiment that new CPU mesh intentionally
 * does not exist, so the immutable worker plan becomes authoritative while the older
 * ready CPU mesh remains resident for recovery. Mixed or unsupported sections remain
 * CPU-only. Dispatch runs as a later render-thread operation after input upload and
 * outside an active render pass. Completion is deferred until the frame fence covers
 * the same-queue helper submission; no production terrain dispatch waits its own
 * Vulkan fence on the render thread.</p>
 */
final class GpuTerrainSectionMesherBridge {
    static final String PROPERTY = "vulkanmod.experimentalGpuTerrainMesher";
    static final String CPU_BYPASS_PROPERTY = "vulkanmod.experimentalGpuTerrainCpuBypass";
    static final String DRAW_HANDOFF_PROPERTY = "vulkanmod.experimentalGpuTerrainDrawHandoff";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final boolean CPU_BYPASS_ENABLED = ENABLED
            && Boolean.getBoolean(CPU_BYPASS_PROPERTY)
            && Boolean.getBoolean(DRAW_HANDOFF_PROPERTY);
    private static final AtomicBoolean ACTIVE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean RECOVERY_LOGGED = new AtomicBoolean();

    private static boolean mesherAttempted;
    private static GpuTerrainSectionMesher mesher;
    private static GpuTerrainModelGpuStore modelStore;
    private static GpuTerrainModelTable modelTable;
    private static long failedModelGeneration = Long.MIN_VALUE;

    private GpuTerrainSectionMesherBridge() {}

    static boolean enabled() {
        return ENABLED;
    }

    static boolean cpuBypassEnabled() {
        return CPU_BYPASS_ENABLED;
    }

    static TerrainRenderType outputLayer() {
        return Initializer.CONFIG.uniqueOpaqueLayer
                ? TerrainRenderType.CUTOUT_MIPPED : TerrainRenderType.CUTOUT;
    }

    static boolean supportsCpuBypass(int faceCount) {
        return CPU_BYPASS_ENABLED && faceCount > 0
                && faceCount <= GpuTerrainDrawHandoff.MAX_AUTO_INDEX_FACES;
    }

    static synchronized void dispatch(ChunkArea area, RenderSection section, long generation) {
        if(!ENABLED || area == null || section == null)
            return;

        RenderSystem.assertOnRenderThread();
        if(section.getChunkArea() != area || section.getVoxelGeneration() != generation)
            return;

        boolean cpuBypassed = section.stagedGpuTerrainCpuBypassed(generation);
        int x = section.xOffset();
        int y = section.yOffset();
        int z = section.zOffset();
        SectionVoxelSnapshot snapshot = area.getVoxels(x, y, z);
        Qualification qualification = qualify(snapshot);
        if(snapshot == null || snapshot.x() != x || snapshot.y() != y || snapshot.z() != z
                || qualification == null
                || !section.matchesStagedGpuTerrainPreflight(generation,
                        qualification.modelGeneration(), qualification.faceCount())) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        TerrainRenderType layer = outputLayer();
        DrawBuffers.DrawParameters cpu = section.getDrawParameters(layer);
        if(cpu.indexCount <= 0 || cpu.indexCount % 6 != 0
                || !cpu.vertexBufferSegment.isReady()) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        int faceCapacity = qualification.faceCount();
        if(faceCapacity <= 0 || faceCapacity > GpuTerrainDrawHandoff.MAX_AUTO_INDEX_FACES) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        // Shadow/validation mode still has a newly-built CPU mesh, so retain the
        // independent face-count oracle. A CPU-bypassed rebuild intentionally kept
        // the previous generation's CPU fallback instead; comparing its face count
        // with current inputs would reject legitimate edits and defeat the bypass.
        if(!cpuBypassed && cpu.indexCount / 6 != faceCapacity) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        RegionVoxelGpuStore.Residency voxel = area.getGpuVoxelResidency(x, y, z);
        RegionVoxelGpuStore.Residency lighting = area.getGpuSparseLightingResidency(x, y, z);
        if(voxel == null || lighting == null || !voxel.valid() || !lighting.valid()
                || voxel.generation() != generation || lighting.generation() != generation) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        StorageBuffer voxelPage = area.getGpuVoxelPage(voxel.pageIndex());
        StorageBuffer lightingPage = area.getGpuVoxelPage(lighting.pageIndex());
        if(voxelPage == null || lightingPage == null || !ensureGpuResources()) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        GpuTerrainModelGpuStore.Residency model = modelStore.getResidency();
        long modelGeneration = qualification.modelGeneration();
        if(!model.valid() || modelTable == null
                || model.generation() != modelGeneration
                || model.generation() != modelTable.generation()
                || model.generation() != GpuTerrainModelRegistry.generation()) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        GpuTerrainOutputStore.Reservation reservation = area.reserveGpuTerrainOutput(
                x, y, z, layer, generation, faceCapacity);
        if(reservation == null) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        try {
            Boolean submitted = reservation.withTarget(target -> mesher.dispatchAsync(
                    voxelPage, voxel, lightingPage, lighting, model,
                    modelTable.templateCount(), target, faceCapacity,
                    (result, failure) -> completeDispatch(area, section, generation,
                            modelGeneration, faceCapacity, cpuBypassed, layer,
                            reservation, result, failure)));
            if(!Boolean.TRUE.equals(submitted)) {
                area.publishGpuTerrainOutput(reservation, 0, true);
                recoverCpuFallback(area, section, generation);
            }
        } catch(RuntimeException error) {
            area.publishGpuTerrainOutput(reservation, 0, true);
            recoverCpuFallback(area, section, generation);
            reportDispatchFailure("GPU terrain section submission failed; preserving CPU terrain fallback",
                    error);
        }
    }

    private static synchronized void completeDispatch(
            ChunkArea area, RenderSection section, long generation,
            long modelGeneration, int faceCapacity, boolean cpuBypassed,
            TerrainRenderType layer, GpuTerrainOutputStore.Reservation reservation,
            GpuTerrainSectionMesher.DispatchResult result, RuntimeException failure) {
        RenderSystem.assertOnRenderThread();

        if(section.getChunkArea() != area || section.getVoxelGeneration() != generation) {
            area.publishGpuTerrainOutput(reservation, 0, true);
            return;
        }

        if(failure != null) {
            area.publishGpuTerrainOutput(reservation, 0, true);
            recoverCpuFallback(area, section, generation);
            reportDispatchFailure("GPU terrain section completion readback failed; preserving CPU terrain fallback",
                    failure);
            return;
        }

        if(GpuTerrainModelRegistry.generation() != modelGeneration
                || !section.matchesStagedGpuTerrainPreflight(
                        generation, modelGeneration, faceCapacity)) {
            area.publishGpuTerrainOutput(reservation, 0, true);
            recoverCpuFallback(area, section, generation);
            return;
        }

        boolean exact = result != null
                && !result.overflow()
                && result.errorFlags() == 0
                && result.requestedFaces() == faceCapacity
                && result.writtenFaces() == faceCapacity;
        if(!exact) {
            area.publishGpuTerrainOutput(reservation, 0, true);
            recoverCpuFallback(area, section, generation);
            return;
        }

        boolean published = area.publishGpuTerrainOutput(
                reservation, result.writtenFaces(), false);
        if(!published) {
            recoverCpuFallback(area, section, generation);
            return;
        }

        if(ACTIVE_LOGGED.compareAndSet(false, true)) {
            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_MESHER_ACTIVE: section=({}, {}, {}) layer={} faces={} cpuBypassed={}; completion published after frame-fence retirement without a helper fence wait",
                    section.xOffset(), section.yOffset(), section.zOffset(), layer.ordinal(),
                    result.writtenFaces(), cpuBypassed);
        }
    }

    private static void reportDispatchFailure(String message, RuntimeException error) {
        if(FAILURE_LOGGED.compareAndSet(false, true))
            Initializer.LOGGER.warn(message, error);
    }

    /**
     * Recover only a still-current build that deliberately skipped new CPU model
     * tessellation. The old CPU draw remains resident while setDirty() schedules a
     * normal rebuild; generation turnover makes delayed recovery a no-op.
     */
    static void recoverCpuFallback(ChunkArea area, RenderSection section, long generation) {
        if(!CPU_BYPASS_ENABLED || area == null || section == null)
            return;
        RenderSystem.assertOnRenderThread();
        if(section.getChunkArea() != area || section.getVoxelGeneration() != generation)
            return;
        if(section.requestGpuTerrainCpuRecovery(generation)
                && RECOVERY_LOGGED.compareAndSet(false, true)) {
            Initializer.LOGGER.warn(
                    "VULKANMOD_GPU_TERRAIN_CPU_RECOVERY: GPU rebuild could not publish; retained CPU geometry is active and one CPU rebuild was requested");
        }
    }

    /** Package-private for the baked-model smoke oracle; production callers use dispatch(). */
    static boolean fullyQualified(SectionVoxelSnapshot snapshot) {
        return qualify(snapshot) != null;
    }

    /**
     * Build the GPU work plan strictly from immutable section/model inputs. This is
     * intentionally independent of TerrainBufferBuilder and DrawParameters: the CPU
     * quad count remains only a temporary validation oracle in non-bypass dispatch.
     */
    static Qualification qualify(SectionVoxelSnapshot snapshot) {
        if(snapshot == null)
            return null;

        long modelGeneration = GpuTerrainModelRegistry.generation();
        int faceCount = 0;
        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            int stateId = snapshot.stateId(index);
            if((snapshot.flags(index) & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0) {
                // GPU_FULL_CUBE is captured by a worker from a particular baked-model
                // generation. Revalidate the state against the current immutable
                // registry so a delayed frame operation cannot consume stale resource
                // qualification after a reload.
                if(GpuTerrainModelRegistry.getFullCubeTemplate(stateId) == null)
                    return null;
                faceCount += candidateFaceCount(snapshot, index);
                continue;
            }

            BlockState state = Block.stateById(stateId);
            if(state == null || !state.getFluidState().isEmpty()
                    || state.getRenderShape() != RenderShape.INVISIBLE)
                return null;
        }
        if(modelGeneration != GpuTerrainModelRegistry.generation())
            return null;
        return new Qualification(modelGeneration, faceCount);
    }

    /** Mirrors section_mesher_probe.comp candidateFaceMask() without consulting CPU geometry. */
    private static int candidateFaceCount(SectionVoxelSnapshot snapshot, int index) {
        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = (index >>> 8) & 15;
        int faces = 0;

        if(y == 0) {
            if(!snapshot.boundaryNeighborSolidRender(index, 0)) faces++;
        } else if((snapshot.flags(index - 16) & SectionVoxelSnapshot.SOLID_RENDER) == 0) faces++;
        if(y == 15) {
            if(!snapshot.boundaryNeighborSolidRender(index, 1)) faces++;
        } else if((snapshot.flags(index + 16) & SectionVoxelSnapshot.SOLID_RENDER) == 0) faces++;
        if(z == 0) {
            if(!snapshot.boundaryNeighborSolidRender(index, 2)) faces++;
        } else if((snapshot.flags(index - 256) & SectionVoxelSnapshot.SOLID_RENDER) == 0) faces++;
        if(z == 15) {
            if(!snapshot.boundaryNeighborSolidRender(index, 3)) faces++;
        } else if((snapshot.flags(index + 256) & SectionVoxelSnapshot.SOLID_RENDER) == 0) faces++;
        if(x == 0) {
            if(!snapshot.boundaryNeighborSolidRender(index, 4)) faces++;
        } else if((snapshot.flags(index - 1) & SectionVoxelSnapshot.SOLID_RENDER) == 0) faces++;
        if(x == 15) {
            if(!snapshot.boundaryNeighborSolidRender(index, 5)) faces++;
        } else if((snapshot.flags(index + 1) & SectionVoxelSnapshot.SOLID_RENDER) == 0) faces++;
        return faces;
    }

    record Qualification(long modelGeneration, int faceCount) {}

    private static boolean ensureGpuResources() {
        if(!mesherAttempted) {
            mesherAttempted = true;
            try {
                mesher = new GpuTerrainSectionMesher();
            } catch(RuntimeException error) {
                reportInitializationFailure(error);
            }
        }
        if(mesher == null)
            return false;

        long generation = GpuTerrainModelRegistry.generation();
        if(modelTable != null && modelTable.generation() == generation
                && modelStore != null && modelStore.getResidency().valid())
            return true;
        if(failedModelGeneration == generation)
            return false;

        if(modelStore != null)
            modelStore.close();
        modelStore = new GpuTerrainModelGpuStore();
        modelTable = null;

        try {
            GpuTerrainModelTable table = GpuTerrainModelTable.captureCurrent();
            if(table.generation() != generation || table.templateCount() <= 0
                    || !modelStore.upload(table)) {
                failedModelGeneration = generation;
                return false;
            }
            modelTable = table;
            failedModelGeneration = Long.MIN_VALUE;
            return true;
        } catch(RuntimeException error) {
            failedModelGeneration = generation;
            reportInitializationFailure(error);
            return false;
        }
    }

    private static void reportInitializationFailure(RuntimeException error) {
        if(FAILURE_LOGGED.compareAndSet(false, true)) {
            Initializer.LOGGER.warn(
                    "GPU terrain mesher initialization failed; preserving CPU terrain fallback",
                    error);
        }
    }
}
