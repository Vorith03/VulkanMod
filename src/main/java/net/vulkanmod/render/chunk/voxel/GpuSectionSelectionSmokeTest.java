package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.RegionBatchLayout;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.joml.Vector3i;
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
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** Isolated Vulkan oracle for generation-safe region section selection. */
public final class GpuSectionSelectionSmokeTest {
    private static final int COMMAND_WORDS = RegionBatchLayout.STRIDE / Integer.BYTES;
    private static final int OUTPUT_HEADER_WORDS = 4;
    private static final int REQUESTED = 0;
    private static final int WRITTEN = 1;
    private static final int OVERFLOW = 2;
    private static final int TABLE_VALID = 3;
    private static final int PARAMETER_WORDS = 36;
    private static final int TARGET_LAYER = 1;
    private static final long GENERATION = 0x1122334455667788L;

    // Camera-relative planes: x [-16,48], y [-32,64], z [0,80].
    private static final float[][] PLANES = {
            { 1, 0, 0, 16}, {-1, 0, 0, 48},
            { 0, 1, 0, 32}, { 0,-1, 0, 64},
            { 0, 0, 1,  0}, { 0, 0,-1, 80}
    };

    private GpuSectionSelectionSmokeTest() {}

    public static void verify() {
        int regionX = -128;
        int regionY = -128;
        int regionZ = -128;
        float relativeX = -64.0f;
        float relativeY = -64.0f;
        float relativeZ = -64.0f;
        GpuRegionCandidateTable.Builder builder = new GpuRegionCandidateTable.Builder(
                GENERATION, regionX, regionY, regionZ);
        boolean[] expected = new boolean[RegionBatchLayout.MAX_SECTIONS];
        int expectedCount = 0;
        for(int section = 0; section < RegionBatchLayout.MAX_SECTIONS; ++section) {
            boolean ready = section % 5 != 0;
            boolean graphVisible = section % 7 != 0;
            int layer = section % 3;
            int indexCount = section % 11 == 0 ? 0 : 6 + section * 3;
            int instanceCount = section % 13 == 0 ? 0 : 1;
            builder.add(indexCount, instanceCount, section * 7, -section * 13, section,
                    GpuRegionCandidateTable.flags(ready, graphVisible, layer));
            expected[section] = ready && graphVisible && layer == TARGET_LAYER
                    && indexCount != 0 && instanceCount != 0
                    && intersectsFrustum(section, relativeX, relativeY, relativeZ);
            if(expected[section]) expectedCount++;
        }
        GpuRegionCandidateTable table = builder.finish();
        require(table.candidateCount() == RegionBatchLayout.MAX_SECTIONS
                        && table.byteSize() == (GpuRegionCandidateTable.HEADER_WORDS
                        + RegionBatchLayout.MAX_SECTIONS * GpuRegionCandidateTable.RECORD_WORDS)
                        * Integer.BYTES,
                "Region candidate ABI must have deterministic bounded size");

        verifyResidency(table);

        try(Probe probe = new Probe()) {
            int[] full = probe.dispatch(table, GENERATION, TARGET_LAYER,
                    relativeX, relativeY, relativeZ, RegionBatchLayout.MAX_SECTIONS);
            require(full[TABLE_VALID] == 1 && full[REQUESTED] == expectedCount
                            && full[WRITTEN] == expectedCount && full[OVERFLOW] == 0,
                    "GPU frustum selection must exactly count the current eligible set");
            verifyCommands(full, expected, expectedCount,
                    RegionBatchLayout.MAX_SECTIONS, true);

            int capacity = 9;
            int[] bounded = probe.dispatch(table, GENERATION, TARGET_LAYER,
                    relativeX, relativeY, relativeZ, capacity);
            require(bounded[TABLE_VALID] == 1 && bounded[REQUESTED] == expectedCount
                            && bounded[WRITTEN] == capacity && bounded[OVERFLOW] == 1,
                    "Bounded GPU frustum selection must signal overflow without overrunning");
            verifyCommands(bounded, expected, capacity, capacity, false);

            int[] stale = probe.dispatch(table, GENERATION + 1, TARGET_LAYER,
                    relativeX, relativeY, relativeZ, RegionBatchLayout.MAX_SECTIONS);
            for(int word : stale)
                require(word == 0, "Stale region generation must produce no GPU commands");
        }

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_SECTION_SELECTION_OK: {} exact frustum/layer/ready/graph matches from {} generation-owned candidates; bounded overflow and stale rejection verified",
                expectedCount, RegionBatchLayout.MAX_SECTIONS);
    }

    private static void verifyResidency(GpuRegionCandidateTable table) {
        GpuRegionCandidateGpuStore store = new GpuRegionCandidateGpuStore();
        try {
            require(store.upload(table), "Initial GPU candidate upload must queue");
            require(!store.getResidency().valid()
                            && store.getResidency().generation() == table.generation(),
                    "Candidate residency must not publish before copy submission");
            AreaUploadManager.INSTANCE.submitUploads();
            GpuRegionCandidateGpuStore.Residency first = store.getResidency();
            require(first.valid() && first.generation() == table.generation()
                            && first.regionX() == table.regionX()
                            && first.regionY() == table.regionY()
                            && first.regionZ() == table.regionZ(),
                    "Submitted candidate generation and region must publish together");
            verifyResidencyBytes(first, table);

            GpuRegionCandidateTable replacement = singleCandidateTable(
                    table.generation() + 1, table.regionX(), table.regionY(), table.regionZ());
            require(store.upload(replacement), "Replacement GPU candidate upload must queue");
            require(!store.getResidency().valid(),
                    "Replacement generation must revoke discoverable old residency");
            AreaUploadManager.INSTANCE.submitUploads();
            GpuRegionCandidateGpuStore.Residency second = store.getResidency();
            require(second.valid() && second.generation() == replacement.generation()
                            && second.buffer().getId() != first.buffer().getId(),
                    "Candidate replacement must allocate then publish without overwrite");
            verifyResidencyBytes(second, replacement);
            require(!store.upload(table) && store.getResidency().valid(),
                    "Stale candidate uploads must not revoke the current generation");

            GpuRegionCandidateTable abandoned = singleCandidateTable(
                    replacement.generation() + 1, table.regionX(), table.regionY(), table.regionZ());
            require(store.upload(abandoned), "Pending candidate generation must queue");
            store.invalidate(abandoned.generation() + 1);
            AreaUploadManager.INSTANCE.submitUploads();
            require(!store.getResidency().valid()
                            && store.getResidency().generation() == abandoned.generation() + 1,
                    "Invalidation before submission must prevent stale candidate publication");
        } finally {
            Vulkan.waitIdle();
            store.close();
        }

        ChunkArea area = new ChunkArea(0,
                new Vector3i(table.regionX(), table.regionY(), table.regionZ()));
        try {
            require(area.publishGpuCandidates(table), "ChunkArea must own matching candidate upload");
            AreaUploadManager.INSTANCE.submitUploads();
            require(area.getGpuCandidateResidency() != null
                            && area.getGpuCandidateResidency().valid(),
                    "ChunkArea must expose its submitted candidate generation");
            area.setPosition(0, 0, 0);
            require(area.getGpuCandidateResidency() == null,
                    "Region reposition must clear candidate residency");
        } finally {
            Vulkan.waitIdle();
            area.releaseBuffers();
        }
    }

    private static GpuRegionCandidateTable singleCandidateTable(long generation,
                                                                 int x, int y, int z) {
        return new GpuRegionCandidateTable.Builder(generation, x, y, z)
                .add(6, 1, 0, 0, 0,
                        GpuRegionCandidateTable.flags(true, true, TARGET_LAYER))
                .finish();
    }

    private static void verifyResidencyBytes(GpuRegionCandidateGpuStore.Residency residency,
                                             GpuRegionCandidateTable expectedTable) {
        int byteLength = expectedTable.byteSize();
        ByteBuffer expected = MemoryUtil.memAlloc(byteLength).order(ByteOrder.nativeOrder());
        long readbackBuffer = VK_NULL_HANDLE;
        long readbackAllocation = VK_NULL_HANDLE;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            expectedTable.writeTo(expected);
            expected.flip();
            LongBuffer pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            MemoryManager manager = MemoryManager.getInstance();
            manager.createBuffer(byteLength, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer, pAllocation);
            readbackBuffer = pBuffer.get(0);
            readbackAllocation = pAllocation.get(0);
            CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
            TransferQueue.uploadBufferCmd(commandBuffer, residency.buffer().getId(), 0L,
                    readbackBuffer, 0L, byteLength);
            Device.getGraphicsQueue().submitCommands(commandBuffer);
            Synchronization.waitFence(commandBuffer.getFence());
            Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);
            long allocation = readbackAllocation;
            manager.MapAndCopy(allocation, byteLength, pointer -> {
                ByteBuffer actual = pointer.getByteBuffer(0, byteLength);
                for(int index = 0; index < byteLength; ++index)
                    require(actual.get(index) == expected.get(index),
                            "GPU candidate residency byte mismatch at " + index);
            });
        } finally {
            MemoryUtil.memFree(expected);
            if(readbackBuffer != VK_NULL_HANDLE)
                MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
        }
    }

    private static boolean intersectsFrustum(int packedSection, float regionX,
                                             float regionY, float regionZ) {
        float minX = regionX + ((packedSection & 7) << 4);
        float minY = regionY + (((packedSection >>> 3) & 7) << 4);
        float minZ = regionZ + (((packedSection >>> 6) & 7) << 4);
        float maxX = minX + 16.0f;
        float maxY = minY + 16.0f;
        float maxZ = minZ + 16.0f;
        for(float[] plane : PLANES) {
            float x = plane[0] >= 0.0f ? maxX : minX;
            float y = plane[1] >= 0.0f ? maxY : minY;
            float z = plane[2] >= 0.0f ? maxZ : minZ;
            if(plane[0] * x + plane[1] * y + plane[2] * z + plane[3] < 0.0f)
                return false;
        }
        return true;
    }

    private static void verifyCommands(int[] result, boolean[] expected, int written,
                                       int capacity, boolean fullCoverage) {
        require(result.length == OUTPUT_HEADER_WORDS + capacity * COMMAND_WORDS,
                "GPU selection result must match declared capacity");
        boolean[] seen = new boolean[RegionBatchLayout.MAX_SECTIONS];
        for(int slot = 0; slot < written; ++slot) {
            int base = OUTPUT_HEADER_WORDS + slot * COMMAND_WORDS;
            int section = result[base + 4];
            require(section >= 0 && section < expected.length && expected[section],
                    "GPU selection emitted a CPU-ineligible candidate");
            require(!seen[section], "GPU selection emitted a candidate more than once");
            seen[section] = true;
            require(result[base] == 6 + section * 3 && result[base + 1] == 1
                            && result[base + 2] == section * 7
                            && result[base + 3] == -section * 13,
                    "GPU selection must preserve all five indirect-command words");
        }
        if(fullCoverage) {
            for(int section = 0; section < expected.length; ++section)
                require(seen[section] == expected[section],
                        "GPU selection must exactly match the independent CPU set");
        }
        for(int word = OUTPUT_HEADER_WORDS + written * COMMAND_WORDS;
            word < result.length; ++word)
            require(result[word] == 0, "Unused GPU command capacity must remain zero");
    }

    private static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }

    private static final class Probe implements AutoCloseable {
        private static final int WORKGROUP_SIZE = 64;
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

        int[] dispatch(GpuRegionCandidateTable table, long expectedGeneration, int targetLayer,
                       float regionX, float regionY, float regionZ, int capacity) {
            if(closed) throw new IllegalStateException("Section-selection probe is closed");
            if(table == null || table.candidateCount() <= 0
                    || targetLayer < 0 || targetLayer > 15 || capacity < 0
                    || capacity > RegionBatchLayout.MAX_SECTIONS)
                throw new IllegalArgumentException("Invalid section-selection input");

            int outputWords = OUTPUT_HEADER_WORDS + capacity * COMMAND_WORDS;
            int outputBytes = outputWords * Integer.BYTES;
            int parameterBytes = PARAMETER_WORDS * Integer.BYTES;
            StorageBuffer input = new StorageBuffer(table.byteSize(), MemoryTypes.GPU_MEM);
            StorageBuffer output = new StorageBuffer(outputBytes, MemoryTypes.GPU_MEM);
            StorageBuffer parameters = new StorageBuffer(parameterBytes, MemoryTypes.GPU_MEM);
            StagingBuffer inputStaging = new StagingBuffer(table.byteSize());
            StagingBuffer parameterStaging = new StagingBuffer(parameterBytes);
            long readbackBuffer = VK_NULL_HANDLE;
            long readbackAllocation = VK_NULL_HANDLE;
            ByteBuffer tableBytes = MemoryUtil.memAlloc(table.byteSize()).order(ByteOrder.nativeOrder());
            ByteBuffer parameterData = MemoryUtil.memAlloc(parameterBytes).order(ByteOrder.nativeOrder());
            try(MemoryStack stack = MemoryStack.stackPush()) {
                table.writeTo(tableBytes);
                tableBytes.flip();
                inputStaging.copyBuffer(table.byteSize(), tableBytes);
                writeParameters(parameterData, table, expectedGeneration, targetLayer, capacity,
                        regionX, regionY, regionZ);
                parameterData.flip();
                parameterStaging.copyBuffer(parameterBytes, parameterData);

                LongBuffer pReadbackBuffer = stack.mallocLong(1);
                var pReadbackAllocation = stack.mallocPointer(1);
                MemoryManager memoryManager = MemoryManager.getInstance();
                memoryManager.createBuffer(outputBytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pReadbackBuffer, pReadbackAllocation);
                readbackBuffer = pReadbackBuffer.get(0);
                readbackAllocation = pReadbackAllocation.get(0);

                updateDescriptorSet(input, output, parameters);
                CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
                TransferQueue.uploadBufferCmd(commandBuffer, inputStaging.getId(), inputStaging.getOffset(),
                        input.getId(), 0L, table.byteSize());
                TransferQueue.uploadBufferCmd(commandBuffer, parameterStaging.getId(), parameterStaging.getOffset(),
                        parameters.getId(), 0L, parameterBytes);
                vkCmdFillBuffer(commandBuffer.getHandle(), output.getId(), 0L, outputBytes, 0);
                barrierTransferToCompute(commandBuffer, input, output, parameters);
                vkCmdBindPipeline(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
                vkCmdBindDescriptorSets(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                        pipelineLayout, 0, stack.longs(descriptorSet), null);
                vkCmdDispatch(commandBuffer.getHandle(),
                        (table.candidateCount() + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE, 1, 1);
                barrierComputeToTransfer(commandBuffer, output);
                TransferQueue.uploadBufferCmd(commandBuffer, output.getId(), 0L,
                        readbackBuffer, 0L, outputBytes);
                Device.getGraphicsQueue().submitCommands(commandBuffer);
                Synchronization.waitFence(commandBuffer.getFence());
                Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

                int[] result = new int[outputWords];
                long allocation = readbackAllocation;
                memoryManager.MapAndCopy(allocation, outputBytes, pointer -> {
                    ByteBuffer actual = pointer.getByteBuffer(0, outputBytes)
                            .order(ByteOrder.nativeOrder());
                    for(int word = 0; word < outputWords; ++word)
                        result[word] = actual.getInt(word * Integer.BYTES);
                });
                return result;
            } finally {
                MemoryUtil.memFree(tableBytes);
                MemoryUtil.memFree(parameterData);
                inputStaging.freeBuffer();
                parameterStaging.freeBuffer();
                input.freeBuffer();
                output.freeBuffer();
                parameters.freeBuffer();
                if(readbackBuffer != VK_NULL_HANDLE)
                    MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
            }
        }

        private static void writeParameters(ByteBuffer target, GpuRegionCandidateTable table,
                                            long generation, int layer, int capacity,
                                            float regionX, float regionY, float regionZ) {
            target.putInt((int)generation).putInt((int)(generation >>> 32))
                    .putInt(layer).putInt(capacity)
                    .putInt(table.regionX()).putInt(table.regionY()).putInt(table.regionZ()).putInt(0)
                    .putFloat(regionX).putFloat(regionY).putFloat(regionZ).putInt(0);
            for(float[] plane : PLANES)
                for(float value : plane) target.putFloat(value);
        }

        private void createDescriptorResources() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings =
                        VkDescriptorSetLayoutBinding.calloc(3, stack);
                for(int binding = 0; binding < 3; ++binding)
                    bindings.get(binding).binding(binding)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                            .pImmutableSamplers(null);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(Device.device,
                        VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                                .pBindings(bindings), null, pLayout),
                        "create section-selection descriptor layout");
                descriptorSetLayout = pLayout.get(0);
                VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
                poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(3);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(Device.device,
                        VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                                .pPoolSizes(poolSize).maxSets(1), null, pPool),
                        "create section-selection descriptor pool");
                descriptorPool = pPool.get(0);
                LongBuffer pSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(Device.device,
                        VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                                .descriptorPool(descriptorPool)
                                .pSetLayouts(stack.longs(descriptorSetLayout)), pSet),
                        "allocate section-selection descriptor set");
                descriptorSet = pSet.get(0);
            }
        }

        private void createPipelineLayout() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreatePipelineLayout(Device.device,
                        VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                                .pSetLayouts(stack.longs(descriptorSetLayout)), null, pLayout),
                        "create section-selection pipeline layout");
                pipelineLayout = pLayout.get(0);
            }
        }

        private void createPipeline() {
            SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShaderResource(
                    "/assets/vulkanmod/shaders/terrain/section_select_probe.comp",
                    SPIRVUtils.ShaderKind.COMPUTE_SHADER);
            long module = VK_NULL_HANDLE;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pModule = stack.mallocLong(1);
                check(vkCreateShaderModule(Device.device,
                        VkShaderModuleCreateInfo.calloc(stack).sType$Default()
                                .pCode(spirv.bytecode()), null, pModule),
                        "create section-selection shader module");
                module = pModule.get(0);
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(module).pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(1, stack);
                info.get(0).sType$Default().stage(stage).layout(pipelineLayout)
                        .basePipelineHandle(VK_NULL_HANDLE).basePipelineIndex(-1);
                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE, info, null, pPipeline),
                        "create section-selection pipeline");
                pipeline = pPipeline.get(0);
            } finally {
                if(module != VK_NULL_HANDLE) vkDestroyShaderModule(Device.device, module, null);
                spirv.free();
            }
        }

        private void updateDescriptorSet(StorageBuffer input, StorageBuffer output,
                                         StorageBuffer parameters) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorBufferInfo.Buffer inputInfo = VkDescriptorBufferInfo.calloc(1, stack);
                inputInfo.get(0).buffer(input.getId()).offset(0L).range(input.getBufferSize());
                VkDescriptorBufferInfo.Buffer outputInfo = VkDescriptorBufferInfo.calloc(1, stack);
                outputInfo.get(0).buffer(output.getId()).offset(0L).range(output.getBufferSize());
                VkDescriptorBufferInfo.Buffer parameterInfo = VkDescriptorBufferInfo.calloc(1, stack);
                parameterInfo.get(0).buffer(parameters.getId()).offset(0L).range(parameters.getBufferSize());
                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
                writes.get(0).sType$Default().dstSet(descriptorSet).dstBinding(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                        .pBufferInfo(inputInfo);
                writes.get(1).sType$Default().dstSet(descriptorSet).dstBinding(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                        .pBufferInfo(outputInfo);
                writes.get(2).sType$Default().dstSet(descriptorSet).dstBinding(2)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                        .pBufferInfo(parameterInfo);
                vkUpdateDescriptorSets(Device.device, writes, null);
            }
        }

        private static void barrierTransferToCompute(CommandPool.CommandBuffer commandBuffer,
                                                      StorageBuffer input, StorageBuffer output,
                                                      StorageBuffer parameters) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(3, stack);
                readBarrier(barriers.get(0), input);
                barriers.get(1).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(output.getId()).offset(0L).size(output.getBufferSize());
                readBarrier(barriers.get(2), parameters);
                vkCmdPipelineBarrier(commandBuffer.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, null, barriers, null);
            }
        }

        private static void readBarrier(VkBufferMemoryBarrier barrier, StorageBuffer buffer) {
            barrier.sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(buffer.getId()).offset(0L).size(buffer.getBufferSize());
        }

        private static void barrierComputeToTransfer(CommandPool.CommandBuffer commandBuffer,
                                                      StorageBuffer output) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(output.getId()).offset(0L).size(output.getBufferSize());
                vkCmdPipelineBarrier(commandBuffer.getHandle(), VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, barrier, null);
            }
        }

        private static boolean graphicsQueueSupportsCompute() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer count = stack.ints(0);
                vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, null);
                VkQueueFamilyProperties.Buffer properties =
                        VkQueueFamilyProperties.malloc(count.get(0), stack);
                vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, properties);
                return (properties.get(Queue.getQueueFamilies().graphicsFamily).queueFlags()
                        & VK_QUEUE_COMPUTE_BIT) != 0;
            }
        }

        private static void check(int result, String action) {
            if(result != VK_SUCCESS)
                throw new RuntimeException("Failed to " + action + ": " + result);
        }

        @Override
        public void close() {
            if(closed) return;
            closed = true;
            if(pipeline != VK_NULL_HANDLE) vkDestroyPipeline(Device.device, pipeline, null);
            if(pipelineLayout != VK_NULL_HANDLE)
                vkDestroyPipelineLayout(Device.device, pipelineLayout, null);
            if(descriptorPool != VK_NULL_HANDLE)
                vkDestroyDescriptorPool(Device.device, descriptorPool, null);
            if(descriptorSetLayout != VK_NULL_HANDLE)
                vkDestroyDescriptorSetLayout(Device.device, descriptorSetLayout, null);
        }
    }
}
