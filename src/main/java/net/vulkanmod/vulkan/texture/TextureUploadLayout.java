package net.vulkanmod.vulkan.texture;

import java.nio.ByteBuffer;

/** Validated source rectangle and its tightly packed staging footprint. */
public record TextureUploadLayout(int sourceOffset, int sourceStride, int rowBytes,
                                  int height, int sourceSpan, int packedBytes) {
    public static TextureUploadLayout of(int width, int height, int skipRows, int skipPixels,
                                         int rowLength, int bytesPerPixel, int availableBytes) {
        int stridePixels = rowLength == 0 ? width : rowLength;
        if(width <= 0 || height <= 0 || bytesPerPixel <= 0 || skipRows < 0 || skipPixels < 0
                || stridePixels < width || (long)skipPixels + width > stridePixels) {
            throw new IllegalArgumentException("Invalid texture upload rectangle");
        }

        // Check against the ByteBuffer range before narrowing any value. Reject
        // hostile/overflowed geometry before touching mapped or source memory.
        long stride = (long)stridePixels * bytesPerPixel;
        long row = (long)width * bytesPerPixel;
        if(stride > Integer.MAX_VALUE || row > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Texture upload row exceeds ByteBuffer range");
        }
        long offset = (long)skipRows * stride + (long)skipPixels * bytesPerPixel;
        long span = (long)(height - 1) * stride + row;
        long packed = row * height;
        if(offset > availableBytes || span > availableBytes - offset) {
            throw new IllegalArgumentException("Texture upload exceeds source image");
        }
        return new TextureUploadLayout((int)offset, (int)stride, (int)row,
                height, (int)span, (int)packed);
    }

    /** Copy directly to mapped staging, without an intermediate packed allocation. */
    public void copyTo(ByteBuffer source, ByteBuffer destination, int destinationOffset) {
        int sourceStart = Math.addExact(source.position(), this.sourceOffset);
        if(sourceStart < source.position() || this.sourceSpan > source.limit() - sourceStart
                || destinationOffset < 0 || this.packedBytes > destination.limit() - destinationOffset) {
            throw new IllegalArgumentException("Texture copy exceeds buffer range");
        }
        if(this.rowBytes == this.sourceStride || this.height == 1) {
            destination.put(destinationOffset, source, sourceStart, this.packedBytes);
        } else {
            for(int y = 0; y < this.height; ++y) {
                destination.put(destinationOffset + y * this.rowBytes,
                        source, sourceStart + y * this.sourceStride, this.rowBytes);
            }
        }
    }
}
