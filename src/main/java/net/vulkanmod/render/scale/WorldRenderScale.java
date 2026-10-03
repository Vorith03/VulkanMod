package net.vulkanmod.render.scale;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.ImmersivePortalsLevelRendererCompat;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.mixin.render.GameRendererPostEffectAccessor;
import net.vulkanmod.mixin.render.LevelRendererPostChainsAccessor;
import net.vulkanmod.mixin.render.RenderTargetTextureNamesAccessor;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;

import java.util.Map;
import java.util.WeakHashMap;

import static org.lwjgl.opengl.GL11.GL_LINEAR;

/** One primary world capture on the primary command buffer, followed by native GUI. */
public final class WorldRenderScale {
    private static TextureTarget target;
    private static RenderTarget main;
    private static boolean active;
    private static int width, height, viewWidth, viewHeight;
    private static final Map<PostChain, Long> chainExtents = new WeakHashMap<>();
    private static boolean warnedPortals;
    private WorldRenderScale() {}

    public static boolean active() { return active; }
    public static RenderTarget target() { return active ? target : null; }
    public static VulkanImage color() { return GlTexture.getVulkanImage(target.getColorTextureId()); }
    public static VulkanImage depth() { return GlTexture.getVulkanImage(target.getDepthTextureId()); }
    public static int renderWidth(int fallback) { return active ? target.width : fallback; }
    public static int renderHeight(int fallback) { return active ? target.height : fallback; }

    public static boolean begin(Minecraft minecraft) {
        double scale = RenderScaleDimensions.clamp(Initializer.CONFIG.worldRenderScale);
        if(scale >= 1 || minecraft.level == null || Renderer.skipRendering) {
            restoreChains(minecraft);
            return false;
        }
        if(ImmersivePortalsLevelRendererCompat.isAvailable()) {
            if(!warnedPortals) {
                warnedPortals = true;
                Initializer.LOGGER.info("World render scaling retains native resolution with Immersive Portals until multi-view target ownership is qualified");
            }
            restoreChains(minecraft);
            return false;
        }
        beginCapture(minecraft.getMainRenderTarget(), scale);
        try {
            resizeChains(minecraft, target.width, target.height);
            return true;
        } catch(RuntimeException | Error failure) {
            endCapture(false);
            throw failure;
        }
    }

    /** Native oracle uses the same ownership path with no synthetic game world. */
    public static void beginCapture(RenderTarget primary, double scale) {
        RenderSystem.assertOnRenderThread();
        if(!(primary instanceof com.mojang.blaze3d.pipeline.MainTarget primaryMain)
                || !net.vulkanmod.vulkan.framebuffer.MainTargetIdentity.isPrimary(primaryMain))
            throw new IllegalArgumentException("World scale capture requires Minecraft's primary MainTarget");
        if(active || !Renderer.getInstance().isRecordingFrame())
            throw new IllegalStateException("World scale capture requires one recording primary frame");
        int scaledWidth = RenderScaleDimensions.extent(primary.width, scale);
        int scaledHeight = RenderScaleDimensions.extent(primary.height, scale);
        if(target == null) {
            target = new TextureTarget(scaledWidth, scaledHeight, true, Minecraft.ON_OSX);
            target.setFilterMode(GL_LINEAR);
        } else if(target.width != scaledWidth || target.height != scaledHeight) {
            target.resize(scaledWidth, scaledHeight, Minecraft.ON_OSX);
        }
        main = primary;
        width = primary.width; height = primary.height;
        viewWidth = primary.viewWidth; viewHeight = primary.viewHeight;
        active = true;
        primary.width = primary.viewWidth = scaledWidth;
        primary.height = primary.viewHeight = scaledHeight;
        try {
            remapNames(primary, color(), depth());
            target.bindWrite(true);
            Renderer.clearAttachments(0x4100);
        } catch(RuntimeException | Error failure) {
            endCapture(false);
            throw failure;
        }
    }

    public static void endCapture(boolean composite) {
        if(!active) throw new IllegalStateException("No world scale capture to finish");
        RenderTarget primary = main;
        active = false;
        main = null;
        primary.width = width; primary.height = height;
        primary.viewWidth = viewWidth; primary.viewHeight = viewHeight;
        remapNames(primary, Vulkan.getSwapChain().getColorAttachment(), Vulkan.getSwapChain().getDepthAttachment());
        primary.bindWrite(true); // Transition low-resolution color on this same primary buffer.
        if(!composite) { ScreenshotReadback.resolveWorldPending(false); return; }
        boolean oldDepth = VRenderSystem.depthTest, oldMask = VRenderSystem.depthMask;
        boolean oldCull = VRenderSystem.cull, oldBlend = PipelineState.blendInfo.enabled;
        boolean oldStencil = VRenderSystem.stencilTest;
        int oldColorMask = VRenderSystem.colorMask;
        var oldShader = RenderSystem.getShader();
        float[] oldShaderColor = RenderSystem.getShaderColor().clone();
        RenderSystem.backupProjectionMatrix();
        boolean composited = false;
        try {
            VRenderSystem.cull = false;
            VRenderSystem.stencilTest = false;
            RenderSystem.setShaderColor(1, 1, 1, 1);
            target.blitToScreen(width, height, true);
            composited = true;
        } finally {
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(() -> oldShader);
            RenderSystem.setShaderColor(oldShaderColor[0], oldShaderColor[1], oldShaderColor[2], oldShaderColor[3]);
            VRenderSystem.depthTest = oldDepth;
            VRenderSystem.depthMask = oldMask;
            VRenderSystem.cull = oldCull;
            VRenderSystem.stencilTest = oldStencil;
            PipelineState.blendInfo.enabled = oldBlend;
            VRenderSystem.colorMask = oldColorMask;
            ScreenshotReadback.resolveWorldPending(composited);
        }
    }

    private static void remapNames(RenderTarget primary, VulkanImage color, VulkanImage depth) {
        var names = (RenderTargetTextureNamesAccessor)primary;
        int colorName = names.vulkanmod$getColorTextureName();
        int depthName = names.vulkanmod$getDepthTextureName();
        if(colorName > 0) GlTexture.setVulkanImage(colorName, color);
        if(depthName > 0) GlTexture.setVulkanImage(depthName, depth);
    }

    /** Only remap a full native viewport while the owned world target is bound. */
    public static boolean remapViewport(int x, int y, int requestedWidth, int requestedHeight) {
        return active && x == 0 && y == 0 && requestedWidth == width && requestedHeight == height
                && Renderer.getInstance().getBoundRenderPass() != null
                && Renderer.getInstance().getBoundRenderPass().getFramebuffer().getColorAttachment() == color();
    }

    private static void restoreChains(Minecraft minecraft) {
        RenderTarget primary = minecraft.getMainRenderTarget();
        if(!chainExtents.isEmpty()) resizeChains(minecraft, primary.width, primary.height);
        chainExtents.clear();
        if(target != null) { target.destroyBuffers(); target = null; }
    }

    private static void resizeChains(Minecraft minecraft, int w, int h) {
        var level = (LevelRendererPostChainsAccessor)minecraft.levelRenderer;
        for(PostChain chain : new PostChain[]{((GameRendererPostEffectAccessor)minecraft.gameRenderer).vulkanmod$getPostEffect(),
                level.vulkanmod$getEntityEffect(), level.vulkanmod$getTransparencyChain()}) {
            if(chain == null) continue;
            long extent = ((long)w << 32) | (h & 0xffffffffL);
            if(!Long.valueOf(extent).equals(chainExtents.get(chain))) {
                chain.resize(w, h);
                chainExtents.put(chain, extent);
            }
        }
    }

    public static void close() {
        if(active) throw new IllegalStateException("Cannot close a recording world scale target");
        if(target != null) { target.destroyBuffers(); target = null; }
        chainExtents.clear();
    }
}
