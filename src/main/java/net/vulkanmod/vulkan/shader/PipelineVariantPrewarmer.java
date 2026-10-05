package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraftforge.fml.loading.FMLPaths;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.shader.cache.BoundedDiskCache;
import net.vulkanmod.vulkan.shader.cache.CompilationCache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Bounded, disposable history of graphics-pipeline states that were actually used.
 *
 * <p>The history never owns native objects or render-pass handles. A replay is only
 * reconstructed after a live compatible RenderPass is supplied by an ordinary draw.
 * Production prewarming is opt-in until representative hardware hitch evidence exists;
 * smoke-test launches enable replay so CI can exercise cached-state reconstruction.</p>
 */
final class PipelineVariantPrewarmer {
    private static final int MAGIC = 0x56505632; // VPV2
    private static final int VERSION = 2;
    private static final int MAX_VARIANTS = 32;
    private static final int RECORD_INTS = 28;
    private static final int HEADER_BYTES = 12;
    private static final int RECORD_BYTES = RECORD_INTS * Integer.BYTES;
    private static final int MAX_ENTRY_BYTES = HEADER_BYTES + MAX_VARIANTS * RECORD_BYTES;
    private static final BoundedDiskCache CACHE = new BoundedDiskCache(
            FMLPaths.GAMEDIR.get().resolve("cache/vulkanmod/pipeline-variants-v2"),
            8 * 1024, 4L * 1024 * 1024, 512);
    private static final AtomicBoolean WARNED_IO = new AtomicBoolean();
    private static final AtomicBoolean WARNED_REPLAY = new AtomicBoolean();
    private static final AtomicBoolean SMOKE_REPLAY_LOGGED = new AtomicBoolean();

    private static final boolean PREWARM_ENABLED = Boolean.getBoolean("vulkanmod.pipelineVariantPrewarm")
            || Boolean.getBoolean("vulkanmod.smokeTest");
    private static final int PREWARM_MAX = intProperty(
            "vulkanmod.pipelineVariantPrewarmMaxVariants", 4, 1, 16);
    private static final long PREWARM_BUDGET_NANOS = (long)(doubleProperty(
            "vulkanmod.pipelineVariantPrewarmBudgetMs", 2.0D, 0.1D, 20.0D) * 1_000_000.0D);

    private PipelineVariantPrewarmer() {}

    static Session open(ByteBuffer vertexSpirv, ByteBuffer fragmentSpirv,
                        VertexFormat vertexFormat, InstanceVertexFormat instanceFormat) {
        // The experimental feature owns all history work. Disabled production paths do
        // not hash shader inputs, touch the filesystem, or allocate a history session.
        if(!PREWARM_ENABLED || !CompilationCache.enabled()) return null;
        byte[] key = identity(vertexSpirv, fragmentSpirv, vertexFormat, instanceFormat);
        return new Session(key, read(key));
    }

    static long prewarmBudgetNanos() { return PREWARM_BUDGET_NANOS; }

    static final class Session {
        private final byte[] key;
        private final List<Variant> loaded;
        private final ArrayList<Variant> observed;
        private final Set<ReplayBoundary> attempted = new HashSet<>();
        private boolean dirty;

        private Session(byte[] key, List<Variant> loaded) {
            this.key = key;
            this.loaded = List.copyOf(loaded);
            this.observed = new ArrayList<>(loaded);
        }

        void observe(PipelineState state, int topology, boolean depthClamp) {
            if(!CompilationCache.enabled() || state == null || state.renderPass == null) return;
            Variant variant = Variant.capture(state, topology, depthClamp);
            if(!variant.valid()) return;
            int old = observed.indexOf(variant);
            if(old >= 0 && old == observed.size() - 1) return;
            if(old >= 0) observed.remove(old);
            else if(observed.size() >= MAX_VARIANTS) observed.remove(0);
            observed.add(variant);
            dirty = true;
        }

        List<Replay> replays(PipelineState current, boolean depthClamp) {
            if(loaded.isEmpty() || current == null || current.renderPass == null) return List.of();
            Pass pass = Pass.capture(current);
            ReplayBoundary boundary = new ReplayBoundary(pass, current.cullState, depthClamp);
            if(!attempted.add(boundary)) return List.of();

            ArrayList<Replay> result = new ArrayList<>(Math.min(PREWARM_MAX, loaded.size()));
            for(int i = loaded.size() - 1; i >= 0 && result.size() < PREWARM_MAX; --i) {
                Variant variant = loaded.get(i);
                if(variant.depthClamp != depthClamp || variant.cull != current.cullState
                        || !variant.pass.equals(pass)) continue;
                PipelineState state = variant.replay(current);
                if(state != null) result.add(new Replay(state, variant.topology, variant.depthClamp));
            }
            return result;
        }

        void persist() {
            if(!dirty || !CompilationCache.enabled() || observed.isEmpty()) return;
            try {
                if(CACHE.write(key, encode(observed))) dirty = false;
            } catch(IOException | RuntimeException failure) {
                warnIo(failure);
            }
        }

        void replayFailed(Throwable failure) {
            if(WARNED_REPLAY.compareAndSet(false, true))
                Initializer.LOGGER.warn("Observed graphics-pipeline prewarm failed; lazy creation remains authoritative", failure);
        }

        void replayed(int count) {
            if(count > 0 && Boolean.getBoolean("vulkanmod.smokeTest")
                    && SMOKE_REPLAY_LOGGED.compareAndSet(false, true))
                Initializer.LOGGER.info("Graphics pipeline variant history replayed successfully in smoke test");
        }
    }

    record Replay(PipelineState state, int topology, boolean depthClamp) {}
    private record ReplayBoundary(Pass pass, boolean cull, boolean depthClamp) {}

    private record Pass(boolean hasColor, int colorFormat, boolean hasDepth, int depthFormat,
                        boolean hasStencil) {
        static Pass capture(PipelineState state) {
            Framebuffer framebuffer = state.renderPass.getFramebuffer();
            boolean hasColor = framebuffer.getColorAttachment() != null;
            boolean hasDepth = framebuffer.getDepthAttachment() != null;
            return new Pass(hasColor, hasColor ? framebuffer.getFormat() : VK_FORMAT_UNDEFINED,
                    hasDepth, hasDepth ? framebuffer.getDepthFormat() : VK_FORMAT_UNDEFINED,
                    framebuffer.hasStencilAttachment());
        }

        boolean valid() {
            return hasColor == (colorFormat != VK_FORMAT_UNDEFINED)
                    && hasDepth == (depthFormat != VK_FORMAT_UNDEFINED)
                    && (!hasStencil || hasDepth);
        }
    }

    private record Variant(
            Pass pass, int topology, boolean depthClamp, boolean cull,
            boolean blendEnabled, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, int blendOp,
            boolean depthTest, boolean depthMask, int depthFunction,
            boolean logicEnabled, int logicOp, int colorMask,
            boolean stencilEnabled, int stencilFunction, int stencilReference,
            int stencilCompareMask, int stencilWriteMask, int stencilFailOp,
            int stencilDepthFailOp, int stencilPassOp) {

        static Variant capture(PipelineState state, int topology, boolean depthClamp) {
            return new Variant(Pass.capture(state), topology, depthClamp, state.cullState,
                    state.blendState.enabled, state.blendState.srcRgbFactor, state.blendState.dstRgbFactor,
                    state.blendState.srcAlphaFactor, state.blendState.dstAlphaFactor, state.blendState.blendOp,
                    state.depthState.depthTest, state.depthState.depthMask, state.depthState.function,
                    state.logicOpState.enabled, state.logicOpState.getLogicOp(), state.colorMask.colorMask,
                    state.stencilState.enabled, state.stencilState.function, state.stencilState.reference,
                    state.stencilState.compareMask, state.stencilState.writeMask, state.stencilState.failOp,
                    state.stencilState.depthFailOp, state.stencilState.passOp);
        }

        PipelineState replay(PipelineState current) {
            if(!valid() || current == null || current.renderPass == null || !pass.equals(Pass.capture(current))
                    || cull != current.cullState || VRenderSystem.cull != current.cullState)
                return null;
            try {
                PipelineState.BlendState blend = new PipelineState.BlendState(
                        blendEnabled, srcRgb, dstRgb, srcAlpha, dstAlpha, blendOp);
                PipelineState.DepthState depth = new PipelineState.DepthState(
                        depthTest, depthMask, depthCompareToGl(depthFunction));
                PipelineState.LogicOpState logic = new PipelineState.LogicOpState(logicEnabled, logicOp);
                PipelineState.ColorMask color = new PipelineState.ColorMask(colorMask);
                PipelineState.StencilState stencil = new PipelineState.StencilState(
                        stencilEnabled, stencilCompareToGl(stencilFunction), stencilReference,
                        stencilCompareMask, stencilWriteMask, stencilOpToGl(stencilFailOp),
                        stencilOpToGl(stencilDepthFailOp), stencilOpToGl(stencilPassOp));
                PipelineState replay = new PipelineState(blend, depth, logic, color, current.renderPass, stencil);
                return replay.cullState == cull ? replay : null;
            } catch(RuntimeException invalid) {
                return null;
            }
        }

        boolean valid() {
            return pass != null && pass.valid() && validTopology(topology)
                    && validBlendFactor(srcRgb) && validBlendFactor(dstRgb)
                    && validBlendFactor(srcAlpha) && validBlendFactor(dstAlpha)
                    && validBlendOp(blendOp) && validDepthCompare(depthFunction)
                    && logicOp >= VK_LOGIC_OP_CLEAR && logicOp <= VK_LOGIC_OP_SET
                    && (colorMask & ~(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                    | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)) == 0
                    && validStencilCompare(stencilFunction)
                    && validStencilOp(stencilFailOp) && validStencilOp(stencilDepthFailOp)
                    && validStencilOp(stencilPassOp)
                    && (!stencilEnabled || pass.hasStencil);
        }
    }

    private static List<Variant> read(byte[] key) {
        try {
            byte[] bytes = CACHE.read(key);
            return decode(bytes);
        } catch(IOException | RuntimeException failure) {
            warnIo(failure);
            return List.of();
        }
    }

    private static byte[] encode(List<Variant> variants) {
        int start = Math.max(0, variants.size() - MAX_VARIANTS);
        int count = variants.size() - start;
        ByteBuffer out = ByteBuffer.allocate(HEADER_BYTES + count * RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MAGIC).putInt(VERSION).putInt(count);
        for(int i = start; i < variants.size(); ++i) {
            Variant value = variants.get(i);
            putBoolean(out, value.pass.hasColor).putInt(value.pass.colorFormat);
            putBoolean(out, value.pass.hasDepth).putInt(value.pass.depthFormat);
            putBoolean(out, value.pass.hasStencil);
            out.putInt(value.topology);
            putBoolean(out, value.depthClamp);
            putBoolean(out, value.cull);
            putBoolean(out, value.blendEnabled).putInt(value.srcRgb).putInt(value.dstRgb)
                    .putInt(value.srcAlpha).putInt(value.dstAlpha).putInt(value.blendOp);
            putBoolean(out, value.depthTest); putBoolean(out, value.depthMask); out.putInt(value.depthFunction);
            putBoolean(out, value.logicEnabled); out.putInt(value.logicOp).putInt(value.colorMask);
            putBoolean(out, value.stencilEnabled).putInt(value.stencilFunction).putInt(value.stencilReference)
                    .putInt(value.stencilCompareMask).putInt(value.stencilWriteMask).putInt(value.stencilFailOp)
                    .putInt(value.stencilDepthFailOp).putInt(value.stencilPassOp);
        }
        return out.array();
    }

    private static List<Variant> decode(byte[] bytes) {
        if(bytes == null || bytes.length < HEADER_BYTES || bytes.length > MAX_ENTRY_BYTES) return List.of();
        try {
            ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if(in.getInt() != MAGIC || in.getInt() != VERSION) return List.of();
            int count = in.getInt();
            if(count < 0 || count > MAX_VARIANTS || bytes.length != HEADER_BYTES + count * RECORD_BYTES)
                return List.of();
            ArrayList<Variant> variants = new ArrayList<>(count);
            for(int i = 0; i < count; ++i) {
                Pass pass = new Pass(getBoolean(in), in.getInt(), getBoolean(in), in.getInt(), getBoolean(in));
                Variant value = new Variant(pass, in.getInt(), getBoolean(in), getBoolean(in),
                        getBoolean(in), in.getInt(), in.getInt(), in.getInt(), in.getInt(), in.getInt(),
                        getBoolean(in), getBoolean(in), in.getInt(),
                        getBoolean(in), in.getInt(), in.getInt(),
                        getBoolean(in), in.getInt(), in.getInt(), in.getInt(), in.getInt(), in.getInt(),
                        in.getInt(), in.getInt());
                if(value.valid() && !variants.contains(value)) variants.add(value);
            }
            return List.copyOf(variants);
        } catch(RuntimeException malformed) {
            return List.of();
        }
    }

    private static ByteBuffer putBoolean(ByteBuffer buffer, boolean value) {
        return buffer.putInt(value ? 1 : 0);
    }

    private static boolean getBoolean(ByteBuffer buffer) {
        int value = buffer.getInt();
        if(value != 0 && value != 1) throw new IllegalArgumentException("Invalid cached boolean");
        return value != 0;
    }

    private static byte[] identity(ByteBuffer vertexSpirv, ByteBuffer fragmentSpirv,
                                   VertexFormat vertexFormat, InstanceVertexFormat instanceFormat) {
        MessageDigest digest = BoundedDiskCache.sha256();
        update(digest, "graphics-pipeline-variants-v2");
        update(digest, vertexSpirv);
        update(digest, fragmentSpirv);
        update(digest, vertexFormat.toString());
        update(digest, instanceFormat == null ? "no-instance-format" : instanceFormat.toString());
        return digest.digest();
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, ByteBuffer value) {
        ByteBuffer bytes = value.duplicate();
        digest.update(ByteBuffer.allocate(4).putInt(bytes.remaining()).array());
        digest.update(bytes);
    }

    private static boolean validTopology(int topology) {
        return topology == VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
                || topology == VK_PRIMITIVE_TOPOLOGY_LINE_LIST
                || topology == VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
    }

    private static boolean validBlendFactor(int factor) {
        return factor == VK_BLEND_FACTOR_ZERO || factor == VK_BLEND_FACTOR_ONE
                || factor == VK_BLEND_FACTOR_SRC_COLOR || factor == VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR
                || factor == VK_BLEND_FACTOR_DST_COLOR || factor == VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR
                || factor == VK_BLEND_FACTOR_SRC_ALPHA || factor == VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    }

    private static boolean validBlendOp(int op) {
        return op == VK_BLEND_OP_ADD || op == VK_BLEND_OP_MIN || op == VK_BLEND_OP_MAX
                || op == VK_BLEND_OP_SUBTRACT || op == VK_BLEND_OP_REVERSE_SUBTRACT;
    }

    private static boolean validDepthCompare(int op) {
        return op == VK_COMPARE_OP_LESS_OR_EQUAL || op == VK_COMPARE_OP_ALWAYS
                || op == VK_COMPARE_OP_GREATER || op == VK_COMPARE_OP_GREATER_OR_EQUAL
                || op == VK_COMPARE_OP_EQUAL;
    }

    private static boolean validStencilCompare(int op) {
        return op >= VK_COMPARE_OP_NEVER && op <= VK_COMPARE_OP_ALWAYS;
    }

    private static boolean validStencilOp(int op) {
        return op == VK_STENCIL_OP_ZERO || op == VK_STENCIL_OP_INVERT || op == VK_STENCIL_OP_KEEP
                || op == VK_STENCIL_OP_REPLACE || op == VK_STENCIL_OP_INCREMENT_AND_CLAMP
                || op == VK_STENCIL_OP_DECREMENT_AND_CLAMP || op == VK_STENCIL_OP_INCREMENT_AND_WRAP
                || op == VK_STENCIL_OP_DECREMENT_AND_WRAP;
    }

    private static int depthCompareToGl(int op) {
        return switch(op) {
            case VK_COMPARE_OP_LESS_OR_EQUAL -> 515;
            case VK_COMPARE_OP_ALWAYS -> 519;
            case VK_COMPARE_OP_GREATER -> 516;
            case VK_COMPARE_OP_GREATER_OR_EQUAL -> 518;
            case VK_COMPARE_OP_EQUAL -> 514;
            default -> throw new IllegalArgumentException("Unsupported cached depth comparison");
        };
    }

    private static int stencilCompareToGl(int op) {
        return switch(op) {
            case VK_COMPARE_OP_NEVER -> 512;
            case VK_COMPARE_OP_LESS -> 513;
            case VK_COMPARE_OP_EQUAL -> 514;
            case VK_COMPARE_OP_LESS_OR_EQUAL -> 515;
            case VK_COMPARE_OP_GREATER -> 516;
            case VK_COMPARE_OP_NOT_EQUAL -> 517;
            case VK_COMPARE_OP_GREATER_OR_EQUAL -> 518;
            case VK_COMPARE_OP_ALWAYS -> 519;
            default -> throw new IllegalArgumentException("Unsupported cached stencil comparison");
        };
    }

    private static int stencilOpToGl(int op) {
        return switch(op) {
            case VK_STENCIL_OP_ZERO -> 0;
            case VK_STENCIL_OP_INVERT -> 5386;
            case VK_STENCIL_OP_KEEP -> 7680;
            case VK_STENCIL_OP_REPLACE -> 7681;
            case VK_STENCIL_OP_INCREMENT_AND_CLAMP -> 7682;
            case VK_STENCIL_OP_DECREMENT_AND_CLAMP -> 7683;
            case VK_STENCIL_OP_INCREMENT_AND_WRAP -> 34055;
            case VK_STENCIL_OP_DECREMENT_AND_WRAP -> 34056;
            default -> throw new IllegalArgumentException("Unsupported cached stencil operation");
        };
    }

    private static void warnIo(Exception failure) {
        if(WARNED_IO.compareAndSet(false, true))
            Initializer.LOGGER.warn("Graphics-pipeline variant history unavailable; using ordinary lazy creation", failure);
    }

    private static int intProperty(String key, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(System.getProperty(key, Integer.toString(fallback))))); }
        catch(NumberFormatException ignored) { return fallback; }
    }

    private static double doubleProperty(String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(System.getProperty(key, Double.toString(fallback)));
            return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
        } catch(NumberFormatException ignored) { return fallback; }
    }
}
