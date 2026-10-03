package net.vulkanmod.render.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.SpriteAnimationTicker;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/** Compare actual transformed vanilla clocks and every rendered mip under validation. */
public final class SpriteAnimationSmokeTest {
    private SpriteAnimationSmokeTest() {}
    private static SpriteContents source(String id) {
        NativeImage sheet = new NativeImage(6, 2, false);
        for(int y = 0; y < 2; y++) for(int x = 0; x < 6; x++)
            sheet.setPixelRGBA(x, y, (0x80 + x * 15) << 24 | (x * 37 + y * 11) << 16
                    | (x * 17 + y * 19) << 8 | (x * 29 + y * 7));
        SpriteContents contents = new SpriteContents(new ResourceLocation("vulkanmod", id), new FrameSize(2, 2), sheet,
                new AnimationMetadataSection(List.of(new AnimationFrame(0, 2), new AnimationFrame(2, 3),
                        new AnimationFrame(2, 1), new AnimationFrame(1, 2)), 2, 2, 1, true));
        contents.increaseMipLevel(1);
        return contents;
    }
    public static void verify() {
        boolean oldEnabled = Initializer.CONFIG.animateOnlyUsedTextures;
        int oldGrace = Initializer.CONFIG.animationVisibilityGraceMs;
        boolean oldUpload = SpriteUtil.shouldUpload();
        VulkanImage previous = VTextureSelector.getBoundTexture();
        VulkanImage atlas = VulkanImage.createTextureImage(VK_FORMAT_R8G8B8A8_UNORM, 2, 4, 2,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                4, false, false);
        long buffer = 0, allocation = 0;
        try(SpriteContents reference = source("animation_reference"); SpriteContents candidate = source("animation_candidate");
            SpriteTicker referenceTicker = reference.createTicker(); SpriteTicker candidateTicker = candidate.createTicker();
            MemoryStack stack = MemoryStack.stackPush()) {
            Initializer.CONFIG.animationVisibilityGraceMs = 0;
            VTextureSelector.bindTexture(atlas);
            SpriteUtil.setDoUpload(true);
            var pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            MemoryManager.getInstance().createBuffer(32, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, pBuffer, pAllocation);
            buffer = pBuffer.get(0); allocation = pAllocation.get(0);
            for(int tick = 0; tick < 24; tick++) {
                Device.getGraphicsQueue().startRecording();
                VTextureSelector.beginSpriteUploadBatch();
                try {
                    Initializer.CONFIG.animateOnlyUsedTextures = false;
                    referenceTicker.tickAndUpload(0, 0);
                    Initializer.CONFIG.animateOnlyUsedTextures = true;
                    long before = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes();
                    candidateTicker.tickAndUpload(2, 0);
                    if(Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes() != before)
                        throw new AssertionError("Hidden ticker staged pixels");
                    long clock = ((SpriteAnimationTicker)candidateTicker).vulkanmod$clock();
                    if(clock != ((SpriteAnimationTicker)referenceTicker).vulkanmod$clock())
                        throw new AssertionError("Hidden animation clock diverged");
                    SpriteAnimationUsage.use(candidate);
                    if(((SpriteAnimationTicker)candidateTicker).vulkanmod$clock() != clock)
                        throw new AssertionError("Refresh advanced animation clock");
                } finally {
                    VTextureSelector.endSpriteUploadBatch();
                    SpriteUtil.transitionLayouts(Device.getGraphicsQueue().getCommandBuffer());
                    Device.getGraphicsQueue().endRecordingAndSubmit();
                }
                Vulkan.waitIdle();
                for(int mip = 0; mip < 2; mip++) {
                    Renderer renderer = Renderer.getInstance();
                    renderer.resetBuffers(); renderer.beginFrame();
                    RenderTargetManager.copyColorMipToBuffer(atlas, buffer, mip);
                    renderer.endFrame(); Vulkan.waitIdle();
                    int width = 4 >> mip, height = 2 >> mip, half = width / 2;
                    MemoryManager.getInstance().MapAndCopy(allocation, width * height * 4, pointer -> {
                        var pixels = pointer.getByteBuffer(0, width * height * 4);
                        for(int y = 0; y < height; y++) for(int x = 0; x < half; x++) for(int channel = 0; channel < 4; channel++)
                            if(pixels.get((y * width + x) * 4 + channel) != pixels.get((y * width + x + half) * 4 + channel))
                                throw new AssertionError("Refreshed animation mip pixels differ from vanilla");
                    });
                }
            }
            Initializer.LOGGER.info("Sprite usage animation smoke passed (hidden clocks, zero staging, first-use refresh, reordered/repeated frames, interpolation, all mips)");
        } finally {
            Initializer.CONFIG.animateOnlyUsedTextures = oldEnabled;
            Initializer.CONFIG.animationVisibilityGraceMs = oldGrace;
            SpriteUtil.setDoUpload(oldUpload);
            Vulkan.waitIdle();
            if(buffer != 0) MemoryManager.freeBuffer(buffer, allocation);
            atlas.doFree(); VTextureSelector.bindTexture(previous);
        }
    }
}
