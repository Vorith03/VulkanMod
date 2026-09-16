package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.GpuTerrainOutputStore;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Real Vulkan oracle for bounded section-wide complete terrain generation.
 *
 * <p>Four isolated qualified voxels, including a section corner, share one exact
 * sparse-lighting record. The kernel classifies the whole 4096-voxel section,
 * atomically compacts surviving faces, reconstructs model/position/UV/AO/color/light
 * and writes complete 20-byte vertices directly into generation-owned area storage.
 * Compact ordering is deliberately not trusted: every result is joined back through
 * its encoded voxel/face descriptor and compared with the already CPU-validated
 * single-block complete-vertex probe.</p>
 */
public final class GpuTerrainSectionMesherSmokeTest {
    private static final int RESULT_HEADER_WORDS = 6;
    private static final int WORDS_PER_VERTEX = 5;
    private static final int WORDS_PER_FACE = GpuTerrainOutputStore.BYTES_PER_FACE / Integer.BYTES;
    private static final int EXPECTED_VOXELS = 4;
    private static final int EXPECTED_FACES = EXPECTED_VOXELS * 6;
    private static final int OVERFLOW_CAPACITY = 7;
    private static final int PUSH_CONSTANT_BYTES = 7 * Integer.BYTES;
    private static final int WORKGROUP_COUNT = SectionVoxelSnapshot.BLOCK_COUNT / 64;
    private static final long GENERATION = 811L;

    private static final int[] QUALIFIED_BLOCKS = {
            SectionVoxelSnapshot.blockIndex(1, 1, 1),
            SectionVoxelSnapshot.blockIndex(8, 8, 8),
            SectionVoxelSnapshot.blockIndex(14, 3, 12),
            SectionVoxelSnapshot.blockIndex(15, 15, 15)
    };

    private GpuTerrainSectionMesherSmokeTest() {}

    public static void verify() {
        if(AreaUploadManager.INSTANCE == null)
            throw new AssertionError("Section mesher smoke requires the terrain upload manager");

        GpuTerrainModelTable table = GpuTerrainModelTable.captureCurrent();
        require(table.templateCount() > 0,
                "Section mesher smoke requires at least one qualified model template");
        int qualifiedState = table.stateIdForTemplate(0);
        require(table.templateIndexForStateId(qualifiedState) == 0,
                "Section mesher model state must round-trip through the current table");

        Fixture fixture = fixture(qualifiedState);
        GpuTerrainModelGpuStore modelStore = new GpuTerrainModelGpuStore();
        RegionVoxelGpuStore inputStore = new RegionVoxelGpuStore();
        ChunkArea area = new ChunkArea(79,
                new Vector3i(fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z()));
        try {
            require(modelStore.upload(table), "Section mesher model table must upload");
            GpuTerrainModelGpuStore.Residency model = modelStore.getResidency();
            require(model.valid() && model.generation() == table.generation(),
                    "Section mesher model table must be current");

            require(inputStore.upload(0, fixture.voxel, GENERATION),
                    "Section mesher voxel input must queue");
            require(inputStore.uploadLighting(0, fixture.lighting, GENERATION),
                    "Section mesher lighting input must queue");
            AreaUploadManager.INSTANCE.submitUploads();

            RegionVoxelGpuStore.Residency voxel = inputStore.getResidency(0);
            RegionVoxelGpuStore.Residency lighting = inputStore.getLightingResidency(0);
            require(voxel.valid() && lighting.valid()
                            && voxel.generation() == GENERATION
                            && lighting.generation() == GENERATION,
                    "Section mesher inputs must publish under one generation");

            Map<Integer, int[]> oracle = buildOracle(inputStore, voxel, lighting,
                    model, table.templateCount());

            GpuTerrainOutputStore.Reservation reservation = area.reserveGpuTerrainOutput(
                    fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z(),
                    TerrainRenderType.SOLID, GENERATION, EXPECTED_FACES);
            require(reservation != null,
                    "Section mesher exact output reservation must fit without growth");

            MesherResult exact;
            try(Probe probe = new Probe()) {
                exact = reservation.withTarget(target -> probe.dispatch(
                        inputStore.getPageBuffer(voxel.pageIndex()), voxel,
                        inputStore.getPageBuffer(lighting.pageIndex()), lighting,
                        model, table.templateCount(), target, EXPECTED_FACES));
            }
            require(exact != null, "Section mesher exact target must remain live through submission");
            verifyExact(exact, oracle);

            require(area.publishGpuTerrainOutput(reservation, exact.writtenFaces(), false),
                    "Exact section mesher output must publish");
            GpuTerrainOutputStore.Residency resident = area.getGpuTerrainOutputResidency(
                    fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z(),
                    TerrainRenderType.SOLID);
            require(resident != null && resident.valid()
                            && resident.generation() == GENERATION
                            && resident.faceCount() == EXPECTED_FACES
                            && resident.byteLength() == EXPECTED_FACES
                            * GpuTerrainOutputStore.BYTES_PER_FACE,
                    "Exact section mesher residency must expose the complete compact output");

            int residentOffset = resident.byteOffset();
            GpuTerrainOutputStore.Reservation overflowReservation =
                    area.reserveGpuTerrainOutput(fixture.voxel.x(), fixture.voxel.y(),
                            fixture.voxel.z(), TerrainRenderType.SOLID,
                            GENERATION, OVERFLOW_CAPACITY);
            require(overflowReservation != null,
                    "Same-generation bounded retry reservation must fit");

            MesherResult overflow;
            try(Probe probe = new Probe()) {
                overflow = overflowReservation.withTarget(target -> probe.dispatch(
                        inputStore.getPageBuffer(voxel.pageIndex()), voxel,
                        inputStore.getPageBuffer(lighting.pageIndex()), lighting,
                        model, table.templateCount(), target, OVERFLOW_CAPACITY));
            }
            require(overflow != null, "Overflow target must remain live through submission");
            require(overflow.requestedFaces() == EXPECTED_FACES,
                    "Overflow dispatch must retain the exact requested face count");
            require(overflow.writtenFaces() == OVERFLOW_CAPACITY,
                    "Overflow dispatch must write exactly its declared capacity");
            require(overflow.overflow() && overflow.errorFlags() == 0,
                    "Overflow dispatch must fail only through the bounded-capacity flag");
            verifyDescriptors(overflow.descriptors, OVERFLOW_CAPACITY);

            require(!area.publishGpuTerrainOutput(overflowReservation,
                            overflow.writtenFaces(), true),
                    "Overflow output must fail closed instead of replacing residency");
            GpuTerrainOutputStore.Residency afterOverflow = area.getGpuTerrainOutputResidency(
                    fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z(),
                    TerrainRenderType.SOLID);
            require(afterOverflow != null && afterOverflow.valid()
                            && afterOverflow.generation() == GENERATION
                            && afterOverflow.faceCount() == EXPECTED_FACES
                            && afterOverflow.byteOffset() == residentOffset,
                    "Failed same-generation retry must preserve the previous valid GPU mesh");

            require(area.reserveGpuTerrainOutput(fixture.voxel.x(), fixture.voxel.y(),
                            fixture.voxel.z(), TerrainRenderType.SOLID,
                            GENERATION - 1L, EXPECTED_FACES) == null,
                    "Stale section generation must not obtain an output reservation");

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_SECTION_MESHER_OK: {} voxels, {} complete faces, descriptor-keyed exact vertex joins, boundary lighting, submit-safe area target lease, forced {}-face overflow preserves resident fallback",
                    EXPECTED_VOXELS, EXPECTED_FACES, OVERFLOW_CAPACITY);
        } finally {
            Vulkan.waitIdle();
            area.releaseBuffers();
            inputStore.close();
            modelStore.close();
        }
    }

    private static Fixture fixture(int stateId) {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 64, 0);
        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            boolean qualified = containsQualified(index);
            builder.add(qualified ? stateId : 0,
                    SectionVoxelSnapshot.CPU_REQUIRED
                            | (qualified ? SectionVoxelSnapshot.GPU_FULL_CUBE : 0));
        }
        SectionVoxelSnapshot voxel = builder.finish();

        int[] packedLight = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        int[] shadeBits = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        boolean[] passes = new boolean[GpuLightingDemandMap.SAMPLE_COUNT];
        for(int i = 0; i < packedLight.length; ++i) {
            int block = (i * 3 & 15) << 4;
            int sky = (15 - (i * 5 & 15)) << 4;
            packedLight[i] = block | (sky << 16);
            shadeBits[i] = Float.floatToRawIntBits(0.55F + (i % 5) * 0.08F);
            passes[i] = i % 7 != 0;
        }
        int[] directional = {
                Float.floatToRawIntBits(0.50F), Float.floatToRawIntBits(1.00F),
                Float.floatToRawIntBits(0.80F), Float.floatToRawIntBits(0.80F),
                Float.floatToRawIntBits(0.60F), Float.floatToRawIntBits(0.60F)
        };
        GpuSparseLightingSnapshot lighting = GpuSparseLightingSnapshot.fromDenseReference(
                voxel, packedLight, shadeBits, passes, directional);
        require(lighting != null,
                "Four isolated section-mesher cubes must fit the sparse-lighting cap");
        return new Fixture(voxel, lighting);
    }

    private static Map<Integer, int[]> buildOracle(
            RegionVoxelGpuStore inputStore,
            RegionVoxelGpuStore.Residency voxel,
            RegionVoxelGpuStore.Residency lighting,
            GpuTerrainModelGpuStore.Residency model,
            int templateCount) {
        Map<Integer, int[]> result = new HashMap<>();
        try(SparseLightingComputeProbe probe = new SparseLightingComputeProbe()) {
            for(int blockIndex : QUALIFIED_BLOCKS) {
                int[] complete = probe.dispatch(
                        inputStore.getPageBuffer(voxel.pageIndex()), voxel,
                        inputStore.getPageBuffer(lighting.pageIndex()), lighting,
                        blockIndex, model, templateCount);
                require(complete[0] == SparseLightingComputeProbe.RESULT_MAGIC
                                && complete[5] == 0,
                        "Per-block complete-vertex oracle must accept section-mesher fixture");
                result.put(blockIndex, complete);
            }
        }
        return result;
    }

    private static void verifyExact(MesherResult result, Map<Integer, int[]> oracle) {
        require(result.requestedFaces() == EXPECTED_FACES,
                "Section mesher must request six faces for every isolated qualified cube");
        require(result.writtenFaces() == EXPECTED_FACES,
                "Section mesher must write every exact-capacity face");
        require(!result.overflow() && result.errorFlags() == 0,
                "Exact-capacity section mesher must complete without overflow/errors");
        require(result.descriptors.length == EXPECTED_FACES
                        && result.vertices.length == EXPECTED_FACES * WORDS_PER_FACE,
                "Exact section mesher readback shape mismatch");

        boolean[] seen = verifyDescriptors(result.descriptors, EXPECTED_FACES);
        for(int slot = 0; slot < EXPECTED_FACES; ++slot) {
            int descriptor = result.descriptors[slot];
            int voxel = descriptor & 0xfff;
            int face = descriptor >>> 12 & 7;
            int[] expected = oracle.get(voxel);
            require(expected != null, "Compacted face must belong to a qualified fixture voxel");
            int expectedBase = SparseLightingComputeProbe.VERTEX_RESULT_BASE
                    + face * 4 * WORDS_PER_VERTEX;
            int actualBase = slot * WORDS_PER_FACE;
            for(int word = 0; word < WORDS_PER_FACE; ++word) {
                if(result.vertices[actualBase + word] != expected[expectedBase + word]) {
                    throw new AssertionError("Section mesher vertex mismatch slot=" + slot
                            + " voxel=" + voxel + " face=" + face + " word=" + word
                            + " expected=" + expected[expectedBase + word]
                            + " actual=" + result.vertices[actualBase + word]);
                }
            }
        }
        for(int block : QUALIFIED_BLOCKS) {
            for(int face = 0; face < 6; ++face)
                require(seen[block * 6 + face],
                        "Section mesher compact set must contain every isolated cube face");
        }
    }

    private static boolean[] verifyDescriptors(int[] descriptors, int count) {
        require(descriptors.length == count, "Compact descriptor readback count mismatch");
        boolean[] seen = new boolean[SectionVoxelSnapshot.BLOCK_COUNT * 6];
        for(int slot = 0; slot < descriptors.length; ++slot) {
            int descriptor = descriptors[slot];
            require((descriptor & 0x80000000) != 0,
                    "Section mesher compact descriptor must carry the live marker");
            int voxel = descriptor & 0xfff;
            int face = descriptor >>> 12 & 7;
            require(face < 6 && containsQualified(voxel),
                    "Section mesher descriptor must decode to a qualified fixture face");
            int key = voxel * 6 + face;
            require(!seen[key], "Section mesher compact output must not duplicate faces");
            seen[key] = true;
        }
        return seen;
    }

    private static boolean containsQualified(int index) {
        for(int block : QUALIFIED_BLOCKS) {
            if(block == index)
                return true;
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private record Fixture(SectionVoxelSnapshot voxel,
                           GpuSparseLightingSnapshot lighting) {}

    private record MesherResult(int[] header, int[] descriptors, int[] vertices) {
        int requestedFaces() { return header[0]; }
        int writtenFaces() { return header[1]; }
        boolean overflow() { return header[2] != 0; }
        int errorFlags() { return header[3]; }
    }

    private static final class Probe implements AutoCloseable {
        private long descriptorSetLayout;
        private long descriptorPool;
        private long descriptorSet;
        private long pipelineLayout;
        private long pipeline;
        private boolean closed;

        Probe() {
            if(!graphicsQueueSupportsCompute())
                throw new UnsupportedOperationException(
                        "Graphics queue family does not support compute dispatch");
            createDescriptorResources();
            createPipelineLayout();
            createPipeline();
        }

        MesherResult dispatch(
                StorageBuffer voxelPage, RegionVoxelGpuStore.Residency voxel,
                StorageBuffer lightingPage, RegionVoxelGpuStore.Residency lighting,
                GpuTerrainModelGpuStore.Residency model, int templateCount,
                GpuTerrainOutputStore.Target target, int faceCapacity) {
            if(closed)
                throw new IllegalStateException("Section mesher probe is closed");
            if(voxelPage == null || lightingPage == null || target == null
                    || target.bufferId() == 0L || model == null || !model.valid()
                    || model.buffer() == null)
                throw new IllegalArgumentException("Section mesher requires live GPU inputs/target");
            if(!voxel.valid() || !lighting.valid()
                    || voxel.generation() != lighting.generation())
                throw new IllegalArgumentException("Section mesher voxel/light generations must match");
            if(model.generation() != GpuTerrainModelRegistry.generation()
                    || templateCount <= 0)
                throw new IllegalArgumentException("Section mesher model generation is stale");
            if(faceCapacity <= 0 || faceCapacity > GpuTerrainOutputStore.MAX_FACES
                    || target.byteCapacity() != faceCapacity * GpuTerrainOutputStore.BYTES_PER_FACE
                    || target.byteOffset() < 0 || target.byteOffset() % Integer.BYTES != 0)
                throw new IllegalArgumentException("Section mesher target capacity is invalid");
            validateSlice(voxelPage, voxel,
                    SectionVoxelSnapshot.HEADER_WORDS * Integer.BYTES, "voxel");
            validateSlice(lightingPage, lighting,
                    GpuSparseLightingSnapshot.HEADER_WORDS * Integer.BYTES, "lighting");

            int resultWords = Math.addExact(RESULT_HEADER_WORDS, faceCapacity);
            int resultBytes = Math.multiplyExact(resultWords, Integer.BYTES);
            int vertexWords = Math.multiplyExact(faceCapacity, WORDS_PER_FACE);
            int vertexBytes = Math.multiplyExact(vertexWords, Integer.BYTES);
            int readbackBytes = Math.addExact(resultBytes, vertexBytes);
            StorageBuffer result = new StorageBuffer(resultBytes, MemoryTypes.GPU_MEM);
            long readbackBuffer = VK_NULL_HANDLE;
            long readbackAllocation = VK_NULL_HANDLE;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pReadbackBuffer = stack.mallocLong(1);
                var pReadbackAllocation = stack.mallocPointer(1);
                MemoryManager memoryManager = MemoryManager.getInstance();
                memoryManager.createBuffer(readbackBytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pReadbackBuffer, pReadbackAllocation);
                readbackBuffer = pReadbackBuffer.get(0);
                readbackAllocation = pReadbackAllocation.get(0);

                updateDescriptorSet(voxelPage, lightingPage, model.buffer(),
                        target.bufferId(), result, resultBytes);

                CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
                vkCmdFillBuffer(commandBuffer.getHandle(), result.getId(), 0L,
                        resultBytes, 0);
                barrierInputsToCompute(commandBuffer, voxelPage, lightingPage,
                        model.buffer(), target, result, resultBytes);

                vkCmdBindPipeline(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                        pipeline);
                vkCmdBindDescriptorSets(commandBuffer.getHandle(),
                        VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0,
                        stack.longs(descriptorSet), null);
                ByteBuffer push = stack.malloc(PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder());
                push.putInt(0, voxel.byteOffset());
                push.putInt(Integer.BYTES, voxel.byteLength());
                push.putInt(2 * Integer.BYTES, lighting.byteOffset());
                push.putInt(3 * Integer.BYTES, lighting.byteLength());
                push.putInt(4 * Integer.BYTES, templateCount);
                push.putInt(5 * Integer.BYTES, target.byteOffset() / Integer.BYTES);
                push.putInt(6 * Integer.BYTES, faceCapacity);
                vkCmdPushConstants(commandBuffer.getHandle(), pipelineLayout,
                        VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
                vkCmdDispatch(commandBuffer.getHandle(), WORKGROUP_COUNT, 1, 1);

                barrierComputeWritesToConsumers(commandBuffer, target, result, resultBytes);
                TransferQueue.uploadBufferCmd(commandBuffer, result.getId(), 0L,
                        readbackBuffer, 0L, resultBytes);
                TransferQueue.uploadBufferCmd(commandBuffer, target.bufferId(),
                        target.byteOffset(), readbackBuffer, resultBytes, vertexBytes);

                // Reservation.withTarget holds the AreaBuffer monitor until this
                // submission occurs. A later grow therefore cannot replace/free the
                // target before queue ownership of this command buffer is established.
                Device.getGraphicsQueue().submitCommands(commandBuffer);
                Synchronization.waitFence(commandBuffer.getFence());
                Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

                int[] header = new int[RESULT_HEADER_WORDS];
                int[] descriptors = new int[faceCapacity];
                int[] vertices = new int[vertexWords];
                long allocation = readbackAllocation;
                memoryManager.MapAndCopy(allocation, readbackBytes, pointer -> {
                    ByteBuffer bytes = pointer.getByteBuffer(0, readbackBytes)
                            .order(ByteOrder.nativeOrder());
                    for(int i = 0; i < header.length; ++i)
                        header[i] = bytes.getInt(i * Integer.BYTES);
                    for(int i = 0; i < descriptors.length; ++i)
                        descriptors[i] = bytes.getInt((RESULT_HEADER_WORDS + i)
                                * Integer.BYTES);
                    for(int i = 0; i < vertices.length; ++i)
                        vertices[i] = bytes.getInt(resultBytes + i * Integer.BYTES);
                });
                return new MesherResult(header, descriptors, vertices);
            } finally {
                result.freeBuffer();
                if(readbackBuffer != VK_NULL_HANDLE)
                    MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
            }
        }

        private void createDescriptorResources() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings =
                        VkDescriptorSetLayoutBinding.calloc(5, stack);
                for(int i = 0; i < 5; ++i) {
                    bindings.get(i)
                            .binding(i)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1)
                            .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                            .pImmutableSamplers(null);
                }
                VkDescriptorSetLayoutCreateInfo layoutInfo =
                        VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                .sType$Default().pBindings(bindings);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(Device.device, layoutInfo, null, pLayout),
                        "create section mesher descriptor set layout");
                descriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
                poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(5);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default().pPoolSizes(poolSizes).maxSets(1);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(Device.device, poolInfo, null, pPool),
                        "create section mesher descriptor pool");
                descriptorPool = pPool.get(0);

                VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default().descriptorPool(descriptorPool)
                        .pSetLayouts(stack.longs(descriptorSetLayout));
                LongBuffer pSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(Device.device, allocateInfo, pSet),
                        "allocate section mesher descriptor set");
                descriptorSet = pSet.get(0);
            }
        }

        private void createPipelineLayout() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
                pushRange.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .offset(0).size(PUSH_CONSTANT_BYTES);
                VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType$Default().pSetLayouts(stack.longs(descriptorSetLayout))
                        .pPushConstantRanges(pushRange);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreatePipelineLayout(Device.device, layoutInfo, null, pLayout),
                        "create section mesher pipeline layout");
                pipelineLayout = pLayout.get(0);
            }
        }

        private void createPipeline() {
            SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShaderResource(
                    "/assets/vulkanmod/shaders/terrain/section_mesher_probe.comp",
                    SPIRVUtils.ShaderKind.COMPUTE_SHADER);
            long shaderModule = VK_NULL_HANDLE;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default().pCode(spirv.bytecode());
                LongBuffer pModule = stack.mallocLong(1);
                check(vkCreateShaderModule(Device.device, moduleInfo, null, pModule),
                        "create section mesher shader module");
                shaderModule = pModule.get(0);

                VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(shaderModule).pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer pipelineInfo =
                        VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0).sType$Default().stage(stageInfo)
                        .layout(pipelineLayout).basePipelineHandle(VK_NULL_HANDLE)
                        .basePipelineIndex(-1);
                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE,
                                pipelineInfo, null, pPipeline),
                        "create section mesher compute pipeline");
                pipeline = pPipeline.get(0);
            } finally {
                if(shaderModule != VK_NULL_HANDLE)
                    vkDestroyShaderModule(Device.device, shaderModule, null);
                spirv.free();
            }
        }

        private void updateDescriptorSet(StorageBuffer voxelPage,
                                         StorageBuffer lightingPage,
                                         StorageBuffer modelBuffer,
                                         long targetBuffer,
                                         StorageBuffer result,
                                         int resultBytes) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(5, stack);
                infos.get(0).buffer(voxelPage.getId()).offset(0L)
                        .range(voxelPage.getBufferSize());
                infos.get(1).buffer(lightingPage.getId()).offset(0L)
                        .range(lightingPage.getBufferSize());
                infos.get(2).buffer(modelBuffer.getId()).offset(0L)
                        .range(modelBuffer.getBufferSize());
                infos.get(3).buffer(targetBuffer).offset(0L).range(VK_WHOLE_SIZE);
                infos.get(4).buffer(result.getId()).offset(0L).range(resultBytes);

                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(5, stack);
                for(int i = 0; i < 5; ++i) {
                    writes.get(i).sType$Default().dstSet(descriptorSet).dstBinding(i)
                            .dstArrayElement(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1)
                            .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(i).address(), 1));
                }
                vkUpdateDescriptorSets(Device.device, writes, null);
            }
        }

        private static void barrierInputsToCompute(
                CommandPool.CommandBuffer commandBuffer,
                StorageBuffer voxelPage, StorageBuffer lightingPage,
                StorageBuffer modelBuffer, GpuTerrainOutputStore.Target target,
                StorageBuffer result, int resultBytes) {
            boolean sharedPage = voxelPage.getId() == lightingPage.getId();
            int count = sharedPage ? 4 : 5;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(count, stack);
                int cursor = 0;
                barriers.get(cursor++).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(voxelPage.getId()).offset(0L).size(voxelPage.getBufferSize());
                if(!sharedPage) {
                    barriers.get(cursor++).sType$Default()
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .buffer(lightingPage.getId()).offset(0L)
                            .size(lightingPage.getBufferSize());
                }
                barriers.get(cursor++).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(modelBuffer.getId()).offset(0L).size(modelBuffer.getBufferSize());
                barriers.get(cursor++).sType$Default()
                        .srcAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT
                                | VK_ACCESS_TRANSFER_READ_BIT
                                | VK_ACCESS_TRANSFER_WRITE_BIT
                                | VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(target.bufferId()).offset(target.byteOffset())
                        .size(target.byteCapacity());
                barriers.get(cursor).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(result.getId()).offset(0L).size(resultBytes);

                vkCmdPipelineBarrier(commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_TRANSFER_BIT
                                | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT
                                | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        0, null, barriers, null);
            }
        }

        private static void barrierComputeWritesToConsumers(
                CommandPool.CommandBuffer commandBuffer,
                GpuTerrainOutputStore.Target target,
                StorageBuffer result, int resultBytes) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(2, stack);
                barriers.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT
                                | VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(target.bufferId()).offset(target.byteOffset())
                        .size(target.byteCapacity());
                barriers.get(1).sType$Default()
                        .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(result.getId()).offset(0L).size(resultBytes);
                vkCmdPipelineBarrier(commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                        0, null, barriers, null);
            }
        }

        private static void validateSlice(StorageBuffer page,
                                          RegionVoxelGpuStore.Residency residency,
                                          int minimumBytes, String label) {
            if(page == null || residency == null || !residency.valid())
                throw new IllegalArgumentException("Section mesher " + label
                        + " residency must be valid");
            long end = (long)residency.byteOffset() + residency.byteLength();
            if(residency.byteOffset() < 0 || residency.byteLength() < minimumBytes
                    || end > page.getBufferSize())
                throw new IllegalArgumentException("Section mesher " + label
                        + " residency exceeds its page");
        }

        private static boolean graphicsQueueSupportsCompute() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer count = stack.ints(0);
                vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, null);
                VkQueueFamilyProperties.Buffer properties =
                        VkQueueFamilyProperties.malloc(count.get(0), stack);
                vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, properties);
                int graphicsFamily = Queue.getQueueFamilies().graphicsFamily;
                return (properties.get(graphicsFamily).queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0;
            }
        }

        private static void check(int result, String action) {
            if(result != VK_SUCCESS)
                throw new RuntimeException("Failed to " + action + ": " + result);
        }

        @Override
        public void close() {
            if(closed)
                return;
            closed = true;
            if(pipeline != VK_NULL_HANDLE)
                vkDestroyPipeline(Device.device, pipeline, null);
            if(pipelineLayout != VK_NULL_HANDLE)
                vkDestroyPipelineLayout(Device.device, pipelineLayout, null);
            if(descriptorPool != VK_NULL_HANDLE)
                vkDestroyDescriptorPool(Device.device, descriptorPool, null);
            if(descriptorSetLayout != VK_NULL_HANDLE)
                vkDestroyDescriptorSetLayout(Device.device, descriptorSetLayout, null);
        }
    }
}
