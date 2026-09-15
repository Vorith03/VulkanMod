package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;
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

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

/** Real Vulkan oracle for sparse-lighting residency in the shared terrain input pages. */
public final class GpuSparseLightingGpuSmokeTest {
    private GpuSparseLightingGpuSmokeTest() {}

    public static void verify() {
        if(AreaUploadManager.INSTANCE == null)
            throw new AssertionError("Sparse lighting GPU smoke requires the terrain upload manager");

        RegionVoxelGpuStore store = new RegionVoxelGpuStore();
        try {
            SectionVoxelSnapshot voxel = voxelFixture();
            GpuSparseLightingSnapshot first = lightingFixture(0x13579BDF);
            require(store.upload(0, voxel, 7L), "Voxel upload must coexist with sparse lighting");
            require(store.uploadLighting(0, first, 41L),
                    "First sparse-lighting upload must be accepted");
            require(!store.getResidency(0).valid(),
                    "Voxel residency must wait for shared-page submission");
            RegionVoxelGpuStore.Residency beforeSubmit = store.getLightingResidency(0);
            require(!beforeSubmit.valid() && beforeSubmit.generation() == 41L,
                    "Lighting residency must not publish before copy submission");

            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency voxelResidency = store.getResidency(0);
            RegionVoxelGpuStore.Residency firstResidency = store.getLightingResidency(0);
            require(voxelResidency.valid() && voxelResidency.generation() == 7L,
                    "Submitted voxel generation must remain independently resident");
            require(firstResidency.valid() && firstResidency.generation() == 41L,
                    "Submitted lighting generation must become resident");
            require(voxelResidency.pageIndex() == firstResidency.pageIndex(),
                    "Voxel and lighting inputs must share the same fixed region page when capacity permits");
            require(nonOverlapping(voxelResidency, firstResidency),
                    "Shared terrain input page slices must not overlap");
            verifyReadback(store, firstResidency, first);

            GpuSparseLightingSnapshot replacement = lightingFixture(0x2468ACE0);
            require(store.uploadLighting(0, replacement, 42L),
                    "Replacement sparse-lighting upload must be accepted");
            RegionVoxelGpuStore.Residency replacing = store.getLightingResidency(0);
            require(!replacing.valid() && replacing.generation() == 42L,
                    "Lighting replacement must revoke old generation before new publication");
            require(store.getResidency(0).valid() && store.getResidency(0).generation() == 7L,
                    "Lighting replacement must not revoke voxel residency");

            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency secondResidency = store.getLightingResidency(0);
            require(secondResidency.valid() && secondResidency.generation() == 42L,
                    "Submitted lighting replacement generation must become resident");
            require(firstResidency.pageIndex() != secondResidency.pageIndex()
                            || firstResidency.byteOffset() != secondResidency.byteOffset(),
                    "Lighting replacement must allocate-then-swap instead of overwriting a live slice");
            verifyReadback(store, secondResidency, replacement);

            GpuSparseLightingSnapshot abandoned = lightingFixture(0x10203040);
            require(store.uploadLighting(0, abandoned, 43L),
                    "Pending stale lighting generation must queue");
            store.invalidateLighting(0, 44L);
            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency invalidated = store.getLightingResidency(0);
            require(!invalidated.valid() && invalidated.generation() == 44L,
                    "Lighting invalidation before submission must prevent stale publication");
            require(store.getResidency(0).valid() && store.getResidency(0).generation() == 7L,
                    "Lighting invalidation must leave voxel generation untouched");

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_SPARSE_LIGHTING_RESIDENCY_OK: shared terrain input page, exact bytes, independent generation gating, fresh replacement, stale-generation rejection");
        } finally {
            Vulkan.waitIdle();
            store.close();
        }
    }

    private static boolean nonOverlapping(RegionVoxelGpuStore.Residency left,
                                          RegionVoxelGpuStore.Residency right) {
        long leftEnd = (long)left.byteOffset() + left.byteLength();
        long rightEnd = (long)right.byteOffset() + right.byteLength();
        return leftEnd <= right.byteOffset() || rightEnd <= left.byteOffset();
    }

    private static SectionVoxelSnapshot voxelFixture() {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i)
            builder.add(0, SectionVoxelSnapshot.CPU_REQUIRED);
        return builder.finish();
    }

    private static GpuSparseLightingSnapshot lightingFixture(int salt) {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        int center = SectionVoxelSnapshot.blockIndex(8, 8, 8);
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i) {
            int flags = SectionVoxelSnapshot.CPU_REQUIRED;
            if(i == center)
                flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
            builder.add(i == center ? 1 : 0, flags);
        }
        SectionVoxelSnapshot voxels = builder.finish();

        int[] packedLight = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        int[] shade = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        boolean[] lightPasses = new boolean[GpuLightingDemandMap.SAMPLE_COUNT];
        for(int i = 0; i < packedLight.length; ++i) {
            packedLight[i] = salt ^ i * 0x45d9f3b;
            shade[i] = Float.floatToRawIntBits(0.125F + (i % 23) * 0.03125F);
            lightPasses[i] = ((i + salt) & 3) != 0;
        }
        int[] directional = new int[Direction.values().length];
        for(Direction direction : Direction.values())
            directional[direction.ordinal()] = Float.floatToRawIntBits(
                    0.35F + direction.ordinal() * 0.075F + (salt & 3) * 0.001F);

        GpuSparseLightingSnapshot snapshot = GpuSparseLightingSnapshot.fromDenseReference(
                voxels, packedLight, shade, lightPasses, directional);
        require(snapshot != null && snapshot.sampleCount() > 0,
                "Sparse-lighting GPU fixture must remain inside the bounded demand cap");
        return snapshot;
    }

    private static void verifyReadback(RegionVoxelGpuStore store,
                                       RegionVoxelGpuStore.Residency residency,
                                       GpuSparseLightingSnapshot expectedSnapshot) {
        StorageBuffer page = store.getPageBuffer(residency.pageIndex());
        require(page != null, "Published lighting residency must reference a live page");
        require(residency.byteLength() == expectedSnapshot.byteSize(),
                "Published lighting residency length must match serialized snapshot");

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
                        throw new AssertionError("GPU sparse-lighting byte mismatch at " + i);
                }
            });
        } finally {
            MemoryUtil.memFree(expected);
            if(readbackBuffer != 0L)
                MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
}
