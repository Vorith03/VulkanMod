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
import java.util.Arrays;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/** Actual transformed Forge clocks and native pixels against an independent numeric oracle. */
public final class SpriteAnimationSmokeTest {
    private SpriteAnimationSmokeTest() {}
    private record Case(String name, int width, int height, int columns, int rows, int mips,
                        boolean interpolate, boolean custom, boolean implicit,
                        List<AnimationFrame> metadata, SpriteAnimationOracle.Frame[] resolved) {}
    private static SpriteAnimationOracle.Frame frame(int index, int duration) {
        return new SpriteAnimationOracle.Frame(index, duration);
    }
    private static Case explicit(String name, int w, int h, int columns, int rows, int mips,
                                 boolean interpolate, boolean custom, SpriteAnimationOracle.Frame... frames) {
        return new Case(name, w, h, columns, rows, mips, interpolate, custom, false,
                Arrays.stream(frames).map(f -> new AnimationFrame(f.index(), f.duration())).toList(), frames);
    }
    private static SpriteContents source(Case test, String suffix) {
        NativeImage sheet = new NativeImage(test.width * test.columns, test.height * test.rows, false);
        // Nonuniform pixels, nonopaque alpha and deliberately asymmetric channels.
        for(int y = 0; y < sheet.getHeight(); y++) for(int x = 0; x < sheet.getWidth(); x++)
            sheet.setPixelRGBA(x, y, ((x * 61 + y * 47) & 255) << 24
                    | ((x * 37 + y * 11) & 255) << 16
                    | ((x * 17 + y * 19) & 255) << 8 | ((x * 29 + y * 7) & 255));
        var metadata = new AnimationMetadataSection(test.metadata, test.width, test.height,
                test.implicit ? 2 : 1, test.interpolate);
        var id = new ResourceLocation("vulkanmod", test.name + suffix);
        SpriteContents contents = test.custom
                ? new SpriteContents(id, new FrameSize(test.width, test.height), sheet, metadata) {}
                : new SpriteContents(id, new FrameSize(test.width, test.height), sheet, metadata);
        contents.increaseMipLevel(test.mips);
        return contents;
    }
    public static void verify() {
        boolean oldEnabled = Initializer.CONFIG.animateOnlyUsedTextures;
        int oldGrace = Initializer.CONFIG.animationVisibilityGraceMs;
        boolean oldUpload = SpriteUtil.shouldUpload();
        VulkanImage previous = VTextureSelector.getBoundTexture();
        try {
            Initializer.CONFIG.animationVisibilityGraceMs = 0;
            SpriteUtil.setDoUpload(true);
            verify(explicit("reordered", 2, 2, 3, 1, 1, true, false,
                    frame(0,2), frame(2,3), frame(2,1), frame(1,2)));
            verify(explicit("odd_rectangular", 3, 2, 2, 2, 1, true, false,
                    frame(3,3), frame(0,1), frame(2,2), frame(3,1)));
            verify(explicit("discrete", 2, 2, 2, 2, 1, false, false,
                    frame(3,1), frame(3,4), frame(0,2), frame(2,1)));
            verify(explicit("zero_mip_extent", 1, 2, 2, 2, 2, true, false,
                    frame(3,2), frame(0,3), frame(2,1)));
            verify(new Case("implicit", 2, 2, 2, 2, 1, true, false, true, List.of(),
                    new SpriteAnimationOracle.Frame[] {frame(0,2),frame(1,2),frame(2,2),frame(3,2)}));
            verify(new Case("filtered_metadata", 2, 2, 2, 2, 1, true, false, false,
                    List.of(new AnimationFrame(99,1), new AnimationFrame(1,0),
                            new AnimationFrame(2,3), new AnimationFrame(0,2)),
                    new SpriteAnimationOracle.Frame[] {frame(2,3),frame(0,2)}));
            verify(explicit("custom_cpu", 2, 2, 2, 2, 1, true, true,
                    frame(3,3),frame(0,2),frame(2,1)));
            Initializer.LOGGER.info("Sprite usage animation smoke passed (independent ABGR/alpha/truncation oracle, hidden clocks, zero staging, first-use refresh, reordered/repeated/implicit/filtered frames, rectangular/odd/all-mip pixels, Forge zero-extent guards, custom CPU exclusion)");
        } finally {
            Initializer.CONFIG.animateOnlyUsedTextures = oldEnabled;
            Initializer.CONFIG.animationVisibilityGraceMs = oldGrace;
            SpriteUtil.setDoUpload(oldUpload);
            VTextureSelector.bindTexture(previous);
        }
    }
    private static void verify(Case test) {
        // Align both destinations at every tested mip, including zero sprite extents.
        int half = 1;
        while(half < test.width || half < (1 << test.mips)) half <<= 1;
        int atlasWidth = half * 2, atlasHeight = 1;
        while(atlasHeight < test.height || atlasHeight < (1 << test.mips)) atlasHeight <<= 1;
        final int candidateX = half;
        VulkanImage atlas = VulkanImage.createTextureImage(VK_FORMAT_R8G8B8A8_UNORM, test.mips + 1,
                atlasWidth, atlasHeight, VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK_IMAGE_USAGE_SAMPLED_BIT, 4, false, false);
        long buffer = 0, allocation = 0;
        try(SpriteContents reference = source(test, "_reference"); SpriteContents candidate = source(test, "_candidate");
            SpriteTicker referenceTicker = reference.createTicker(); SpriteTicker candidateTicker = candidate.createTicker();
            MemoryStack stack = MemoryStack.stackPush()) {
            if(referenceTicker == null || candidateTicker == null) throw new AssertionError("Missing ticker: " + test.name);
            int[][] sheets = new int[test.mips + 1][];
            int[] widths = new int[sheets.length];
            for(int mip = 0; mip < sheets.length; mip++) {
                NativeImage image = reference.byMipLevel[mip];
                widths[mip] = image.getWidth();
                sheets[mip] = new int[image.getWidth() * image.getHeight()];
                for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++)
                    sheets[mip][y * image.getWidth() + x] = image.getPixelRGBA(x,y);
            }
            var oracle = new SpriteAnimationOracle(test.resolved, test.interpolate);
            VTextureSelector.bindTexture(atlas);
            var pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            MemoryManager.getInstance().createBuffer((long)atlasWidth * atlasHeight * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, pBuffer, pAllocation);
            buffer = pBuffer.get(0); allocation = pAllocation.get(0);
            Device.getGraphicsQueue().startRecording();
            try {
                Initializer.CONFIG.animateOnlyUsedTextures = false;
                GpuAnimatedTextureResidency.setCiForceCpuPath(true);
                try {
                    reference.uploadFirstFrame(0,0);
                } finally {
                    GpuAnimatedTextureResidency.setCiForceCpuPath(false);
                }
                candidate.uploadFirstFrame(candidateX,0);
            } finally {
                SpriteUtil.transitionLayouts(Device.getGraphicsQueue().getCommandBuffer());
                Device.getGraphicsQueue().endRecordingAndSubmit();
            }
            Vulkan.waitIdle();
            for(int tick = 0; tick < 24; tick++) {
                var update = oracle.tick();
                Device.getGraphicsQueue().startRecording();
                VTextureSelector.beginSpriteUploadBatch();
                try {
                    Initializer.CONFIG.animateOnlyUsedTextures = false;
                    long beforeReference = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes();
                    GpuAnimatedTextureResidency.setCiForceCpuPath(true);
                    try {
                        referenceTicker.tickAndUpload(0, 0);
                    } finally {
                        GpuAnimatedTextureResidency.setCiForceCpuPath(false);
                    }
                    boolean stagedReference = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes() != beforeReference;
                    if(stagedReference != (update != SpriteAnimationOracle.Update.NONE))
                        throw new AssertionError("Upload cadence differs: " + test.name + " tick " + tick);
                    Initializer.CONFIG.animateOnlyUsedTextures = true;
                    long before = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes();
                    candidateTicker.tickAndUpload(candidateX, 0);
                    boolean stagedCandidate = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes() != before;
                    if(stagedCandidate != (test.custom && update != SpriteAnimationOracle.Update.NONE))
                        throw new AssertionError("Hidden/custom staging differs: " + test.name + " tick " + tick);
                    long clock = ((SpriteAnimationTicker)candidateTicker).vulkanmod$clock();
                    if(clock != oracle.clock() || clock != ((SpriteAnimationTicker)referenceTicker).vulkanmod$clock())
                        throw new AssertionError("Animation clock differs: " + test.name + " tick " + tick);
                    SpriteAnimationUsage.use(candidate);
                    if(((SpriteAnimationTicker)candidateTicker).vulkanmod$clock() != clock)
                        throw new AssertionError("Refresh advanced animation clock");
                } finally {
                    VTextureSelector.endSpriteUploadBatch();
                    SpriteUtil.transitionLayouts(Device.getGraphicsQueue().getCommandBuffer());
                    Device.getGraphicsQueue().endRecordingAndSubmit();
                }
                Vulkan.waitIdle();
                for(int mip = 0; mip <= test.mips; mip++) {
                    if((test.width >> mip) == 0 || (test.height >> mip) == 0) continue;
                    Renderer renderer = Renderer.getInstance();
                    renderer.resetBuffers(); renderer.beginFrame();
                    RenderTargetManager.copyColorMipToBuffer(atlas, buffer, mip);
                    renderer.endFrame(); Vulkan.waitIdle();
                    final int level = mip, sampleTick = tick;
                    int width = atlasWidth >> mip, height = atlasHeight >> mip, right = candidateX >> mip;
                    MemoryManager.getInstance().MapAndCopy(allocation, width * height * 4, pointer -> {
                        var pixels = pointer.getByteBuffer(0, width * height * 4);
                        for(int y = 0; y < (test.height >> level); y++) for(int x = 0; x < (test.width >> level); x++) {
                            int expected = oracle.pixel(sheets[level], widths[level], test.columns,
                                    test.width, test.height, level, x, y);
                            for(int channel = 0; channel < 4; channel++) {
                                byte value = (byte)(expected >>> (channel * 8));
                                if(pixels.get((y * width + x) * 4 + channel) != value
                                        || pixels.get((y * width + x + right) * 4 + channel) != value)
                                    throw new AssertionError("Animation oracle pixel differs: " + test.name + " tick "
                                            + sampleTick + " mip " + level + " x/y " + x + "/" + y + " channel " + channel
                                            + " expected " + (value & 255) + " reference "
                                            + (pixels.get((y * width + x) * 4 + channel) & 255) + " candidate "
                                            + (pixels.get((y * width + x + right) * 4 + channel) & 255));
                            }
                        }
                    });
                }
            }
            if("discrete".equals(test.name) && GpuAnimatedTextureResidency.enabled()) {
                GpuAnimatedTextureResidency.Stats beforeMutation = GpuAnimatedTextureResidency.stats();
                NativeImage base = candidate.byMipLevel[0];
                base.setPixelRGBA(0, 0, base.getPixelRGBA(0, 0) ^ 0x00010101);

                Device.getGraphicsQueue().startRecording();
                VTextureSelector.beginSpriteUploadBatch();
                long beforeFallbackStaging = Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes();
                try {
                    // The visibility test intentionally leaves the candidate hidden
                    // with materialize=false. Re-enter the ordinary ungated ticker
                    // once so this mutation probe reaches SpriteContents.upload();
                    // That tick may itself invalidate residency if it changes frame;
                    // the explicit first-frame upload below then proves CPU fallback.
                    Initializer.CONFIG.animateOnlyUsedTextures = false;
                    candidateTicker.tickAndUpload(candidateX, 0);
                    candidate.uploadFirstFrame(candidateX, 0);
                } finally {
                    VTextureSelector.endSpriteUploadBatch();
                    SpriteUtil.transitionLayouts(Device.getGraphicsQueue().getCommandBuffer());
                    Device.getGraphicsQueue().endRecordingAndSubmit();
                }
                Vulkan.waitIdle();

                GpuAnimatedTextureResidency.Stats afterMutation = GpuAnimatedTextureResidency.stats();
                if(afterMutation.totalCopyCalls() <= 0L)
                    throw new AssertionError("Resident animation copy path was not exercised");
                if(afterMutation.sourceInvalidated() <= beforeMutation.sourceInvalidated())
                    throw new AssertionError("Resident animation source mutation did not invalidate");
                if(Vulkan.getStagingBuffer(Renderer.getCurrentFrame()).getUsedBytes() == beforeFallbackStaging)
                    throw new AssertionError("Resident mutation did not fall back to CPU staging");
                Initializer.LOGGER.info("GPU resident animation smoke passed (discrete all-mip raw pixels, CPU clock ownership, mutation invalidation and staging fallback)");
            }

            Initializer.LOGGER.info("Animation oracle case passed: {}", test.name);
        } finally {
            GpuAnimatedTextureResidency.setCiForceCpuPath(false);
            Vulkan.waitIdle();
            if(buffer != 0) MemoryManager.freeBuffer(buffer, allocation);
            atlas.doFree();
        }
    }
}
