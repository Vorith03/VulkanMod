package net.vulkanmod.render.chunk.voxel;

/**
 * Conservative CPU-side ownership planner for future mixed GPU/CPU terrain sections.
 *
 * <p>This planner does not change the serialized voxel ABI or production rendering.
 * It derives a bounded 4096-bit candidate mask from the already-captured v4 snapshot
 * plus one worker-owned fact that v4 does not encode: whether each block has visible
 * CPU block-model geometry. A candidate may leave CPU tessellation only when the
 * existing full-cube model contract is satisfied and its local neighborhood does not
 * require unproven Forge face semantics.</p>
 *
 * <p>The first hybrid contract deliberately excludes section-boundary cells. The v4
 * halo stores only SOLID_RENDER outside the section, which is insufficient to prove
 * whether an adjacent modded/resource-pack model is a compatible GPU full cube or a
 * CPU exception. Interior candidates are also rejected when touching a visible CPU
 * exception, fluid, or block entity. Adjacent qualified full cubes remain compatible
 * even when one of them is conservatively retained on CPU for another reason.</p>
 */
public final class GpuTerrainHybridMask {
    private static final int WORD_BITS = Long.SIZE;
    private static final int WORD_COUNT = SectionVoxelSnapshot.BLOCK_COUNT / WORD_BITS;

    private GpuTerrainHybridMask() {}

    public static Plan plan(SectionVoxelSnapshot snapshot, boolean[] visibleCpuBlockModel) {
        if(snapshot == null)
            throw new IllegalArgumentException("Hybrid terrain planning requires a voxel snapshot");
        if(visibleCpuBlockModel == null
                || visibleCpuBlockModel.length != SectionVoxelSnapshot.BLOCK_COUNT)
            throw new IllegalArgumentException("Hybrid terrain planning requires 4096 CPU visibility flags");

        long[] owned = new long[WORD_COUNT];
        int qualified = 0;
        int ownedCount = 0;
        int boundaryDemotions = 0;
        int exceptionDemotions = 0;

        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            if(!qualifiedFullCube(snapshot, index))
                continue;
            qualified++;

            int x = index & 15;
            int y = (index >>> 4) & 15;
            int z = (index >>> 8) & 15;
            if(x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
                boundaryDemotions++;
                continue;
            }

            if(unsafeNeighbor(snapshot, visibleCpuBlockModel, index - 1)
                    || unsafeNeighbor(snapshot, visibleCpuBlockModel, index + 1)
                    || unsafeNeighbor(snapshot, visibleCpuBlockModel, index - 16)
                    || unsafeNeighbor(snapshot, visibleCpuBlockModel, index + 16)
                    || unsafeNeighbor(snapshot, visibleCpuBlockModel, index - 256)
                    || unsafeNeighbor(snapshot, visibleCpuBlockModel, index + 256)) {
                exceptionDemotions++;
                continue;
            }

            owned[index >>> 6] |= 1L << (index & 63);
            ownedCount++;
        }

        return new Plan(snapshot, owned, qualified, ownedCount,
                boundaryDemotions, exceptionDemotions);
    }

    private static boolean unsafeNeighbor(SectionVoxelSnapshot snapshot,
                                          boolean[] visibleCpuBlockModel,
                                          int index) {
        int flags = snapshot.flags(index);
        if((flags & (SectionVoxelSnapshot.HAS_FLUID | SectionVoxelSnapshot.HAS_BLOCK_ENTITY)) != 0)
            return true;
        return visibleCpuBlockModel[index] && !qualifiedFullCube(snapshot, index);
    }

    private static boolean qualifiedFullCube(SectionVoxelSnapshot snapshot, int index) {
        int flags = snapshot.flags(index);
        return (flags & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0
                && (flags & (SectionVoxelSnapshot.HAS_FLUID
                | SectionVoxelSnapshot.HAS_BLOCK_ENTITY)) == 0;
    }

    public static final class Plan {
        private final SectionVoxelSnapshot source;
        private final long[] owned;
        private final int qualifiedCount;
        private final int ownedCount;
        private final int boundaryDemotions;
        private final int exceptionDemotions;

        private Plan(SectionVoxelSnapshot source, long[] owned,
                     int qualifiedCount, int ownedCount,
                     int boundaryDemotions, int exceptionDemotions) {
            this.source = source;
            this.owned = owned;
            this.qualifiedCount = qualifiedCount;
            this.ownedCount = ownedCount;
            this.boundaryDemotions = boundaryDemotions;
            this.exceptionDemotions = exceptionDemotions;
        }

        public boolean owns(int index) {
            if(index < 0 || index >= SectionVoxelSnapshot.BLOCK_COUNT)
                throw new IllegalArgumentException("Hybrid terrain index is outside its section");
            return ((owned[index >>> 6] >>> (index & 63)) & 1L) != 0L;
        }

        /**
         * Produce the exact v4 payload that the existing GPU mesher can consume for
         * this hybrid subset. All state IDs, semantic flags and boundary halo values
         * are retained; only GPU_FULL_CUBE is cleared for CPU-owned cells. The shader
         * therefore needs no ABI or descriptor change to emit exactly this subset.
         */
        public SectionVoxelSnapshot filteredSnapshot() {
            SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(
                    source.x(), source.y(), source.z());
            for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
                int flags = source.flags(index);
                if(!owns(index))
                    flags &= ~SectionVoxelSnapshot.GPU_FULL_CUBE;
                builder.add(source.stateId(index), flags);

                int x = index & 15;
                int y = (index >>> 4) & 15;
                int z = (index >>> 8) & 15;
                if(y == 0)
                    builder.setBoundaryNeighborSolidRender(index, 0,
                            source.boundaryNeighborSolidRender(index, 0));
                if(y == 15)
                    builder.setBoundaryNeighborSolidRender(index, 1,
                            source.boundaryNeighborSolidRender(index, 1));
                if(z == 0)
                    builder.setBoundaryNeighborSolidRender(index, 2,
                            source.boundaryNeighborSolidRender(index, 2));
                if(z == 15)
                    builder.setBoundaryNeighborSolidRender(index, 3,
                            source.boundaryNeighborSolidRender(index, 3));
                if(x == 0)
                    builder.setBoundaryNeighborSolidRender(index, 4,
                            source.boundaryNeighborSolidRender(index, 4));
                if(x == 15)
                    builder.setBoundaryNeighborSolidRender(index, 5,
                            source.boundaryNeighborSolidRender(index, 5));
            }
            return builder.finish();
        }

        public int qualifiedCount() {
            return qualifiedCount;
        }

        public int ownedCount() {
            return ownedCount;
        }

        public int boundaryDemotions() {
            return boundaryDemotions;
        }

        public int exceptionDemotions() {
            return exceptionDemotions;
        }
    }
}
