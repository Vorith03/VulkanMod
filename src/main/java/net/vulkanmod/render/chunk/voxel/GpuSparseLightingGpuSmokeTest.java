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
            require(store.upload(0, voxel, 41L), "Voxel upload must coexist with sparse lighting");
            require(store.uploadLighting(0, first, 41L),
                    "Matching sparse-lighting generation must be accepted");
            require(!store.getResidency(0).valid(),
                    "Voxel residency must wait for shared-page submission");
            RegionVoxelGpuStore.Residency beforeSubmit = store.getLightingResidency(0);
            require(!beforeSubmit.valid() && beforeSubmit.generation() == 41L,
                    "Lighting residency must not publish before copy submission");

            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency firstVoxelResidency = store.getResidency(0);
            RegionVoxelGpuStore.Residency firstLightingResidency = store.getLightingResidency(0);
            require(firstVoxelResidency.valid() && firstVoxelResidency.generation() == 41L,
                    "Submitted voxel generation must become resident");
            require(firstLightingResidency.valid() && firstLightingResidency.generation() == 41L,
                    "Submitted lighting generation must become resident");
            require(firstVoxelResidency.pageIndex() == firstLightingResidency.pageIndex(),
                    "Voxel and lighting inputs must share the same fixed region page when capacity permits");
            require(nonOverlapping(firstVoxelResidency, firstLightingResidency),
                    "Shared terrain input page slices must not overlap");
            verifyReadback(store, firstLightingResidency, first);
            verifyCompute(store, firstVoxelResidency, firstLightingResidency, first);

            GpuSparseLightingSnapshot replacement = lightingFixture(0x2468ACE0);
            require(store.upload(0, voxel, 42L),
                    "Replacement voxel generation must queue");
            RegionVoxelGpuStore.Residency replacingVoxel = store.getResidency(0);
            RegionVoxelGpuStore.Residency revokedLighting = store.getLightingResidency(0);
            require(!replacingVoxel.valid() && replacingVoxel.generation() == 42L,
                    "Replacement voxel generation must remain unpublished until submission");
            require(!revokedLighting.valid() && revokedLighting.generation() == 42L,
                    "Voxel turnover must revoke lighting tied to prior voxel contents");
            require(store.uploadLighting(0, replacement, 42L),
                    "Replacement lighting must pair with the new voxel generation");
            require(!store.getLightingResidency(0).valid(),
                    "Replacement lighting must remain unpublished until submission");

            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency secondVoxelResidency = store.getResidency(0);
            RegionVoxelGpuStore.Residency secondLightingResidency = store.getLightingResidency(0);
            require(secondVoxelResidency.valid() && secondVoxelResidency.generation() == 42L,
                    "Submitted replacement voxel generation must become resident");
            require(secondLightingResidency.valid() && secondLightingResidency.generation() == 42L,
                    "Submitted replacement lighting must become resident");
            require(firstLightingResidency.pageIndex() != secondLightingResidency.pageIndex()
                            || firstLightingResidency.byteOffset() != secondLightingResidency.byteOffset(),
                    "Lighting replacement must allocate a fresh slice instead of overwriting a live slice");
            verifyReadback(store, secondLightingResidency, replacement);

            GpuSparseLightingSnapshot unpaired = lightingFixture(0x10203040);
            require(!store.uploadLighting(0, unpaired, 43L),
                    "Lighting without matching voxel generation must be rejected");
            require(store.getResidency(0).valid() && store.getResidency(0).generation() == 42L,
                    "Rejected unpaired lighting must leave current voxel residency intact");
            require(!store.getLightingResidency(0).valid(),
                    "Rejected unpaired lighting must revoke stale discoverable lighting");

            require(store.upload(0, voxel, 43L),
                    "Next paired voxel generation must queue");
            require(store.uploadLighting(0, unpaired, 43L),
                    "Lighting may queue once its matching voxel generation exists");
            store.invalidateLighting(0, 44L);
            AreaUploadManager.INSTANCE.submitUploads();
            RegionVoxelGpuStore.Residency advancedVoxel = store.getResidency(0);
            RegionVoxelGpuStore.Residency invalidated = store.getLightingResidency(0);
            require(advancedVoxel.valid() && advancedVoxel.generation() == 43L,
                    "Lighting invalidation must not prevent matching voxel publication");
            require(!invalidated.valid() && invalidated.generation() == 44L,
                    "Lighting invalidation before submission must prevent stale publication");

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_SPARSE_LIGHTING_RESIDENCY_OK: shared terrain input page, exact bytes, paired turnover, voxel-driven light revocation, unpaired rejection, stale-generation rejection");
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

    private static void verifyCompute(RegionVoxelGpuStore store,
                                      RegionVoxelGpuStore.Residency voxelResidency,
                                      RegionVoxelGpuStore.Residency lightingResidency,
                                      GpuSparseLightingSnapshot snapshot) {
        StorageBuffer voxelPage = store.getPageBuffer(voxelResidency.pageIndex());
        StorageBuffer lightingPage = store.getPageBuffer(lightingResidency.pageIndex());
        require(voxelPage != null && lightingPage != null,
                "Sparse-lighting compute requires live paired input pages");

        int[] actual;
        try(SparseLightingComputeProbe probe = new SparseLightingComputeProbe()) {
            actual = probe.dispatch(voxelPage, voxelResidency, lightingPage, lightingResidency);

            RegionVoxelGpuStore.Residency mismatched = new RegionVoxelGpuStore.Residency(
                    lightingResidency.pageIndex(), lightingResidency.byteOffset(),
                    lightingResidency.byteLength(), lightingResidency.generation() + 1L, true);
            boolean rejected = false;
            try {
                probe.dispatch(voxelPage, voxelResidency, lightingPage, mismatched);
            } catch(IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "Sparse-lighting compute must reject mismatched input generations");
        }

        require(actual.length == SparseLightingComputeProbe.RESULT_WORDS,
                "Sparse-lighting compute output size must cover the fixed 20-cube lattice");
        int[] expected = new int[SparseLightingComputeProbe.RESULT_WORDS];
        expected[0] = SparseLightingComputeProbe.RESULT_MAGIC;
        expected[1] = snapshot.sampleCount();

        int demanded = 0;
        int passCount = 0;
        for(int latticeIndex = 0; latticeIndex < GpuLightingDemandMap.SAMPLE_COUNT; ++latticeIndex) {
            int localX = latticeIndex % GpuLightingDemandMap.DOMAIN_WIDTH - 2;
            int localY = (latticeIndex / GpuLightingDemandMap.DOMAIN_WIDTH)
                    % GpuLightingDemandMap.DOMAIN_WIDTH - 2;
            int localZ = latticeIndex
                    / (GpuLightingDemandMap.DOMAIN_WIDTH * GpuLightingDemandMap.DOMAIN_WIDTH) - 2;
            if(!snapshot.demanded(localX, localY, localZ))
                continue;

            int rank = snapshot.compactRank(localX, localY, localZ);
            require(rank == demanded,
                    "Sparse-lighting rank prefix must remain monotonic in lattice order");
            int outputBase = SparseLightingComputeProbe.RESULT_HEADER_WORDS
                    + latticeIndex * SparseLightingComputeProbe.RESULT_RECORD_WORDS;
            expected[outputBase] = rank + 1;
            expected[outputBase + 1] = snapshot.packedLight(localX, localY, localZ);
            expected[outputBase + 2] = snapshot.shadeBrightnessBits(localX, localY, localZ);
            boolean passes = snapshot.lightPasses(localX, localY, localZ);
            expected[outputBase + 3] = passes ? 1 : 0;
            if(passes)
                ++passCount;
            ++demanded;
        }
        expected[2] = demanded;
        expected[3] = demanded;
        expected[4] = passCount;
        for(Direction direction : Direction.values())
            expected[6 + direction.ordinal()] = snapshot.directionalShadeBits(direction);

        require(demanded == snapshot.sampleCount(),
                "Every sparse-lighting compact record must map from exactly one demanded lattice point");
        for(int i = 0; i < expected.length; ++i) {
            if(actual[i] != expected[i]) {
                throw new AssertionError("GPU sparse-lighting compute mismatch at result word " + i
                        + ": expected=0x" + Integer.toHexString(expected[i])
                        + " actual=0x" + Integer.toHexString(actual[i]));
            }
        }

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_SPARSE_LIGHTING_COMPUTE_OK: paired-generation host gate, exact sparse rank decode, {} demanded lattice records, packed light, shade brightness, light-passing predicates, six directional shade values",
                demanded);
    }

    private static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
}
