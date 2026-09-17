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

        SectionVoxelSnapshot isolatedCube = fixture(qualifiedState, -1, 0, false, 0, false);
        require(GpuTerrainSectionMesherBridge.fullyQualified(isolatedCube),
                "Qualified cubes plus invisible air must be bridge-safe");
        GpuTerrainSectionMesherBridge.Qualification isolatedPlan =
                GpuTerrainSectionMesherBridge.qualify(isolatedCube);
        require(isolatedPlan != null && isolatedPlan.faceCount() == 6,
                "Input-only qualification must size an isolated cube as six faces");

        RenderSection.GpuTerrainPreflight publicPlan = RenderSection.qualifyGpuTerrain(isolatedCube);
        require(publicPlan != null
                        && publicPlan.modelGeneration() == isolatedPlan.modelGeneration()
                        && publicPlan.faceCount() == isolatedPlan.faceCount(),
                "Worker-facing preflight must preserve the bridge qualification plan");

        RenderSection stagedSection = new RenderSection(0, 0, 64, 0);
        long generation = stagedSection.getVoxelGeneration();
        stagedSection.stageGpuTerrainPreflight(publicPlan, generation);
        require(stagedSection.matchesStagedGpuTerrainPreflight(generation,
                        publicPlan.modelGeneration(), publicPlan.faceCount()),
                "Same-generation worker preflight must become eligible for bridge dispatch");

        stagedSection.stageGpuTerrainPreflight(
                new RenderSection.GpuTerrainPreflight(
                        publicPlan.modelGeneration(), publicPlan.faceCount() + 1),
                generation + 1);
        require(stagedSection.matchesStagedGpuTerrainPreflight(generation,
                        publicPlan.modelGeneration(), publicPlan.faceCount()),
                "Stale/future generation preflight must not replace current ownership");

        stagedSection.stageGpuTerrainPreflight(null, generation);
        require(!stagedSection.matchesStagedGpuTerrainPreflight(generation,
                        publicPlan.modelGeneration(), publicPlan.faceCount()),
                "Same-generation fallback must clear staged GPU ownership");

        SectionVoxelSnapshot boundaryOccludedCube = fixture(
                qualifiedState, -1, 0, false, 0, true);
        GpuTerrainSectionMesherBridge.Qualification boundaryPlan =
                GpuTerrainSectionMesherBridge.qualify(boundaryOccludedCube);
        require(boundaryPlan != null && boundaryPlan.faceCount() == 3,
                "Input-only qualification must mirror boundary-halo face rejection");

        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, unsupportedVisibleState, false, 0, false)),
                "Visible unsupported block geometry must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1,
                                Block.getId(Blocks.WATER.defaultBlockState()), false, 0, false)),
                "Fluid geometry must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, qualifiedState, true,
                                SectionVoxelSnapshot.HAS_FLUID, false)),
                "A GPU-qualified waterlogged/full-cube state must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, qualifiedState, true,
                                SectionVoxelSnapshot.HAS_BLOCK_ENTITY, false)),
                "A GPU-qualified block-entity-backed full cube must retain CPU fallback");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 0, qualifiedState, false, 0, false)),
                "A visible qualified state without its captured GPU_FULL_CUBE bit must fail closed");
        require(!GpuTerrainSectionMesherBridge.fullyQualified(
                        fixture(qualifiedState, 1, unsupportedVisibleState, true, 0, false)),
                "A stale or forged GPU_FULL_CUBE bit must be rejected by current model qualification");

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_BRIDGE_POLICY_OK: input-only face planning mirrors compute culling; staged generation ownership is fail-closed; unsupported visible, fluid, block-entity, waterlogged/full-cube, missing-bit and stale/forged qualification cases retain CPU fallback");
    }

    private static SectionVoxelSnapshot fixture(int qualifiedState,
                                                int replacementIndex,
                                                int replacementState,
                                                boolean replacementGpuFlag,
                                                int replacementExtraFlags,
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
                flags = SectionVoxelSnapshot.CPU_REQUIRED | replacementExtraFlags
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
