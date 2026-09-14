package net.vulkanmod.render.chunk.voxel;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.ForgeConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resource-generation-scoped qualification of the deliberately tiny terrain model
 * subset that can eventually be instantiated by compute without asking Minecraft's
 * dynamic model path for geometry.
 *
 * <p>This registry is diagnostic-only for now: CPU renderBatched remains authoritative.
 * Qualification is based on the actual baked model generation, never a block ID,
 * block class, registry name, or SOLID_RENDER bit. It queries the Forge ModelData
 * and render-type overload used by terrain rendering and rejects multi-layer or
 * seed-varying results. The whole immutable snapshot is replaced atomically when
 * BlockModelShaper installs a new baked-model cache.</p>
 */
public final class GpuTerrainModelRegistry {
    private static final long[] QUAD_STABILITY_SEEDS = { 0L, 42L, 0x6a09e667f3bcc909L };
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static volatile Snapshot CURRENT = Snapshot.empty();

    private GpuTerrainModelRegistry() {}

    public static void replaceModels(Map<BlockState, BakedModel> models) {
        if(models == null)
            throw new IllegalArgumentException("Baked model cache must be present");

        IdentityHashMap<BlockState, FullCubeTemplate> qualified = new IdentityHashMap<>();
        HashMap<Integer, FullCubeTemplate> qualifiedByStateId = new HashMap<>();
        EnumMap<RejectReason, Integer> rejected = new EnumMap<>(RejectReason.class);

        for(Map.Entry<BlockState, BakedModel> entry : models.entrySet()) {
            BlockState state = entry.getKey();
            Qualification qualification = qualify(state, entry.getValue());
            if(qualification.template != null) {
                FullCubeTemplate template = qualification.template;
                qualified.put(state, template);
                qualifiedByStateId.put(Block.getId(state), template);
            } else {
                rejected.merge(qualification.rejectReason, 1, Integer::sum);
            }
        }

        // The regular client path remains fail-closed: an unusual resource pack can
        // simply make this optimization ineligible. The isolated CI startup smoke,
        // however, must prove that the real vanilla baked-model generation produced
        // at least one bit-exact textured template rather than silently testing zero.
        if(Boolean.getBoolean("vulkanmod.smokeTest") && !models.isEmpty() && qualified.isEmpty()) {
            throw new IllegalStateException(
                    "GPU terrain smoke produced no canonical full-cube sprite/UV templates");
        }

        long generation = NEXT_GENERATION.incrementAndGet();
        Snapshot snapshot = new Snapshot(
                generation,
                models.size(),
                Collections.unmodifiableMap(qualified),
                Collections.unmodifiableMap(qualifiedByStateId),
                new EnumMap<>(rejected));
        CURRENT = snapshot;

        Initializer.LOGGER.info(
                "GPU terrain baked-model generation {}: canonical textured full-cube templates {}/{}; rejected [{}]",
                generation, qualified.size(), models.size(), describeRejected(rejected));
        if(!qualified.isEmpty()) {
            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_UV_TEMPLATE_OK: {} state-id keyed templates retain exact baked sprite identity and ordered atlas UV bits",
                    qualified.size());
        }
    }

    /** Safe from chunk workers: published snapshots are immutable after the volatile swap. */
    public static boolean isFullCubeGeometry(BlockState state) {
        return isFullCubeGeometry(state,
                ForgeConfig.CLIENT.experimentalForgeLightPipelineEnabled.get());
    }

    /** Package-private seam for the startup capability oracle; never mutates Forge config. */
    static boolean isFullCubeGeometry(BlockState state, boolean forgeExperimentalLighting) {
        return !forgeExperimentalLighting
                && state != null && CURRENT.fullCubes.containsKey(state);
    }

    public static FullCubeTemplate getFullCubeTemplate(BlockState state) {
        return state == null ? null : CURRENT.fullCubes.get(state);
    }

    /**
     * O(1) lookup using the same numeric block-state ID serialized into section
     * voxel snapshots. This is the CPU-side shape of the future GPU model table;
     * section snapshots do not duplicate sprite or UV data.
     */
    public static FullCubeTemplate getFullCubeTemplate(int blockStateId) {
        return CURRENT.fullCubesByStateId.get(blockStateId);
    }

    public static long generation() {
        return CURRENT.generation;
    }

    public static int qualifiedStateCount() {
        return CURRENT.fullCubes.size();
    }

    public static String describe() {
        Snapshot snapshot = CURRENT;
        return "GPU canonical textured cube models: " + snapshot.fullCubes.size() + "/" + snapshot.totalStates
                + " (generation " + snapshot.generation + ")";
    }

    private static Qualification qualify(BlockState state, BakedModel model) {
        if(state == null || model == null)
            return Qualification.rejected(RejectReason.MISSING_STATE_OR_MODEL);
        if(state.getRenderShape() != RenderShape.MODEL)
            return Qualification.rejected(RejectReason.NON_MODEL_RENDER_SHAPE);
        if(state.hasBlockEntity())
            return Qualification.rejected(RejectReason.BLOCK_ENTITY);
        if(!state.getFluidState().isEmpty())
            return Qualification.rejected(RejectReason.FLUID);
        RenderType solid = RenderType.solid();
        if(ItemBlockRenderTypes.getChunkRenderType(state) != solid)
            return Qualification.rejected(RejectReason.NON_SOLID_LAYER);

        // Exact class, not instanceof: Forge/custom wrapper models may implement
        // dynamic state/model-data behavior even when they eventually delegate to
        // a SimpleBakedModel. Such models remain on the CPU path by construction.
        if(model.getClass() != SimpleBakedModel.class)
            return Qualification.rejected(RejectReason.NON_SIMPLE_MODEL);
        if(model.isCustomRenderer())
            return Qualification.rejected(RejectReason.CUSTOM_RENDERER);
        var renderTypes = model.getRenderTypes(state, RandomSource.create(0L), ModelData.EMPTY);
        var renderTypeIterator = renderTypes.iterator();
        if(!renderTypeIterator.hasNext() || renderTypeIterator.next() != solid
                || renderTypeIterator.hasNext())
            return Qualification.rejected(RejectReason.FORGE_RENDER_TYPE_CONTRACT);
        if(!model.useAmbientOcclusion(state, solid))
            return Qualification.rejected(RejectReason.AMBIENT_OCCLUSION_DISABLED);

        List<BakedQuad> unculled = stableForgeQuads(model, state, null, solid);
        if(unculled == null)
            return Qualification.rejected(RejectReason.UNSTABLE_FORGE_QUADS);
        if(!unculled.isEmpty())
            return Qualification.rejected(RejectReason.UNCULLED_QUADS);

        FaceTemplate down = null;
        FaceTemplate up = null;
        FaceTemplate north = null;
        FaceTemplate south = null;
        FaceTemplate west = null;
        FaceTemplate east = null;

        for(Direction direction : Direction.values()) {
            List<BakedQuad> quads = stableForgeQuads(model, state, direction, solid);
            if(quads == null)
                return Qualification.rejected(RejectReason.UNSTABLE_FORGE_QUADS);
            if(quads.size() != 1)
                return Qualification.rejected(RejectReason.FACE_QUAD_COUNT);

            BakedQuad quad = quads.get(0);
            if(quad.getDirection() != direction)
                return Qualification.rejected(RejectReason.FACE_DIRECTION);
            if(quad.isTinted())
                return Qualification.rejected(RejectReason.TINTED_FACE);
            if(!quad.isShade())
                return Qualification.rejected(RejectReason.UNSHADED_FACE);
            if(!quad.hasAmbientOcclusion())
                return Qualification.rejected(RejectReason.FACE_AMBIENT_OCCLUSION_DISABLED);
            if(!hasOpaqueWhiteVertexColors(quad.getVertices()))
                return Qualification.rejected(RejectReason.NON_WHITE_VERTEX_COLOR);
            if(!FullCubeGeometry.isUnitFace(quad.getVertices(), direction))
                return Qualification.rejected(RejectReason.NON_UNIT_FACE);
            if(!FullCubeGeometry.hasCanonicalVertexOrder(quad.getVertices(), direction))
                return Qualification.rejected(RejectReason.NON_CANONICAL_VERTEX_ORDER);

            FaceTemplate face = captureFaceTemplate(quad);
            if(face == null)
                return Qualification.rejected(RejectReason.INVALID_FACE_TEXTURE_TEMPLATE);
            if(!face.matches(quad))
                return Qualification.rejected(RejectReason.FACE_TEXTURE_TEMPLATE_MISMATCH);

            switch(direction) {
                case DOWN -> down = face;
                case UP -> up = face;
                case NORTH -> north = face;
                case SOUTH -> south = face;
                case WEST -> west = face;
                case EAST -> east = face;
            }
        }

        return Qualification.accepted(new FullCubeTemplate(0x3f, down, up, north, south, west, east));
    }

    /**
     * Query the same Forge overload used by terrain rendering. Exact
     * {@link SimpleBakedModel} instances inherit Forge's ModelData.EMPTY behavior;
     * multiple position-like seeds guard that the captured generation has no
     * random variant hidden behind the otherwise static model.
     */
    private static List<BakedQuad> stableForgeQuads(BakedModel model, BlockState state,
                                                     Direction direction, RenderType renderType) {
        List<BakedQuad> baseline = null;
        RandomSource random = RandomSource.create();
        for(long seed : QUAD_STABILITY_SEEDS) {
            random.setSeed(seed);
            List<BakedQuad> candidate = model.getQuads(state, direction, random,
                    ModelData.EMPTY, renderType);
            if(candidate == null)
                return null;
            if(baseline == null)
                baseline = candidate;
            else if(!sameQuads(baseline, candidate))
                return null;
        }
        return baseline;
    }

    private static boolean sameQuads(List<BakedQuad> left, List<BakedQuad> right) {
        if(left.size() != right.size())
            return false;
        for(int i = 0; i < left.size(); ++i) {
            BakedQuad a = left.get(i);
            BakedQuad b = right.get(i);
            if(a == null || b == null
                    || a.getTintIndex() != b.getTintIndex()
                    || a.getDirection() != b.getDirection()
                    || a.isShade() != b.isShade()
                    || a.hasAmbientOcclusion() != b.hasAmbientOcclusion()
                    || a.getSprite() != b.getSprite()
                    || !Arrays.equals(a.getVertices(), b.getVertices()))
                return false;
        }
        return true;
    }

    /**
     * Capture the atlas-space UV words exactly as baked. We intentionally do not
     * reconstruct them from sprite bounds: rotated or otherwise transformed model
     * UVs must survive with exactly the same vertex identity as the CPU quad.
     */
    private static FaceTemplate captureFaceTemplate(BakedQuad quad) {
        if(quad == null)
            return null;

        TextureAtlasSprite sprite = quad.getSprite();
        if(sprite == null || sprite.contents() == null || sprite.contents().name() == null)
            return null;

        int[] vertices = quad.getVertices();
        if(vertices == null || vertices.length % 4 != 0)
            return null;
        int stride = vertices.length / 4;
        if(stride < 6)
            return null;

        int u0 = vertices[4];
        int v0 = vertices[5];
        int u1 = vertices[stride + 4];
        int v1 = vertices[stride + 5];
        int u2 = vertices[stride * 2 + 4];
        int v2 = vertices[stride * 2 + 5];
        int u3 = vertices[stride * 3 + 4];
        int v3 = vertices[stride * 3 + 5];
        if(!finiteUv(u0, v0) || !finiteUv(u1, v1)
                || !finiteUv(u2, v2) || !finiteUv(u3, v3))
            return null;

        return new FaceTemplate(sprite.contents().name(),
                u0, v0, u1, v1, u2, v2, u3, v3);
    }

    private static boolean finiteUv(int uBits, int vBits) {
        return Float.isFinite(Float.intBitsToFloat(uBits))
                && Float.isFinite(Float.intBitsToFloat(vBits));
    }

    /**
     * VertexConsumer.putBulkData multiplies baked vertex color into the AO/shade
     * result even when a quad has no tint index. Until the immutable model table
     * carries those four color words, only the identity value is reproducible.
     */
    private static boolean hasOpaqueWhiteVertexColors(int[] vertices) {
        if(vertices == null || vertices.length % 4 != 0)
            return false;
        int stride = vertices.length / 4;
        if(stride < 4)
            return false;
        for(int vertex = 0; vertex < 4; vertex++) {
            if(vertices[vertex * stride + 3] != 0xffffffff)
                return false;
        }
        return true;
    }

    private static String describeRejected(EnumMap<RejectReason, Integer> rejected) {
        if(rejected.isEmpty())
            return "none";
        StringJoiner joiner = new StringJoiner(", ");
        for(RejectReason reason : RejectReason.values()) {
            Integer count = rejected.get(reason);
            if(count != null && count != 0)
                joiner.add(reason.name().toLowerCase() + "=" + count);
        }
        return joiner.toString();
    }

    /**
     * One fully qualified unit-cube model. Direction fields avoid depending on
     * enum ordinals in the future GPU packer while keeping this Java snapshot
     * immutable and allocation-free for worker lookups.
     */
    public record FullCubeTemplate(int faceMask,
                                   FaceTemplate down,
                                   FaceTemplate up,
                                   FaceTemplate north,
                                   FaceTemplate south,
                                   FaceTemplate west,
                                   FaceTemplate east) {
        public FaceTemplate face(Direction direction) {
            if(direction == null)
                return null;
            return switch(direction) {
                case DOWN -> down;
                case UP -> up;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }
    }

    /**
     * Exact baked face texture identity. UV values are retained as their original
     * IEEE-754 words instead of round-tripping through float serialization.
     */
    public record FaceTemplate(ResourceLocation spriteId,
                               int u0Bits, int v0Bits,
                               int u1Bits, int v1Bits,
                               int u2Bits, int v2Bits,
                               int u3Bits, int v3Bits) {
        public int uBits(int vertex) {
            return switch(vertex) {
                case 0 -> u0Bits;
                case 1 -> u1Bits;
                case 2 -> u2Bits;
                case 3 -> u3Bits;
                default -> throw new IndexOutOfBoundsException("face vertex " + vertex);
            };
        }

        public int vBits(int vertex) {
            return switch(vertex) {
                case 0 -> v0Bits;
                case 1 -> v1Bits;
                case 2 -> v2Bits;
                case 3 -> v3Bits;
                default -> throw new IndexOutOfBoundsException("face vertex " + vertex);
            };
        }

        public float u(int vertex) {
            return Float.intBitsToFloat(uBits(vertex));
        }

        public float v(int vertex) {
            return Float.intBitsToFloat(vBits(vertex));
        }

        private boolean matches(BakedQuad quad) {
            if(quad == null || quad.getSprite() == null || quad.getSprite().contents() == null
                    || !spriteId.equals(quad.getSprite().contents().name()))
                return false;
            int[] vertices = quad.getVertices();
            if(vertices == null || vertices.length % 4 != 0)
                return false;
            int stride = vertices.length / 4;
            if(stride < 6)
                return false;
            return u0Bits == vertices[4] && v0Bits == vertices[5]
                    && u1Bits == vertices[stride + 4] && v1Bits == vertices[stride + 5]
                    && u2Bits == vertices[stride * 2 + 4] && v2Bits == vertices[stride * 2 + 5]
                    && u3Bits == vertices[stride * 3 + 4] && v3Bits == vertices[stride * 3 + 5];
        }
    }

    private record Qualification(FullCubeTemplate template, RejectReason rejectReason) {
        static Qualification accepted(FullCubeTemplate template) {
            return new Qualification(template, null);
        }

        static Qualification rejected(RejectReason reason) {
            return new Qualification(null, reason);
        }
    }

    private record Snapshot(long generation, int totalStates,
                            Map<BlockState, FullCubeTemplate> fullCubes,
                            Map<Integer, FullCubeTemplate> fullCubesByStateId,
                            EnumMap<RejectReason, Integer> rejected) {
        static Snapshot empty() {
            return new Snapshot(0L, 0, Collections.emptyMap(), Collections.emptyMap(),
                    new EnumMap<>(RejectReason.class));
        }
    }

    private enum RejectReason {
        MISSING_STATE_OR_MODEL,
        NON_MODEL_RENDER_SHAPE,
        BLOCK_ENTITY,
        FLUID,
        NON_SOLID_LAYER,
        NON_SIMPLE_MODEL,
        CUSTOM_RENDERER,
        FORGE_RENDER_TYPE_CONTRACT,
        AMBIENT_OCCLUSION_DISABLED,
        UNSTABLE_FORGE_QUADS,
        UNCULLED_QUADS,
        FACE_QUAD_COUNT,
        FACE_DIRECTION,
        TINTED_FACE,
        UNSHADED_FACE,
        FACE_AMBIENT_OCCLUSION_DISABLED,
        NON_WHITE_VERTEX_COLOR,
        NON_UNIT_FACE,
        NON_CANONICAL_VERTEX_ORDER,
        INVALID_FACE_TEXTURE_TEMPLATE,
        FACE_TEXTURE_TEMPLATE_MISMATCH
    }
}
