package net.vulkanmod.render;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.CreateStencilCompat;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.MainTargetIdentity;
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

        require(mainTarget instanceof MainTarget,
                "Minecraft main target is not a MainTarget");
        require(MainTargetIdentity.isPrimary((MainTarget)mainTarget),
                "Minecraft main target was not classified as the swapchain target");
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

        verifyAuxiliaryMainTarget();

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
        verifyCreateStencilMixinTarget();
        Initializer.LOGGER.info("Forge RenderTarget stencil capability smoke passed");
    }

    private static void verifyAuxiliaryMainTarget() {
        MainTarget auxiliary = new MainTarget(8, 8);
        try {
            require(!MainTargetIdentity.isPrimary(auxiliary),
                    "Auxiliary MainTarget was incorrectly classified as the swapchain target");

            VulkanImage color = GlTexture.getVulkanImage(auxiliary.getColorTextureId());
            VulkanImage depth = GlTexture.getVulkanImage(auxiliary.getDepthTextureId());
            require(color != null && depth != null,
                    "Auxiliary MainTarget did not allocate Vulkan color/depth attachments");
            require(color != Vulkan.getSwapChain().getColorAttachment(),
                    "Auxiliary MainTarget color attachment aliased the swapchain");
            require(depth != Vulkan.getSwapChain().getDepthAttachment(),
                    "Auxiliary MainTarget depth attachment aliased the swapchain");
            require(color.width == 8 && color.height == 8,
                    "Auxiliary MainTarget did not preserve its requested off-screen extent");
        } finally {
            auxiliary.destroyBuffers();
        }
    }

    private static void verifyCreateStyleStencilStateBridge() {
        CreateStencilCompat.disableStencilTest(GL11.GL_STENCIL_TEST);
        require(!VRenderSystem.stencilTest, "Create stencil disable bridge did not reach Vulkan state");

        RenderSystem.stencilMask(~0);
        RenderSystem.stencilOp(GL11.GL_REPLACE, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_NEVER, 1, 0xFF);
        CreateStencilCompat.enableStencilTest(GL11.GL_STENCIL_TEST);

        require(VRenderSystem.stencilTest, "Create stencil enable bridge did not reach Vulkan state");
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

        CreateStencilCompat.disableStencilTest(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(~0);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, ~0);
    }

    private static void verifyCreateStencilMixinTarget() {
        if(!Boolean.getBoolean("vulkanmod.ciCreateStencilSmoke")) {
            return;
        }

        try {
            Class.forName("com.simibubi.create.foundation.gui.element.StencilElement", true,
                    Thread.currentThread().getContextClassLoader());
        } catch(ClassNotFoundException e) {
            throw new AssertionError("Create 0.5.1.j stencil smoke requested without Create on the runtime classpath", e);
        }

        Initializer.LOGGER.info("Create 0.5.1.j stencil compatibility mixin target loaded");
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
