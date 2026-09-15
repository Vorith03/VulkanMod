package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;

/**
 * Exact demanded-sample map for the canonical full-cube lighting subset.
 *
 * <p>The map is derived only from the immutable section voxel snapshot and its
 * conservative solid-render halo. It contains no lighting values. Production
 * lighting capture may use it to resolve only samples that surviving canonical
 * faces can read, while dense sections remain eligible for an explicit CPU
 * fallback.</p>
 */
final class GpuLightingDemandMap {
    static final int DOMAIN_WIDTH = 20;
    static final int SAMPLE_COUNT = CanonicalCubeLightingLattice.RECTANGULAR_SAMPLE_COUNT;
    static final int SAMPLE_WORDS = SAMPLE_COUNT / 32;

    private final int[] sampleWords;
    private final int qualifiedVoxels;
    private final int candidateFaces;
    private final int uniqueSamples;

    private GpuLightingDemandMap(int[] sampleWords, int qualifiedVoxels,
                                 int candidateFaces, int uniqueSamples) {
        this.sampleWords = sampleWords;
        this.qualifiedVoxels = qualifiedVoxels;
        this.candidateFaces = candidateFaces;
        this.uniqueSamples = uniqueSamples;
    }

    static GpuLightingDemandMap analyze(SectionVoxelSnapshot snapshot) {
        if(snapshot == null)
            throw new IllegalArgumentException("Snapshot must be present");

        int[] samples = new int[SAMPLE_WORDS];
        int qualifiedVoxels = 0;
        int candidateFaces = 0;

        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            if(!hasFlag(snapshot, index, SectionVoxelSnapshot.GPU_FULL_CUBE))
                continue;
            ++qualifiedVoxels;

            int faceMask = candidateFaceMask(snapshot, index);
            int x = index & 15;
            int y = (index >>> 4) & 15;
            int z = (index >>> 8) & 15;
            for(Direction face : Direction.values()) {
                if((faceMask & (1 << face.ordinal())) == 0)
                    continue;
                ++candidateFaces;
                markFaceSamples(samples, x, y, z, face);
            }
        }

        int uniqueSamples = 0;
        for(int word : samples)
            uniqueSamples += Integer.bitCount(word);
        return new GpuLightingDemandMap(samples, qualifiedVoxels,
                candidateFaces, uniqueSamples);
    }

    int qualifiedVoxels() { return this.qualifiedVoxels; }
    int candidateFaces() { return this.candidateFaces; }
    int uniqueSamples() { return this.uniqueSamples; }
    int word(int index) { return this.sampleWords[index]; }

    boolean demanded(int latticeIndex) {
        if(latticeIndex < 0 || latticeIndex >= SAMPLE_COUNT)
            throw new IndexOutOfBoundsException("Lighting lattice index " + latticeIndex);
        return (this.sampleWords[latticeIndex >>> 5] & (1 << (latticeIndex & 31))) != 0;
    }

    int[] copyWords() {
        return this.sampleWords.clone();
    }

    private static int candidateFaceMask(SectionVoxelSnapshot snapshot, int index) {
        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = (index >>> 8) & 15;
        int mask = 0;
        if(y == 0 ? !snapshot.boundaryNeighborSolidRender(index, 0)
                : !hasFlag(snapshot, index - 16, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 1;
        if(y == 15 ? !snapshot.boundaryNeighborSolidRender(index, 1)
                : !hasFlag(snapshot, index + 16, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 2;
        if(z == 0 ? !snapshot.boundaryNeighborSolidRender(index, 2)
                : !hasFlag(snapshot, index - 256, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 4;
        if(z == 15 ? !snapshot.boundaryNeighborSolidRender(index, 3)
                : !hasFlag(snapshot, index + 256, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 8;
        if(x == 0 ? !snapshot.boundaryNeighborSolidRender(index, 4)
                : !hasFlag(snapshot, index - 1, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 16;
        if(x == 15 ? !snapshot.boundaryNeighborSolidRender(index, 5)
                : !hasFlag(snapshot, index + 1, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 32;
        return mask;
    }

    private static void markFaceSamples(int[] samples,
                                        int x, int y, int z, Direction face) {
        Direction[] tangents = tangents(face);
        int centerX = x + face.getStepX();
        int centerY = y + face.getStepY();
        int centerZ = z + face.getStepZ();
        mark(samples, centerX, centerY, centerZ);
        for(Direction tangent : tangents) {
            int sideX = centerX + tangent.getStepX();
            int sideY = centerY + tangent.getStepY();
            int sideZ = centerZ + tangent.getStepZ();
            mark(samples, sideX, sideY, sideZ);
            mark(samples, sideX + face.getStepX(),
                    sideY + face.getStepY(), sideZ + face.getStepZ());
        }
        int[][] diagonals = { { 0, 2 }, { 0, 3 }, { 1, 2 }, { 1, 3 } };
        for(int[] diagonal : diagonals) {
            Direction first = tangents[diagonal[0]];
            Direction second = tangents[diagonal[1]];
            mark(samples,
                    centerX + first.getStepX() + second.getStepX(),
                    centerY + first.getStepY() + second.getStepY(),
                    centerZ + first.getStepZ() + second.getStepZ());
        }
    }

    private static void mark(int[] samples, int x, int y, int z) {
        int index = CanonicalCubeLightingLattice.index(
                CanonicalCubeLightingLattice.Layout.RECTANGULAR_20, x, y, z);
        if(index < 0)
            throw new AssertionError("Canonical lighting demand escaped the 20-cube");
        samples[index >>> 5] |= 1 << (index & 31);
    }

    private static Direction[] tangents(Direction face) {
        return switch(face) {
            case DOWN -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.NORTH, Direction.SOUTH };
            case UP -> new Direction[] { Direction.EAST, Direction.WEST,
                    Direction.NORTH, Direction.SOUTH };
            case NORTH -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.EAST, Direction.WEST };
            case SOUTH -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.DOWN, Direction.UP };
            case WEST -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.NORTH, Direction.SOUTH };
            case EAST -> new Direction[] { Direction.DOWN, Direction.UP,
                    Direction.NORTH, Direction.SOUTH };
        };
    }

    private static boolean hasFlag(SectionVoxelSnapshot snapshot, int index, int flag) {
        return (snapshot.flags(index) & flag) != 0;
    }
}
