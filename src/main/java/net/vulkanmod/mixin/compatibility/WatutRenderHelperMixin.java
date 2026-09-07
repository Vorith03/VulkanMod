package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * WATUT 1.20.1's dynamic GUI sharing path renders into OpenGL framebuffers and
 * reads them back with GL11.glReadPixels. VulkanMod owns a GLFW NO_API window,
 * so no OpenGL context exists and that native readback aborts the JVM.
 *
 * WATUT already has a supported fallback path when its dynamic GUI system is
 * unavailable. Disable only that OpenGL-only feature while VulkanMod is active;
 * the rest of WATUT remains loaded and functional.
 */
@Pseudo
@Mixin(targets = "com.corosus.watut.client.screen.RenderHelper", remap = false)
public abstract class WatutRenderHelperMixin {

    @Overwrite(remap = false)
    public static boolean useDynamicGUISystem() {
        return false;
    }
}
