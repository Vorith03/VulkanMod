package net.vulkanmod.vulkan.util;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collection;

import static org.lwjgl.system.MemoryStack.stackGet;

public class VUtil {
    public static final int UINT32_MAX = 0xFFFFFFFF;
    public static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;

    public static PointerBuffer asPointerBuffer(Collection<String> collection) {

        MemoryStack stack = stackGet();

        PointerBuffer buffer = stack.mallocPointer(collection.size());

        collection.stream()
                .map(stack::UTF8)
                .forEach(buffer::put);

        return buffer.rewind();
    }

    /** Copy remaining source bytes at current positions, then reset source to full capacity.
     * Destination position/limit are retained; the explicit-offset overload moves its position.
     * These are legacy upload semantics, not position-preserving memcpy APIs.
     */
    public static void memcpy(ByteBuffer src, ByteBuffer dst) {
        MemoryUtil.memCopy(src, dst);
        src.limit(src.capacity()).rewind();
    }

    /** Move destination to dstByteOffset, copy remaining source, then reset source. */
    public static void memcpy(ByteBuffer src, ByteBuffer dst, long dstByteOffset) {
        dst.position((int)dstByteOffset);
        MemoryUtil.memCopy(src, dst);
        src.limit(src.capacity()).rewind();
    }

    /** sourceByteLimit is an absolute limit, NOT a count relative to src.position().
     * Move destination, set source limit, copy remaining bytes, then reset source.
     */
    public static void memcpy(ByteBuffer src, ByteBuffer dst, int sourceByteLimit, long dstByteOffset) {
        dst.position((int)dstByteOffset);
        src.limit(sourceByteLimit);
        MemoryUtil.memCopy(src, dst);
        src.limit(src.capacity()).rewind();
    }

    public static int align(int x, int align) {
        int r = x % align;
        return r == 0 ? x : x + align - r;
    }

}
