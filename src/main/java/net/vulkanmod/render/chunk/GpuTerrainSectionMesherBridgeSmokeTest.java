package net.vulkanmod.render.chunk;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelTable;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;

/** Host-side fail-closed oracle for the production section-mesher bridge policy. */
public final class GpuTerrainSectionMesherBridgeSmokeTest {
    private GpuTerrainSectionMesherBridgeSmokeTest() {}

    public static void verify() {
        GpuTerrainModelTable table = GpuTerrainModelTable.captureCurrent();
        require(table.templateCount() > 0,
                "GPU terrain bridge smoke requires a qualified baked model");
        int qualifiedState = table.stateIdForTemplate(0);
        int unsupportedVisibleState = Block.getId(Blocks.OAK_SLAB.defaultBlockState());

        SectionVoxelSnapshot isolatedCube = fixture(qualifiedState, -1, 0, false, false);
        require(GpuTerrainSectionMesherBridge.fullyQualified(isolatedCube),
                "Qualified cubes plus invisible air must be bridge-safe");
        GpuTerrainSectionMesherBridge.Qualification isolatedPlan =
                GpuTerrainSectionMesherBridge.qualify(isolatedCube);
        require(isolatedPlan != null && isolatedPlan.faceCount() == 6,
                "Input-only qualification must size an isolated cube as six faces");

        SectionVoxelSnapshot boundaryOccludedCube = fixture(
                qualifiedState, -1, 0, false, true);
        GpuTerrainSectionMesherBridge.Qualification boundaryPlan =
                GpuTerrainSectionMesherBridge.qualify(boundaryOccludedCube);
        require(boundaryPlan != null && boundaryPlan.faceCount() == 3,
                "Input-only qualification must mirror boundary-halo face rejection");

        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, unsupportedVisibleState, false, false)),
                "Visible unsupported block geometry must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1,
                                Block.getId(Blocks.WATER.defaultBlockState()), false, false)),
                "Fluid geometry must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 0, qualifiedState, false, false)),
                "A visible qualified state without its captured GPU_FULL_CUBE bit must fail closed");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, unsupportedVisibleState, true, false)),
                "A stale or forged GPU_FULL_CUBE bit must be rejected by current model qualification");

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_BRIDGE_POLICY_OK: input-only face planning mirrors compute culling; unsupported visible, fluid, missing-bit and stale/forged qualification cases retain CPU fallback");
    }

    private static SectionVoxelSnapshot fixture(int qualifiedState,
                                                int replacementIndex,
                                                int replacementState,
                                                boolean replacementGpuFlag,
                                                boolean occludeCubeBoundary) {
        int airState = Block.getId(Blocks.AIR.defaultBlockState());
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 64, 0);
        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            int stateId = airState;
            int flags = SectionVoxelSnapshot.CPU_REQUIRED;
            if(index == 0) {
                stateId = qualifiedState;
                flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
            }
            if(index == replacementIndex) {
                stateId = replacementState;
                flags = SectionVoxelSnapshot.CPU_REQUIRED
                        | (replacementGpuFlag ? SectionVoxelSnapshot.GPU_FULL_CUBE : 0);
            }
            builder.add(stateId, flags);
        }
        if(occludeCubeBoundary) {
            builder.setBoundaryNeighborSolidRender(0, 0, true);
            builder.setBoundaryNeighborSolidRender(0, 2, true);
            builder.setBoundaryNeighborSolidRender(0, 4, true);
        }
        return builder.finish();
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
