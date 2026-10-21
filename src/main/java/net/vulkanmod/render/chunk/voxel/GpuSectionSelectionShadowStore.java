package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.RegionBatchLayout;
import net.vulkanmod.render.chunk.VFrustum;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.memory.StorageIndirectBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Persistent, bounded GPU-selected indirect-command output for one region/layer.
 *
 * The output supports diagnostics and default-off production indirect ownership.
 * RegionDrawBatch coordinates admission; ordinary terrain retains the existing
 * CPU-built RegionDrawBatch commands. Dispatches use a helper graphics command buffer
 * because terrain draw recording occurs inside the active render pass, where
 * vkCmdDispatch would be invalid. Same-queue order plus explicit barriers makes the
 * buffer ready for a later main-frame indirect consumer without a synchronous wait.
 */
public final class GpuSectionSelectionShadowStore implements AutoCloseable {
    private static final boolean ENABLED = Boolean.getBoolean(
            "vulkanmod.experimentalGpuIndirectCommands");
    private static final int GLOBAL_BUDGET_BYTES = 16 * 1024 * 1024;
    private static final Budget GLOBAL_BUDGET = new Budget(GLOBAL_BUDGET_BYTES);
    private static final int OUTPUT_HEADER_WORDS = 4;
    private static final int COMMAND_WORDS = RegionBatchLayout.STRIDE / Integer.BYTES;
    private static final int OUTPUT_BYTES = (OUTPUT_HEADER_WORDS
            + RegionBatchLayout.MAX_DRAW_COMMANDS * COMMAND_WORDS) * Integer.BYTES;
    private static final int COMMAND_OFFSET_BYTES = OUTPUT_HEADER_WORDS * Integer.BYTES;
    private static final int PARAMETER_WORDS = 36;
    private static final int PARAMETER_BYTES = PARAMETER_WORDS * Integer.BYTES;
    private static final int WORKGROUP_SIZE = 64;
    private static boolean warnedUnavailable;
    private static boolean loggedReady;

    private final StorageIndirectBuffer output;
    private final StorageBuffer[] parameters;
    private final long descriptorPool;
    private final long[] descriptorSets;
    private long generation = -1L;
    private int regionX;
    private int regionY;
    private int regionZ;
    private final boolean[] resourcesInFlight;
    private GpuSectionSelectionShadowStore nextView;
    private long failedAllocationRevision = -1L;
    private boolean valid;
    private boolean closed;

    private GpuSectionSelectionShadowStore(int frames) {
        if(frames <= 0)
            throw new IllegalArgumentException("GPU indirect shadow store requires frame slots");

        StorageIndirectBuffer createdOutput = null;
        StorageBuffer[] createdParameters = new StorageBuffer[frames];
        long createdPool = VK_NULL_HANDLE;
        long[] createdSets = new long[frames];
        try {
            createdOutput = new StorageIndirectBuffer(OUTPUT_BYTES, MemoryTypes.GPU_MEM);
            for(int frame = 0; frame < frames; ++frame)
                createdParameters[frame] = new StorageBuffer(PARAMETER_BYTES, MemoryTypes.GPU_MEM);

            GpuSectionSelectionShadowPipeline.State pipeline =
                    GpuSectionSelectionShadowPipeline.get();
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
                poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(frames * 3);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(Device.device,
                        VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                                .pPoolSizes(poolSize).maxSets(frames), null, pPool),
                        "create section-selection shadow descriptor pool");
                createdPool = pPool.get(0);

                LongBuffer layouts = stack.mallocLong(frames);
                for(int frame = 0; frame < frames; ++frame)
                    layouts.put(frame, pipeline.descriptorSetLayout);
                LongBuffer sets = stack.mallocLong(frames);
                check(vkAllocateDescriptorSets(Device.device,
                        VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                                .descriptorPool(createdPool)
                                .pSetLayouts(layouts), sets),
                        "allocate section-selection shadow descriptor sets");
                for(int frame = 0; frame < frames; ++frame)
                    createdSets[frame] = sets.get(frame);
            }
        } catch(RuntimeException | Error failure) {
            if(createdPool != VK_NULL_HANDLE)
                vkDestroyDescriptorPool(Device.device, createdPool, null);
            for(StorageBuffer parameter : createdParameters) {
                if(parameter != null)
                    parameter.freeBuffer();
            }
            if(createdOutput != null)
                createdOutput.freeBuffer();
            throw failure;
        }

        this.output = createdOutput;
        this.parameters = createdParameters;
        this.resourcesInFlight = new boolean[frames];
        this.descriptorPool = createdPool;
        this.descriptorSets = createdSets;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static GpuSectionSelectionShadowStore tryCreate(int frames) {
        if(!ENABLED)
            return null;
        if(!GLOBAL_BUDGET.tryReserve(OUTPUT_BYTES)) {
            warnUnavailable("global output budget exhausted", null);
            return null;
        }

        try {
            return new GpuSectionSelectionShadowStore(frames);
        } catch(RuntimeException | Error failure) {
            GLOBAL_BUDGET.release(OUTPUT_BYTES);
            warnUnavailable("persistent output allocation/pipeline creation failed", failure);
            return null;
        }
    }

    private static synchronized void warnUnavailable(String reason, Throwable failure) {
        if(warnedUnavailable)
            return;
        warnedUnavailable = true;
        if(failure == null) {
            Initializer.LOGGER.warn(
                    "GPU indirect shadow path unavailable for this draw: {}. CPU terrain remains authoritative.",
                    reason);
        } else {
            Initializer.LOGGER.warn(
                    "GPU indirect shadow path unavailable for this draw: {}. CPU terrain remains authoritative.",
                    reason, failure);
        }
    }

    private static synchronized void logReady(GpuRegionCandidateTable table, int layer) {
        if(loggedReady)
            return;
        loggedReady = true;
        Initializer.LOGGER.info(
                "VULKANMOD_GPU_INDIRECT_SHADOW_READY: region=({}, {}, {}) layer={} generation={} candidates={} outputCapacity={}",
                table.regionX(), table.regionY(), table.regionZ(), layer,
                table.generation(), table.candidateCount(), RegionBatchLayout.MAX_DRAW_COMMANDS);
    }

    /**
     * Dispatch one generation into the persistent output. Returns true when the
     * generation is already present or a new helper dispatch was submitted.
     */
    public boolean dispatch(GpuRegionCandidateGpuStore.Residency residency,
                            GpuRegionCandidateTable table, int targetLayer,
                            VFrustum frustum) {
        if(closed || residency == null || !residency.valid()
                || table == null || frustum == null)
            return false;
        if(residency.generation() != table.generation()
                || residency.regionX() != table.regionX()
                || residency.regionY() != table.regionY()
                || residency.regionZ() != table.regionZ())
            return false;
        int frame = Renderer.getCurrentFrame();
        if(frame < 0 || frame >= parameters.length)
            return false;
        if(isValidFor(table.generation(), table.regionX(), table.regionY(), table.regionZ())) {
            retainForFrame(frame);
            return true;
        }

        // Protect the descriptor/parameter buffer until its actual frame retirement,
        // not until a different numeric slot happens to dispatch. Cached generations
        // can span a whole ring, so equal slot indices do not imply the same frame.
        if(resourcesInFlight[frame])
            return false;

        GpuSectionSelectionShadowPipeline.State pipeline =
                GpuSectionSelectionShadowPipeline.get();
        float[] planes = new float[VFrustum.PLANE_COUNT * VFrustum.PLANE_WORDS];
        frustum.copyPlaneEquations(planes);

        try(MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer parameterData = stack.malloc(PARAMETER_BYTES);
            writeParameters(parameterData, table, targetLayer,
                    frustum.relativeX(table.regionX()),
                    frustum.relativeY(table.regionY()),
                    frustum.relativeZ(table.regionZ()), planes);
            parameterData.flip();

            StagingBuffer staging = Vulkan.getStagingBuffer(frame);
            staging.copyBuffer(PARAMETER_BYTES, parameterData);
            long stagingOffset = staging.getOffset();

            updateDescriptorSet(frame, residency);

            CommandPool.CommandBuffer commandBuffer = Device.getGraphicsQueue().beginCommands();
            barrierPriorIndirectReadToTransfer(commandBuffer);
            TransferQueue.uploadBufferCmd(commandBuffer, staging.getId(), stagingOffset,
                    parameters[frame].getId(), 0L, PARAMETER_BYTES);
            vkCmdFillBuffer(commandBuffer.getHandle(), output.getId(),
                    0L, OUTPUT_BYTES, 0);
            barrierTransferToCompute(commandBuffer, residency, parameters[frame]);

            vkCmdBindPipeline(commandBuffer.getHandle(),
                    VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.pipeline);
            vkCmdBindDescriptorSets(commandBuffer.getHandle(),
                    VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.pipelineLayout,
                    0, stack.longs(descriptorSets[frame]), null);
            int groups = Math.max(1,
                    (table.candidateCount() + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE);
            vkCmdDispatch(commandBuffer.getHandle(), groups, 1, 1);
            barrierComputeToIndirect(commandBuffer);

            // GraphicsQueue tracks this helper until the later main-frame fence.
            // Since it is submitted before the main frame on the same VkQueue, the
            // barrier above covers a future vkCmdDrawIndexedIndirect consumer.
            Device.getGraphicsQueue().submitCommands(commandBuffer);
        } catch(RuntimeException | Error failure) {
            valid = false;
            warnUnavailable("shadow compute dispatch failed", failure);
            return false;
        }

        this.generation = table.generation();
        this.regionX = table.regionX();
        this.regionY = table.regionY();
        this.regionZ = table.regionZ();
        retainForFrame(frame);
        this.valid = true;
        logReady(table, targetLayer);
        return true;
    }

    private void retainForFrame(int frame) {
        if(resourcesInFlight[frame])
            return;
        resourcesInFlight[frame] = true;
        MemoryManager.getInstance().addFrameOp(() -> resourcesInFlight[frame] = false);
    }

    /**
     * Borrow an output for a view without overwriting another draw recorded in the
     * current frame. The root owns all slots, including returned children. Cached
     * reads claim their slot too: no compute dispatch does not mean no GPU use.
     */
    public GpuSectionSelectionShadowStore dispatchView(
            GpuRegionCandidateGpuStore.Residency residency,
            GpuRegionCandidateTable table, int layer, VFrustum frustum) {
        if(closed || table == null || residency == null || frustum == null)
            return null;
        int frame = Renderer.getCurrentFrame();
        if(frame < 0 || frame >= parameters.length)
            return null;
        // Preserve an exact cached output before considering any idle slot.
        for(GpuSectionSelectionShadowStore slot = this; slot != null; slot = slot.nextView) {
            if(slot.isValidFor(table.generation(), table.regionX(), table.regionY(), table.regionZ()))
                return slot.dispatch(residency, table, layer, frustum) ? slot : null;
        }
        GpuSectionSelectionShadowStore tail = this;
        for(GpuSectionSelectionShadowStore slot = this; slot != null; slot = slot.nextView) {
            tail = slot;
            if(!slot.resourcesInFlight[frame])
                return slot.dispatch(residency, table, layer, frustum) ? slot : null;
        }
        long revision = retirementRevision();
        if(failedAllocationRevision == revision)
            return null;
        GpuSectionSelectionShadowStore slot = tryCreate(parameters.length);
        if(slot == null) {
            failedAllocationRevision = retirementRevision();
            return null;
        }
        tail.nextView = slot;
        return slot.dispatch(residency, table, layer, frustum) ? slot : null;
    }

    /** Changes only when native output quota is actually retired. */
    public static long retirementRevision() {
        return GLOBAL_BUDGET.retirementRevision();
    }

    public boolean isValidFor(long generation, int regionX, int regionY, int regionZ) {
        return !closed && valid && this.generation == generation
                && this.regionX == regionX && this.regionY == regionY
                && this.regionZ == regionZ;
    }

    public StorageIndirectBuffer output() {
        return output;
    }

    public int commandOffsetBytes() {
        return COMMAND_OFFSET_BYTES;
    }

    public int commandCapacity() {
        return RegionBatchLayout.MAX_DRAW_COMMANDS;
    }

    public long generation() {
        return generation;
    }

    private void updateDescriptorSet(int frame,
                                     GpuRegionCandidateGpuStore.Residency residency) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer inputInfo = VkDescriptorBufferInfo.calloc(1, stack);
            inputInfo.get(0).buffer(residency.buffer().getId())
                    .offset(0L).range(residency.byteLength());
            VkDescriptorBufferInfo.Buffer outputInfo = VkDescriptorBufferInfo.calloc(1, stack);
            outputInfo.get(0).buffer(output.getId()).offset(0L).range(OUTPUT_BYTES);
            VkDescriptorBufferInfo.Buffer parameterInfo = VkDescriptorBufferInfo.calloc(1, stack);
            parameterInfo.get(0).buffer(parameters[frame].getId())
                    .offset(0L).range(PARAMETER_BYTES);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
            writes.get(0).sType$Default().dstSet(descriptorSets[frame]).dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).pBufferInfo(inputInfo);
            writes.get(1).sType$Default().dstSet(descriptorSets[frame]).dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).pBufferInfo(outputInfo);
            writes.get(2).sType$Default().dstSet(descriptorSets[frame]).dstBinding(2)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).pBufferInfo(parameterInfo);
            vkUpdateDescriptorSets(Device.device, writes, null);
        }
    }

    private static void writeParameters(ByteBuffer target,
                                        GpuRegionCandidateTable table,
                                        int layer, float regionX,
                                        float regionY, float regionZ,
                                        float[] planes) {
        long generation = table.generation();
        target.putInt((int)generation).putInt((int)(generation >>> 32))
                .putInt(layer).putInt(RegionBatchLayout.MAX_DRAW_COMMANDS)
                .putInt(table.regionX()).putInt(table.regionY()).putInt(table.regionZ()).putInt(0)
                .putFloat(regionX).putFloat(regionY).putFloat(regionZ).putInt(0);
        int planeWords = VFrustum.PLANE_COUNT * VFrustum.PLANE_WORDS;
        for(int index = 0; index < planeWords; ++index)
            target.putFloat(planes[index]);
    }

    private void barrierPriorIndirectReadToTransfer(CommandPool.CommandBuffer commandBuffer) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(output.getId()).offset(0L).size(OUTPUT_BYTES);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, barrier, null);
        }
    }

    private void barrierTransferToCompute(CommandPool.CommandBuffer commandBuffer,
                                          GpuRegionCandidateGpuStore.Residency residency,
                                          StorageBuffer parameterBuffer) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(3, stack);
            barriers.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(residency.buffer().getId()).offset(0L)
                    .size(residency.byteLength());
            barriers.get(1).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    // Selector header atomicAdd/atomicOr read the freshly cleared words
                    // as well as writing them. Both accesses must see vkCmdFillBuffer.
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(output.getId()).offset(0L).size(OUTPUT_BYTES);
            barriers.get(2).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(parameterBuffer.getId()).offset(0L).size(PARAMETER_BYTES);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, null, barriers, null);
        }
    }

    private void barrierComputeToIndirect(CommandPool.CommandBuffer commandBuffer) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(output.getId()).offset(0L).size(OUTPUT_BYTES);
            vkCmdPipelineBarrier(commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    0, null, barrier, null);
        }
    }

    @Override
    public void close() {
        GpuSectionSelectionShadowStore slot = this;
        while(slot != null) {
            GpuSectionSelectionShadowStore next = slot.nextView;
            slot.nextView = null;
            slot.closeSlot();
            slot = next;
        }
    }

    private void closeSlot() {
        if(closed)
            return;
        closed = true;
        valid = false;

        output.freeBuffer();
        for(StorageBuffer parameter : parameters)
            parameter.freeBuffer();

        MemoryManager manager = MemoryManager.getInstance();
        if(manager != null) {
            long pool = descriptorPool;
            manager.addFrameOp(() -> {
                vkDestroyDescriptorPool(Device.device, pool, null);
                GLOBAL_BUDGET.retire(OUTPUT_BYTES);
            });
        } else {
            vkDestroyDescriptorPool(Device.device, descriptorPool, null);
            GLOBAL_BUDGET.retire(OUTPUT_BYTES);
        }
    }

    static String describeGlobalBudget() {
        return GLOBAL_BUDGET.describe();
    }

    private static void check(int result, String action) {
        if(result != VK_SUCCESS)
            throw new RuntimeException("Failed to " + action + ": " + result);
    }

    private static final class Budget {
        private final int maxBytes;
        private int usedBytes;
        private long rejectedAllocations;
        private long retirementRevision;

        Budget(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        synchronized boolean tryReserve(int bytes) {
            if(bytes <= 0 || bytes > maxBytes - usedBytes) {
                rejectedAllocations++;
                return false;
            }
            usedBytes += bytes;
            return true;
        }

        synchronized void release(int bytes) {
            if(bytes <= 0 || bytes > usedBytes)
                throw new IllegalStateException("GPU indirect shadow budget accounting underflow");
            usedBytes -= bytes;
        }

        synchronized void retire(int bytes) {
            release(bytes);
            retirementRevision++;
        }

        synchronized long retirementRevision() {
            return retirementRevision;
        }

        synchronized String describe() {
            return "Terrain GPU indirect shadow buffers: " + usedBytes / 1024 + "/"
                    + maxBytes / 1024 + " KiB, rejected allocations " + rejectedAllocations;
        }
    }
}
