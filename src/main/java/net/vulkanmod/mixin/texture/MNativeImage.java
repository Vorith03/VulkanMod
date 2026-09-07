package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryDiagnostics;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(NativeImage.class)
public abstract class MNativeImage {
    @Unique
    private static final long vulkanmod$MIB = 1024L * 1024L;
    @Unique
    private static final long vulkanmod$NATIVE_IMAGE_SAFETY_LIMIT_MIB = Math.max(
            1024L, Long.getLong("vulkanmod.nativeImageSafetyLimitMiB", 4096L));
    @Unique
    private static final long vulkanmod$NATIVE_IMAGE_SAFETY_LIMIT_BYTES =
            vulkanmod$NATIVE_IMAGE_SAFETY_LIMIT_MIB * vulkanmod$MIB;

    @Shadow private long pixels;
    @Shadow private long size;

    @Shadow public abstract void close();

    @Shadow @Final private NativeImage.Format format;

    @Shadow public abstract int getWidth();

    @Shadow @Final private int width;
    @Shadow @Final private int height;

    @Shadow public abstract int getHeight();

    @Shadow public abstract void setPixelRGBA(int i, int j, int k);

    @Shadow public abstract int getPixelRGBA(int i, int j);

    @Unique
    private ByteBuffer vulkanmod$buffer;
    @Unique
    private long vulkanmod$trackedNativeBytes;
    @Unique
    private boolean vulkanmod$nativeMemoryReleased;

    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V", at = @At("RETURN"))
    private void constr(NativeImage.Format format, int width, int height, boolean useStb, CallbackInfo ci) {
        this.vulkanmod$initializeNativeTracking();
        this.vulkanmod$enforceNativeImageBudget();
    }

    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZJ)V", at = @At("RETURN"))
    private void constr(NativeImage.Format format, int width, int height, boolean useStb, long pixels, CallbackInfo ci) {
        this.vulkanmod$initializeNativeTracking();
        this.vulkanmod$enforceNativeImageBudget();
    }

    @Unique
    private void vulkanmod$initializeNativeTracking() {
        if(this.pixels != 0) {
            this.vulkanmod$buffer = MemoryUtil.memByteBuffer(this.pixels, (int)this.size);
        }

        // NativeImage constructors can delegate to one another. Guard the instance
        // counter so a chained constructor only contributes its native allocation once.
        if(this.vulkanmod$trackedNativeBytes == 0L && this.pixels != 0L && this.size > 0L) {
            this.vulkanmod$trackedNativeBytes = this.size;
            MemoryDiagnostics.onNativeImageAllocated(this.size);
        }
    }

    @Unique
    private void vulkanmod$enforceNativeImageBudget() {
        long live = MemoryDiagnostics.getNativeImageLiveBytes();
        if(live <= vulkanmod$NATIVE_IMAGE_SAFETY_LIMIT_BYTES)
            return;

        // Mixin 0.8.5 does not permit a constructor HEAD injector. Check at RETURN
        // instead, but explicitly release the just-created image before propagating
        // the failure so the circuit breaker itself cannot strand native memory.
        long triggeringBytes = this.vulkanmod$trackedNativeBytes;
        MemoryDiagnostics.logSnapshot("NativeImage safety limit after allocation");
        this.close();
        throw new OutOfMemoryError(String.format(
                "VulkanMod stopped resource loading before NativeImage memory could exhaust the system: " +
                        "tracked=%d MiB trigger=%d MiB limit=%d MiB (%dx%d). " +
                        "Override with -Dvulkanmod.nativeImageSafetyLimitMiB=<MiB> only for diagnosis.",
                live / vulkanmod$MIB, triggeringBytes / vulkanmod$MIB,
                vulkanmod$NATIVE_IMAGE_SAFETY_LIMIT_MIB, this.width, this.height));
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void vulkanmod$trackNativeClose(CallbackInfo ci) {
        if(!this.vulkanmod$nativeMemoryReleased && this.vulkanmod$trackedNativeBytes > 0L) {
            this.vulkanmod$nativeMemoryReleased = true;
            MemoryDiagnostics.onNativeImageFreed(this.vulkanmod$trackedNativeBytes);
        }
    }

    /**
     * @author
     */
    @Overwrite
    private void _upload(int level, int xOffset, int yOffset, int unpackSkipPixels, int unpackSkipRows, int widthIn, int heightIn, boolean blur, boolean clamp, boolean mipmap, boolean autoClose) {
        RenderSystem.assertOnRenderThreadOrInit();

        VTextureSelector.uploadSubTexture(level, widthIn, heightIn, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, this.getWidth(), this.vulkanmod$buffer);

        if (autoClose) {
            this.close();
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void downloadTexture(int level, boolean removeAlpha) {
        RenderSystem.assertOnRenderThread();

        VulkanImage.downloadTexture(this.width, this.height, 4, this.vulkanmod$buffer, Vulkan.getSwapChain().getColorAttachment().getId());

        if (removeAlpha && this.format.hasAlpha()) {
            for (int i = 0; i < this.height; ++i) {
                for (int j = 0; j < this.getWidth(); ++j) {
                    int v = this.getPixelRGBA(j, i);

                    if(Vulkan.getSwapChain().isBGRAformat)
                        v = ColorUtil.BGRAtoRGBA(v);

                    this.setPixelRGBA(j, i, v | 255 << this.format.alphaOffset());
                }
            }
        }

    }
}
