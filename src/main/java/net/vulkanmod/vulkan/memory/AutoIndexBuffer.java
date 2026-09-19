package net.vulkanmod.vulkan.memory;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT16;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;

public class AutoIndexBuffer {
    int vertexCount;
    DrawType drawType;
    IndexBuffer indexBuffer;
    IndexBuffer.IndexType indexType;

    public AutoIndexBuffer(int vertexCount, DrawType type) {
        this.drawType = type;

        createIndexBuffer(vertexCount);
    }

    private void createIndexBuffer(int vertexCount) {
        this.vertexCount = vertexCount;
        this.indexType = indexTypeForVertexCount(vertexCount);

        int indexCount = switch (drawType) {
            case QUADS -> vertexCount * 3 / 2;
            case TRIANGLE_FAN, TRIANGLE_STRIP -> (vertexCount - 2) * 3;
        };
        int size = Math.multiplyExact(indexCount, this.indexType.size);

        ByteBuffer buffer = switch (drawType) {
            case QUADS -> genQuadIdxs(vertexCount, this.indexType);
            case TRIANGLE_FAN -> genTriangleFanIdxs(vertexCount, this.indexType);
            case TRIANGLE_STRIP -> genTriangleStripIdxs(vertexCount, this.indexType);
        };

        indexBuffer = new IndexBuffer(size, MemoryTypes.GPU_MEM);
        try {
            indexBuffer.copyBuffer(buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public void checkCapacity(int vertexCount) {
        if(vertexCount > this.vertexCount) {
            int newVertexCount = Math.max(this.vertexCount * 2, vertexCount);
            System.out.println("Reallocating AutoIndexBuffer from " + this.vertexCount + " to " + newVertexCount);

            // Can't know when the previous VBO will stop using it, so retire it through the frame-safe path.
            indexBuffer.freeBuffer();
            createIndexBuffer(newVertexCount);
        }
    }

    static IndexBuffer.IndexType indexTypeForVertexCount(int vertexCount) {
        if(vertexCount < 0)
            throw new IllegalArgumentException("vertexCount must be non-negative");
        return vertexCount <= 0x10000
                ? IndexBuffer.IndexType.SHORT
                : IndexBuffer.IndexType.INT;
    }

    public static ByteBuffer genQuadIdxs(int vertexCount) {
        return genQuadIdxs(vertexCount, indexTypeForVertexCount(vertexCount));
    }

    static ByteBuffer genQuadIdxs(int vertexCount, IndexBuffer.IndexType type) {
        int indexCount = vertexCount * 3 / 2;
        ByteBuffer buffer = MemoryUtil.memAlloc(Math.multiplyExact(indexCount, type.size));

        if(type == IndexBuffer.IndexType.SHORT) {
            ShortBuffer idxs = buffer.asShortBuffer();
            int j = 0;
            for(int i = 0; i < vertexCount; i += 4) {
                idxs.put(j, (short)i);
                idxs.put(j + 1, (short)(i + 1));
                idxs.put(j + 2, (short)(i + 2));
                idxs.put(j + 3, (short)i);
                idxs.put(j + 4, (short)(i + 2));
                idxs.put(j + 5, (short)(i + 3));
                j += 6;
            }
        } else {
            IntBuffer idxs = buffer.asIntBuffer();
            int j = 0;
            for(int i = 0; i < vertexCount; i += 4) {
                idxs.put(j, i);
                idxs.put(j + 1, i + 1);
                idxs.put(j + 2, i + 2);
                idxs.put(j + 3, i);
                idxs.put(j + 4, i + 2);
                idxs.put(j + 5, i + 3);
                j += 6;
            }
        }

        return buffer;
    }

    public static ByteBuffer genTriangleFanIdxs(int vertexCount) {
        return genTriangleFanIdxs(vertexCount, indexTypeForVertexCount(vertexCount));
    }

    static ByteBuffer genTriangleFanIdxs(int vertexCount, IndexBuffer.IndexType type) {
        int indexCount = (vertexCount - 2) * 3;
        ByteBuffer buffer = MemoryUtil.memAlloc(Math.multiplyExact(indexCount, type.size));

        if(type == IndexBuffer.IndexType.SHORT) {
            ShortBuffer idxs = buffer.asShortBuffer();
            int j = 0;
            for(int i = 0; i < vertexCount - 2; ++i) {
                idxs.put(j, (short)0);
                idxs.put(j + 1, (short)(i + 1));
                idxs.put(j + 2, (short)(i + 2));
                j += 3;
            }
        } else {
            IntBuffer idxs = buffer.asIntBuffer();
            int j = 0;
            for(int i = 0; i < vertexCount - 2; ++i) {
                idxs.put(j, 0);
                idxs.put(j + 1, i + 1);
                idxs.put(j + 2, i + 2);
                j += 3;
            }
        }

        return buffer;
    }

    public static ByteBuffer genTriangleStripIdxs(int vertexCount) {
        return genTriangleStripIdxs(vertexCount, indexTypeForVertexCount(vertexCount));
    }

    static ByteBuffer genTriangleStripIdxs(int vertexCount, IndexBuffer.IndexType type) {
        int indexCount = (vertexCount - 2) * 3;
        ByteBuffer buffer = MemoryUtil.memAlloc(Math.multiplyExact(indexCount, type.size));

        if(type == IndexBuffer.IndexType.SHORT) {
            ShortBuffer idxs = buffer.asShortBuffer();
            int j = 0;
            for(int i = 0; i < vertexCount - 2; ++i) {
                idxs.put(j, (short)i);
                idxs.put(j + 1, (short)(i + 1));
                idxs.put(j + 2, (short)(i + 2));
                j += 3;
            }
        } else {
            IntBuffer idxs = buffer.asIntBuffer();
            int j = 0;
            for(int i = 0; i < vertexCount - 2; ++i) {
                idxs.put(j, i);
                idxs.put(j + 1, i + 1);
                idxs.put(j + 2, i + 2);
                j += 3;
            }
        }

        return buffer;
    }

    public IndexBuffer getIndexBuffer() { return indexBuffer; }

    public int getVkIndexType() {
        return this.indexType == IndexBuffer.IndexType.INT
                ? VK_INDEX_TYPE_UINT32
                : VK_INDEX_TYPE_UINT16;
    }

    public enum DrawType {
        QUADS(7),
        TRIANGLE_FAN(6),
        TRIANGLE_STRIP(5);

        public final int n;

        DrawType (int n) {
            this.n = n;
        }
    }
}
