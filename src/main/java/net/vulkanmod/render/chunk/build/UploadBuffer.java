package net.vulkanmod.render.chunk.build;

import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class UploadBuffer {

    private static final AtomicLong COPY_COUNT = new AtomicLong();
    private static final AtomicLong COPY_BYTES = new AtomicLong();
    private static final AtomicLong COPY_NANOS = new AtomicLong();

    public final int indexCount;
    public final boolean autoIndices;
    public final boolean indexOnly;
    private final ByteBuffer vertexBuffer;
    private final ByteBuffer indexBuffer;

    private boolean released = false;

    public UploadBuffer(TerrainBufferBuilder.RenderedBuffer renderedBuffer) {
        TerrainBufferBuilder.DrawState drawState = renderedBuffer.drawState();
        this.indexCount = drawState.indexCount();
        this.autoIndices = drawState.sequentialIndex();
        this.indexOnly = drawState.indexOnly();

        int copiedBytes = 0;
        long startNanos = System.nanoTime();

        if(!this.indexOnly) {
            ByteBuffer vertices = renderedBuffer.vertexBuffer();
            copiedBytes += vertices.remaining();
            this.vertexBuffer = Util.createCopy(vertices);
        } else {
            this.vertexBuffer = null;
        }

        if(!drawState.sequentialIndex()) {
            ByteBuffer indices = renderedBuffer.indexBuffer();
            copiedBytes += indices.remaining();
            this.indexBuffer = Util.createCopy(indices);
        } else {
            this.indexBuffer = null;
        }

        COPY_COUNT.incrementAndGet();
        COPY_BYTES.addAndGet(copiedBytes);
        COPY_NANOS.addAndGet(Math.max(0L, System.nanoTime() - startNanos));
    }

    public int indexCount() { return indexCount; }

    public ByteBuffer getVertexBuffer() { return vertexBuffer; }

    public ByteBuffer getIndexBuffer() { return indexBuffer; }

    static void resetCopyStats() {
        COPY_COUNT.set(0L);
        COPY_BYTES.set(0L);
        COPY_NANOS.set(0L);
    }

    static String getCopyStats() {
        long count = COPY_COUNT.get();
        long bytes = COPY_BYTES.get();
        long nanos = COPY_NANOS.get();
        double totalMs = nanos / 1_000_000.0D;
        double averageMs = count == 0L ? 0.0D : totalMs / count;
        return String.format(Locale.ROOT, "handoffCopy:%d/%.1fMiB/%.1fms avg:%.3fms",
                count, bytes / 1048576.0D, totalMs, averageMs);
    }

    public void release() {
        if(this.released)
            return;

        this.released = true;
        if(vertexBuffer != null)
            MemoryUtil.memFree(vertexBuffer);
        if(indexBuffer != null)
            MemoryUtil.memFree(indexBuffer);
    }
}
