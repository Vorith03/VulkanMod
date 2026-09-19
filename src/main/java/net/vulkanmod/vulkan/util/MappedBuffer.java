package net.vulkanmod.vulkan.util;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class MappedBuffer {

    public final ByteBuffer buffer;
    public final long ptr;
    private final boolean owned;
    private boolean freed;

    public static MappedBuffer createFromBuffer(ByteBuffer buffer) {
        return new MappedBuffer(buffer, MemoryUtil.memAddress0(buffer), false);
    }

    MappedBuffer(ByteBuffer buffer, long ptr, boolean owned) {
        this.buffer = buffer;
        this.ptr = ptr;
        this.owned = owned;
    }

    public MappedBuffer(int size) {
        this.buffer = MemoryUtil.memAlloc(size);
        this.ptr = MemoryUtil.memAddress0(this.buffer);
        this.owned = true;
    }

    public synchronized void free() {
        if(this.freed)
            return;
        if(this.owned)
            MemoryUtil.memFree(this.buffer);
        this.freed = true;
    }

    public void putFloat(int idx, float f) {
        VUtil.UNSAFE.putFloat(ptr + idx, f);
    }

    public void putInt(int idx, int f) {
        VUtil.UNSAFE.putInt(ptr + idx, f);
    }

    public float getFloat(int idx) {
        return VUtil.UNSAFE.getFloat(ptr + idx);
    }

    public int getInt(int idx) {
        return VUtil.UNSAFE.getInt(ptr + idx);
    }
}
