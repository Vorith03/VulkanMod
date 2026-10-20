package net.vulkanmod.vulkan.memory;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

/** Device/host memory buffer intended for shader storage access and staged copies. */
public final class StorageBuffer extends Buffer {
    @Override
    public Retirement retirement() { return Retirement.FRAME_FENCE; }

    public StorageBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                | VK_BUFFER_USAGE_TRANSFER_DST_BIT
                | VK_BUFFER_USAGE_TRANSFER_SRC_BIT, type);
        this.createBuffer(size);
    }
}
