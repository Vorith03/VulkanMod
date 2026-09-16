package net.vulkanmod.vulkan.memory;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

/** Terrain vertex storage that can be written by compute and consumed by vertex input. */
public final class TerrainVertexBuffer extends Buffer {
    public TerrainVertexBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, type);
        this.createBuffer(size);
    }
}
