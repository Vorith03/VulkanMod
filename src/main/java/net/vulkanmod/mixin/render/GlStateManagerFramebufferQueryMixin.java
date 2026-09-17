package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.vulkanmod.gl.GlFramebuffer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Provides the legacy framebuffer-binding query without touching OpenGL. */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerFramebufferQueryMixin {
    /**
     * @author VulkanMod Forge compatibility
     * @reason VulkanMod owns a GLFW_NO_API window, so glGetInteger on the GL
     * framebuffer binding is invalid. Report only framebuffer identities that
     * VulkanMod can prove from its active render pass and synthetic GL table.
     */
    @Overwrite(remap = false)
    public static int getBoundFramebuffer() {
        RenderPass renderPass = Renderer.getInstance().getBoundRenderPass();
        if(renderPass == null) {
            return GlFramebuffer.getBoundFramebufferId();
        }

        Framebuffer framebuffer = renderPass.getFramebuffer();
        if(framebuffer == Vulkan.getSwapChain()) {
            return 0;
        }

        int legacyId = GlFramebuffer.getBoundFramebufferId();
        if(legacyId != 0 && GlFramebuffer.isBoundFramebuffer(framebuffer)) {
            return legacyId;
        }

        throw new UnsupportedOperationException(
                "Active Vulkan off-screen RenderTarget has no legacy GL framebuffer id");
    }
}
