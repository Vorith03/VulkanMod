package net.vulkanmod.render.instancing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;

/** Owned CPU geometry. BLOCK vertices are 32 bytes; shading remains separate for material admission. */
public final class ModelGeometry {
    public static final int STRIDE = 32;
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    private final byte[] vertices;
    private final int[] indices;
    private final BitSet unshaded;
    private final int indexBytes;

    public ModelGeometry(byte[] vertices, int[] indices, BitSet unshaded) {
        if(vertices.length % STRIDE != 0 || vertices.length > MAX_BYTES
                || indices.length % 3 != 0 || (long)indices.length * 4 > MAX_BYTES
                || unshaded.length() > vertices.length / STRIDE)
            throw new IllegalArgumentException("Invalid or oversized model geometry");
        int largest = 0;
        for(int index : indices) {
            if(index < 0 || index >= vertices.length / STRIDE)
                throw new IllegalArgumentException("Model index outside vertex range");
            largest = Math.max(largest, index);
        }
        this.vertices = vertices.clone(); this.indices = indices.clone();
        this.unshaded = (BitSet)unshaded.clone();
        indexBytes = largest > 65535 ? 4 : 2;
    }

    public static int[] quadIndices(int vertexCount) {
        if(vertexCount < 0 || vertexCount % 4 != 0 || (long)vertexCount * STRIDE > MAX_BYTES)
            throw new IllegalArgumentException("Invalid or oversized sequential quad model");
        int[] indices = new int[vertexCount / 4 * 6];
        for(int q = 0, at = 0; q < vertexCount; q += 4) {
            for(int relative : new int[]{0,1,2,2,3,0}) indices[at++] = q + relative;
        }
        return indices;
    }

    public int vertexCount() { return vertices.length / STRIDE; }
    public int indexCount() { return indices.length; }
    public int indexBytes() { return indexBytes; }
    public boolean isShaded(int vertex) {
        if(vertex < 0 || vertex >= vertexCount()) throw new IndexOutOfBoundsException(vertex);
        return !unshaded.get(vertex);
    }
    public ByteBuffer vertices() { return ByteBuffer.wrap(vertices).asReadOnlyBuffer().order(ByteOrder.nativeOrder()); }
    public ByteBuffer indices() {
        var buffer = ByteBuffer.allocate(indices.length * indexBytes).order(ByteOrder.nativeOrder());
        for(int index : indices) { if(indexBytes == 2) buffer.putShort((short)index); else buffer.putInt(index); }
        return buffer.flip().asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }
}
