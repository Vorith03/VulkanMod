package net.vulkanmod.vulkan.memory;

import net.vulkanmod.render.chunk.util.Util;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.system.libc.LibCString.nmemcpy;
import static org.lwjgl.vulkan.VK10.*;

public class StagingBuffer extends Buffer {
    private int highWaterMark;
    private int resizeCount;

    public StagingBuffer(int bufferSize) {
        super(VK_BUFFER_USAGE_TRANSFER_SRC_BIT, MemoryTypes.HOST_MEM);
        this.usedBytes = 0;
        this.offset = 0;
        this.highWaterMark = 0;
        this.resizeCount = 0;

        this.createBuffer(bufferSize);
    }

    public void copyBuffer(int size, ByteBuffer byteBuffer) {

        if(size > this.bufferSize - this.usedBytes) {
            resizeBuffer(Math.max(this.bufferSize * 2, this.usedBytes + size));
        }

        nmemcpy(this.data.get(0) + this.usedBytes, MemoryUtil.memAddress(byteBuffer), size);

        offset = usedBytes;
        usedBytes += size;
        this.highWaterMark = Math.max(this.highWaterMark, this.usedBytes);
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
