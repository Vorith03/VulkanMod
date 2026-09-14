package net.vulkanmod.render.chunk.voxel;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CPU-resolved prototype for the numeric lighting input needed by canonical cube
 * faces. This is deliberately not part of {@link SectionVoxelSnapshot}: startup
 * smoke uses it to measure and compare candidate layouts before an ABI decision.
 */
final class CanonicalCubeLightingLattice {
    static final int RECTANGULAR_SAMPLE_COUNT = 20 * 20 * 20;
    static final int SPARSE_SAMPLE_COUNT = 18 * 18 * 18 + 6 * 18 * 18;
    static final int MAX_DEMANDED_SAMPLE_COUNT = 18 * 18 * 18 + 6 * (18 * 18 - 4);
    private static final int CORE_WIDTH = 18;
    private static final int CORE_SAMPLE_COUNT = CORE_WIDTH * CORE_WIDTH * CORE_WIDTH;

    enum Layout {
        RECTANGULAR_20,
        CORE_18_WITH_SECOND_SHELL
    }

    private final Layout layout;
    private final int[] packedLight;
    private final int[] shadeBrightnessBits;
    private final int[] lightPassesWords;
    private final int[] directionalShadeBits;
    private final long captureNanos;

    private CanonicalCubeLightingLattice(Layout layout, int[] packedLight,
                                         int[] shadeBrightnessBits,
                                         int[] lightPassesWords,
                                         int[] directionalShadeBits,
                                         long captureNanos) {
        this.layout = layout;
        this.packedLight = packedLight;
        this.shadeBrightnessBits = shadeBrightnessBits;
        this.lightPassesWords = lightPassesWords;
        this.directionalShadeBits = directionalShadeBits;
        this.captureNanos = captureNanos;
    }

    static CanonicalCubeLightingLattice capture(BlockAndTintGetter level,
                                                 BlockPos sectionOrigin,
                                                 Layout layout) {
        if(level == null || sectionOrigin == null || layout == null)
            throw new IllegalArgumentException("Lighting lattice inputs must be present");
        if(((sectionOrigin.getX() | sectionOrigin.getY() | sectionOrigin.getZ()) & 15) != 0)
            throw new IllegalArgumentException("Lighting lattice origin must be section-aligned");

        long start = System.nanoTime();
        int sampleCount = sampleCount(layout);
        int[] packedLight = new int[sampleCount];
        int[] shadeBrightnessBits = new int[sampleCount];
        int[] lightPassesWords = new int[(sampleCount + 31) >>> 5];
        int[] directionalShadeBits = new int[Direction.values().length];
        for(Direction direction : Direction.values()) {
            directionalShadeBits[direction.ordinal()] = Float.floatToRawIntBits(
                    level.getShade(direction, true));
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for(int z = -2; z <= 17; ++z) {
            for(int y = -2; y <= 17; ++y) {
                for(int x = -2; x <= 17; ++x) {
                    int index = index(layout, x, y, z);
                    if(index < 0)
                        continue;
                    pos.set(sectionOrigin.getX() + x, sectionOrigin.getY() + y,
                            sectionOrigin.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    packedLight[index] = LevelRenderer.getLightColor(level, state, pos);
                    shadeBrightnessBits[index] = Float.floatToRawIntBits(
                            state.getShadeBrightness(level, pos));
                    boolean lightPasses = !state.isViewBlocking(level, pos)
                            || state.getLightBlock(level, pos) == 0;
                    if(lightPasses)
                        lightPassesWords[index >>> 5] |= 1 << (index & 31);
                }
            }
        }
        return new CanonicalCubeLightingLattice(layout, packedLight,
                shadeBrightnessBits, lightPassesWords, directionalShadeBits,
                System.nanoTime() - start);
    }

    int packedLight(int x, int y, int z) {
        return packedLight[requiredIndex(x, y, z)];
    }

    int shadeBrightnessBits(int x, int y, int z) {
        return shadeBrightnessBits[requiredIndex(x, y, z)];
    }

    boolean lightPasses(int x, int y, int z) {
        int index = requiredIndex(x, y, z);
        return ((lightPassesWords[index >>> 5] >>> (index & 31)) & 1) != 0;
    }

    int directionalShadeBits(Direction direction) {
        return directionalShadeBits[direction.ordinal()];
    }

    int sampleCount() {
        return packedLight.length;
    }

    int payloadBytes() {
        return (packedLight.length + shadeBrightnessBits.length
                + lightPassesWords.length + directionalShadeBits.length) * Integer.BYTES;
    }

    long captureNanos() {
        return captureNanos;
    }

    private int requiredIndex(int x, int y, int z) {
        int index = index(layout, x, y, z);
        if(index < 0)
            throw new IllegalArgumentException("Coordinate is outside the lighting layout");
        return index;
    }

    private static int sampleCount(Layout layout) {
        return layout == Layout.RECTANGULAR_20
                ? RECTANGULAR_SAMPLE_COUNT : SPARSE_SAMPLE_COUNT;
    }

    static int index(Layout layout, int x, int y, int z) {
        if(x < -2 || x > 17 || y < -2 || y > 17 || z < -2 || z > 17)
            return -1;
        if(layout == Layout.RECTANGULAR_20)
            return (x + 2) + 20 * ((y + 2) + 20 * (z + 2));

        boolean xCore = x >= -1 && x <= 16;
        boolean yCore = y >= -1 && y <= 16;
        boolean zCore = z >= -1 && z <= 16;
        if(xCore && yCore && zCore)
            return (x + 1) + CORE_WIDTH * ((y + 1) + CORE_WIDTH * (z + 1));

        int outerAxes = (xCore ? 0 : 1) + (yCore ? 0 : 1) + (zCore ? 0 : 1);
        if(outerAxes != 1)
            return -1;
        int slab;
        int first;
        int second;
        if(!xCore) {
            slab = x == -2 ? 0 : 1;
            first = y + 1;
            second = z + 1;
        } else if(!yCore) {
            slab = y == -2 ? 2 : 3;
            first = x + 1;
            second = z + 1;
        } else {
            slab = z == -2 ? 4 : 5;
            first = x + 1;
            second = y + 1;
        }
        return CORE_SAMPLE_COUNT + slab * CORE_WIDTH * CORE_WIDTH
                + first + CORE_WIDTH * second;
    }
}
