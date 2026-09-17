package net.vulkanmod.mixin.debug;

import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** CI-only runtime target/signature smoke against the published Forge IP mod. */
@Mixin(Minecraft.class)
public abstract class ImmersivePortalsCompatSmokeMixin {
    private boolean vulkanmod$immersivePortalsSmokeRan;

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanmod$verifyImmersivePortalsCompatTargets(boolean tick, CallbackInfo ci) {
        if(this.vulkanmod$immersivePortalsSmokeRan || !Boolean.getBoolean("vulkanmod.ciImmersivePortalsSmoke")) {
            return;
        }
        this.vulkanmod$immersivePortalsSmokeRan = true;

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> queryManager = Class.forName("qouteall.imm_ptl.core.render.QueryManager", true, loader);
            Class<?> cHelper = Class.forName("qouteall.imm_ptl.core.CHelper", true, loader);
            Class<?> portalRenderer = Class.forName("qouteall.imm_ptl.core.render.PortalRenderer", true, loader);

            // Force-load every class with a VulkanMod IP redirect so Mixin verifies
            // the published 3.0.7 bytecode targets even though this smoke does not
            // need to create a real portal.
            Class.forName("qouteall.imm_ptl.core.render.RendererUsingFrameBuffer", true, loader);
            Class<?> frontClipping = Class.forName("qouteall.imm_ptl.core.render.FrontClipping", true, loader);
            Class.forName("qouteall.imm_ptl.core.render.ViewAreaRenderer", true, loader);
            Class.forName("qouteall.imm_ptl.core.render.MyRenderHelper", true, loader);

            AtomicBoolean rendered = new AtomicBoolean(false);
            Method query = queryManager.getMethod("renderAndGetDoesAnySamplePass", Runnable.class);
            Object result = query.invoke(null, (Runnable) () -> rendered.set(true));
            if(!rendered.get() || !Boolean.TRUE.equals(result)) {
                throw new IllegalStateException("Immersive Portals Vulkan query fallback did not render conservatively");
            }

            // These methods normally enter LWJGL OpenGL directly. The calls must
            // be harmless with VulkanMod's no-OpenGL-context window.
            cHelper.getMethod("doCheckGlError").invoke(null);
            cHelper.getMethod("enableDepthClamp").invoke(null);
            cHelper.getMethod("disableDepthClamp").invoke(null);

            Field clippingEnabled = frontClipping.getField("isClippingEnabled");
            clippingEnabled.setBoolean(null, true);
            frontClipping.getMethod("disableClipping").invoke(null);
            if(clippingEnabled.getBoolean(null)) {
                throw new IllegalStateException("Immersive Portals clipping bookkeeping was not preserved");
            }

            // Verify the default stencil mode is converted to IP's own framebuffer
            // compatibility renderer before the original selector chooses a renderer.
            Class<?> globalClass = Class.forName("qouteall.imm_ptl.core.IPGlobal", true, loader);
            Field renderMode = globalClass.getField("renderMode");
            Object current = renderMode.get(null);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object normal = Enum.valueOf((Class<? extends Enum>) current.getClass(), "normal");
            renderMode.set(null, normal);
            portalRenderer.getMethod("switchToCorrectRenderer").invoke(null);
            if(!"compatibility".equals(((Enum<?>) renderMode.get(null)).name())) {
                throw new IllegalStateException("Immersive Portals did not select framebuffer compatibility mode");
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Immersive Portals compatibility smoke invocation failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Immersive Portals compatibility smoke target was not available", e);
        }

        Initializer.LOGGER.info("Immersive Portals 3.0.7 compatibility mixin smoke passed");
    }
}
