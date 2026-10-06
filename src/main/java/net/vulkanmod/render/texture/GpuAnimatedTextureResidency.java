package net.vulkanmod.render.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.vulkanmod.interfaces.VNativeImageI;
import net.vulkanmod.render.profiling.TextureTickAttribution;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.memory.TextureResidentBuffer;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * O3/O4 animated-texture residency.
 *
 * <p>Forge keeps ownership of the animation clock and frame schedule. Immutable
 * source mip bytes are copied once into device-local storage. Discrete frame
 * changes use buffer-to-image copies; qualified interpolated frames use O4's
 * compute scratch path. Custom/dynamic sources retain the original CPU path.</p>
 */
public final class GpuAnimatedTextureResidency implements AutoCloseable {
    private static final boolean COPY_ENABLED =
            Boolean.getBoolean("vulkanmod.gpuAnimatedTextureCopies");
    private static final long LIMIT_BYTES = 128L * 1024L * 1024L;

    private static long currentBytes;
    private static long peakBytes;
    private static long admitted;
    private static long capacityRejected;
    private static long sourceRejected;
    private static long stagingRejected;
    private static long sourceInvalidated;
    private static long totalCopyCalls;
    private static long totalCopyRegions;
    private static long totalCopyBytes;
    private static long interpolationCalls;
    private static long interpolationDispatches;
    private static long interpolationPixels;
    private static long interpolationBytes;
    private static long interpolationBindingRejected;
    private static boolean ciForceCpuPath;

    private final NativeImage[] images;
    private final long[] mutationGenerations;
    private final int[] mipOffsets;
    private final int[] sourceWidths;
    private final int[] sourceHeights;
    private final int[] interpolationOutputOffsets;
    private final TextureResidentBuffer buffer;
    private final int sourceBytes;
    private final int budgetChargeBytes;
    private final int interpolationScratchBytes;
    private final int frameWidth;
    private final int frameHeight;
    private final int columns;

    private GpuTextureInterpolationCompute.Binding interpolationBinding;
    private boolean interpolationBindingAttempted;
    private boolean closed;

    private GpuAnimatedTextureResidency(NativeImage[] images, long[] mutationGenerations,
                                        int[] mipOffsets, int[] sourceWidths, int[] sourceHeights,
                                        int[] interpolationOutputOffsets,
                                        TextureResidentBuffer buffer,
                                        int sourceBytes, int budgetChargeBytes,
                                        int interpolationScratchBytes,
                                        int frameWidth, int frameHeight, int columns) {
        this.images = images;
        this.mutationGenerations = mutationGenerations;
        this.mipOffsets = mipOffsets;
        this.sourceWidths = sourceWidths;
        this.sourceHeights = sourceHeights;
        this.interpolationOutputOffsets = interpolationOutputOffsets;
        this.buffer = buffer;
        this.sourceBytes = sourceBytes;
        this.budgetChargeBytes = budgetChargeBytes;
        this.interpolationScratchBytes = interpolationScratchBytes;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.columns = columns;
    }

    public static boolean enabled() {
        return !ciForceCpuPath
                && (COPY_ENABLED || GpuTextureInterpolationCompute.requested());
    }

    public static boolean interpolationEnabled() {
        return !ciForceCpuPath && GpuTextureInterpolationCompute.enabled();
    }

    static void setCiForceCpuPath(boolean force) {
        if(force && !Boolean.getBoolean("vulkanmod.ciScreenshotSmoke")) {
            throw new IllegalStateException("CPU-reference override is CI-only");
        }
        ciForceCpuPath = force;
    }

    public static GpuAnimatedTextureResidency tryCreate(
            String spriteId, NativeImage[] sourceImages,
            int frameWidth, int frameHeight) {
        if(!enabled() || sourceImages == null || sourceImages.length == 0
                || sourceImages.length > 32 || frameWidth <= 0 || frameHeight <= 0
                || !Device.getGraphicsQueue().hasActiveUploadBatch()) {
            return null;
        }

        NativeImage baseImage = sourceImages[0];
        if(baseImage == null || baseImage.getWidth() <= 0 || baseImage.getHeight() <= 0
                || baseImage.getWidth() % frameWidth != 0
                || baseImage.getHeight() % frameHeight != 0) {
            sourceRejected++;
            TextureTickAttribution.residentSourceRejected();
            return null;
        }

        int columns = baseImage.getWidth() / frameWidth;
        if(columns <= 0) {
            sourceRejected++;
            TextureTickAttribution.residentSourceRejected();
            return null;
        }

        NativeImage[] images = sourceImages.clone();
        long[] generations = new long[images.length];
        int[] offsets = new int[images.length];
        int[] widths = new int[images.length];
        int[] heights = new int[images.length];
        int[] interpolationOffsets = new int[images.length];

        long total = 0L;
        long scratchTotal = 0L;
        for(int mip = 0; mip < images.length; ++mip) {
            NativeImage image = images[mip];
            if(image == null) {
                sourceRejected++;
                TextureTickAttribution.residentSourceRejected();
                return null;
            }

            VNativeImageI nativeImage = (VNativeImageI)(Object)image;
            if(!nativeImage.vulkanmod$isRgba()) {
                sourceRejected++;
                TextureTickAttribution.residentSourceRejected();
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            if(width <= 0 || height <= 0) {
                sourceRejected++;
                TextureTickAttribution.residentSourceRejected();
                return null;
            }

            long bytes;
            try {
                bytes = Math.multiplyExact(Math.multiplyExact((long)width, height), 4L);
                if(total > Integer.MAX_VALUE || bytes > Integer.MAX_VALUE - total) {
                    capacityRejected++;
                    TextureTickAttribution.residentCapacityRejected();
                    return null;
                }
            } catch(ArithmeticException overflow) {
                capacityRejected++;
                TextureTickAttribution.residentCapacityRejected();
                return null;
            }

            ByteBuffer view = nativeImage.vulkanmod$getReadOnlyBuffer();
            if(view == null || view.remaining() < bytes) {
                sourceRejected++;
                TextureTickAttribution.residentSourceRejected();
                return null;
            }

            offsets[mip] = (int)total;
            widths[mip] = width;
            heights[mip] = height;
            generations[mip] = nativeImage.vulkanmod$getMutationGeneration();
            total += bytes;

            interpolationOffsets[mip] = (int)scratchTotal;
            int mipFrameWidth = frameWidth >> mip;
            int mipFrameHeight = frameHeight >> mip;
            if(mipFrameWidth > 0 && mipFrameHeight > 0) {
                try {
                    scratchTotal = Math.addExact(scratchTotal,
                            Math.multiplyExact(
                                    Math.multiplyExact((long)mipFrameWidth, mipFrameHeight),
                                    4L));
                } catch(ArithmeticException overflow) {
                    capacityRejected++;
                    TextureTickAttribution.residentCapacityRejected();
                    return null;
                }
                if(scratchTotal > Integer.MAX_VALUE) {
                    capacityRejected++;
                    TextureTickAttribution.residentCapacityRejected();
                    return null;
                }
            }
        }

        long budgetCharge = total;
        if(GpuTextureInterpolationCompute.enabled()) {
            budgetCharge += scratchTotal;
        }
        if(total <= 0L || total > LIMIT_BYTES || budgetCharge > LIMIT_BYTES
                || currentBytes > LIMIT_BYTES - budgetCharge) {
            capacityRejected++;
            TextureTickAttribution.residentCapacityRejected();
            return null;
        }

        int sourceBytes = (int)total;
        int scratchBytes = (int)scratchTotal;
        int chargeBytes = (int)budgetCharge;
        ByteBuffer packed = null;
        TextureResidentBuffer buffer = null;
        boolean reserved = false;
        try {
            currentBytes += chargeBytes;
            peakBytes = Math.max(peakBytes, currentBytes);
            reserved = true;

            packed = MemoryUtil.memAlloc(sourceBytes);
            for(int mip = 0; mip < images.length; ++mip) {
                VNativeImageI nativeImage = (VNativeImageI)(Object)images[mip];
                ByteBuffer view = nativeImage.vulkanmod$getReadOnlyBuffer();
                if(view == null) {
                    sourceRejected++;
                    TextureTickAttribution.residentSourceRejected();
                    return null;
                }
                int bytes = Math.multiplyExact(
                        Math.multiplyExact(widths[mip], heights[mip]), 4);
                view.limit(view.position() + bytes);
                packed.put(view);
            }
            packed.flip();

            buffer = new TextureResidentBuffer(sourceBytes);
            if(!VTextureSelector.uploadTextureResidentSource(buffer, packed)) {
                stagingRejected++;
                TextureTickAttribution.residentStagingRejected();
                return null;
            }

            admitted++;
            TextureTickAttribution.residentAdmitted(chargeBytes);
            TextureResidentBuffer owned = buffer;
            buffer = null;
            reserved = false;
            return new GpuAnimatedTextureResidency(
                    images, generations, offsets, widths, heights,
                    interpolationOffsets, owned,
                    sourceBytes, chargeBytes, scratchBytes,
                    frameWidth, frameHeight, columns);
        } catch(OutOfMemoryError | RuntimeException failure) {
            sourceRejected++;
            TextureTickAttribution.residentSourceRejected();
            return null;
        } finally {
            if(packed != null) {
                MemoryUtil.memFree(packed);
            }
            if(buffer != null) {
                final int releaseBytes = chargeBytes;
                buffer.retire(() -> currentBytes = Math.max(0L, currentBytes - releaseBytes));
            } else if(reserved) {
                currentBytes = Math.max(0L, currentBytes - chargeBytes);
            }
        }
    }

    public boolean copyToAtlas(VulkanImage atlas, int destX, int destY, int sourceX, int sourceY,
                               int frameWidth, int frameHeight, NativeImage[] currentImages) {
        if(this.closed || atlas == null || !this.matches(currentImages)) {
            if(!this.closed) {
                sourceInvalidated++;
                TextureTickAttribution.residentInvalidated();
            }
            return false;
        }

        int regions = VTextureSelector.copyResidentSpriteFrame(
                this.buffer, atlas, this.mipOffsets, this.sourceWidths, this.sourceHeights,
                destX, destY, sourceX, sourceY, frameWidth, frameHeight);
        if(regions <= 0) {
            return false;
        }

        long bytes = 0L;
        for(int mip = 0; mip < this.sourceWidths.length; ++mip) {
            int width = frameWidth >> mip;
            int height = frameHeight >> mip;
            if(width <= 0 || height <= 0) continue;
            bytes += (long)width * height * 4L;
        }

        totalCopyCalls++;
        totalCopyRegions += regions;
        totalCopyBytes += bytes;
        TextureTickAttribution.recordResidentCopy(regions, bytes);
        return true;
    }

    public boolean interpolateToAtlas(VulkanImage atlas, int destX, int destY,
                                      int currentIndex, int nextIndex,
                                      int subFrame, int duration) {
        if(this.closed || atlas == null || currentIndex == nextIndex
                || subFrame <= 0 || duration <= 0 || subFrame >= duration
                || !interpolationEnabled() || !this.sourcesValid()) {
            return false;
        }

        if(this.interpolationBinding == null) {
            if(this.interpolationBindingAttempted || this.interpolationScratchBytes <= 0) {
                return false;
            }
            this.interpolationBindingAttempted = true;
            this.interpolationBinding = GpuTextureInterpolationCompute.createBinding(
                    this.buffer, this.interpolationScratchBytes);
            if(this.interpolationBinding == null) {
                interpolationBindingRejected++;
                return false;
            }
        }

        GpuTextureInterpolationCompute.DispatchResult result =
                this.interpolationBinding.dispatch(
                        atlas, this.mipOffsets, this.sourceWidths, this.sourceHeights,
                        this.interpolationOutputOffsets,
                        this.frameWidth, this.frameHeight, this.columns,
                        currentIndex, nextIndex, subFrame, duration, destX, destY);
        if(result == null) {
            return false;
        }

        interpolationCalls++;
        interpolationDispatches += result.regions();
        interpolationPixels += result.pixels();
        interpolationBytes += result.bytes();
        TextureTickAttribution.recordResidentInterpolation(
                result.regions(), result.pixels(), result.bytes());
        return true;
    }

    public boolean sourcesValid() {
        if(this.closed) {
            return false;
        }
        for(int mip = 0; mip < this.images.length; ++mip) {
            NativeImage image = this.images[mip];
            if(image == null) {
                return false;
            }
            VNativeImageI nativeImage = (VNativeImageI)(Object)image;
            if(nativeImage.vulkanmod$getMutationGeneration()
                    != this.mutationGenerations[mip]) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(NativeImage[] currentImages) {
        if(currentImages == null || currentImages.length != this.images.length) {
            return false;
        }
        for(int mip = 0; mip < this.images.length; ++mip) {
            if(currentImages[mip] != this.images[mip]) {
                return false;
            }
        }
        return this.sourcesValid();
    }

    public void noteSourceInvalidated() {
        sourceInvalidated++;
        TextureTickAttribution.residentInvalidated();
    }

    @Override
    public void close() {
        if(this.closed) {
            return;
        }
        this.closed = true;
        if(this.interpolationBinding != null) {
            this.interpolationBinding.retire();
            this.interpolationBinding = null;
        }

        final int releaseBytes = this.budgetChargeBytes;
        this.buffer.retire(() -> {
            currentBytes = Math.max(0L, currentBytes - releaseBytes);
            TextureTickAttribution.residentReleased(releaseBytes);
        });
    }

    public static Stats stats() {
        return new Stats(enabled(), currentBytes, peakBytes, admitted, capacityRejected,
                sourceRejected, stagingRejected, sourceInvalidated,
                totalCopyCalls, totalCopyRegions, totalCopyBytes,
                interpolationCalls, interpolationDispatches,
                interpolationPixels, interpolationBytes, interpolationBindingRejected);
    }

    public record Stats(boolean enabled, long currentBytes, long peakBytes, long admitted,
                        long capacityRejected, long sourceRejected, long stagingRejected,
                        long sourceInvalidated, long totalCopyCalls, long totalCopyRegions,
                        long totalCopyBytes, long interpolationCalls,
                        long interpolationDispatches, long interpolationPixels,
                        long interpolationBytes, long interpolationBindingRejected) {
    }
}
