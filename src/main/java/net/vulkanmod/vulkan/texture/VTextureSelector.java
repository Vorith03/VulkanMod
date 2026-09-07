package net.vulkanmod.vulkan.texture;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryDiagnostics;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.memory.StagingBufferSmokeTest;
import net.vulkanmod.vulkan.queue.GraphicsQueue;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public abstract class VTextureSelector {
    private static final int TEXTURE_STAGING_BATCH_LIMIT = 128 * 1024 * 1024;
    private static final int MAX_SINGLE_TEXTURE_STAGING = 512 * 1024 * 1024;
    private static final int MAX_UNBATCHED_TEXTURE_SUBMISSIONS = 256;

    static {
        if(Boolean.getBoolean("vulkanmod.smokeTest")) {
            StagingBufferSmokeTest.verify();
        }
    }

    private static VulkanImage boundTexture;
    private static VulkanImage boundTexture2;
    private static VulkanImage boundTexture3;
    private static VulkanImage lightTexture;
    private static VulkanImage overlayTexture;
    private static VulkanImage framebufferTexture;
    private static VulkanImage framebufferTexture2;

    private static final VulkanImage whiteTexture = VulkanImage.createWhiteTexture();
    private static final Set<String> missingSamplerWarnings = new HashSet<>();

    private static int activeTexture = 0;
    private static long stagingReuseCount;
    private static long stagingBatchSourceBytes;
    private static long stagingBatchStagedBytes;
    private static long stagingBatchLogicalBytes;
    private static int unbatchedTextureSubmissions;

    public static void bindTexture(VulkanImage texture) {
        boundTexture = texture;
    }

    public static void bindTexture(int i, VulkanImage texture) {
        switch (i) {
            case 0 -> boundTexture = texture;
            case 1 -> lightTexture = texture;
            case 2 -> overlayTexture = texture;
        }
    }

    public static void bindTexture2(VulkanImage texture) {
        boundTexture2 = texture;
    }

    public static void bindTexture3(VulkanImage texture) {
        boundTexture3 = texture;
    }

    public static void bindFramebufferTexture(VulkanImage texture) {
        framebufferTexture = texture;
    }

    public static void bindFramebufferTexture2(VulkanImage texture) {
        framebufferTexture2 = texture;
    }

    public static void uploadSubTexture(int mipLevel, int width, int height, int xOffset, int yOffset, int unpackSkipRows, int unpackSkipPixels, int unpackRowLength, ByteBuffer buffer) {
        VulkanImage texture;
        if(activeTexture == 0) texture = boundTexture;
        else if(activeTexture == 1) texture = lightTexture;
        else texture = overlayTexture;

        if(width <= 0 || height <= 0)
            return;

        int rowLength = unpackRowLength > 0 ? unpackRowLength : width;
        if(rowLength < width || unpackSkipRows < 0 || unpackSkipPixels < 0 || unpackSkipPixels + width > rowLength) {
            throw new IllegalArgumentException("Invalid texture upload row/skip geometry");
        }

        long formatSize = texture.formatSize;
        long sourceOffset = ((long)rowLength * unpackSkipRows + unpackSkipPixels) * formatSize;
        long stagedBytes = ((long)(height - 1) * rowLength + width) * formatSize;
        long logicalBytes = (long)width * height * formatSize;
        long availableBytes = buffer.remaining();
        long sourceEnd = sourceOffset + stagedBytes;

        if(sourceOffset < 0L || stagedBytes <= 0L || sourceEnd < sourceOffset || sourceEnd > availableBytes) {
            throw new IllegalArgumentException(String.format(
                    "Texture upload exceeds source image: source=%d offset=%d span=%d row=%d size=%dx%d",
                    availableBytes, sourceOffset, stagedBytes, rowLength, width, height));
        }
        if(stagedBytes > MAX_SINGLE_TEXTURE_STAGING) {
            throw new OutOfMemoryError(String.format(
                    "Refusing %d MiB single texture staging upload (%dx%d row=%d); safety limit is %d MiB",
                    stagedBytes / (1024L * 1024L), width, height, rowLength,
                    MAX_SINGLE_TEXTURE_STAGING / (1024 * 1024)));
        }

        // Catch aggregate native/heap pressure even when no single VulkanMod
        // allocator has crossed its own local budget. This is throttled internally
        // to avoid turning /proc reads into per-sprite overhead.
        MemoryDiagnostics.enforceSystemMemorySafety("texture staging");

        // Old VulkanMod copied buffer.limit() for every sub-rectangle. Animated
        // sprites and mip levels therefore re-copied the entire backing NativeImage
        // even when Vulkan consumed only one frame. Slice to exactly the contiguous
        // source span referenced by VkBufferImageCopy and normalize the skips to 0.
        ByteBuffer uploadBuffer = buffer.duplicate();
        int basePosition = buffer.position();
        uploadBuffer.position(basePosition + (int)sourceOffset);
        uploadBuffer.limit(basePosition + (int)sourceEnd);
        uploadBuffer = uploadBuffer.slice();

        GraphicsQueue graphicsQueue = Device.getGraphicsQueue();
        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(Renderer.getCurrentFrame());
        int uploadSize = (int)stagedBytes;
        boolean activeUploadBatch = graphicsQueue.hasActiveUploadBatch();
        boolean stagingBudgetReached = stagingBuffer.wouldExceedUsageLimit(
                uploadSize, texture.formatSize, TEXTURE_STAGING_BATCH_LIMIT);
        boolean submissionBudgetReached = !activeUploadBatch
                && unbatchedTextureSubmissions >= MAX_UNBATCHED_TEXTURE_SUBMISSIONS;

        if(stagingBudgetReached || submissionBudgetReached) {
            // The old resource-reload guard disabled recycling while the animated
            // texture queue owned a shared command buffer. That made the 128 MiB
            // limit ineffective exactly when a long upload batch could grow the
            // mapped host buffer without bound. Close the batch, make every queue
            // idle, drain deferred old staging allocations, then transparently
            // resume batching with a fresh command buffer.
            boolean restartUploadBatch = activeUploadBatch;
            if(restartUploadBatch) {
                graphicsQueue.endRecordingAndSubmit();
            }

            if(AreaUploadManager.INSTANCE != null) {
                AreaUploadManager.INSTANCE.waitAllUploads();
            }
            Vulkan.waitIdle();
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();

            // StagingBuffer resize schedules the previous mapped buffer for frame
            // retirement. Startup/resource reload may not advance another frame
            // before memory pressure becomes catastrophic, so collect all already-
            // retired resources now that device idleness makes that unambiguous.
            MemoryManager.getInstance().freeAllBuffers();

            long usedMiB = stagingBuffer.getUsedBytes() / (1024L * 1024L);
            long sourceMiB = stagingBatchSourceBytes / (1024L * 1024L);
            long stagedMiB = stagingBatchStagedBytes / (1024L * 1024L);
            long logicalMiB = stagingBatchLogicalBytes / (1024L * 1024L);
            int retiredSubmissions = unbatchedTextureSubmissions;
            stagingBuffer.reset();
            stagingReuseCount++;
            Initializer.LOGGER.info(
                    "Reused texture staging buffer after {} MiB batch (flush #{}, source/staged/logical={}/{}/{} MiB, submissions={}, restartedBatch={}, reason={})",
                    usedMiB, stagingReuseCount, sourceMiB, stagedMiB, logicalMiB,
                    retiredSubmissions, restartUploadBatch,
                    stagingBudgetReached ? "bytes" : "submission-count");
            MemoryDiagnostics.logSnapshot("texture staging flush #" + stagingReuseCount);
            stagingBatchSourceBytes = 0L;
            stagingBatchStagedBytes = 0L;
            stagingBatchLogicalBytes = 0L;
            unbatchedTextureSubmissions = 0;

            if(restartUploadBatch) {
                graphicsQueue.startRecording();
            }
        }

        // Always bound geometric growth, including while a shared graphics upload
        // batch is active. One upload may exceed the normal batch budget, but it
        // grows only to its exact requirement and is separately safety-limited.
        stagingBuffer.setGrowthLimit(TEXTURE_STAGING_BATCH_LIMIT);

        stagingBatchSourceBytes += availableBytes;
        stagingBatchStagedBytes += stagedBytes;
        stagingBatchLogicalBytes += logicalBytes;
        if(!graphicsQueue.hasActiveUploadBatch()) {
            unbatchedTextureSubmissions++;
        }

        try {
            texture.uploadSubTextureAsync(mipLevel, width, height, xOffset, yOffset,
                    0, 0, rowLength, uploadBuffer);
        } finally {
            stagingBuffer.setGrowthLimit(Integer.MAX_VALUE);
        }
    }

    public static VulkanImage getTexture(String name) {
        VulkanImage texture = switch (name) {
            case "Sampler0" -> getBoundTexture();
            case "Sampler1" -> getOverlayTexture();
            case "Sampler2" -> getLightTexture();
            case "Sampler3" -> boundTexture2;
            case "Sampler4" -> boundTexture3;
            case "Framebuffer0" -> framebufferTexture;
            case "Framebuffer1" -> framebufferTexture2;
            default -> throw new RuntimeException("unknown sampler name: " + name);
        };

        if(texture == null) {
            if(missingSamplerWarnings.add(name)) {
                Initializer.LOGGER.warn("Sampler {} has no bound Vulkan texture; using white fallback", name);
            }
            return whiteTexture;
        }

        return texture;
    }

    public static void setLightTexture(VulkanImage texture) {
        lightTexture = texture;
    }

    public static void setOverlayTexture(VulkanImage texture) { overlayTexture = texture; }

    public static void setActiveTexture(int activeTexture) {
        VTextureSelector.activeTexture = activeTexture;
    }

    public static VulkanImage getLightTexture() {
        return lightTexture;
    }

    public static VulkanImage getOverlayTexture() {
        return overlayTexture;
    }

    public static VulkanImage getBoundTexture() { return boundTexture; }

    public static VulkanImage getWhiteTexture() { return whiteTexture; }
}
