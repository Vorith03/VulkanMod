package net.vulkanmod.vulkan.shader.cache;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Vulkan's v1 cache header is tightly packed and little endian on every host. */
public final class PipelineCacheIdentity {
    private PipelineCacheIdentity() {}

    public static boolean matches(byte[] data, int vendor, int device, byte[] uuid) {
        if(data == null || data.length < 32 || uuid.length != 16) return false;
        ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        return header.getInt(0) == 32 && header.getInt(4) == 1
                && header.getInt(8) == vendor && header.getInt(12) == device
                && Arrays.equals(Arrays.copyOfRange(data, 16, 32), uuid);
    }
}
