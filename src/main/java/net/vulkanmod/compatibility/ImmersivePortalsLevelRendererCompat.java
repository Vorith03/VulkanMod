package net.vulkanmod.compatibility;

import net.minecraft.client.Minecraft;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional bridge for Immersive Portals' multi-world LevelRenderer reload hook.
 *
 * VulkanMod cancels vanilla LevelRenderer.allChanged() before its original tail,
 * so IP's own TAIL injector cannot fan the reload out to secondary dimensions.
 * Invoke the same IP hook explicitly after VulkanMod rebuilds the active terrain
 * renderer. This class has no compile-time dependency on Immersive Portals.
 */
public final class ImmersivePortalsLevelRendererCompat {
    private static final String CLIENT_WORLD_LOADER =
            "qouteall.imm_ptl.core.ClientWorldLoader";

    private static boolean initialized;
    private static Method worldRendererReloaded;

    private ImmersivePortalsLevelRendererCompat() {
    }

    public static boolean isAvailable() {
        initialize();
        return worldRendererReloaded != null;
    }

    public static void afterWorldRendererReloaded(Minecraft minecraft) {
        if(minecraft == null || minecraft.level == null) {
            return;
        }

        initialize();
        if(worldRendererReloaded == null) {
            return;
        }

        try {
            worldRendererReloaded.invoke(null);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot access Immersive Portals world-renderer reload hook", e);
        } catch(InvocationTargetException e) {
            Throwable cause = e.getCause();
            if(cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if(cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Immersive Portals world-renderer reload hook failed", cause);
        }
    }

    private static synchronized void initialize() {
        if(initialized) {
            return;
        }

        initialized = true;
        try {
            Class<?> clazz = Class.forName(
                    CLIENT_WORLD_LOADER,
                    false,
                    ImmersivePortalsLevelRendererCompat.class.getClassLoader()
            );
            worldRendererReloaded = clazz.getMethod("_onWorldRendererReloaded");
        } catch(ClassNotFoundException ignored) {
            worldRendererReloaded = null;
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Unsupported Immersive Portals world-renderer reload API", e);
        }
    }
}
