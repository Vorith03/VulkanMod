package net.vulkanmod.vulkan.texture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.client.event.ScreenshotEvent;
import net.minecraftforge.common.MinecraftForge;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.*;

/** Pixel oracle for the production screenshot API, opt-in under Lavapipe validation. */
public final class ScreenshotReadbackSmokeTest {
    private ScreenshotReadbackSmokeTest() {}

    public static void verify(Minecraft minecraft) throws Exception {
        Renderer renderer = Renderer.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        File directory = new File("screenshot-smoke");
        File screenshots = new File(directory, "screenshots");
        Files.createDirectories(screenshots.toPath());
        for(String name : new String[]{"main.png", "offscreen.png", "resized.png", "redirected.png", "cancel.png"})
            Files.deleteIfExists(new File(screenshots, name).toPath());

        // Old synchronous entry points must fail without allocating/reading a GPU transfer.
        expectUnsupported(() -> { try(NativeImage ignored = Screenshot.takeScreenshot(main)) {} });
        try(NativeImage image = new NativeImage(1, 1, false)) {
            expectUnsupported(() -> image.downloadTexture(0, true));
        }

        AtomicInteger events = new AtomicInteger();
        CountDownLatch saved = new CountDownLatch(4);
        ConcurrentLinkedQueue<String> feedback = new ConcurrentLinkedQueue<>();
        Consumer<net.minecraft.network.chat.Component> onSaved = message -> {
            feedback.add(message.getString());
            saved.countDown();
        };
        Consumer<ScreenshotEvent> listener = event -> {
            events.incrementAndGet();
            String name = event.getScreenshotFile().getName();
            if(name.equals("resized.png")) {
                event.setScreenshotFile(new File(screenshots, "redirected.png"));
                event.setResultMessage(net.minecraft.network.chat.Component.literal("readback redirected"));
            }
            if(name.equals("cancel.png")) event.setCanceled(true);
        };
        // This isolated process exits at constructor return, before Forge's
        // completeModLoading normally starts the initially shut-down event bus.
        MinecraftForge.EVENT_BUS.start();
        MinecraftForge.EVENT_BUS.addListener(listener);
        TextureTarget offscreen = new TextureTarget(8, 6, true, Minecraft.ON_OSX);
        try {
            // F2 timing: request between frames. It must not read the previously
            // presented swapchain image or invoke Forge's event before GPU completion.
            Screenshot.grab(directory, "main.png", main, onSaved);
            if(events.get() != 0) throw new AssertionError("Screenshot completed before a frame was recorded");

            renderer.resetBuffers();
            renderer.beginFrame();
            pattern(main, 1, 0, 0, 0, 0, 1); // top red, bottom blue, source alpha zero

            pattern(offscreen, 0, 1, 0, 1, 1, 0); // RGBA source, not swapchain BGRA
            Screenshot.grab(directory, "offscreen.png", offscreen, onSaved);
            // Its copy has been recorded; later rendering and resize must not change it.
            pattern(offscreen, 0, 0, 1, 0, 0, 1);
            offscreen.unbindWrite();
            offscreen.resize(12, 10, Minecraft.ON_OSX);
            pattern(offscreen, 1, 0, 1, 0, 1, 1);
            Screenshot.grab(directory, "resized.png", offscreen, onSaved);
            Screenshot.grab(directory, "cancel.png", offscreen, onSaved);
            offscreen.unbindWrite();
            if(events.get() != 0) throw new AssertionError("CPU readback ran before submission");
            renderer.endFrame();
            if(!saved.await(10, TimeUnit.SECONDS)) throw new AssertionError("Screenshot save callbacks timed out");
            if(events.get() != 4) throw new AssertionError("Expected 4 Forge screenshot events, got "
                    + events.get() + "; feedback: " + feedback);
            if(!feedback.contains("readback redirected"))
                throw new AssertionError("Forge custom success message was lost: " + feedback);

            verifyFile(new File(screenshots, "main.png"), main.width, main.height, 0xFF0000FF, 0xFFFF0000);
            verifyFile(new File(screenshots, "offscreen.png"), 8, 6, 0xFF00FF00, 0xFF00FFFF);
            verifyFile(new File(screenshots, "redirected.png"), 12, 10, 0xFFFF00FF, 0xFFFFFF00);
            if(new File(screenshots, "resized.png").exists() || new File(screenshots, "cancel.png").exists())
                throw new AssertionError("Forge redirect/cancellation was ignored");

            // Queue another capture after presentation and verify a new frame's
            // pixels through the public future API, not a retained image/cache.
            var next = ScreenshotReadback.request(main);
            if(next.isDone()) throw new AssertionError("Post-present request read an unowned image");
            renderer.resetBuffers();
            renderer.beginFrame();
            pattern(main, 0, 1, 0, 1, 1, 0);
            renderer.endFrame();
            try(NativeImage image = next.get(1, TimeUnit.SECONDS)) {
                verifyPixels(image, main.width, main.height, 0xFF00FF00, 0xFF00FFFF);
            }
            Initializer.LOGGER.info("Vulkan screenshot readback smoke passed: pixels, orientation, alpha, offscreen resize, next frame, Forge redirect/cancel");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            MinecraftForge.EVENT_BUS.shutdown();
            offscreen.destroyBuffers();
        }
    }

    private static void expectUnsupported(Runnable action) {
        try { action.run(); }
        catch(UnsupportedOperationException expected) { return; }
        throw new AssertionError("Unsafe synchronous readback was not gated");
    }

    private static void verifyFile(File file, int width, int height, int top, int bottom) throws Exception {
        try(var input = Files.newInputStream(file.toPath()); NativeImage image = NativeImage.read(input)) {
            verifyPixels(image, width, height, top, bottom);
        }
    }

    private static void verifyPixels(NativeImage image, int width, int height, int top, int bottom) {
        if(image.getWidth() != width || image.getHeight() != height)
            throw new AssertionError("Screenshot dimensions changed during resize");
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
                int expected = y < height / 2 ? top : bottom;
                int actual = image.getPixelRGBA(x, y);
                if(actual != expected)
                    throw new AssertionError("Screenshot pixel " + x + "," + y + " expected "
                            + Integer.toHexString(expected) + " got " + Integer.toHexString(actual));
            }
        }
    }

    private static void pattern(RenderTarget target, float r1, float g1, float b1, float r2, float g2, float b2) {
        target.bindWrite(true);
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkClearAttachment.Buffer color = VkClearAttachment.calloc(1, stack);
            color.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).colorAttachment(0);
            VkClearRect.Buffer rect = VkClearRect.calloc(1, stack);
            rect.layerCount(1);
            rect.rect().extent().set(target.width, target.height / 2);
            color.clearValue().color().float32(stack.floats(r1, g1, b1, 0));
            vkCmdClearAttachments(Renderer.getCommandBuffer(), color, rect);
            rect.rect().offset().set(0, target.height / 2);
            rect.rect().extent().set(target.width, target.height - target.height / 2);
            color.clearValue().color().float32(stack.floats(r2, g2, b2, 0));
            vkCmdClearAttachments(Renderer.getCommandBuffer(), color, rect);
        }
    }
}
