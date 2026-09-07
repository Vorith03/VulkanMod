package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryDiagnostics;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public class MSpriteAtlasTexture {
    @Unique
    private static final long vulkanmod$LARGE_ATLAS_BYTES = 64L * 1024L * 1024L;

    @Unique
    private boolean vulkanmod$traceLargeAtlasUpload;
    @Unique
    private int vulkanmod$traceAtlasWidth;
    @Unique
    private int vulkanmod$traceAtlasHeight;

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V"))
    private void redirect(int id, int maxLevel, int width, int height) {
        VAbstractTextureI texture = (VAbstractTextureI)(this);
        long expectedBytes = vulkanmod$estimateSizeBytes(width, height, maxLevel + 1, 4);
        boolean largeAtlas = expectedBytes >= vulkanmod$LARGE_ATLAS_BYTES;

        this.vulkanmod$traceLargeAtlasUpload = largeAtlas;
        this.vulkanmod$traceAtlasWidth = width;
        this.vulkanmod$traceAtlasHeight = height;

        if(largeAtlas) {
            MemoryDiagnostics.logSnapshot("atlas " + width + "x" + height + " before allocation");

            VulkanImage previous = texture.getVulkanImage();
            if(previous != null && previous.getEstimatedSizeBytes() >= vulkanmod$LARGE_ATLAS_BYTES) {
                long previousMiB = previous.getEstimatedSizeBytes() / (1024L * 1024L);
                Initializer.LOGGER.info(
                        "Pre-retiring large Vulkan atlas {}x{} mips={} (~{} MiB) before allocating {}x{} replacement",
                        previous.width, previous.height, previous.mipLevels, previousMiB, width, height);

                // setVulkanImage() cannot prevent the allocation-time overlap because
                // the replacement has already been constructed by then. At the atlas
                // prepareImage boundary the old storage is semantically being replaced,
                // so establish GPU idleness and retire it before requesting the new image.
                Vulkan.waitIdle();
                previous.doFree();
                MemoryDiagnostics.logSnapshot("atlas " + width + "x" + height + " after pre-retire");
            }
        }

        VulkanImage image = new VulkanImage.Builder(width, height).setMipLevels(maxLevel + 1).createVulkanImage();
        texture.setVulkanImage(image);
        texture.bindTexture();

        if(largeAtlas) {
            MemoryDiagnostics.logSnapshot("atlas " + width + "x" + height + " after Vulkan allocation");
        }
    }

    @Inject(method = "upload", at = @At("RETURN"))
    private void vulkanmod$traceAtlasUploadComplete(CallbackInfo ci) {
        if(this.vulkanmod$traceLargeAtlasUpload) {
            MemoryDiagnostics.logSnapshot(
                    "atlas " + this.vulkanmod$traceAtlasWidth + "x" + this.vulkanmod$traceAtlasHeight + " upload complete");
            this.vulkanmod$traceLargeAtlasUpload = false;
        }
    }

    @Unique
    private static long vulkanmod$estimateSizeBytes(int width, int height, int mipLevels, int formatSize) {
        long total = 0L;
        int mipWidth = width;
        int mipHeight = height;

        for(int level = 0; level < mipLevels; ++level) {
            total += (long)mipWidth * mipHeight * formatSize;
            mipWidth = Math.max(1, mipWidth >> 1);
            mipHeight = Math.max(1, mipHeight >> 1);
        }

        return total;
    }

    /**
     * @author
     */
    @Overwrite
    public void updateFilter(SpriteLoader.Preparations data) {
        //this.setFilter(false, data.maxLevel > 0);
    }
}
