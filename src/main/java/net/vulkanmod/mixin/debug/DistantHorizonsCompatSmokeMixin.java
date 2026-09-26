package net.vulkanmod.mixin.debug;

import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

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
            Class<?> forgeRenderWrapper = Class.forName(
                    "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper_forge",
                    false, loader);

            // Load/transform the Forge proxy without initializing it. Its static
            // PACKET_SENDER is populated through DH's dependency injector during the
            // normal Forge client lifecycle; initializing this class from the early
            // CI menu smoke would cache null and poison the later client-setup event.
            // Loading with initialize=false is still sufficient for Mixin to transform
            // the published class, so require=1 on the production mixin verifies the
            // exact afterLevelRenderEvent target without perturbing DH initialization.
            Class<?> forgeClientProxy = Class.forName(
                    "com.seibel.distanthorizons.forge.ForgeClientProxy", false, loader);

            // Class loading applies the exact Forge lightmap guard. require=1 on
            // that mixin rejects DH versions with a missing updateLightmap target.
            if(Arrays.stream(forgeRenderWrapper.getDeclaredMethods())
                    .noneMatch(method -> method.getName().equals("updateLightmap"))) {
                throw new NoSuchMethodException("Distant Horizons Forge updateLightmap");
            }
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

            // The full Create Chronicles run reached this Forge wrapper after the
            // LOD draw had already been suppressed, then hard-aborted in
            // GL11.glGetInteger because VulkanMod has no OpenGL context. Do not invoke
            // this method from the early CI hook: constructing ForgeClientProxy would
            // initialize its dependency-injected static fields before DH is ready.
            // Instead, resolve the transformed method while leaving class initialization
            // to DH's normal lifecycle. The production mixin's require=1 makes a missing
            // or changed target fail class transformation rather than silently passing.
            Arrays.stream(forgeClientProxy.getDeclaredMethods())
                    .filter(method -> method.getName().equals("afterLevelRenderEvent"))
                    .filter(method -> method.getParameterCount() == 1)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException(
                            "Distant Horizons Forge afterLevelRenderEvent"));
            Initializer.LOGGER.info(
                    "Distant Horizons Forge afterLevelRenderEvent compatibility target verified without early class initialization");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Distant Horizons compatibility smoke invocation failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Distant Horizons compatibility smoke target was not available", e);
        }

        Initializer.LOGGER.info("Distant Horizons 3.2.0-b compatibility smoke passed");
    }
}
