package net.vulkanmod.vulkan.memory;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.apache.commons.lang3.Validate;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class MemoryManager {
    private static final boolean DEBUG = false;
    // Opt-in trace for a validation-reported buffer handle. This records the
    // allocation and retirement call sites without logging every GPU object.
    private static final long TRACE_BUFFER_ID = Long.getLong("vulkanmod.traceBufferId", -1L);

    private static void traceBuffer(String event, long id) {
        if(id == TRACE_BUFFER_ID)
            new Throwable("Vulkan buffer " + Long.toHexString(id) + " " + event)
                    .printStackTrace(System.err);
    }

    private static MemoryManager INSTANCE;

    private static final Long2ReferenceOpenHashMap<Buffer> buffers = new Long2ReferenceOpenHashMap<>();
    private static final Long2ReferenceOpenHashMap<VulkanImage> images = new Long2ReferenceOpenHashMap<>();

    private static final VkDevice device = Vulkan.getDevice();
    private static final long allocator = Vulkan.getAllocator();
    static int Frames;

    private static long deviceMemory = 0;
    private static long nativeMemory = 0;

    private int currentFrame = 0;

    private ObjectArrayList<Buffer.BufferInfo>[] freeableBuffers = new ObjectArrayList[Frames];
    private ObjectArrayList<Buffer.BufferInfo>[] freeableStagingBuffers = new ObjectArrayList[Frames];
    private ObjectArrayList<VulkanImage>[] freeableImages = new ObjectArrayList[Frames];

    private ObjectArrayList<Runnable>[] frameOps = new ObjectArrayList[Frames];

    //debug
    private ObjectArrayList<StackTraceElement[]>[] stackTraces;

    public static MemoryManager getInstance() {
        return INSTANCE;
    }

    public static void createInstance(int frames) {
        Frames = frames;

        INSTANCE = new MemoryManager();
    }

    public static int getFrames() {
        return Frames;
    }

    MemoryManager() {
        for(int i = 0; i < Frames; ++i) {
            freeableBuffers[i] = new ObjectArrayList<>();
            freeableStagingBuffers[i] = new ObjectArrayList<>();
            freeableImages[i] = new ObjectArrayList<>();

            frameOps[i] = new ObjectArrayList<>();
        }

        if(DEBUG) {
            stackTraces = new ObjectArrayList[Frames];
            for(int i = 0; i < Frames; ++i) {
                stackTraces[i] = new ObjectArrayList<>();
            }
        }
    }

    public synchronized void initFrame(int frame) {
        this.setCurrentFrame(frame);
        this.freeBuffers(frame);
        this.doFrameOps(frame);
    }

    public void setCurrentFrame(int frame) {
        Validate.isTrue(frame < Frames, "Out of bounds frame index");
        this.currentFrame = frame;
    }

    public void freeAllBuffers() {
        for(int frame = 0; frame < Frames ; ++frame) {
            this.freeBuffers(frame);
            this.doFrameOps(frame);
        }

//        buffers.values().forEach(buffer -> freeBuffer(buffer.getId(), buffer.getAllocation()));
//        images.values().forEach(image -> image.doFree(this));
    }

    public void createBuffer(long size, int usage, int properties, LongBuffer pBuffer, PointerBuffer pBufferMemory) {

        try(MemoryStack stack = stackPush()) {

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            bufferInfo.size(size);
            bufferInfo.usage(usage);

            Queue.QueueFamilyIndices queueFamilies = Queue.getQueueFamilies();
            boolean transferred = (usage & (VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT)) != 0;
            if(transferred && !queueFamilies.graphicsFamily.equals(queueFamilies.transferFamily)) {
                bufferInfo.sharingMode(VK_SHARING_MODE_CONCURRENT);
                bufferInfo.pQueueFamilyIndices(stack.ints(queueFamilies.graphicsFamily, queueFamilies.transferFamily));
            } else {
                bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            VmaAllocationCreateInfo allocationInfo  = VmaAllocationCreateInfo.callocStack(stack);
            //allocationInfo.usage(VMA_MEMORY_USAGE_CPU_ONLY);
            allocationInfo.requiredFlags(properties);

            int result = vmaCreateBuffer(allocator, bufferInfo, allocationInfo, pBuffer, pBufferMemory, null);
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer:" + result);
            }
            if(pBuffer.get(0) == TRACE_BUFFER_ID)
                traceBuffer("created (size=" + size + ", usage=" + usage + ")", pBuffer.get(0));

        }
    }

    public synchronized void createBuffer(Buffer buffer, int size, int usage, int properties) {

        try (MemoryStack stack = stackPush()) {
            buffer.setBufferSize(size);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.pointers(VK_NULL_HANDLE);

            this.createBuffer(size, usage, properties, pBuffer, pAllocation);

            buffer.setId(pBuffer.get(0));
            buffer.setAllocation(pAllocation.get(0));

            if((properties & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) > 0) {
                deviceMemory += size;
            } else {
                nativeMemory += size;
            }

            buffers.putIfAbsent(buffer.getId(), buffer);
        }
    }

    public static synchronized void createImage(int width, int height, int mipLevels, int format, int tiling, int usage, int memProperties,
                                   LongBuffer pTextureImage, PointerBuffer pTextureImageMemory) {

        try(MemoryStack stack = stackPush()) {

            VkImageCreateInfo imageInfo = VkImageCreateInfo.callocStack(stack);
            imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
            imageInfo.imageType(VK_IMAGE_TYPE_2D);
            imageInfo.extent().width(width);
            imageInfo.extent().height(height);
            imageInfo.extent().depth(1);
            imageInfo.mipLevels(mipLevels);
            imageInfo.arrayLayers(1);
            imageInfo.format(format);
            imageInfo.tiling(tiling);
            imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            // RenderTarget.copyDepthFrom is a framebuffer-level operation in
            // OpenGL, but Vulkan requires the underlying depth images to opt into
            // transfer usage when they are created. Make every depth attachment
            // eligible so both the swapchain depth buffer and generic off-screen
            // targets can participate without a separate allocation path.
            int imageUsage = usage;
            if(format == VK_FORMAT_D32_SFLOAT
                    || format == VK_FORMAT_D32_SFLOAT_S8_UINT
                    || format == VK_FORMAT_D24_UNORM_S8_UINT) {
                imageUsage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            }
            imageInfo.usage(imageUsage);
            imageInfo.samples(VK_SAMPLE_COUNT_1_BIT);
//            imageInfo.sharingMode(VK_SHARING_MODE_CONCURRENT);
            //TODO
            imageInfo.pQueueFamilyIndices(stack.ints(0,1));

            VmaAllocationCreateInfo allocationInfo  = VmaAllocationCreateInfo.callocStack(stack);
            //allocationInfo.usage(VMA_MEMORY_USAGE_CPU_ONLY);
            allocationInfo.requiredFlags(memProperties);

            int result = vmaCreateImage(allocator, imageInfo, allocationInfo, pTextureImage, pTextureImageMemory, null);
            if(result != VK_SUCCESS) {
                String message = "Failed to create Vulkan image " + width + "x" + height +
                        " mips=" + mipLevels + " format=" + format + ": " + result;
                // VulkanImage's legacy create path catches Exception. Propagate the
                // two actual Vulkan allocation failures as Errors so it cannot
                // continue with null handles after memory exhaustion.
                if(result == VK_ERROR_OUT_OF_HOST_MEMORY || result == VK_ERROR_OUT_OF_DEVICE_MEMORY) {
                    throw new OutOfMemoryError(message);
                }
                throw new RuntimeException(message);
            }

        }
    }

    public static synchronized void addImage(VulkanImage image) {
        if(!images.containsKey(image.getId())) {
            images.put(image.getId(), image);
            MemoryDiagnostics.onVulkanImageAllocated(
                    image.getEstimatedSizeBytes(), getAllocationSize(image.getAllocation()));
        }
    }

    public void MapAndCopy(long allocation, long bufferSize, Consumer<PointerBuffer> consumer){

        try(MemoryStack stack = stackPush()) {
            PointerBuffer data = stack.mallocPointer(1);
            int result = vmaMapMemory(allocator, allocation, data);
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to map VMA allocation: " + result);
            }

            try {
                consumer.accept(data);
            } finally {
                vmaUnmapMemory(allocator, allocation);
            }
        }

    }

    public PointerBuffer Map(long allocation) {
        PointerBuffer data = MemoryUtil.memAllocPointer(1);
        int result = vmaMapMemory(allocator, allocation, data);
        if(result != VK_SUCCESS) {
            MemoryUtil.memFree(data);
            throw new RuntimeException("Failed to map VMA allocation: " + result);
        }

        return data;
    }

    public static void freeBuffer(long buffer, long allocation) {
        traceBuffer("destroyed directly", buffer);
        vmaDestroyBuffer(allocator, buffer, allocation);

        buffers.remove(buffer);
    }

    private static void freeBuffer(Buffer.BufferInfo bufferInfo) {
        if(bufferInfo.data() != null) {
            vmaUnmapMemory(allocator, bufferInfo.allocation());
            MemoryUtil.memFree(bufferInfo.data());
        }

        traceBuffer("destroyed after frame retirement", bufferInfo.id());
        vmaDestroyBuffer(allocator, bufferInfo.id(), bufferInfo.allocation());

        if(bufferInfo.type() == MemoryType.Type.DEVICE_LOCAL) {
            deviceMemory -= bufferInfo.bufferSize();
        } else {
            nativeMemory -= bufferInfo.bufferSize();
        }

        buffers.remove(bufferInfo.id());
    }

    public static synchronized void freeImage(long image, long allocation) {
        VulkanImage tracked = images.get(image);
        long allocationBytes = tracked != null ? getAllocationSize(allocation) : 0L;

        vmaDestroyImage(allocator, image, allocation);

        tracked = images.remove(image);
        if(tracked != null) {
            MemoryDiagnostics.onVulkanImageFreed(tracked.getEstimatedSizeBytes(), allocationBytes);
        }
    }

    private static long getAllocationSize(long allocation) {
        if(allocation == VK_NULL_HANDLE)
            return 0L;

        try(MemoryStack stack = stackPush()) {
            VmaAllocationInfo allocationInfo = VmaAllocationInfo.malloc(stack);
            vmaGetAllocationInfo(allocator, allocation, allocationInfo);
            return allocationInfo.size();
        }
    }

    public synchronized void addToFreeable(Buffer buffer) {
        Buffer.BufferInfo bufferInfo = buffer.getBufferInfo();

        checkBuffer(bufferInfo);
        if(bufferInfo.id() == TRACE_BUFFER_ID)
            traceBuffer("enqueued from " + buffer.getClass().getSimpleName() +
                    " in frame slot " + currentFrame, bufferInfo.id());

        if(buffer instanceof StagingBuffer) {
            // Uploads may be submitted after this slot's last frame fence, or
            // still be recorded in a shared atlas batch. That fence alone does
            // not prove a resized staging buffer is safe to destroy.
            freeableStagingBuffers[currentFrame].add(bufferInfo);
        } else {
            freeableBuffers[currentFrame].add(bufferInfo);
        }

        if(DEBUG)
            stackTraces[currentFrame].add(new Throwable().getStackTrace());
    }

    public synchronized void addToFreeable(VulkanImage image) {
        freeableImages[currentFrame].add(image);
    }

    public synchronized void addFrameOp(Runnable runnable) {
        this.frameOps[currentFrame].add(runnable);
    }

    public void doFrameOps(int frame) {

        for(Runnable runnable : this.frameOps[frame]) {
            runnable.run();
        }

        this.frameOps[frame].clear();
    }

    private void freeBuffers(int frame) {

        List<Buffer.BufferInfo> stagingBuffers = freeableStagingBuffers[frame];
        if(!stagingBuffers.isEmpty() && !Device.getGraphicsQueue().hasActiveUploadBatch()) {
            // Both queues can copy from staging memory. Waiting only for the
            // frame fence misses uploads submitted after that frame. Never wait
            // while a batch is recording: it may still reference these buffers.
            Device.getGraphicsQueue().waitIdle();
            Device.getTransferQueue().waitIdle();
            for(Buffer.BufferInfo bufferInfo : stagingBuffers) {
                freeBuffer(bufferInfo);
            }
            stagingBuffers.clear();
        }

        List<Buffer.BufferInfo> bufferList = freeableBuffers[frame];
        for(Buffer.BufferInfo bufferInfo : bufferList) {

            freeBuffer(bufferInfo);
        }

        bufferList.clear();

        if(DEBUG)
            stackTraces[frame].clear();

        this.freeImages(frame);
    }

    private void freeImages(int frame) {
        List<VulkanImage> bufferList = freeableImages[frame];
        for(VulkanImage image : bufferList) {

            image.doFree();
        }

        bufferList.clear();
    }

    private void checkBuffer(Buffer.BufferInfo bufferInfo) {
        if(buffers.get(bufferInfo.id()) == null){
            throw new RuntimeException("trying to free not present buffer");
        }

    }

    public static int findMemoryType(int typeFilter, int properties) {

        VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.mallocStack();
        vkGetPhysicalDeviceMemoryProperties(Vulkan.getDevice().getPhysicalDevice(), memProperties);

        for(int i = 0;i < memProperties.memoryTypeCount();i++) {
            if((typeFilter & (1 << i)) != 0 && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }

        throw new RuntimeException("Failed to find suitable memory type");
    }

    public int getNativeMemoryMB() { return (int) (nativeMemory / 1048576L); }

    public int getDeviceMemoryMB() { return (int) (deviceMemory / 1048576L); }

    /**
     * Diagnostic ownership counts. These deliberately expose counts rather than
     * collections so lifecycle telemetry cannot mutate MemoryManager state.
     */
    public static synchronized int getTrackedBufferCount() {
        return buffers.size();
    }

    public static synchronized int getTrackedImageCount() {
        return images.size();
    }

    /**
     * Snapshot the work that has been retired logically but is still waiting for a
     * safe frame slot. The three values are diagnostic only and need not describe
     * one globally atomic instant relative to Vulkan submission.
     */
    public synchronized DeferredResourceStats getDeferredResourceStats() {
        int pendingBuffers = 0;
        int pendingImages = 0;
        int pendingFrameOps = 0;

        for(int frame = 0; frame < Frames; ++frame) {
            pendingBuffers += this.freeableBuffers[frame].size();
            pendingImages += this.freeableImages[frame].size();
            pendingFrameOps += this.frameOps[frame].size();
        }

        return new DeferredResourceStats(pendingBuffers, pendingImages, pendingFrameOps);
    }

    public record DeferredResourceStats(int buffers, int images, int frameOps) {
    }
}
