package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
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
 * Minimal compute consumer for the section voxel ABI.
 *
 * <p>This intentionally binds the whole region page at descriptor offset zero and
 * passes the section slice offset through push constants. That keeps descriptors
 * portable regardless of {@code minStorageBufferOffsetAlignment} and is also the
 * shape needed for batching many resident sections from the same page later.</p>
 *
 * <p>The current kernel is an oracle, not the final terrain mesher: it decodes all
 * 4096 voxels on the GPU, emits deterministic aggregate values, writes fixed-slot
 * and compacted candidate-face descriptors, and expands compact faces into four
 * section-local unit-cube corner coordinates. When a current baked-model table is
 * supplied, every compact descriptor also resolves its exact sprite/UV face row
 * and packs the position/UV fields of four 20-byte terrain vertices. Unsupported
 * color/light fields remain zero. Compact output capacity is explicit: the kernel
 * reports requested and written counts plus overflow so a production caller can
 * fall back instead of overrunning its allocation. The synchronization and
 * descriptor path are the same pieces future mesh generation can reuse.</p>
 */
final class VoxelComputeProbe implements AutoCloseable {
    // Header: state sum, flag sum, checksum, requested faces, written faces, overflow.
    static final int HEADER_WORDS = 6;
    static final int FACES_PER_VOXEL = 6;
    static final int VERTICES_PER_FACE = 4;
    static final int FACE_DESCRIPTOR_WORDS = SectionVoxelSnapshot.BLOCK_COUNT * FACES_PER_VOXEL;
    static final int COMPACT_DESCRIPTOR_BASE = HEADER_WORDS + FACE_DESCRIPTOR_WORDS;
    static final int MODEL_FACE_RESULT_WORDS = 1 + GpuTerrainModelTable.FACE_WORDS;
    static final int PARTIAL_VERTEX_WORDS_PER_VERTEX = 5;
    static final int FACE_VERTEX_BASE = faceVertexBase(FACE_DESCRIPTOR_WORDS);
    static final int MODEL_FACE_BASE = modelFaceBase(FACE_DESCRIPTOR_WORDS);
    static final int PARTIAL_VERTEX_BASE = partialVertexBase(FACE_DESCRIPTOR_WORDS);
    static final int RESULT_WORDS = resultWords(FACE_DESCRIPTOR_WORDS);
    private static final int PUSH_CONSTANT_BYTES = 4 * Integer.BYTES;
    private static final int WORKGROUP_SIZE = 64;
    private static final int WORKGROUP_COUNT = SectionVoxelSnapshot.BLOCK_COUNT / WORKGROUP_SIZE;

    private long descriptorSetLayout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipelineLayout;
    private long pipeline;
    private boolean closed;

    VoxelComputeProbe() {
        if(!graphicsQueueSupportsCompute())
            throw new UnsupportedOperationException("Graphics queue family does not support compute dispatch");

        this.createDescriptorResources();
        this.createPipelineLayout();
        this.createPipeline();
    }

    int[] dispatch(StorageBuffer inputPage, int sliceByteOffset, int sliceByteLength) {
        return this.dispatch(inputPage, sliceByteOffset, sliceByteLength, null, 0,
                FACE_DESCRIPTOR_WORDS);
    }

    int[] dispatch(StorageBuffer inputPage, int sliceByteOffset, int sliceByteLength,
                   int compactCapacity) {
        return this.dispatch(inputPage, sliceByteOffset, sliceByteLength, null, 0,
                compactCapacity);
    }

    int[] dispatch(StorageBuffer inputPage, int sliceByteOffset, int sliceByteLength,
                   GpuTerrainModelGpuStore.Residency modelResidency, int templateCount) {
        return this.dispatch(inputPage, sliceByteOffset, sliceByteLength,
                modelResidency, templateCount, FACE_DESCRIPTOR_WORDS);
    }

    private int[] dispatch(StorageBuffer inputPage, int sliceByteOffset, int sliceByteLength,
                           GpuTerrainModelGpuStore.Residency modelResidency,
                           int templateCount, int compactCapacity) {
        if(this.closed)
            throw new IllegalStateException("Voxel compute probe is closed");
        if(inputPage == null || sliceByteOffset < 0 || sliceByteLength <= 0
                || (sliceByteOffset & 3) != 0
                || (long)sliceByteOffset + sliceByteLength > inputPage.getBufferSize())
            throw new IllegalArgumentException("Invalid resident voxel slice");
        boolean hasModelTable = modelResidency != null;
        if(hasModelTable != (templateCount > 0)
                || hasModelTable && (!modelResidency.valid() || modelResidency.buffer() == null
                || modelResidency.byteLength() <= 0))
            throw new IllegalArgumentException("Valid model-table residency and template count must agree");
        if(compactCapacity < 0 || compactCapacity > FACE_DESCRIPTOR_WORDS)
            throw new IllegalArgumentException("Invalid compact face-output capacity");

        StorageBuffer modelTable = hasModelTable ? modelResidency.buffer() : inputPage;
        int modelTableBytes = hasModelTable
                ? modelResidency.byteLength() : Math.toIntExact(inputPage.getBufferSize());

        int resultWords = resultWords(compactCapacity);
        int resultBytes = Math.multiplyExact(resultWords, Integer.BYTES);
        StorageBuffer output = new StorageBuffer(resultBytes, MemoryTypes.GPU_MEM);
        long readbackBuffer = VK_NULL_HANDLE;
        long readbackAllocation = VK_NULL_HANDLE;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pReadbackBuffer = stack.mallocLong(1);
            var pReadbackAllocation = stack.mallocPointer(1);
            MemoryManager memoryManager = MemoryManager.getInstance();
            memoryManager.createBuffer(resultBytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pReadbackBuffer, pReadbackAllocation);
            readbackBuffer = pReadbackBuffer.get(0);
            readbackAllocation = pReadbackAllocation.get(0);

            this.updateDescriptorSet(inputPage, output, resultBytes, modelTable, modelTableBytes);

            CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
            vkCmdFillBuffer(commandBuffer.getHandle(), output.getId(), 0L, resultBytes, 0);
            barrierTransferWritesToCompute(commandBuffer, inputPage, output,
                    resultBytes, hasModelTable ? modelTable : null, modelTableBytes);

            vkCmdBindPipeline(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
            vkCmdBindDescriptorSets(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                    this.pipelineLayout, 0, stack.longs(this.descriptorSet), null);
            ByteBuffer push = stack.malloc(PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder());
            push.putInt(0, sliceByteOffset);
            push.putInt(Integer.BYTES, sliceByteLength);
            push.putInt(2 * Integer.BYTES, templateCount);
            push.putInt(3 * Integer.BYTES, compactCapacity);
            vkCmdPushConstants(commandBuffer.getHandle(), this.pipelineLayout,
                    VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            vkCmdDispatch(commandBuffer.getHandle(), WORKGROUP_COUNT, 1, 1);

            barrierComputeWritesToTransfer(commandBuffer, output, resultBytes);
            TransferQueue.uploadBufferCmd(commandBuffer, output.getId(), 0L,
                    readbackBuffer, 0L, resultBytes);

            Device.getGraphicsQueue().submitCommands(commandBuffer);
            Synchronization.waitFence(commandBuffer.getFence());
            Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

            int[] result = new int[resultWords];
            long allocation = readbackAllocation;
            memoryManager.MapAndCopy(allocation, resultBytes, pointer -> {
                ByteBuffer actual = pointer.getByteBuffer(0, resultBytes).order(ByteOrder.nativeOrder());
                for(int i = 0; i < resultWords; ++i)
                    result[i] = actual.getInt(i * Integer.BYTES);
            });
            return result;
        } finally {
            output.freeBuffer();
            if(readbackBuffer != VK_NULL_HANDLE)
                MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
        }
    }

    private void createDescriptorResources() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
            for(int i = 0; i < 3; ++i) {
                bindings.get(i)
                        .binding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .pImmutableSamplers(null);
            }

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(bindings);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(Device.device, layoutInfo, null, pLayout),
                    "create voxel compute descriptor set layout");
            this.descriptorSetLayout = pLayout.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(3);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .pPoolSizes(poolSizes)
                    .maxSets(1);
            LongBuffer pPool = stack.mallocLong(1);
            check(vkCreateDescriptorPool(Device.device, poolInfo, null, pPool),
                    "create voxel compute descriptor pool");
            this.descriptorPool = pPool.get(0);

            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(this.descriptorPool)
                    .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer pSet = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(Device.device, allocateInfo, pSet),
                    "allocate voxel compute descriptor set");
            this.descriptorSet = pSet.get(0);
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
                    .pSetLayouts(stack.longs(this.descriptorSetLayout))
                    .pPushConstantRanges(pushRange);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreatePipelineLayout(Device.device, layoutInfo, null, pLayout),
                    "create voxel compute pipeline layout");
            this.pipelineLayout = pLayout.get(0);
        }
    }

    private void createPipeline() {
        SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShaderResource(
                "/assets/vulkanmod/shaders/terrain/voxel_probe.comp",
                SPIRVUtils.ShaderKind.COMPUTE_SHADER);
        long shaderModule = VK_NULL_HANDLE;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(spirv.bytecode());
            LongBuffer pModule = stack.mallocLong(1);
            check(vkCreateShaderModule(Device.device, moduleInfo, null, pModule),
                    "create voxel compute shader module");
            shaderModule = pModule.get(0);

            VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8("main"));

            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType$Default()
                    .stage(stageInfo)
                    .layout(this.pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline),
                    "create voxel compute pipeline");
            this.pipeline = pPipeline.get(0);
        } finally {
            if(shaderModule != VK_NULL_HANDLE)
                vkDestroyShaderModule(Device.device, shaderModule, null);
            spirv.free();
        }
    }

    private void updateDescriptorSet(StorageBuffer inputPage, StorageBuffer output,
                                     int outputBytes, StorageBuffer modelTable,
                                     int modelTableBytes) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer inputInfo = VkDescriptorBufferInfo.calloc(1, stack);
            inputInfo.get(0)
                    .buffer(inputPage.getId())
                    .offset(0L)
                    .range(inputPage.getBufferSize());
            VkDescriptorBufferInfo.Buffer outputInfo = VkDescriptorBufferInfo.calloc(1, stack);
            outputInfo.get(0)
                    .buffer(output.getId())
                    .offset(0L)
                    .range(outputBytes);
            VkDescriptorBufferInfo.Buffer modelInfo = VkDescriptorBufferInfo.calloc(1, stack);
            modelInfo.get(0)
                    .buffer(modelTable.getId())
                    .offset(0L)
                    .range(modelTableBytes);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(inputInfo);
            writes.get(1)
                    .sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(1)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(outputInfo);
            writes.get(2)
                    .sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(2)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(modelInfo);
            vkUpdateDescriptorSets(Device.device, writes, null);
        }
    }

    private static void barrierTransferWritesToCompute(CommandPool.CommandBuffer commandBuffer,
                                                        StorageBuffer inputPage,
                                                        StorageBuffer output,
                                                        int outputBytes,
                                                        StorageBuffer modelTable,
                                                        int modelTableBytes) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(
                    modelTable == null ? 2 : 3, stack);
            barriers.get(0)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(inputPage.getId())
                    .offset(0L)
                    .size(inputPage.getBufferSize());
            barriers.get(1)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(output.getId())
                    .offset(0L)
                    .size(outputBytes);
            if(modelTable != null) {
                barriers.get(2)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(modelTable.getId())
                        .offset(0L)
                        .size(modelTableBytes);
            }
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, null, barriers, null);
        }
    }

    private static void barrierComputeWritesToTransfer(CommandPool.CommandBuffer commandBuffer,
                                                        StorageBuffer output,
                                                        int outputBytes) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(output.getId())
                    .offset(0L)
                    .size(outputBytes);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, barrier, null);
        }
    }

    private static boolean graphicsQueueSupportsCompute() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, null);
            VkQueueFamilyProperties.Buffer properties = VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, properties);
            int graphicsFamily = Queue.getQueueFamilies().graphicsFamily;
            return (properties.get(graphicsFamily).queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0;
        }
    }

    static int faceVertexBase(int compactCapacity) {
        return Math.addExact(COMPACT_DESCRIPTOR_BASE, compactCapacity);
    }

    static int modelFaceBase(int compactCapacity) {
        return Math.addExact(faceVertexBase(compactCapacity),
                Math.multiplyExact(compactCapacity, VERTICES_PER_FACE));
    }

    static int partialVertexBase(int compactCapacity) {
        return Math.addExact(modelFaceBase(compactCapacity),
                Math.multiplyExact(compactCapacity, MODEL_FACE_RESULT_WORDS));
    }

    static int resultWords(int compactCapacity) {
        if(compactCapacity < 0 || compactCapacity > FACE_DESCRIPTOR_WORDS)
            throw new IllegalArgumentException("Invalid compact face-output capacity");
        return Math.addExact(partialVertexBase(compactCapacity),
                Math.multiplyExact(compactCapacity,
                        VERTICES_PER_FACE * PARTIAL_VERTEX_WORDS_PER_VERTEX));
    }

    private static void check(int result, String action) {
        if(result != VK_SUCCESS)
            throw new RuntimeException("Failed to " + action + ": " + result);
    }

    @Override
    public void close() {
        if(this.closed)
            return;
        this.closed = true;
        if(this.pipeline != VK_NULL_HANDLE)
            vkDestroyPipeline(Device.device, this.pipeline, null);
        if(this.pipelineLayout != VK_NULL_HANDLE)
            vkDestroyPipelineLayout(Device.device, this.pipelineLayout, null);
        if(this.descriptorPool != VK_NULL_HANDLE)
            vkDestroyDescriptorPool(Device.device, this.descriptorPool, null);
        if(this.descriptorSetLayout != VK_NULL_HANDLE)
            vkDestroyDescriptorSetLayout(Device.device, this.descriptorSetLayout, null);
    }
}
