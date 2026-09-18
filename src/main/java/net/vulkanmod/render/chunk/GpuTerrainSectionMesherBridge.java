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
 * output. Fully-qualified sections may skip CPU block-model tessellation, including
 * fresh sections that do not yet have a CPU mesh. Explicit hybrid APPEND ownership may
 * accelerate a conservative filtered cube subset while CPU exception geometry remains
 * present; unsupported or failed work requests a normal CPU rebuild.
 *
 * <p>REPLACE accepts only sections whose visible block-model geometry consists entirely
 * of qualified GPU full cubes (plus invisible states). APPEND accepts the worker's
 * filtered v4 subset: only retained GPU_FULL_CUBE cells are validated and emitted,
 * while every other cell remains CPU-owned. Hybrid candidates are required to be
 * interior cells; the worker planner additionally excludes adjacency to visible CPU
 * exceptions because v4 boundary/occlusion data is intentionally conservative.
 * Dispatch runs after input upload and outside an active render pass. Completion can
 * be consumed from a non-blocking helper-fence poll; the frame-fence callback remains
 * the guaranteed exactly-once fallback.</p>
 */
final class GpuTerrainSectionMesherBridge {
    static final String PROPERTY = "vulkanmod.experimentalGpuTerrainMesher";
    static final String CPU_BYPASS_PROPERTY = "vulkanmod.experimentalGpuTerrainCpuBypass";
    static final String DRAW_HANDOFF_PROPERTY = "vulkanmod.experimentalGpuTerrainDrawHandoff";
    static final String HYBRID_PROPERTY = "vulkanmod.experimentalGpuTerrainHybrid";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final boolean CPU_BYPASS_ENABLED = ENABLED
            && Boolean.getBoolean(CPU_BYPASS_PROPERTY)
            && Boolean.getBoolean(DRAW_HANDOFF_PROPERTY);
    private static final boolean HYBRID_ENABLED = CPU_BYPASS_ENABLED
            && Boolean.getBoolean(HYBRID_PROPERTY);
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

    static boolean hybridEnabled() {
        return HYBRID_ENABLED;
    }

    static synchronized void pollCompletions() {
        if(!ENABLED || mesher == null)
            return;
        RenderSystem.assertOnRenderThread();
        mesher.pollCompletions();
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
        if(section.getChunkArea() != area || section.getVoxelGeneration() != generation) {
            GpuTerrainDiagnostics.record("dispatch", "section_stale_before_dispatch",
                    section, generation, null);
            return;
        }

        boolean cpuBypassed = section.stagedGpuTerrainCpuBypassed(generation);
        GpuTerrainDrawHandoff.Ownership ownership = section.stagedGpuTerrainOwnership(generation);
        if(ownership == GpuTerrainDrawHandoff.Ownership.APPEND && !HYBRID_ENABLED) {
            GpuTerrainDiagnostics.record("dispatch", "hybrid_gate_disabled",
                    section, generation, null);
            recoverCpuFallback(area, section, generation);
            return;
        }

        int x = section.xOffset();
        int y = section.yOffset();
        int z = section.zOffset();
        SectionVoxelSnapshot snapshot = area.getVoxels(x, y, z);
        if(snapshot == null) {
            GpuTerrainDiagnostics.record("dispatch", "snapshot_missing",
                    section, generation, null);
            recoverCpuFallback(area, section, generation);
            return;
        }
        if(snapshot.x() != x || snapshot.y() != y || snapshot.z() != z) {
            GpuTerrainDiagnostics.record("dispatch", "snapshot_origin_mismatch",
                    section, generation,
                    "snapshot=(" + snapshot.x() + "," + snapshot.y() + "," + snapshot.z() + ")");
            recoverCpuFallback(area, section, generation);
            return;
        }

        Qualification qualification = ownership == GpuTerrainDrawHandoff.Ownership.APPEND
                ? qualifyHybrid(snapshot) : qualify(snapshot);
        if(qualification == null) {
            GpuTerrainDiagnostics.recordQualificationFailure(snapshot, section, generation);
            recoverCpuFallback(area, section, generation);
            return;
        }
        if(!section.matchesStagedGpuTerrainPreflight(generation,
                qualification.modelGeneration(), qualification.faceCount(), ownership)) {
            GpuTerrainDiagnostics.record("dispatch", "staged_preflight_mismatch",
                    section, generation,
                    "modelGeneration=" + qualification.modelGeneration()
                            + " faces=" + qualification.faceCount()
                            + " ownership=" + ownership);
            recoverCpuFallback(area, section, generation);
            return;
        }

        TerrainRenderType layer = outputLayer();
        DrawBuffers.DrawParameters cpu = section.getDrawParameters(layer);
        // Shadow/validation dispatch still requires its independently-built CPU mesh.
        // CPU-omitting REPLACE and APPEND builds use their immutable worker plans as
        // authoritative; APPEND's draw consumer separately waits for CPU exceptions.
        if(!cpuBypassed && (cpu.indexCount <= 0 || cpu.indexCount % 6 != 0
                || !cpu.vertexBufferSegment.isReady())) {
            GpuTerrainDiagnostics.record("dispatch", "cpu_fallback_not_ready",
                    section, generation,
                    "indexCount=" + cpu.indexCount
                            + " segmentReady=" + cpu.vertexBufferSegment.isReady()
                            + " cpuBypassed=false");
            recoverCpuFallback(area, section, generation);
            return;
        }

        int faceCapacity = qualification.faceCount();
        if(faceCapacity <= 0) {
            GpuTerrainDiagnostics.record("dispatch", "face_count_empty",
                    section, generation, null);
            recoverCpuFallback(area, section, generation);
            return;
        }
        if(faceCapacity > GpuTerrainDrawHandoff.MAX_AUTO_INDEX_FACES) {
            GpuTerrainDiagnostics.record("dispatch", "face_count_overflow",
                    section, generation,
                    "faces=" + faceCapacity + " limit=" + GpuTerrainDrawHandoff.MAX_AUTO_INDEX_FACES);
            recoverCpuFallback(area, section, generation);
            return;
        }

        // Shadow/validation mode still has a newly-built complete CPU mesh, so retain
        // the independent face-count oracle. CPU-omitting plans intentionally differ.
        if(!cpuBypassed && cpu.indexCount / 6 != faceCapacity) {
            GpuTerrainDiagnostics.record("dispatch", "shadow_face_count_mismatch",
                    section, generation,
                    "cpuFaces=" + (cpu.indexCount / 6) + " gpuFaces=" + faceCapacity);
            recoverCpuFallback(area, section, generation);
            return;
        }

        RegionVoxelGpuStore.Residency voxel = area.getGpuVoxelResidency(x, y, z);
        RegionVoxelGpuStore.Residency lighting = area.getGpuSparseLightingResidency(x, y, z);
        if(voxel == null || lighting == null) {
            GpuTerrainDiagnostics.record("dispatch", "input_residency_missing",
                    section, generation,
                    "voxel=" + (voxel != null) + " lighting=" + (lighting != null));
            recoverCpuFallback(area, section, generation);
            return;
        }
        if(!voxel.valid() || !lighting.valid()
                || voxel.generation() != generation || lighting.generation() != generation) {
            GpuTerrainDiagnostics.record("dispatch", "input_residency_stale",
                    section, generation,
                    "voxelValid=" + voxel.valid() + " voxelGeneration=" + voxel.generation()
                            + " lightingValid=" + lighting.valid()
                            + " lightingGeneration=" + lighting.generation());
            recoverCpuFallback(area, section, generation);
            return;
        }

        StorageBuffer voxelPage = area.getGpuVoxelPage(voxel.pageIndex());
        StorageBuffer lightingPage = area.getGpuVoxelPage(lighting.pageIndex());
        if(voxelPage == null || lightingPage == null) {
            GpuTerrainDiagnostics.record("dispatch", "input_page_missing",
                    section, generation,
                    "voxelPage=" + (voxelPage != null) + " lightingPage=" + (lightingPage != null));
            recoverCpuFallback(area, section, generation);
            return;
        }
        if(!ensureGpuResources()) {
            GpuTerrainDiagnostics.record("dispatch", "gpu_resources_unavailable",
                    section, generation, null);
            recoverCpuFallback(area, section, generation);
            return;
        }

        GpuTerrainModelGpuStore.Residency model = modelStore.getResidency();
        long modelGeneration = qualification.modelGeneration();
        if(!model.valid() || modelTable == null
                || model.generation() != modelGeneration
                || model.generation() != modelTable.generation()
                || model.generation() != GpuTerrainModelRegistry.generation()) {
            GpuTerrainDiagnostics.record("dispatch", "model_generation_mismatch",
                    section, generation,
                    "residentValid=" + model.valid()
                            + " residentGeneration=" + model.generation()
                            + " expectedGeneration=" + modelGeneration
                            + " registryGeneration=" + GpuTerrainModelRegistry.generation());
            recoverCpuFallback(area, section, generation);
            return;
        }

        GpuTerrainOutputStore.Reservation reservation = area.reserveGpuTerrainOutput(
                x, y, z, layer, generation, faceCapacity);
        if(reservation == null) {
            GpuTerrainDiagnostics.record("dispatch", "output_reservation_failed",
                    section, generation, "faces=" + faceCapacity);
            recoverCpuFallback(area, section, generation);
            return;
        }

        try {
            boolean submitted = reservation.submitWithTarget(target -> mesher.dispatchAsync(
                    voxelPage, voxel, lightingPage, lighting, model,
                    modelTable.templateCount(), target, faceCapacity,
                    (result, failure) -> completeDispatch(area, section, generation,
                            modelGeneration, faceCapacity, ownership, cpuBypassed, layer,
                            reservation, result, failure)));
            if(!submitted) {
                reservation.complete(0, true);
                GpuTerrainDiagnostics.record("dispatch", "submission_rejected",
                        section, generation, "faces=" + faceCapacity);
                recoverCpuFallback(area, section, generation);
            }
        } catch(RuntimeException error) {
            reservation.complete(0, true);
            GpuTerrainDiagnostics.record("dispatch", "submission_exception",
                    section, generation, error.getClass().getName() + ": " + error.getMessage());
            recoverCpuFallback(area, section, generation);
            reportDispatchFailure("GPU terrain section submission failed; preserving CPU terrain fallback",
                    error);
        }
    }

    private static synchronized void completeDispatch(
            ChunkArea area, RenderSection section, long generation,
            long modelGeneration, int faceCapacity,
            GpuTerrainDrawHandoff.Ownership ownership, boolean cpuBypassed,
            TerrainRenderType layer, GpuTerrainOutputStore.Reservation reservation,
            GpuTerrainSectionMesher.DispatchResult result, RuntimeException failure) {
        RenderSystem.assertOnRenderThread();

        if(section.getChunkArea() != area || section.getVoxelGeneration() != generation) {
            reservation.complete(0, true);
            GpuTerrainDiagnostics.record("completion", "section_stale",
                    section, generation, null);
            return;
        }

        if(failure != null) {
            reservation.complete(0, true);
            GpuTerrainDiagnostics.record("completion", "readback_failed",
                    section, generation, failure.getClass().getName() + ": " + failure.getMessage());
            recoverCpuFallback(area, section, generation);
            reportDispatchFailure("GPU terrain section completion readback failed; preserving CPU terrain fallback",
                    failure);
            return;
        }

        if(GpuTerrainModelRegistry.generation() != modelGeneration
                || !section.matchesStagedGpuTerrainPreflight(
                        generation, modelGeneration, faceCapacity, ownership)) {
            reservation.complete(0, true);
            GpuTerrainDiagnostics.record("completion", "generation_or_preflight_changed",
                    section, generation,
                    "modelGeneration=" + modelGeneration
                            + " registryGeneration=" + GpuTerrainModelRegistry.generation()
                            + " faces=" + faceCapacity
                            + " ownership=" + ownership);
            recoverCpuFallback(area, section, generation);
            return;
        }

        boolean exact = result != null
                && !result.overflow()
                && result.errorFlags() == 0
                && result.requestedFaces() == faceCapacity
                && result.writtenFaces() == faceCapacity;
        if(!exact) {
            reservation.complete(0, true);
            String detail = result == null ? "result=null"
                    : "overflow=" + result.overflow()
                            + " errorFlags=0x" + Integer.toHexString(result.errorFlags())
                            + " requested=" + result.requestedFaces()
                            + " written=" + result.writtenFaces()
                            + " expected=" + faceCapacity;
            GpuTerrainDiagnostics.record("completion", "output_mismatch",
                    section, generation, detail);
            recoverCpuFallback(area, section, generation);
            return;
        }

        boolean published = reservation.complete(result.writtenFaces(), false);
        if(!published) {
            GpuTerrainDiagnostics.record("completion", "publication_rejected",
                    section, generation, "faces=" + result.writtenFaces());
            recoverCpuFallback(area, section, generation);
            return;
        }
        if(!section.publishGpuTerrainDrawHandoff(generation, ownership)) {
            area.invalidateGpuTerrainOutput(section.xOffset(), section.yOffset(),
                    section.zOffset(), generation);
            GpuTerrainDiagnostics.record("completion", "draw_handoff_rejected",
                    section, generation, "ownership=" + ownership);
            recoverCpuFallback(area, section, generation);
            return;
        }

        GpuTerrainDiagnostics.recordSuccess("published", section, generation,
                result.writtenFaces(), cpuBypassed);
        if(ACTIVE_LOGGED.compareAndSet(false, true)) {
            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_MESHER_ACTIVE: section=({}, {}, {}) layer={} faces={} ownership={} cpuBypassed={}; completion published without a blocking helper fence wait",
                    section.xOffset(), section.yOffset(), section.zOffset(), layer.ordinal(),
                    result.writtenFaces(), ownership, cpuBypassed);
        }
    }

    private static void reportDispatchFailure(String message, RuntimeException error) {
        if(FAILURE_LOGGED.compareAndSet(false, true))
            Initializer.LOGGER.warn(message, error);
    }

    /**
     * Recover only a still-current build that deliberately skipped CPU model
     * tessellation. REPLACE rebuilds keep prior CPU geometry when available; fresh
     * REPLACE/APPEND sections may be incomplete until the recovery rebuild publishes.
     * Generation turnover makes delayed recovery a no-op.
     */
    static void recoverCpuFallback(ChunkArea area, RenderSection section, long generation) {
        if(!CPU_BYPASS_ENABLED || area == null || section == null)
            return;
        RenderSystem.assertOnRenderThread();
        if(section.getChunkArea() != area || section.getVoxelGeneration() != generation)
            return;
        if(section.requestGpuTerrainCpuRecovery(generation)) {
            GpuTerrainDiagnostics.record("recovery", "cpu_rebuild_requested",
                    section, generation, null);
            if(RECOVERY_LOGGED.compareAndSet(false, true)) {
                Initializer.LOGGER.warn(
                        "VULKANMOD_GPU_TERRAIN_CPU_RECOVERY: GPU terrain could not publish; a CPU recovery rebuild was requested and prior CPU geometry remains active when available");
            }
        }
    }

    /** Package-private for the baked-model smoke oracle; production callers use dispatch(). */
    static boolean fullyQualified(SectionVoxelSnapshot snapshot) {
        return qualify(snapshot) != null;
    }

    /**
     * Build the full-section GPU work plan strictly from immutable section/model
     * inputs. Every visible block model must be a qualified GPU full cube.
     */
    static Qualification qualify(SectionVoxelSnapshot snapshot) {
        if(snapshot == null)
            return null;

        long modelGeneration = GpuTerrainModelRegistry.generation();
        int faceCount = 0;
        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            int stateId = snapshot.stateId(index);
            int flags = snapshot.flags(index);

            // Fluids and block entities remain hard CPU ownership boundaries even
            // when the associated baked block model happens to qualify as a full cube.
            if((flags & SectionVoxelSnapshot.HAS_FLUID) != 0
                    || (flags & SectionVoxelSnapshot.HAS_BLOCK_ENTITY) != 0)
                return null;

            if((flags & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0) {
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

    /**
     * Validate a worker-filtered v4 subset for APPEND ownership. Non-GPU cells are
     * deliberately ignored because their geometry remains CPU-owned. Retained GPU
     * cells must still match the current immutable model generation, remain free of
     * fluid/block-entity semantics, and stay away from section boundaries where v4
     * lacks enough neighboring qualification information for hybrid ownership.
     */
    static Qualification qualifyHybrid(SectionVoxelSnapshot snapshot) {
        if(snapshot == null || !HYBRID_ENABLED)
            return null;

        long modelGeneration = GpuTerrainModelRegistry.generation();
        int faceCount = 0;
        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            int flags = snapshot.flags(index);
            if((flags & SectionVoxelSnapshot.GPU_FULL_CUBE) == 0)
                continue;
            if((flags & (SectionVoxelSnapshot.HAS_FLUID
                    | SectionVoxelSnapshot.HAS_BLOCK_ENTITY)) != 0)
                return null;

            int x = index & 15;
            int y = (index >>> 4) & 15;
            int z = (index >>> 8) & 15;
            if(x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15)
                return null;

            if(GpuTerrainModelRegistry.getFullCubeTemplate(snapshot.stateId(index)) == null)
                return null;
            faceCount += candidateFaceCount(snapshot, index);
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
