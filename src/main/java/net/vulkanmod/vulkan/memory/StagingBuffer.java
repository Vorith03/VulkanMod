package net.vulkanmod.vulkan.memory;

import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.vulkan.texture.TextureUploadLayout;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.system.libc.LibCString.nmemcpy;
import static org.lwjgl.vulkan.VK10.*;

public class StagingBuffer extends Buffer {
    private int highWaterMark;
    private int resizeCount;
    private int growthLimit = Integer.MAX_VALUE;

    public StagingBuffer(int bufferSize) {
        super(VK_BUFFER_USAGE_TRANSFER_SRC_BIT, MemoryTypes.HOST_MEM);
        this.usedBytes = 0;
        this.offset = 0;
        this.highWaterMark = 0;
        this.resizeCount = 0;

        this.createBuffer(bufferSize);
    }

    public void copyBuffer(int size, ByteBuffer byteBuffer) {
        copyBuffer(size, byteBuffer, this.growthLimit);
    }

    public void copyTexture(ByteBuffer source, TextureUploadLayout layout, int alignment) {
        if(alignment <= 0)
            throw new IllegalArgumentException("Invalid texture staging alignment");
        long alignedUsed = ((long)this.usedBytes + alignment - 1L) / alignment * alignment;
        long required = alignedUsed + layout.packedBytes();
        if(required > Integer.MAX_VALUE)
            throw new IllegalStateException("Staging buffer exceeds 2 GiB addressable range");
        if(required > this.bufferSize) {
            resizeBuffer(calculateGrowthSize(this.bufferSize, (int)required, this.growthLimit));
        }
        layout.copyTo(source, MemoryUtil.memByteBuffer(this.data.get(0), this.bufferSize), (int)alignedUsed);
        this.offset = (int)alignedUsed;
        this.usedBytes = (int)required;
        this.highWaterMark = Math.max(this.highWaterMark, this.usedBytes);
    }

    /**
     * Copy into this staging buffer while limiting geometric growth. The limit is
     * a growth cap, not a hard maximum: an individual upload larger than the cap
     * is still allowed, but the buffer grows only to the required size instead of
     * doubling far beyond it.
     */
    public void copyBuffer(int size, ByteBuffer byteBuffer, int growthLimit) {
        if(size < 0)
            throw new IllegalArgumentException("Negative staging copy size");

        long requiredBytes = (long)this.usedBytes + size;
        if(requiredBytes > Integer.MAX_VALUE)
            throw new IllegalStateException("Staging buffer exceeds 2 GiB addressable range");

        if(requiredBytes > this.bufferSize) {
            resizeBuffer(calculateGrowthSize(this.bufferSize, (int)requiredBytes, growthLimit));
        }

        nmemcpy(this.data.get(0) + this.usedBytes, MemoryUtil.memAddress(byteBuffer), size);

        offset = usedBytes;
        usedBytes += size;
        this.highWaterMark = Math.max(this.highWaterMark, this.usedBytes);
    }

    /**
     * Temporarily cap geometric growth for a caller that owns the synchronous
     * staging copy. Restore Integer.MAX_VALUE after that copy when normal growth
     * policy should resume.
     */
    public void setGrowthLimit(int growthLimit) {
        if(growthLimit <= 0)
            throw new IllegalArgumentException("Staging growth limit must be positive");
        this.growthLimit = growthLimit;
    }

    /**
     * Returns true when appending this copy would exceed a caller-selected batch
     * budget. An empty buffer is never flushed: callers must allow a single upload
     * larger than the budget (or split that upload themselves).
     */
    public boolean wouldExceedUsageLimit(int size, int alignment, int usageLimit) {
        return wouldExceedUsageLimit(this.usedBytes, size, alignment, usageLimit);
    }

    static boolean wouldExceedUsageLimit(int usedBytes, int size, int alignment, int usageLimit) {
        if(usedBytes <= 0)
            return false;
        if(size < 0 || alignment <= 0 || usageLimit <= 0)
            throw new IllegalArgumentException("Invalid staging usage-limit arguments");

        long alignedUsed = ((long)usedBytes + alignment - 1L) / alignment * alignment;
        return alignedUsed + size > usageLimit;
    }

    static int calculateGrowthSize(int currentSize, int requiredSize, int growthLimit) {
        if(currentSize <= 0 || requiredSize <= 0 || growthLimit <= 0)
            throw new IllegalArgumentException("Invalid staging growth arguments");

        long doubled = Math.min((long)Integer.MAX_VALUE, (long)currentSize * 2L);
        long target = Math.max(doubled, requiredSize);

        if(target > growthLimit) {
            // Do not retain a geometrically oversized host allocation merely
            // because one copy crossed the normal growth boundary.
            target = Math.max((long)requiredSize, growthLimit);
        }

        if(target > Integer.MAX_VALUE)
            throw new IllegalStateException("Staging buffer exceeds 2 GiB addressable range");

        return (int)target;
    }

    public void align(int alignment) {
        int alignedValue = Util.align(usedBytes, alignment);

        if(alignedValue > this.bufferSize) {
            resizeBuffer(Math.max(this.bufferSize * 2, alignedValue));
        }

        usedBytes = alignedValue;
        this.highWaterMark = Math.max(this.highWaterMark, this.usedBytes);
    }

    private void resizeBuffer(int newSize) {
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer(newSize);
        this.resizeCount++;
    }

    public int getHighWaterMark() {
        return this.highWaterMark;
    }

    public int getResizeCount() {
        return this.resizeCount;
    }
}
