package net.vulkanmod.vulkan.texture;

import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryDiagnostics;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.memory.TextureResidentBuffer;
import net.vulkanmod.vulkan.memory.StagingBufferSmokeTest;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.GraphicsQueue;
import net.vulkanmod.vulkan.shader.EffectRenderState;
import net.vulkanmod.vulkan.shader.ShaderRenderState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

public abstract class VTextureSelector {
    public static final int MAX_LEGACY_TEXTURE_UNITS = 32;
    private static final int GL_TEXTURE0 = 33984;

    private static final int TEXTURE_STAGING_BATCH_LIMIT = 128 * 1024 * 1024;
    private static final int MAX_SINGLE_TEXTURE_STAGING = 512 * 1024 * 1024;
    private static final int MAX_UNBATCHED_TEXTURE_SUBMISSIONS = 256;
    private static final int MAX_SPRITE_COPY_BATCH_REGIONS = 32;

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
    private static final VulkanImage[] additionalLegacyTextures =
            new VulkanImage[MAX_LEGACY_TEXTURE_UNITS];

    private static final VulkanImage whiteTexture = VulkanImage.createWhiteTexture();
    private static final Set<String> missingSamplerWarnings = new HashSet<>();

    private static int activeTexture = 0;
    private static long coreSamplerMutationVersion;
    private static long stagingReuseCount;
    private static long stagingBatchSourceBytes;
    private static long stagingBatchStagedBytes;
    private static long stagingBatchLogicalBytes;
    private static int unbatchedTextureSubmissions;
    private static long observedMainFrameSubmissions;

    // SpriteContents uploads each mip through NativeImage independently. During a
    // texture/atlas upload batch those copies all target the same Vulkan image and
    // staging buffer, so retain their regions and record them with one Vulkan call.
    // Fixed primitive storage keeps the hot path allocation-free; 32 regions is far
    // above Minecraft's practical mip count and overflow simply flushes early.
    private static int spriteUploadDepth;
    private static VulkanImage spriteUploadTexture;
    private static long spriteUploadStagingBufferId;
    private static int spriteUploadRegionCount;
    private static final int[] spriteMipLevels = new int[MAX_SPRITE_COPY_BATCH_REGIONS];
    private static final int[] spriteWidths = new int[MAX_SPRITE_COPY_BATCH_REGIONS];
    private static final int[] spriteHeights = new int[MAX_SPRITE_COPY_BATCH_REGIONS];
    private static final int[] spriteXOffsets = new int[MAX_SPRITE_COPY_BATCH_REGIONS];
    private static final int[] spriteYOffsets = new int[MAX_SPRITE_COPY_BATCH_REGIONS];
    private static final int[] spriteBufferOffsets = new int[MAX_SPRITE_COPY_BATCH_REGIONS];

    private static void markCoreSamplerMutation() {
        coreSamplerMutationVersion++;
    }

    public static long getCoreSamplerMutationVersion() {
        return coreSamplerMutationVersion;
    }

    public static void bindTexture(VulkanImage texture) {
        if(boundTexture != texture) {
            boundTexture = texture;
            markCoreSamplerMutation();
        }
    }

    /**
     * Bind a Minecraft shader sampler slot. These are not the same numbering
     * contract as legacy OpenGL active texture units: Sampler1 is overlay and
     * Sampler2 is lightmap.
     */
    public static void bindTexture(int i, VulkanImage texture) {
        switch(i) {
            case 0 -> {
                if(boundTexture != texture) {
                    boundTexture = texture;
                    markCoreSamplerMutation();
                }
            }
            case 1 -> {
                if(overlayTexture != texture) {
                    overlayTexture = texture;
                    markCoreSamplerMutation();
                }
            }
            case 2 -> {
                if(lightTexture != texture) {
                    lightTexture = texture;
                    markCoreSamplerMutation();
                }
            }
            default -> {
                // Higher fixed shader slots use their dedicated selectors below.
            }
        }
    }

    public static void bindActiveTexture(VulkanImage texture) {
        bindLegacyTextureUnit(activeTexture, texture);
    }

    public static void bindLegacyTextureUnit(int unit, VulkanImage texture) {
        validateLegacyTextureUnit(unit);
        switch(unit) {
            case 0 -> {
                if(boundTexture != texture) {
                    boundTexture = texture;
                    markCoreSamplerMutation();
                }
            }
            case 1 -> {
                if(lightTexture != texture) {
                    lightTexture = texture;
                    markCoreSamplerMutation();
                }
            }
            case 2 -> {
                if(overlayTexture != texture) {
                    overlayTexture = texture;
                    markCoreSamplerMutation();
                }
            }
            default -> additionalLegacyTextures[unit] = texture;
        }
    }

    public static VulkanImage getLegacyTextureUnit(int unit) {
        validateLegacyTextureUnit(unit);
        return switch(unit) {
            case 0 -> boundTexture;
            case 1 -> lightTexture;
            case 2 -> overlayTexture;
            default -> additionalLegacyTextures[unit];
        };
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

    /**
     * Seed one immutable device-local animation source while the texture tick owns
     * the shared graphics upload command buffer. This is a one-time host copy;
     * steady frame changes copy directly from the resident buffer into the atlas.
     */
    public static boolean uploadTextureResidentSource(TextureResidentBuffer destination, ByteBuffer source) {
        if(destination == null || source == null || source.remaining() <= 0) {
            return false;
        }

        GraphicsQueue graphicsQueue = Device.getGraphicsQueue();
        if(!graphicsQueue.hasActiveUploadBatch()) {
            return false;
        }

        // Preserve atlas-copy order when this resident source is seeded between
        // CPU-backed sprite uploads in the outer texture-tick batch.
        flushSpriteUploadCopies();

        int size = source.remaining();
        if(size > TEXTURE_STAGING_BATCH_LIMIT || destination.getBufferSize() < size) {
            return false;
        }

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(Renderer.getCurrentFrame());
        if(stagingBuffer.wouldExceedUsageLimit(size, 4, TEXTURE_STAGING_BATCH_LIMIT)) {
            // Pending sprite regions point at the current staging allocation.
            // Record them before the normal bounded reset/reuse sequence.
            flushSpriteUploadCopies();
            graphicsQueue.endRecordingAndSubmit();

            if(AreaUploadManager.INSTANCE != null) {
                AreaUploadManager.INSTANCE.waitAllUploads();
            }
            Vulkan.waitIdle();
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();

            stagingBuffer.reset();
            stagingReuseCount++;
            resetStagingSubmissionWindow();
            graphicsQueue.startRecording();
        }

        stagingBuffer.setGrowthLimit(TEXTURE_STAGING_BATCH_LIMIT);
        try {
            stagingBuffer.align(4);
            stagingBuffer.copyBuffer(size, source, TEXTURE_STAGING_BATCH_LIMIT);
        } finally {
            stagingBuffer.setGrowthLimit(Integer.MAX_VALUE);
        }

        long sourceOffset = stagingBuffer.getOffset();
        CommandPool.CommandBuffer commandBuffer = graphicsQueue.getCommandBuffer();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer copy = VkBufferCopy.callocStack(1, stack);
            copy.srcOffset(sourceOffset);
            copy.dstOffset(0L);
            copy.size(size);
            vkCmdCopyBuffer(commandBuffer.getHandle(), stagingBuffer.getId(), destination.getId(), copy);

            VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.callocStack(1, stack);
            barrier.sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER);
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_SHADER_READ_BIT);
            barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
            barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
            barrier.buffer(destination.getId());
            barrier.offset(0L);
            barrier.size(size);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, null, barrier, null);
        }

        stagingBatchSourceBytes += size;
        stagingBatchStagedBytes += size;
        stagingBatchLogicalBytes += size;
        return true;
    }

    /**
     * Copy one discrete frame from immutable resident source sheets to an atlas.
     * All source/destination ranges are validated before any layout transition or
     * command emission so rejection can cleanly retain the CPU fallback.
     */
    public static int copyResidentSpriteFrame(TextureResidentBuffer source, VulkanImage texture,
                                               int[] mipOffsets, int[] sourceWidths, int[] sourceHeights,
                                               int destX, int destY, int sourceX, int sourceY,
                                               int frameWidth, int frameHeight) {
        if(source == null || texture == null || mipOffsets == null
                || sourceWidths == null || sourceHeights == null) {
            return -1;
        }

        GraphicsQueue graphicsQueue = Device.getGraphicsQueue();
        if(!graphicsQueue.hasActiveUploadBatch() || texture.formatSize != 4) {
            return -1;
        }

        int levels = Math.min(texture.mipLevels,
                Math.min(mipOffsets.length, Math.min(sourceWidths.length, sourceHeights.length)));
        if(levels <= 0 || frameWidth <= 0 || frameHeight <= 0) {
            return -1;
        }

        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer regions = VkBufferImageCopy.callocStack(levels, stack);
            int regionCount = 0;

            for(int mip = 0; mip < levels; ++mip) {
                int width = frameWidth >> mip;
                int height = frameHeight >> mip;
                if(width <= 0 || height <= 0) {
                    continue;
                }

                int sheetWidth = sourceWidths[mip];
                int sheetHeight = sourceHeights[mip];
                int srcX = sourceX >> mip;
                int srcY = sourceY >> mip;
                int dstX = destX >> mip;
                int dstY = destY >> mip;
                int atlasWidth = Math.max(1, texture.width >> mip);
                int atlasHeight = Math.max(1, texture.height >> mip);

                if(sheetWidth <= 0 || sheetHeight <= 0 || srcX < 0 || srcY < 0
                        || dstX < 0 || dstY < 0
                        || srcX > sheetWidth - width || srcY > sheetHeight - height
                        || dstX > atlasWidth - width || dstY > atlasHeight - height) {
                    return -1;
                }

                long rowStart = (long)srcY * sheetWidth + srcX;
                long lastPixelExclusive = ((long)srcY + height - 1L) * sheetWidth + srcX + width;
                long bufferOffset = (long)mipOffsets[mip] + rowStart * texture.formatSize;
                long bufferEnd = (long)mipOffsets[mip] + lastPixelExclusive * texture.formatSize;
                if(bufferOffset < 0L || bufferEnd > source.getBufferSize()
                        || (bufferOffset & 3L) != 0L) {
                    return -1;
                }

                VkBufferImageCopy region = regions.get(regionCount++);
                region.bufferOffset(bufferOffset);
                region.bufferRowLength(sheetWidth);
                region.bufferImageHeight(sheetHeight);
                region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                region.imageSubresource().mipLevel(mip);
                region.imageSubresource().baseArrayLayer(0);
                region.imageSubresource().layerCount(1);
                region.imageOffset().set(dstX, dstY, 0);
                region.imageExtent().set(width, height, 1);
            }

            if(regionCount <= 0) {
                return -1;
            }

            // Prior CPU-backed regions were staged earlier in this same logical
            // texture tick; emit them before this direct resident copy.
            flushSpriteUploadCopies();

            CommandPool.CommandBuffer commandBuffer = graphicsQueue.getCommandBuffer();
            texture.transferDstLayout(commandBuffer);
            regions.position(0);
            regions.limit(regionCount);
            vkCmdCopyBufferToImage(commandBuffer.getHandle(), source.getId(), texture.getId(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, regions);
            return regionCount;
        }
    }

    /**
     * Emit any CPU-backed atlas regions before an operation that records its own
     * transfer/compute commands into the same texture-tick command buffer.
     */
    public static void flushPendingSpriteUploadCopies() {
        flushSpriteUploadCopies();
    }

    public static void beginSpriteUploadBatch() {
        if(spriteUploadDepth++ == 0) {
            spriteUploadTexture = null;
            spriteUploadStagingBufferId = VK_NULL_HANDLE;
            spriteUploadRegionCount = 0;
        }
    }

    public static int spriteUploadBatchDepth() { return spriteUploadDepth; }

    /** Record pending regions without consuming an outer scope's ownership. */
    public static void flushSpriteUploadBatch() { flushSpriteUploadCopies(); }

    /** Restore the owning scope even if a leaf upload threw before its RETURN hook. */
    public static void endSpriteUploadBatchesTo(int depth) {
        if(depth < 0 || depth > spriteUploadDepth)
            throw new IllegalArgumentException("Invalid sprite upload scope depth");
        while(spriteUploadDepth > depth) endSpriteUploadBatch();
    }

    public static void endSpriteUploadBatch() {
        if(spriteUploadDepth <= 0) {
            return;
        }

        if(--spriteUploadDepth == 0) {
            try {
                flushSpriteUploadCopies();
            } finally {
                spriteUploadTexture = null;
                spriteUploadStagingBufferId = VK_NULL_HANDLE;
                spriteUploadRegionCount = 0;
            }
        }
    }

    public static void uploadSubTexture(int mipLevel, int width, int height, int xOffset, int yOffset, int unpackSkipRows, int unpackSkipPixels, int unpackRowLength, ByteBuffer buffer) {
        VulkanImage texture = getLegacyTextureUnit(activeTexture);

        if(width <= 0 || height <= 0)
            return;
        if(texture == null)
            throw new IllegalStateException(
                    "No Vulkan texture bound to active legacy texture unit " + activeTexture);

        long mainFrameSubmissions = Synchronization.INSTANCE.getMainFrameSubmissionCount();
        if(mainFrameSubmissions != observedMainFrameSubmissions) {
            resetStagingSubmissionWindow();
            observedMainFrameSubmissions = mainFrameSubmissions;
        }

        TextureUploadLayout layout = TextureUploadLayout.of(width, height, unpackSkipRows,
                unpackSkipPixels, unpackRowLength, texture.formatSize, buffer.remaining());
        long availableBytes = buffer.remaining();
        long stagedBytes = layout.packedBytes();
        long logicalBytes = stagedBytes;
        if(stagedBytes > MAX_SINGLE_TEXTURE_STAGING) {
            throw new OutOfMemoryError(String.format(
                    "Refusing %d MiB single texture staging upload (%dx%d); safety limit is %d MiB",
                    stagedBytes / (1024L * 1024L), width, height,
                    MAX_SINGLE_TEXTURE_STAGING / (1024 * 1024)));
        }

        MemoryDiagnostics.enforceSystemMemorySafety("texture staging");

        GraphicsQueue graphicsQueue = Device.getGraphicsQueue();
        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(Renderer.getCurrentFrame());
        int uploadSize = (int)stagedBytes;
        boolean activeUploadBatch = graphicsQueue.hasActiveUploadBatch();
        boolean batchSpriteCopies = spriteUploadDepth > 0 && activeUploadBatch;
        boolean stagingBudgetReached = stagingBuffer.wouldExceedUsageLimit(
                uploadSize, texture.formatSize, TEXTURE_STAGING_BATCH_LIMIT);
        boolean submissionBudgetReached = !activeUploadBatch
                && unbatchedTextureSubmissions >= MAX_UNBATCHED_TEXTURE_SUBMISSIONS;

        if(stagingBudgetReached || submissionBudgetReached) {
            // Pending regions reference the current staging contents. Record them
            // before the existing budget path submits/waits/resets that buffer.
            flushSpriteUploadCopies();

            boolean restartUploadBatch = activeUploadBatch;
            if(restartUploadBatch) {
                graphicsQueue.endRecordingAndSubmit();
            }

            if(AreaUploadManager.INSTANCE != null) {
                AreaUploadManager.INSTANCE.waitAllUploads();
            }
            Vulkan.waitIdle();
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();

            if(Minecraft.getInstance().level == null) {
                MemoryManager.getInstance().freeAllBuffers();
            }

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
            resetStagingSubmissionWindow();

            if(restartUploadBatch) {
                graphicsQueue.startRecording();
            }
        }

        stagingBuffer.setGrowthLimit(TEXTURE_STAGING_BATCH_LIMIT);

        stagingBatchSourceBytes += availableBytes;
        stagingBatchStagedBytes += stagedBytes;
        stagingBatchLogicalBytes += logicalBytes;
        if(!graphicsQueue.hasActiveUploadBatch()) {
            unbatchedTextureSubmissions++;
        }

        try {
            if(batchSpriteCopies && texture.getCurrentLayout() == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                if(spriteUploadTexture != null && spriteUploadTexture != texture) {
                    flushSpriteUploadCopies();
                }
                spriteUploadTexture = texture;
                stagingBuffer.copyTexture(buffer, layout, texture.formatSize);
                queueSpriteUploadCopy(mipLevel, width, height, xOffset, yOffset,
                        stagingBuffer.getId(), (int)stagingBuffer.getOffset());
            } else {
                // The first upload for an image still uses VulkanImage's normal path
                // so it owns the SHADER_READ/UNDEFINED -> TRANSFER_DST transition.
                // Once that transition is recorded, the remaining sprite/mip copies
                // can share one vkCmdCopyBufferToImage call.
                flushSpriteUploadCopies();
                texture.uploadSubTextureAsync(mipLevel, width, height, xOffset, yOffset,
                        layout, buffer);
                if(batchSpriteCopies) {
                    spriteUploadTexture = texture;
                }
            }
        } finally {
            stagingBuffer.setGrowthLimit(Integer.MAX_VALUE);
        }
    }

    private static void queueSpriteUploadCopy(int mipLevel, int width, int height,
                                              int xOffset, int yOffset,
                                              long stagingBufferId, int bufferOffset) {
        // StagingBuffer growth swaps its VkBuffer but deliberately retires the old
        // allocation only after the active upload batch ends. If growth happened
        // while this sprite was being staged, close the old region set against the
        // old handle before accumulating regions from the replacement buffer.
        if(spriteUploadRegionCount > 0 && spriteUploadStagingBufferId != stagingBufferId) {
            flushSpriteUploadCopies();
        }
        if(spriteUploadRegionCount == MAX_SPRITE_COPY_BATCH_REGIONS) {
            flushSpriteUploadCopies();
        }
        if(spriteUploadRegionCount == 0) {
            spriteUploadStagingBufferId = stagingBufferId;
        }

        int index = spriteUploadRegionCount++;
        spriteMipLevels[index] = mipLevel;
        spriteWidths[index] = width;
        spriteHeights[index] = height;
        spriteXOffsets[index] = xOffset;
        spriteYOffsets[index] = yOffset;
        spriteBufferOffsets[index] = bufferOffset;
    }

    private static void flushSpriteUploadCopies() {
        int count = spriteUploadRegionCount;
        if(count == 0) {
            return;
        }

        VulkanImage texture = spriteUploadTexture;
        GraphicsQueue graphicsQueue = Device.getGraphicsQueue();
        if(texture == null || spriteUploadStagingBufferId == VK_NULL_HANDLE
                || !graphicsQueue.hasActiveUploadBatch()) {
            throw new IllegalStateException("Sprite upload copy batch lost its graphics command-buffer owner");
        }

        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer regions = VkBufferImageCopy.calloc(count, stack);
            for(int i = 0; i < count; ++i) {
                VkBufferImageCopy region = regions.get(i);
                region.bufferOffset(spriteBufferOffsets[i]);
                region.bufferRowLength(0);
                region.bufferImageHeight(0);
                region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                region.imageSubresource().mipLevel(spriteMipLevels[i]);
                region.imageSubresource().baseArrayLayer(0);
                region.imageSubresource().layerCount(1);
                region.imageOffset().set(spriteXOffsets[i], spriteYOffsets[i], 0);
                region.imageExtent().set(spriteWidths[i], spriteHeights[i], 1);
            }

            vkCmdCopyBufferToImage(
                    graphicsQueue.getCommandBuffer().getHandle(),
                    spriteUploadStagingBufferId,
                    texture.getId(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    regions);
        }

        spriteUploadRegionCount = 0;
        spriteUploadStagingBufferId = VK_NULL_HANDLE;
    }

    private static void resetStagingSubmissionWindow() {
        stagingBatchSourceBytes = 0L;
        stagingBatchStagedBytes = 0L;
        stagingBatchLogicalBytes = 0L;
        unbatchedTextureSubmissions = 0;
    }

    public static VulkanImage getTexture(String name) {
        if(EffectRenderState.isActive()) {
            VulkanImage effectTexture = EffectRenderState.resolveTexture(name);
            if(effectTexture != null)
                return effectTexture;

            if(missingSamplerWarnings.add("effect:" + name)) {
                Initializer.LOGGER.warn("Effect sampler {} has no bound Vulkan texture; using white fallback", name);
            }
            return whiteTexture;
        }

        // Converted mod/legacy ShaderInstances may own arbitrary named sampler
        // objects, but they can also keep using Minecraft's standard SamplerN
        // slots. Prefer an explicit shader-owned sampler, then retain the normal
        // fixed selector contract before falling back to white.
        if(ShaderRenderState.isActive()) {
            VulkanImage shaderTexture = ShaderRenderState.resolveTexture(name);
            if(shaderTexture != null)
                return shaderTexture;

            if(isFixedSamplerName(name)) {
                VulkanImage fixedTexture = resolveFixedTexture(name);
                if(fixedTexture != null)
                    return fixedTexture;
            }

            if(missingSamplerWarnings.add("shader:" + name)) {
                Initializer.LOGGER.warn("Legacy shader sampler {} has no bound Vulkan texture; using white fallback", name);
            }
            return whiteTexture;
        }

        if(!isFixedSamplerName(name)) {
            throw new RuntimeException("unknown sampler name: " + name);
        }

        VulkanImage texture = resolveFixedTexture(name);
        if(texture == null) {
            if(missingSamplerWarnings.add(name)) {
                Initializer.LOGGER.warn("Sampler {} has no bound Vulkan texture; using white fallback", name);
            }
            return whiteTexture;
        }

        return texture;
    }

    private static boolean isFixedSamplerName(String name) {
        return switch (name) {
            case "Sampler0", "Sampler1", "Sampler2", "Sampler3", "Sampler4",
                    "Framebuffer0", "Framebuffer1" -> true;
            default -> false;
        };
    }

    private static VulkanImage resolveFixedTexture(String name) {
        return switch (name) {
            case "Sampler0" -> getBoundTexture();
            case "Sampler1" -> getOverlayTexture();
            case "Sampler2" -> getLightTexture();
            case "Sampler3" -> boundTexture2;
            case "Sampler4" -> boundTexture3;
            case "Framebuffer0" -> framebufferTexture;
            case "Framebuffer1" -> framebufferTexture2;
            default -> null;
        };
    }

    public static void setLightTexture(VulkanImage texture) {
        if(lightTexture != texture) {
            lightTexture = texture;
            markCoreSamplerMutation();
        }
    }

    public static void setOverlayTexture(VulkanImage texture) {
        if(overlayTexture != texture) {
            overlayTexture = texture;
            markCoreSamplerMutation();
        }
    }

    public static void setActiveTexture(int texture) {
        int unit = texture >= GL_TEXTURE0 ? texture - GL_TEXTURE0 : texture;
        validateLegacyTextureUnit(unit);
        activeTexture = unit;
    }

    public static int getActiveTextureUnit() {
        return activeTexture;
    }

    private static void validateLegacyTextureUnit(int unit) {
        if(unit < 0 || unit >= MAX_LEGACY_TEXTURE_UNITS) {
            throw new IllegalArgumentException(
                    "Unsupported legacy texture unit " + unit
                            + " (expected 0-" + (MAX_LEGACY_TEXTURE_UNITS - 1) + ")");
        }
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
