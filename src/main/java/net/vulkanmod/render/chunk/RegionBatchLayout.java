package net.vulkanmod.render.chunk;

import java.nio.ByteBuffer;

/** CPU/GPU ABI: an 8x8x8 region, and Vulkan's five-word indexed draw command. */
public final class RegionBatchLayout {
    public static final int MAX_SECTIONS = 512;
    public static final int STRIDE = 20;

    private RegionBatchLayout() {}

    public static int drawLimit(int vulkanLimit) {
        return (int) Math.max(1L, Math.min(MAX_SECTIONS, Integer.toUnsignedLong(vulkanLimit)));
    }

    public static int packSection(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= 128 || y >= 128 || z >= 128 || ((x | y | z) & 15) != 0) {
            throw new IllegalArgumentException("Section must be aligned inside its 128-block region");
        }
        return (x >> 4) | ((y >> 4) << 3) | ((z >> 4) << 6);
    }

    public static void putCommand(ByteBuffer target, int indexCount, int firstIndex,
                                  int vertexOffset, int packedSection) {
        target.putInt(indexCount).putInt(1).putInt(firstIndex).putInt(vertexOffset).putInt(packedSection);
    }
}
