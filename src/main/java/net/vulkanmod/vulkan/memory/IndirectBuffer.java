package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

public class IndirectBuffer extends Buffer {
    CommandPool.CommandBuffer commandBuffer;

    public IndirectBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, type);
        this.createBuffer(size);
    }

    public void recordCopyCmd(ByteBuffer byteBuffer) {
        int size = byteBuffer.remaining();

        if(size > this.bufferSize - this.usedBytes) {
            resizeBuffer(size);
        }

        if(this.type.mappable()) {
            this.type.copyToBuffer(this, size, byteBuffer);
        }
        else {
            if(commandBuffer == null)
                commandBuffer = Device.getTransferQueue().beginCommands();

            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(Renderer.getCurrentFrame());
            stagingBuffer.copyBuffer(size, byteBuffer);

            TransferQueue.uploadBufferCmd(commandBuffer, stagingBuffer.id, stagingBuffer.offset, this.getId(), this.getUsedBytes(), size);
        }

        offset = usedBytes;
        usedBytes += size;
    }

    private void resizeBuffer(int requiredSize) {
        int oldUsedBytes = this.usedBytes;
        long requiredCapacity = (long) oldUsedBytes + requiredSize;
        long grownCapacity = (long) this.bufferSize + (this.bufferSize >> 1);
        long newCapacity = Math.max(grownCapacity, requiredCapacity);

        if(newCapacity > Integer.MAX_VALUE) {
            throw new IllegalStateException("Indirect buffer exceeds maximum supported size: " + newCapacity);
        }

        if(oldUsedBytes > 0 && !this.type.mappable()) {
            throw new IllegalStateException("Cannot grow a non-mappable indirect buffer after commands have been recorded");
        }

        long oldDataAddress = oldUsedBytes > 0 ? this.data.get(0) : 0L;
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer((int) newCapacity);

        if(oldUsedBytes > 0) {
            MemoryUtil.memCopy(oldDataAddress, this.data.get(0), oldUsedBytes);
        }

        this.usedBytes = oldUsedBytes;
    }

    public void submitUploads() {
        if(commandBuffer == null)
            return;

        Device.getTransferQueue().submitCommands(commandBuffer, true);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer, true);
        commandBuffer = null;
    }

    //debug
    public ByteBuffer getByteBuffer() {
        return this.data.getByteBuffer(0, this.bufferSize);
    }
}
