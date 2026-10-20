package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class VertexBuffer extends Buffer {
    @Override
    public Retirement retirement() { return Retirement.FRAME_FENCE; }


    public VertexBuffer(int size) {
        this(size, MemoryTypes.HOST_MEM);
    }

    public VertexBuffer(int size, MemoryType type) {
        this(size, type, 0);
    }

    public VertexBuffer(int size, MemoryType type, int extraUsage) {
        super(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | extraUsage, type);
        this.createBuffer(size);

    }

    public void copyToVertexBuffer(long vertexSize, long vertexCount, ByteBuffer byteBuffer) {
        int bufferSize = (int) (vertexSize * vertexCount);
//        long bufferSize = byteBuffer.limit();

        if(bufferSize > this.bufferSize - this.usedBytes) {
            resizeBuffer(Math.max(this.bufferSize * 2, this.usedBytes + bufferSize));
        }

        this.type.copyToBuffer(this, bufferSize, byteBuffer);
        offset = usedBytes;
        usedBytes += bufferSize;

    }

    public void reserve(int bytes) {
        if(bytes < 0) throw new IllegalArgumentException("Negative vertex reservation");
        if(bytes > bufferSize - usedBytes) resizeBuffer(Math.max(bufferSize * 2, Math.addExact(usedBytes, bytes)));
    }

    protected void resizeBuffer(int newSize) {
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer(newSize);

//        System.out.println("resized vertexBuffer to: " + newSize);
    }

}
