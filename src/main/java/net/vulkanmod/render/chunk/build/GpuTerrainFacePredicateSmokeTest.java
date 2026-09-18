package net.vulkanmod.render.chunk.build;

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
 * CI smoke for the CPU-bypass face-culling qualification boundary.
 *
 * <p>The negative fixture intentionally uses glass next to glass: the shader's
 * SOLID_RENDER-derived predicate would emit the shared face because glass is not
 * solid-rendering, while vanilla suppresses that same-block face through
 * skipRendering. The production helper must therefore reject the cube.</p>
 */
public final class GpuTerrainFacePredicateSmokeTest {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private GpuTerrainFacePredicateSmokeTest() {}

    public static void verify() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();

        require(ChunkTask.BuildTask.hasGpuFacePredicateEquivalence(
                        stone, new UniformNeighborGetter(stone, air), ORIGIN),
                "Stone surrounded by air should match the GPU face predicate");
        require(ChunkTask.BuildTask.hasGpuFacePredicateEquivalence(
                        stone, new UniformNeighborGetter(stone, stone), ORIGIN),
                "Stone surrounded by stone should match the GPU face predicate");

        UniformNeighborGetter glassNeighbors = new UniformNeighborGetter(glass, glass);
        BlockPos neighborPos = ORIGIN.relative(Direction.EAST);
        boolean gpuWouldRender = !glassNeighbors.getBlockState(neighborPos)
                .isSolidRender(glassNeighbors, neighborPos);
        boolean authoritative = Block.shouldRenderFace(
                glass, glassNeighbors, ORIGIN, Direction.EAST, neighborPos);

        require(gpuWouldRender && !authoritative,
                "Glass/glass fixture must exercise a real GPU/vanilla face disagreement");
        require(!ChunkTask.BuildTask.hasGpuFacePredicateEquivalence(
                        glass, glassNeighbors, ORIGIN),
                "Face-predicate disagreement must keep the cube CPU-owned");

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_FACE_PREDICATE_SMOKE_OK: stone controls agree; glass/glass skipRendering disagreement rejected");
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
