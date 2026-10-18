package net.vulkanmod.render.particle;

import net.vulkanmod.vulkan.memory.VertexBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import static org.lwjgl.vulkan.VK10.*;

/** Only shared frame-slot buffers opt into storage usage; ordinary model buffers stay unchanged. */
public final class ParticleVertexBuffer extends VertexBuffer {
    public ParticleVertexBuffer(int bytes) { super(bytes,MemoryTypes.HOST_MEM,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT); }
    @Override protected void resizeBuffer(int bytes) {
        ParticleBillboardCompute.retireBuffer(getId());
        super.resizeBuffer(bytes);
    }
    @Override public void freeBuffer() {
        ParticleBillboardCompute.retireBuffer(getId());
        super.freeBuffer();
    }
}
