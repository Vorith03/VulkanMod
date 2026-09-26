package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.render.chunk.RegionBatchLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Versioned CPU/GPU ABI for one region generation's section draw candidates. */
public final class GpuRegionCandidateTable {
    public static final int MAGIC = 0x47525331; // GRS1
    public static final int VERSION = 1;
    public static final int HEADER_WORDS = 8;
    public static final int RECORD_WORDS = 8;

    public static final int READY = 1;
    public static final int GRAPH_VISIBLE = 1 << 1;
    public static final int DIRECT_SEED = 1 << 2;
    public static final int LAYER_SHIFT = 8;
    public static final int LAYER_MASK = 0xf << LAYER_SHIFT;

    private final long generation;
    private final int regionX;
    private final int regionY;
    private final int regionZ;
    private final int count;
    private final int[] records;

    private GpuRegionCandidateTable(long generation, int regionX, int regionY, int regionZ,
                                    int count, int[] records) {
        this.generation = generation;
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionZ = regionZ;
        this.count = count;
        this.records = records;
    }

    public long generation() { return generation; }
    public int regionX() { return regionX; }
    public int regionY() { return regionY; }
    public int regionZ() { return regionZ; }
    public int candidateCount() { return count; }

    public int byteSize() {
        return Math.multiplyExact(HEADER_WORDS + count * RECORD_WORDS, Integer.BYTES);
    }

    public void writeTo(ByteBuffer target) {
        if(target.remaining() < byteSize())
            throw new IllegalArgumentException("Candidate-table destination is too small");
        target.order(ByteOrder.nativeOrder())
                .putInt(MAGIC).putInt(VERSION)
                .putInt((int)generation).putInt((int)(generation >>> 32))
                .putInt(count).putInt(regionX).putInt(regionY).putInt(regionZ);
        for(int word : records) target.putInt(word);
    }

    int recordWord(int candidate, int word) {
        if(candidate < 0 || candidate >= count || word < 0 || word >= RECORD_WORDS)
            throw new IndexOutOfBoundsException("GPU region candidate word");
        return records[candidate * RECORD_WORDS + word];
    }

    public static int flags(boolean ready, boolean graphVisible, int layer) {
        return flags(ready, graphVisible, false, layer);
    }

    public static int flags(boolean ready, boolean graphVisible, boolean directSeed, int layer) {
        if(layer < 0 || layer > 15)
            throw new IllegalArgumentException("Terrain layer must fit four bits");
        return (ready ? READY : 0) | (graphVisible ? GRAPH_VISIBLE : 0)
                | (directSeed ? DIRECT_SEED : 0) | (layer << LAYER_SHIFT);
    }

    public static final class Builder {
        private final long generation;
        private final int regionX;
        private final int regionY;
        private final int regionZ;
        private final int[] records = new int[RegionBatchLayout.MAX_SECTIONS * RECORD_WORDS];
        private int count;

        public Builder(long generation, int regionX, int regionY, int regionZ) {
            // ChunkArea spans eight sections per axis, but dimensions such as the
            // Overworld begin at Y=-64. The ABI only requires a section-aligned
            // origin; packedSection is relative to that origin and remains 0..511.
            if(((regionX | regionY | regionZ) & 15) != 0)
                throw new IllegalArgumentException("GPU candidate region origin must be section aligned");
            this.generation = generation;
            this.regionX = regionX;
            this.regionY = regionY;
            this.regionZ = regionZ;
        }

        public Builder add(int indexCount, int instanceCount, int firstIndex,
                           int vertexOffset, int packedSection, int flags) {
            if(count >= RegionBatchLayout.MAX_SECTIONS)
                throw new IllegalStateException("GPU candidate region exceeds 512 sections");
            if(packedSection < 0 || packedSection >= RegionBatchLayout.MAX_SECTIONS)
                throw new IllegalArgumentException("Packed section is outside its region");
            if((flags & ~(READY | GRAPH_VISIBLE | DIRECT_SEED | LAYER_MASK)) != 0)
                throw new IllegalArgumentException("Unknown GPU candidate flags");
            int base = count++ * RECORD_WORDS;
            records[base] = indexCount;
            records[base + 1] = instanceCount;
            records[base + 2] = firstIndex;
            records[base + 3] = vertexOffset;
            records[base + 4] = packedSection;
            records[base + 5] = flags;
            records[base + 6] = 0;
            records[base + 7] = 0;
            return this;
        }

        public GpuRegionCandidateTable finish() {
            return new GpuRegionCandidateTable(generation, regionX, regionY, regionZ,
                    count, Arrays.copyOf(records, count * RECORD_WORDS));
        }
    }
}
