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
 * Diagnostic compute decoder for one generation-matched voxel + sparse-lighting pair.
 *
 * <p>The probe does not generate or publish terrain. It proves that a future terrain
 * compute consumer can bind the real region-owned input pages, validate both ABIs,
 * map every demanded 20-cube lattice point through the sparse rank table, and recover
 * the exact packed-light, shade-brightness and light-passing records.</p>
 */
final class SparseLightingComputeProbe implements AutoCloseable {
    static final int RESULT_MAGIC = 0x4c495431; // LIT1
    static final int RESULT_HEADER_WORDS = 12;
    static final int RESULT_RECORD_WORDS = 4;
    static final int FACE_RESULT_BASE = RESULT_HEADER_WORDS
            + GpuLightingDemandMap.SAMPLE_COUNT * RESULT_RECORD_WORDS;
    static final int RESULT_WORDS = FACE_RESULT_BASE + 6 * 8;

    private static final int PUSH_CONSTANT_BYTES = 5 * Integer.BYTES;
    private static final int WORKGROUP_SIZE = 64;
    private static final int WORKGROUP_COUNT = (GpuLightingDemandMap.SAMPLE_COUNT
            + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;

    private long descriptorSetLayout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipelineLayout;
    private long pipeline;
    private boolean closed;

    SparseLightingComputeProbe() {
        if(!graphicsQueueSupportsCompute())
            throw new UnsupportedOperationException("Graphics queue family does not support compute dispatch");
        this.createDescriptorResources();
        this.createPipelineLayout();
        this.createPipeline();
    }

    int[] dispatch(StorageBuffer voxelPage, RegionVoxelGpuStore.Residency voxelResidency,
                   StorageBuffer lightingPage, RegionVoxelGpuStore.Residency lightingResidency,
                   int blockIndex) {
        if(blockIndex < 0 || blockIndex >= SectionVoxelSnapshot.BLOCK_COUNT)
            throw new IllegalArgumentException("Probe block index must be inside the section");
        if(this.closed)
            throw new IllegalStateException("Sparse-lighting compute probe is closed");
        validateSlice(voxelPage, voxelResidency, SectionVoxelSnapshot.HEADER_WORDS * Integer.BYTES,
                "voxel");
        validateSlice(lightingPage, lightingResidency,
                GpuSparseLightingSnapshot.HEADER_WORDS * Integer.BYTES, "sparse lighting");
        if(voxelResidency.generation() != lightingResidency.generation())
            throw new IllegalArgumentException("Voxel and sparse-lighting generations must match");

        int resultBytes = Math.multiplyExact(RESULT_WORDS, Integer.BYTES);
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

            this.updateDescriptorSet(voxelPage, lightingPage, output, resultBytes);

            CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
            vkCmdFillBuffer(commandBuffer.getHandle(), output.getId(), 0L, resultBytes, 0);
            barrierTransferWritesToCompute(commandBuffer, voxelPage, lightingPage,
                    output, resultBytes);

            vkCmdBindPipeline(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                    this.pipeline);
            vkCmdBindDescriptorSets(commandBuffer.getHandle(), VK_PIPELINE_BIND_POINT_COMPUTE,
                    this.pipelineLayout, 0, stack.longs(this.descriptorSet), null);
            ByteBuffer push = stack.malloc(PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder());
            push.putInt(0, voxelResidency.byteOffset());
            push.putInt(Integer.BYTES, voxelResidency.byteLength());
            push.putInt(2 * Integer.BYTES, lightingResidency.byteOffset());
            push.putInt(3 * Integer.BYTES, lightingResidency.byteLength());
            push.putInt(4 * Integer.BYTES, blockIndex);
            vkCmdPushConstants(commandBuffer.getHandle(), this.pipelineLayout,
                    VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            vkCmdDispatch(commandBuffer.getHandle(), WORKGROUP_COUNT, 1, 1);

            barrierComputeWritesToTransfer(commandBuffer, output, resultBytes);
            TransferQueue.uploadBufferCmd(commandBuffer, output.getId(), 0L,
                    readbackBuffer, 0L, resultBytes);

            Device.getGraphicsQueue().submitCommands(commandBuffer);
            Synchronization.waitFence(commandBuffer.getFence());
            Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

            int[] result = new int[RESULT_WORDS];
            long allocation = readbackAllocation;
            memoryManager.MapAndCopy(allocation, resultBytes, pointer -> {
                ByteBuffer actual = pointer.getByteBuffer(0, resultBytes).order(ByteOrder.nativeOrder());
                for(int i = 0; i < result.length; ++i)
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
                    "create sparse-lighting compute descriptor set layout");
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
                    "create sparse-lighting compute descriptor pool");
            this.descriptorPool = pPool.get(0);

            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(this.descriptorPool)
                    .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer pSet = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(Device.device, allocateInfo, pSet),
                    "allocate sparse-lighting compute descriptor set");
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
                    "create sparse-lighting compute pipeline layout");
            this.pipelineLayout = pLayout.get(0);
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
                    "create sparse-lighting compute shader module");
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
            check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE,
                    pipelineInfo, null, pPipeline),
                    "create sparse-lighting compute pipeline");
            this.pipeline = pPipeline.get(0);
        } finally {
            if(shaderModule != VK_NULL_HANDLE)
                vkDestroyShaderModule(Device.device, shaderModule, null);
            spirv.free();
        }
    }

    private void updateDescriptorSet(StorageBuffer voxelPage, StorageBuffer lightingPage,
                                     StorageBuffer output, int outputBytes) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer voxelInfo = VkDescriptorBufferInfo.calloc(1, stack);
            voxelInfo.get(0)
                    .buffer(voxelPage.getId())
                    .offset(0L)
                    .range(voxelPage.getBufferSize());
            VkDescriptorBufferInfo.Buffer lightingInfo = VkDescriptorBufferInfo.calloc(1, stack);
            lightingInfo.get(0)
                    .buffer(lightingPage.getId())
                    .offset(0L)
                    .range(lightingPage.getBufferSize());
            VkDescriptorBufferInfo.Buffer outputInfo = VkDescriptorBufferInfo.calloc(1, stack);
            outputInfo.get(0)
                    .buffer(output.getId())
                    .offset(0L)
                    .range(outputBytes);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(voxelInfo);
            writes.get(1)
                    .sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(1)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(lightingInfo);
            writes.get(2)
                    .sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(2)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(outputInfo);
            vkUpdateDescriptorSets(Device.device, writes, null);
        }
    }

    private static void barrierTransferWritesToCompute(CommandPool.CommandBuffer commandBuffer,
                                                        StorageBuffer voxelPage,
                                                        StorageBuffer lightingPage,
                                                        StorageBuffer output,
                                                        int outputBytes) {
        boolean sharedPage = voxelPage.getId() == lightingPage.getId();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(sharedPage ? 2 : 3, stack);
            barriers.get(0)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(voxelPage.getId())
                    .offset(0L)
                    .size(voxelPage.getBufferSize());
            int outputIndex = 1;
            if(!sharedPage) {
                barriers.get(1)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(lightingPage.getId())
                        .offset(0L)
                        .size(lightingPage.getBufferSize());
                outputIndex = 2;
            }
            barriers.get(outputIndex)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(output.getId())
                    .offset(0L)
                    .size(outputBytes);
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

    private static void validateSlice(StorageBuffer page, RegionVoxelGpuStore.Residency residency,
                                      int minimumBytes, String name) {
        if(page == null || residency == null || !residency.valid()
                || residency.byteOffset() < 0 || (residency.byteOffset() & 3) != 0
                || residency.byteLength() < minimumBytes || (residency.byteLength() & 3) != 0
                || (long)residency.byteOffset() + residency.byteLength() > page.getBufferSize())
            throw new IllegalArgumentException("Invalid resident " + name + " slice");
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
