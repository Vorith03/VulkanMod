package net.vulkanmod.vulkan.memory;

import net.vulkanmod.render.chunk.AreaBuffer;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

/** CPU-only contract test for AreaBuffer backing selection and storage transfer usage. */
public final class StorageBufferUsageTest {
    private StorageBufferUsageTest() {}

    public static void verify() {
        MemoryType previous = MemoryTypes.GPU_MEM;
        RecordingMemory memory = new RecordingMemory();
        MemoryTypes.GPU_MEM = memory;
        try {
            new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 256, Integer.BYTES);
            require(memory.lastUsage == VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    "Vertex AreaBuffer must retain vertex usage");

            new AreaBuffer(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 256, Short.BYTES);
            require(memory.lastUsage == VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    "Index AreaBuffer must retain index usage");

            new AreaBuffer(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, 4096, Integer.BYTES);
            int expectedStorageUsage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                    | VK_BUFFER_USAGE_TRANSFER_DST_BIT
                    | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            require(memory.lastUsage == expectedStorageUsage,
                    "Storage AreaBuffer must allocate transfer-capable shader storage");
            require(memory.lastSize == 4096, "Storage AreaBuffer allocation size");

            reject(() -> new AreaBuffer(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, 256, Integer.BYTES));
        } finally {
            MemoryTypes.GPU_MEM = previous;
        }
    }

    private static final class RecordingMemory extends MemoryType {
        int lastUsage;
        int lastSize;

        @Override
        void createBuffer(Buffer buffer, int size) {
            this.lastUsage = buffer.usage;
            this.lastSize = size;
            buffer.setBufferSize(size);
        }

        @Override void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            throw new AssertionError("Unexpected copy");
        }
        @Override void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            throw new AssertionError("Unexpected copy");
        }
        @Override void uploadBuffer(Buffer buffer, ByteBuffer byteBuffer) {
            throw new AssertionError("Unexpected upload");
        }
        @Override boolean mappable() { return false; }
    }

    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsupported AreaBuffer usage accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
