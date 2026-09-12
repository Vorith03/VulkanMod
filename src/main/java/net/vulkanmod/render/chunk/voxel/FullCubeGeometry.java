package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;

/** Pure baked-quad geometry checks shared by model qualification and CPU-only tests. */
final class FullCubeGeometry {
    private static final float EPSILON = 1.0e-5f;

    private FullCubeGeometry() {}

    /**
     * Accept only one canonical unit-cube face: four unique corners on the requested
     * x/y/z boundary plane. The first three packed vertex words are Minecraft's
     * position floats; any remaining vertex attributes are intentionally ignored
     * here and must be qualified separately before a CPU fallback can be removed.
     */
    static boolean isUnitFace(int[] vertices, Direction direction) {
        if(vertices == null || direction == null || vertices.length % 4 != 0)
            return false;

        int stride = vertices.length / 4;
        if(stride < 3)
            return false;

        boolean[] corners = new boolean[4];
        float expectedPlane = switch(direction) {
            case DOWN, NORTH, WEST -> 0.0f;
            case UP, SOUTH, EAST -> 1.0f;
        };

        for(int vertex = 0; vertex < 4; ++vertex) {
            int base = vertex * stride;
            float x = Float.intBitsToFloat(vertices[base]);
            float y = Float.intBitsToFloat(vertices[base + 1]);
            float z = Float.intBitsToFloat(vertices[base + 2]);
            if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
                return false;

            float plane;
            float u;
            float v;
            switch(direction.getAxis()) {
                case X -> {
                    plane = x;
                    u = y;
                    v = z;
                }
                case Y -> {
                    plane = y;
                    u = x;
                    v = z;
                }
                case Z -> {
                    plane = z;
                    u = x;
                    v = y;
                }
                default -> throw new IllegalStateException("Unknown direction axis");
            }

            if(!near(plane, expectedPlane))
                return false;
            int uBit = endpoint(u);
            int vBit = endpoint(v);
            if(uBit < 0 || vBit < 0)
                return false;

            int corner = uBit | (vBit << 1);
            if(corners[corner])
                return false;
            corners[corner] = true;
        }

        return corners[0] && corners[1] && corners[2] && corners[3];
    }

    private static int endpoint(float value) {
        if(near(value, 0.0f)) return 0;
        if(near(value, 1.0f)) return 1;
        return -1;
    }

    private static boolean near(float value, float expected) {
        return Math.abs(value - expected) <= EPSILON;
    }
}
