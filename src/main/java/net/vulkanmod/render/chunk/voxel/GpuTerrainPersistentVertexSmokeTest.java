package net.vulkanmod.render.chunk.voxel;

import net.minecraft.world.level.block.state.BlockState;
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

import static org.lwjgl.vulkan.VK10.*;

/**
 * CI-only end-to-end proof that the already-verified complete terrain vertices can
 * remain on the GPU and land in generation-owned persistent terrain storage.
 *
 * <p>The sparse-lighting shader still writes its diagnostic scratch layout. This
 * probe copies only the six complete cube faces from that GPU scratch buffer into an
 * {@link GpuTerrainOutputStore} reservation, then proves the persistent bytes match
 * the independent complete-vertex oracle. There is no CPU readback/reupload join.</p>
 */
public final class GpuTerrainPersistentVertexSmokeTest {
    private static final int FACE_COUNT = 6;
    private static final int VERTICES_PER_FACE = 4;
    private static final int WORDS_PER_VERTEX = 5;
    private static final int VERTEX_WORDS = FACE_COUNT * VERTICES_PER_FACE * WORDS_PER_VERTEX;
    private static final int VERTEX_BYTES = VERTEX_WORDS * Integer.BYTES;
    private static final int HEADER_WORDS = 6;
    private static final int HEADER_BYTES = HEADER_WORDS * Integer.BYTES;
    private static final int READBACK_BYTES = HEADER_BYTES + VERTEX_BYTES;
    private static final int PUSH_CONSTANT_BYTES = 6 * Integer.BYTES;
    private static final int WORKGROUP_SIZE = 64;
    private static final int WORKGROUP_COUNT = (GpuLightingDemandMap.SAMPLE_COUNT
            + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;

    private GpuTerrainPersistentVertexSmokeTest() {}

    public static void verify() {
        if(AreaUploadManager.INSTANCE == null)
            throw new AssertionError("Persistent GPU terrain vertex smoke requires the terrain upload manager");

        GpuTerrainModelTable table = GpuTerrainModelTable.captureCurrent();
        require(table.templateCount() > 0,
                "Persistent GPU terrain vertex smoke requires a qualified model template");

        GpuTerrainModelGpuStore modelStore = new GpuTerrainModelGpuStore();
        RegionVoxelGpuStore inputStore = new RegionVoxelGpuStore();
        var baseFixture = CanonicalCubeLightingSmokeTest.sparseGpuFixture(0);
        var fixture = withQualifiedModelState(baseFixture, table);
        SectionVoxelSnapshot voxel = fixture.voxel();
        long generation = 700L;
        ChunkArea area = new ChunkArea(73,
                new Vector3i(voxel.x(), voxel.y(), voxel.z()));
        try {
            require(modelStore.upload(table),
                    "Persistent GPU terrain vertex model table must upload");
            var model = modelStore.getResidency();
            require(model.valid() && model.generation() == table.generation(),
                    "Persistent GPU terrain vertex model table must be current");

            require(inputStore.upload(0, voxel, generation),
                    "Persistent GPU terrain vertex voxel input must queue");
            require(inputStore.uploadLighting(0, fixture.lighting(), generation),
                    "Persistent GPU terrain vertex lighting input must queue");
            AreaUploadManager.INSTANCE.submitUploads();
            var voxelResidency = inputStore.getResidency(0);
            var lightingResidency = inputStore.getLightingResidency(0);
            require(voxelResidency.valid() && lightingResidency.valid()
                            && voxelResidency.generation() == generation
                            && lightingResidency.generation() == generation,
                    "Persistent GPU terrain vertex inputs must publish under one generation");

            int[] oracle;
            try(SparseLightingComputeProbe diagnostic = new SparseLightingComputeProbe()) {
                oracle = diagnostic.dispatch(
                        inputStore.getPageBuffer(voxelResidency.pageIndex()), voxelResidency,
                        inputStore.getPageBuffer(lightingResidency.pageIndex()), lightingResidency,
                        fixture.blockIndex(), model, table.templateCount());
            }
            require(oracle[0] == SparseLightingComputeProbe.RESULT_MAGIC && oracle[5] == 0,
                    "Complete-vertex oracle must succeed before persistent publication proof");

            var reservation = area.reserveGpuTerrainOutput(
                    voxel.x(), voxel.y(), voxel.z(), TerrainRenderType.SOLID,
                    generation, FACE_COUNT);
            require(reservation != null,
                    "Persistent GPU terrain vertex reservation must fit");
            var target = area.getGpuTerrainOutputTarget(reservation);
            require(target != null && target.byteCapacity() == VERTEX_BYTES,
                    "Persistent GPU terrain vertex target must expose exact six-face capacity");

            PersistentResult persistent;
            try(PersistentProbe probe = new PersistentProbe()) {
                persistent = probe.dispatch(
                        inputStore.getPageBuffer(voxelResidency.pageIndex()), voxelResidency,
                        inputStore.getPageBuffer(lightingResidency.pageIndex()), lightingResidency,
                        fixture.blockIndex(), model, table.templateCount(),
                        reservation, target);
            }

            require(persistent.header[0] == SparseLightingComputeProbe.RESULT_MAGIC,
                    "Persistent GPU terrain vertex compute header magic mismatch");
            require(persistent.header[5] == 0,
                    "Persistent GPU terrain vertex compute reported an input/model error");
            require(persistent.vertices.length == VERTEX_WORDS,
                    "Persistent GPU terrain vertex readback size mismatch");
            for(int word = 0; word < VERTEX_WORDS; ++word) {
                int expected = oracle[SparseLightingComputeProbe.VERTEX_RESULT_BASE + word];
                int actual = persistent.vertices[word];
                if(actual != expected) {
                    throw new AssertionError("Persistent GPU terrain vertex mismatch at word "
                            + word + ": expected=" + expected + " actual=" + actual);
                }
            }

            require(area.publishGpuTerrainOutput(reservation, FACE_COUNT, false),
                    "Exact persistent GPU terrain vertices must publish");
            var resident = area.getGpuTerrainOutputResidency(
                    voxel.x(), voxel.y(), voxel.z(), TerrainRenderType.SOLID);
            require(resident != null && resident.valid()
                            && resident.generation() == generation
                            && resident.faceCount() == FACE_COUNT
                            && resident.byteLength() == VERTEX_BYTES,
                    "Persistent GPU terrain vertex residency mismatch");

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_PERSISTENT_VERTEX_OK: generation-matched voxel/light/model compute, GPU-only complete-vertex copy into area residency, exact 480-byte persistent output");
        } finally {
            Vulkan.waitIdle();
            area.releaseBuffers();
            inputStore.close();
            modelStore.close();
        }
    }

    private static CanonicalCubeLightingSmokeTest.SparseGpuFixture withQualifiedModelState(
            CanonicalCubeLightingSmokeTest.SparseGpuFixture fixture,
            GpuTerrainModelTable table) {
        int qualifiedStateId = table.stateIdForTemplate(0);
        require(table.templateIndexForStateId(qualifiedStateId) == 0,
                "Qualified persistent-vertex model state must round-trip through the table");

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

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private record PersistentResult(int[] header, int[] vertices) {}

    private static final class PersistentProbe implements AutoCloseable {
        private long descriptorSetLayout;
        private long descriptorPool;
        private long descriptorSet;
        private long pipelineLayout;
        private long pipeline;
        private boolean closed;

        PersistentProbe() {
            if(!graphicsQueueSupportsCompute())
                throw new UnsupportedOperationException(
                        "Graphics queue family does not support compute dispatch");
            createDescriptorResources();
            createPipelineLayout();
            createPipeline();
        }

        PersistentResult dispatch(
                StorageBuffer voxelPage, RegionVoxelGpuStore.Residency voxelResidency,
                StorageBuffer lightingPage, RegionVoxelGpuStore.Residency lightingResidency,
                int blockIndex, GpuTerrainModelGpuStore.Residency modelResidency,
                int templateCount, GpuTerrainOutputStore.Reservation reservation,
                GpuTerrainOutputStore.Target target) {
            if(closed)
                throw new IllegalStateException("Persistent GPU terrain vertex probe is closed");
            if(voxelPage == null || lightingPage == null || modelResidency == null
                    || !modelResidency.valid() || modelResidency.buffer() == null)
                throw new IllegalArgumentException("Persistent GPU terrain vertex inputs must be resident");
            if(voxelResidency == null || lightingResidency == null
                    || !voxelResidency.valid() || !lightingResidency.valid()
                    || voxelResidency.generation() != lightingResidency.generation())
                throw new IllegalArgumentException("Persistent GPU terrain voxel/light generations must match");
            if(reservation == null || target == null
                    || reservation.generation() != voxelResidency.generation()
                    || target.bufferId() == 0L || target.byteCapacity() < VERTEX_BYTES)
                throw new IllegalArgumentException("Persistent GPU terrain output generation/target mismatch");
            if(blockIndex < 0 || blockIndex >= SectionVoxelSnapshot.BLOCK_COUNT)
                throw new IllegalArgumentException("Persistent GPU terrain block index is outside the section");
            if(templateCount <= 0
                    || modelResidency.generation() != GpuTerrainModelRegistry.generation())
                throw new IllegalArgumentException("Persistent GPU terrain model generation is stale");

            validateSlice(voxelPage, voxelResidency,
                    SectionVoxelSnapshot.HEADER_WORDS * Integer.BYTES, "voxel");
            validateSlice(lightingPage, lightingResidency,
                    GpuSparseLightingSnapshot.HEADER_WORDS * Integer.BYTES, "lighting");

            int scratchBytes = Math.multiplyExact(
                    SparseLightingComputeProbe.RESULT_WORDS, Integer.BYTES);
            StorageBuffer scratch = new StorageBuffer(scratchBytes, MemoryTypes.GPU_MEM);
            long readbackBuffer = VK_NULL_HANDLE;
            long readbackAllocation = VK_NULL_HANDLE;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pReadbackBuffer = stack.mallocLong(1);
                var pReadbackAllocation = stack.mallocPointer(1);
                MemoryManager memoryManager = MemoryManager.getInstance();
                memoryManager.createBuffer(READBACK_BYTES, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pReadbackBuffer, pReadbackAllocation);
                readbackBuffer = pReadbackBuffer.get(0);
                readbackAllocation = pReadbackAllocation.get(0);

                updateDescriptorSet(voxelPage, lightingPage, scratch,
                        scratchBytes, modelResidency.buffer());

                CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
                vkCmdFillBuffer(commandBuffer.getHandle(), scratch.getId(), 0L,
                        scratchBytes, 0);
                barrierInputsToCompute(commandBuffer, voxelPage, lightingPage,
                        scratch, scratchBytes, modelResidency.buffer());

                vkCmdBindPipeline(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                        pipeline);
                vkCmdBindDescriptorSets(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                        pipelineLayout, 0, stack.longs(descriptorSet), null);
                ByteBuffer push = stack.malloc(PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder());
                push.putInt(0, voxelResidency.byteOffset());
                push.putInt(Integer.BYTES, voxelResidency.byteLength());
                push.putInt(2 * Integer.BYTES, lightingResidency.byteOffset());
                push.putInt(3 * Integer.BYTES, lightingResidency.byteLength());
                push.putInt(4 * Integer.BYTES, blockIndex);
                push.putInt(5 * Integer.BYTES, templateCount);
                vkCmdPushConstants(commandBuffer.getHandle(), pipelineLayout,
                        VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
                vkCmdDispatch(commandBuffer.getHandle(), WORKGROUP_COUNT, 1, 1);

                barrierScratchToTransfer(commandBuffer, scratch, scratchBytes);
                barrierTargetToTransferWrite(commandBuffer, target);
                TransferQueue.uploadBufferCmd(commandBuffer,
                        scratch.getId(),
                        (long)SparseLightingComputeProbe.VERTEX_RESULT_BASE * Integer.BYTES,
                        target.bufferId(), target.byteOffset(), VERTEX_BYTES);
                barrierTargetTransferToConsumers(commandBuffer, target);

                TransferQueue.uploadBufferCmd(commandBuffer,
                        scratch.getId(), 0L,
                        readbackBuffer, 0L, HEADER_BYTES);
                TransferQueue.uploadBufferCmd(commandBuffer,
                        target.bufferId(), target.byteOffset(),
                        readbackBuffer, HEADER_BYTES, VERTEX_BYTES);

                Device.getGraphicsQueue().submitCommands(commandBuffer);
                Synchronization.waitFence(commandBuffer.getFence());
                Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

                int[] header = new int[HEADER_WORDS];
                int[] vertices = new int[VERTEX_WORDS];
                long allocation = readbackAllocation;
                memoryManager.MapAndCopy(allocation, READBACK_BYTES, pointer -> {
                    ByteBuffer bytes = pointer.getByteBuffer(0, READBACK_BYTES)
                            .order(ByteOrder.nativeOrder());
                    for(int i = 0; i < header.length; ++i)
                        header[i] = bytes.getInt(i * Integer.BYTES);
                    for(int i = 0; i < vertices.length; ++i)
                        vertices[i] = bytes.getInt(HEADER_BYTES + i * Integer.BYTES);
                });
                return new PersistentResult(header, vertices);
            } finally {
                scratch.freeBuffer();
                if(readbackBuffer != VK_NULL_HANDLE)
                    MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
            }
        }

        private void createDescriptorResources() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings =
                        VkDescriptorSetLayoutBinding.calloc(4, stack);
                for(int i = 0; i < 4; ++i) {
                    bindings.get(i)
                            .binding(i)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1)
                            .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                            .pImmutableSamplers(null);
                }

                VkDescriptorSetLayoutCreateInfo layoutInfo =
                        VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                .sType$Default()
                                .pBindings(bindings);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(Device.device, layoutInfo, null, pLayout),
                        "create persistent GPU terrain vertex descriptor set layout");
                descriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
                poolSizes.get(0)
                        .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(4);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default()
                        .pPoolSizes(poolSizes)
                        .maxSets(1);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(Device.device, poolInfo, null, pPool),
                        "create persistent GPU terrain vertex descriptor pool");
                descriptorPool = pPool.get(0);

                VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default()
                        .descriptorPool(descriptorPool)
                        .pSetLayouts(stack.longs(descriptorSetLayout));
                LongBuffer pSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(Device.device, allocateInfo, pSet),
                        "allocate persistent GPU terrain vertex descriptor set");
                descriptorSet = pSet.get(0);
            }
        }

        private void createPipelineLayout() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
                pushRange.get(0)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .offset(0)
                        .size(PUSH_CONSTANT_BYTES);
                VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pSetLayouts(stack.longs(descriptorSetLayout))
                        .pPushConstantRanges(pushRange);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreatePipelineLayout(Device.device, layoutInfo, null, pLayout),
                        "create persistent GPU terrain vertex pipeline layout");
                pipelineLayout = pLayout.get(0);
            }
        }

        private void createPipeline() {
            SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShaderResource(
                    "/assets/vulkanmod/shaders/terrain/sparse_lighting_probe.comp",
                    SPIRVUtils.ShaderKind.COMPUTE_SHADER);
            long shaderModule = VK_NULL_HANDLE;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default()
                        .pCode(spirv.bytecode());
                LongBuffer pModule = stack.mallocLong(1);
                check(vkCreateShaderModule(Device.device, moduleInfo, null, pModule),
                        "create persistent GPU terrain vertex shader module");
                shaderModule = pModule.get(0);

                VkPipelineShaderStageCreateInfo stageInfo =
                        VkPipelineShaderStageCreateInfo.calloc(stack)
                                .sType$Default()
                                .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                                .module(shaderModule)
                                .pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer pipelineInfo =
                        VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0)
                        .sType$Default()
                        .stage(stageInfo)
                        .layout(pipelineLayout)
                        .basePipelineHandle(VK_NULL_HANDLE)
                        .basePipelineIndex(-1);
                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE,
                                pipelineInfo, null, pPipeline),
                        "create persistent GPU terrain vertex pipeline");
                pipeline = pPipeline.get(0);
            } finally {
                if(shaderModule != VK_NULL_HANDLE)
                    vkDestroyShaderModule(Device.device, shaderModule, null);
                spirv.free();
            }
        }

        private void updateDescriptorSet(StorageBuffer voxelPage,
                                         StorageBuffer lightingPage,
                                         StorageBuffer scratch,
                                         int scratchBytes,
                                         StorageBuffer modelBuffer) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(4, stack);
                infos.get(0).buffer(voxelPage.getId()).offset(0L)
                        .range(voxelPage.getBufferSize());
                infos.get(1).buffer(lightingPage.getId()).offset(0L)
                        .range(lightingPage.getBufferSize());
                infos.get(2).buffer(scratch.getId()).offset(0L).range(scratchBytes);
                infos.get(3).buffer(modelBuffer.getId()).offset(0L)
                        .range(modelBuffer.getBufferSize());

                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);
                for(int i = 0; i < 4; ++i) {
                    writes.get(i)
                            .sType$Default()
                            .dstSet(descriptorSet)
                            .dstBinding(i)
                            .dstArrayElement(0)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1)
                            .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(i).address(), 1));
                }
                vkUpdateDescriptorSets(Device.device, writes, null);
            }
        }

        private static void barrierInputsToCompute(
                CommandPool.CommandBuffer commandBuffer,
                StorageBuffer voxelPage, StorageBuffer lightingPage,
                StorageBuffer scratch, int scratchBytes,
                StorageBuffer modelBuffer) {
            boolean sharedInputPage = voxelPage.getId() == lightingPage.getId();
            int count = sharedInputPage ? 3 : 4;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(count, stack);
                int cursor = 0;
                barriers.get(cursor++)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(voxelPage.getId()).offset(0L)
                        .size(voxelPage.getBufferSize());
                if(!sharedInputPage) {
                    barriers.get(cursor++)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .buffer(lightingPage.getId()).offset(0L)
                            .size(lightingPage.getBufferSize());
                }
                barriers.get(cursor++)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(scratch.getId()).offset(0L).size(scratchBytes);
                barriers.get(cursor)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(modelBuffer.getId()).offset(0L)
                        .size(modelBuffer.getBufferSize());

                vkCmdPipelineBarrier(commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        0, null, barriers, null);
            }
        }

        private static void barrierScratchToTransfer(
                CommandPool.CommandBuffer commandBuffer,
                StorageBuffer scratch, int scratchBytes) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.get(0)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(scratch.getId()).offset(0L).size(scratchBytes);
                vkCmdPipelineBarrier(commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, barrier, null);
            }
        }

        private static void barrierTargetToTransferWrite(
                CommandPool.CommandBuffer commandBuffer,
                GpuTerrainOutputStore.Target target) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.get(0)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT
                                | VK_ACCESS_TRANSFER_READ_BIT
                                | VK_ACCESS_TRANSFER_WRITE_BIT
                                | VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(target.bufferId()).offset(target.byteOffset())
                        .size(VERTEX_BYTES);
                vkCmdPipelineBarrier(commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_VERTEX_INPUT_BIT
                                | VK_PIPELINE_STAGE_TRANSFER_BIT
                                | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, barrier, null);
            }
        }

        private static void barrierTargetTransferToConsumers(
                CommandPool.CommandBuffer commandBuffer,
                GpuTerrainOutputStore.Target target) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.get(0)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT
                                | VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(target.bufferId()).offset(target.byteOffset())
                        .size(VERTEX_BYTES);
                vkCmdPipelineBarrier(commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                        0, null, barrier, null);
            }
        }

        private static void validateSlice(StorageBuffer page,
                                          RegionVoxelGpuStore.Residency residency,
                                          int minimumBytes, String label) {
            if(page == null || residency == null || !residency.valid())
                throw new IllegalArgumentException("Persistent GPU terrain " + label
                        + " residency must be valid");
            long end = (long)residency.byteOffset() + residency.byteLength();
            if(residency.byteOffset() < 0 || residency.byteLength() < minimumBytes
                    || end > page.getBufferSize())
                throw new IllegalArgumentException("Persistent GPU terrain " + label
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
