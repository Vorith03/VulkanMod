package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.vulkanmod.interfaces.VNativeImageI;
import net.vulkanmod.interfaces.VSpriteContentsI;
import net.vulkanmod.render.texture.SpriteMipMemoryTracker;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.IntStream;

@Mixin(SpriteContents.class)
public abstract class MSpriteContents implements VSpriteContentsI {

    @Shadow @Final private NativeImage originalImage;
    @Shadow private NativeImage[] byMipLevel;

    @Shadow public abstract IntStream getUniqueFrames();
    @Shadow public abstract void increaseMipLevel(int mipmapLevels);

    @Unique private boolean vulkanmod$staticSpriteClassified;
    @Unique private boolean vulkanmod$staticSprite;
    @Unique private boolean vulkanmod$materializingDeferredMips;
    @Unique private boolean vulkanmod$materializedDeferredMips;
    @Unique private int vulkanmod$deferredMipLevel = -1;
    @Unique private long vulkanmod$deferredMipBytes;

    @Override
    public boolean vulkanmod$isStaticSprite() {
        if(!this.vulkanmod$staticSpriteClassified) {
            this.vulkanmod$staticSprite = this.getUniqueFrames().limit(2L).count() <= 1L;
            this.vulkanmod$staticSpriteClassified = true;
        }
        return this.vulkanmod$staticSprite;
    }

    @Override
    public long vulkanmod$getCpuBytes() {
        NativeImage[] images = this.byMipLevel;
        if(images == null || images.length == 0) {
            return 0L;
        }

        Set<NativeImage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        long total = 0L;
        for(NativeImage image : images) {
            if(image != null && seen.add(image) && image instanceof VNativeImageI trackedImage) {
                total += trackedImage.vulkanmod$getTrackedNativeBytes();
            }
        }
        return total;
    }

    /**
     * Static sprite mipmaps do not need to exist while every sprite supplier waits
     * for atlas stitching/apply. Record the requested mip level and generate those
     * copies only immediately before this sprite is uploaded. Animated sprites keep
     * the vanilla eager path because their ticker needs the mip chain for later frame
     * uploads. Custom SpriteContents that already contain multiple mip images are
     * also left untouched.
     */
    @Inject(method = "increaseMipLevel", at = @At("HEAD"), cancellable = true)
    private void vulkanmod$deferStaticMipGeneration(int mipmapLevels, CallbackInfo ci) {
        if(this.vulkanmod$materializingDeferredMips || mipmapLevels <= 0
                || !this.vulkanmod$isStaticSprite()
                || this.byMipLevel == null || this.byMipLevel.length != 1) {
            return;
        }

        int requested = Math.max(this.vulkanmod$deferredMipLevel, mipmapLevels);
        long estimate = this.vulkanmod$estimateDeferredMipBytes(requested);
        SpriteMipMemoryTracker.updateDeferred(this.vulkanmod$deferredMipBytes, estimate);
        this.vulkanmod$deferredMipBytes = estimate;
        this.vulkanmod$deferredMipLevel = requested;
        ci.cancel();
    }

    @Inject(method = "uploadFirstFrame", at = @At("HEAD"))
    private void vulkanmod$materializeStaticMipsForUpload(int x, int y, CallbackInfo ci) {
        if(this.vulkanmod$deferredMipLevel <= 0
                || this.byMipLevel == null || this.byMipLevel.length != 1) {
            return;
        }

        this.vulkanmod$materializingDeferredMips = true;
        try {
            this.increaseMipLevel(this.vulkanmod$deferredMipLevel);
            this.vulkanmod$materializedDeferredMips = this.byMipLevel != null && this.byMipLevel.length > 1;
        } finally {
            this.vulkanmod$materializingDeferredMips = false;
        }
    }

    @Inject(method = "uploadFirstFrame", at = @At("RETURN"))
    private void vulkanmod$releaseStaticMipsAfterUpload(int x, int y, CallbackInfo ci) {
        if(!this.vulkanmod$materializedDeferredMips) {
            return;
        }

        NativeImage[] images = this.byMipLevel;
        if(images != null) {
            for(int level = 1; level < images.length; ++level) {
                NativeImage image = images[level];
                if(image != null && image != this.originalImage) {
                    image.close();
                }
            }
        }

        // Preserve the original mod-visible CPU image. Only generated mip copies
        // are transient; future transparency/source-pixel reads keep working.
        this.byMipLevel = new NativeImage[] { this.originalImage };
        this.vulkanmod$materializedDeferredMips = false;
        this.vulkanmod$clearDeferredMipAccounting();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void vulkanmod$clearDeferredMipAccountingOnClose(CallbackInfo ci) {
        this.vulkanmod$clearDeferredMipAccounting();
    }

    @Unique
    private void vulkanmod$clearDeferredMipAccounting() {
        if(this.vulkanmod$deferredMipBytes > 0L) {
            SpriteMipMemoryTracker.updateDeferred(this.vulkanmod$deferredMipBytes, 0L);
        }
        this.vulkanmod$deferredMipBytes = 0L;
        this.vulkanmod$deferredMipLevel = -1;
    }

    @Unique
    private long vulkanmod$estimateDeferredMipBytes(int mipmapLevels) {
        if(!(this.originalImage instanceof VNativeImageI trackedImage)) {
            return 0L;
        }

        long baseBytes = trackedImage.vulkanmod$getTrackedNativeBytes();
        long basePixels = (long)this.originalImage.getWidth() * this.originalImage.getHeight();
        if(baseBytes <= 0L || basePixels <= 0L) {
            return 0L;
        }

        long bytesPerPixel = Math.max(1L, baseBytes / basePixels);
        int width = this.originalImage.getWidth();
        int height = this.originalImage.getHeight();
        long total = 0L;
        for(int level = 1; level <= mipmapLevels; ++level) {
            width = Math.max(1, width >> 1);
            height = Math.max(1, height >> 1);
            total += (long)width * height * bytesPerPixel;
        }
        return total;
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void checkUpload(int i, int j, int k, int l, NativeImage[] nativeImages, CallbackInfo ci) {
        if(!SpriteUtil.shouldUpload()) {
            ci.cancel();
            return;
        }

        SpriteUtil.addTransitionedLayout(VTextureSelector.getBoundTexture());
    }
}
