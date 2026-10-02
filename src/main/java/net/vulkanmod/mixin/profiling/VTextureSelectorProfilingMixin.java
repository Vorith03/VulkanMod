package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.TextureTickAttribution;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Benchmark-only observation of the existing sprite-copy batch flush boundary. */
@Mixin(value = VTextureSelector.class, remap = false)
public abstract class VTextureSelectorProfilingMixin {
    @Shadow private static int spriteUploadRegionCount;
    @Unique private static long vulkanmod$copyFlushStart;
    @Unique private static int vulkanmod$copyFlushRegions;

    @Inject(method = "flushSpriteUploadCopies()V", at = @At("HEAD"), remap = false)
    private static void vulkanmod$beginCopyFlush(CallbackInfo ci) {
        int regions = spriteUploadRegionCount;
        if (regions <= 0) {
            vulkanmod$copyFlushStart = 0L;
            vulkanmod$copyFlushRegions = 0;
            return;
        }
        vulkanmod$copyFlushRegions = regions;
        vulkanmod$copyFlushStart = TextureTickAttribution.beginSpriteCopyFlush();
    }

    @Inject(method = "flushSpriteUploadCopies()V", at = @At("RETURN"), remap = false)
    private static void vulkanmod$endCopyFlush(CallbackInfo ci) {
        TextureTickAttribution.endSpriteCopyFlush(vulkanmod$copyFlushStart, vulkanmod$copyFlushRegions);
        vulkanmod$copyFlushStart = 0L;
        vulkanmod$copyFlushRegions = 0;
    }
}
