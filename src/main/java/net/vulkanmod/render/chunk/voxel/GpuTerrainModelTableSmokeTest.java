package net.vulkanmod.render.chunk.voxel;

import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

/** Real Vulkan oracle for the resource-generation GPU terrain model table. */
public final class GpuTerrainModelTableSmokeTest {
    private static final int JOINED_VOXEL_OFFSET = 64;
    private static final float POSITION_SCALE = 1900.0f;
    private static final float UV_SCALE = 65536.0f;
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
        CanonicalCubeLightingSmokeTest.verify();

        GpuTerrainModelGpuStore store = new GpuTerrainModelGpuStore();
        StorageBuffer voxelPage = null;
        try {
            require(store.upload(table), "GPU model-table upload must be accepted");
            GpuTerrainModelGpuStore.Residency resident = store.getResidency();
            require(resident.valid() && resident.generation() == table.generation(),
                    "Completed GPU model-table upload must publish current-generation residency");
            require(resident.byteLength() == table.byteSize(),
                    "GPU model-table residency must expose the exact packed byte length");
            verifyReadback(resident, table);

            SectionVoxelSnapshot voxelSnapshot = joinedVoxelFixture(table);
            voxelPage = uploadJoinedVoxelFixture(voxelSnapshot);
            verifyComputeLookup(resident, table, voxelPage, JOINED_VOXEL_OFFSET,
                    voxelSnapshot.byteSize(), voxelSnapshot);
            verifyCompactCandidateFaceRows(resident, table, voxelPage,
                    JOINED_VOXEL_OFFSET, voxelSnapshot.byteSize(), voxelSnapshot);

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_MODEL_TABLE_OK: generation {}, {} templates, {} sprites, {} state-index entries, {} bytes; exact CPU ABI, device-local readback, compute state-to-template/UV decode, 4096 resident voxel face-row joins, exact compact candidate face rows, and packed position/UV terrain-vertex fields",
                    table.generation(), table.templateCount(), table.spriteCount(),
                    table.stateIndexCount(), table.byteSize());
        } finally {
            Vulkan.waitIdle();
            if(voxelPage != null)
                voxelPage.freeBuffer();
            store.close();
        }
    }

    private static void verifyComputeLookup(GpuTerrainModelGpuStore.Residency residency,
                                            GpuTerrainModelTable table,
                                            StorageBuffer voxelPage,
                                            int voxelByteOffset,
                                            int voxelByteLength,
                                            SectionVoxelSnapshot voxelSnapshot) {
        try(GpuTerrainModelComputeProbe probe = new GpuTerrainModelComputeProbe()) {
            int[] actual = probe.dispatch(residency, table.templateCount(), voxelPage,
                    voxelByteOffset, voxelByteLength);
            int lookupBase = GpuTerrainModelComputeProbe.voxelLookupBase(table.templateCount());
            int expectedWords = Math.addExact(lookupBase,
                    Math.multiplyExact(SectionVoxelSnapshot.BLOCK_COUNT,
                            GpuTerrainModelComputeProbe.RESULT_WORDS_PER_VOXEL));
            require(actual.length == expectedWords,
                    "GPU model-table compute output must cover every dense template and voxel face lookup");

            for(int template = 0; template < table.templateCount(); ++template) {
                int outputBase = template * GpuTerrainModelComputeProbe.RESULT_WORDS_PER_TEMPLATE;
                require(actual[outputBase] == GpuTerrainModelComputeProbe.VALID_MARKER,
                        "GPU model-table compute lookup must validate sparse state mapping for template "
                                + template);
                int tableBase = table.templateBaseWord()
                        + template * GpuTerrainModelTable.TEMPLATE_WORDS;
                for(int word = 0; word < GpuTerrainModelTable.TEMPLATE_WORDS; ++word) {
                    if(actual[outputBase + 1 + word] != table.word(tableBase + word)) {
                        throw new AssertionError("GPU model-table compute decode mismatch at template "
                                + template + " word " + word);
                    }
                }
            }

            int qualifiedWithoutHint = 0;
            int rejectedWithHint = 0;
            for(int voxel = 0; voxel < SectionVoxelSnapshot.BLOCK_COUNT; ++voxel) {
                int templateIndex = table.templateIndexForStateId(voxelSnapshot.stateId(voxel));
                int expected = templateIndex + 1;
                int voxelBase = GpuTerrainModelComputeProbe.voxelResultBase(
                        table.templateCount(), voxel);
                if(actual[voxelBase] != expected) {
                    throw new AssertionError("GPU resident voxel model lookup mismatch at voxel "
                            + voxel + ": expected=" + expected
                            + " actual=" + actual[voxelBase]);
                }

                // Cycle all six directions without trusting the legacy geometry hint.
                int face = voxel % GpuTerrainModelTable.FACE_COUNT;
                for(int word = 0; word < GpuTerrainModelTable.FACE_WORDS; ++word) {
                    int expectedFaceWord = templateIndex < 0 ? 0
                            : table.word(table.templateBaseWord()
                                    + templateIndex * GpuTerrainModelTable.TEMPLATE_WORDS
                                    + 2 + face * GpuTerrainModelTable.FACE_WORDS + word);
                    if(actual[voxelBase + 1 + word] != expectedFaceWord) {
                        throw new AssertionError("GPU resident voxel face-row mismatch at voxel "
                                + voxel + " face " + face + " word " + word
                                + ": expected=" + expectedFaceWord
                                + " actual=" + actual[voxelBase + 1 + word]);
                    }
                }

                boolean hinted = (voxelSnapshot.flags(voxel)
                        & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0;
                if(templateIndex >= 0 && !hinted)
                    qualifiedWithoutHint++;
                if(templateIndex < 0 && hinted)
                    rejectedWithHint++;
            }
            require(qualifiedWithoutHint > 0 && rejectedWithHint > 0,
                    "Joined voxel fixture must prove current model-table lookup is independent of stale geometry hints");
        }
    }

    private static void verifyCompactCandidateFaceRows(
            GpuTerrainModelGpuStore.Residency residency,
            GpuTerrainModelTable table,
            StorageBuffer voxelPage,
            int voxelByteOffset,
            int voxelByteLength,
            SectionVoxelSnapshot voxelSnapshot) {
        int[] actual;
        try(VoxelComputeProbe probe = new VoxelComputeProbe()) {
            actual = probe.dispatch(voxelPage, voxelByteOffset, voxelByteLength,
                    residency, table.templateCount());
        }

        require(actual.length == VoxelComputeProbe.RESULT_WORDS,
                "Joined compact-face output must retain the complete classifier oracle");
        int candidateCount = actual[3];
        require(candidateCount == SectionVoxelSnapshot.BLOCK_COUNT / 2
                        * GpuTerrainModelTable.FACE_COUNT,
                "Joined fixture must emit all six faces for every geometry-hinted voxel");

        boolean[] seen = new boolean[VoxelComputeProbe.FACE_DESCRIPTOR_WORDS];
        int qualifiedRows = 0;
        int unqualifiedRows = 0;
        for(int slot = 0; slot < candidateCount; ++slot) {
            int descriptor = actual[VoxelComputeProbe.COMPACT_DESCRIPTOR_BASE + slot];
            require((descriptor & 0x80000000) != 0,
                    "Joined compact candidate descriptor must carry the live marker");
            int voxel = descriptor & 0xfff;
            int face = (descriptor >>> 12) & 7;
            require(voxel < SectionVoxelSnapshot.BLOCK_COUNT
                            && face < GpuTerrainModelTable.FACE_COUNT,
                    "Joined compact candidate descriptor must decode to a valid voxel and face");
            require((voxelSnapshot.flags(voxel) & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0,
                    "Joined compact candidate must originate from a geometry-hinted voxel");
            int descriptorIndex = voxel * GpuTerrainModelTable.FACE_COUNT + face;
            require(!seen[descriptorIndex],
                    "Joined compact candidate list must not contain duplicate descriptors");
            seen[descriptorIndex] = true;

            int templateIndex = table.templateIndexForStateId(voxelSnapshot.stateId(voxel));
            int rowBase = VoxelComputeProbe.MODEL_FACE_BASE
                    + slot * VoxelComputeProbe.MODEL_FACE_RESULT_WORDS;
            require(actual[rowBase] == templateIndex + 1,
                    "Joined compact candidate must resolve through the current model-table state index");
            for(int word = 0; word < GpuTerrainModelTable.FACE_WORDS; ++word) {
                int expected = templateIndex < 0 ? 0
                        : table.word(table.templateBaseWord()
                                + templateIndex * GpuTerrainModelTable.TEMPLATE_WORDS
                                + 2 + face * GpuTerrainModelTable.FACE_WORDS + word);
                if(actual[rowBase + 1 + word] != expected) {
                    throw new AssertionError("Joined compact candidate face-row mismatch at slot "
                            + slot + " voxel " + voxel + " face " + face + " word " + word);
                }
            }
            int[] expectedCorners = expectedFaceCorners(voxel, face);
            int vertexBase = VoxelComputeProbe.PARTIAL_VERTEX_BASE
                    + slot * VoxelComputeProbe.VERTICES_PER_FACE
                    * VoxelComputeProbe.PARTIAL_VERTEX_WORDS_PER_VERTEX;
            for(int vertex = 0; vertex < VoxelComputeProbe.VERTICES_PER_FACE; ++vertex) {
                int[] expectedVertex = templateIndex < 0
                        ? new int[VoxelComputeProbe.PARTIAL_VERTEX_WORDS_PER_VERTEX]
                        : expectedPartialVertex(table, templateIndex, face,
                                vertex, expectedCorners[vertex]);
                for(int word = 0; word < expectedVertex.length; ++word) {
                    if(actual[vertexBase + vertex
                            * VoxelComputeProbe.PARTIAL_VERTEX_WORDS_PER_VERTEX + word]
                            != expectedVertex[word]) {
                        throw new AssertionError("Joined compact partial vertex mismatch at slot "
                                + slot + " vertex " + vertex + " word " + word);
                    }
                }
            }
            if(templateIndex >= 0)
                qualifiedRows++;
            else
                unqualifiedRows++;
        }

        for(int voxel = 0; voxel < SectionVoxelSnapshot.BLOCK_COUNT; ++voxel) {
            boolean expectedCandidate = (voxelSnapshot.flags(voxel)
                    & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0;
            for(int face = 0; face < GpuTerrainModelTable.FACE_COUNT; ++face) {
                require(seen[voxel * GpuTerrainModelTable.FACE_COUNT + face]
                                == expectedCandidate,
                        "Joined compact candidate set must match the independent CPU fixture");
            }
        }
        require(qualifiedRows > 0 && unqualifiedRows > 0,
                "Joined compact candidates must cover qualified rows and fail-closed unqualified hints");
        for(int word = VoxelComputeProbe.MODEL_FACE_BASE
                + candidateCount * VoxelComputeProbe.MODEL_FACE_RESULT_WORDS;
            word < VoxelComputeProbe.PARTIAL_VERTEX_BASE; ++word) {
            if(actual[word] != 0)
                throw new AssertionError("Joined compact model-face tail must remain zero at word " + word);
        }
        for(int word = VoxelComputeProbe.PARTIAL_VERTEX_BASE
                + candidateCount * VoxelComputeProbe.VERTICES_PER_FACE
                * VoxelComputeProbe.PARTIAL_VERTEX_WORDS_PER_VERTEX;
            word < VoxelComputeProbe.RESULT_WORDS; ++word) {
            if(actual[word] != 0)
                throw new AssertionError("Joined compact partial-vertex tail must remain zero at word " + word);
        }
    }

    private static int[] expectedPartialVertex(GpuTerrainModelTable table,
                                               int templateIndex,
                                               int face,
                                               int vertex,
                                               int packedCorner) {
        int x = packedCorner & 31;
        int y = (packedCorner >>> 5) & 31;
        int z = (packedCorner >>> 10) & 31;
        short packedX = (short) (x * POSITION_SCALE + 0.1f);
        short packedY = (short) (y * POSITION_SCALE + 0.1f);
        short packedZ = (short) (z * POSITION_SCALE + 0.1f);
        short packedU = (short) (Float.intBitsToFloat(
                table.uBits(templateIndex, face, vertex)) * UV_SCALE);
        short packedV = (short) (Float.intBitsToFloat(
                table.vBits(templateIndex, face, vertex)) * UV_SCALE);
        return new int[] {
                Short.toUnsignedInt(packedX) | (Short.toUnsignedInt(packedY) << 16),
                Short.toUnsignedInt(packedZ),
                0,
                Short.toUnsignedInt(packedU) | (Short.toUnsignedInt(packedV) << 16),
                0
        };
    }

    private static int[] expectedFaceCorners(int index, int face) {
        int x0 = index & 15;
        int y0 = (index >>> 4) & 15;
        int z0 = (index >>> 8) & 15;
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;
        FaceInfo faceInfo = FaceInfo.fromFacing(FACE_DIRECTIONS[face]);
        int[] corners = new int[VoxelComputeProbe.VERTICES_PER_FACE];
        for(int vertex = 0; vertex < corners.length; ++vertex) {
            FaceInfo.VertexInfo info = faceInfo.getVertexInfo(vertex);
            int x = extentCoordinate(info.xFace, FaceInfo.Constants.MIN_X,
                    FaceInfo.Constants.MAX_X, x0, x1);
            int y = extentCoordinate(info.yFace, FaceInfo.Constants.MIN_Y,
                    FaceInfo.Constants.MAX_Y, y0, y1);
            int z = extentCoordinate(info.zFace, FaceInfo.Constants.MIN_Z,
                    FaceInfo.Constants.MAX_Z, z0, z1);
            corners[vertex] = x | (y << 5) | (z << 10);
        }
        return corners;
    }

    private static int extentCoordinate(int extent, int minExtent, int maxExtent,
                                        int min, int max) {
        if(extent == minExtent)
            return min;
        if(extent == maxExtent)
            return max;
        throw new AssertionError("FaceInfo extent must map to a unit-cube coordinate");
    }

    private static StorageBuffer uploadJoinedVoxelFixture(SectionVoxelSnapshot snapshot) {
        StorageBuffer buffer = new StorageBuffer(
                JOINED_VOXEL_OFFSET + snapshot.byteSize(), MemoryTypes.GPU_MEM);
        ByteBuffer bytes = MemoryUtil.memAlloc(snapshot.byteSize());
        try {
            snapshot.writeTo(bytes);
            bytes.flip();
            GpuTerrainModelGpuStore.uploadImmediate(buffer, JOINED_VOXEL_OFFSET, bytes);
            return buffer;
        } catch(RuntimeException | Error error) {
            buffer.freeBuffer();
            throw error;
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    private static SectionVoxelSnapshot joinedVoxelFixture(GpuTerrainModelTable table) {
        ArrayList<Integer> qualified = new ArrayList<>();
        ArrayList<Integer> unqualified = new ArrayList<>();
        for(Block block : BuiltInRegistries.BLOCK) {
            for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = Block.getId(state);
                if(table.templateIndexForStateId(stateId) >= 0)
                    qualified.add(stateId);
                else
                    unqualified.add(stateId);
            }
        }
        require(!qualified.isEmpty() && !unqualified.isEmpty(),
                "Joined voxel fixture requires real qualified and unqualified block states");

        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        for(int voxel = 0; voxel < SectionVoxelSnapshot.BLOCK_COUNT; ++voxel) {
            int category = voxel & 3;
            boolean useQualified = category < 2;
            ArrayList<Integer> states = useQualified ? qualified : unqualified;
            int stateId = states.get((voxel >>> 2) % states.size());

            // Hint agrees for categories 0/3 and deliberately disagrees for 1/2.
            // The current resource-generation model table must be authoritative.
            int flags = SectionVoxelSnapshot.CPU_REQUIRED;
            if(category == 0 || category == 2)
                flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
            builder.add(stateId, flags);
        }
        return builder.finish();
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
