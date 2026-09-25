package net.vulkanmod.mixin.compatibility;

import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distant Horizons' Forge AFTER_LEVEL event only caches the currently-bound
 * OpenGL framebuffer id for its native OpenGL renderer. VulkanMod creates a
 * GLFW_NO_API window and suppresses that renderer, so querying
 * GL_FRAMEBUFFER_BINDING here has no useful Vulkan meaning and can hard-abort
 * LWJGL because there is no current OpenGL context.
 *
 * Keep the rest of ForgeClientProxy's lifecycle/input/chunk events intact; only
 * this OpenGL-only render-event wrapper is skipped.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.forge.ForgeClientProxy", remap = false)
public abstract class DistantHorizonsForgeClientProxyMixin {
    @Unique
    private static boolean vulkanmod$distantHorizonsFramebufferQuerySuppressionLogged;

    @Inject(method = "afterLevelRenderEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void vulkanmod$skipUnsupportedFramebufferQuery(RenderLevelStageEvent event, CallbackInfo ci) {
        if(!vulkanmod$distantHorizonsFramebufferQuerySuppressionLogged) {
            vulkanmod$distantHorizonsFramebufferQuerySuppressionLogged = true;
            Initializer.LOGGER.warn(
                    "Distant Horizons Forge framebuffer query is unavailable under Vulkan; " +
                            "suppressing the OpenGL-only AFTER_LEVEL framebuffer probe");
        }
        ci.cancel();
    }
}
