package net.vulkanmod.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.opengl.GL11;

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

        verifyCreateStyleStencilStateBridge();
        Initializer.LOGGER.info("Forge RenderTarget stencil capability smoke passed");
    }

    private static void verifyCreateStyleStencilStateBridge() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        require(!VRenderSystem.stencilTest, "Direct GL stencil disable did not reach Vulkan state");

        RenderSystem.stencilMask(~0);
        RenderSystem.stencilOp(GL11.GL_REPLACE, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_NEVER, 1, 0xFF);
        GL11.glEnable(GL11.GL_STENCIL_TEST);

        require(VRenderSystem.stencilTest, "Direct GL stencil enable did not reach Vulkan state");
        require(GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                "Direct GL stencil enabled query did not reflect Vulkan state");
        require(VRenderSystem.stencilWriteMask == 0xFF,
                "RenderSystem stencil write mask did not reach Vulkan state");
        require(VRenderSystem.stencilFun == GL11.GL_NEVER
                        && VRenderSystem.stencilRef == 1
                        && VRenderSystem.stencilCompareMask == 0xFF,
                "RenderSystem stencil comparison state did not reach Vulkan state");
        require(VRenderSystem.stencilFailOp == GL11.GL_REPLACE
                        && VRenderSystem.stencilDepthFailOp == GL11.GL_KEEP
                        && VRenderSystem.stencilPassOp == GL11.GL_KEEP,
                "RenderSystem stencil operations did not reach Vulkan state");

        // PipelineState construction performs the OpenGL-to-Vulkan conversion
        // used by the actual draw path; this must accept Create's exact sequence.
        VRenderSystem.getStencilState();

        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(~0);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, ~0);
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
