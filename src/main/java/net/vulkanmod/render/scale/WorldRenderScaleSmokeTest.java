package net.vulkanmod.render.scale;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.mixin.render.GameRendererPostEffectAccessor;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;
import java.util.concurrent.TimeUnit;
import static org.lwjgl.vulkan.VK10.*;

/** Real low-resolution color/depth, post effects, upscale and native overlay pixels. */
public final class WorldRenderScaleSmokeTest {
    private WorldRenderScaleSmokeTest() {}
    public static void verify(Minecraft minecraft) throws Exception {
        RenderTarget main = minecraft.getMainRenderTarget();
        int width = main.width, height = main.height;
        Renderer renderer = Renderer.getInstance();
        try {
            for(double scale : new double[]{0.5, 0.75}) {
                renderer.resetBuffers(); renderer.beginFrame();
                WorldRenderScale.beginCapture(main, scale);
                if(main.width != (scale == 0.5 ? (width+1)/2 : (width*3+3)/4)
                        || GlTexture.getVulkanImage(main.getDepthTextureId()) != WorldRenderScale.depth()
                        || GlTexture.getVulkanImage(main.getColorTextureId()) != WorldRenderScale.color())
                    throw new AssertionError("Scaled primary attachment/extent routing failed");
                clearRect(0,0,main.width,main.height/2,1,0,0);
                clearRect(0,main.height/2,main.width,main.height-main.height/2,0,0,1);
                main.unbindWrite(); main.bindWrite(true);
                var worldIcon = ScreenshotReadback.request(main);
                WorldRenderScale.endCapture(true);
                verifyRestored(main,width,height);
                clearRect(3,3,1,1,0,1,0);
                var capture = ScreenshotReadback.request(main);
                renderer.endFrame();
                try(NativeImage image = worldIcon.get(10,TimeUnit.SECONDS)) {
                    if(image.getWidth() != width || image.getPixelRGBA(3,3) != 0xFF0000FF)
                        throw new AssertionError(String.format("World icon differs: extent=%dx%d expected=%dx%d pixel=%08x top=%08x bottom=%08x",
                                image.getWidth(),image.getHeight(),width,height,image.getPixelRGBA(3,3),
                                image.getPixelRGBA(width/2,height/4),image.getPixelRGBA(width/2,3*height/4)));
                }
                try(NativeImage image = capture.get(10, TimeUnit.SECONDS)) {
                    if(image.getWidth() != width || image.getHeight() != height
                            || image.getPixelRGBA(width/2,height/4) != 0xFF0000FF
                            || image.getPixelRGBA(width/2,3*height/4) != 0xFFFF0000
                            || image.getPixelRGBA(3,3) != 0xFF00FF00
                            || image.getPixelRGBA(4,3) != 0xFF0000FF)
                        throw new AssertionError(String.format("Upscale/native overlay differs: scale=%.2f extent=%dx%d top=%08x bottom=%08x overlay=%08x neighbor=%08x",
                                scale,image.getWidth(),image.getHeight(),image.getPixelRGBA(width/2,height/4),
                                image.getPixelRGBA(width/2,3*height/4),image.getPixelRGBA(3,3),image.getPixelRGBA(4,3)));
                }
            }
            renderer.resetBuffers(); renderer.beginFrame();
            WorldRenderScale.beginCapture(main,0.5);
            var cameraEffects = (GameRendererPostEffectAccessor)minecraft.gameRenderer;
            PostChain previousEffect = cameraEffects.vulkanmod$getPostEffect();
            try(PostChain chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                    main, new ResourceLocation("shaders/post/creeper.json"))) {
                cameraEffects.vulkanmod$setPostEffect(chain);
                WorldRenderScale.resizeChains(minecraft,main.width,main.height);
                // An external native resize must invalidate a previously cached
                // scale extent, even when rounded world dimensions do not change.
                chain.resize(width,height);
                WorldRenderScale.resizeChains(minecraft,main.width,main.height);
                if(chain.getTempTarget("swap").width != main.width
                        || chain.getTempTarget("swap").height != main.height)
                    throw new AssertionError("External resize retained stale post-chain extents");
                clearRect(0,0,main.width,main.height,1,0,0);
                chain.process(0);
                WorldRenderScale.endCapture(true);
                var capture = ScreenshotReadback.request(main);
                renderer.endFrame();
                try(NativeImage image = capture.get(10,TimeUnit.SECONDS)) {
                    int pixel = image.getPixelRGBA(width/2,height/2);
                    int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                    if(g < 16 || g <= r+8 || g <= b+8)
                        throw new AssertionError("Scaled creeper effect did not produce green output");
                }
            } finally { cameraEffects.vulkanmod$setPostEffect(previousEffect); }
            renderer.resetBuffers(); renderer.beginFrame();
            WorldRenderScale.beginCapture(main,0.75);
            try(PostChain chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                    main, new ResourceLocation("shaders/post/transparency.json"))) {
                chain.resize(main.width,main.height);
                VRenderSystem.clearColor(0.25f,0.5f,0.75f,1);
                VRenderSystem.clearDepth = 0.625f;
                Renderer.clearAttachments(0x4100);
                for(String name : new String[]{"translucent", "itemEntity", "particles", "clouds", "weather"}) {
                    RenderTarget temp = chain.getTempTarget(name);
                    temp.setClearColor(0,0,0,0); temp.clear(Minecraft.ON_OSX);
                    temp.copyDepthFrom(main);
                }
                main.bindWrite(true); chain.process(0);
                WorldRenderScale.endCapture(true);
                verifyRestored(main,width,height);
                renderer.endFrame(); Vulkan.waitIdle(); // Oracle only, never a production readback/wait.
            }
            renderer.resetBuffers(); renderer.beginFrame();
            WorldRenderScale.beginCapture(main,0.5);
            var abortedIcon = ScreenshotReadback.request(main);
            WorldRenderScale.endCapture(false);
            if(!abortedIcon.isCompletedExceptionally()) throw new AssertionError("Aborted world icon was retained");
            verifyRestored(main,width,height);
            renderer.endFrame(); Vulkan.waitIdle();
            Initializer.LOGGER.info("World render scale native smoke passed: extents, color/depth routing, resized upscale pixels, native overlay, color/depth effects, abort restore");
        } finally {
            if(WorldRenderScale.active()) WorldRenderScale.endCapture(false);
            WorldRenderScale.close(); VRenderSystem.clearDepth = 1;
        }
    }
    private static void verifyRestored(RenderTarget main, int width, int height) {
        if(WorldRenderScale.active() || main.width != width || main.height != height
                || GlTexture.getVulkanImage(main.getDepthTextureId()) != Vulkan.getSwapChain().getDepthAttachment())
            throw new AssertionError("Capture did not restore native main identity/extent/depth");
    }
    private static void clearRect(int x, int y, int w, int h, float r, float g, float b) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkClearAttachment.Buffer color = VkClearAttachment.calloc(1,stack);
            color.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).colorAttachment(0);
            color.clearValue().color().float32(stack.floats(r,g,b,1));
            VkClearRect.Buffer rect = VkClearRect.calloc(1,stack);
            rect.layerCount(1); rect.rect().offset().set(x,y); rect.rect().extent().set(w,h);
            vkCmdClearAttachments(Renderer.getCommandBuffer(),color,rect);
        }
    }
}
