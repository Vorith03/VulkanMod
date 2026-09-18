package net.vulkanmod.mixin.debug;

import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** CI-only runtime target/signature smoke against Distant Horizons 3.2.0-b. */
@Mixin(Minecraft.class)
public abstract class DistantHorizonsCompatSmokeMixin {
    private boolean vulkanmod$distantHorizonsSmokeRan;

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanmod$verifyDistantHorizonsCompatTargets(boolean tick, CallbackInfo ci) {
        if(this.vulkanmod$distantHorizonsSmokeRan ||
                !Boolean.getBoolean("vulkanmod.ciDistantHorizonsSmoke")) {
            return;
        }

        this.vulkanmod$distantHorizonsSmokeRan = true;

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> clientApi = Class.forName(
                    "com.seibel.distanthorizons.core.api.internal.ClientApi", true, loader);
            Object instance = clientApi.getField("INSTANCE").get(null);

            // Force resolution of the normal/deferred entry points. Loading ClientApi
            // also makes Mixin verify the private renderLodLayer injection point used
            // by the production fail-closed bridge.
            clientApi.getMethod("renderLods");
            clientApi.getMethod("renderDeferredLodsForShaders");

            // These are safe to execute in the menu because VulkanMod cancels them at
            // HEAD. They prove the transformed published class is active without
            // allowing DH to enter any native OpenGL renderer.
            Method opaqueFade = clientApi.getMethod("renderFadeOpaque");
            Method transparentFade = clientApi.getMethod("renderFadeTransparent");
            opaqueFade.invoke(instance);
            transparentFade.invoke(instance);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Distant Horizons compatibility smoke invocation failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Distant Horizons compatibility smoke target was not available", e);
        }

        Initializer.LOGGER.info("Distant Horizons 3.2.0-b compatibility smoke passed");
    }
}
