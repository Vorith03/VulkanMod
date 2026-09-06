package net.vulkanmod.render.chunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;

public final class RegionBatchLayoutTest {
    public static void main(String[] args) {
        var ids = new HashSet<Integer>();
        for (int x = 0; x < 128; x += 16) {
            for (int y = 0; y < 128; y += 16) {
                for (int z = 0; z < 128; z += 16) {
                    int id = RegionBatchLayout.packSection(x, y, z);
                    require(ids.add(id), "Unique section ID");
                    // Mirror the GLSL decode, covering all region corners and edges.
                    require((id & 7) * 16 == x, "X shader decode");
                    require(((id >>> 3) & 7) * 16 == y, "Y shader decode");
                    require(((id >>> 6) & 7) * 16 == z, "Z shader decode");
                    for (int origin : new int[]{-30000000, -128, 0, 29999872}) {
                        require(RegionBatchLayout.packSection((origin + x) - origin, y, z) == id,
                                "World-coordinate translation");
                    }
                }
            }
        }
        require(ids.size() == 512, "Full region capacity");
        for (int bad : new int[]{-16, 1, 127, 128, Integer.MAX_VALUE}) {
            reject(bad, 0, 0); reject(0, bad, 0); reject(0, 0, bad);
        }
        ByteBuffer commands = ByteBuffer.allocate(40).order(ByteOrder.nativeOrder());
        RegionBatchLayout.putCommand(commands, 36, 12, -24, 511);
        RegionBatchLayout.putCommand(commands, 6, 0, 8, 0);
        require(commands.position() == 40, "Two 20-byte commands");
        int[] words = {36, 1, 12, -24, 511, 6, 1, 0, 8, 0};
        commands.flip();
        for (int word : words) require(commands.getInt() == word, "Vulkan indexed-command ABI");
        require(RegionBatchLayout.drawLimit(-1) == 512, "Unsigned Vulkan limit");
        require(RegionBatchLayout.drawLimit(128) == 128, "Device batch limit");
        require(RegionBatchLayout.drawLimit(1) == 1, "Single draw limit");
        System.out.println("Terrain region layout tests passed");
    }

    private static void reject(int x, int y, int z) {
        try { RegionBatchLayout.packSection(x, y, z); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid section accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
