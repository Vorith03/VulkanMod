package net.vulkanmod.gl;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.vulkan.VK10.*;

/** Transformed GlStateManager routing and borrowed-attachment pixel/lifetime oracle. */
public final class LegacyFramebufferSmokeTest {
    private LegacyFramebufferSmokeTest() {}

    public static void verify() throws Exception {
        Renderer renderer = Renderer.getInstance();
        TextureTarget first = new TextureTarget(8, 6, true, Minecraft.ON_OSX);
        TextureTarget second = new TextureTarget(4, 3, true, Minecraft.ON_OSX);
        int id = GlStateManager.glGenFramebuffers();
        try {
            if(id <= 0) throw new AssertionError("Generated default framebuffer alias");
            expectUnsupported(GlStateManager::glGenRenderbuffers);
            renderer.resetBuffers();
            renderer.beginFrame();
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, id);
            status(GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT);
            attach(GL_COLOR_ATTACHMENT0, first.getColorTextureId());
            status(GL_FRAMEBUFFER_COMPLETE);
            Framebuffer borrowed = renderer.getBoundRenderPass().getFramebuffer();
            if(borrowed.getColorAttachment() != GlTexture.getVulkanImage(first.getColorTextureId())
                    || borrowed.getDepthAttachment() != null)
                throw new AssertionError("Legacy FBO did not borrow the specified color-only storage");
            clear(8, 6, 1, 0, 0);
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, 0);
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, id);
            if(renderer.getBoundRenderPass().getFramebuffer() != borrowed)
                throw new AssertionError("Unchanged legacy FBO reallocated on rebind");
            attach(GL_DEPTH_ATTACHMENT, first.getDepthTextureId());
            status(GL_FRAMEBUFFER_COMPLETE);
            var red = ScreenshotReadback.request(first); // Rebind/reattach must LOAD existing red pixels.

            attach(GL_DEPTH_ATTACHMENT, second.getDepthTextureId());
            status(GL_FRAMEBUFFER_UNSUPPORTED);
            if(renderer.getBoundRenderPass() != null)
                throw new AssertionError("Incomplete FBO retained an unrelated active pass");
            attach(GL_DEPTH_ATTACHMENT, 0);
            attach(GL_COLOR_ATTACHMENT0, second.getColorTextureId());
            clear(4, 3, 0, 0, 1);
            var blue = ScreenshotReadback.request(second);

            int incompleteId = GlStateManager.glGenFramebuffers();
            try {
                GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, incompleteId);
                status(GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT);
                if(renderer.getBoundRenderPass() != null)
                    throw new AssertionError("Incomplete FBO retained an active pass");
                GlStateManager._glDeleteFramebuffers(incompleteId);
                if(renderer.getBoundRenderPass() == null
                        || renderer.getBoundRenderPass().getFramebuffer() != Vulkan.getSwapChain()
                        || GlStateManager.getBoundFramebuffer() != 0)
                    throw new AssertionError("Deleting incomplete FBO did not restore framebuffer zero");
            } finally {
                GlStateManager._glDeleteFramebuffers(incompleteId);
            }

            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, id);
            attach(GL_COLOR_ATTACHMENT0, first.getColorTextureId());
            attach(GL_DEPTH_ATTACHMENT, first.getDepthTextureId());
            first.resize(12, 10, Minecraft.ON_OSX); // Borrowed VkFramebuffer must retire before its views.
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, id);
            status(GL_FRAMEBUFFER_COMPLETE);
            if(renderer.getBoundRenderPass().getFramebuffer().getWidth() != 12)
                throw new AssertionError("Resized attachment left stale legacy framebuffer backing");
            clear(12, 10, 0, 1, 0);
            var green = ScreenshotReadback.request(first);
            GlStateManager._glDeleteFramebuffers(id);
            GlStateManager._glDeleteFramebuffers(id); // Idempotent and intercepted; no OpenGL context exists.
            if(GlFramebuffer.getBoundFramebufferId() != 0)
                throw new AssertionError("Deleted legacy framebuffer remained bound");
            status(GL_FRAMEBUFFER_COMPLETE);
            renderer.endFrame();
            pixels(red, 8, 6, 0xFF0000FF);
            pixels(blue, 4, 3, 0xFFFF0000);
            pixels(green, 12, 10, 0xFF00FF00);

            // Borrowed deletion must not destroy either target's owned textures.
            renderer.resetBuffers();
            renderer.beginFrame();
            first.bindWrite(true);
            clear(12, 10, 1, 0, 1);
            var survivor = ScreenshotReadback.request(first);
            first.unbindWrite();
            renderer.endFrame();
            pixels(survivor, 12, 10, 0xFFFF00FF);
            Initializer.LOGGER.info("Legacy framebuffer Vulkan smoke passed: transformed GL hooks, real texture attachments, LOAD pixels, incomplete status, resize retirement, borrowed deletion");
        } finally {
            Vulkan.waitIdle(); // Oracle-only synchronization; never added to production bindings.
            GlStateManager._glDeleteFramebuffers(id);
            first.destroyBuffers();
            second.destroyBuffers();
        }
    }

    private static void attach(int attachment, int texture) {
        GlStateManager._glFramebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, texture, 0);
    }

    private static void status(int expected) {
        int actual = GlStateManager.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if(actual != expected) throw new AssertionError("Legacy framebuffer status " + actual + " != " + expected);
    }

    private static void expectUnsupported(Runnable action) {
        try { action.run(); }
        catch(UnsupportedOperationException expected) { return; }
        throw new AssertionError("Legacy renderbuffer generation falsely succeeded");
    }

    private static void pixels(CompletableFuture<NativeImage> capture, int width, int height, int expected) throws Exception {
        try(NativeImage image = capture.get(1, TimeUnit.SECONDS)) {
            if(image.getWidth() != width || image.getHeight() != height)
                throw new AssertionError("Legacy framebuffer readback dimensions changed");
            for(int y = 0; y < height; y++) for(int x = 0; x < width; x++)
                if(image.getPixelRGBA(x, y) != expected)
                    throw new AssertionError("Legacy framebuffer pixel mismatch at " + x + "," + y);
        }
    }

    private static void clear(int width, int height, float r, float g, float b) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkClearAttachment.Buffer color = VkClearAttachment.calloc(1, stack);
            color.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).colorAttachment(0);
            color.clearValue().color().float32(stack.floats(r, g, b, 1));
            VkClearRect.Buffer rect = VkClearRect.calloc(1, stack);
            rect.layerCount(1);
            rect.rect().extent().set(width, height);
            vkCmdClearAttachments(Renderer.getCommandBuffer(), color, rect);
        }
    }
}
