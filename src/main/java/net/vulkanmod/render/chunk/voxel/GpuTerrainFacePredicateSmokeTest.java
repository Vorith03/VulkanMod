package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.vulkanmod.Initializer;

import javax.annotation.Nullable;

/**
 * Post-bake smoke for the real Minecraft/Forge face-culling policy consumed by
 * GPU-terrain CPU-omission qualification.
 *
 * <p>Unlike the pure boolean truth table, this deliberately calls
 * {@link Block#shouldRenderFace} against real vanilla states. Glass next to the
 * same glass state is the negative oracle: the neighbor is not SOLID_RENDER, so
 * the compute predicate would emit the face, while vanilla suppresses the shared
 * face through same-block transparency/skipRendering semantics.</p>
 */
public final class GpuTerrainFacePredicateSmokeTest {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private GpuTerrainFacePredicateSmokeTest() {}

    public static void verify() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();

        requireAllDirectionsMatch(stone, air,
                "Stone surrounded by air must agree with the GPU face predicate");
        requireAllDirectionsMatch(stone, stone,
                "Stone surrounded by stone must agree with the GPU face predicate");

        UniformNeighborGetter glassNeighbors = new UniformNeighborGetter(glass, glass);
        for(Direction direction : Direction.values()) {
            BlockPos neighborPos = ORIGIN.relative(direction);
            boolean neighborSolidRender = glass.isSolidRender(glassNeighbors, neighborPos);
            boolean authoritative = Block.shouldRenderFace(
                    glass, glassNeighbors, ORIGIN, direction, neighborPos);

            require(!neighborSolidRender,
                    "Glass negative oracle requires a non-SOLID_RENDER neighbor");
            require(!authoritative,
                    "Vanilla must suppress the shared glass/glass face");
            require(!GpuTerrainFacePredicate.matches(neighborSolidRender, authoritative),
                    "Real glass/glass disagreement must demote GPU ownership");
        }

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_REAL_FACE_ORACLE_OK: stone controls agree; real glass/glass Block.shouldRenderFace disagreement is rejected");
    }

    private static void requireAllDirectionsMatch(BlockState center, BlockState neighbor,
                                                  String message) {
        UniformNeighborGetter getter = new UniformNeighborGetter(center, neighbor);
        for(Direction direction : Direction.values()) {
            BlockPos neighborPos = ORIGIN.relative(direction);
            boolean neighborSolidRender = neighbor.isSolidRender(getter, neighborPos);
            boolean authoritative = Block.shouldRenderFace(
                    center, getter, ORIGIN, direction, neighborPos);
            require(GpuTerrainFacePredicate.matches(neighborSolidRender, authoritative),
                    message + " direction=" + direction);
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class UniformNeighborGetter implements BlockGetter {
        private final BlockState center;
        private final BlockState neighbor;

        private UniformNeighborGetter(BlockState center, BlockState neighbor) {
            this.center = center;
            this.neighbor = neighbor;
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return ORIGIN.equals(pos) ? center : neighbor;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}
