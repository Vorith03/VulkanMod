package net.vulkanmod.vulkan.texture;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryDiagnostics;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.memory.StagingBufferSmokeTest;
import net.vulkanmod.vulkan.queue.GraphicsQueue;

import java.nio.ByteBuffer;

public abstract class VTextureSelector {
    private static final int TEXTURE_STAGING_BATCH_LIMIT = 128 * 1024 * 1024;

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

    private static int activeTexture = 0;
    private static long stagingReuseCount;
    private static long stagingBatchSourceBytes;
    private static long stagingBatchLogicalBytes;

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

        GraphicsQueue graphicsQueue = Device.getGraphicsQueue();
        boolean canRecycleStaging = !graphicsQueue.hasActiveUploadBatch();
        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(Renderer.getCurrentFrame());

        if(canRecycleStaging && stagingBuffer.wouldExceedUsageLimit(buffer.limit(), texture.formatSize, TEXTURE_STAGING_BATCH_LIMIT)) {
            // Terrain and texture uploads share this frame's staging allocation.
            // Submit/wait any outstanding transfer work before reusing its bytes.
            if(AreaUploadManager.INSTANCE != null) {
                AreaUploadManager.INSTANCE.waitAllUploads();
            }
            Device.getTransferQueue().waitIdle();
            graphicsQueue.waitIdle();

            // Every same-queue texture helper is complete after queue idle. Recycle
            // those command buffers now instead of retaining a large resource
            // reload's worth of completed helpers until a later frame fence.
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();

            long usedMiB = stagingBuffer.getUsedBytes() / (1024L * 1024L);
            long sourceMiB = stagingBatchSourceBytes / (1024L * 1024L);
            long logicalMiB = stagingBatchLogicalBytes / (1024L * 1024L);
            stagingBuffer.reset();
            stagingReuseCount++;
            Initializer.LOGGER.info(
                    "Reused texture staging buffer after {} MiB batch (flush #{}, source/logical={}/{} MiB)",
                    usedMiB, stagingReuseCount, sourceMiB, logicalMiB);
            MemoryDiagnostics.logSnapshot("texture staging flush #" + stagingReuseCount);
            stagingBatchSourceBytes = 0L;
            stagingBatchLogicalBytes = 0L;
        }

        if(canRecycleStaging) {
            // Prevent a threshold-crossing copy from geometrically doubling the
            // persistent host allocation beyond the resource-reload budget.
            stagingBuffer.setGrowthLimit(TEXTURE_STAGING_BATCH_LIMIT);
        }

        long sourceBytes = buffer.limit();
        long logicalBytes = (long)width * height * texture.formatSize;
        stagingBatchSourceBytes += sourceBytes;
        stagingBatchLogicalBytes += logicalBytes;

        try {
            texture.uploadSubTextureAsync(mipLevel, width, height, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, unpackRowLength, buffer);
        } finally {
            if(canRecycleStaging) {
                stagingBuffer.setGrowthLimit(Integer.MAX_VALUE);
            }
        }
    }

    public static VulkanImage getTexture(String name) {
        return switch (name) {
            case "Sampler0" -> getBoundTexture();
            case "Sampler1" -> getOverlayTexture();
            case "Sampler2" -> getLightTexture();
            case "Sampler3" -> boundTexture2;
            case "Sampler4" -> boundTexture3;
            case "Framebuffer0" -> framebufferTexture;
            case "Framebuffer1" -> framebufferTexture2;
            default -> throw new RuntimeException("unknown sampler name: " + name);
        };
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
