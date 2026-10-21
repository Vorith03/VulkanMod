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

            // A second generation in this still-unsubmitted frame must not rewrite the
            // descriptor or erase output already referenced by its first draw. Use real
            // independent uploaded bytes so rejection reaches the lifetime guard.
            try(GpuRegionCandidateGpuStore newerStore = new GpuRegionCandidateGpuStore()) {
                var newer = new GpuRegionCandidateTable.Builder(GENERATION + 1,
                        REGION_X, REGION_Y, REGION_Z).finish();
                if(!newerStore.upload(newer)) throw new AssertionError("New generation upload rejected");
                AreaUploadManager.INSTANCE.submitUploads();
                if(!newerStore.getResidency().valid()) throw new AssertionError("New generation not resident");
                if(shadowStore.dispatch(newerStore.getResidency(), newer, TARGET_LAYER, fixture.frustum))
                    throw new AssertionError("In-flight parameter descriptor was rewritten before frame retirement");
                if(!shadowStore.dispatch(residency, fixture.table, TARGET_LAYER, fixture.frustum))
                    throw new AssertionError("Unchanged resident output must remain reusable in its original frame");
                if(!shadowStore.isValidFor(fixture.table.generation(), REGION_X, REGION_Y, REGION_Z))
                    throw new AssertionError("Rejected generation revoked usable old output");
            }

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
        verifyAtomicAppendSelection(fixture.frustum);
        verifyViewOwnership(fixture.frustum);
    }

    /** Real uploads/compute/readback across cached reads, quota and fence retirement. */
    private static void verifyViewOwnership(VFrustum frustum) {
        var holders = new java.util.ArrayList<GpuSectionSelectionShadowStore>();
        GpuSectionSelectionShadowStore root = null;
        try(GpuRegionCandidateGpuStore input = new GpuRegionCandidateGpuStore()) {
            retireFixtureFrame();
            root = GpuSectionSelectionShadowStore.tryCreate(Renderer.getFramesNum());
            if(root == null) throw new AssertionError("View root unavailable");
            var first = dispatchView(input, root, frustum, 1);
            var second = dispatchView(input, root, frustum, 2);
            if(first == null || second == null || first == second)
                throw new AssertionError("Overlapping views require independent live output");
            assertViewWords(first, 1);
            assertViewWords(second, 2);
            retireFixtureFrame();
            if(dispatchView(input, root, frustum, 2) != second)
                throw new AssertionError("Exact cached view must be selected before an idle slot");
            if(dispatchView(input, root, frustum, 3) != first)
                throw new AssertionError("Retired slot was not reused for changed view");
            assertViewWords(second, 2);
            assertViewWords(first, 3);
            for(int i = 0; i < 1024; i++) {
                var holder = GpuSectionSelectionShadowStore.tryCreate(Renderer.getFramesNum());
                if(holder == null) break;
                holders.add(holder);
            }
            if(holders.size() < 2 || holders.size() == 1024)
                throw new AssertionError("Expected bounded global output exhaustion");
            if(dispatchView(input, root, frustum, 4) != null)
                throw new AssertionError("Exhausted view quota must reject");
            assertViewWords(first, 3);
            assertViewWords(second, 2);
            long revision = GpuSectionSelectionShadowStore.retirementRevision();
            holders.remove(holders.size() - 1).close();
            holders.remove(holders.size() - 1).close();
            if(GpuSectionSelectionShadowStore.retirementRevision() != revision
                    || dispatchView(input, root, frustum, 4) != null)
                throw new AssertionError("Closing output must not return quota before retirement");
            retireFixtureFrame();
            if(GpuSectionSelectionShadowStore.retirementRevision() == revision)
                throw new AssertionError("Actual retirement must reopen admission");
            var fourth = dispatchView(input, root, frustum, 4);
            var fifth = dispatchView(input, root, frustum, 5);
            var sixth = dispatchView(input, root, frustum, 6);
            if(fourth == null || fifth == null || sixth == null
                    || fourth == fifth || fourth == sixth || fifth == sixth)
                throw new AssertionError("Retired quota must admit a third simultaneous view");
            assertViewWords(fourth, 4);
            assertViewWords(fifth, 5);
            assertViewWords(sixth, 6);
            Initializer.LOGGER.info("VULKANMOD_GPU_SELECTOR_VIEW_OWNERSHIP_OK: distinct overlapping output, cached-read protection, retired-slot reuse, exhausted quota rejection and fenced readmission; exact native commands/zero tails");
        } finally {
            if(root != null) root.close();
            for(var holder : holders) holder.close();
            retireFixtureFrame();
        }
    }

    private static GpuSectionSelectionShadowStore dispatchView(GpuRegionCandidateGpuStore input,
            GpuSectionSelectionShadowStore root, VFrustum frustum, int generation) {
        var table = new GpuRegionCandidateTable.Builder(GENERATION + generation,
                REGION_X, REGION_Y, REGION_Z).add(6 * generation, 1, 6 * generation,
                4 * generation, 0, GpuRegionCandidateTable.flags(true, true, true, TARGET_LAYER)).finish();
        if(!input.upload(table)) throw new AssertionError("View candidate upload rejected");
        AreaUploadManager.INSTANCE.submitUploads();
        return root.dispatchView(input.getResidency(), table, TARGET_LAYER, frustum);
    }

    private static void retireFixtureFrame() {
        // Constructor-boundary fixture only; production never waits for a view.
        Device.getGraphicsQueue().waitIdle();
        Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();
        MemoryManager.getInstance().initFrame(Renderer.getCurrentFrame());
    }

    private static void assertViewWords(GpuSectionSelectionShadowStore store, int generation) {
        int size = store.commandOffsetBytes() + store.commandCapacity() * RegionBatchLayout.STRIDE;
        long buffer = VK_NULL_HANDLE, allocation = VK_NULL_HANDLE;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            var memory = MemoryManager.getInstance();
            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            memory.createBuffer(size, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer, pAllocation);
            buffer = pBuffer.get(0);
            allocation = pAllocation.get(0);
            var command = Device.getGraphicsQueue().beginCommands();
            var barrier = VkBufferMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(store.output().getId()).offset(0L).size(size);
            vkCmdPipelineBarrier(command.getHandle(), VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, barrier, null);
            TransferQueue.uploadBufferCmd(command, store.output().getId(), 0L, buffer, 0L, size);
            Device.getGraphicsQueue().submitCommands(command);
            Synchronization.waitFence(command.getFence());
            Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(command);
            int[] expected = {1, 1, 0, 1, 6 * generation, 1, 6 * generation, 4 * generation, 0};
            memory.MapAndCopy(allocation, size, pointer -> {
                var bytes = pointer.getByteBuffer(0, size).order(ByteOrder.nativeOrder());
                for(int i = 0; i < size / Integer.BYTES; i++) {
                    int wanted = i < expected.length ? expected[i] : 0;
                    if(bytes.getInt(i * Integer.BYTES) != wanted)
                        throw new AssertionError("View " + generation + " word " + i + " expected " + wanted
                                + " got " + bytes.getInt(i * Integer.BYTES));
                }
            });
        } finally {
            if(buffer != VK_NULL_HANDLE) MemoryManager.freeBuffer(buffer, allocation);
        }
    }

    /** Real selector/readback proof, not generated-world or portal rasterization. */
    private static void verifyAtomicAppendSelection(VFrustum frustum) {
        GpuRegionCandidateTable.Builder builder = new GpuRegionCandidateTable.Builder(
                GENERATION + 1, REGION_X, REGION_Y, REGION_Z);
        int flags = GpuRegionCandidateTable.flags(true, true, true, TARGET_LAYER);
        for(int packed = 0; packed < 509; ++packed)
            builder.addPair(12, 10 + packed, 30 + packed * 8,
                    18, 0, 4000 + packed * 64, packed, flags);
        builder.add(6, 1, 99, 66, 509, flags);
        builder.add(18, 1, 0, 30000, 510, flags);
        // Corrupt an atomic pair's section identity: neither command may draw.
        builder.add(12, 1, 8, 16, 511, flags | GpuRegionCandidateTable.PAIR_FIRST);
        builder.add(18, 1, 0, 30064, 510, flags | GpuRegionCandidateTable.PAIR_SECOND);
        builder.addPair(12, 15, 24, 18, 0, 30128, 511,
                GpuRegionCandidateTable.flags(false, true, true, TARGET_LAYER));
        GpuRegionCandidateTable table = builder.finish();
        if(table.candidateCount() != RegionBatchLayout.MAX_DRAW_COMMANDS)
            throw new IllegalStateException("Atomic APPEND fixture must fill candidate capacity");

        GpuRegionCandidateGpuStore candidateStore = new GpuRegionCandidateGpuStore();
        GpuSectionSelectionShadowStore shadowStore = null;
        long readbackBuffer = VK_NULL_HANDLE;
        long readbackAllocation = VK_NULL_HANDLE;
        try {
            if(!candidateStore.upload(table))
                throw new IllegalStateException("APPEND candidate upload rejected");
            AreaUploadManager.INSTANCE.submitUploads();
            var residency = candidateStore.getResidency();
            if(!residency.valid() || residency.generation() != table.generation())
                throw new IllegalStateException("APPEND candidate generation was not resident");
            shadowStore = GpuSectionSelectionShadowStore.tryCreate(Renderer.getFramesNum());
            if(shadowStore == null || !shadowStore.dispatch(residency, table, TARGET_LAYER, frustum))
                throw new IllegalStateException("APPEND selector dispatch rejected");

            int bytesCount = shadowStore.commandOffsetBytes()
                    + shadowStore.commandCapacity() * RegionBatchLayout.STRIDE;
            int[] words = new int[bytesCount / Integer.BYTES];
            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pBuffer = stack.mallocLong(1);
                PointerBuffer pAllocation = stack.mallocPointer(1);
                MemoryManager memory = MemoryManager.getInstance();
                memory.createBuffer(bytesCount, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pBuffer, pAllocation);
                readbackBuffer = pBuffer.get(0);
                readbackAllocation = pAllocation.get(0);
                CommandPool.CommandBuffer command = Device.getGraphicsQueue().beginCommands();
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(shadowStore.output().getId()).offset(0L).size(bytesCount);
                vkCmdPipelineBarrier(command.getHandle(),
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, barrier, null);
                TransferQueue.uploadBufferCmd(command, shadowStore.output().getId(), 0L,
                        readbackBuffer, 0L, bytesCount);
                Device.getGraphicsQueue().submitCommands(command);
                Synchronization.waitFence(command.getFence());
                Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(command);
                long allocation = readbackAllocation;
                memory.MapAndCopy(allocation, bytesCount, pointer -> {
                    ByteBuffer bytes = pointer.getByteBuffer(0, bytesCount).order(ByteOrder.nativeOrder());
                    for(int i = 0; i < words.length; ++i)
                        words[i] = bytes.getInt(i * Integer.BYTES);
                });
            }
            if(words[0] != 1020 || words[1] != 1020 || words[2] != 0 || words[3] != 1)
                throw new IllegalStateException("APPEND selector count/overflow/table marker mismatch");
            boolean[] seen = new boolean[511];
            int observedPairs = 0;
            int observedSingles = 0;
            for(int i = 0; i < 1020;) {
                int base = OUTPUT_HEADER_WORDS + i * COMMAND_WORDS;
                int packed = words[base + 4];
                if(packed < 0 || packed > 510 || seen[packed])
                    throw new IllegalStateException("APPEND selector duplicate/foreign section " + packed);
                seen[packed] = true;
                if(packed < 509) {
                    int next = base + COMMAND_WORDS;
                    if(i + 1 >= 1020 || words[base] != 12 || words[base + 1] != 1
                            || words[base + 2] != 10 + packed
                            || words[base + 3] != 30 + packed * 8
                            || words[next] != 18 || words[next + 1] != 1
                            || words[next + 2] != 0
                            || words[next + 3] != 4000 + packed * 64
                            || words[next + 4] != packed)
                        throw new IllegalStateException("APPEND CPU/GPU commands not atomic and ordered at " + packed);
                    observedPairs++;
                    i += 2;
                } else {
                    if(words[base] != (packed == 509 ? 6 : 18)
                            || words[base + 2] != (packed == 509 ? 99 : 0)
                            || words[base + 3] != (packed == 509 ? 66 : 30000))
                        throw new IllegalStateException("APPEND mixed ordinary/REPLACE field mismatch");
                    observedSingles++;
                    ++i;
                }
            }
            if(observedPairs != 509 || observedSingles != 2)
                throw new IllegalStateException("APPEND mixed command counts mismatch");
            for(int i = 1020; i < RegionBatchLayout.MAX_DRAW_COMMANDS; ++i)
                for(int word = 0; word < COMMAND_WORDS; ++word)
                    if(words[OUTPUT_HEADER_WORDS + i * COMMAND_WORDS + word] != 0)
                        throw new IllegalStateException("APPEND zero-tail corruption");
            Device.getGraphicsQueue().waitIdle();
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();
            Initializer.LOGGER.info("VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK: 509 ordered atomic CPU/GPU pairs, mixed ordinary/REPLACE, malformed and unready pair rejection, max candidate capacity and zero tail; native fixture only");
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
                new Matrix4f().ortho(-32.0F, 32.0F, -48.0F, 48.0F, -96.0F, 96.0F));

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
            // X=7 lies beyond the narrowed X frustum while graph-visible.
            // Mark only alternating Y rows as seeds so other eligible X=7
            // sections also prove ordinary frustum rejection.
            boolean directSeed = (packed & 15) == 7;
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
                + RegionBatchLayout.MAX_DRAW_COMMANDS * COMMAND_WORDS;
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

        for(int slot = fixture.expectedCount; slot < RegionBatchLayout.MAX_DRAW_COMMANDS; ++slot) {
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
