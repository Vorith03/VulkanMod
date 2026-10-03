package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.interfaces.BufferBuilderMemory;

/** Private synchronous CPU batches. Abort/retirement frees the native builder rather than only resetting it. */
final class OwnedBufferSource implements AutoCloseable {
    private BufferBuilder builder;
    private MultiBufferSource.BufferSource source;
    private boolean closed;

    VertexConsumer getBuffer(RenderType type) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("CPU batch source is retired");
        if (builder == null) {
            builder = new BufferBuilder(256);
            source = MultiBufferSource.immediate(builder);
        }
        return source.getBuffer(type);
    }
    void endBatch(RenderType type) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("CPU batch source is retired");
        if (source != null) source.endBatch(type);
    }
    long retainedBytes() {
        return builder == null ? 0 : ((BufferBuilderMemory)builder).vulkanmod$retainedBytes();
    }
    /** Caller has finished synchronous consumers; no RenderedBuffer may survive this boundary. */
    void discard() {
        RenderSystem.assertOnRenderThread();
        if (builder != null) ((BufferBuilderMemory)builder).vulkanmod$releaseMemory();
        source = null;
        builder = null;
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        discard();
        closed = true;
    }
}
