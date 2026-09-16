package net.vulkanmod.render.chunk;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

/** Exercise the real cache and mapped command buffers during opt-in CI startup. */
public final class RegionBatchSmokeTest {
    private RegionBatchSmokeTest() {}

    public static void verify() {
        verifyGpuIndirectFallbackContract();

        ChunkArea area = new ChunkArea(0, new Vector3i(-128, -128, 128));
        ChunkArea recycleArea = new ChunkArea(1, new Vector3i(-128, -128, 128));
        DrawBuffers buffers = area.drawBuffers;
        DrawBuffers recycleBuffers = recycleArea.drawBuffers;
        var first = new RegionDrawBatch.FrameBatch();
        var second = new RegionDrawBatch.FrameBatch();
        var solid = new RegionDrawBatch.FrameBatch();
        AreaBuffer persistentBuffer = null;
        try {
            require(!TerrainShaderManager.useRegionBatching(RenderType.translucent()), "Water must retain its renderer");
            require(!TerrainShaderManager.useRegionBatching(RenderType.tripwire()), "Tripwire must retain its renderer");
            require(new ChunkAreaManager(1, 24, -64).ySize == 3, "Overworld area rows must match section count");
            require(new ChunkAreaManager(1, 16, 0).ySize == 2, "Zero-based area rows must match section count");

            // A drained coarse ring slot should keep its physical Vulkan buffers
            // when it wraps to a new world region. This is the common traversal path.
            recycleBuffers.allocateBuffers();
            long regionVertexBuffer = recycleBuffers.vertexBuffer.getId();
            long regionIndexBuffer = recycleBuffers.indexBuffer.getId();
            long regionReuseBefore = RegionBatchStats.regionBufferReuses;
            recycleArea.repositionForReuse(256, -128, 128);
            require(recycleBuffers.isAllocated(), "Drained region buffers must remain allocated across ring reuse");
            require(recycleBuffers.vertexBuffer.getId() == regionVertexBuffer
                            && recycleBuffers.indexBuffer.getId() == regionIndexBuffer,
                    "Drained region reuse must preserve Vulkan buffer handles");
            require(RegionBatchStats.regionBufferReuses == regionReuseBefore + 1,
                    "Drained region reuse must be observable in terrain stats");

            // Exercise the production region suballocator with a real GPU buffer.
            // The first copy uses the normal asynchronous submit path: same-graphics-
            // queue ordering makes the segment immediately eligible for this frame
            // without registering a cross-queue wait semaphore. The CI-only queue
            // idle below retires the helper because this smoke exits before a normal
            // main-frame fence can own that retirement.
            persistentBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 256, Integer.BYTES);
            AreaBuffer.Segment persistentSegment = new AreaBuffer.Segment();
            try(MemoryStack stack = MemoryStack.stackPush()) {
                int waitSemaphoresBefore = Synchronization.INSTANCE.getWaitSemaphoreCount();
                persistentBuffer.upload(stack.malloc(64), persistentSegment);
                AreaUploadManager.INSTANCE.submitUploads();
                require(persistentSegment.isReady(),
                        "Same-queue terrain submit must make the segment eligible for the later graphics frame");
                require(Synchronization.INSTANCE.getWaitSemaphoreCount() == waitSemaphoresBefore,
                        "Same-queue terrain uploads must not add a transfer wait semaphore");
                Device.getGraphicsQueue().waitIdle();
                Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();

                int originalOffset = persistentSegment.getOffset();
                int reservedBytes = persistentBuffer.getUsedBytes();

                // A rebuild that still fits its old reservation must keep the same
                // address instead of returning the slice to the free list.
                persistentBuffer.upload(stack.malloc(32), persistentSegment);
                AreaUploadManager.INSTANCE.waitAllUploads();
                require(persistentSegment.getOffset() == originalOffset,
                        "Smaller terrain rebuild must reuse its persistent GPU slice");
                require(persistentBuffer.getUsedBytes() == reservedBytes,
                        "In-place rebuild must preserve reserved-byte accounting");

                // A larger rebuild still takes the established growth/relocation
                // path, whose source-buffer copy is now graphics-queue ordered too.
                persistentBuffer.upload(stack.malloc(320), persistentSegment);
                AreaUploadManager.INSTANCE.waitAllUploads();
                require(persistentSegment.getOffset() != originalOffset,
                        "Oversized terrain rebuild must relocate to a larger reservation");
                require(persistentBuffer.getCapacityBytes() > 256,
                        "Oversized terrain rebuild must retain the buffer growth fallback");
            }

            RenderSection section = new RenderSection(0, -16, -96, 176);
            var parameters = section.getDrawParameters(TerrainRenderType.CUTOUT_MIPPED);
            parameters.indexCount = 6;
            parameters.vertexOffset = 24;
            parameters.vertexBufferSegment.setPending();
            area.addSection(section);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Initial cache build");
            require(first.drawCount == 0 && first.pendingUploads, "Pending vertices must not draw");
            parameters.vertexBufferSegment.setReady();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Upload completion must retry");
            require(first.drawCount == 1 && !first.pendingUploads, "Ready geometry must draw");
            require(first.commands.getByteBuffer().getInt(16) == (7 | (2 << 3) | (3 << 6)), "GPU section coordinates");
            require(!first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Unchanged cache must not upload");

            var solidParameters = section.getDrawParameters(TerrainRenderType.SOLID);
            solidParameters.indexCount = 3;
            solidParameters.vertexOffset = 8;
            solidParameters.vertexBufferSegment.setReady();
            require(solid.update(buffers, area, TerrainRenderType.SOLID) && solid.drawCount == 1,
                    "Independent terrain layer must build its own cache");

            var emptyParameters = section.getDrawParameters(TerrainRenderType.CUTOUT);
            long emptyRevision = buffers.getMeshRevision(TerrainRenderType.CUTOUT);
            emptyParameters.reset(area);
            require(buffers.getMeshRevision(TerrainRenderType.CUTOUT) == emptyRevision,
                    "Resetting an already-empty terrain layer must not invalidate its cache");

            area.resetQueue();
            area.addSection(section);
            require(!first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED),
                    "Equivalent visibility rebuild must preserve cached commands");

            second.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED);
            require(first.commands.getId() != second.commands.getId(), "Frames must own distinct buffers");
            parameters.indexCount = 12;
            buffers.markMeshChanged(TerrainRenderType.CUTOUT_MIPPED);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Mesh edit invalidation");
            require(first.commands.getByteBuffer().getInt(0) == 12, "Edited count");
            require(second.commands.getByteBuffer().getInt(0) == 6, "Other frame must remain untouched");
            require(!solid.update(buffers, area, TerrainRenderType.SOLID),
                    "Editing one terrain layer must not invalidate another layer's command cache");

            // Prove the first real draw consumer, not only the pure handoff planner.
            // Publication must invalidate the cached CPU command, the opt-in path must
            // substitute the exact-generation GPU slice, and generation turnover must
            // immediately rebuild back to the untouched CPU DrawParameters before the
            // released GPU slice can be reused.
            long publishRevision = buffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED);
            GpuTerrainOutputStore.Reservation reservation = area.reserveGpuTerrainOutput(
                    section.xOffset, section.yOffset, section.zOffset,
                    TerrainRenderType.CUTOUT_MIPPED, section.getVoxelGeneration(), 3);
            require(reservation != null, "GPU handoff smoke reservation must fit");
            require(area.publishGpuTerrainOutput(reservation, 3, false),
                    "GPU handoff smoke residency must publish");
            require(buffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED)
                            == publishRevision + 1,
                    "GPU publication must invalidate the affected FrameBatch cache");
            GpuTerrainOutputStore.Residency residency = area.getGpuTerrainOutputResidency(
                    section.xOffset, section.yOffset, section.zOffset,
                    TerrainRenderType.CUTOUT_MIPPED);
            require(residency != null && residency.valid()
                            && residency.generation() == section.getVoxelGeneration(),
                    "GPU handoff smoke residency must match the section generation");

            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED, false),
                    "GPU publication must rebuild even while the draw handoff is disabled");
            require(first.gpuDrawCount == 0
                            && first.commands.getByteBuffer().getInt(0) == 12
                            && first.commands.getByteBuffer().getInt(8) == parameters.firstIndex
                            && first.commands.getByteBuffer().getInt(12) == parameters.vertexOffset,
                    "Disabled handoff must preserve the CPU FrameBatch command byte-for-byte");

            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED, true),
                    "Enabling the GPU terrain handoff must rebuild the frame-local cache");
            require(first.gpuDrawCount == 1 && first.maxGpuVertexCount == 12,
                    "Exact-generation residency must be counted as one bounded GPU draw");
            require(first.commands.getByteBuffer().getInt(0) == 18
                            && first.commands.getByteBuffer().getInt(8) == 0
                            && first.commands.getByteBuffer().getInt(12) == residency.vertexOffset()
                            && first.commands.getByteBuffer().getInt(16)
                            == (7 | (2 << 3) | (3 << 6)),
                    "FrameBatch must substitute GPU quad geometry while retaining section identity");

            long invalidateRevision = buffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED);
            area.removeVoxels(section.xOffset, section.yOffset, section.zOffset,
                    section.getVoxelGeneration() + 1L);
            require(buffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED)
                            == invalidateRevision + 1,
                    "GPU generation turnover must invalidate cached resident commands");
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED, true),
                    "Stale GPU residency must force a FrameBatch rebuild");
            require(first.gpuDrawCount == 0
                            && first.commands.getByteBuffer().getInt(0) == 12
                            && first.commands.getByteBuffer().getInt(8) == parameters.firstIndex
                            && first.commands.getByteBuffer().getInt(12) == parameters.vertexOffset,
                    "Generation turnover must fall back to the untouched CPU command");

            area.resetQueue();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 0,
                    "Visibility removal must clear draws");
            area.resetQueue();
            area.addSection(section);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 1,
                    "Visibility restoration must rebuild draws");

            parameters.reset(area);
            require(!parameters.ready, "Reset parameters must not retain upload readiness");
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 0,
                    "Section reset must invalidate cached geometry");

            // If a coarse slot unexpectedly still owns geometry, keep the old safe
            // behavior instead of reusing storage whose contents may still matter.
            long fallbackBefore = RegionBatchStats.regionBufferFallbacks;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                AreaBuffer.Segment liveSegment = new AreaBuffer.Segment();
                recycleBuffers.vertexBuffer.upload(
                        stack.malloc(TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize()), liveSegment);
                AreaUploadManager.INSTANCE.waitAllUploads();
            }
            require(recycleBuffers.hasLiveGeometry(), "Fallback probe must create live region geometry");
            recycleArea.repositionForReuse(384, -128, 128);
            require(!recycleBuffers.isAllocated(), "Live region geometry must retain release/reallocate fallback");
            require(RegionBatchStats.regionBufferFallbacks == fallbackBefore + 1,
                    "Live-region fallback must be observable in terrain stats");

            Initializer.LOGGER.info("Terrain region cache smoke test passed");
        } finally {
            if (persistentBuffer != null) persistentBuffer.freeBuffer();
            area.releaseBuffers();
            recycleArea.releaseBuffers();
            if (first.commands != null) first.commands.freeBuffer();
            if (second.commands != null) second.commands.freeBuffer();
            if (solid.commands != null) solid.commands.freeBuffer();
            RegionBatchStats.reset();
        }
    }

    private static void verifyGpuIndirectFallbackContract() {
        require(!RegionDrawBatch.isGpuIndirectPlanSafe(false, true, 512, 99, 512),
                "Disabled GPU indirect draw must retain CPU commands");
        require(!RegionDrawBatch.isGpuIndirectPlanSafe(true, false, 512, 99, 512),
                "Invalid or stale GPU output must retain CPU commands");
        require(!RegionDrawBatch.isGpuIndirectPlanSafe(true, true, 98, 99, 512),
                "GPU candidate input smaller than the CPU draw set must fail closed");
        require(!RegionDrawBatch.isGpuIndirectPlanSafe(true, true, 513, 99, 512),
                "GPU candidate input must not exceed persistent output capacity");
        require(!RegionDrawBatch.isGpuIndirectPlanSafe(true, true, 1, 0, 512),
                "Empty authoritative CPU draw sets must not switch ownership");
        require(RegionDrawBatch.isGpuIndirectPlanSafe(true, true, 99, 99, 512),
                "Exact bounded GPU candidate sets should be consumable");
        require(RegionDrawBatch.isGpuIndirectPlanSafe(true, true, 512, 99, 512),
                "Bounded GPU supersets should be consumable with zero-tail commands");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
