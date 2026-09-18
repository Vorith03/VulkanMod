package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.GpuTerrainOutputStore;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import org.joml.Vector3i;

/**
 * CI-only proof for the production non-blocking section-mesher completion path.
 *
 * <p>The synchronous mesher oracle proves shader output. This smoke instead launches
 * real {@link GpuTerrainSectionMesher#dispatchAsync} submissions from a recording
 * frame, deliberately fills the bounded descriptor pool, makes the submitted helper
 * fences deterministically signaled in test code, and consumes them through the same
 * non-blocking poll used by production. It then allows the original frame-slot
 * callbacks to drain and verifies they cannot deliver duplicate completion.</p>
 */
public final class GpuTerrainSectionMesherAsyncSmokeTest {
    private static final int EXPECTED_FACES = 6;
    private static final int MAX_PROBE_SUBMISSIONS = 128;
    private static final long GENERATION = 913L;

    private static boolean armed;
    private static boolean started;
    private static AsyncRun active;

    private GpuTerrainSectionMesherAsyncSmokeTest() {}

    /** Prepare stable inputs after the synchronous model-bake probes, then arm. */
    public static synchronized void arm() {
        if(armed || started)
            return;

        AsyncRun run = new AsyncRun();
        try {
            run.prepare();
        } catch(RuntimeException | Error failure) {
            run.abort();
            throw failure;
        }
        active = run;
        armed = true;
    }

    /** Called at the tail of each real recording-frame begin while the smoke is active. */
    public static synchronized void onFrameStarted() {
        if(Renderer.getInstance() == null || !Renderer.getInstance().isRecordingFrame())
            return;

        AsyncRun run = active;
        if(armed && !started) {
            require(run != null, "Async section-mesher smoke lost its prepared run");
            armed = false;
            started = true;
            try {
                run.submitAndPollSignaledHelpers();
            } catch(RuntimeException | Error failure) {
                active = null;
                run.abort();
                throw failure;
            }
            return;
        }

        if(started && run != null)
            run.afterLaterFrameStarted();
    }

    private static synchronized void finish(AsyncRun run) {
        require(active == run,
                "Async section-mesher smoke completed an inactive run");
        active = null;
        run.cleanup();
        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_ASYNC_COMPLETION_OK: {} accepted submissions completed through non-blocking helper-fence polling; bounded descriptor saturation rejected the next submission; fallback frame callbacks drained without duplicate completion; output published exactly once",
                run.submittedCount);
        Initializer.LOGGER.info("Vulkan smoke test passed");
        System.exit(0);
    }

    private static CanonicalCubeLightingSmokeTest.SparseGpuFixture withQualifiedModelState(
            CanonicalCubeLightingSmokeTest.SparseGpuFixture fixture,
            GpuTerrainModelTable table) {
        int qualifiedStateId = table.stateIdForTemplate(0);
        require(table.templateIndexForStateId(qualifiedStateId) == 0,
                "Qualified async-smoke model state must round-trip through the table");

        SectionVoxelSnapshot source = fixture.voxel();
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(
                source.x(), source.y(), source.z());
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i) {
            int stateId = i == fixture.blockIndex() ? qualifiedStateId : source.stateId(i);
            builder.add(stateId, source.flags(i));
        }
        return new CanonicalCubeLightingSmokeTest.SparseGpuFixture(
                builder.finish(), fixture.lighting(), fixture.faceWords(), fixture.blockIndex());
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class AsyncRun {
        private GpuTerrainModelGpuStore modelStore;
        private RegionVoxelGpuStore inputStore;
        private ChunkArea area;
        private GpuTerrainSectionMesher mesher;
        private CanonicalCubeLightingSmokeTest.SparseGpuFixture fixture;
        private GpuTerrainModelTable table;
        private GpuTerrainModelGpuStore.Residency model;
        private RegionVoxelGpuStore.Residency voxel;
        private RegionVoxelGpuStore.Residency lighting;
        private GpuTerrainOutputStore.Reservation reservation;
        private int submittedFrame = -1;
        private int submittedCount;
        private int completionCount;
        private int laterFrameStarts;
        private boolean submissionReturned;
        private boolean completionsObserved;
        private boolean aborted;
        private boolean cleaned;

        void prepare() {
            if(AreaUploadManager.INSTANCE == null)
                throw new AssertionError("Async section-mesher smoke requires the terrain upload manager");

            table = GpuTerrainModelTable.captureCurrent();
            require(table.templateCount() > 0,
                    "Async section-mesher smoke requires a qualified model template");
            fixture = withQualifiedModelState(
                    CanonicalCubeLightingSmokeTest.sparseGpuFixture(0), table);

            modelStore = new GpuTerrainModelGpuStore();
            inputStore = new RegionVoxelGpuStore();
            area = new ChunkArea(83,
                    new Vector3i(fixture.voxel().x(), fixture.voxel().y(), fixture.voxel().z()));
            mesher = new GpuTerrainSectionMesher();

            require(modelStore.upload(table),
                    "Async section-mesher model table must upload");
            model = modelStore.getResidency();
            require(model.valid() && model.generation() == table.generation(),
                    "Async section-mesher model table must be current");

            require(inputStore.upload(0, fixture.voxel(), GENERATION),
                    "Async section-mesher voxel input must queue");
            require(inputStore.uploadLighting(0, fixture.lighting(), GENERATION),
                    "Async section-mesher lighting input must queue");
            AreaUploadManager.INSTANCE.submitUploads();

            voxel = inputStore.getResidency(0);
            lighting = inputStore.getLightingResidency(0);
            require(voxel.valid() && lighting.valid()
                            && voxel.generation() == GENERATION
                            && lighting.generation() == GENERATION,
                    "Async section-mesher inputs must publish under one generation");

            reservation = area.reserveGpuTerrainOutput(
                    fixture.voxel().x(), fixture.voxel().y(), fixture.voxel().z(),
                    TerrainRenderType.SOLID, GENERATION, EXPECTED_FACES);
            require(reservation != null,
                    "Async section-mesher output reservation must fit");
        }

        void submitAndPollSignaledHelpers() {
            submittedFrame = Renderer.getCurrentFrame();
            require(GpuTerrainSectionMesher.readbackBarrierContractMatchesVulkan(),
                    "Async section-mesher readback must retain transfer-write -> host-read synchronization");
            require(GpuTerrainSectionMesher.readbackBarrierSmokeTrackingEnabled(),
                    "Async section-mesher smoke must track submitted readback barriers");
            int readbackBarriersBefore = GpuTerrainSectionMesher.readbackBarrierSmokeCount();
            boolean pinned = reservation.submitWithTarget(target -> {
                for(int attempt = 0; attempt < MAX_PROBE_SUBMISSIONS; ++attempt) {
                    boolean accepted = mesher.dispatchAsync(
                            inputStore.getPageBuffer(voxel.pageIndex()), voxel,
                            inputStore.getPageBuffer(lighting.pageIndex()), lighting,
                            model, table.templateCount(), target, EXPECTED_FACES,
                            this::completeOne);
                    if(!accepted)
                        break;
                    submittedCount++;
                }
                require(submittedCount > 0,
                        "Async section-mesher must accept at least one production submission");
                require(submittedCount < MAX_PROBE_SUBMISSIONS,
                        "Async section-mesher descriptor pool must remain bounded");
                return true;
            });
            require(pinned,
                    "Async section-mesher reservation must pin after submission");
            submissionReturned = true;
            require(GpuTerrainSectionMesher.readbackBarrierSmokeCount()
                            == readbackBarriersBefore + submittedCount,
                    "Every accepted async dispatch must record one transfer-to-host barrier");

            // Test-only synchronization makes every helper fence deterministically
            // signaled. Production does not wait here: it merely polls status once per
            // render frame and falls back to the existing frame-slot callback.
            Vulkan.waitIdle();
            mesher.pollCompletions();
            require(completionCount == submittedCount,
                    "Non-blocking helper-fence poll must consume every signaled submission");
            require(completionsObserved,
                    "Async section-mesher poll did not publish completed output");
        }

        void afterLaterFrameStarted() {
            mesher.pollCompletions();
            laterFrameStarts++;
            require(completionCount == submittedCount,
                    "Frame fallback delivered duplicate async section-mesher completion");

            // The frame callback and Synchronization command-buffer reset for the
            // submitted slot both execute before beginFrame reaches this smoke hook.
            // Seeing that slot again therefore proves the fallback callbacks drained.
            if(laterFrameStarts > 0 && Renderer.getCurrentFrame() == submittedFrame)
                finish(this);
        }

        private void completeOne(GpuTerrainSectionMesher.DispatchResult result,
                                 RuntimeException failure) {
            if(aborted)
                return;
            require(submissionReturned,
                    "Async section-mesher callback must not run inline with submission");
            require(failure == null,
                    "Async section-mesher readback failed: " + failure);
            require(result != null,
                    "Async section-mesher callback must provide a result");
            require(result.requestedFaces() == EXPECTED_FACES,
                    "Async section-mesher requested-face count mismatch");
            require(result.writtenFaces() == EXPECTED_FACES,
                    "Async section-mesher written-face count mismatch");
            require(!result.overflow() && result.errorFlags() == 0,
                    "Async section-mesher production dispatch must complete exactly");

            completionCount++;
            require(completionCount <= submittedCount,
                    "Async section-mesher delivered too many callbacks");
            if(completionCount != submittedCount)
                return;

            require(area.publishGpuTerrainOutput(
                            reservation, EXPECTED_FACES, false),
                    "Async section-mesher completed output must publish once");
            GpuTerrainOutputStore.Residency resident = area.getGpuTerrainOutputResidency(
                    fixture.voxel().x(), fixture.voxel().y(), fixture.voxel().z(),
                    TerrainRenderType.SOLID);
            require(resident != null && resident.valid()
                            && resident.generation() == GENERATION
                            && resident.faceCount() == EXPECTED_FACES
                            && resident.byteLength() == EXPECTED_FACES
                            * GpuTerrainOutputStore.BYTES_PER_FACE,
                    "Async section-mesher published residency mismatch");
            completionsObserved = true;
        }

        void abort() {
            aborted = true;
            try {
                Vulkan.waitIdle();
            } catch(RuntimeException | Error ignored) {
                // Preserve the original smoke failure while making cleanup best-effort.
            }
            cleanup();
        }

        void cleanup() {
            if(cleaned)
                return;
            cleaned = true;
            if(mesher != null)
                mesher.close();
            if(area != null)
                area.releaseBuffers();
            if(inputStore != null)
                inputStore.close();
            if(modelStore != null)
                modelStore.close();
        }
    }
}
