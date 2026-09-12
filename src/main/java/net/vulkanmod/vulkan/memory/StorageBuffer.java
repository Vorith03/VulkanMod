package net.vulkanmod.vulkan.memory;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

/** Device/host memory buffer intended for shader storage access. */
public final class StorageBuffer extends Buffer {
    public StorageBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, type);
        this.createBuffer(size);
    }
}
