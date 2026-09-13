package net.vulkanmod.render.chunk.voxel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, pointer-free input stream for future terrain compute. State IDs are
 * runtime registry IDs, NOT GPU model-template IDs. See docs/GPU_TERRAIN_BOUNDARY.md.
 * All offsets in the header are uint32 word offsets relative to this record.
 */
public final class SectionVoxelSnapshot {
    public static final int MAGIC = 0x56584c31;
    public static final int VERSION = 3;
    public static final int BLOCK_COUNT = 4096;
    public static final int HEADER_WORDS = 16;
    public static final int INDEX_WORDS = BLOCK_COUNT / 2;
    public static final int PLANE_WORDS = BLOCK_COUNT / 32;
    public static final int FLAG_PLANES = 5;
    public static final int HALO_FACES = 6;
    public static final int HALO_FACE_WORDS = 256 / 32;
    public static final int HALO_WORDS = HALO_FACES * HALO_FACE_WORDS;
    public static final int SOLID_RENDER = 1;
    public static final int HAS_FLUID = 2;
    public static final int HAS_BLOCK_ENTITY = 4;
    public static final int CPU_REQUIRED = 8;
    /** Geometry-only qualification; CPU_REQUIRED remains authoritative until GPU emission is proven. */
    public static final int GPU_FULL_CUBE = 16;
    public static final int MAX_BYTES = (HEADER_WORDS + BLOCK_COUNT + INDEX_WORDS
            + FLAG_PLANES * PLANE_WORDS + HALO_WORDS) * Integer.BYTES;

    private final int[] words;

    private SectionVoxelSnapshot(int[] words) {
        this.words = words;
    }

    public int x() { return words[4]; }
    public int y() { return words[5]; }
    public int z() { return words[6]; }
    public int byteSize() { return words.length * Integer.BYTES; }
    public int paletteSize() { return words[7]; }

    public static int blockIndex(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= 16 || y >= 16 || z >= 16)
            throw new IllegalArgumentException("Block must be inside its section");
        return x | (y << 4) | (z << 8);
    }

    public int stateId(int index) {
        checkIndex(index);
        int packed = words[words[9] + (index >>> 1)];
        int paletteIndex = (packed >>> ((index & 1) * 16)) & 0xffff;
        return words[words[8] + paletteIndex];
    }

    public int flags(int index) {
        checkIndex(index);
        int flags = 0;
        for (int plane = 0; plane < FLAG_PLANES; plane++) {
            int bits = words[words[10] + plane * PLANE_WORDS + (index >>> 5)];
            flags |= ((bits >>> (index & 31)) & 1) << plane;
        }
        return flags;
    }

    /**
     * Returns whether the one-block-outside neighbor for a section-boundary face
     * was qualified as GPU_FULL_CUBE when this snapshot was built. Faces use the
     * vanilla Direction ordinal order: down, up, north, south, west, east.
     */
    public boolean boundaryNeighborGpuFullCube(int index, int face) {
        checkIndex(index);
        int bit = haloBit(index, face);
        int word = words[words[12] + face * HALO_FACE_WORDS + (bit >>> 5)];
        return ((word >>> (bit & 31)) & 1) != 0;
    }

    /** Write directly into a caller-owned staging slice; never allocates native memory. */
    public void writeTo(ByteBuffer target) {
        if (target.isReadOnly() || target.remaining() < byteSize()
                || (target.position() & 3) != 0)
            throw new IllegalArgumentException("Writable, aligned snapshot capacity required");
        ByteBuffer view = target.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (int word : words) view.putInt(word);
        target.position(view.position());
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= BLOCK_COUNT)
            throw new IllegalArgumentException("Invalid voxel index");
    }

    private static int haloBit(int index, int face) {
        if (face < 0 || face >= HALO_FACES)
            throw new IllegalArgumentException("Invalid halo face");
        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = (index >>> 8) & 15;
        return switch (face) {
            case 0 -> {
                if (y != 0) throw new IllegalArgumentException("DOWN halo requires y=0");
                yield x | (z << 4);
            }
            case 1 -> {
                if (y != 15) throw new IllegalArgumentException("UP halo requires y=15");
                yield x | (z << 4);
            }
            case 2 -> {
                if (z != 0) throw new IllegalArgumentException("NORTH halo requires z=0");
                yield x | (y << 4);
            }
            case 3 -> {
                if (z != 15) throw new IllegalArgumentException("SOUTH halo requires z=15");
                yield x | (y << 4);
            }
            case 4 -> {
                if (x != 0) throw new IllegalArgumentException("WEST halo requires x=0");
                yield z | (y << 4);
            }
            case 5 -> {
                if (x != 15) throw new IllegalArgumentException("EAST halo requires x=15");
                yield z | (y << 4);
            }
            default -> throw new IllegalArgumentException("Invalid halo face");
        };
    }

    /** Worker-local, single-use builder. No BlockState, world, model or BE references escape. */
    public static final class Builder {
        private final int x, y, z;
        private final Map<Integer, Integer> palette = new HashMap<>();
        private final int[] indices = new int[INDEX_WORDS];
        private final int[] flags = new int[FLAG_PLANES * PLANE_WORDS];
        private final int[] halo = new int[HALO_WORDS];
        private int count;
        private boolean finished;

        public Builder(int x, int y, int z) {
            if (((x | y | z) & 15) != 0)
                throw new IllegalArgumentException("Section origin must be aligned");
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** Append in x-fastest, then y, then z order (BlockPos.betweenClosed). */
        public void add(int stateId, int blockFlags) {
            if (finished || count == BLOCK_COUNT)
                throw new IllegalStateException("Snapshot builder is full or sealed");
            if (stateId < 0 || (blockFlags & ~31) != 0)
                throw new IllegalArgumentException("Invalid state ID or flags");
            // GPU_FULL_CUBE remains informational in v3. CPU meshing remains authoritative
            // until GPU face/light/vertex emission has its own validated fallback gate.
            if ((blockFlags & CPU_REQUIRED) == 0)
                throw new IllegalArgumentException("Version 3 still requires CPU fallback");
            int paletteIndex = palette.computeIfAbsent(stateId, ignored -> palette.size());
            indices[count >>> 1] |= paletteIndex << ((count & 1) * 16);
            for (int plane = 0; plane < FLAG_PLANES; plane++) {
                if ((blockFlags & (1 << plane)) != 0)
                    flags[plane * PLANE_WORDS + (count >>> 5)] |= 1 << (count & 31);
            }
            count++;
        }

        /**
         * Capture only the qualified-cube occupancy immediately outside one section
         * face. This is a narrow face-rejection halo, not final occlusion/light/AO data.
         */
        public void setBoundaryNeighborGpuFullCube(int index, int face, boolean gpuFullCube) {
            if (finished)
                throw new IllegalStateException("Snapshot builder is sealed");
            checkIndex(index);
            int bit = haloBit(index, face);
            int word = face * HALO_FACE_WORDS + (bit >>> 5);
            int mask = 1 << (bit & 31);
            if (gpuFullCube) halo[word] |= mask;
            else halo[word] &= ~mask;
        }

        public SectionVoxelSnapshot finish() {
            if (finished || count != BLOCK_COUNT)
                throw new IllegalStateException("Exactly 4096 voxels required, once");
            finished = true;
            int indexOffset = HEADER_WORDS + palette.size();
            int flagsOffset = indexOffset + INDEX_WORDS;
            int haloOffset = flagsOffset + flags.length;
            int[] words = new int[haloOffset + halo.length];
            words[0] = MAGIC;
            words[1] = VERSION;
            words[2] = words.length * Integer.BYTES;
            words[3] = BLOCK_COUNT;
            words[4] = x; words[5] = y; words[6] = z;
            words[7] = palette.size();
            words[8] = HEADER_WORDS;
            words[9] = indexOffset;
            words[10] = flagsOffset;
            words[11] = FLAG_PLANES;
            words[12] = haloOffset;
            words[13] = HALO_WORDS;
            // 14..15 reserved, zero: no light/tint/template streams yet.
            palette.forEach((state, index) -> words[HEADER_WORDS + index] = state);
            System.arraycopy(indices, 0, words, indexOffset, indices.length);
            System.arraycopy(flags, 0, words, flagsOffset, flags.length);
            System.arraycopy(halo, 0, words, haloOffset, halo.length);
            return new SectionVoxelSnapshot(words);
        }
    }
}
