package net.vulkanmod.mixin.debug;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.ImmersivePortalsPortalMatrixCompat;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import net.vulkanmod.compatibility.ImmersivePortalsLevelRendererCompat;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.DepthClampState;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
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
            if(!ImmersivePortalsLevelRendererCompat.isAvailable()) {
                throw new IllegalStateException(
                        "Immersive Portals world-renderer reload hook is unavailable");
            }

            Class<?> queryManager = Class.forName("qouteall.imm_ptl.core.render.QueryManager", true, loader);
            Class<?> cHelper = Class.forName("qouteall.imm_ptl.core.CHelper", true, loader);
            Class<?> globalClass = Class.forName("qouteall.imm_ptl.core.IPGlobal", true, loader);
            Class<?> portalRenderer = Class.forName("qouteall.imm_ptl.core.render.PortalRenderer", true, loader);

            // Force-load every class with a VulkanMod IP redirect/injection so
            // Mixin verifies the published 3.0.7 bytecode targets even though
            // this smoke does not need to create a real portal.
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

            // VulkanMod cancels GameRenderer.reloadShaders at HEAD, so IP's own
            // RETURN injector cannot be trusted to populate these helper shaders.
            GraphicsPipeline portalAreaPipeline = null;
            ShaderInstance portalAreaShader = null;
            ShaderInstance framebufferAreaShader = null;
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
                if("portalAreaShader".equals(fieldName)) {
                    portalAreaPipeline = shaderMixed.getPipeline();
                    portalAreaShader = (ShaderInstance)shader;
                }
                else if("drawFbInAreaShader".equals(fieldName)) {
                    framebufferAreaShader = (ShaderInstance)shader;
                }
            }
            if(portalAreaPipeline == null || portalAreaShader == null || framebufferAreaShader == null) {
                throw new IllegalStateException("Immersive Portals portal-area Vulkan shaders were not installed");
            }

            // IP deliberately writes camera-relative matrices before apply(). The
            // converted legacy ShaderInstance path mirrors global RenderSystem
            // matrices during apply(), so verify our compatibility bridge restores
            // IP's explicit values before BufferUploader performs the UBO upload.
            verifyPortalMatrixRestore(portalAreaShader, "portalAreaShader");
            verifyPortalMatrixRestore(framebufferAreaShader, "drawFbInAreaShader");
            Initializer.LOGGER.info(
                    "VULKANMOD_IP_PORTAL_MATRIX_RESTORE_OK: explicit portal matrices survive converted ShaderInstance.apply");

            // These methods normally enter LWJGL OpenGL directly. The calls must
            // be harmless with VulkanMod's no-OpenGL-context window. Force IP's
            // clipping mechanism on so this also proves GL_DEPTH_CLAMP is translated
            // into the equivalent Vulkan rasterization state rather than discarded.
            Field clippingMechanism = globalClass.getDeclaredField("enableClippingMechanism");
            clippingMechanism.setAccessible(true);
            boolean oldClippingMechanism = clippingMechanism.getBoolean(null);
            try {
                clippingMechanism.setBoolean(null, true);
                cHelper.getMethod("doCheckGlError").invoke(null);
                cHelper.getMethod("enableDepthClamp").invoke(null);
                if(DepthClampState.isSupported() != DepthClampState.isEnabled()) {
                    throw new IllegalStateException(
                            "Immersive Portals depth clamp did not reach supported Vulkan rasterization state");
                }
                if(DepthClampState.isSupported()) {
                    // Bind an already-created IP compatibility pipeline while clamp
                    // is enabled. This forces GraphicsPipeline.getHandle() to create
                    // the depth-clamped VkPipeline variant without depending on
                    // BufferBuilder/Tesselator lifecycle details unrelated to clamp.
                    Renderer.getInstance().bindGraphicsPipeline(portalAreaPipeline);
                }
                cHelper.getMethod("disableDepthClamp").invoke(null);
                if(DepthClampState.isEnabled()) {
                    throw new IllegalStateException(
                            "Immersive Portals depth clamp disable did not restore Vulkan rasterization state");
                }
            } finally {
                DepthClampState.disable();
                clippingMechanism.setBoolean(null, oldClippingMechanism);
            }

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

            // Verify the default stencil mode is converted to IP's own framebuffer
            // compatibility renderer before the original selector chooses a renderer.
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

    private static void verifyPortalMatrixRestore(ShaderInstance shader, String shaderName) {
        Matrix4f expectedModelView = new Matrix4f()
                .translation(37.25f, -19.5f, 11.75f)
                .rotateXYZ(0.31f, -0.47f, 0.23f);
        Matrix4f expectedProjection = new Matrix4f()
                .scaling(0.625f, 1.375f, 0.875f)
                .translate(-4.5f, 8.25f, 2.0f);

        shader.MODEL_VIEW_MATRIX.set(expectedModelView);
        shader.PROJECTION_MATRIX.set(expectedProjection);
        shader.apply();
        try {
            if(matrixUniformMatches(shader.MODEL_VIEW_MATRIX, expectedModelView)
                    && matrixUniformMatches(shader.PROJECTION_MATRIX, expectedProjection)) {
                throw new IllegalStateException(
                        "Immersive Portals matrix smoke did not exercise the converted legacy apply overwrite for "
                                + shaderName);
            }

            ImmersivePortalsPortalMatrixCompat.restoreExplicitPortalMatrices(
                    expectedModelView, expectedProjection);

            if(!matrixUniformMatches(shader.MODEL_VIEW_MATRIX, expectedModelView)
                    || !matrixUniformMatches(shader.PROJECTION_MATRIX, expectedProjection)) {
                throw new IllegalStateException(
                        "Immersive Portals explicit portal matrices were not restored for " + shaderName);
            }
        } finally {
            shader.clear();
        }
    }

    private static boolean matrixUniformMatches(Uniform uniform, Matrix4f expected) {
        if(uniform == null) {
            return false;
        }

        float[] expectedValues = new float[16];
        expected.get(expectedValues);
        FloatBuffer actual = uniform.getFloatBuffer();
        if(actual == null || actual.capacity() < expectedValues.length) {
            return false;
        }

        for(int i = 0; i < expectedValues.length; ++i) {
            if(Float.floatToIntBits(actual.get(i)) != Float.floatToIntBits(expectedValues[i])) {
                return false;
            }
        }
        return true;
    }
}
