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
 * First production bridge from generation-owned section inputs to persistent GPU
 * terrain output. CPU terrain remains fully built and resident; this helper only
 * publishes an optional replacement after every fail-closed check succeeds.
 *
 * <p>The initial bridge is deliberately conservative: it accepts only sections whose
 * visible block-model geometry consists entirely of already-qualified GPU full cubes
 * (plus invisible states), and it still cross-checks the independently planned GPU
 * face count against the authoritative CPU quad count. Mixed or unsupported sections
 * remain CPU-only. The bridge is default-off and runs from a later frame operation,
 * after the input upload submission has become generation-visible and outside any
 * active render pass.</p>
 */
final class GpuTerrainSectionMesherBridge {
    static final String PROPERTY = "vulkanmod.experimentalGpuTerrainMesher";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final AtomicBoolean ACTIVE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private static boolean mesherAttempted;
    private static GpuTerrainSectionMesher mesher;
    private static GpuTerrainModelGpuStore modelStore;
    private static GpuTerrainModelTable modelTable;
    private static long failedModelGeneration = Long.MIN_VALUE;

    private GpuTerrainSectionMesherBridge() {}

    static boolean enabled() {
        return ENABLED;
    }

    static synchronized void dispatch(ChunkArea area, RenderSection section, long generation) {
        if(!ENABLED || area == null || section == null)
            return;

        RenderSystem.assertOnRenderThread();
        if(section.getChunkArea() != area || section.getVoxelGeneration() != generation)
            return;

        int x = section.xOffset();
        int y = section.yOffset();
        int z = section.zOffset();
        SectionVoxelSnapshot snapshot = area.getVoxels(x, y, z);
        Qualification qualification = qualify(snapshot);
        if(snapshot == null || snapshot.x() != x || snapshot.y() != y || snapshot.z() != z
                || qualification == null)
            return;

        TerrainRenderType layer = Initializer.CONFIG.uniqueOpaqueLayer
                ? TerrainRenderType.CUTOUT_MIPPED : TerrainRenderType.CUTOUT;
        DrawBuffers.DrawParameters cpu = section.getDrawParameters(layer);
        if(cpu.indexCount <= 0 || cpu.indexCount % 6 != 0
                || !cpu.vertexBufferSegment.isReady())
            return;

        int cpuFaces = cpu.indexCount / 6;
        int faceCapacity = qualification.faceCount();
        // Keep the CPU mesh as an independent semantic oracle while the GPU subset is
        // still experimental. Capacity and expected GPU output are now derived only
        // from immutable section inputs, so a future bypass no longer needs CPU output
        // to size the dispatch; removing this cross-check is a separate safety gate.
        if(faceCapacity <= 0 || faceCapacity > GpuTerrainDrawHandoff.MAX_AUTO_INDEX_FACES
                || cpuFaces != faceCapacity)
            return;

        RegionVoxelGpuStore.Residency voxel = area.getGpuVoxelResidency(x, y, z);
        RegionVoxelGpuStore.Residency lighting = area.getGpuSparseLightingResidency(x, y, z);
        if(voxel == null || lighting == null || !voxel.valid() || !lighting.valid()
                || voxel.generation() != generation || lighting.generation() != generation)
            return;

        StorageBuffer voxelPage = area.getGpuVoxelPage(voxel.pageIndex());
        StorageBuffer lightingPage = area.getGpuVoxelPage(lighting.pageIndex());
        if(voxelPage == null || lightingPage == null || !ensureGpuResources())
            return;

        GpuTerrainModelGpuStore.Residency model = modelStore.getResidency();
        if(!model.valid() || modelTable == null
                || model.generation() != qualification.modelGeneration()
                || model.generation() != modelTable.generation()
                || model.generation() != GpuTerrainModelRegistry.generation())
            return;

        GpuTerrainOutputStore.Reservation reservation = area.reserveGpuTerrainOutput(
                x, y, z, layer, generation, faceCapacity);
        if(reservation == null)
            return;

        try {
            GpuTerrainSectionMesher.DispatchResult result = reservation.withTarget(target ->
                    mesher.dispatch(voxelPage, voxel, lightingPage, lighting, model,
                            modelTable.templateCount(), target, faceCapacity));

            boolean exact = result != null
                    && !result.overflow()
                    && result.errorFlags() == 0
                    && result.requestedFaces() == faceCapacity
                    && result.writtenFaces() == faceCapacity;
            if(!exact || section.getVoxelGeneration() != generation) {
                area.publishGpuTerrainOutput(reservation, 0, true);
                return;
            }

            if(area.publishGpuTerrainOutput(reservation, result.writtenFaces(), false)
                    && ACTIVE_LOGGED.compareAndSet(false, true)) {
                Initializer.LOGGER.info(
                        "VULKANMOD_GPU_TERRAIN_MESHER_ACTIVE: section=({}, {}, {}) layer={} faces={}; exact-generation CPU mesh remains resident as fallback",
                        x, y, z, layer.ordinal(), result.writtenFaces());
            }
        } catch(RuntimeException error) {
            area.publishGpuTerrainOutput(reservation, 0, true);
            if(FAILURE_LOGGED.compareAndSet(false, true)) {
                Initializer.LOGGER.warn(
                        "GPU terrain section dispatch failed; preserving CPU terrain fallback",
                        error);
            }
        }
    }

    /** Package-private for the baked-model smoke oracle; production callers use dispatch(). */
    static boolean fullyQualified(SectionVoxelSnapshot snapshot) {
        return qualify(snapshot) != null;
    }

    /**
     * Build the GPU work plan strictly from immutable section/model inputs. This is
     * intentionally independent of TerrainBufferBuilder and DrawParameters: the CPU
     * quad count remains only a temporary validation oracle in dispatch().
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
