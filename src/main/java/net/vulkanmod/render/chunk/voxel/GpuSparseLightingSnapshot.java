package net.vulkanmod.render.chunk.voxel;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Bounded exact lighting input for the canonical GPU full-cube subset.
 *
 * <p>This is intentionally separate from {@link SectionVoxelSnapshot} v4. A fixed
 * 8,000-point demand bitmap maps canonical AO/light lattice coordinates to compact
 * records through a per-word rank prefix. Sections demanding more than
 * {@link #MAX_SAMPLES} points are ineligible rather than truncated.</p>
 */
public final class GpuSparseLightingSnapshot {
    static final int MAGIC = 0x564b534c; // VKSL
    static final int VERSION = 1;
    static final int HEADER_WORDS = 16;
    static final int DEMAND_WORDS = GpuLightingDemandMap.SAMPLE_WORDS;
    static final int RANK_WORDS = DEMAND_WORDS;
    static final int DIRECTIONAL_SHADE_WORDS = 6;
    public static final int MAX_SAMPLES = 2_000;
    public static final int MAX_BYTES = bytesFor(MAX_SAMPLES);

    private final int x;
    private final int y;
    private final int z;
    private final int[] demandWords;
    private final int[] rankPrefix;
    private final int[] packedLight;
    private final int[] shadeBrightnessBits;
    private final int[] lightPassWords;
    private final int[] directionalShadeBits;

    private GpuSparseLightingSnapshot(int x, int y, int z,
                                      int[] demandWords, int[] rankPrefix,
                                      int[] packedLight, int[] shadeBrightnessBits,
                                      int[] lightPassWords, int[] directionalShadeBits) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.demandWords = demandWords;
        this.rankPrefix = rankPrefix;
        this.packedLight = packedLight;
        this.shadeBrightnessBits = shadeBrightnessBits;
        this.lightPassWords = lightPassWords;
        this.directionalShadeBits = directionalShadeBits;
    }

    /**
     * Capture only exact lighting points demanded by the qualified canonical faces.
     * Returns null when the section exceeds the measured sparse-density cap so the
     * caller can retain the established CPU terrain path.
     */
    @Nullable
    public static GpuSparseLightingSnapshot tryCapture(BlockAndTintGetter level,
                                                        BlockPos sectionOrigin,
                                                        SectionVoxelSnapshot voxels) {
        if(level == null || sectionOrigin == null || voxels == null)
            throw new IllegalArgumentException("Lighting capture inputs must be present");
        if(voxels.x() != sectionOrigin.getX() || voxels.y() != sectionOrigin.getY()
                || voxels.z() != sectionOrigin.getZ())
            throw new IllegalArgumentException("Lighting section origin must match voxel snapshot");

        GpuLightingDemandMap demand = GpuLightingDemandMap.analyze(voxels);
        if(demand.uniqueSamples() > MAX_SAMPLES)
            return null;

        int sampleCount = demand.uniqueSamples();
        int[] packedLight = new int[sampleCount];
        int[] shade = new int[sampleCount];
        int[] lightPass = new int[(sampleCount + 31) >>> 5];
        int[] directional = new int[DIRECTIONAL_SHADE_WORDS];
        int[] rank = buildRankPrefix(demand);

        for(int latticeIndex = 0; latticeIndex < GpuLightingDemandMap.SAMPLE_COUNT; ++latticeIndex) {
            if(!demand.demanded(latticeIndex))
                continue;
            int compact = compactRank(demand, rank, latticeIndex);
            int localX = latticeIndex % GpuLightingDemandMap.DOMAIN_WIDTH - 2;
            int localY = (latticeIndex / GpuLightingDemandMap.DOMAIN_WIDTH)
                    % GpuLightingDemandMap.DOMAIN_WIDTH - 2;
            int localZ = latticeIndex
                    / (GpuLightingDemandMap.DOMAIN_WIDTH * GpuLightingDemandMap.DOMAIN_WIDTH) - 2;
            BlockPos pos = sectionOrigin.offset(localX, localY, localZ);
            BlockState state = level.getBlockState(pos);
            packedLight[compact] = LevelRenderer.getLightColor(level, state, pos);
            shade[compact] = Float.floatToRawIntBits(state.getShadeBrightness(level, pos));
            if(!state.isViewBlocking(level, pos) || state.getLightBlock(level, pos) == 0)
                lightPass[compact >>> 5] |= 1 << (compact & 31);
        }
        for(Direction direction : Direction.values())
            directional[direction.ordinal()] = Float.floatToRawIntBits(level.getShade(direction, true));

        return new GpuSparseLightingSnapshot(voxels.x(), voxels.y(), voxels.z(),
                demand.copyWords(), rank, packedLight, shade, lightPass, directional);
    }

    /** Package-private deterministic seam for the pointer-free ABI regression test. */
    @Nullable
    static GpuSparseLightingSnapshot fromDenseReference(SectionVoxelSnapshot voxels,
                                                         int[] densePackedLight,
                                                         int[] denseShadeBits,
                                                         boolean[] denseLightPasses,
                                                         int[] directionalShadeBits) {
        if(voxels == null || densePackedLight == null || denseShadeBits == null
                || denseLightPasses == null || directionalShadeBits == null
                || densePackedLight.length != GpuLightingDemandMap.SAMPLE_COUNT
                || denseShadeBits.length != GpuLightingDemandMap.SAMPLE_COUNT
                || denseLightPasses.length != GpuLightingDemandMap.SAMPLE_COUNT
                || directionalShadeBits.length != DIRECTIONAL_SHADE_WORDS)
            throw new IllegalArgumentException("Dense lighting oracle shape mismatch");

        GpuLightingDemandMap demand = GpuLightingDemandMap.analyze(voxels);
        if(demand.uniqueSamples() > MAX_SAMPLES)
            return null;

        int[] rank = buildRankPrefix(demand);
        int[] packedLight = new int[demand.uniqueSamples()];
        int[] shade = new int[demand.uniqueSamples()];
        int[] lightPass = new int[(demand.uniqueSamples() + 31) >>> 5];
        for(int latticeIndex = 0; latticeIndex < GpuLightingDemandMap.SAMPLE_COUNT; ++latticeIndex) {
            if(!demand.demanded(latticeIndex))
                continue;
            int compact = compactRank(demand, rank, latticeIndex);
            packedLight[compact] = densePackedLight[latticeIndex];
            shade[compact] = denseShadeBits[latticeIndex];
            if(denseLightPasses[latticeIndex])
                lightPass[compact >>> 5] |= 1 << (compact & 31);
        }
        return new GpuSparseLightingSnapshot(voxels.x(), voxels.y(), voxels.z(),
                demand.copyWords(), rank, packedLight, shade, lightPass,
                directionalShadeBits.clone());
    }

    public int x() { return this.x; }
    public int y() { return this.y; }
    public int z() { return this.z; }
    public int sampleCount() { return this.packedLight.length; }
    public int byteSize() { return bytesFor(this.sampleCount()); }

    public boolean demanded(int localX, int localY, int localZ) {
        int latticeIndex = CanonicalCubeLightingLattice.index(
                CanonicalCubeLightingLattice.Layout.RECTANGULAR_20,
                localX, localY, localZ);
        return latticeIndex >= 0 && (this.demandWords[latticeIndex >>> 5]
                & (1 << (latticeIndex & 31))) != 0;
    }

    public int compactRank(int localX, int localY, int localZ) {
        int latticeIndex = CanonicalCubeLightingLattice.index(
                CanonicalCubeLightingLattice.Layout.RECTANGULAR_20,
                localX, localY, localZ);
        if(latticeIndex < 0 || (this.demandWords[latticeIndex >>> 5]
                & (1 << (latticeIndex & 31))) == 0)
            return -1;
        int wordIndex = latticeIndex >>> 5;
        int bit = latticeIndex & 31;
        int before = bit == 0 ? 0 : this.demandWords[wordIndex] & ((1 << bit) - 1);
        return this.rankPrefix[wordIndex] + Integer.bitCount(before);
    }

    public int packedLight(int localX, int localY, int localZ) {
        int rank = requireRank(localX, localY, localZ);
        return this.packedLight[rank];
    }

    public int shadeBrightnessBits(int localX, int localY, int localZ) {
        int rank = requireRank(localX, localY, localZ);
        return this.shadeBrightnessBits[rank];
    }

    public boolean lightPasses(int localX, int localY, int localZ) {
        int rank = requireRank(localX, localY, localZ);
        return (this.lightPassWords[rank >>> 5] & (1 << (rank & 31))) != 0;
    }

    public int directionalShadeBits(Direction direction) {
        if(direction == null)
            throw new IllegalArgumentException("Direction must be present");
        return this.directionalShadeBits[direction.ordinal()];
    }

    public void writeTo(ByteBuffer target) {
        if(target == null || target.isReadOnly() || target.remaining() < this.byteSize()
                || (target.position() & 3) != 0)
            throw new IllegalArgumentException("Writable, aligned sparse lighting capacity required");

        int sampleCount = this.sampleCount();
        int predicateWords = this.lightPassWords.length;
        int demandOffset = HEADER_WORDS;
        int rankOffset = demandOffset + DEMAND_WORDS;
        int lightOffset = rankOffset + RANK_WORDS;
        int shadeOffset = lightOffset + sampleCount;
        int predicateOffset = shadeOffset + sampleCount;
        int directionalOffset = predicateOffset + predicateWords;

        int start = target.position();
        ByteBuffer out = target.slice().order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MAGIC);
        out.putInt(VERSION);
        out.putInt(this.x);
        out.putInt(this.y);
        out.putInt(this.z);
        out.putInt(sampleCount);
        out.putInt(DEMAND_WORDS);
        out.putInt(RANK_WORDS);
        out.putInt(predicateWords);
        out.putInt(DIRECTIONAL_SHADE_WORDS);
        out.putInt(demandOffset);
        out.putInt(rankOffset);
        out.putInt(lightOffset);
        out.putInt(shadeOffset);
        out.putInt(predicateOffset);
        out.putInt(directionalOffset);
        putInts(out, this.demandWords);
        putInts(out, this.rankPrefix);
        putInts(out, this.packedLight);
        putInts(out, this.shadeBrightnessBits);
        putInts(out, this.lightPassWords);
        putInts(out, this.directionalShadeBits);
        if(out.position() != this.byteSize())
            throw new AssertionError("Sparse lighting serializer size drift");
        target.position(start + this.byteSize());
    }

    private int requireRank(int x, int y, int z) {
        int rank = this.compactRank(x, y, z);
        if(rank < 0)
            throw new IllegalArgumentException("Lighting sample was not demanded");
        return rank;
    }

    private static int[] buildRankPrefix(GpuLightingDemandMap demand) {
        int[] rank = new int[RANK_WORDS];
        int total = 0;
        for(int word = 0; word < RANK_WORDS; ++word) {
            rank[word] = total;
            total += Integer.bitCount(demand.word(word));
        }
        if(total != demand.uniqueSamples())
            throw new AssertionError("Sparse lighting rank prefix count mismatch");
        return rank;
    }

    private static int compactRank(GpuLightingDemandMap demand, int[] rank, int latticeIndex) {
        int wordIndex = latticeIndex >>> 5;
        int bit = latticeIndex & 31;
        int before = bit == 0 ? 0 : demand.word(wordIndex) & ((1 << bit) - 1);
        return rank[wordIndex] + Integer.bitCount(before);
    }

    private static void putInts(ByteBuffer out, int[] values) {
        for(int value : values)
            out.putInt(value);
    }

    static int bytesFor(int sampleCount) {
        if(sampleCount < 0 || sampleCount > MAX_SAMPLES)
            throw new IllegalArgumentException("Sparse lighting sample count out of bounds");
        int predicateWords = (sampleCount + 31) >>> 5;
        int words = HEADER_WORDS + DEMAND_WORDS + RANK_WORDS
                + sampleCount * 2 + predicateWords + DIRECTIONAL_SHADE_WORDS;
        return Math.multiplyExact(words, Integer.BYTES);
    }
}
