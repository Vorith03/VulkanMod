package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
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
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

/**
 * Real Vulkan oracle for region-owned section voxel residency.
 *
 * <p>This runs only in the isolated startup smoke process. It deliberately waits
 * helper fences so exact device bytes can be compared without changing gameplay
 * synchronization.</p>
 */
public final class SectionVoxelGpuSmokeTest {
    private SectionVoxelGpuSmokeTest() {}

    public static void verify() {
        if(AreaUploadManager.INSTANCE == null)
            throw new AssertionError("GPU voxel smoke requires the terrain upload manager");

        RegionVoxelGpuStore store = new RegionVoxelGpuStore();
        try {
            SectionVoxelSnapshot first = fixture(0x13579BDF);
            require(store.upload(0, first, 41L), "First GPU voxel upload must be accepted");
            RegionVoxelGpuStore.Residency beforeSubmit = store.getResidency(0);
            require(!beforeSubmit.valid() && beforeSubmit.generation() == 41L,
                    "Residency must not publish before copy submission");

            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency firstResidency = store.getResidency(0);
            require(firstResidency.valid() && firstResidency.generation() == 41L,
                    "Submitted first generation must become resident");
            verifyReadback(store, firstResidency, first);

            SectionVoxelSnapshot replacement = fixture(0x2468ACE0);
            require(store.upload(0, replacement, 42L), "Replacement GPU voxel upload must be accepted");
            RegionVoxelGpuStore.Residency replacing = store.getResidency(0);
            require(!replacing.valid() && replacing.generation() == 42L,
                    "Replacement must revoke old generation before new publication");

            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency secondResidency = store.getResidency(0);
            require(secondResidency.valid() && secondResidency.generation() == 42L,
                    "Submitted replacement generation must become resident");
            require(firstResidency.pageIndex() != secondResidency.pageIndex()
                            || firstResidency.byteOffset() != secondResidency.byteOffset(),
                    "Replacement must allocate-then-swap instead of overwriting a live slice");
            verifyReadback(store, secondResidency, replacement);
            verifyCompute(store, secondResidency, replacement);

            SectionVoxelSnapshot abandoned = fixture(0x10203040);
            require(store.upload(0, abandoned, 43L), "Pending stale-generation upload must queue");
            store.invalidate(0, 44L);
            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency invalidated = store.getResidency(0);
            require(!invalidated.valid() && invalidated.generation() == 44L,
                    "Invalidation before submission must prevent stale publication");

            Initializer.LOGGER.info(
                    "VULKANMOD_VOXEL_GPU_READBACK_OK: exact bytes, submission gating, fresh replacement, stale-generation rejection");
        } finally {
            Vulkan.waitIdle();
            store.close();
        }
    }

    private static SectionVoxelSnapshot fixture(int salt) {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        for(int i = 0; i < 4096; ++i) {
            int stateId = 1 + Math.floorMod(i * 1103515245 + salt, 6000);
            int flags = SectionVoxelSnapshot.CPU_REQUIRED | (i & 7);
            if((i & 1) == 0)
                flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
            builder.add(stateId, flags);
        }
        return builder.finish();
    }

    private static void verifyReadback(RegionVoxelGpuStore store,
                                       RegionVoxelGpuStore.Residency residency,
                                       SectionVoxelSnapshot expectedSnapshot) {
        StorageBuffer page = store.getPageBuffer(residency.pageIndex());
        require(page != null, "Published voxel residency must reference a live page");
        require(residency.byteLength() == expectedSnapshot.byteSize(),
                "Published voxel residency length must match the serialized snapshot");

        long readbackBuffer = 0L;
        long readbackAllocation = 0L;
        ByteBuffer expected = MemoryUtil.memAlloc(expectedSnapshot.byteSize());
        try(MemoryStack stack = MemoryStack.stackPush()) {
            expectedSnapshot.writeTo(expected);
            expected.flip();

            var pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            MemoryManager memoryManager = MemoryManager.getInstance();
            memoryManager.createBuffer(residency.byteLength(), VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer, pAllocation);
            readbackBuffer = pBuffer.get(0);
            readbackAllocation = pAllocation.get(0);

            CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
            TransferQueue.uploadBufferCmd(commandBuffer,
                    page.getId(), residency.byteOffset(),
                    readbackBuffer, 0L, residency.byteLength());
            Device.getGraphicsQueue().submitCommands(commandBuffer);
            Synchronization.waitFence(commandBuffer.getFence());
            Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

            long allocation = readbackAllocation;
            memoryManager.MapAndCopy(allocation, residency.byteLength(), pointer -> {
                ByteBuffer actual = pointer.getByteBuffer(0, residency.byteLength());
                for(int i = 0; i < residency.byteLength(); ++i) {
                    if(actual.get(i) != expected.get(i))
                        throw new AssertionError("GPU voxel byte mismatch at " + i);
                }
            });
        } finally {
            MemoryUtil.memFree(expected);
            if(readbackBuffer != 0L)
                MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
        }
    }

    private static void verifyCompute(RegionVoxelGpuStore store,
                                      RegionVoxelGpuStore.Residency residency,
                                      SectionVoxelSnapshot snapshot) {
        StorageBuffer page = store.getPageBuffer(residency.pageIndex());
        require(page != null, "Compute probe requires a live resident page");

        int[] actual;
        try(VoxelComputeProbe probe = new VoxelComputeProbe()) {
            actual = probe.dispatch(page, residency.byteOffset(), residency.byteLength());
        }

        int[] expected = new int[VoxelComputeProbe.COMPACT_DESCRIPTOR_BASE];
        int[] expectedCompact = new int[VoxelComputeProbe.FACE_DESCRIPTOR_WORDS];
        int eligibleVoxels = 0;
        int descriptorCount = 0;
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i) {
            int stateId = snapshot.stateId(i);
            int flags = snapshot.flags(i);
            int faceMask = candidateFaceMask(snapshot, i);
            expected[0] += stateId;
            expected[1] += flags;
            expected[2] += ((stateId * 33) ^ flags ^ i) ^ (faceMask * 0x9e3779b9);
            expected[3] += Integer.bitCount(faceMask);
            if((flags & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0)
                eligibleVoxels++;

            int descriptorBase = VoxelComputeProbe.HEADER_WORDS + i * VoxelComputeProbe.FACES_PER_VOXEL;
            for(int face = 0; face < VoxelComputeProbe.FACES_PER_VOXEL; ++face) {
                if((faceMask & (1 << face)) != 0) {
                    int descriptor = encodeFaceDescriptor(i, face);
                    expected[descriptorBase + face] = descriptor;
                    expectedCompact[descriptorCount++] = descriptor;
                }
            }
        }

        require(actual.length == VoxelComputeProbe.RESULT_WORDS,
                "GPU voxel compute result size must include fixed and compact descriptor regions");
        for(int i = 0; i < expected.length; ++i) {
            if(actual[i] != expected[i]) {
                String kind = i < VoxelComputeProbe.HEADER_WORDS ? "aggregate" : "fixed face descriptor";
                throw new AssertionError("GPU voxel compute " + kind + " mismatch at result word " + i
                        + ": expected=0x" + Integer.toHexString(expected[i])
                        + " actual=0x" + Integer.toHexString(actual[i]));
            }
        }

        require(eligibleVoxels == SectionVoxelSnapshot.BLOCK_COUNT / 2,
                "GPU_FULL_CUBE fixture must exercise the fifth flag plane");
        require(expected[3] == 4608,
                "Alternating-x fixture must expose the expected diagnostic candidate face count");
        require(descriptorCount == expected[3],
                "Every diagnostic candidate face must produce exactly one fixed-slot descriptor");

        int[] actualCompact = Arrays.copyOfRange(actual,
                VoxelComputeProbe.COMPACT_DESCRIPTOR_BASE,
                VoxelComputeProbe.COMPACT_DESCRIPTOR_BASE + descriptorCount);
        int[] expectedDense = Arrays.copyOf(expectedCompact, descriptorCount);
        Arrays.sort(actualCompact);
        Arrays.sort(expectedDense);
        for(int i = 0; i < descriptorCount; ++i) {
            if(actualCompact[i] != expectedDense[i]) {
                throw new AssertionError("GPU voxel compact face descriptor mismatch at sorted slot " + i
                        + ": expected=0x" + Integer.toHexString(expectedDense[i])
                        + " actual=0x" + Integer.toHexString(actualCompact[i]));
            }
        }
        for(int i = VoxelComputeProbe.COMPACT_DESCRIPTOR_BASE + descriptorCount;
            i < VoxelComputeProbe.RESULT_WORDS; ++i) {
            if(actual[i] != 0)
                throw new AssertionError("GPU voxel compact descriptor tail must remain zero at result word " + i);
        }

        Initializer.LOGGER.info(
                "VULKANMOD_VOXEL_COMPUTE_OK: 4096 voxel decode, v2 GPU_FULL_CUBE plane, six-neighbor diagnostic face classification, {} exact fixed-slot descriptors, dense GPU face-list compaction with no missing/duplicate descriptors, storage descriptors, compute barriers, full-stream readback",
                descriptorCount);
    }

    private static int candidateFaceMask(SectionVoxelSnapshot snapshot, int index) {
        if(!isGpuFullCube(snapshot, index))
            return 0;

        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = (index >>> 8) & 15;
        int mask = 0;

        if(y == 0 || !isGpuFullCube(snapshot, index - 16)) mask |= 1 << 0;
        if(y == 15 || !isGpuFullCube(snapshot, index + 16)) mask |= 1 << 1;
        if(z == 0 || !isGpuFullCube(snapshot, index - 256)) mask |= 1 << 2;
        if(z == 15 || !isGpuFullCube(snapshot, index + 256)) mask |= 1 << 3;
        if(x == 0 || !isGpuFullCube(snapshot, index - 1)) mask |= 1 << 4;
        if(x == 15 || !isGpuFullCube(snapshot, index + 1)) mask |= 1 << 5;
        return mask;
    }

    private static int encodeFaceDescriptor(int index, int face) {
        return 0x80000000 | (face << 12) | index;
    }

    private static boolean isGpuFullCube(SectionVoxelSnapshot snapshot, int index) {
        return (snapshot.flags(index) & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
