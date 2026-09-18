package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distant Horizons 3.x on Minecraft 1.20.1 only exposes its native OpenGL
 * renderer. VulkanMod creates Minecraft's window with GLFW_NO_API, so letting
 * DH enter that renderer would make it consume OpenGL-only framebuffer/state
 * contracts that do not exist under Vulkan.
 *
 * Keep DH's level/data lifecycle and its once-per-frame maintenance alive, but
 * stop immediately before render-parameter setup and suppress the two vanilla
 * fade passes. This is intentionally a fail-closed compatibility boundary, not
 * a Vulkan implementation of Distant Horizons LOD rendering.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public abstract class DistantHorizonsClientApiMixin {
    @Unique
    private static boolean vulkanmod$distantHorizonsSuppressionLogged;

    @Inject(
            method = "renderLodLayer(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/seibel/distanthorizons/core/render/DhApiRenderProxy;getDeferTransparentRendering()Z",
                    ordinal = 0
            ),
            cancellable = true,
            require = 1
    )
    private void vulkanmod$skipUnsupportedLodRenderer(boolean renderingDeferredLayer, CallbackInfo ci) {
        vulkanmod$logDistantHorizonsSuppression();
        ci.cancel();
    }

    @Inject(method = "renderFadeOpaque()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void vulkanmod$skipUnsupportedOpaqueFade(CallbackInfo ci) {
        vulkanmod$logDistantHorizonsSuppression();
        ci.cancel();
    }

    @Inject(method = "renderFadeTransparent()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void vulkanmod$skipUnsupportedTransparentFade(CallbackInfo ci) {
        vulkanmod$logDistantHorizonsSuppression();
        ci.cancel();
    }

    @Unique
    private static void vulkanmod$logDistantHorizonsSuppression() {
        if(!vulkanmod$distantHorizonsSuppressionLogged) {
            vulkanmod$distantHorizonsSuppressionLogged = true;
            Initializer.LOGGER.warn(
                    "Distant Horizons OpenGL LOD rendering is unavailable under Vulkan; " +
                            "suppressing DH draw/fade passes while retaining DH data and render-thread maintenance");
        }
    }
}
