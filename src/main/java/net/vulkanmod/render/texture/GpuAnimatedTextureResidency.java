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
 * O3 experimental discrete-frame residency. Forge retains animation clock/frame
 * ownership; only immutable source-byte movement is replaced. Interpolated and
 * custom sources stay on the CPU path.
 */
public final class GpuAnimatedTextureResidency implements AutoCloseable {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.gpuAnimatedTextureCopies");
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
    private static boolean ciForceCpuPath;

    private final NativeImage[] images;
    private final long[] mutationGenerations;
    private final int[] mipOffsets;
    private final int[] sourceWidths;
    private final int[] sourceHeights;
    private final TextureResidentBuffer buffer;
    private final int residentBytes;
    private boolean closed;

    private GpuAnimatedTextureResidency(NativeImage[] images, long[] mutationGenerations,
                                        int[] mipOffsets, int[] sourceWidths, int[] sourceHeights,
                                        TextureResidentBuffer buffer, int residentBytes) {
        this.images = images;
        this.mutationGenerations = mutationGenerations;
        this.mipOffsets = mipOffsets;
        this.sourceWidths = sourceWidths;
        this.sourceHeights = sourceHeights;
        this.buffer = buffer;
        this.residentBytes = residentBytes;
    }

    public static boolean enabled() {
        return ENABLED && !ciForceCpuPath;
    }

    static void setCiForceCpuPath(boolean force) {
        if(force && !Boolean.getBoolean("vulkanmod.ciScreenshotSmoke")) {
            throw new IllegalStateException("CPU-reference override is CI-only");
        }
        ciForceCpuPath = force;
    }

    public static GpuAnimatedTextureResidency tryCreate(String spriteId, NativeImage[] sourceImages) {
        if(!enabled() || sourceImages == null || sourceImages.length == 0
                || sourceImages.length > 32 || !Device.getGraphicsQueue().hasActiveUploadBatch()) {
            return null;
        }

        NativeImage[] images = sourceImages.clone();
        long[] generations = new long[images.length];
        int[] offsets = new int[images.length];
        int[] widths = new int[images.length];
        int[] heights = new int[images.length];

        long total = 0L;
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
        }

        if(total <= 0L || total > LIMIT_BYTES || currentBytes > LIMIT_BYTES - total) {
            capacityRejected++;
            TextureTickAttribution.residentCapacityRejected();
            return null;
        }

        int residentBytes = (int)total;
        ByteBuffer packed = null;
        TextureResidentBuffer buffer = null;
        boolean reserved = false;
        try {
            currentBytes += residentBytes;
            peakBytes = Math.max(peakBytes, currentBytes);
            reserved = true;

            packed = MemoryUtil.memAlloc(residentBytes);
            for(int mip = 0; mip < images.length; ++mip) {
                VNativeImageI nativeImage = (VNativeImageI)(Object)images[mip];
                ByteBuffer view = nativeImage.vulkanmod$getReadOnlyBuffer();
                if(view == null) {
                    sourceRejected++;
                    TextureTickAttribution.residentSourceRejected();
                    return null;
                }
                int bytes = Math.multiplyExact(Math.multiplyExact(widths[mip], heights[mip]), 4);
                view.limit(view.position() + bytes);
                packed.put(view);
            }
            packed.flip();

            buffer = new TextureResidentBuffer(residentBytes);
            if(!VTextureSelector.uploadTextureResidentSource(buffer, packed)) {
                stagingRejected++;
                TextureTickAttribution.residentStagingRejected();
                return null;
            }

            admitted++;
            TextureTickAttribution.residentAdmitted(residentBytes);
            TextureResidentBuffer owned = buffer;
            buffer = null;
            reserved = false;
            return new GpuAnimatedTextureResidency(
                    images, generations, offsets, widths, heights, owned, residentBytes);
        } catch(OutOfMemoryError | RuntimeException failure) {
            sourceRejected++;
            TextureTickAttribution.residentSourceRejected();
            return null;
        } finally {
            if(packed != null) {
                MemoryUtil.memFree(packed);
            }
            if(buffer != null) {
                final int releaseBytes = residentBytes;
                buffer.retire(() -> currentBytes = Math.max(0L, currentBytes - releaseBytes));
            } else if(reserved) {
                currentBytes = Math.max(0L, currentBytes - residentBytes);
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
            sourceInvalidated++;
            TextureTickAttribution.residentInvalidated();
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

    private boolean matches(NativeImage[] currentImages) {
        if(currentImages == null || currentImages.length != this.images.length) {
            return false;
        }
        for(int mip = 0; mip < this.images.length; ++mip) {
            if(currentImages[mip] != this.images[mip]) {
                return false;
            }
            VNativeImageI nativeImage = (VNativeImageI)(Object)currentImages[mip];
            if(nativeImage.vulkanmod$getMutationGeneration() != this.mutationGenerations[mip]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        if(this.closed) {
            return;
        }
        this.closed = true;
        final int releaseBytes = this.residentBytes;
        this.buffer.retire(() -> {
            currentBytes = Math.max(0L, currentBytes - releaseBytes);
            TextureTickAttribution.residentReleased(releaseBytes);
        });
    }

    public static Stats stats() {
        return new Stats(ENABLED, currentBytes, peakBytes, admitted, capacityRejected,
                sourceRejected, stagingRejected, sourceInvalidated,
                totalCopyCalls, totalCopyRegions, totalCopyBytes);
    }

    public record Stats(boolean enabled, long currentBytes, long peakBytes, long admitted,
                        long capacityRejected, long sourceRejected, long stagingRejected,
                        long sourceInvalidated, long totalCopyCalls, long totalCopyRegions,
                        long totalCopyBytes) {
    }
}
