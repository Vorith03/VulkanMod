package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.RegionBatchLayout;
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
 * Isolated Vulkan oracle for bounded GPU section selection.
 *
 * <p>The input is Vulkan's existing five-word indexed indirect command ABI. A zero
 * index or instance count marks a candidate which must not draw. The compute pass
 * compacts live candidates into a caller-sized output and reports requested,
 * written and overflow counts before this mechanism is allowed to own production
 * commands. CPU graph traversal and the current region draw path remain
 * authoritative.</p>
 */
public final class GpuSectionSelectionSmokeTest {
    private static final int COMMAND_WORDS = RegionBatchLayout.STRIDE / Integer.BYTES;
    private static final int HEADER_WORDS = 3;
    private static final int REQUESTED = 0;
    private static final int WRITTEN = 1;
    private static final int OVERFLOW = 2;

    private GpuSectionSelectionSmokeTest() {}

    public static void verify() {
        int[] candidates = new int[RegionBatchLayout.MAX_SECTIONS * COMMAND_WORDS];
        boolean[] expected = new boolean[RegionBatchLayout.MAX_SECTIONS];
        int expectedCount = 0;
        for(int section = 0; section < RegionBatchLayout.MAX_SECTIONS; ++section) {
            int base = section * COMMAND_WORDS;
            boolean hasIndices = section % 3 != 0;
            boolean selectedByCpu = section % 11 != 0;
            candidates[base] = hasIndices ? 6 + section * 3 : 0;
            candidates[base + 1] = selectedByCpu ? 1 : 0;
            candidates[base + 2] = section * 7;
            candidates[base + 3] = -section * 13;
            candidates[base + 4] = section;
            expected[section] = hasIndices && selectedByCpu;
            if(expected[section]) expectedCount++;
        }

        try(Probe probe = new Probe()) {
            int[] full = probe.dispatch(candidates, RegionBatchLayout.MAX_SECTIONS,
                    RegionBatchLayout.MAX_SECTIONS);
            require(full[REQUESTED] == expectedCount && full[WRITTEN] == expectedCount
                            && full[OVERFLOW] == 0,
                    "Unbounded GPU selection must emit every live candidate exactly once");
            verifyCommands(full, expected, expectedCount,
                    RegionBatchLayout.MAX_SECTIONS, true);

            int capacity = 17;
            int[] bounded = probe.dispatch(candidates, RegionBatchLayout.MAX_SECTIONS, capacity);
            require(bounded[REQUESTED] == expectedCount && bounded[WRITTEN] == capacity
                            && bounded[OVERFLOW] == 1,
                    "Bounded GPU selection must expose overflow without exceeding capacity");
            verifyCommands(bounded, expected, capacity, capacity, false);

            int[] disabled = probe.dispatch(candidates, RegionBatchLayout.MAX_SECTIONS, 0);
            require(disabled.length == HEADER_WORDS && disabled[REQUESTED] == expectedCount
                            && disabled[WRITTEN] == 0 && disabled[OVERFLOW] == 1,
                    "Zero-capacity selection must remain a safe fallback signal");
        }

        Initializer.LOGGER.info(
                "VULKANMOD_GPU_SECTION_SELECTION_OK: {} live of {} candidates; bounded capacity/overflow and exact indirect metadata verified",
                expectedCount, RegionBatchLayout.MAX_SECTIONS);
    }

    private static void verifyCommands(int[] result, boolean[] expected, int written,
                                       int capacity, boolean requireFullCoverage) {
        require(result.length == HEADER_WORDS + capacity * COMMAND_WORDS,
                "GPU selection result must match declared capacity");
        boolean[] seen = new boolean[RegionBatchLayout.MAX_SECTIONS];
        for(int slot = 0; slot < written; ++slot) {
            int base = HEADER_WORDS + slot * COMMAND_WORDS;
            int section = result[base + 4];
            require(section >= 0 && section < expected.length && expected[section],
                    "GPU selection emitted an ineligible candidate");
            require(!seen[section], "GPU selection emitted a candidate more than once");
            seen[section] = true;
            require(result[base] == 6 + section * 3 && result[base + 1] == 1
                            && result[base + 2] == section * 7
                            && result[base + 3] == -section * 13,
                    "GPU selection must preserve all five indirect-command words");
        }
        if(requireFullCoverage) {
            for(int section = 0; section < expected.length; ++section)
                require(seen[section] == expected[section],
                        "Full-capacity GPU selection must exactly match the CPU oracle");
        }
        for(int word = HEADER_WORDS + written * COMMAND_WORDS;
            word < result.length; ++word)
            require(result[word] == 0, "Unwritten GPU selection capacity must remain zero");
    }

    private static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }

    private static final class Probe implements AutoCloseable {
        private static final int PUSH_CONSTANT_BYTES = 2 * Integer.BYTES;
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

        int[] dispatch(int[] commands, int candidateCount, int outputCapacity) {
            if(closed) throw new IllegalStateException("Section-selection probe is closed");
            if(candidateCount <= 0 || candidateCount > RegionBatchLayout.MAX_SECTIONS
                    || outputCapacity < 0 || outputCapacity > RegionBatchLayout.MAX_SECTIONS
                    || commands.length < candidateCount * COMMAND_WORDS)
                throw new IllegalArgumentException("Invalid section-selection bounds");

            int inputBytes = Math.multiplyExact(candidateCount,
                    RegionBatchLayout.STRIDE);
            int outputWords = Math.addExact(HEADER_WORDS,
                    Math.multiplyExact(outputCapacity, COMMAND_WORDS));
            int outputBytes = Math.multiplyExact(outputWords, Integer.BYTES);
            StorageBuffer input = new StorageBuffer(inputBytes, MemoryTypes.GPU_MEM);
            StorageBuffer output = new StorageBuffer(outputBytes, MemoryTypes.GPU_MEM);
            StagingBuffer staging = new StagingBuffer(inputBytes);
            long readbackBuffer = VK_NULL_HANDLE;
            long readbackAllocation = VK_NULL_HANDLE;
            ByteBuffer bytes = MemoryUtil.memAlloc(inputBytes).order(ByteOrder.nativeOrder());
            try(MemoryStack stack = MemoryStack.stackPush()) {
                for(int word = 0; word < candidateCount * COMMAND_WORDS; ++word)
                    bytes.putInt(commands[word]);
                bytes.flip();
                staging.copyBuffer(inputBytes, bytes);

                LongBuffer pReadbackBuffer = stack.mallocLong(1);
                var pReadbackAllocation = stack.mallocPointer(1);
                MemoryManager memoryManager = MemoryManager.getInstance();
                memoryManager.createBuffer(outputBytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pReadbackBuffer, pReadbackAllocation);
                readbackBuffer = pReadbackBuffer.get(0);
                readbackAllocation = pReadbackAllocation.get(0);

                updateDescriptorSet(input, output);
                CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
                TransferQueue.uploadBufferCmd(commandBuffer, staging.getId(), staging.getOffset(),
                        input.getId(), 0L, inputBytes);
                vkCmdFillBuffer(commandBuffer.getHandle(), output.getId(), 0L, outputBytes, 0);
                barrierTransferToCompute(commandBuffer, input, inputBytes, output, outputBytes);

                vkCmdBindPipeline(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
                vkCmdBindDescriptorSets(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                        pipelineLayout, 0, stack.longs(descriptorSet), null);
                ByteBuffer push = stack.malloc(PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder());
                push.putInt(0, candidateCount);
                push.putInt(Integer.BYTES, outputCapacity);
                vkCmdPushConstants(commandBuffer.getHandle(), pipelineLayout,
                        VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
                int workgroups = (candidateCount + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
                if(workgroups > 0) vkCmdDispatch(commandBuffer.getHandle(), workgroups, 1, 1);

                barrierComputeToTransfer(commandBuffer, output, outputBytes);
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
                MemoryUtil.memFree(bytes);
                staging.freeBuffer();
                input.freeBuffer();
                output.freeBuffer();
                if(readbackBuffer != VK_NULL_HANDLE)
                    MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
            }
        }

        private void createDescriptorResources() {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings =
                        VkDescriptorSetLayoutBinding.calloc(2, stack);
                for(int binding = 0; binding < 2; ++binding) {
                    bindings.get(binding).binding(binding)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                            .pImmutableSamplers(null);
                }
                VkDescriptorSetLayoutCreateInfo layoutInfo =
                        VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                                .pBindings(bindings);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(Device.device, layoutInfo, null, pLayout),
                        "create section-selection descriptor layout");
                descriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
                poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(2);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default().pPoolSizes(poolSize).maxSets(1);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(Device.device, poolInfo, null, pPool),
                        "create section-selection descriptor pool");
                descriptorPool = pPool.get(0);

                VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default().descriptorPool(descriptorPool)
                        .pSetLayouts(stack.longs(descriptorSetLayout));
                LongBuffer pSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(Device.device, allocateInfo, pSet),
                        "allocate section-selection descriptor set");
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
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default().pCode(spirv.bytecode());
                LongBuffer pModule = stack.mallocLong(1);
                check(vkCreateShaderModule(Device.device, moduleInfo, null, pModule),
                        "create section-selection shader module");
                module = pModule.get(0);
                VkPipelineShaderStageCreateInfo stageInfo =
                        VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                                .stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module)
                                .pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer pipelineInfo =
                        VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0).sType$Default().stage(stageInfo).layout(pipelineLayout)
                        .basePipelineHandle(VK_NULL_HANDLE).basePipelineIndex(-1);
                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE,
                        pipelineInfo, null, pPipeline), "create section-selection pipeline");
                pipeline = pPipeline.get(0);
            } finally {
                if(module != VK_NULL_HANDLE) vkDestroyShaderModule(Device.device, module, null);
                spirv.free();
            }
        }

        private void updateDescriptorSet(StorageBuffer input, StorageBuffer output) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorBufferInfo.Buffer inputInfo = VkDescriptorBufferInfo.calloc(1, stack);
                inputInfo.get(0).buffer(input.getId()).offset(0L).range(input.getBufferSize());
                VkDescriptorBufferInfo.Buffer outputInfo = VkDescriptorBufferInfo.calloc(1, stack);
                outputInfo.get(0).buffer(output.getId()).offset(0L).range(output.getBufferSize());
                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
                writes.get(0).sType$Default().dstSet(descriptorSet).dstBinding(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1).pBufferInfo(inputInfo);
                writes.get(1).sType$Default().dstSet(descriptorSet).dstBinding(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1).pBufferInfo(outputInfo);
                vkUpdateDescriptorSets(Device.device, writes, null);
            }
        }

        private static void barrierTransferToCompute(CommandPool.CommandBuffer commandBuffer,
                                                      StorageBuffer input, int inputBytes,
                                                      StorageBuffer output, int outputBytes) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(2, stack);
                barriers.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(input.getId()).offset(0L).size(inputBytes);
                barriers.get(1).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(output.getId()).offset(0L).size(outputBytes);
                vkCmdPipelineBarrier(commandBuffer.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, null, barriers, null);
            }
        }

        private static void barrierComputeToTransfer(CommandPool.CommandBuffer commandBuffer,
                                                      StorageBuffer output, int outputBytes) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(output.getId()).offset(0L).size(outputBytes);
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
