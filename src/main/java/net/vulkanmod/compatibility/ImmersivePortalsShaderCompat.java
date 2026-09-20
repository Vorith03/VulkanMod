package net.vulkanmod.compatibility;

import com.mojang.blaze3d.shaders.Program;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanmod.mixin.compatibility.ImmersivePortalsGameRendererInvoker;
import net.vulkanmod.vulkan.util.MappedBuffer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Optional bridge to Immersive Portals' shader source transformation without a
 * compile-time dependency on the mod.
 *
 * Immersive Portals normally reaches vanilla Program.compileShaderInternal via
 * a mixin redirect. VulkanMod owns the actual Vulkan shader compilation path,
 * so transformed source must be requested explicitly when IP is present.
 */
public final class ImmersivePortalsShaderCompat {
    private static final String TRANSFORM_CLASS =
            "qouteall.imm_ptl.core.render.ShaderCodeTransformation";
    private static final String RENDER_HELPER_CLASS =
            "qouteall.imm_ptl.core.render.MyRenderHelper";
    private static final String FRONT_CLIPPING_CLASS =
            "qouteall.imm_ptl.core.render.FrontClipping";
    private static final String CROSS_PORTAL_ENTITY_RENDERER_CLASS =
            "qouteall.imm_ptl.core.render.CrossPortalEntityRenderer";
    private static final String RENDER_STATES_CLASS =
            "qouteall.imm_ptl.core.render.context_management.RenderStates";

    // IP transforms terrain Position + ChunkOffset against the camera-relative,
    // pre-model-view equation.
    private static final Set<String> TERRAIN_CLIPPING_SHADERS = Set.of(
            "rendertype_solid",
            "rendertype_cutout",
            "rendertype_cutout_mipped",
            "rendertype_translucent"
    );

    // IP's second vanilla transformation group clips raw Position. Its normal
    // RenderSystem hook supplies the after-model-view equation while rendering
    // entities/projections, the pre-model-view equation for portal weather, and
    // disables the uniform otherwise. Aliased Forge shaders must preserve that
    // runtime selection instead of borrowing the terrain equation.
    private static final Set<String> MODEL_VIEW_CLIPPING_SHADERS = Set.of(
            "rendertype_entity_solid",
            "rendertype_entity_cutout",
            "rendertype_entity_cutout_no_cull",
            "rendertype_entity_cutout_no_cull_z_offset",
            "rendertype_item_entity_translucent_cull",
            "rendertype_entity_translucent_cull",
            "rendertype_entity_translucent",
            "rendertype_entity_smooth_cutout",
            "rendertype_beacon_beam",
            "rendertype_entity_translucent_emissive",
            "portal_area",
            "particle"
    );

    private static boolean initialized;
    private static boolean available;
    private static Method shouldAddUniform;
    private static Method transform;

    private static volatile boolean renderHelperReady;
    private static Object loadShaderSignal;
    private static Method emitShaderSignal;

    private static final MappedBuffer TERRAIN_CLIP_PLANE = new MappedBuffer(4 * Float.BYTES);
    private static final MappedBuffer MODEL_VIEW_CLIP_PLANE = new MappedBuffer(4 * Float.BYTES);
    private static boolean terrainClippingInitialized;
    private static boolean terrainClippingAvailable;
    private static java.lang.reflect.Field clippingEnabled;
    private static Method getActiveClipPlane;
    private static boolean modelViewClippingInitialized;
    private static boolean modelViewClippingAvailable;
    private static Method getActiveClipPlaneAfterModelView;
    private static java.lang.reflect.Field renderingEntityNormally;
    private static java.lang.reflect.Field renderingEntityProjection;
    private static java.lang.reflect.Field renderingPortalWeather;

    static {
        clearTerrainClipPlane();
        clearModelViewClipPlane();
    }

    private ImmersivePortalsShaderCompat() {
    }

    public static boolean isAvailable() {
        initialize();
        return available;
    }

    public static boolean shouldTransform(String shaderName) {
        initialize();
        if(!available) {
            return false;
        }

        try {
            return (boolean) shouldAddUniform.invoke(null, shaderName);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException("Cannot access Immersive Portals shader compatibility API", e);
        } catch(InvocationTargetException e) {
            throw propagate("Immersive Portals shader compatibility check failed", e);
        }
    }

    public static String transform(Program.Type type, String shaderName, String source) {
        initialize();
        if(!available) {
            return source;
        }

        try {
            return (String) transform.invoke(null, type, shaderName, source);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException("Cannot access Immersive Portals shader transformation API", e);
        } catch(InvocationTargetException e) {
            throw propagate("Immersive Portals shader transformation failed for " + shaderName, e);
        }
    }

    public static void markRenderHelperReady() {
        renderHelperReady = true;
    }

    /**
     * VulkanMod's custom terrain pipelines bypass Minecraft ShaderInstance, so
     * Immersive Portals cannot update them through IEShader. Mirror IP's active
     * camera-relative clip equation into a Vulkan UBO field. A positive constant
     * distance disables clipping when IP is absent or clipping is inactive.
     */
    public static MappedBuffer getTerrainClipPlane() {
        return TERRAIN_CLIP_PLANE;
    }

    public static boolean usesTerrainClipPlane(String shaderName) {
        return TERRAIN_CLIPPING_SHADERS.contains(shaderName);
    }

    public static MappedBuffer getModelViewClipPlane() {
        return MODEL_VIEW_CLIP_PLANE;
    }

    public static boolean usesModelViewClipPlane(String shaderName) {
        return MODEL_VIEW_CLIPPING_SHADERS.contains(shaderName);
    }

    public static void refreshTerrainClipPlane() {
        initializeTerrainClipping();
        if(!terrainClippingAvailable) {
            clearTerrainClipPlane();
            return;
        }

        try {
            if(!clippingEnabled.getBoolean(null)) {
                clearTerrainClipPlane();
                return;
            }

            Object value = getActiveClipPlane.invoke(null);
            if(!(value instanceof double[] equation) || equation.length < 4) {
                throw new IllegalStateException(
                        "Immersive Portals clipping is enabled without a valid terrain clip equation");
            }
            setTerrainClipPlane(
                    (float) equation[0],
                    (float) equation[1],
                    (float) equation[2],
                    (float) equation[3]
            );
        } catch(IllegalAccessException e) {
            throw new IllegalStateException("Cannot access Immersive Portals terrain clipping state", e);
        } catch(InvocationTargetException e) {
            throw propagate("Immersive Portals terrain clipping lookup failed", e);
        }
    }

    public static void clearTerrainClipPlane() {
        setClipPlane(TERRAIN_CLIP_PLANE, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static void refreshModelViewClipPlane() {
        initializeModelViewClipping();
        if(!modelViewClippingAvailable) {
            clearModelViewClipPlane();
            return;
        }

        try {
            if(!clippingEnabled.getBoolean(null)) {
                clearModelViewClipPlane();
                return;
            }

            boolean isRenderingEntity =
                    renderingEntityNormally.getBoolean(null)
                            || renderingEntityProjection.getBoolean(null);
            Object value;
            if(isRenderingEntity) {
                value = getActiveClipPlaneAfterModelView.invoke(null);
            }
            else if(renderingPortalWeather.getBoolean(null)) {
                value = getActiveClipPlane.invoke(null);
            }
            else {
                clearModelViewClipPlane();
                return;
            }

            if(!(value instanceof double[] equation) || equation.length < 4) {
                throw new IllegalStateException(
                        "Immersive Portals clipping is enabled without a valid model-view clip equation");
            }
            setClipPlane(
                    MODEL_VIEW_CLIP_PLANE,
                    (float) equation[0],
                    (float) equation[1],
                    (float) equation[2],
                    (float) equation[3]
            );
        } catch(IllegalAccessException e) {
            throw new IllegalStateException("Cannot access Immersive Portals model-view clipping state", e);
        } catch(InvocationTargetException e) {
            throw propagate("Immersive Portals model-view clipping lookup failed", e);
        }
    }

    public static void clearModelViewClipPlane() {
        setClipPlane(MODEL_VIEW_CLIP_PLANE, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    private static void setTerrainClipPlane(float x, float y, float z, float w) {
        setClipPlane(TERRAIN_CLIP_PLANE, x, y, z, w);
    }

    private static void setClipPlane(MappedBuffer target, float x, float y, float z, float w) {
        target.putFloat(0, x);
        target.putFloat(Float.BYTES, y);
        target.putFloat(2 * Float.BYTES, z);
        target.putFloat(3 * Float.BYTES, w);
    }

    /**
     * Rebuild VulkanMod's core shaders, then invoke Immersive Portals' shader
     * listener signal explicitly. VulkanMod cancels GameRenderer.reloadShaders
     * at HEAD, so IP's normal RETURN injection cannot reliably run by itself.
     */
    public static void rebuildShaders(Minecraft minecraft) {
        if(minecraft == null || minecraft.gameRenderer == null) {
            return;
        }

        ResourceProvider resourceProvider = minecraft.getResourceManager();
        ImmersivePortalsGameRendererInvoker bridge =
                (ImmersivePortalsGameRendererInvoker)(Object)minecraft.gameRenderer;

        bridge.vulkanmod$reloadShaders(resourceProvider);

        if(!renderHelperReady) {
            return;
        }

        emitPortalShaders(resourceProvider,
                shader -> bridge.vulkanmod$getShaders().put(shader.getName(), shader));
    }

    private static void emitPortalShaders(
            ResourceProvider resourceProvider,
            Consumer<ShaderInstance> resultConsumer) {
        initializeRenderHelperSignal();

        try {
            emitShaderSignal.invoke(loadShaderSignal, resourceProvider, resultConsumer);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException("Cannot access Immersive Portals shader loader signal", e);
        } catch(InvocationTargetException e) {
            throw propagate("Immersive Portals shader loader signal failed", e);
        }
    }

    private static synchronized void initialize() {
        if(initialized) {
            return;
        }

        initialized = true;
        try {
            Class<?> clazz = Class.forName(
                    TRANSFORM_CLASS,
                    false,
                    ImmersivePortalsShaderCompat.class.getClassLoader()
            );
            shouldAddUniform = clazz.getMethod("shouldAddUniform", String.class);
            transform = clazz.getMethod("transform", Program.Type.class, String.class, String.class);
            available = true;
        } catch(ClassNotFoundException ignored) {
            available = false;
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Immersive Portals shader compatibility API", e);
        }
    }

    private static synchronized void initializeTerrainClipping() {
        if(terrainClippingInitialized) {
            return;
        }

        terrainClippingInitialized = true;
        try {
            Class<?> clazz = Class.forName(
                    FRONT_CLIPPING_CLASS,
                    false,
                    ImmersivePortalsShaderCompat.class.getClassLoader()
            );
            clippingEnabled = clazz.getField("isClippingEnabled");
            getActiveClipPlane = clazz.getMethod("getActiveClipPlaneEquationBeforeModelView");
            terrainClippingAvailable = true;
        } catch(ClassNotFoundException ignored) {
            terrainClippingAvailable = false;
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Immersive Portals terrain clipping API", e);
        }
    }

    private static synchronized void initializeModelViewClipping() {
        if(modelViewClippingInitialized) {
            return;
        }

        modelViewClippingInitialized = true;
        initializeTerrainClipping();
        if(!terrainClippingAvailable) {
            modelViewClippingAvailable = false;
            return;
        }

        try {
            ClassLoader classLoader = ImmersivePortalsShaderCompat.class.getClassLoader();
            Class<?> frontClipping = Class.forName(FRONT_CLIPPING_CLASS, false, classLoader);
            Class<?> entityRenderer = Class.forName(
                    CROSS_PORTAL_ENTITY_RENDERER_CLASS, false, classLoader);
            Class<?> renderStates = Class.forName(RENDER_STATES_CLASS, false, classLoader);

            getActiveClipPlaneAfterModelView =
                    frontClipping.getMethod("getActiveClipPlaneEquationAfterModelView");
            renderingEntityNormally = entityRenderer.getField("isRenderingEntityNormally");
            renderingEntityProjection = entityRenderer.getField("isRenderingEntityProjection");
            renderingPortalWeather = renderStates.getField("isRenderingPortalWeather");
            modelViewClippingAvailable = true;
        } catch(ClassNotFoundException ignored) {
            modelViewClippingAvailable = false;
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Unsupported Immersive Portals model-view clipping API", e);
        }
    }

    private static synchronized void initializeRenderHelperSignal() {
        if(emitShaderSignal != null) {
            return;
        }

        try {
            Class<?> clazz = Class.forName(
                    RENDER_HELPER_CLASS,
                    false,
                    ImmersivePortalsShaderCompat.class.getClassLoader()
            );
            loadShaderSignal = clazz.getField("loadShaderSignal").get(null);
            emitShaderSignal = loadShaderSignal.getClass().getMethod("emit", Object.class, Object.class);
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Immersive Portals shader loader API", e);
        }
    }

    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if(cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if(cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(message, cause);
    }
}
