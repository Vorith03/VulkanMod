package net.vulkanmod.vulkan.util;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Owned native allocation or borrowed view. Byte offsets are relative to the
 * view base (independent of position). Borrowers never release the backing owner.
 * Render-thread confined: callers must exclude concurrent release and must not
 * retain exposed addresses/views beyond the owner's lifetime.
 */
public class MappedBuffer {

    private final ByteBuffer buffer;
    private final long ptr;
    private final boolean owned;
    private boolean freed;

    /** Borrow the entire direct view; its owner must outlive this wrapper. */
    public static MappedBuffer createFromBuffer(ByteBuffer buffer) {
        return new MappedBuffer(buffer, MemoryUtil.memAddress0(buffer), false);
    }

    MappedBuffer(ByteBuffer buffer, long ptr, boolean owned) {
        this.buffer = buffer;
        this.ptr = ptr;
        this.owned = owned;
    }

    /** Allocate owned native storage, explicitly released by free(). */
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

    public boolean isFreed() { return this.freed; }

    public void requireOpen() {
        if (this.freed) throw new IllegalStateException("MappedBuffer is freed");
    }

    /** Borrowed escape; does not extend lifetime or prevent concurrent release. */
    public long address() { requireOpen(); return this.ptr; }

    /** Borrowed mutable view; do not retain across release/rebind. */
    public ByteBuffer byteBuffer() { requireOpen(); return this.buffer; }

    public void putFloat(int idx, float f) {
        requireOpen();
        MemoryUtil.memPutFloat(ptr + idx, f);
    }

    public void putInt(int idx, int f) {
        requireOpen();
        MemoryUtil.memPutInt(ptr + idx, f);
    }

    public float getFloat(int idx) {
        requireOpen();
        return MemoryUtil.memGetFloat(ptr + idx);
    }

    public int getInt(int idx) {
        requireOpen();
        return MemoryUtil.memGetInt(ptr + idx);
    }
}
