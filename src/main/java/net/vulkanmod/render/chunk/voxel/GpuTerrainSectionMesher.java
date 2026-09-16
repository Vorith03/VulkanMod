package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.render.chunk.GpuTerrainOutputStore;
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
 * Reusable Vulkan dispatcher for the bounded section terrain compute kernel.
 *
 * <p>The caller owns generation validation and output reservation/publication. This
 * class owns only the Vulkan pipeline contract and one synchronous dispatch. The
 * dispatch is intentionally synchronous for the first production bridge so success,
 * overflow and shader errors are known before publication; it must therefore run on
 * the render thread, never a chunk worker. CPU terrain remains authoritative.</p>
 */
public final class GpuTerrainSectionMesher implements AutoCloseable {
    static final int RESULT_HEADER_WORDS = 6;
    static final int WORDS_PER_VERTEX = 5;
    static final int WORDS_PER_FACE = GpuTerrainOutputStore.BYTES_PER_FACE / Integer.BYTES;
    private static final int PUSH_CONSTANT_BYTES = 7 * Integer.BYTES;
    private static final int WORKGROUP_COUNT = SectionVoxelSnapshot.BLOCK_COUNT / 64;

    private long descriptorSetLayout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipelineLayout;
    private long pipeline;
    private boolean closed;

    public GpuTerrainSectionMesher() {
        if(!graphicsQueueSupportsCompute())
            throw new UnsupportedOperationException(
                    "Graphics queue family does not support compute dispatch");
        createDescriptorResources();
        createPipelineLayout();
        createPipeline();
    }

    /** Production path: read back only the six-word completion header. */
    public DispatchResult dispatch(
            StorageBuffer voxelPage, RegionVoxelGpuStore.Residency voxel,
            StorageBuffer lightingPage, RegionVoxelGpuStore.Residency lighting,
            GpuTerrainModelGpuStore.Residency model, int templateCount,
            GpuTerrainOutputStore.Target target, int faceCapacity) {
        return dispatchInternal(voxelPage, voxel, lightingPage, lighting, model,
                templateCount, target, faceCapacity, false).dispatch();
    }

    /**
     * Smoke-only oracle path. Captures descriptors and generated vertices without
     * changing the production dispatcher contract or requiring a second pipeline.
     */
    ValidationResult dispatchForValidation(
            StorageBuffer voxelPage, RegionVoxelGpuStore.Residency voxel,
            StorageBuffer lightingPage, RegionVoxelGpuStore.Residency lighting,
            GpuTerrainModelGpuStore.Residency model, int templateCount,
            GpuTerrainOutputStore.Target target, int faceCapacity) {
        return dispatchInternal(voxelPage, voxel, lightingPage, lighting, model,
                templateCount, target, faceCapacity, true);
    }

    private ValidationResult dispatchInternal(
            StorageBuffer voxelPage, RegionVoxelGpuStore.Residency voxel,
            StorageBuffer lightingPage, RegionVoxelGpuStore.Residency lighting,
            GpuTerrainModelGpuStore.Residency model, int templateCount,
            GpuTerrainOutputStore.Target target, int faceCapacity,
            boolean capturePayload) {
        validateDispatch(voxelPage, voxel, lightingPage, lighting, model,
                templateCount, target, faceCapacity);

        int resultWords = Math.addExact(RESULT_HEADER_WORDS, faceCapacity);
        int resultBytes = Math.multiplyExact(resultWords, Integer.BYTES);
        int vertexWords = Math.multiplyExact(faceCapacity, WORDS_PER_FACE);
        int vertexBytes = Math.multiplyExact(vertexWords, Integer.BYTES);
        int readbackBytes = capturePayload
                ? Math.addExact(resultBytes, vertexBytes)
                : RESULT_HEADER_WORDS * Integer.BYTES;

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
            int resultReadbackBytes = capturePayload
                    ? resultBytes : RESULT_HEADER_WORDS * Integer.BYTES;
            TransferQueue.uploadBufferCmd(commandBuffer, result.getId(), 0L,
                    readbackBuffer, 0L, resultReadbackBytes);
            if(capturePayload) {
                TransferQueue.uploadBufferCmd(commandBuffer, target.bufferId(),
                        target.byteOffset(), readbackBuffer, resultBytes, vertexBytes);
            }

            // Reservation.withTarget holds the AreaBuffer monitor until submission.
            // Waiting here is deliberately render-thread-only and gives publication
            // an unambiguous completion/error boundary for the first bridge.
            Device.getGraphicsQueue().submitCommands(commandBuffer);
            Synchronization.waitFence(commandBuffer.getFence());
            Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(commandBuffer);

            int[] header = new int[RESULT_HEADER_WORDS];
            int[] descriptors = capturePayload ? new int[faceCapacity] : new int[0];
            int[] vertices = capturePayload ? new int[vertexWords] : new int[0];
            long allocation = readbackAllocation;
            memoryManager.MapAndCopy(allocation, readbackBytes, pointer -> {
                ByteBuffer bytes = pointer.getByteBuffer(0, readbackBytes)
                        .order(ByteOrder.nativeOrder());
                for(int i = 0; i < header.length; ++i)
                    header[i] = bytes.getInt(i * Integer.BYTES);
                if(capturePayload) {
                    for(int i = 0; i < descriptors.length; ++i)
                        descriptors[i] = bytes.getInt((RESULT_HEADER_WORDS + i)
                                * Integer.BYTES);
                    for(int i = 0; i < vertices.length; ++i)
                        vertices[i] = bytes.getInt(resultBytes + i * Integer.BYTES);
                }
            });

            DispatchResult dispatch = new DispatchResult(
                    header[0], header[1], header[2] != 0, header[3]);
            return new ValidationResult(dispatch, descriptors, vertices);
        } finally {
            result.freeBuffer();
            if(readbackBuffer != VK_NULL_HANDLE)
                MemoryManager.freeBuffer(readbackBuffer, readbackAllocation);
        }
    }

    private void validateDispatch(
            StorageBuffer voxelPage, RegionVoxelGpuStore.Residency voxel,
            StorageBuffer lightingPage, RegionVoxelGpuStore.Residency lighting,
            GpuTerrainModelGpuStore.Residency model, int templateCount,
            GpuTerrainOutputStore.Target target, int faceCapacity) {
        if(closed)
            throw new IllegalStateException("Section mesher is closed");
        if(voxelPage == null || lightingPage == null || target == null
                || target.bufferId() == 0L || model == null || !model.valid()
                || model.buffer() == null)
            throw new IllegalArgumentException("Section mesher requires live GPU inputs/target");
        if(voxel == null || lighting == null || !voxel.valid() || !lighting.valid()
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

    public record DispatchResult(int requestedFaces, int writtenFaces,
                                 boolean overflow, int errorFlags) {
        public boolean successful() {
            return !overflow && errorFlags == 0 && writtenFaces > 0
                    && writtenFaces <= requestedFaces;
        }
    }

    record ValidationResult(DispatchResult dispatch, int[] descriptors, int[] vertices) {}
}
