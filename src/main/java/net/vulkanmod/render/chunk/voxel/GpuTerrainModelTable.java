package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Immutable, pointer-free GPU model-table image for the tiny terrain model subset
 * qualified by {@link GpuTerrainModelRegistry}.
 *
 * <p>The section voxel ABI continues to carry ordinary runtime block-state IDs.
 * This table supplies the separate resource-generation mapping from those IDs to
 * exact baked face templates. No Java model/sprite objects are needed by compute.</p>
 */
public final class GpuTerrainModelTable {
    public static final int MAGIC = 0x47544d31; // GTM1
    public static final int VERSION = 1;
    public static final int HEADER_WORDS = 8;
    public static final int FACE_COUNT = 6;
    public static final int FACE_WORDS = 9; // sprite slot + four exact UV pairs
    public static final int TEMPLATE_WORDS = 2 + FACE_COUNT * FACE_WORDS; // state ID + face mask + faces

    private static final Direction[] FACE_DIRECTIONS = {
            Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int MAX_CAPTURE_RETRIES = 3;

    private final long generation;
    private final int stateIndexCount;
    private final int templateCount;
    private final int templateBaseWord;
    private final int[] words;
    private final List<ResourceLocation> sprites;

    private GpuTerrainModelTable(long generation, int stateIndexCount, int templateCount,
                                 int templateBaseWord, int[] words,
                                 List<ResourceLocation> sprites) {
        this.generation = generation;
        this.stateIndexCount = stateIndexCount;
        this.templateCount = templateCount;
        this.templateBaseWord = templateBaseWord;
        this.words = words;
        this.sprites = List.copyOf(sprites);
    }

    /**
     * Capture one internally consistent registry generation. Resource reload can
     * atomically replace the qualification snapshot while this scan is running;
     * retry rather than publishing a table assembled from two generations.
     */
    public static GpuTerrainModelTable captureCurrent() {
        for(int attempt = 0; attempt < MAX_CAPTURE_RETRIES; ++attempt) {
            long generation = GpuTerrainModelRegistry.generation();
            int expectedQualified = GpuTerrainModelRegistry.qualifiedStateCount();

            HashMap<Integer, GpuTerrainModelRegistry.FullCubeTemplate> qualified = new HashMap<>();
            int maxStateId = -1;
            for(Block block : BuiltInRegistries.BLOCK) {
                for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                    int stateId = Block.getId(state);
                    if(stateId < 0)
                        throw new IllegalStateException("Negative runtime block-state ID in GPU terrain model capture");
                    maxStateId = Math.max(maxStateId, stateId);

                    GpuTerrainModelRegistry.FullCubeTemplate template =
                            GpuTerrainModelRegistry.getFullCubeTemplate(stateId);
                    if(template != null)
                        qualified.put(stateId, template);
                }
            }

            if(generation != GpuTerrainModelRegistry.generation()
                    || expectedQualified != GpuTerrainModelRegistry.qualifiedStateCount())
                continue;
            if(qualified.size() != expectedQualified) {
                throw new IllegalStateException("GPU terrain model table could only resolve " + qualified.size()
                        + " of " + expectedQualified + " qualified runtime block states");
            }

            return pack(generation, maxStateId + 1, qualified);
        }
        throw new IllegalStateException("GPU terrain baked-model generation changed repeatedly during table capture");
    }

    private static GpuTerrainModelTable pack(
            long generation,
            int stateIndexCount,
            Map<Integer, GpuTerrainModelRegistry.FullCubeTemplate> qualified) {
        if(stateIndexCount < 0)
            throw new IllegalArgumentException("Invalid GPU model state-index size");

        ArrayList<Integer> stateIds = new ArrayList<>(qualified.keySet());
        stateIds.sort(Integer::compareTo);

        TreeSet<ResourceLocation> spriteSet = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        for(int stateId : stateIds) {
            GpuTerrainModelRegistry.FullCubeTemplate template = qualified.get(stateId);
            requireCompleteTemplate(stateId, template);
            for(Direction direction : FACE_DIRECTIONS)
                spriteSet.add(template.face(direction).spriteId());
        }
        ArrayList<ResourceLocation> sprites = new ArrayList<>(spriteSet);
        HashMap<ResourceLocation, Integer> spriteSlots = new HashMap<>();
        for(int i = 0; i < sprites.size(); ++i)
            spriteSlots.put(sprites.get(i), i);

        long templateBase = (long) HEADER_WORDS + stateIndexCount;
        long wordCount = templateBase + (long) stateIds.size() * TEMPLATE_WORDS;
        if(wordCount > Integer.MAX_VALUE)
            throw new IllegalStateException("GPU terrain model table is too large: " + wordCount + " words");

        int[] words = new int[(int) wordCount];
        words[0] = MAGIC;
        words[1] = VERSION;
        words[2] = (int) generation;
        words[3] = (int) (generation >>> 32);
        words[4] = stateIndexCount;
        words[5] = stateIds.size();
        words[6] = sprites.size();
        words[7] = (int) templateBase;

        for(int denseIndex = 0; denseIndex < stateIds.size(); ++denseIndex) {
            int stateId = stateIds.get(denseIndex);
            if(stateId >= stateIndexCount)
                throw new IllegalStateException("Qualified state ID exceeds model-table index range");
            words[HEADER_WORDS + stateId] = denseIndex + 1; // zero remains the unqualified sentinel

            GpuTerrainModelRegistry.FullCubeTemplate template = qualified.get(stateId);
            int base = (int) templateBase + denseIndex * TEMPLATE_WORDS;
            words[base] = stateId;
            words[base + 1] = template.faceMask();

            for(int face = 0; face < FACE_COUNT; ++face) {
                GpuTerrainModelRegistry.FaceTemplate source = template.face(FACE_DIRECTIONS[face]);
                Integer spriteSlot = spriteSlots.get(source.spriteId());
                if(spriteSlot == null)
                    throw new IllegalStateException("Missing deterministic sprite slot for " + source.spriteId());
                int faceBase = base + 2 + face * FACE_WORDS;
                words[faceBase] = spriteSlot;
                for(int vertex = 0; vertex < 4; ++vertex) {
                    words[faceBase + 1 + vertex * 2] = source.uBits(vertex);
                    words[faceBase + 2 + vertex * 2] = source.vBits(vertex);
                }
            }
        }

        return new GpuTerrainModelTable(generation, stateIndexCount, stateIds.size(),
                (int) templateBase, words, sprites);
    }

    private static void requireCompleteTemplate(int stateId,
                                                GpuTerrainModelRegistry.FullCubeTemplate template) {
        if(template == null || template.faceMask() != 0x3f)
            throw new IllegalStateException("Incomplete GPU cube template for state " + stateId);
        for(Direction direction : FACE_DIRECTIONS) {
            GpuTerrainModelRegistry.FaceTemplate face = template.face(direction);
            if(face == null || face.spriteId() == null)
                throw new IllegalStateException("Missing GPU face template for state " + stateId
                        + " direction " + direction);
        }
    }

    public long generation() { return generation; }
    public int stateIndexCount() { return stateIndexCount; }
    public int templateCount() { return templateCount; }
    public int spriteCount() { return sprites.size(); }
    public int templateBaseWord() { return templateBaseWord; }
    public int wordCount() { return words.length; }
    public int byteSize() { return Math.multiplyExact(words.length, Integer.BYTES); }

    /** Returns -1 when the runtime state is not GPU-template qualified. */
    public int templateIndexForStateId(int stateId) {
        if(stateId < 0 || stateId >= stateIndexCount)
            return -1;
        return words[HEADER_WORDS + stateId] - 1;
    }

    public int stateIdForTemplate(int templateIndex) {
        return words[templateBase(templateIndex)];
    }

    public int faceMask(int templateIndex) {
        return words[templateBase(templateIndex) + 1];
    }

    public int spriteSlot(int templateIndex, int face) {
        return words[faceBase(templateIndex, face)];
    }

    public ResourceLocation spriteId(int spriteSlot) {
        if(spriteSlot < 0 || spriteSlot >= sprites.size())
            throw new IndexOutOfBoundsException("GPU terrain sprite slot " + spriteSlot);
        return sprites.get(spriteSlot);
    }

    public int uBits(int templateIndex, int face, int vertex) {
        checkVertex(vertex);
        return words[faceBase(templateIndex, face) + 1 + vertex * 2];
    }

    public int vBits(int templateIndex, int face, int vertex) {
        checkVertex(vertex);
        return words[faceBase(templateIndex, face) + 2 + vertex * 2];
    }

    public int word(int index) {
        if(index < 0 || index >= words.length)
            throw new IndexOutOfBoundsException("GPU terrain model-table word " + index);
        return words[index];
    }

    /** Write in the little-endian uint32 ABI consumed by Vulkan shaders. */
    public void writeTo(ByteBuffer target) {
        if(target == null || target.isReadOnly() || target.remaining() < byteSize()
                || (target.position() & 3) != 0)
            throw new IllegalArgumentException("Writable, aligned GPU model-table capacity required");
        ByteBuffer view = target.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for(int word : words)
            view.putInt(word);
        target.position(view.position());
    }

    private int templateBase(int templateIndex) {
        if(templateIndex < 0 || templateIndex >= templateCount)
            throw new IndexOutOfBoundsException("GPU terrain template index " + templateIndex);
        return templateBaseWord + templateIndex * TEMPLATE_WORDS;
    }

    private int faceBase(int templateIndex, int face) {
        if(face < 0 || face >= FACE_COUNT)
            throw new IndexOutOfBoundsException("GPU terrain face " + face);
        return templateBase(templateIndex) + 2 + face * FACE_WORDS;
    }

    private static void checkVertex(int vertex) {
        if(vertex < 0 || vertex >= 4)
            throw new IndexOutOfBoundsException("GPU terrain face vertex " + vertex);
    }
}
