package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.VRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Bridges Mojang's clear-depth state wrapper to Vulkan.
 *
 * <p>VulkanMod already intercepts the corresponding clear call, but mods may
 * set the clear depth directly through GlStateManager before clearing an
 * off-screen RenderTarget. Allowing that wrapper to reach LWJGL is unsafe
 * because VulkanMod creates a GLFW_NO_API window with no OpenGL context.</p>
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerClearDepthMixin {
    /**
     * @author VulkanMod Forge compatibility
     * @reason Preserve clear-depth state without invoking OpenGL.
     */
    @Overwrite(remap = false)
    public static void _clearDepth(double depth) {
        RenderSystem.assertOnRenderThreadOrInit();
        VRenderSystem.clearDepth = (float) depth;
    }
}
