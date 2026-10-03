package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.memory.IndexBuffer;
import net.vulkanmod.vulkan.memory.VertexBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** Immutable shared mesh owner. Cache once per model/material generation, retire after its last draw. */
public final class SharedModelBuffer implements AutoCloseable {
    private VertexBuffer vertices;
    private IndexBuffer indices;
    private final ModelGeometry geometry;

    public SharedModelBuffer(ModelGeometry geometry) {
        RenderSystem.assertOnRenderThread();
        if(geometry.vertexCount() == 0 || geometry.indexCount() == 0)
            throw new IllegalArgumentException("Empty shared mesh");
        this.geometry = geometry;
        ByteBuffer vertexData = null, indexData = null;
        try {
            vertexData = MemoryUtil.memAlloc(geometry.vertexCount()*ModelGeometry.STRIDE);
            indexData = MemoryUtil.memAlloc(geometry.indexCount()*geometry.indexBytes());
            vertexData.put(geometry.vertices()).flip(); indexData.put(geometry.indices()).flip();
            vertices = new VertexBuffer(vertexData.remaining());
            vertices.copyToVertexBuffer(ModelGeometry.STRIDE,geometry.vertexCount(),vertexData);
            indices = new IndexBuffer(indexData.remaining()); indices.copyBuffer(indexData);
        } catch(RuntimeException | Error failure) { close(); throw failure; }
        finally { MemoryUtil.memFree(vertexData); MemoryUtil.memFree(indexData); }
    }
    public VertexBuffer vertices() { requireOpen(); return vertices; }
    public IndexBuffer indices() { requireOpen(); return indices; }
    public int indexType() { return geometry.indexBytes() == 2 ? VK_INDEX_TYPE_UINT16 : VK_INDEX_TYPE_UINT32; }
    public ModelGeometry geometry() { return geometry; }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if(vertices != null) { vertices.freeBuffer(); vertices = null; }
        if(indices != null) { indices.freeBuffer(); indices = null; }
    }
    private void requireOpen() { if(vertices == null || indices == null) throw new IllegalStateException("Shared mesh is closed"); }
}
