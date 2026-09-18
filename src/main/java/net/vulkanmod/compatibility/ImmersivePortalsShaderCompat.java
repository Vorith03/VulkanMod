package net.vulkanmod.compatibility;

import com.mojang.blaze3d.shaders.Program;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanmod.mixin.compatibility.ImmersivePortalsGameRendererInvoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    private static boolean initialized;
    private static boolean available;
    private static Method shouldAddUniform;
    private static Method transform;

    private static volatile boolean renderHelperReady;
    private static Object loadShaderSignal;
    private static Method emitShaderSignal;

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
