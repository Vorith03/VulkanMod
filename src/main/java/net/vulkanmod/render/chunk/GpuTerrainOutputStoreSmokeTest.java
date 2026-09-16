package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Vulkan;

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

            // A newer section generation immediately revokes the older result even
            // when its replacement later fails, leaving the CPU mesh authoritative.
            var newer = requireReservation(store.reserve(7, TerrainRenderType.SOLID, 11L, 16),
                    "Newer-generation GPU terrain reservation must fit");
            require(!store.getResidency(7, TerrainRenderType.SOLID).valid(),
                    "Generation turnover must revoke stale GPU terrain output immediately");
            require(!store.publish(newer, 0, false),
                    "Zero-face GPU terrain output must remain on CPU fallback");
            require(!store.getResidency(7, TerrainRenderType.SOLID).valid(),
                    "Failed newer generation must not resurrect stale GPU terrain output");
            require(store.reserve(7, TerrainRenderType.SOLID, 10L, 8) == null,
                    "Stale GPU terrain generation must be rejected");

            var finalResult = requireReservation(store.reserve(7, TerrainRenderType.CUTOUT, 12L, 8),
                    "Independent supported terrain layer reservation must fit");
            require(store.publish(finalResult, 8, false),
                    "Supported cutout GPU terrain output must publish");
            store.invalidateSection(7, 12L);
            require(!store.getResidency(7, TerrainRenderType.CUTOUT).valid(),
                    "Section invalidation must revoke every terrain-layer output");
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

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_OUTPUT_RESIDENCY_OK: storage-capable area vertices, no-growth reservation, generation revocation, same-generation retry fallback, translucent/tripwire CPU fallback");
        } finally {
            Vulkan.waitIdle();
            if(store != null)
                store.close();
            drawBuffers.releaseBuffers();
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
