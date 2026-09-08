package net.vulkanmod.vulkan.texture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.lwjgl.vulkan.VK10.*;

/** On-demand, render-thread-owned captures; no per-frame copy or persistent image cache. */
public final class ScreenshotReadback {
    private static final List<Request> pending = new ArrayList<>();
    private static final List<Readback> recorded = new ArrayList<>();
    private static Capture capture;

    private ScreenshotReadback() {}

    public static CompletableFuture<NativeImage> request(RenderTarget target) {
        RenderSystem.assertOnRenderThread();
        CompletableFuture<NativeImage> result = new CompletableFuture<>();
        if(pending.size() + recorded.size() >= 8) {
            result.completeExceptionally(new IllegalStateException("Too many pending screenshots"));
            return result;
        }
        Request request = new Request(target, result);
        if(Renderer.getInstance().isRecordingFrame())
            record(request);
        else
            pending.add(request); // F2 is normally polled AFTER present: capture the next rendered frame.
        return result;
    }

    public static void recordPending() {
        List<Request> requests = List.copyOf(pending);
        pending.clear();
        requests.forEach(ScreenshotReadback::record);
    }

    private static void record(Request request) {
        if(request.result.isCancelled())
            return;
        long buffer = 0, allocation = 0;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            // Resolve at recording time, after any resize/recreation, not at keypress time.
            VulkanImage source = request.target instanceof MainTarget
                    ? Vulkan.getSwapChain().getColorAttachment()
                    : GlTexture.getVulkanImage(request.target.getColorTextureId());
            if(source == null || !source.supportsTransferSource())
                throw new UnsupportedOperationException("This Vulkan target does not support screenshot transfers");
            if(source.format != VK_FORMAT_R8G8B8A8_UNORM && source.format != VK_FORMAT_R8G8B8A8_SRGB
                    && source.format != VK_FORMAT_B8G8R8A8_UNORM && source.format != VK_FORMAT_B8G8R8A8_SRGB)
                throw new UnsupportedOperationException("Unsupported screenshot color format: " + source.format);
            int bytes = Math.multiplyExact(Math.multiplyExact(source.width, source.height), 4);
            if(bytes <= 0)
                throw new IllegalArgumentException("Cannot capture an empty RenderTarget");
            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            MemoryManager.getInstance().createBuffer(bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer, pAllocation);
            buffer = pBuffer.get(0);
            allocation = pAllocation.get(0);
            RenderTargetManager.copyColorToBuffer(source, buffer);
            recorded.add(new Readback(request, buffer, allocation, source.width, source.height, source.format));
        } catch(RuntimeException failure) {
            if(buffer != 0)
                MemoryManager.freeBuffer(buffer, allocation);
            request.result.completeExceptionally(failure);
        }
    }

    /** Called after submission/presentation with the fence of the submitted frame slot. */
    public static void complete(long fence) {
        if(recorded.isEmpty())
            return;
        int result = vkWaitForFences(Vulkan.getDevice(), fence, true, -1L);
        if(result != VK_SUCCESS)
            throw new IllegalStateException("Screenshot frame fence failed: " + result);
        List<Readback> completed = List.copyOf(recorded);
        recorded.clear();
        for(Readback readback : completed) {
            NativeImage image = null;
            try {
                image = new NativeImage(readback.width, readback.height, false);
                NativeImage destination = image;
                int bytes = Math.multiplyExact(Math.multiplyExact(readback.width, readback.height), 4);
                MemoryManager.getInstance().MapAndCopy(readback.allocation, bytes, pointer -> {
                    ByteBuffer pixels = pointer.getByteBuffer(0, bytes).order(ByteOrder.LITTLE_ENDIAN);
                    boolean bgra = readback.format == VK_FORMAT_B8G8R8A8_UNORM || readback.format == VK_FORMAT_B8G8R8A8_SRGB;
                    for(int y = 0; y < readback.height; y++) {
                        for(int x = 0; x < readback.width; x++) {
                            int rgba = pixels.getInt((y * readback.width + x) * 4);
                            if(bgra) rgba = ColorUtil.BGRAtoRGBA(rgba);
                            // Vulkan's negative viewport already gives top-down image rows.
                            destination.setPixelRGBA(x, y, rgba | 0xFF000000);
                        }
                    }
                });
                if(!readback.request.result.complete(image))
                    image.close();
                image = null; // The future's consumer now owns the completed NativeImage.
            } catch(RuntimeException failure) {
                readback.request.result.completeExceptionally(failure);
            } finally {
                if(image != null) image.close();
                MemoryManager.freeBuffer(readback.buffer, readback.allocation);
            }
        }
    }

    /** Caller must establish device idleness before retiring recorded transfers. */
    public static void cancelAll(Throwable failure) {
        List<Request> requests = List.copyOf(pending);
        pending.clear();
        List<Readback> readbacks = List.copyOf(recorded);
        recorded.clear();
        for(Readback readback : readbacks) {
            MemoryManager.freeBuffer(readback.buffer, readback.allocation);
            readback.request.result.completeExceptionally(failure);
        }
        requests.forEach(request -> request.result.completeExceptionally(failure));
    }

    /** Replay vanilla save/crop/event logic only after its synchronous image input exists. */
    public static void withCapture(RenderTarget target, NativeImage image, Runnable save) {
        Capture previous = capture;
        Capture current = new Capture(target, image);
        capture = current;
        try {
            save.run();
        } catch(RuntimeException | Error failure) {
            // If vanilla consumed the image but failed before scheduling its
            // writer, ownership must not disappear with the callback exception.
            if(current.image == null) image.close();
            throw failure;
        } finally {
            capture = previous;
            if(current.image != null) current.image.close();
        }
    }

    public static boolean hasCapture(RenderTarget target) {
        return capture != null && capture.target == target && capture.image != null;
    }

    public static NativeImage takeCapture(RenderTarget target) {
        RenderSystem.assertOnRenderThread();
        if(!hasCapture(target))
            throw new UnsupportedOperationException("Synchronous Vulkan capture is unavailable; use Screenshot.grab or ScreenshotReadback.request");
        NativeImage image = capture.image;
        capture.image = null;
        return image;
    }

    private record Request(RenderTarget target, CompletableFuture<NativeImage> result) {}
    private record Readback(Request request, long buffer, long allocation, int width, int height, int format) {}
    private static final class Capture {
        final RenderTarget target;
        NativeImage image;
        Capture(RenderTarget target, NativeImage image) { this.target = target; this.image = image; }
    }
}
