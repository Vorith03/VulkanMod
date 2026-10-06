package net.vulkanmod.render.texture;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.memory.TextureResidentBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.GraphicsQueue;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
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
 * Shared O4 compute pipeline for exact animated-texture interpolation.
 *
 * <p>Each admitted animation owns an immutable resident source buffer plus a
 * bounded scratch buffer and descriptor set. The dispatch is recorded directly
 * into the existing texture-tick graphics command buffer:
 * resident source -> compute scratch -> atlas transfer. No GPU readback or host
 * pixel upload is introduced.</p>
 *
 * <p>The pinned Forge implementation uses Java double arithmetic before integer
 * truncation. This path therefore requires shaderFloat64; unsupported devices
 * retain the CPU implementation unchanged.</p>
 */
public final class GpuTextureInterpolationCompute implements AutoCloseable {
    private static final boolean REQUESTED =
            Boolean.getBoolean("vulkanmod.gpuAnimatedTextureInterpolation");
    private static final int MAX_BINDINGS = 2048;
    private static final int WORKGROUP_SIZE = 64;
    private static final int PUSH_WORDS = 12;
    private static final int PUSH_CONSTANT_BYTES = PUSH_WORDS * Integer.BYTES;

    private static GpuTextureInterpolationCompute instance;
    private static boolean unavailable;
    private static Boolean graphicsComputeSupported;

    private long descriptorSetLayout;
    private long descriptorPool;
    private long pipelineLayout;
    private long pipeline;
    private int liveBindings;
    private boolean closed;

    private GpuTextureInterpolationCompute() {
        if(!Device.isShaderFloat64Enabled())
            throw new UnsupportedOperationException("shaderFloat64 is not enabled");
        if(!graphicsQueueSupportsCompute())
            throw new UnsupportedOperationException("graphics queue does not support compute");

        try {
            this.createDescriptorResources();
            this.createPipelineLayout();
            this.createPipeline();
        } catch(RuntimeException | Error failure) {
            this.close();
            throw failure;
        }
    }

    public static boolean requested() {
        return REQUESTED;
    }

    public static boolean enabled() {
        return REQUESTED
                && !unavailable
                && Device.isShaderFloat64Enabled()
                && graphicsQueueSupportsCompute();
    }

    public static synchronized Binding createBinding(TextureResidentBuffer source, int scratchBytes) {
        if(!enabled() || source == null || scratchBytes <= 0) {
            return null;
        }

        try {
            if(instance == null) {
                instance = new GpuTextureInterpolationCompute();
            }
            return instance.allocateBinding(source, scratchBytes);
        } catch(RuntimeException | OutOfMemoryError failure) {
            unavailable = true;
            Initializer.LOGGER.warn(
                    "Disabling GPU animated-texture interpolation after compute initialization/allocation failure",
                    failure);
            return null;
        }
    }

    public static synchronized void shutdownAfterDeviceIdle() {
        if(instance != null) {
            instance.close();
            instance = null;
        }
        graphicsComputeSupported = null;
        unavailable = false;
    }

    private synchronized Binding allocateBinding(TextureResidentBuffer source, int scratchBytes) {
        if(this.closed || this.liveBindings >= MAX_BINDINGS) {
            return null;
        }

        long descriptorSet = VK_NULL_HANDLE;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(this.descriptorPool)
                    .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer pSet = stack.mallocLong(1);
            int result = vkAllocateDescriptorSets(Device.device, allocateInfo, pSet);
            if(result != VK_SUCCESS) {
                return null;
            }
            descriptorSet = pSet.get(0);
        }

        TextureResidentBuffer scratch = null;
        boolean success = false;
        try {
            scratch = new TextureResidentBuffer(scratchBytes);
            this.updateDescriptorSet(descriptorSet, source, scratch);
            this.liveBindings++;
            success = true;
            return new Binding(this, descriptorSet, source, scratch, scratchBytes);
        } finally {
            if(!success) {
                if(scratch != null) {
                    scratch.retire(null);
                }
                if(descriptorSet != VK_NULL_HANDLE) {
                    this.freeDescriptorSet(descriptorSet);
                }
            }
        }
    }

    private void updateDescriptorSet(long descriptorSet, TextureResidentBuffer source,
                                     TextureResidentBuffer scratch) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer sourceInfo = VkDescriptorBufferInfo.calloc(1, stack);
            sourceInfo.get(0)
                    .buffer(source.getId())
                    .offset(0L)
                    .range(source.getBufferSize());
            VkDescriptorBufferInfo.Buffer outputInfo = VkDescriptorBufferInfo.calloc(1, stack);
            outputInfo.get(0)
                    .buffer(scratch.getId())
                    .offset(0L)
                    .range(scratch.getBufferSize());

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(sourceInfo);
            writes.get(1)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(1)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(outputInfo);
            vkUpdateDescriptorSets(Device.device, writes, null);
        }
    }

    private synchronized void freeDescriptorSet(long descriptorSet) {
        if(descriptorSet == VK_NULL_HANDLE || this.closed || this.descriptorPool == VK_NULL_HANDLE) {
            return;
        }

        try(MemoryStack stack = MemoryStack.stackPush()) {
            int result = vkFreeDescriptorSets(Device.device, this.descriptorPool,
                    stack.longs(descriptorSet));
            if(result != VK_SUCCESS) {
                Initializer.LOGGER.warn(
                        "Failed to free animated-texture interpolation descriptor set: {}", result);
            }
        }

        if(this.liveBindings > 0) {
            this.liveBindings--;
        }
    }

    private DispatchResult dispatch(Binding binding, VulkanImage atlas,
                                    int[] sourceMipOffsets, int[] sourceWidths,
                                    int[] sourceHeights, int[] outputOffsets,
                                    int frameWidth, int frameHeight, int columns,
                                    int currentIndex, int nextIndex,
                                    int subFrame, int duration, int destX, int destY) {
        if(this.closed || binding == null || binding.retired || atlas == null
                || sourceMipOffsets == null || sourceWidths == null
                || sourceHeights == null || outputOffsets == null
                || frameWidth <= 0 || frameHeight <= 0 || columns <= 0
                || currentIndex < 0 || nextIndex < 0
                || subFrame <= 0 || duration <= 0 || subFrame >= duration
                || atlas.formatSize != 4) {
            return null;
        }

        GraphicsQueue graphicsQueue = Device.getGraphicsQueue();
        if(!graphicsQueue.hasActiveUploadBatch()) {
            return null;
        }

        int levels = Math.min(atlas.mipLevels,
                Math.min(sourceMipOffsets.length,
                        Math.min(sourceWidths.length,
                                Math.min(sourceHeights.length, outputOffsets.length))));
        if(levels <= 0) {
            return null;
        }

        int regionCount = 0;
        long totalPixels = 0L;
        long totalBytes = 0L;
        for(int mip = 0; mip < levels; ++mip) {
            int width = frameWidth >> mip;
            int height = frameHeight >> mip;
            if(width <= 0 || height <= 0) {
                continue;
            }

            int sheetWidth = sourceWidths[mip];
            int sheetHeight = sourceHeights[mip];
            long currentBaseX = ((long)(currentIndex % columns) * frameWidth) >> mip;
            long currentBaseY = ((long)(currentIndex / columns) * frameHeight) >> mip;
            long nextBaseX = ((long)(nextIndex % columns) * frameWidth) >> mip;
            long nextBaseY = ((long)(nextIndex / columns) * frameHeight) >> mip;
            long dstMipX = ((long)destX) >> mip;
            long dstMipY = ((long)destY) >> mip;
            long atlasWidth = Math.max(1, atlas.width >> mip);
            long atlasHeight = Math.max(1, atlas.height >> mip);
            long outputBytes = (long)width * height * 4L;
            long outputEnd = (long)outputOffsets[mip] + outputBytes;
            long sourceBase = sourceMipOffsets[mip];

            if(sheetWidth <= 0 || sheetHeight <= 0 || sourceBase < 0L
                    || (sourceBase & 3L) != 0L
                    || currentBaseX < 0L || currentBaseY < 0L
                    || nextBaseX < 0L || nextBaseY < 0L
                    || currentBaseX > (long)sheetWidth - width
                    || currentBaseY > (long)sheetHeight - height
                    || nextBaseX > (long)sheetWidth - width
                    || nextBaseY > (long)sheetHeight - height
                    || dstMipX < 0L || dstMipY < 0L
                    || dstMipX > atlasWidth - width || dstMipY > atlasHeight - height
                    || outputOffsets[mip] < 0 || (outputOffsets[mip] & 3) != 0
                    || outputEnd > binding.scratchBytes) {
                return null;
            }

            long currentLastPixelExclusive =
                    (currentBaseY + height - 1L) * sheetWidth + currentBaseX + width;
            long nextLastPixelExclusive =
                    (nextBaseY + height - 1L) * sheetWidth + nextBaseX + width;
            long maxSourceEnd = sourceBase
                    + Math.max(currentLastPixelExclusive, nextLastPixelExclusive) * 4L;
            if(maxSourceEnd > binding.source.getBufferSize()) {
                return null;
            }

            long pixels = (long)width * height;
            if(pixels > Integer.MAX_VALUE) {
                return null;
            }
            regionCount++;
            totalPixels += pixels;
            totalBytes += outputBytes;
        }

        if(regionCount <= 0 || totalPixels <= 0L) {
            return null;
        }

        VTextureSelector.flushPendingSpriteUploadCopies();
        CommandPool.CommandBuffer commandBuffer = graphicsQueue.getCommandBuffer();

        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer beforeCompute = VkBufferMemoryBarrier.calloc(2, stack);
            beforeCompute.get(0)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(binding.source.getId())
                    .offset(0L)
                    .size(binding.source.getBufferSize());
            beforeCompute.get(1)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(binding.scratch.getId())
                    .offset(0L)
                    .size(binding.scratchBytes);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, null, beforeCompute, null);

            vkCmdBindPipeline(commandBuffer.getHandle(),
                    VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
            vkCmdBindDescriptorSets(commandBuffer.getHandle(),
                    VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout, 0,
                    stack.longs(binding.descriptorSet), null);

            ByteBuffer push = stack.malloc(PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder());
            for(int mip = 0; mip < levels; ++mip) {
                int width = frameWidth >> mip;
                int height = frameHeight >> mip;
                if(width <= 0 || height <= 0) {
                    continue;
                }

                int currentX = (int)(((long)(currentIndex % columns) * frameWidth) >> mip);
                int currentY = (int)(((long)(currentIndex / columns) * frameHeight) >> mip);
                int nextX = (int)(((long)(nextIndex % columns) * frameWidth) >> mip);
                int nextY = (int)(((long)(nextIndex / columns) * frameHeight) >> mip);
                int pixelCount = Math.multiplyExact(width, height);

                push.putInt(0, sourceMipOffsets[mip] >>> 2);
                push.putInt(1 * Integer.BYTES, sourceWidths[mip]);
                push.putInt(2 * Integer.BYTES, currentX);
                push.putInt(3 * Integer.BYTES, currentY);
                push.putInt(4 * Integer.BYTES, nextX);
                push.putInt(5 * Integer.BYTES, nextY);
                push.putInt(6 * Integer.BYTES, width);
                push.putInt(7 * Integer.BYTES, height);
                push.putInt(8 * Integer.BYTES, outputOffsets[mip] >>> 2);
                push.putInt(9 * Integer.BYTES, subFrame);
                push.putInt(10 * Integer.BYTES, duration);
                push.putInt(11 * Integer.BYTES, 0);

                vkCmdPushConstants(commandBuffer.getHandle(), this.pipelineLayout,
                        VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
                vkCmdDispatch(commandBuffer.getHandle(),
                        (pixelCount + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE, 1, 1);
            }

            VkBufferMemoryBarrier.Buffer afterCompute = VkBufferMemoryBarrier.calloc(1, stack);
            afterCompute.get(0)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(binding.scratch.getId())
                    .offset(0L)
                    .size(binding.scratchBytes);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, afterCompute, null);

            atlas.transferDstLayout(commandBuffer);

            // The texture tick may have written another sprite (or the CPU
            // reference half of the native oracle) in this same atlas immediately
            // before this interpolation. Remaining in TRANSFER_DST does not itself
            // create a memory dependency. Serialize prior transfer writes before
            // this compute result writes any atlas subresource again.
            VkImageMemoryBarrier.Buffer atlasWriteBarrier =
                    VkImageMemoryBarrier.calloc(1, stack);
            atlasWriteBarrier.get(0)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(atlas.getId());
            atlasWriteBarrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(atlas.mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, atlasWriteBarrier);

            VkBufferImageCopy.Buffer regions = VkBufferImageCopy.calloc(regionCount, stack);
            int region = 0;
            for(int mip = 0; mip < levels; ++mip) {
                int width = frameWidth >> mip;
                int height = frameHeight >> mip;
                if(width <= 0 || height <= 0) {
                    continue;
                }

                VkBufferImageCopy copy = regions.get(region++);
                copy.bufferOffset(outputOffsets[mip]);
                copy.bufferRowLength(0);
                copy.bufferImageHeight(0);
                copy.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                copy.imageSubresource().mipLevel(mip);
                copy.imageSubresource().baseArrayLayer(0);
                copy.imageSubresource().layerCount(1);
                copy.imageOffset().set(destX >> mip, destY >> mip, 0);
                copy.imageExtent().set(width, height, 1);
            }

            vkCmdCopyBufferToImage(commandBuffer.getHandle(),
                    binding.scratch.getId(), atlas.getId(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, regions);
        }

        return new DispatchResult(regionCount, totalPixels, totalBytes);
    }

    private void createDescriptorResources() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(2, stack);
            for(int i = 0; i < 2; ++i) {
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
            check(vkCreateDescriptorSetLayout(
                    Device.device, layoutInfo, null, pLayout),
                    "create animation interpolation descriptor set layout");
            this.descriptorSetLayout = pLayout.get(0);

            VkDescriptorPoolSize.Buffer poolSizes =
                    VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(MAX_BINDINGS * 2);
            VkDescriptorPoolCreateInfo poolInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                            .pPoolSizes(poolSizes)
                            .maxSets(MAX_BINDINGS);
            LongBuffer pPool = stack.mallocLong(1);
            check(vkCreateDescriptorPool(
                    Device.device, poolInfo, null, pPool),
                    "create animation interpolation descriptor pool");
            this.descriptorPool = pPool.get(0);
        }
    }

    private void createPipelineLayout() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer pushRange =
                    VkPushConstantRange.calloc(1, stack);
            pushRange.get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(PUSH_CONSTANT_BYTES);

            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(stack.longs(this.descriptorSetLayout))
                            .pPushConstantRanges(pushRange);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreatePipelineLayout(
                    Device.device, layoutInfo, null, pLayout),
                    "create animation interpolation pipeline layout");
            this.pipelineLayout = pLayout.get(0);
        }
    }

    private void createPipeline() {
        SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShaderResource(
                "/assets/vulkanmod/shaders/texture/animation_interpolate.comp",
                SPIRVUtils.ShaderKind.COMPUTE_SHADER);
        long shaderModule = VK_NULL_HANDLE;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo moduleInfo =
                    VkShaderModuleCreateInfo.calloc(stack)
                            .sType$Default()
                            .pCode(spirv.bytecode());
            LongBuffer pModule = stack.mallocLong(1);
            check(vkCreateShaderModule(
                    Device.device, moduleInfo, null, pModule),
                    "create animation interpolation shader module");
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
                    .layout(this.pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(vkCreateComputePipelines(
                    Device.device, VK_NULL_HANDLE,
                    pipelineInfo, null, pPipeline),
                    "create animation interpolation compute pipeline");
            this.pipeline = pPipeline.get(0);
        } finally {
            if(shaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(Device.device, shaderModule, null);
            }
            spirv.free();
        }
    }

    private static boolean graphicsQueueSupportsCompute() {
        Boolean cached = graphicsComputeSupported;
        if(cached != null) {
            return cached;
        }
        if(Device.physicalDevice == null) {
            return false;
        }

        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    Device.physicalDevice, count, null);
            VkQueueFamilyProperties.Buffer properties =
                    VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    Device.physicalDevice, count, properties);
            int graphicsFamily = Queue.getQueueFamilies().graphicsFamily;
            boolean supported =
                    (properties.get(graphicsFamily).queueFlags()
                            & VK_QUEUE_COMPUTE_BIT) != 0;
            graphicsComputeSupported = supported;
            return supported;
        }
    }

    private static void check(int result, String action) {
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to " + action + ": " + result);
        }
    }

    @Override
    public synchronized void close() {
        if(this.closed) {
            return;
        }
        this.closed = true;

        if(this.pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(Device.device, this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        if(this.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(Device.device, this.pipelineLayout, null);
            this.pipelineLayout = VK_NULL_HANDLE;
        }
        if(this.descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(Device.device, this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
        }
        if(this.descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(
                    Device.device, this.descriptorSetLayout, null);
            this.descriptorSetLayout = VK_NULL_HANDLE;
        }
        this.liveBindings = 0;
    }

    public record DispatchResult(int regions, long pixels, long bytes) {
    }

    public static final class Binding {
        private final GpuTextureInterpolationCompute owner;
        private final long descriptorSet;
        private final TextureResidentBuffer source;
        private final TextureResidentBuffer scratch;
        private final int scratchBytes;
        private boolean retired;

        private Binding(GpuTextureInterpolationCompute owner, long descriptorSet,
                        TextureResidentBuffer source, TextureResidentBuffer scratch,
                        int scratchBytes) {
            this.owner = owner;
            this.descriptorSet = descriptorSet;
            this.source = source;
            this.scratch = scratch;
            this.scratchBytes = scratchBytes;
        }

        public DispatchResult dispatch(VulkanImage atlas,
                                       int[] sourceMipOffsets, int[] sourceWidths,
                                       int[] sourceHeights, int[] outputOffsets,
                                       int frameWidth, int frameHeight, int columns,
                                       int currentIndex, int nextIndex,
                                       int subFrame, int duration, int destX, int destY) {
            if(this.retired) {
                return null;
            }
            return this.owner.dispatch(
                    this, atlas, sourceMipOffsets, sourceWidths, sourceHeights,
                    outputOffsets, frameWidth, frameHeight, columns,
                    currentIndex, nextIndex, subFrame, duration, destX, destY);
        }

        public synchronized void retire() {
            if(this.retired) {
                return;
            }
            this.retired = true;
            long set = this.descriptorSet;
            this.scratch.retire(() -> this.owner.freeDescriptorSet(set));
        }
    }
}
