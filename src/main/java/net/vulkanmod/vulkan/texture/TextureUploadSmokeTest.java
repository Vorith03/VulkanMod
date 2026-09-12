package net.vulkanmod.vulkan.texture;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** Runs under the screenshot gate's Vulkan synchronization validation. */
public final class TextureUploadSmokeTest {
    private TextureUploadSmokeTest() {}

    public static void verify() {
        VulkanImage previous = VTextureSelector.getBoundTexture();
        VulkanImage image = VulkanImage.createTextureImage(VK_FORMAT_R8G8B8A8_UNORM, 1, 8, 6,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                4, false, false);
        long readbackBuffer = 0, readbackAllocation = 0;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer source = stack.malloc(7 + 11 * 6 * 4);
            for(int i = 0; i < source.capacity(); ++i) source.put(i, (byte)(i * 17 + 3));
            source.position(7);
            VTextureSelector.setActiveTexture(0);
            VTextureSelector.bindTexture(image);
            long before = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes();
            VTextureSelector.uploadSubTexture(0, 8, 6, 0, 0, 0, 0, 11, source);
            image.readOnlyLayout();
            Device.getGraphicsQueue().startRecording();
            VTextureSelector.uploadSubTexture(0, 3, 2, 2, 1, 2, 4, 11, source);
            image.readOnlyLayout();
            Device.getGraphicsQueue().endRecordingAndSubmit();
            long staged = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes() - before;
            if(staged < 216 || staged > 219)
                throw new AssertionError("Texture staging retained source row gaps: " + staged);
            if(source.position() != 7)
                throw new AssertionError("Texture upload changed source position");

            // Test-only idleness keeps the helper upload complete before frame
            // staging resets and allows deterministic destruction after readback.
            Vulkan.waitIdle();
            var pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            MemoryManager manager = MemoryManager.getInstance();
            manager.createBuffer(8 * 6 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer, pAllocation);
            readbackBuffer = pBuffer.get(0);
            readbackAllocation = pAllocation.get(0);
            Renderer renderer = Renderer.getInstance();
            renderer.resetBuffers();
            renderer.beginFrame();
            RenderTargetManager.copyColorToBuffer(image, readbackBuffer);
            renderer.endFrame();
            Vulkan.waitIdle();
            manager.MapAndCopy(readbackAllocation, 192, pointer -> {
                ByteBuffer pixels = pointer.getByteBuffer(0, 192);
                for(int y = 0; y < 6; y++) {
                    for(int x = 0; x < 8; x++) {
                        boolean sub = x >= 2 && x < 5 && y >= 1 && y < 3;
                        int sx = sub ? x - 2 + 4 : x;
                        int sy = sub ? y - 1 + 2 : y;
                        for(int c = 0; c < 4; c++) {
                            byte expected = source.get(7 + (sy * 11 + sx) * 4 + c);
                            if(pixels.get((y * 8 + x) * 4 + c) != expected)
                                throw new AssertionError("Packed Vulkan texture pixel mismatch at " + x + "," + y);
                        }
                    }
                }
            });
            Initializer.LOGGER.info("Packed texture upload Vulkan smoke passed: full/offset rectangles, nonzero source position, exact staging bytes, RGBA readback");
        } finally {
            Vulkan.waitIdle();
            if(readbackBuffer != 0) MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
            image.doFree();
            VTextureSelector.bindTexture(previous);
        }
    }
}
