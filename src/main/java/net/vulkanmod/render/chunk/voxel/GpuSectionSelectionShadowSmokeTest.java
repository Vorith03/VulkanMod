package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.RegionBatchLayout;
import net.vulkanmod.render.chunk.VFrustum;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.*;

/** CI-only proof for persistent GPU-produced indirect shadow output. */
public final class GpuSectionSelectionShadowSmokeTest {
    private static final int TARGET_LAYER = 2;
    private static final int REGION_X = 0;
    private static final int REGION_Y = -64;
    private static final int REGION_Z = 0;
    private static final long GENERATION = 0x1234_5678_9abc_def0L;
    private static final int OUTPUT_HEADER_WORDS = 4;
    private static final int COMMAND_WORDS = RegionBatchLayout.STRIDE / Integer.BYTES;

    private GpuSectionSelectionShadowSmokeTest() {}

    public static void verify() {
        if(!Boolean.getBoolean("vulkanmod.ciGpuIndirectShadowSmoke"))
            return;
        if(!GpuSectionSelectionShadowStore.enabled())
            throw new IllegalStateException("GPU indirect shadow smoke requires its experimental gate");

        Fixture fixture = createFixture();
        GpuRegionCandidateGpuStore candidateStore = new GpuRegionCandidateGpuStore();
        GpuSectionSelectionShadowStore shadowStore = null;
        long readbackBuffer = VK_NULL_HANDLE;
        long readbackAllocation = VK_NULL_HANDLE;
        try {
            if(!candidateStore.upload(fixture.table))
                throw new IllegalStateException("Could not queue GPU candidate residency for shadow smoke");
            AreaUploadManager.INSTANCE.submitUploads();

            GpuRegionCandidateGpuStore.Residency residency = candidateStore.getResidency();
            if(!residency.valid() || residency.generation() != fixture.table.generation())
                throw new IllegalStateException("Candidate residency did not publish after helper submission");

            shadowStore = GpuSectionSelectionShadowStore.tryCreate(Renderer.getFramesNum());
            if(shadowStore == null)
                throw new IllegalStateException("Could not allocate persistent GPU indirect shadow output");
            if(!shadowStore.dispatch(residency, fixture.table, TARGET_LAYER, fixture.frustum))
                throw new IllegalStateException("Persistent GPU indirect shadow dispatch was not submitted");

            int outputBytes = shadowStore.commandOffsetBytes()
                    + shadowStore.commandCapacity() * RegionBatchLayout.STRIDE;
            int[] result = new int[outputBytes / Integer.BYTES];

            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pReadbackBuffer = stack.mallocLong(1);
                PointerBuffer pReadbackAllocation = stack.mallocPointer(1);
                MemoryManager memoryManager = MemoryManager.getInstance();
                memoryManager.createBuffer(outputBytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pReadbackBuffer, pReadbackAllocation);
                readbackBuffer = pReadbackBuffer.get(0);
                readbackAllocation = pReadbackAllocation.get(0);

                CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(shadowStore.output().getId()).offset(0L).size(outputBytes);
                vkCmdPipelineBarrier(commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, barrier, null);
                TransferQueue.uploadBufferCmd(commandBuffer, shadowStore.output().getId(), 0L,
                        readbackBuffer, 0L, outputBytes);
                Device.getGraphicsQueue().submitCommands(commandBuffer);
                Synchronization.waitFence(commandBuffer.getFence());
                Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

                long allocation = readbackAllocation;
                memoryManager.MapAndCopy(allocation, outputBytes, pointer -> {
                    ByteBuffer bytes = pointer.getByteBuffer(0, outputBytes)
                            .order(ByteOrder.nativeOrder());
                    for(int word = 0; word < result.length; ++word)
                        result[word] = bytes.getInt(word * Integer.BYTES);
                });
            }

            validateResult(result, fixture);

            // The readback fence proves the earlier candidate upload and shadow
            // dispatch completed on the same graphics queue. Recycle the remaining
            // helper command buffers now instead of relying on a main-frame fence in
            // this constructor-boundary smoke process.
            Device.getGraphicsQueue().waitIdle();
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_INDIRECT_SHADOW_SMOKE_OK: {} exact persistent storage+indirect commands from 512 live candidates; {} off-frustum direct traversal seeds preserved; ordinary frustum rejection, compute-to-indirect barrier and zero-tail output verified",
                    fixture.expectedCount, fixture.directSeedBypassCount);
        } finally {
            if(readbackBuffer != VK_NULL_HANDLE)
                MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
            if(shadowStore != null)
                shadowStore.close();
            candidateStore.close();
        }
    }

    private static Fixture createFixture() {
        VFrustum frustum = new VFrustum();
        frustum.setCamOffset(64.0D, 0.0D, 64.0D);
        frustum.calculateFrustum(new Matrix4f().identity(),
                new Matrix4f().ortho(-48.0F, 48.0F, -48.0F, 48.0F, -96.0F, 96.0F));

        GpuRegionCandidateTable.Builder builder = new GpuRegionCandidateTable.Builder(
                GENERATION, REGION_X, REGION_Y, REGION_Z);
        boolean[] expected = new boolean[RegionBatchLayout.MAX_SECTIONS];
        int[][] commands = new int[RegionBatchLayout.MAX_SECTIONS][COMMAND_WORDS];
        int expectedCount = 0;
        int directSeedBypassCount = 0;
        int ordinaryFrustumRejectCount = 0;

        for(int packed = 0; packed < RegionBatchLayout.MAX_SECTIONS; ++packed) {
            int indexCount = packed % 11 == 0 ? 0 : 6 + (packed % 5) * 6;
            int instanceCount = packed % 17 == 0 ? 0 : 1;
            int firstIndex = 31 + packed * 7;
            int vertexOffset = -1200 + packed * 5;
            boolean ready = packed % 3 != 0;
            boolean graphVisible = packed % 4 != 0;
            // X=7 lies beyond this fixture's frustum while remaining graph-visible;
            // X=1 was inside it and never exercised the direct-seed exception.
            boolean directSeed = (packed & 7) == 7;
            int layer = packed % 7 == 0 ? TARGET_LAYER - 1 : TARGET_LAYER;
            int flags = GpuRegionCandidateTable.flags(ready, graphVisible, directSeed, layer);

            builder.add(indexCount, instanceCount, firstIndex, vertexOffset, packed, flags);
            commands[packed][0] = indexCount;
            commands[packed][1] = instanceCount;
            commands[packed][2] = firstIndex;
            commands[packed][3] = vertexOffset;
            commands[packed][4] = packed;

            int minX = REGION_X + ((packed & 7) << 4);
            int minY = REGION_Y + (((packed >>> 3) & 7) << 4);
            int minZ = REGION_Z + (((packed >>> 6) & 7) << 4);
            boolean inFrustum = frustum.cubeInFrustum(minX, minY, minZ,
                    minX + 16, minY + 16, minZ + 16) < 0;
            boolean otherwiseEligible = indexCount != 0 && instanceCount != 0
                    && ready && graphVisible && layer == TARGET_LAYER;
            boolean selected = otherwiseEligible && (directSeed || inFrustum);
            expected[packed] = selected;
            if(selected)
                expectedCount++;
            if(otherwiseEligible && !inFrustum) {
                if(directSeed)
                    directSeedBypassCount++;
                else
                    ordinaryFrustumRejectCount++;
            }
        }

        if(expectedCount <= 0 || expectedCount >= RegionBatchLayout.MAX_SECTIONS)
            throw new IllegalStateException("Shadow smoke fixture did not produce a selective frustum set");
        if(directSeedBypassCount <= 0)
            throw new IllegalStateException("Shadow smoke fixture produced no off-frustum direct seed");
        if(ordinaryFrustumRejectCount <= 0)
            throw new IllegalStateException("Shadow smoke fixture produced no ordinary frustum rejection");
        return new Fixture(builder.finish(), frustum, expected, commands,
                expectedCount, directSeedBypassCount);
    }

    private static void validateResult(int[] result, Fixture fixture) {
        int expectedWords = OUTPUT_HEADER_WORDS
                + RegionBatchLayout.MAX_SECTIONS * COMMAND_WORDS;
        if(result.length != expectedWords)
            throw new IllegalStateException("Unexpected GPU shadow output size");
        if(result[0] != fixture.expectedCount || result[1] != fixture.expectedCount)
            throw new IllegalStateException("GPU shadow count mismatch: requested="
                    + result[0] + " written=" + result[1]
                    + " expected=" + fixture.expectedCount);
        if(result[2] != 0)
            throw new IllegalStateException("Full-capacity GPU shadow output overflowed");
        if(result[3] != 1)
            throw new IllegalStateException("GPU shadow candidate table was rejected");

        boolean[] seen = new boolean[RegionBatchLayout.MAX_SECTIONS];
        for(int slot = 0; slot < fixture.expectedCount; ++slot) {
            int base = OUTPUT_HEADER_WORDS + slot * COMMAND_WORDS;
            int packed = result[base + 4];
            if(packed < 0 || packed >= RegionBatchLayout.MAX_SECTIONS
                    || !fixture.expected[packed])
                throw new IllegalStateException("GPU shadow emitted ineligible section " + packed);
            if(seen[packed])
                throw new IllegalStateException("GPU shadow emitted duplicate section " + packed);
            seen[packed] = true;
            if(!Arrays.equals(Arrays.copyOfRange(result, base, base + COMMAND_WORDS),
                    fixture.commands[packed]))
                throw new IllegalStateException("GPU shadow changed indirect metadata for section " + packed);
        }

        for(int packed = 0; packed < RegionBatchLayout.MAX_SECTIONS; ++packed) {
            if(seen[packed] != fixture.expected[packed])
                throw new IllegalStateException("GPU shadow selection mismatch at section " + packed);
        }

        for(int slot = fixture.expectedCount; slot < RegionBatchLayout.MAX_SECTIONS; ++slot) {
            int base = OUTPUT_HEADER_WORDS + slot * COMMAND_WORDS;
            for(int word = 0; word < COMMAND_WORDS; ++word) {
                if(result[base + word] != 0)
                    throw new IllegalStateException("GPU shadow output tail was not zeroed at slot " + slot);
            }
        }
    }

    private record Fixture(GpuRegionCandidateTable table, VFrustum frustum,
                           boolean[] expected, int[][] commands, int expectedCount,
                           int directSeedBypassCount) {}
}
