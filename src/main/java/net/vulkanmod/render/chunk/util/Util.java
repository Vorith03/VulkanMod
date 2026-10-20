package net.vulkanmod.render.chunk.util;

import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class Util {

    public static final Direction[] DIRECTIONS = Direction.values();
    public static final Direction[] XZ_DIRECTIONS = getXzDirections();

    private static Direction[] getXzDirections() {
        Direction[] directions = new Direction[4];

        int i = 0;
        for(Direction direction : Direction.values()) {
            if(direction.getAxis() == Direction.Axis.X || direction.getAxis() == Direction.Axis.Z) {
                directions[i] = direction;
                ++i;
            }
        }
        return directions;
    }

    /** Floor log2 for strictly positive area dimensions. */
    public static int flooredLog(int value) {
        if (value <= 0) throw new IllegalArgumentException("Dimension must be positive");
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    public static int align(int value, int alignment) {
        return net.vulkanmod.vulkan.util.VUtil.align(value, alignment);
    }

    public static ByteBuffer createCopy(ByteBuffer src) {
        ByteBuffer ret = MemoryUtil.memAlloc(src.remaining());
        MemoryUtil.memCopy(src, ret);
        return ret;
    }
}
