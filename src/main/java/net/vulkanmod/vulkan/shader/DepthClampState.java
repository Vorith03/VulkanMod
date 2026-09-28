package net.vulkanmod.vulkan.shader;

/**
 * Tracks the optional Vulkan depth-clamp rasterization state used by compatibility
 * renderers such as Immersive Portals. The feature must be enabled when the
 * logical device is created before a graphics pipeline may request it.
 */
public final class DepthClampState {
    private static boolean supported;
    private static boolean enabled;

    private DepthClampState() {
    }

    public static void initialize(boolean isSupported) {
        supported = isSupported;
        enabled = false;
    }

    public static void enable() {
        enabled = supported;
    }

    public static void disable() {
        enabled = false;
    }

    public static boolean isSupported() {
        return supported;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
