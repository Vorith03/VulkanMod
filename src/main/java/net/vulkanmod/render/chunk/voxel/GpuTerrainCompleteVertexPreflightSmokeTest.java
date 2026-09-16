package net.vulkanmod.render.chunk.voxel;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.Vulkan;

/**
 * Smoke-only discriminator for the post-bake complete-vertex join.
 *
 * <p>It proves the exact Stone voxel slice resolves through the current device-local
 * model table before asking the sparse-lighting probe to consume that same residency.
 * This keeps CI failures localized without changing production terrain semantics.</p>
 */
public final class GpuTerrainCompleteVertexPreflightSmokeTest {
    private GpuTerrainCompleteVertexPreflightSmokeTest() {}

    public static void verify() {
        GpuTerrainModelTable table = GpuTerrainModelTable.captureCurrent();
        int stoneStateId = Block.getId(Blocks.STONE.defaultBlockState());
        int stoneTemplate = table.templateIndexForStateId(stoneStateId);
        require(stoneTemplate >= 0,
                "Stone must be present in the current qualified GPU model table");

        GpuTerrainModelGpuStore modelStore = new GpuTerrainModelGpuStore();
        RegionVoxelGpuStore regionStore = new RegionVoxelGpuStore();
        try {
            require(modelStore.upload(table), "Preflight model-table upload must succeed");
            GpuTerrainModelGpuStore.Residency model = modelStore.getResidency();
            require(model.valid() && model.generation() == table.generation(),
                    "Preflight model residency must match the captured generation");

            var fixture = CanonicalCubeLightingSmokeTest.sparseGpuFixture(0, 0);
            long generation = 9_001L;
            require(regionStore.upload(0, fixture.voxel(), generation),
                    "Preflight Stone voxel upload must succeed");
            require(regionStore.uploadLighting(0, fixture.lighting(), generation),
                    "Preflight Stone lighting upload must succeed");
            AreaUploadManager.INSTANCE.submitUploads();

            RegionVoxelGpuStore.Residency voxel = regionStore.getResidency(0);
            RegionVoxelGpuStore.Residency lighting = regionStore.getLightingResidency(0);
            require(voxel.valid() && lighting.valid()
                            && voxel.generation() == generation
                            && lighting.generation() == generation,
                    "Preflight voxel and lighting residency must publish as one generation");

            int densePlusOne;
            try(GpuTerrainModelComputeProbe probe = new GpuTerrainModelComputeProbe()) {
                int[] decoded = probe.dispatch(model, table.templateCount(),
                        regionStore.getPageBuffer(voxel.pageIndex()),
                        voxel.byteOffset(), voxel.byteLength());
                int base = GpuTerrainModelComputeProbe.voxelResultBase(
                        table.templateCount(), 0);
                densePlusOne = decoded[base];
            }
            require(densePlusOne == stoneTemplate + 1,
                    "Preflight model compute must resolve the resident Stone voxel: expected="
                            + (stoneTemplate + 1) + " actual=" + densePlusOne);

            int errorFlags;
            try(SparseLightingComputeProbe probe = new SparseLightingComputeProbe()) {
                int[] joined = probe.dispatch(
                        regionStore.getPageBuffer(voxel.pageIndex()), voxel,
                        regionStore.getPageBuffer(lighting.pageIndex()), lighting,
                        0, model, table.templateCount());
                errorFlags = joined[5];
            }
            require(errorFlags == 0,
                    "Complete-vertex preflight failed after successful model lookup: errorFlags="
                            + errorFlags + " stoneStateId=" + stoneStateId
                            + " stoneTemplate=" + stoneTemplate
                            + " modelGeneration=" + table.generation());

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_COMPLETE_VERTEX_PREFLIGHT_OK: Stone state {} -> template {} through the resident model decoder and sparse-lighting join",
                    stoneStateId, stoneTemplate);
        } finally {
            Vulkan.waitIdle();
            regionStore.close();
            modelStore.close();
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
