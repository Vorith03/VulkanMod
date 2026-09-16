package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;
import net.minecraft.client.renderer.FaceInfo;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.chunk.TerrainShaderManager;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.vertex.VertexUtil;
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
import java.nio.ByteOrder;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

/** Real Vulkan oracle for sparse-lighting residency in the shared terrain input pages. */
public final class GpuSparseLightingGpuSmokeTest {
    private static final int PROBE_CUBE_COORD = 8;
    private static final int FACE_RESULT_WORDS = 8;

    private GpuSparseLightingGpuSmokeTest() {}

    public static void verify() {
        if(AreaUploadManager.INSTANCE == null)
            throw new AssertionError("Sparse lighting GPU smoke requires the terrain upload manager");

        RegionVoxelGpuStore store = new RegionVoxelGpuStore();
        try {
            SectionVoxelSnapshot voxel = voxelFixture();
            GpuSparseLightingSnapshot first = lightingFixture(voxel, 0x13579BDF);
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
            verifyCompute(store, firstVoxelResidency, firstLightingResidency, first,
                    SectionVoxelSnapshot.blockIndex(8, 8, 8));

            GpuSparseLightingSnapshot replacement = lightingFixture(voxel, 0x2468ACE0);
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
                    "Submitted replacement lighting generation must become resident");
            require(firstLightingResidency.pageIndex() != secondLightingResidency.pageIndex()
                            || firstLightingResidency.byteOffset() != secondLightingResidency.byteOffset(),
                    "Lighting replacement must allocate a fresh slice instead of overwriting a live slice");
            verifyReadback(store, secondLightingResidency, replacement);

            GpuSparseLightingSnapshot unpaired = lightingFixture(voxel, 0x10203040);
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

            // Every corner exercises both outer halo shells on three axes.
            for(int corner = 0; corner < 8; ++corner) {
                int index = SectionVoxelSnapshot.blockIndex((corner & 1) * 15,
                        ((corner >> 1) & 1) * 15, ((corner >> 2) & 1) * 15);
                SectionVoxelSnapshot boundaryVoxel = voxelFixture(index);
                GpuSparseLightingSnapshot boundaryLight = lightingFixture(boundaryVoxel, corner);
                long generation = 50L + corner;
                require(store.upload(0, boundaryVoxel, generation), "Boundary voxel upload");
                require(store.uploadLighting(0, boundaryLight, generation), "Boundary lighting upload");
                AreaUploadManager.INSTANCE.submitUploads();
                verifyCompute(store, store.getResidency(0), store.getLightingResidency(0),
                        boundaryLight, index);
            }

            verifyCapturedFixtures(store, null, null);

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_SPARSE_LIGHTING_RESIDENCY_OK: shared terrain input page, exact bytes, paired turnover, voxel-driven light revocation, unpaired rejection, stale-generation rejection");
        } finally {
            Vulkan.waitIdle();
            store.close();
        }
    }

    /** Runs from the post-model-bake hook, after qualified UV residency exists. */
    public static void verifyCompleteVertexJoin(GpuTerrainModelTable table,
                                               GpuTerrainModelGpuStore.Residency modelResidency) {
        RegionVoxelGpuStore store = new RegionVoxelGpuStore();
        try {
            verifyCapturedFixtures(store, table, modelResidency);
        } finally {
            Vulkan.waitIdle();
            store.close();
        }
    }

    private static void verifyCapturedFixtures(RegionVoxelGpuStore store,
            GpuTerrainModelTable modelTable, GpuTerrainModelGpuStore.Residency modelResidency) {
        try(SparseLightingComputeProbe probe = new SparseLightingComputeProbe()) {
            for(int mask = 0; mask < 24; ++mask) {
                int corner = mask - 16;
                int blockIndex = corner < 0 ? 0 : SectionVoxelSnapshot.blockIndex(
                        (corner & 1) * 15, ((corner >> 1) & 1) * 15, ((corner >> 2) & 1) * 15);
                var fixture = CanonicalCubeLightingSmokeTest.sparseGpuFixture(mask & 15, blockIndex);
                if(modelTable != null)
                    fixture = withQualifiedModelState(fixture, modelTable);
                long generation = 100L + mask;
                require(store.upload(0, fixture.voxel(), generation), "Captured voxel upload");
                require(store.uploadLighting(0, fixture.lighting(), generation),
                        "Captured lighting upload");
                AreaUploadManager.INSTANCE.submitUploads();
                var voxelResidency = store.getResidency(0);
                var lightResidency = store.getLightingResidency(0);
                int[] result = probe.dispatch(store.getPageBuffer(voxelResidency.pageIndex()),
                        voxelResidency, store.getPageBuffer(lightResidency.pageIndex()),
                        lightResidency, blockIndex, modelResidency,
                        modelTable == null ? 0 : modelTable.templateCount());
                require(result[0] == SparseLightingComputeProbe.RESULT_MAGIC && result[5] == 0,
                        "Captured lighting must decode without GPU errors");
                if(modelTable != null)
                    verifyCompleteVertices(result, fixture, modelTable, mask);
                if(mask == 0) {
                    int[] rejected = probe.dispatch(store.getPageBuffer(voxelResidency.pageIndex()),
                            voxelResidency, store.getPageBuffer(lightResidency.pageIndex()),
                            lightResidency, 1, modelResidency,
                            modelTable == null ? 0 : modelTable.templateCount());
                    require(rejected[5] == 8, "Unqualified voxel must reject complete vertex generation");
                    for(int word = SparseLightingComputeProbe.VERTEX_RESULT_BASE;
                        word < rejected.length; ++word)
                        require(rejected[word] == 0,
                                "Rejected voxel must leave complete vertices empty");
                }
                for(int word = 0; word < fixture.faceWords().length; ++word) {
                    int actual = result[SparseLightingComputeProbe.FACE_RESULT_BASE + word];
                    int expected = fixture.faceWords()[word];
                    if(actual != expected)
                        throw new AssertionError("Minecraft/GPU lighting mismatch mask=" + mask
                                + " word=" + word + " expected=" + expected + " actual=" + actual);
                }
            }

            var original = CanonicalCubeLightingSmokeTest.sparseGpuFixture(0);
            if(modelTable != null)
                original = withQualifiedModelState(original, modelTable);
            var distant = CanonicalCubeLightingSmokeTest.sparseGpuFixture(0,
                    SectionVoxelSnapshot.blockIndex(15, 15, 15));
            verifyRejectedJoin(probe, store, modelResidency, modelTable,
                    original.voxel(), distant.lighting(), 200L, 4);
            if(modelTable != null) {
                SectionVoxelSnapshot.Builder unsupported = new SectionVoxelSnapshot.Builder(0, 64, 0);
                for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i)
                    unsupported.add(0, SectionVoxelSnapshot.CPU_REQUIRED
                            | (i == 0 ? SectionVoxelSnapshot.GPU_FULL_CUBE : 0));
                verifyRejectedJoin(probe, store, modelResidency, modelTable,
                        unsupported.finish(), original.lighting(), 201L, 16);
            }
        }
        Initializer.LOGGER.info("VULKANMOD_GPU_SPARSE_LIGHTING_MINECRAFT_OK: "
                + "576 exact captured face-vertex color/light pairs; complete CPU-writer vertices={}; "
                + "16 occluder masks plus eight section corners", modelTable != null);
    }

    /**
     * The captured Minecraft lighting record is independent of the baked model ID once
     * capture has completed. For the model-enabled join, substitute only the source
     * voxel state with a deterministic state that is actually qualified in this model
     * generation instead of assuming a particular vanilla block (such as Stone) is
     * always represented by the supported SimpleBakedModel subset.
     */
    private static CanonicalCubeLightingSmokeTest.SparseGpuFixture withQualifiedModelState(
            CanonicalCubeLightingSmokeTest.SparseGpuFixture fixture,
            GpuTerrainModelTable table) {
        require(table.templateCount() > 0,
                "Complete vertex join requires at least one qualified GPU model template");
        int qualifiedStateId = table.stateIdForTemplate(0);
        require(table.templateIndexForStateId(qualifiedStateId) == 0,
                "First qualified GPU model state must round-trip through the table");

        SectionVoxelSnapshot source = fixture.voxel();
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(
                source.x(), source.y(), source.z());
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i) {
            int stateId = i == fixture.blockIndex() ? qualifiedStateId : source.stateId(i);
            builder.add(stateId, source.flags(i));
        }
        return new CanonicalCubeLightingSmokeTest.SparseGpuFixture(
                builder.finish(), fixture.lighting(), fixture.faceWords(), fixture.blockIndex());
    }

    private static void verifyRejectedJoin(SparseLightingComputeProbe probe,
            RegionVoxelGpuStore store, GpuTerrainModelGpuStore.Residency model,
            GpuTerrainModelTable table, SectionVoxelSnapshot voxel,
            GpuSparseLightingSnapshot lighting, long generation, int expectedError) {
        require(store.upload(0, voxel, generation), "Rejected join voxel upload");
        require(store.uploadLighting(0, lighting, generation), "Rejected join lighting upload");
        AreaUploadManager.INSTANCE.submitUploads();
        var vr = store.getResidency(0);
        var lr = store.getLightingResidency(0);
        int[] result = probe.dispatch(store.getPageBuffer(vr.pageIndex()), vr,
                store.getPageBuffer(lr.pageIndex()), lr, 0, model,
                table == null ? 0 : table.templateCount());
        require(result[5] == expectedError, "Incomplete join must report its exact failure");
        for(int word = SparseLightingComputeProbe.VERTEX_RESULT_BASE; word < result.length; ++word)
            require(result[word] == 0, "Incomplete join must never emit complete vertices");
    }

    private static void verifyCompleteVertices(int[] actual,
            CanonicalCubeLightingSmokeTest.SparseGpuFixture fixture,
            GpuTerrainModelTable table, int mask) {
        require(TerrainShaderManager.TERRAIN_VERTEX_FORMAT == CustomVertexFormat.COMPRESSED_TERRAIN,
                "Joined vertex oracle requires the production compressed writer");
        int stateId = fixture.voxel().stateId(fixture.blockIndex());
        int template = table.templateIndexForStateId(stateId);
        require(template >= 0,
                "Joined fixture source state must have a qualified UV template");
        TerrainBufferBuilder builder = new TerrainBufferBuilder(1024);
        try {
            builder.begin(VertexFormat.Mode.QUADS, CustomVertexFormat.COMPRESSED_TERRAIN);
            for(Direction face : Direction.values()) {
                for(int vertex = 0; vertex < 4; ++vertex) {
                    var corner = FaceInfo.fromFacing(face).getVertexInfo(vertex);
                    int color = fixture.faceWords()[face.ordinal() * 8 + vertex * 2];
                    int light = fixture.faceWords()[face.ordinal() * 8 + vertex * 2 + 1];
                    // Use bin centers so converting already-verified packed colors back
                    // to float cannot introduce a second quantization rounding error.
                    float gray = Math.min(255.0F, (color & 255) + 0.25F) / 255.0F;
                    builder.vertex((fixture.blockIndex() & 15) + (corner.xFace == FaceInfo.Constants.MAX_X ? 1 : 0),
                            ((fixture.blockIndex() >> 4) & 15) + (corner.yFace == FaceInfo.Constants.MAX_Y ? 1 : 0),
                            ((fixture.blockIndex() >> 8) & 15) + (corner.zFace == FaceInfo.Constants.MAX_Z ? 1 : 0),
                            gray, gray, gray, 1.0F,
                            Float.intBitsToFloat(table.uBits(template, face.ordinal(), vertex)),
                            Float.intBitsToFloat(table.vBits(template, face.ordinal(), vertex)),
                            0, light, face.getStepX(), face.getStepY(), face.getStepZ());
                }
            }
            var rendered = builder.end();
            try {
                ByteBuffer expected = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
                require(expected.remaining() == 24 * 20, "CPU writer must emit exactly 24 vertices");
                for(int word = 0; word < 24 * 5; ++word) {
                    int value = expected.getInt(word * 4);
                    if(word % 5 == 1) value &= 65535; // Unused CPU padding is unspecified.
                    if(actual[SparseLightingComputeProbe.VERTEX_RESULT_BASE + word] != value)
                        throw new AssertionError("Complete GPU vertex mismatch mask=" + mask + " word=" + word);
                }
            } finally {
                rendered.release();
            }
        } finally {
            builder.free();
        }
    }

    private static boolean nonOverlapping(RegionVoxelGpuStore.Residency left,
                                          RegionVoxelGpuStore.Residency right) {
        long leftEnd = (long)left.byteOffset() + left.byteLength();
        long rightEnd = (long)right.byteOffset() + right.byteLength();
        return leftEnd <= right.byteOffset() || rightEnd <= left.byteOffset();
    }

    private static SectionVoxelSnapshot voxelFixture() {
        return voxelFixture(SectionVoxelSnapshot.blockIndex(
                PROBE_CUBE_COORD, PROBE_CUBE_COORD, PROBE_CUBE_COORD));
    }

    private static SectionVoxelSnapshot voxelFixture(int center) {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(-16, 64, -32);
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i) {
            int flags = SectionVoxelSnapshot.CPU_REQUIRED;
            if(i == center)
                flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
            builder.add(i == center ? 1 : 0, flags);
        }
        return builder.finish();
    }

    private static GpuSparseLightingSnapshot lightingFixture(SectionVoxelSnapshot voxels,
                                                              int salt) {
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
                                      GpuSparseLightingSnapshot snapshot, int blockIndex) {
        StorageBuffer voxelPage = store.getPageBuffer(voxelResidency.pageIndex());
        StorageBuffer lightingPage = store.getPageBuffer(lightingResidency.pageIndex());
        require(voxelPage != null && lightingPage != null,
                "Sparse-lighting compute requires live paired input pages");

        int[] actual;
        try(SparseLightingComputeProbe probe = new SparseLightingComputeProbe()) {
            actual = probe.dispatch(voxelPage, voxelResidency, lightingPage, lightingResidency, blockIndex);

            RegionVoxelGpuStore.Residency mismatched = new RegionVoxelGpuStore.Residency(
                    lightingResidency.pageIndex(), lightingResidency.byteOffset(),
                    lightingResidency.byteLength(), lightingResidency.generation() + 1L, true);
            boolean rejected = false;
            try {
                probe.dispatch(voxelPage, voxelResidency, lightingPage, mismatched, blockIndex);
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

        writeExpectedFaceLighting(expected, snapshot, blockIndex);
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
                "VULKANMOD_GPU_SPARSE_LIGHTING_COMPUTE_OK: paired-generation host gate, exact sparse rank decode, {} demanded lattice records, packed light, shade brightness, light-passing predicates, six directional shade values, 24 exact canonical face-vertex color/light outputs at block {}",
                demanded, blockIndex);
    }

    private static void writeExpectedFaceLighting(int[] expected,
                                                  GpuSparseLightingSnapshot snapshot, int blockIndex) {
        for(Direction face : Direction.values()) {
            Direction[] tangent = tangents(face);
            int centerX = (blockIndex & 15) + face.getStepX();
            int centerY = ((blockIndex >> 4) & 15) + face.getStepY();
            int centerZ = ((blockIndex >> 8) & 15) + face.getStepZ();
            float[] sideBrightness = new float[4];
            int[] sideLight = new int[4];
            boolean[] open = new boolean[4];
            for(int i = 0; i < 4; ++i) {
                int sideX = centerX + tangent[i].getStepX();
                int sideY = centerY + tangent[i].getStepY();
                int sideZ = centerZ + tangent[i].getStepZ();
                sideBrightness[i] = Float.intBitsToFloat(
                        snapshot.shadeBrightnessBits(sideX, sideY, sideZ));
                sideLight[i] = snapshot.packedLight(sideX, sideY, sideZ);
                open[i] = snapshot.lightPasses(
                        sideX + face.getStepX(),
                        sideY + face.getStepY(),
                        sideZ + face.getStepZ());
            }

            LightSample diagonal02 = diagonal(snapshot, face, tangent, centerX, centerY, centerZ,
                    sideBrightness, sideLight, open, 0, 2);
            LightSample diagonal03 = diagonal(snapshot, face, tangent, centerX, centerY, centerZ,
                    sideBrightness, sideLight, open, 0, 3);
            LightSample diagonal12 = diagonal(snapshot, face, tangent, centerX, centerY, centerZ,
                    sideBrightness, sideLight, open, 1, 2);
            LightSample diagonal13 = diagonal(snapshot, face, tangent, centerX, centerY, centerZ,
                    sideBrightness, sideLight, open, 1, 3);
            float centerBrightness = Float.intBitsToFloat(
                    snapshot.shadeBrightnessBits(centerX, centerY, centerZ));
            int centerLight = snapshot.packedLight(centerX, centerY, centerZ);

            float[] cornerBrightness = {
                    average(sideBrightness[3], sideBrightness[0], diagonal03.brightness, centerBrightness),
                    average(sideBrightness[2], sideBrightness[0], diagonal02.brightness, centerBrightness),
                    average(sideBrightness[2], sideBrightness[1], diagonal12.brightness, centerBrightness),
                    average(sideBrightness[3], sideBrightness[1], diagonal13.brightness, centerBrightness)
            };
            int[] cornerLight = {
                    blend(sideLight[3], sideLight[0], diagonal03.light, centerLight),
                    blend(sideLight[2], sideLight[0], diagonal02.light, centerLight),
                    blend(sideLight[2], sideLight[1], diagonal12.light, centerLight),
                    blend(sideLight[3], sideLight[1], diagonal13.light, centerLight)
            };

            int[] remap = vertexRemap(face);
            float directionalShade = Float.intBitsToFloat(snapshot.directionalShadeBits(face));
            int faceBase = SparseLightingComputeProbe.FACE_RESULT_BASE
                    + face.ordinal() * FACE_RESULT_WORDS;
            for(int corner = 0; corner < 4; ++corner) {
                int vertex = remap[corner];
                float value = cornerBrightness[corner] * directionalShade;
                expected[faceBase + vertex * 2] = VertexUtil.packColor(value, value, value, 1.0F);
                expected[faceBase + vertex * 2 + 1] = cornerLight[corner];
            }
        }
    }

    private static LightSample diagonal(GpuSparseLightingSnapshot snapshot, Direction face,
                                        Direction[] tangent, int centerX, int centerY, int centerZ,
                                        float[] sideBrightness, int[] sideLight, boolean[] open,
                                        int first, int second) {
        if(!open[first] && !open[second])
            return new LightSample(sideBrightness[0], sideLight[0]);
        int x = centerX + tangent[first].getStepX() + tangent[second].getStepX();
        int y = centerY + tangent[first].getStepY() + tangent[second].getStepY();
        int z = centerZ + tangent[first].getStepZ() + tangent[second].getStepZ();
        return new LightSample(Float.intBitsToFloat(snapshot.shadeBrightnessBits(x, y, z)),
                snapshot.packedLight(x, y, z));
    }

    private static float average(float a, float b, float c, float d) {
        return (a + b + c + d) * 0.25F;
    }

    private static int blend(int a, int b, int c, int center) {
        if(a == 0) a = center;
        if(b == 0) b = center;
        if(c == 0) c = center;
        return (a + b + c + center >> 2) & 0x00ff00ff;
    }

    private static Direction[] tangents(Direction face) {
        return switch(face) {
            case DOWN -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.NORTH, Direction.SOUTH };
            case UP -> new Direction[] { Direction.EAST, Direction.WEST,
                    Direction.NORTH, Direction.SOUTH };
            case NORTH -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.EAST, Direction.WEST };
            case SOUTH -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.DOWN, Direction.UP };
            case WEST -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.NORTH, Direction.SOUTH };
            case EAST -> new Direction[] { Direction.DOWN, Direction.UP,
                    Direction.NORTH, Direction.SOUTH };
        };
    }

    private static int[] vertexRemap(Direction face) {
        return switch(face) {
            case DOWN, SOUTH -> new int[] { 0, 1, 2, 3 };
            case UP -> new int[] { 2, 3, 0, 1 };
            case NORTH, WEST -> new int[] { 3, 0, 1, 2 };
            case EAST -> new int[] { 1, 2, 3, 0 };
        };
    }

    private record LightSample(float brightness, int light) {}

    private static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
}
