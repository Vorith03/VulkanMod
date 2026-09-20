package net.vulkanmod.compatibility;

import net.vulkanmod.vulkan.VRenderSystem;
import org.lwjgl.opengl.GL11;

/**
 * Call-site bridge for Create 0.5.1.j's raw GL_STENCIL_TEST toggles.
 *
 * LWJGL's GL11 class can be loaded before Sponge Mixin can reliably replace its
 * static entry points. Compatibility mixins must therefore redirect third-party
 * raw OpenGL calls here instead of relying on GL11M.
 */
public final class CreateStencilCompat {
    private CreateStencilCompat() {
    }

    public static void enableStencilTest(int capability) {
        requireStencilCapability(capability);
        VRenderSystem.enableStencilTest();
    }

    public static void disableStencilTest(int capability) {
        requireStencilCapability(capability);
        VRenderSystem.disableStencilTest();
    }

    private static void requireStencilCapability(int capability) {
        if(capability != GL11.GL_STENCIL_TEST) {
            throw new IllegalArgumentException("Create stencil bridge received unexpected GL capability: " + capability);
        }
    }
}
