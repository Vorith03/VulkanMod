package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

/** Real Vulkan oracle for the resource-generation GPU terrain model table. */
public final class GpuTerrainModelTableSmokeTest {
    private static final Direction[] FACE_DIRECTIONS = {
            Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private GpuTerrainModelTableSmokeTest() {}

    public static void verify() {
        GpuTerrainModelTable table = GpuTerrainModelTable.captureCurrent();
        require(table.generation() == GpuTerrainModelRegistry.generation(),
                "Packed model table must capture the current baked-model generation");
        require(table.templateCount() == GpuTerrainModelRegistry.qualifiedStateCount(),
                "Packed model-table template count must equal registry qualification count");
        require(table.templateCount() > 0,
                "Startup smoke requires at least one qualified GPU terrain template");
        require(table.spriteCount() > 0,
                "Qualified GPU terrain templates must retain sprite identity");
        verifyCpuAbi(table);

        GpuTerrainModelGpuStore store = new GpuTerrainModelGpuStore();
        try {
            require(store.upload(table), "GPU model-table upload must be accepted");
            GpuTerrainModelGpuStore.Residency resident = store.getResidency();
            require(resident.valid() && resident.generation() == table.generation(),
                    "Completed GPU model-table upload must publish current-generation residency");
            require(resident.byteLength() == table.byteSize(),
                    "GPU model-table residency must expose the exact packed byte length");
            verifyReadback(resident, table);

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_MODEL_TABLE_OK: generation {}, {} templates, {} sprites, {} state-index entries, {} bytes; exact CPU ABI and device-local readback",
                    table.generation(), table.templateCount(), table.spriteCount(),
                    table.stateIndexCount(), table.byteSize());
        } finally {
            Vulkan.waitIdle();
            store.close();
        }
    }

    private static void verifyCpuAbi(GpuTerrainModelTable table) {
        require(table.word(0) == GpuTerrainModelTable.MAGIC,
                "GPU model-table magic must match ABI");
        require(table.word(1) == GpuTerrainModelTable.VERSION,
                "GPU model-table version must match ABI");
        long encodedGeneration = Integer.toUnsignedLong(table.word(2))
                | ((long) table.word(3) << 32);
        require(encodedGeneration == table.generation(),
                "GPU model-table header must retain the resource generation");
        require(table.word(4) == table.stateIndexCount()
                        && table.word(5) == table.templateCount()
                        && table.word(6) == table.spriteCount()
                        && table.word(7) == table.templateBaseWord(),
                "GPU model-table header offsets/counts must be self-consistent");

        int seen = 0;
        for(int stateId = 0; stateId < table.stateIndexCount(); ++stateId) {
            GpuTerrainModelRegistry.FullCubeTemplate source =
                    GpuTerrainModelRegistry.getFullCubeTemplate(stateId);
            int templateIndex = table.templateIndexForStateId(stateId);
            if(source == null) {
                require(templateIndex == -1,
                        "Unqualified block state must retain zero model-table sentinel");
                continue;
            }

            seen++;
            require(templateIndex >= 0 && templateIndex < table.templateCount(),
                    "Qualified block state must resolve to a dense GPU template");
            require(table.stateIdForTemplate(templateIndex) == stateId,
                    "Dense GPU template must round-trip its runtime state ID");
            require(table.faceMask(templateIndex) == source.faceMask(),
                    "Dense GPU template must retain exact face mask");

            for(int face = 0; face < GpuTerrainModelTable.FACE_COUNT; ++face) {
                GpuTerrainModelRegistry.FaceTemplate sourceFace = source.face(FACE_DIRECTIONS[face]);
                int spriteSlot = table.spriteSlot(templateIndex, face);
                require(table.spriteId(spriteSlot).equals(sourceFace.spriteId()),
                        "Dense GPU face must retain baked sprite identity");
                for(int vertex = 0; vertex < 4; ++vertex) {
                    require(table.uBits(templateIndex, face, vertex) == sourceFace.uBits(vertex)
                                    && table.vBits(templateIndex, face, vertex) == sourceFace.vBits(vertex),
                            "Dense GPU face must retain exact ordered baked UV bits");
                }
            }
        }
        require(seen == table.templateCount(),
                "State-index indirection must cover every dense GPU template exactly once");
    }

    private static void verifyReadback(GpuTerrainModelGpuStore.Residency residency,
                                       GpuTerrainModelTable table) {
        StorageBuffer buffer = residency.buffer();
        require(buffer != null, "Published GPU model table must reference a live storage buffer");

        long readbackBuffer = 0L;
        long readbackAllocation = 0L;
        ByteBuffer expected = MemoryUtil.memAlloc(table.byteSize());
        try(MemoryStack stack = MemoryStack.stackPush()) {
            table.writeTo(expected);
            expected.flip();

            var pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            MemoryManager memoryManager = MemoryManager.getInstance();
            memoryManager.createBuffer(table.byteSize(), VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer, pAllocation);
            readbackBuffer = pBuffer.get(0);
            readbackAllocation = pAllocation.get(0);

            CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
            TransferQueue.uploadBufferCmd(commandBuffer,
                    buffer.getId(), 0L, readbackBuffer, 0L, table.byteSize());
            Device.getGraphicsQueue().submitCommands(commandBuffer);
            Synchronization.waitFence(commandBuffer.getFence());
            Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

            long allocation = readbackAllocation;
            memoryManager.MapAndCopy(allocation, table.byteSize(), pointer -> {
                ByteBuffer actual = pointer.getByteBuffer(0, table.byteSize());
                for(int i = 0; i < table.byteSize(); ++i) {
                    if(actual.get(i) != expected.get(i))
                        throw new AssertionError("GPU terrain model-table byte mismatch at " + i);
                }
            });
        } finally {
            MemoryUtil.memFree(expected);
            if(readbackBuffer != 0L)
                MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
