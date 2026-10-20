package net.vulkanmod.vulkan.util;

import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Behavioral native-access/lifetime and legacy upload position contracts. */
public final class MappedBufferContractTest {
    public static void main(String[] args) {
        MappedBuffer owned = new MappedBuffer(16);
        owned.putInt(0, 0x7fc00001);
        require(Float.floatToRawIntBits(owned.getFloat(0)) == 0x7fc00001, "NaN bits");
        owned.putFloat(4, -0f);
        require(owned.getInt(4) == 0x80000000, "Signed zero");
        owned.free(); owned.free();
        expectClosed(owned::address); expectClosed(owned::byteBuffer);
        expectClosed(() -> owned.getInt(0)); expectClosed(() -> owned.putFloat(0, 1));
        ByteBuffer owner = MemoryUtil.memAlloc(64).order(ByteOrder.nativeOrder());
        ByteBuffer dst = MemoryUtil.memAlloc(32).order(ByteOrder.nativeOrder());
        try {
            owner.position(8).limit(40);
            ByteBuffer slice = owner.slice().order(ByteOrder.nativeOrder());
            MappedBuffer borrowed = MappedBuffer.createFromBuffer(slice);
            borrowed.putInt(0, 0x12345678);
            require(owner.getInt(8) == 0x12345678, "Borrowed slice base");
            borrowed.free(); borrowed.free();
            expectClosed(borrowed::address);
            owner.putInt(8, 123); // Borrowed close must not release the owner.
            for (int i=0; i<32; i++) slice.put(i, (byte)i);
            slice.position(3).limit(20); dst.position(1).limit(24);
            VUtil.memcpy(slice, dst, 9, 5);
            for (int i=0; i<6; i++) require(dst.get(5+i) == (byte)(3+i), "Absolute limit copy");
            require(slice.position()==0 && slice.limit()==32, "Source reset");
            require(dst.position()==5 && dst.limit()==24, "Destination semantics");
            require(owner.position()==8 && owner.limit()==40, "Parent slice state unchanged");
        } finally { MemoryUtil.memFree(dst); MemoryUtil.memFree(owner); }
        System.out.println("Mapped buffer/copy behavior passed");
    }
    private static void expectClosed(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Closed wrapper accepted access");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
