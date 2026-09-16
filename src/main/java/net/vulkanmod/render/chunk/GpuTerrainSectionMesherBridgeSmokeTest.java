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

        require(GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, -1, 0, false)),
                "Qualified cubes plus invisible air must be bridge-safe");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, unsupportedVisibleState, false)),
                "Visible unsupported block geometry must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1,
                                Block.getId(Blocks.WATER.defaultBlockState()), false)),
                "Fluid geometry must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 0, qualifiedState, false)),
                "A visible qualified state without its captured GPU_FULL_CUBE bit must fail closed");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, unsupportedVisibleState, true)),
                "A stale or forged GPU_FULL_CUBE bit must be rejected by current model qualification");

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_BRIDGE_POLICY_OK: qualified-only accepted; unsupported visible, fluid, missing-bit and stale/forged qualification cases retain CPU fallback");
    }

    private static SectionVoxelSnapshot fixture(int qualifiedState,
                                                int replacementIndex,
                                                int replacementState,
                                                boolean replacementGpuFlag) {
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
        return builder.finish();
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
