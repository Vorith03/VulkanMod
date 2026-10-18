package net.vulkanmod.interfaces;

import net.vulkanmod.render.particle.ParticleQuadBatch;

public interface ParticleBillboardBuffer {
    ParticleQuadBatch vulkanmod$billboards();
    void vulkanmod$billboards(ParticleQuadBatch batch);
}
