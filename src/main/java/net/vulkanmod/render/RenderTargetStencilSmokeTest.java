package net.vulkanmod.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.vulkanmod.Initializer;

/** Opt-in runtime oracle for Forge's RenderTarget stencil capability contract. */
public final class RenderTargetStencilSmokeTest {
    private RenderTargetStencilSmokeTest() {
    }

    public static void verify(RenderTarget target) {
        if(!Boolean.getBoolean("vulkanmod.smokeTest")) {
            return;
        }

        require(!target.isStencilEnabled(),
                "RenderTarget unexpectedly reported stencil support before enableStencil()");

        boolean rejected = false;
        try {
            target.enableStencil();
        } catch(UnsupportedOperationException expected) {
            rejected = true;
        }

        require(rejected, "VulkanMod accepted Forge stencil enablement without a stencil attachment");
        require(!target.isStencilEnabled(),
                "Rejected stencil enablement still marked the RenderTarget stencil-enabled");
        Initializer.LOGGER.info("Forge RenderTarget stencil capability smoke passed");
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
