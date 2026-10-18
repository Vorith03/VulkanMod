package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.vulkanmod.interfaces.ParticleBillboardBuffer;
import net.vulkanmod.render.particle.ParticleQuadBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.RenderedBuffer.class)
public class ParticleRenderedBufferMixin implements ParticleBillboardBuffer {
    @Unique private ParticleQuadBatch vulkanmod$batch;
    public ParticleQuadBatch vulkanmod$billboards() { return vulkanmod$batch; }
    public void vulkanmod$billboards(ParticleQuadBatch batch) { vulkanmod$batch = batch; }
}
