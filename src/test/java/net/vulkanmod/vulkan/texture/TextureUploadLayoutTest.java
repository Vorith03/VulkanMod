package net.vulkanmod.vulkan.texture;

import java.nio.ByteBuffer;

public final class TextureUploadLayoutTest {
    public static void main(String[] args) {
        for(boolean direct : new boolean[]{false, true}) {
            for(int bytes : new int[]{1, 2, 4}) {
                for(int height : new int[]{1, 2, 7}) {
                    for(int stride : new int[]{5, 13, 64}) {
                        verifyCopy(direct, bytes, height, stride);
                    }
                }
            }
        }
        TextureUploadLayout wide = TextureUploadLayout.of(64, 64, 0, 0, 4096, 4, 4096 * 64 * 4);
        require(wide.packedBytes() == 16384, "wide sheet stages only the requested frame");
        require(wide.sourceSpan() == 1032448, "wide source span oracle");
        rejects(() -> TextureUploadLayout.of(4, 4, 0, 0, 4, 4, 63));
        rejects(() -> TextureUploadLayout.of(4, 4, 0, 1, 4, 4, 100));
        rejects(() -> TextureUploadLayout.of(4, 4, -1, 0, 4, 4, 100));
        rejects(() -> TextureUploadLayout.of(1, 1, 0, 0, -1, 4, 100));
        rejects(() -> TextureUploadLayout.of(Integer.MAX_VALUE, 2, 0, 0, 0, 4, Integer.MAX_VALUE));
        rejects(() -> TextureUploadLayout.of(1, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                Integer.MAX_VALUE, 1, Integer.MAX_VALUE));
        System.out.println("Packed texture upload layout tests passed: pixels, positions, bounds, 1032448 -> 16384 bytes");
    }

    private static void verifyCopy(boolean direct, int bytes, int height, int stride) {
        int width = 5, skipX = stride == width ? 0 : 3, skipY = 2, prefix = 7;
        int sourceSize = prefix + stride * (height + skipY) * bytes;
        ByteBuffer source = direct ? ByteBuffer.allocateDirect(sourceSize + 9) : ByteBuffer.allocate(sourceSize + 9);
        for(int i = 0; i < source.capacity(); i++) source.put(i, (byte)(i * 31));
        source.position(prefix).limit(sourceSize);
        TextureUploadLayout layout = TextureUploadLayout.of(width, height, skipY, skipX,
                stride == width ? 0 : stride, bytes, source.remaining());
        int offset = 11, packed = width * height * bytes;
        ByteBuffer dest = direct ? ByteBuffer.allocateDirect(offset + packed + 13) : ByteBuffer.allocate(offset + packed + 13);
        for(int i = 0; i < dest.capacity(); i++) dest.put(i, (byte)0x5A);
        dest.position(3);
        layout.copyTo(source, dest, offset);
        require(source.position() == prefix && source.limit() == sourceSize && dest.position() == 3,
                "copy must preserve caller buffer state");
        for(int i = 0; i < dest.capacity(); i++) {
            byte expected = 0x5A;
            if(i >= offset && i < offset + packed) {
                int p = i - offset, y = p / (width * bytes), xByte = p % (width * bytes);
                expected = source.get(prefix + ((skipY + y) * stride + skipX) * bytes + xByte);
            }
            require(dest.get(i) == expected, "pixel or sentinel mismatch at " + i);
        }
        rejects(() -> layout.copyTo(source, dest, dest.limit() - packed + 1));
        source.limit(source.position() + layout.sourceOffset() + layout.sourceSpan() - 1);
        rejects(() -> layout.copyTo(source, dest, offset));
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid upload range was accepted");
    }

    private static void require(boolean value, String message) {
        if(!value) throw new AssertionError(message);
    }
}
