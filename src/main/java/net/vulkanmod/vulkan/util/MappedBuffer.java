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
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + idx, f);
    }

    public void putInt(int idx, int f) {
        org.lwjgl.system.MemoryUtil.memPutInt(ptr + idx, f);
    }

    public float getFloat(int idx) {
        return org.lwjgl.system.MemoryUtil.memGetFloat(ptr + idx);
    }

    public int getInt(int idx) {
        return org.lwjgl.system.MemoryUtil.memGetInt(ptr + idx);
    }
}
