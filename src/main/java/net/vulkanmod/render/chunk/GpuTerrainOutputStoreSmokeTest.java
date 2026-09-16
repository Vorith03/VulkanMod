package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Vulkan;
import org.joml.Vector3i;

/** CI-only lifecycle oracle for non-consuming GPU terrain output ownership. */
public final class GpuTerrainOutputStoreSmokeTest {
    private GpuTerrainOutputStoreSmokeTest() {}

    public static void verify() {
        DrawBuffers drawBuffers = new DrawBuffers();
        GpuTerrainOutputStore store = null;
        try {
            drawBuffers.allocateBuffers();
            int initialCapacity = drawBuffers.vertexBuffer.getCapacityBytes();
            store = new GpuTerrainOutputStore(drawBuffers);

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

            // Prove allocation pressure is bounded: one maximum section fits in the
            // initial area buffer, while a second one fails instead of growing it.
            var maximum = requireReservation(store.reserve(8, TerrainRenderType.SOLID, 20L,
                            GpuTerrainOutputStore.MAX_FACES),
                    "One maximum bounded GPU terrain output must fit the initial area buffer");
            require(store.target(maximum) != null,
                    "Maximum bounded GPU terrain reservation must resolve a target");
            require(store.reserve(9, TerrainRenderType.SOLID, 20L,
                            GpuTerrainOutputStore.MAX_FACES) == null,
                    "GPU terrain allocation pressure must fail closed without growth");
            require(drawBuffers.vertexBuffer.getCapacityBytes() == initialCapacity,
                    "GPU terrain pressure must never grow the area vertex buffer");
            store.invalidateSection(8, 21L);

            verifyChunkAreaLifecycle();

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_OUTPUT_RESIDENCY_OK: storage-capable area vertices, no-growth reservation, section-global generation revocation, ChunkArea teardown/reuse, same-generation retry fallback, translucent/tripwire CPU fallback");
        } finally {
            Vulkan.waitIdle();
            if(store != null)
                store.close();
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
            long reusableBuffer = currentTarget.bufferId();

            // Coarse-area reuse must close the output owner before hasLiveGeometry()
            // decides whether persistent draw buffers can be retained. With no CPU
            // geometry in this smoke, the same physical vertex buffer should survive.
            area.repositionForReuse(0, y, z);
            require(area.getGpuTerrainOutputResidency(0, y, z, TerrainRenderType.CUTOUT) == null,
                    "ChunkArea reposition must discard old-coordinate GPU output ownership");
            var reused = requireReservation(area.reserveGpuTerrainOutput(
                            0, y, z, TerrainRenderType.SOLID, 40L, 4),
                    "Repositioned ChunkArea must accept fresh GPU terrain ownership");
            var reusedTarget = area.getGpuTerrainOutputTarget(reused);
            require(reusedTarget != null && reusedTarget.bufferId() == reusableBuffer,
                    "GPU-only output must not prevent safe persistent area-buffer reuse");
            require(area.publishGpuTerrainOutput(reused, 4, false),
                    "Repositioned ChunkArea GPU terrain output must publish");

            area.releaseBuffers();
            require(area.getGpuTerrainOutputResidency(0, y, z, TerrainRenderType.SOLID) == null,
                    "ChunkArea release must discard GPU terrain output ownership");
        } finally {
            area.releaseBuffers();
        }
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
