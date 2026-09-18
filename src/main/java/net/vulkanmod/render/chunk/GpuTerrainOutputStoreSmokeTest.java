package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Vulkan;
import org.joml.Vector3i;

import java.util.ArrayDeque;

/** CI-only lifecycle oracle for non-consuming GPU terrain output ownership. */
public final class GpuTerrainOutputStoreSmokeTest {
    private GpuTerrainOutputStoreSmokeTest() {}

    public static void verify() {
        DrawBuffers drawBuffers = new DrawBuffers();
        ArrayDeque<Runnable> residentRetirements = new ArrayDeque<>();
        GpuTerrainOutputStore store = null;
        try {
            drawBuffers.allocateBuffers();
            int initialCapacity = drawBuffers.vertexBuffer.getCapacityBytes();
            store = new GpuTerrainOutputStore(drawBuffers, residentRetirements::addLast);

            var first = requireReservation(store.reserve(7, TerrainRenderType.SOLID, 10L, 64),
                    "Initial GPU terrain output reservation must fit");
            require(!store.getResidency(7, TerrainRenderType.SOLID).valid(),
                    "Pending GPU terrain output must remain undiscoverable");
            var firstTarget = store.target(first);
            require(firstTarget != null && firstTarget.bufferId() != 0L,
                    "Pending GPU terrain output must resolve the current area buffer");
            require(firstTarget.byteCapacity() == 64 * GpuTerrainOutputStore.BYTES_PER_FACE,
                    "GPU terrain output reservation size mismatch");
            require(firstTarget.byteOffset() % GpuTerrainOutputStore.VERTEX_BYTES == 0,
                    "GPU terrain output offset must remain vertex aligned");
            require(drawBuffers.vertexBuffer.getCapacityBytes() == initialCapacity,
                    "GPU terrain reservation must not grow the area vertex buffer");

            require(store.publish(first, 32, false),
                    "Completed bounded GPU terrain output must publish");
            var resident = store.getResidency(7, TerrainRenderType.SOLID);
            require(resident.valid() && resident.generation() == 10L
                            && resident.faceCount() == 32
                            && resident.byteLength() == 32 * GpuTerrainOutputStore.BYTES_PER_FACE,
                    "Published GPU terrain output residency mismatch");

            // Future-generation APPEND work must be able to reserve and complete
            // without changing what the renderer can discover until an explicit
            // atomic commit point.
            var stagedOld = requireReservation(store.reserve(
                            6, TerrainRenderType.SOLID, 50L, 4),
                    "Staged-output baseline reservation must fit");
            require(store.publish(stagedOld, 4, false),
                    "Staged-output baseline must publish");
            var stagedVisible = store.getResidency(6, TerrainRenderType.SOLID);
            int stagedOldOffset = stagedVisible.vertexOffset();
            long stagedRevision = drawBuffers.getMeshRevision(TerrainRenderType.SOLID);

            var staged = requireStagedReservation(store.reserveStaged(
                            6, TerrainRenderType.SOLID, 51L, 6),
                    "Future-generation staged GPU output must reserve");
            require(store.getSectionGeneration(6) == 50L
                            && store.getResidency(6, TerrainRenderType.SOLID).valid()
                            && store.getResidency(6, TerrainRenderType.SOLID).generation() == 50L
                            && store.getResidency(6, TerrainRenderType.SOLID).vertexOffset()
                            == stagedOldOffset,
                    "Future-generation staged output must preserve the current resident");
            require(drawBuffers.getMeshRevision(TerrainRenderType.SOLID) == stagedRevision,
                    "Staging future GPU output must not invalidate the visible draw cache");
            require(staged.submitWithTarget(target -> target != null),
                    "Future-generation staged GPU output must enter submitted ownership");
            require(staged.complete(6, false) && staged.ready(),
                    "Completed staged GPU output must become commit-ready");
            require(store.getSectionGeneration(6) == 50L
                            && store.getResidency(6, TerrainRenderType.SOLID).generation() == 50L,
                    "Completed staged output must remain invisible before commit");
            require(staged.commit(),
                    "Ready future-generation GPU output must commit");
            stagedVisible = store.getResidency(6, TerrainRenderType.SOLID);
            require(stagedVisible.valid() && stagedVisible.generation() == 51L
                            && stagedVisible.faceCount() == 6,
                    "Committed staged GPU output must atomically advance residency");

            var abandoned = requireStagedReservation(store.reserveStaged(
                            6, TerrainRenderType.SOLID, 52L, 5),
                    "Discard-path staged GPU output must reserve");
            require(abandoned.submitWithTarget(target -> target != null),
                    "Discard-path staged GPU output must submit");
            abandoned.discard();
            require(!abandoned.complete(5, false),
                    "Cancelled in-flight staged output must retire on completion without publishing");
            stagedVisible = store.getResidency(6, TerrainRenderType.SOLID);
            require(stagedVisible.valid() && stagedVisible.generation() == 51L
                            && stagedVisible.faceCount() == 6,
                    "Discarded staged output must not disturb the committed generation");

            var turnoverBase = requireReservation(store.reserve(
                            5, TerrainRenderType.SOLID, 60L, 4),
                    "Staged-turnover baseline reservation must fit");
            require(store.publish(turnoverBase, 4, false),
                    "Staged-turnover baseline must publish");
            var turnoverStaged = requireStagedReservation(store.reserveStaged(
                            5, TerrainRenderType.SOLID, 61L, 4),
                    "Future staged output for turnover must reserve");
            require(turnoverStaged.submitWithTarget(target -> target != null),
                    "Future staged output for turnover must submit");
            store.invalidateSection(5, 61L);
            require(!turnoverStaged.complete(4, false)
                            && !turnoverStaged.ready()
                            && !store.getResidency(5, TerrainRenderType.SOLID).valid(),
                    "Generation turnover must cancel submitted staged output without publishing it");

            // A complete-CPU dirty rebuild invalidates GPU output first, advancing
            // the store to the new section generation before replacement GPU work is
            // reserved. Staging that current generation must therefore be legal.
            store.invalidateSection(7, 70L);
            var currentStage = requireStagedReservation(store.reserveStaged(
                            7, TerrainRenderType.SOLID, 70L, 4),
                    "Current-generation staging must work after dirty invalidation");
            require(currentStage.submitWithTarget(target -> target != null)
                            && currentStage.complete(4, false)
                            && currentStage.canCommit()
                            && currentStage.commit(),
                    "Current-generation staged output must commit after exact completion");
            var currentResidency = store.getResidency(7, TerrainRenderType.SOLID);
            require(currentResidency.valid() && currentResidency.generation() == 70L
                            && currentResidency.faceCount() == 4,
                    "Current-generation staged commit must publish residency");

            var sameGenerationRevoked = requireStagedReservation(store.reserveStaged(
                            7, TerrainRenderType.SOLID, 70L, 5),
                    "Same-generation staged retry must reserve without replacing the resident");
            require(sameGenerationRevoked.submitWithTarget(target -> target != null),
                    "Same-generation staged retry must submit");
            store.invalidateSection(7, 70L);
            require(!sameGenerationRevoked.complete(5, false)
                            && !store.getResidency(7, TerrainRenderType.SOLID).valid(),
                    "Explicit same-generation invalidation must revoke staged retry and resident");

            // A same-generation retry must leave the previous result available if
            // the new compute result overflows its reservation.
            var retry = requireReservation(store.reserve(7, TerrainRenderType.SOLID, 10L, 16),
                    "Same-generation replacement reservation must fit");
            require(store.getResidency(7, TerrainRenderType.SOLID).valid(),
                    "Same-generation retry must preserve previous resident output");
            require(!store.publish(retry, 17, true),
                    "Overflowed GPU terrain output must fail closed");
            resident = store.getResidency(7, TerrainRenderType.SOLID);
            require(resident.valid() && resident.generation() == 10L
                            && resident.faceCount() == 32,
                    "Overflowed retry must not displace valid same-generation output");

            // Section generation is shared across terrain layers. Publish a peer
            // layer at generation 10 so generation 11 on SOLID must revoke both.
            var peer = requireReservation(store.reserve(7, TerrainRenderType.CUTOUT, 10L, 8),
                    "Same-generation peer terrain layer reservation must fit");
            require(store.publish(peer, 8, false),
                    "Same-generation peer terrain layer must publish");
            require(store.getResidency(7, TerrainRenderType.CUTOUT).valid(),
                    "Peer terrain layer residency must be visible before turnover");

            // A newer section generation immediately revokes every older layer even
            // when its replacement later fails, leaving the CPU mesh authoritative.
            var newer = requireReservation(store.reserve(7, TerrainRenderType.SOLID, 11L, 16),
                    "Newer-generation GPU terrain reservation must fit");
            require(!store.getResidency(7, TerrainRenderType.SOLID).valid()
                            && !store.getResidency(7, TerrainRenderType.CUTOUT).valid(),
                    "Generation turnover must revoke every stale terrain layer immediately");
            require(store.getSectionGeneration(7) == 11L,
                    "GPU terrain output must track one generation per section");
            require(!store.publish(newer, 0, false),
                    "Zero-face GPU terrain output must remain on CPU fallback");
            require(!store.getResidency(7, TerrainRenderType.SOLID).valid(),
                    "Failed newer generation must not resurrect stale GPU terrain output");
            require(store.reserve(7, TerrainRenderType.SOLID, 10L, 8) == null,
                    "Stale GPU terrain generation must be rejected");
            require(store.reserve(7, TerrainRenderType.CUTOUT_MIPPED, 10L, 8) == null,
                    "Never-published terrain layer must still reject stale section generation");

            var finalResult = requireReservation(store.reserve(7, TerrainRenderType.CUTOUT, 12L, 8),
                    "Independent supported terrain layer reservation must fit");
            require(store.publish(finalResult, 8, false),
                    "Supported cutout GPU terrain output must publish");
            store.invalidateSection(7, 12L);
            require(!store.getResidency(7, TerrainRenderType.CUTOUT).valid()
                            && store.getSectionGeneration(7) == 12L,
                    "Section invalidation must revoke every terrain-layer output");
            require(store.reserve(7, TerrainRenderType.CUTOUT_MIPPED, 11L, 8) == null,
                    "Explicit invalidation must reject stale work on unused layers");
            require(store.reserve(7, TerrainRenderType.TRANSLUCENT, 13L, 8) == null
                            && store.reserve(7, TerrainRenderType.TRIPWIRE, 13L, 8) == null,
                    "Translucent and tripwire terrain must remain CPU-only");

            // Simulate the frame-fence retirement point for the smaller residents
            // above before using a maximum-size reservation as a precise capacity
            // oracle for published-resident lifetime.
            runRetirements(residentRetirements);

            // A published resident may already be referenced by an in-flight or
            // frame-local indirect command. Logical invalidation must therefore hide
            // it immediately while keeping its physical slice unavailable until the
            // owning frame slot retires.
            var residentMaximum = requireReservation(store.reserve(8, TerrainRenderType.SOLID, 20L,
                            GpuTerrainOutputStore.MAX_FACES),
                    "One maximum published GPU terrain output must fit the initial area buffer");
            require(store.publish(residentMaximum, 1, false),
                    "Maximum-capacity GPU terrain reservation must publish a bounded resident");
            store.invalidateSection(8, 21L);
            require(!store.getResidency(8, TerrainRenderType.SOLID).valid(),
                    "Invalidated published GPU terrain output must disappear logically immediately");
            require(store.reserve(9, TerrainRenderType.SOLID, 20L,
                            GpuTerrainOutputStore.MAX_FACES) == null,
                    "Invalidated published GPU terrain output must stay physically pinned before frame retirement");
            require(drawBuffers.vertexBuffer.getCapacityBytes() == initialCapacity,
                    "Pinned published terrain pressure must never grow the area vertex buffer");
            runRetirements(residentRetirements);

            var afterResidentRetirement = requireReservation(store.reserve(9, TerrainRenderType.SOLID, 20L,
                            GpuTerrainOutputStore.MAX_FACES),
                    "Frame retirement must release stale published output capacity");
            require(store.publish(afterResidentRetirement, 1, false),
                    "Released published terrain capacity must remain reusable");
            store.invalidateSection(9, 21L);
            runRetirements(residentRetirements);

            // A submitted maximum reservation must remain physically unavailable after
            // logical generation turnover. Only completion may return that slice to the
            // AreaBuffer free list, preventing in-flight compute from racing reuse.
            var maximum = requireReservation(store.reserve(10, TerrainRenderType.SOLID, 30L,
                            GpuTerrainOutputStore.MAX_FACES),
                    "One maximum bounded GPU terrain output must fit the initial area buffer");
            require(maximum.submitWithTarget(target -> target != null),
                    "Maximum GPU terrain reservation must enter submitted ownership");
            store.invalidateSection(10, 31L);
            require(store.reserve(11, TerrainRenderType.SOLID, 30L,
                            GpuTerrainOutputStore.MAX_FACES) == null,
                    "Invalidated in-flight GPU terrain output must stay physically pinned");
            require(drawBuffers.vertexBuffer.getCapacityBytes() == initialCapacity,
                    "Pinned GPU terrain pressure must never grow the area vertex buffer");
            require(!maximum.complete(0, true),
                    "Stale submitted GPU terrain completion must never publish");

            var afterCompletion = requireReservation(store.reserve(11, TerrainRenderType.SOLID, 30L,
                            GpuTerrainOutputStore.MAX_FACES),
                    "Frame-fence completion must release stale submitted output capacity");
            require(store.publish(afterCompletion, 1, false),
                    "Released GPU terrain capacity must remain reusable after completion");
            store.invalidateSection(11, 31L);
            runRetirements(residentRetirements);

            verifyChunkAreaLifecycle();

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_OUTPUT_RESIDENCY_OK: storage-capable area vertices, no-growth reservation, published-resident pinning through frame retirement, submitted-output pinning through completion, section-global generation revocation, non-visible future-generation staging/commit/discard, ChunkArea teardown/reuse safety, same-generation retry fallback, translucent/tripwire CPU fallback");
        } finally {
            Vulkan.waitIdle();
            if(store != null)
                store.close();
            runRetirements(residentRetirements);
            drawBuffers.releaseBuffers();
        }
    }

    private static void verifyChunkAreaLifecycle() {
        int x = -128;
        int y = -64;
        int z = 256;
        ChunkArea area = new ChunkArea(37, new Vector3i(x, y, z));
        try {
            var first = requireReservation(area.reserveGpuTerrainOutput(
                            x, y, z, TerrainRenderType.SOLID, 30L, 8),
                    "ChunkArea GPU terrain reservation must fit");
            var firstTarget = area.getGpuTerrainOutputTarget(first);
            require(firstTarget != null && firstTarget.bufferId() != 0L,
                    "ChunkArea GPU terrain reservation must resolve the area buffer");
            require(area.publishGpuTerrainOutput(first, 8, false),
                    "ChunkArea GPU terrain result must publish");
            var resident = area.getGpuTerrainOutputResidency(
                    x, y, z, TerrainRenderType.SOLID);
            require(resident != null && resident.valid() && resident.generation() == 30L,
                    "ChunkArea GPU terrain residency mismatch");

            // Exercise the compatibility invalidation path with no voxel GPU store.
            // It must derive generation from the output owner rather than defaulting
            // to zero and accidentally leaving generation-30 geometry discoverable.
            area.removeVoxels(x, y, z);
            resident = area.getGpuTerrainOutputResidency(x, y, z, TerrainRenderType.SOLID);
            require(resident != null && !resident.valid() && resident.generation() == 31L,
                    "ChunkArea compatibility invalidation must advance GPU output generation");
            require(area.reserveGpuTerrainOutput(
                            x, y, z, TerrainRenderType.CUTOUT_MIPPED, 30L, 4) == null,
                    "ChunkArea must reject stale output work on another terrain layer");

            var current = requireReservation(area.reserveGpuTerrainOutput(
                            x, y, z, TerrainRenderType.CUTOUT, 31L, 4),
                    "Current ChunkArea GPU terrain generation must reserve");
            var currentTarget = area.getGpuTerrainOutputTarget(current);
            require(currentTarget != null,
                    "Current ChunkArea GPU terrain generation must resolve a target");
            require(area.publishGpuTerrainOutput(current, 4, false),
                    "Current ChunkArea GPU terrain generation must publish");

            // Coarse-area reuse closes GPU output ownership before deciding whether
            // the persistent draw buffer can be reused. A resident slice may now stay
            // physically pinned until frame retirement, so replacement of the whole
            // area buffer is an acceptable conservative fallback; stale ownership is not.
            area.repositionForReuse(0, y, z);
            require(area.getGpuTerrainOutputResidency(0, y, z, TerrainRenderType.CUTOUT) == null,
                    "ChunkArea reposition must discard old-coordinate GPU output ownership");
            var reused = requireReservation(area.reserveGpuTerrainOutput(
                            0, y, z, TerrainRenderType.SOLID, 40L, 4),
                    "Repositioned ChunkArea must accept fresh GPU terrain ownership");
            var reusedTarget = area.getGpuTerrainOutputTarget(reused);
            require(reusedTarget != null && reusedTarget.bufferId() != 0L,
                    "Repositioned ChunkArea must expose a valid fresh terrain target");
            require(area.publishGpuTerrainOutput(reused, 4, false),
                    "Repositioned ChunkArea GPU terrain output must publish");

            area.releaseBuffers();
            require(area.getGpuTerrainOutputResidency(0, y, z, TerrainRenderType.SOLID) == null,
                    "ChunkArea release must discard GPU terrain output ownership");
        } finally {
            area.releaseBuffers();
        }
    }

    private static void runRetirements(ArrayDeque<Runnable> retirements) {
        while(!retirements.isEmpty())
            retirements.removeFirst().run();
    }

    private static GpuTerrainOutputStore.StagedReservation requireStagedReservation(
            GpuTerrainOutputStore.StagedReservation reservation, String message) {
        if(reservation == null)
            throw new AssertionError(message);
        return reservation;
    }

    private static GpuTerrainOutputStore.Reservation requireReservation(
            GpuTerrainOutputStore.Reservation reservation, String message) {
        if(reservation == null)
            throw new AssertionError(message);
        return reservation;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
