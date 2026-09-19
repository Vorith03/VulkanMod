package net.vulkanmod.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VulkanImage;

/** Opt-in runtime oracle for Forge's RenderTarget stencil capability contract. */
public final class RenderTargetStencilSmokeTest {
    private RenderTargetStencilSmokeTest() {
    }

    public static void verify(RenderTarget mainTarget) {
        if(!Boolean.getBoolean("vulkanmod.smokeTest")) {
            return;
        }

        require(!mainTarget.isStencilEnabled(),
                "MainTarget unexpectedly reported stencil support before enableStencil()");

        boolean rejected = false;
        try {
            mainTarget.enableStencil();
        } catch(UnsupportedOperationException expected) {
            rejected = true;
        }

        require(rejected, "Swapchain MainTarget unexpectedly accepted Forge stencil enablement");
        require(!mainTarget.isStencilEnabled(),
                "Rejected MainTarget stencil enablement still marked it stencil-enabled");

        RenderTarget offscreen = new RenderTarget(true) { };
        try {
            offscreen.resize(8, 8, false);
            require(!offscreen.isStencilEnabled(),
                    "Fresh off-screen RenderTarget unexpectedly reported stencil support");

            offscreen.enableStencil();
            require(offscreen.isStencilEnabled(),
                    "Off-screen RenderTarget did not accept Forge stencil enablement");

            VulkanImage depthStencil = GlTexture.getVulkanImage(offscreen.getDepthTextureId());
            require(depthStencil != null, "Stencil-enabled RenderTarget has no Vulkan depth attachment");
            require(VulkanImage.hasStencilComponent(depthStencil.format),
                    "Stencil-enabled RenderTarget did not allocate a combined depth/stencil format");
            require(depthStencil.getImageView() != depthStencil.getAttachmentImageView(),
                    "Combined depth/stencil RenderTarget reused its attachment view for depth sampling");
        } finally {
            offscreen.destroyBuffers();
        }

        Initializer.LOGGER.info("Forge RenderTarget stencil capability smoke passed");
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
