package net.vulkanmod.mixin.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.util.MappedBuffer;
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

        // The Forge client setup task that loads IP's transformation table runs
        // after Minecraft's initial resource reload. Wait until that table is live
        // so this smoke validates the actual first-launch Vulkan compatibility path.
        if(!ImmersivePortalsShaderCompat.shouldTransform("rendertype_solid")) {
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
            Class<?> myRenderHelper = Class.forName("qouteall.imm_ptl.core.render.MyRenderHelper", true, loader);

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
            Field activeClipPlane = frontClipping.getDeclaredField(
                    "activeClipPlaneEquationBeforeModelView");
            activeClipPlane.setAccessible(true);
            activeClipPlane.set(null, new double[]{1.0, 2.0, 3.0, 4.0});
            clippingEnabled.setBoolean(null, false);
            Method enableClipping = frontClipping.getDeclaredMethod("enableClipping");
            enableClipping.setAccessible(true);
            enableClipping.invoke(null);
            if(!clippingEnabled.getBoolean(null)) {
                throw new IllegalStateException("Immersive Portals clipping enable bookkeeping was not preserved");
            }

            MappedBuffer terrainClipPlane = ImmersivePortalsShaderCompat.getTerrainClipPlane();
            if(terrainClipPlane.getFloat(0) != 1.0f
                    || terrainClipPlane.getFloat(Float.BYTES) != 2.0f
                    || terrainClipPlane.getFloat(2 * Float.BYTES) != 3.0f
                    || terrainClipPlane.getFloat(3 * Float.BYTES) != 4.0f) {
                throw new IllegalStateException(
                        "Immersive Portals clip equation did not reach Vulkan terrain uniforms");
            }

            frontClipping.getMethod("disableClipping").invoke(null);
            if(clippingEnabled.getBoolean(null)) {
                throw new IllegalStateException("Immersive Portals clipping bookkeeping was not preserved");
            }
            activeClipPlane.set(null, null);
            terrainClipPlane = ImmersivePortalsShaderCompat.getTerrainClipPlane();
            if(terrainClipPlane.getFloat(0) != 0.0f
                    || terrainClipPlane.getFloat(Float.BYTES) != 0.0f
                    || terrainClipPlane.getFloat(2 * Float.BYTES) != 0.0f
                    || terrainClipPlane.getFloat(3 * Float.BYTES) != 1.0f) {
                throw new IllegalStateException(
                        "Disabled Immersive Portals clipping did not restore no-clip terrain state");
            }

            // VulkanMod cancels GameRenderer.reloadShaders at HEAD, so IP's own
            // RETURN injector cannot be trusted to populate these helper shaders.
            for(String fieldName : new String[]{"drawFbInAreaShader", "portalAreaShader", "blitScreenNoBlendShader"}) {
                Object shader = myRenderHelper.getField(fieldName).get(null);
                if(shader == null) {
                    throw new IllegalStateException(
                            "Immersive Portals helper shader was not installed: " + fieldName);
                }
                if(!(shader instanceof ShaderMixed shaderMixed) || shaderMixed.getPipeline() == null) {
                    throw new IllegalStateException(
                            "Immersive Portals helper shader has no Vulkan pipeline: " + fieldName);
                }
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

            // Exercise the selected framebuffer renderer itself, not just its class
            // load. prepareRendering() is the first real compatibility-mode path and
            // reaches IP's direct GL_STENCIL_TEST disable on an unpatched build.
            Class<?> ipcGlobal = Class.forName("qouteall.imm_ptl.core.IPCGlobal", true, loader);
            Object selectedRenderer = ipcGlobal.getField("renderer").get(null);
            if(selectedRenderer == null ||
                    !"qouteall.imm_ptl.core.render.RendererUsingFrameBuffer".equals(
                            selectedRenderer.getClass().getName())) {
                throw new IllegalStateException(
                        "Immersive Portals did not install its framebuffer compatibility renderer");
            }
            selectedRenderer.getClass().getMethod("prepareRendering").invoke(selectedRenderer);

            // Immersive Portals creates one LevelRenderer per remote client
            // dimension. Construct and dispose a second renderer here so CI proves
            // VulkanMod terrain ownership is per renderer rather than a singleton.
            Minecraft minecraft = Minecraft.getInstance();
            LevelRenderer secondaryLevelRenderer = new LevelRenderer(
                    minecraft,
                    minecraft.getEntityRenderDispatcher(),
                    minecraft.getBlockEntityRenderDispatcher(),
                    minecraft.renderBuffers()
            );
            secondaryLevelRenderer.close();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Immersive Portals compatibility smoke invocation failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Immersive Portals compatibility smoke target was not available", e);
        }

        Initializer.LOGGER.info("Immersive Portals 3.0.7 compatibility mixin smoke passed");
    }
}
