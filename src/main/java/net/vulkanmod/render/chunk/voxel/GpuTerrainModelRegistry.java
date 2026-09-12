package net.vulkanmod.render.chunk.voxel;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;

import java.util.Collections;
import java.util.EnumMap;
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
 * block class, registry name, or SOLID_RENDER bit. The whole immutable snapshot is
 * replaced atomically when BlockModelShaper installs a new baked-model cache.</p>
 */
public final class GpuTerrainModelRegistry {
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static final FullCubeTemplate FULL_CUBE_TEMPLATE = new FullCubeTemplate(0x3f);
    private static volatile Snapshot CURRENT = Snapshot.empty();

    private GpuTerrainModelRegistry() {}

    public static void replaceModels(Map<BlockState, BakedModel> models) {
        if(models == null)
            throw new IllegalArgumentException("Baked model cache must be present");

        IdentityHashMap<BlockState, FullCubeTemplate> qualified = new IdentityHashMap<>();
        EnumMap<RejectReason, Integer> rejected = new EnumMap<>(RejectReason.class);

        for(Map.Entry<BlockState, BakedModel> entry : models.entrySet()) {
            BlockState state = entry.getKey();
            BakedModel model = entry.getValue();
            RejectReason reason = rejectReason(state, model);
            if(reason == null) {
                qualified.put(state, FULL_CUBE_TEMPLATE);
            } else {
                rejected.merge(reason, 1, Integer::sum);
            }
        }

        long generation = NEXT_GENERATION.incrementAndGet();
        Snapshot snapshot = new Snapshot(
                generation,
                models.size(),
                Collections.unmodifiableMap(qualified),
                new EnumMap<>(rejected));
        CURRENT = snapshot;

        Initializer.LOGGER.info(
                "GPU terrain baked-model generation {}: full-cube geometry {}/{}; rejected [{}]",
                generation, qualified.size(), models.size(), describeRejected(rejected));
    }

    /** Safe from chunk workers: published snapshots are immutable after the volatile swap. */
    public static boolean isFullCubeGeometry(BlockState state) {
        return state != null && CURRENT.fullCubes.containsKey(state);
    }

    public static FullCubeTemplate getFullCubeTemplate(BlockState state) {
        return state == null ? null : CURRENT.fullCubes.get(state);
    }

    public static long generation() {
        return CURRENT.generation;
    }

    public static int qualifiedStateCount() {
        return CURRENT.fullCubes.size();
    }

    public static String describe() {
        Snapshot snapshot = CURRENT;
        return "GPU cube models: " + snapshot.fullCubes.size() + "/" + snapshot.totalStates
                + " (generation " + snapshot.generation + ")";
    }

    private static RejectReason rejectReason(BlockState state, BakedModel model) {
        if(state == null || model == null)
            return RejectReason.MISSING_STATE_OR_MODEL;
        if(state.getRenderShape() != RenderShape.MODEL)
            return RejectReason.NON_MODEL_RENDER_SHAPE;
        if(state.hasBlockEntity())
            return RejectReason.BLOCK_ENTITY;
        if(!state.getFluidState().isEmpty())
            return RejectReason.FLUID;
        if(ItemBlockRenderTypes.getChunkRenderType(state) != RenderType.solid())
            return RejectReason.NON_SOLID_LAYER;

        // Exact class, not instanceof: Forge/custom wrapper models may implement
        // dynamic state/model-data behavior even when they eventually delegate to
        // a SimpleBakedModel. Such models remain on the CPU path by construction.
        if(model.getClass() != SimpleBakedModel.class)
            return RejectReason.NON_SIMPLE_MODEL;
        if(model.isCustomRenderer())
            return RejectReason.CUSTOM_RENDERER;
        if(!model.useAmbientOcclusion())
            return RejectReason.AMBIENT_OCCLUSION_DISABLED;

        List<BakedQuad> unculled = model.getQuads(state, null, RandomSource.create(0L));
        if(!unculled.isEmpty())
            return RejectReason.UNCULLED_QUADS;

        for(Direction direction : Direction.values()) {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(0L));
            if(quads.size() != 1)
                return RejectReason.FACE_QUAD_COUNT;

            BakedQuad quad = quads.get(0);
            if(quad.getDirection() != direction)
                return RejectReason.FACE_DIRECTION;
            if(quad.isTinted())
                return RejectReason.TINTED_FACE;
            if(!quad.isShade())
                return RejectReason.UNSHADED_FACE;
            if(!FullCubeGeometry.isUnitFace(quad.getVertices(), direction))
                return RejectReason.NON_UNIT_FACE;
        }

        return null;
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

    /** Metadata placeholder for the first template family; later phases add UV/light data. */
    public record FullCubeTemplate(int faceMask) {}

    private record Snapshot(long generation, int totalStates,
                            Map<BlockState, FullCubeTemplate> fullCubes,
                            EnumMap<RejectReason, Integer> rejected) {
        static Snapshot empty() {
            return new Snapshot(0L, 0, Collections.emptyMap(), new EnumMap<>(RejectReason.class));
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
        AMBIENT_OCCLUSION_DISABLED,
        UNCULLED_QUADS,
        FACE_QUAD_COUNT,
        FACE_DIRECTION,
        TINTED_FACE,
        UNSHADED_FACE,
        NON_UNIT_FACE
    }
}
