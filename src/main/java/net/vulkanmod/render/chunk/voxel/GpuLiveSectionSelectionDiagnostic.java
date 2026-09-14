package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.RegionBatchLayout;
import net.vulkanmod.render.chunk.VFrustum;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
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
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Opt-in, rate-limited comparison of live resident candidate tables against the
 * authoritative CPU draw set. It never supplies commands to production rendering.
 */
public final class GpuLiveSectionSelectionDiagnostic {
    private static final boolean ENABLED = Boolean.getBoolean(
            "vulkanmod.debugGpuSectionSelection");
    private static final int MAX_SAMPLES = Math.max(1,
            Integer.getInteger("vulkanmod.debugGpuSectionSelectionSamples", 8));
    private static final long CLAIM_INTERVAL_NANOS = 500_000_000L;
    private static final long CLAIM_TIMEOUT_NANOS = 5_000_000_000L;

    private static final int COMMAND_WORDS = RegionBatchLayout.STRIDE / Integer.BYTES;
    private static final int OUTPUT_HEADER_WORDS = 4;
    private static final int REQUESTED = 0;
    private static final int WRITTEN = 1;
    private static final int OVERFLOW = 2;
    private static final int TABLE_VALID = 3;
    private static final int PARAMETER_WORDS = 36;

    private static long nextToken = 1L;
    private static long activeToken;
    private static long claimDeadlineNanos;
    private static long nextClaimNanos;
    private static int completedSamples;

    private GpuLiveSectionSelectionDiagnostic() {}

    /** Reserve the single global diagnostic slot so live regions cannot all stall. */
    public static synchronized long claim() {
        if(!ENABLED || completedSamples >= MAX_SAMPLES)
            return 0L;
        long now = System.nanoTime();
        if(activeToken != 0L && now >= claimDeadlineNanos)
            activeToken = 0L;
        if(activeToken != 0L || now < nextClaimNanos)
            return 0L;
        long token = nextToken++;
        if(token == 0L)
            token = nextToken++;
        activeToken = token;
        claimDeadlineNanos = now + CLAIM_TIMEOUT_NANOS;
        return token;
    }

    public static synchronized void cancel(long token) {
        if(token != 0L && activeToken == token) {
            activeToken = 0L;
            claimDeadlineNanos = 0L;
        }
    }

    public static synchronized boolean isCurrent(long token) {
        return token != 0L && activeToken == token;
    }

    private static synchronized int complete(long token) {
        if(token == 0L || activeToken != token)
            return completedSamples;
        activeToken = 0L;
        claimDeadlineNanos = 0L;
        completedSamples++;
        nextClaimNanos = System.nanoTime() + CLAIM_INTERVAL_NANOS;
        return completedSamples;
    }

    /**
     * Consume a claimed live table once its generation has become resident. Returns
     * true once the claim is consumed, even when the diagnostic reports a mismatch.
     */
    public static boolean compare(long token,
                                  GpuRegionCandidateGpuStore.Residency residency,
                                  GpuRegionCandidateTable table,
                                  int targetLayer,
                                  VFrustum frustum,
                                  boolean[] cpuExpected) {
        if(!isCurrent(token))
            return true;
        if(residency == null || !residency.valid() || table == null || frustum == null
                || cpuExpected == null
                || cpuExpected.length != RegionBatchLayout.MAX_SECTIONS
                || residency.generation() != table.generation())
            return false;

        try {
            Comparison reconstructed = buildExpected(table, targetLayer, frustum);
            String predicateMismatch = compareExpectedSets(
                    reconstructed.expectedPacked, cpuExpected);
            int cpuCount = count(cpuExpected);

            if(table.candidateCount() == 0) {
                int sample = complete(token);
                if(predicateMismatch == null && cpuCount == 0) {
                    Initializer.LOGGER.info(
                            "VULKANMOD_GPU_LIVE_SECTION_SELECTION_OK: sample={} region=({}, {}, {}) layer={} generation={} selected=0 candidates=0",
                            sample, table.regionX(), table.regionY(), table.regionZ(),
                            targetLayer, table.generation());
                } else {
                    Initializer.LOGGER.error(
                            "VULKANMOD_GPU_LIVE_SECTION_SELECTION_MISMATCH: sample={} region=({}, {}, {}) layer={} generation={} expected={} candidates=0 reason={}",
                            sample, table.regionX(), table.regionY(), table.regionZ(),
                            targetLayer, table.generation(), cpuCount,
                            predicateMismatch == null ? "CPU queue is nonempty" : predicateMismatch);
                }
                return true;
            }

            float[] planes = new float[VFrustum.PLANE_COUNT * VFrustum.PLANE_WORDS];
            frustum.copyPlaneEquations(planes);
            int[] result;
            try(Probe probe = new Probe()) {
                result = probe.dispatch(residency, table, targetLayer,
                        frustum.relativeX(table.regionX()),
                        frustum.relativeY(table.regionY()),
                        frustum.relativeZ(table.regionZ()), planes);
            }

            String gpuMismatch = validateResult(result, table, reconstructed.rowByPacked,
                    cpuExpected, cpuCount);
            String mismatch = predicateMismatch != null ? predicateMismatch : gpuMismatch;
            int sample = complete(token);
            if(mismatch == null) {
                Initializer.LOGGER.info(
                        "VULKANMOD_GPU_LIVE_SECTION_SELECTION_OK: sample={} region=({}, {}, {}) layer={} generation={} selected={} candidates={}",
                        sample, table.regionX(), table.regionY(), table.regionZ(),
                        targetLayer, table.generation(), cpuCount, table.candidateCount());
            } else {
                Initializer.LOGGER.error(
                        "VULKANMOD_GPU_LIVE_SECTION_SELECTION_MISMATCH: sample={} region=({}, {}, {}) layer={} generation={} expected={} reconstructed={} candidates={} reason={}",
                        sample, table.regionX(), table.regionY(), table.regionZ(),
                        targetLayer, table.generation(), cpuCount, reconstructed.count,
                        table.candidateCount(), mismatch);
            }
            return true;
        } catch(RuntimeException error) {
            int sample = complete(token);
            Initializer.LOGGER.error(
                    "VULKANMOD_GPU_LIVE_SECTION_SELECTION_ERROR: sample={} region=({}, {}, {}) layer={} generation={}",
                    sample, table.regionX(), table.regionY(), table.regionZ(),
                    targetLayer, table.generation(), error);
            return true;
        }
    }

    private static Comparison buildExpected(GpuRegionCandidateTable table, int layer,
                                            VFrustum frustum) {
        boolean[] expectedPacked = new boolean[RegionBatchLayout.MAX_SECTIONS];
        int[] rowByPacked = new int[RegionBatchLayout.MAX_SECTIONS];
        Arrays.fill(rowByPacked, -1);
        int count = 0;
        for(int row = 0; row < table.candidateCount(); ++row) {
            int packed = table.recordWord(row, 4);
            if(packed < 0 || packed >= RegionBatchLayout.MAX_SECTIONS)
                throw new IllegalStateException("Live candidate contains invalid packed section");
            if(rowByPacked[packed] != -1)
                throw new IllegalStateException("Live candidate table contains duplicate section");
            rowByPacked[packed] = row;

            int flags = table.recordWord(row, 5);
            boolean ready = (flags & GpuRegionCandidateTable.READY) != 0;
            boolean graphVisible = (flags & GpuRegionCandidateTable.GRAPH_VISIBLE) != 0;
            int candidateLayer = (flags & GpuRegionCandidateTable.LAYER_MASK)
                    >>> GpuRegionCandidateTable.LAYER_SHIFT;
            if(!ready || !graphVisible || candidateLayer != layer
                    || table.recordWord(row, 0) == 0 || table.recordWord(row, 1) == 0)
                continue;

            int minX = table.regionX() + ((packed & 7) << 4);
            int minY = table.regionY() + (((packed >>> 3) & 7) << 4);
            int minZ = table.regionZ() + (((packed >>> 6) & 7) << 4);
            if(frustum.cubeInFrustum(minX, minY, minZ,
                    minX + 16, minY + 16, minZ + 16) >= 0)
                continue;
            expectedPacked[packed] = true;
            count++;
        }
        return new Comparison(expectedPacked, rowByPacked, count);
    }

    private static String compareExpectedSets(boolean[] reconstructed, boolean[] cpuExpected) {
        for(int packed = 0; packed < RegionBatchLayout.MAX_SECTIONS; ++packed) {
            if(reconstructed[packed] != cpuExpected[packed])
                return "CPU graph/frustum reconstruction differs at section " + packed
                        + " reconstructed=" + reconstructed[packed]
                        + " cpuQueue=" + cpuExpected[packed];
        }
        return null;
    }

    private static int count(boolean[] values) {
        int count = 0;
        for(boolean value : values)
            if(value) count++;
        return count;
    }

    private static String validateResult(int[] result, GpuRegionCandidateTable table,
                                         int[] rowByPacked, boolean[] cpuExpected,
                                         int cpuCount) {
        if(result.length != OUTPUT_HEADER_WORDS
                + RegionBatchLayout.MAX_SECTIONS * COMMAND_WORDS)
            return "unexpected output size";
        if(result[TABLE_VALID] != 1)
            return "resident table rejected by generation/origin validation";
        if(result[OVERFLOW] != 0)
            return "full-capacity output overflowed";
        if(result[REQUESTED] != cpuCount || result[WRITTEN] != cpuCount)
            return "count mismatch requested=" + result[REQUESTED]
                    + " written=" + result[WRITTEN];

        boolean[] seen = new boolean[RegionBatchLayout.MAX_SECTIONS];
        for(int slot = 0; slot < result[WRITTEN]; ++slot) {
            int base = OUTPUT_HEADER_WORDS + slot * COMMAND_WORDS;
            int packed = result[base + 4];
            if(packed < 0 || packed >= seen.length || !cpuExpected[packed])
                return "GPU emitted CPU-ineligible section " + packed;
            if(seen[packed])
                return "GPU emitted duplicate section " + packed;
            seen[packed] = true;
            int row = rowByPacked[packed];
            if(row < 0)
                return "CPU queue section is absent from candidate superset " + packed;
            for(int word = 0; word < COMMAND_WORDS; ++word) {
                if(result[base + word] != table.recordWord(row, word))
                    return "command metadata mismatch for section " + packed
                            + " word " + word;
            }
        }
        for(int packed = 0; packed < cpuExpected.length; ++packed) {
            if(seen[packed] != cpuExpected[packed])
                return "selection set mismatch for section " + packed;
        }
        return null;
    }

    private record Comparison(boolean[] expectedPacked, int[] rowByPacked, int count) {}

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

        int[] dispatch(GpuRegionCandidateGpuStore.Residency residency,
                       GpuRegionCandidateTable table, int targetLayer,
                       float regionX, float regionY, float regionZ, float[] planes) {
            int capacity = RegionBatchLayout.MAX_SECTIONS;
            int outputWords = OUTPUT_HEADER_WORDS + capacity * COMMAND_WORDS;
            int outputBytes = outputWords * Integer.BYTES;
            int parameterBytes = PARAMETER_WORDS * Integer.BYTES;
            StorageBuffer output = new StorageBuffer(outputBytes, MemoryTypes.GPU_MEM);
            StorageBuffer parameters = new StorageBuffer(parameterBytes, MemoryTypes.GPU_MEM);
            StagingBuffer parameterStaging = new StagingBuffer(parameterBytes);
            long readbackBuffer = VK_NULL_HANDLE;
            long readbackAllocation = VK_NULL_HANDLE;
            ByteBuffer parameterData = MemoryUtil.memAlloc(parameterBytes)
                    .order(ByteOrder.nativeOrder());
            try(MemoryStack stack = MemoryStack.stackPush()) {
                writeParameters(parameterData, table, targetLayer, capacity,
                        regionX, regionY, regionZ, planes);
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

                updateDescriptorSet(residency.buffer(), residency.byteLength(), output, parameters);
                CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
                TransferQueue.uploadBufferCmd(commandBuffer, parameterStaging.getId(),
                        parameterStaging.getOffset(), parameters.getId(), 0L, parameterBytes);
                vkCmdFillBuffer(commandBuffer.getHandle(), output.getId(), 0L, outputBytes, 0);
                barrierTransferToCompute(commandBuffer, residency.buffer(),
                        residency.byteLength(), output, parameters);
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
                MemoryUtil.memFree(parameterData);
                parameterStaging.freeBuffer();
                output.freeBuffer();
                parameters.freeBuffer();
                if(readbackBuffer != VK_NULL_HANDLE)
                    MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
            }
        }

        private static void writeParameters(ByteBuffer target, GpuRegionCandidateTable table,
                                            int layer, int capacity, float regionX,
                                            float regionY, float regionZ, float[] planes) {
            if(planes == null || planes.length < VFrustum.PLANE_COUNT * VFrustum.PLANE_WORDS)
                throw new IllegalArgumentException("Live frustum must contain six planes");
            long generation = table.generation();
            target.putInt((int)generation).putInt((int)(generation >>> 32))
                    .putInt(layer).putInt(capacity)
                    .putInt(table.regionX()).putInt(table.regionY()).putInt(table.regionZ()).putInt(0)
                    .putFloat(regionX).putFloat(regionY).putFloat(regionZ).putInt(0);
            int planeWords = VFrustum.PLANE_COUNT * VFrustum.PLANE_WORDS;
            for(int index = 0; index < planeWords; ++index)
                target.putFloat(planes[index]);
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
                        "create live section-selection descriptor layout");
                descriptorSetLayout = pLayout.get(0);
                VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
                poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(3);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(Device.device,
                        VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                                .pPoolSizes(poolSize).maxSets(1), null, pPool),
                        "create live section-selection descriptor pool");
                descriptorPool = pPool.get(0);
                LongBuffer pSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(Device.device,
                        VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                                .descriptorPool(descriptorPool)
                                .pSetLayouts(stack.longs(descriptorSetLayout)), pSet),
                        "allocate live section-selection descriptor set");
                descriptorSet = pSet.get(0);
            }
        }

        private void createPipelineLayout() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreatePipelineLayout(Device.device,
                        VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                                .pSetLayouts(stack.longs(descriptorSetLayout)), null, pLayout),
                        "create live section-selection pipeline layout");
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
                        "create live section-selection shader module");
                module = pModule.get(0);
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(module).pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(1, stack);
                info.get(0).sType$Default().stage(stage).layout(pipelineLayout)
                        .basePipelineHandle(VK_NULL_HANDLE).basePipelineIndex(-1);
                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE, info, null, pPipeline),
                        "create live section-selection pipeline");
                pipeline = pPipeline.get(0);
            } finally {
                if(module != VK_NULL_HANDLE)
                    vkDestroyShaderModule(Device.device, module, null);
                spirv.free();
            }
        }

        private void updateDescriptorSet(StorageBuffer input, int inputBytes,
                                         StorageBuffer output, StorageBuffer parameters) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorBufferInfo.Buffer inputInfo = VkDescriptorBufferInfo.calloc(1, stack);
                inputInfo.get(0).buffer(input.getId()).offset(0L).range(inputBytes);
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
                                                      StorageBuffer input, int inputBytes,
                                                      StorageBuffer output,
                                                      StorageBuffer parameters) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(3, stack);
                barriers.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(input.getId()).offset(0L).size(inputBytes);
                barriers.get(1).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(output.getId()).offset(0L).size(output.getBufferSize());
                barriers.get(2).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(parameters.getId()).offset(0L).size(parameters.getBufferSize());
                vkCmdPipelineBarrier(commandBuffer.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, null, barriers, null);
            }
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
