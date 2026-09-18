package net.vulkanmod.render.chunk;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.CompiledSection;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;
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
        verifyStagedCpuReplacementContract();
        verifyGpuAppendRebuildTransactionContract();
        verifyGpuFirstTransitionContract();

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
            require(first.drawCount == 0 && first.sectionCount == 0 && first.pendingUploads,
                    "Pending vertices must not draw or count as a rendered section");
            parameters.vertexBufferSegment.setReady();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Upload completion must retry");
            require(first.drawCount == 1 && first.sectionCount == 1 && !first.pendingUploads,
                    "Ready geometry must draw and count exactly one rendered section");
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
            require(first.drawCount == 1 && first.gpuDrawCount == 1 && first.maxGpuVertexCount == 12,
                    "Exact-generation replacement residency must be counted as one bounded GPU draw");
            require(first.commands.getByteBuffer().getInt(0) == 18
                            && first.commands.getByteBuffer().getInt(8) == 0
                            && first.commands.getByteBuffer().getInt(12) == residency.vertexOffset()
                            && first.commands.getByteBuffer().getInt(16)
                            == (7 | (2 << 3) | (3 << 6)),
                    "FrameBatch must substitute GPU quad geometry while retaining section identity");

            // Stage explicit APPEND ownership for the same generation. Production
            // workers cannot do this yet; this smoke exercises the live command
            // consumer independently before mixed CPU tessellation is enabled.
            section.stageGpuTerrainPreflight(new RenderSection.GpuTerrainPreflight(
                            123L, 3, GpuTerrainDrawHandoff.Ownership.APPEND),
                    section.getVoxelGeneration(), true);
            buffers.markMeshChanged(TerrainRenderType.CUTOUT_MIPPED);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED, true),
                    "Explicit append ownership must rebuild the live frame batch");
            require(first.drawCount == 2 && first.sectionCount == 1
                            && first.gpuDrawCount == 1 && first.maxGpuVertexCount == 12,
                    "Hybrid handoff must record two commands while counting exactly one rendered section");
            var hybridCommands = first.commands.getByteBuffer();
            int packedSection = 7 | (2 << 3) | (3 << 6);
            require(hybridCommands.getInt(0) == 12
                            && hybridCommands.getInt(8) == parameters.firstIndex
                            && hybridCommands.getInt(12) == parameters.vertexOffset
                            && hybridCommands.getInt(16) == packedSection,
                    "Hybrid first command must preserve the CPU exception draw byte-for-byte");
            require(hybridCommands.getInt(20) == 18
                            && hybridCommands.getInt(28) == 0
                            && hybridCommands.getInt(32) == residency.vertexOffset()
                            && hybridCommands.getInt(36) == packedSection,
                    "Hybrid second command must append the exact GPU quad draw with the same section identity");

            parameters.vertexBufferSegment.setPending();
            buffers.markMeshChanged(TerrainRenderType.CUTOUT_MIPPED);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED, true),
                    "Pending hybrid CPU exception upload must rebuild the cache");
            require(first.drawCount == 0 && first.sectionCount == 0
                            && first.gpuDrawCount == 0 && first.pendingUploads,
                    "Pending hybrid handoff must suppress commands and must not count an undrawn section");
            parameters.vertexBufferSegment.setReady();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED, true),
                    "Ready hybrid CPU exception upload must retry automatically");
            require(first.drawCount == 2 && first.sectionCount == 1
                            && first.gpuDrawCount == 1 && !first.pendingUploads,
                    "Ready hybrid handoff must restore two commands while counting one rendered section");

            long invalidateRevision = buffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED);
            area.removeVoxels(section.xOffset, section.yOffset, section.zOffset,
                    section.getVoxelGeneration() + 1L);
            require(buffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED)
                            == invalidateRevision + 1,
                    "GPU generation turnover must invalidate cached resident commands");
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED, true),
                    "Stale GPU residency must force a FrameBatch rebuild");
            require(first.gpuDrawCount == 0 && first.drawCount == 1 && first.sectionCount == 1
                            && first.commands.getByteBuffer().getInt(0) == 12
                            && first.commands.getByteBuffer().getInt(8) == parameters.firstIndex
                            && first.commands.getByteBuffer().getInt(12) == parameters.vertexOffset,
                    "Generation turnover must fall back to the untouched CPU command even from append ownership");

            area.resetQueue();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED)
                            && first.drawCount == 0 && first.sectionCount == 0,
                    "Visibility removal must clear commands and rendered-section count");
            area.resetQueue();
            area.addSection(section);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED)
                            && first.drawCount == 1 && first.sectionCount == 1,
                    "Visibility restoration must rebuild one command for one rendered section");

            parameters.reset(area);
            require(!parameters.ready, "Reset parameters must not retain upload readiness");
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED)
                            && first.drawCount == 0 && first.sectionCount == 0,
                    "Section reset must invalidate commands and rendered-section count");

            // If a coarse slot unexpectedly still owns geometry, detach the old
            // DrawBuffers immediately so the recycled region can proceed, but keep
            // that old backing allocation alive until every frame slot crosses its
            // retirement boundary. RegionBatchLayoutTest separately proves that the
            // all-slot callback cannot fire early.
            long fallbackBefore = RegionBatchStats.regionBufferFallbacks;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                AreaBuffer.Segment liveSegment = new AreaBuffer.Segment();
                recycleBuffers.vertexBuffer.upload(
                        stack.malloc(TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize()), liveSegment);
                AreaUploadManager.INSTANCE.waitAllUploads();
            }
            require(recycleBuffers.hasLiveGeometry(), "Fallback probe must create live region geometry");
            recycleArea.repositionForReuse(384, -128, 128);
            DrawBuffers replacementBuffers = recycleArea.drawBuffers;
            require(replacementBuffers != recycleBuffers,
                    "Live region reuse must detach the old DrawBuffers immediately");
            require(recycleBuffers.isAllocated(),
                    "Detached live region buffers must remain allocated until all frame slots retire");
            require(!replacementBuffers.isAllocated(),
                    "Recycled region must receive fresh lazy DrawBuffers");
            require(RegionBatchStats.regionBufferFallbacks == fallbackBefore + 1,
                    "Live-region fallback must be observable in terrain stats");

            Device.getGraphicsQueue().waitIdle();
            for(int frame = 0; frame < AreaUploadManager.INSTANCE.frameOps.length; ++frame) {
                AreaUploadManager.INSTANCE.updateFrame(frame);
            }
            AreaUploadManager.INSTANCE.updateFrame(net.vulkanmod.vulkan.Renderer.getCurrentFrame());
            require(!recycleBuffers.isAllocated(),
                    "Detached live region buffers must release after every frame slot retires");

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

    private static void verifyStagedCpuReplacementContract() {
        ChunkArea area = new ChunkArea(38, new Vector3i(0, 0, 0));
        DrawBuffers buffers = area.getDrawBuffers();
        TerrainRenderType type = TerrainRenderType.CUTOUT_MIPPED;
        RenderSection section = new RenderSection(0, 16, 16, 16);
        section.setChunkArea(area);
        DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
        int vertexBytes = TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize() * 4;

        try {
            require(buffers.vertexBuffer.tryReserve(vertexBytes, parameters.vertexBufferSegment),
                    "Staged CPU replacement baseline allocation must fit");
            parameters.vertexBufferSegment.setReady();
            parameters.indexCount = 6;
            parameters.firstIndex = 0;
            parameters.vertexOffset = parameters.vertexBufferSegment.getOffset()
                    / TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
            parameters.ready = true;

            int oldVertexOffset = parameters.vertexOffset;
            long oldRevision = buffers.getMeshRevision(type);
            DrawBuffers.StagedDrawParameters staged;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                staged = buffers.stageVertexData(
                        section, type, stack.calloc(vertexBytes), 12, section.getVoxelGeneration());
            }

            require(parameters.vertexOffset == oldVertexOffset
                            && parameters.indexCount == 6
                            && buffers.getMeshRevision(type) == oldRevision,
                    "Staging replacement CPU geometry must not mutate the visible draw");
            AreaUploadManager.INSTANCE.submitUploads();
            require(staged.ready(),
                    "Submitted staged CPU replacement must become ready without publication");
            require(parameters.vertexOffset == oldVertexOffset
                            && parameters.indexCount == 6,
                    "Ready staged CPU replacement must remain invisible before commit");

            require(buffers.commitStaged(parameters, staged),
                    "Ready staged CPU replacement must commit");
            int committedVertexOffset = parameters.vertexOffset;
            require(committedVertexOffset == staged.vertexOffset()
                            && committedVertexOffset != oldVertexOffset
                            && parameters.indexCount == 12
                            && buffers.getMeshRevision(type) == oldRevision + 1,
                    "CPU staged commit must swap draw metadata once and invalidate the cache");

            DrawBuffers.StagedDrawParameters abandoned;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                abandoned = buffers.stageVertexData(
                        section, type, stack.calloc(vertexBytes), 18, section.getVoxelGeneration());
            }
            AreaUploadManager.INSTANCE.submitUploads();
            require(abandoned.ready(),
                    "Discard-path staged CPU replacement must become ready");
            buffers.discardStaged(abandoned);
            require(parameters.vertexOffset == committedVertexOffset
                            && parameters.indexCount == 12,
                    "Discarding staged CPU geometry must leave the committed draw untouched");

            DrawBuffers.StagedDrawParameters empty =
                    buffers.stageEmpty(section, type, section.getVoxelGeneration());
            require(empty.ready() && buffers.commitStaged(parameters, empty)
                            && parameters.indexCount == 0
                            && parameters.vertexBufferSegment.getOffset() == -1,
                    "Staged empty CPU replacement must atomically retire prior opaque geometry");

            Device.getGraphicsQueue().waitIdle();
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();
            for(int frame = 0; frame < AreaUploadManager.INSTANCE.frameOps.length; ++frame)
                AreaUploadManager.INSTANCE.updateFrame(frame);
            AreaUploadManager.INSTANCE.updateFrame(net.vulkanmod.vulkan.Renderer.getCurrentFrame());

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_CPU_STAGE_OK: replacement CPU vertices upload out-of-band, remain hidden before commit, swap atomically when ready, and discard without disturbing the visible draw");
        } finally {
            area.releaseBuffers();
        }
    }

    private static void verifyGpuAppendRebuildTransactionContract() {
        ChunkArea area = new ChunkArea(40, new Vector3i(0, 0, 0));
        DrawBuffers buffers = area.getDrawBuffers();
        RegionDrawBatch.FrameBatch batch = new RegionDrawBatch.FrameBatch();
        boolean voxelStoreEnabled = RegionVoxelStore.ENABLED;
        RegionVoxelStore.ENABLED = true;
        try {
            RenderSection section = new RenderSection(0, 16, 16, 16);
            section.setChunkArea(area);
            area.registerSection(section, section.xOffset, section.yOffset, section.zOffset);
            area.addSection(section);

            TerrainRenderType type = RenderSection.gpuTerrainOutputLayer();
            DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
            int vertexSize = TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
            AreaBuffer.Segment oldCpuSegment = parameters.vertexBufferSegment;
            require(buffers.vertexBuffer.tryReserve(vertexSize * 4, oldCpuSegment),
                    "Atomic APPEND baseline CPU segment must fit");
            oldCpuSegment.setReady();
            parameters.indexCount = 6;
            parameters.firstIndex = 0;
            parameters.vertexOffset = oldCpuSegment.getOffset() / vertexSize;
            parameters.ready = true;

            long oldGeneration = section.getVoxelGeneration();
            section.stageGpuTerrainPreflight(new RenderSection.GpuTerrainPreflight(
                            700L, 3, GpuTerrainDrawHandoff.Ownership.APPEND),
                    oldGeneration, true);
            section.setGpuTerrainCpuMeshComplete(oldGeneration, false);
            GpuTerrainOutputStore.Reservation oldGpu = area.reserveGpuTerrainOutput(
                    section.xOffset, section.yOffset, section.zOffset,
                    type, oldGeneration, 3);
            require(oldGpu != null && area.publishGpuTerrainOutput(oldGpu, 3, false),
                    "Atomic APPEND baseline GPU half must publish");
            require(section.publishGpuTerrainDrawHandoff(
                            oldGeneration, GpuTerrainDrawHandoff.Ownership.APPEND),
                    "Atomic APPEND baseline handoff must publish");
            buffers.markMeshChanged(type);
            require(batch.update(buffers, area, type, true)
                            && batch.drawCount == 2 && batch.gpuDrawCount == 1,
                    "Atomic APPEND baseline pair must be drawable");
            section.setCompiledSection(new CompiledSection());
            require(section.hasReadyGpuTerrainAppendFallback(),
                    "Published APPEND pair must qualify as a complete rebuild fallback");

            int oldCpuOffset = parameters.vertexOffset;
            int oldGpuOffset = area.getGpuTerrainOutputResidency(
                    section.xOffset, section.yOffset, section.zOffset, type).vertexOffset();

            section.invalidateVoxels(true);
            long generation = section.getVoxelGeneration();
            require(generation == oldGeneration + 1L
                            && section.gpuTerrainCpuRecoveryRequired()
                            && section.hasReadyGpuTerrainAppendFallback(),
                    "Dirty APPEND baseline must advance inputs while retaining a complete pair fallback");
            section.stageGpuTerrainPreflight(new RenderSection.GpuTerrainPreflight(
                            701L, 4, GpuTerrainDrawHandoff.Ownership.APPEND),
                    generation, true);

            DrawBuffers.StagedDrawParameters stagedCpu;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                stagedCpu = buffers.stageVertexData(section, type,
                        stack.calloc(vertexSize * 8), 12, generation);
            }
            AreaUploadManager.INSTANCE.submitUploads();
            require(stagedCpu.ready()
                            && section.stageGpuTerrainAppendCpu(generation, stagedCpu),
                    "Atomic APPEND replacement CPU half must become staged-ready and section-owned");

            GpuTerrainOutputStore.StagedReservation stagedGpu =
                    area.reserveStagedGpuTerrainOutput(
                            section.xOffset, section.yOffset, section.zOffset,
                            type, generation, 4);
            require(stagedGpu != null
                            && stagedGpu.submitWithTarget(target -> target != null)
                            && stagedGpu.complete(4, false)
                            && stagedGpu.ready(),
                    "Atomic APPEND replacement GPU half must become staged-ready");

            require(!batch.update(buffers, area, type, true)
                            && parameters.vertexOffset == oldCpuOffset
                            && parameters.indexCount == 6,
                    "Ready staged halves must leave the previous complete pair visible");
            GpuTerrainOutputStore.Residency beforeCommit =
                    area.getGpuTerrainOutputResidency(
                            section.xOffset, section.yOffset, section.zOffset, type);
            require(beforeCommit.valid()
                            && beforeCommit.generation() == oldGeneration
                            && beforeCommit.vertexOffset() == oldGpuOffset,
                    "Staged future GPU output must not revoke the previous resident");

            GpuTerrainAppendRebuildTransaction transaction =
                    new GpuTerrainAppendRebuildTransaction(
                            section, generation, buffers, stagedCpu, stagedGpu);
            require(transaction.ready() && transaction.commit(),
                    "Ready APPEND CPU/GPU halves must commit as one render-thread transaction");
            RenderSection.GpuTerrainDrawState committedState = section.gpuTerrainDrawState();
            GpuTerrainOutputStore.Residency committedGpu =
                    area.getGpuTerrainOutputResidency(
                            section.xOffset, section.yOffset, section.zOffset, type);
            require(committedState.generation() == generation
                            && committedState.ownership() == GpuTerrainDrawHandoff.Ownership.APPEND
                            && committedGpu.valid()
                            && committedGpu.generation() == generation
                            && committedGpu.faceCount() == 4
                            && parameters.vertexOffset == stagedCpu.vertexOffset()
                            && parameters.indexCount == 12
                            && !section.gpuTerrainCpuRecoveryRequired()
                            && !section.gpuTerrainCpuMeshComplete(),
                    "Atomic APPEND commit must expose one matching new generation and clear forced recovery");

            require(batch.update(buffers, area, type, true)
                            && batch.drawCount == 2 && batch.gpuDrawCount == 1,
                    "Atomic APPEND commit must invalidate and rebuild the frame batch once");
            var commands = batch.commands.getByteBuffer();
            require(commands.getInt(0) == 12
                            && commands.getInt(12) == stagedCpu.vertexOffset()
                            && commands.getInt(20) == 24
                            && commands.getInt(32) == committedGpu.vertexOffset(),
                    "Atomic APPEND commit must draw the new CPU exceptions and new GPU half together");

            // Stale transaction path: prepare both future halves, then advance the
            // section again before commit. Aborting must not disturb the generation
            // that was already complete and visible.
            section.invalidateVoxels(true);
            long staleGeneration = section.getVoxelGeneration();
            section.stageGpuTerrainPreflight(new RenderSection.GpuTerrainPreflight(
                            702L, 5, GpuTerrainDrawHandoff.Ownership.APPEND),
                    staleGeneration, true);

            DrawBuffers.StagedDrawParameters staleCpu;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                staleCpu = buffers.stageVertexData(section, type,
                        stack.calloc(vertexSize * 8), 18, staleGeneration);
            }
            AreaUploadManager.INSTANCE.submitUploads();
            require(staleCpu.ready()
                            && section.stageGpuTerrainAppendCpu(staleGeneration, staleCpu),
                    "Stale-path APPEND CPU half must become section-owned");
            GpuTerrainOutputStore.StagedReservation staleGpu =
                    area.reserveStagedGpuTerrainOutput(
                            section.xOffset, section.yOffset, section.zOffset,
                            type, staleGeneration, 5);
            require(staleGpu != null
                            && staleGpu.submitWithTarget(target -> target != null)
                            && staleGpu.complete(5, false),
                    "Stale-path APPEND replacement halves must stage");

            GpuTerrainAppendRebuildTransaction staleTransaction =
                    new GpuTerrainAppendRebuildTransaction(
                            section, staleGeneration, buffers, staleCpu, staleGpu);
            section.invalidateVoxels(true);
            require(!staleTransaction.ready() && !staleTransaction.commit(),
                    "Generation turnover must reject an otherwise-ready APPEND transaction");
            staleTransaction.discard();

            RenderSection.GpuTerrainDrawState retainedState = section.gpuTerrainDrawState();
            GpuTerrainOutputStore.Residency retainedGpu =
                    area.getGpuTerrainOutputResidency(
                            section.xOffset, section.yOffset, section.zOffset, type);
            require(retainedState.generation() == generation
                            && retainedState.ownership() == GpuTerrainDrawHandoff.Ownership.APPEND
                            && retainedGpu.valid()
                            && retainedGpu.generation() == generation
                            && parameters.indexCount == 12
                            && parameters.vertexOffset == stagedCpu.vertexOffset()
                            && !batch.update(buffers, area, type, true)
                            && batch.drawCount == 2 && batch.gpuDrawCount == 1,
                    "Discarding a stale APPEND transaction must leave the previous complete pair intact");

            Device.getGraphicsQueue().waitIdle();
            Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();
            for(int frame = 0; frame < AreaUploadManager.INSTANCE.frameOps.length; ++frame)
                AreaUploadManager.INSTANCE.updateFrame(frame);
            AreaUploadManager.INSTANCE.updateFrame(net.vulkanmod.vulkan.Renderer.getCurrentFrame());

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_APPEND_TRANSACTION_OK: staged CPU/GPU halves remain hidden, commit together as one generation, and stale transaction discard preserves the previous complete pair");
        } finally {
            if(batch.commands != null)
                batch.commands.freeBuffer();
            area.releaseBuffers();
            RegionVoxelStore.ENABLED = voxelStoreEnabled;
        }
    }

    private static void verifyGpuFirstTransitionContract() {
        ChunkArea area = new ChunkArea(39, new Vector3i(0, 0, 0));
        DrawBuffers buffers = area.drawBuffers;
        RegionDrawBatch.FrameBatch batch = new RegionDrawBatch.FrameBatch();
        boolean voxelStoreEnabled = RegionVoxelStore.ENABLED;
        // This startup smoke runs with experimental terrain disabled by default, but
        // the transition contract being modeled is reachable only while voxel/input
        // generations are active. Scope the gate to this oracle and restore it below.
        RegionVoxelStore.ENABLED = true;
        try {
            RenderSection section = new RenderSection(0, 16, 16, 16);
            section.setChunkArea(area);
            area.registerSection(section, section.xOffset, section.yOffset, section.zOffset);
            area.addSection(section);

            TerrainRenderType type = TerrainRenderType.CUTOUT_MIPPED;
            DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
            parameters.indexCount = 12; // CPU exceptions only: intentionally incomplete.
            parameters.firstIndex = 0;
            parameters.vertexOffset = 24;
            parameters.vertexBufferSegment.setReady();

            long generation = section.getVoxelGeneration();
            section.stageGpuTerrainPreflight(new RenderSection.GpuTerrainPreflight(
                            321L, 3, GpuTerrainDrawHandoff.Ownership.APPEND),
                    generation, true);
            section.setGpuTerrainCpuMeshComplete(generation, false);
            buffers.markMeshChanged(type);

            require(batch.update(buffers, area, type, true),
                    "Fresh hybrid publication must rebuild the frame batch");
            require(batch.drawCount == 0 && batch.gpuDrawCount == 0 && batch.pendingUploads,
                    "Incomplete CPU exceptions must stay hidden until matching GPU output publishes");

            GpuTerrainOutputStore.Reservation reservation = area.reserveGpuTerrainOutput(
                    section.xOffset, section.yOffset, section.zOffset, type, generation, 3);
            require(reservation != null, "Fresh hybrid transition reservation must fit");
            require(area.publishGpuTerrainOutput(reservation, 3, false),
                    "Fresh hybrid transition GPU result must publish");
            require(section.publishGpuTerrainDrawHandoff(
                            generation, GpuTerrainDrawHandoff.Ownership.APPEND),
                    "Fresh hybrid transition must publish explicit draw ownership");

            require(batch.update(buffers, area, type, true),
                    "Matching APPEND GPU output must make the complete pair drawable");
            require(batch.drawCount == 2 && batch.gpuDrawCount == 1 && !batch.pendingUploads,
                    "Fresh APPEND must become visible only as one complete CPU+GPU pair");
            int oldGpuVertexOffset = area.getGpuTerrainOutputResidency(
                    section.xOffset, section.yOffset, section.zOffset, type).vertexOffset();

            // Dirtying a GPU-first section advances build/input generation, but its
            // old complete draw pair must remain visible until a complete CPU mesh is
            // published. This is the same old-mesh-until-rebuild behavior users get
            // from ordinary CPU terrain.
            section.invalidateVoxels(true);
            long recoveryGeneration = section.getVoxelGeneration();
            require(recoveryGeneration == generation + 1L
                            && section.gpuTerrainCpuRecoveryRequired()
                            && !section.gpuTerrainCpuMeshComplete(),
                    "Dirty GPU-first terrain must force a complete CPU recovery generation");
            GpuTerrainOutputStore.Residency retained = area.getGpuTerrainOutputResidency(
                    section.xOffset, section.yOffset, section.zOffset, type);
            require(retained != null && retained.valid()
                            && retained.generation() == generation
                            && retained.vertexOffset() == oldGpuVertexOffset,
                    "Dirty transition must retain the previous complete GPU handoff");
            RenderSection.GpuTerrainDrawState retainedState = section.gpuTerrainDrawState();
            require(retainedState.generation() == generation
                            && retainedState.ownership() == GpuTerrainDrawHandoff.Ownership.APPEND,
                    "Dirty transition must keep drawing the previous visible generation");
            require(!batch.update(buffers, area, type, true)
                            && batch.drawCount == 2 && batch.gpuDrawCount == 1,
                    "Dirty transition must keep the cached complete APPEND pair intact");

            // Model the render-thread publication point of the forced CPU rebuild.
            parameters.indexCount = 30;
            parameters.vertexOffset = 40;
            parameters.vertexBufferSegment.setReady();
            buffers.markMeshChanged(type);
            section.completeGpuTerrainCpuRecovery(recoveryGeneration);
            require(section.gpuTerrainCpuMeshComplete()
                            && !section.gpuTerrainCpuRecoveryRequired(),
                    "Complete CPU publication must close the forced recovery state");
            GpuTerrainOutputStore.Residency retired = area.getGpuTerrainOutputResidency(
                    section.xOffset, section.yOffset, section.zOffset, type);
            require(retired != null && !retired.valid()
                            && retired.generation() == recoveryGeneration,
                    "Complete CPU publication must retire the retained old GPU generation");
            require(batch.update(buffers, area, type, true),
                    "Complete CPU recovery must rebuild away from the old APPEND pair");
            require(batch.drawCount == 1 && batch.gpuDrawCount == 0
                            && batch.commands.getByteBuffer().getInt(0) == 30
                            && batch.commands.getByteBuffer().getInt(12) == 40,
                    "CPU recovery must expose one complete CPU command, never the old partial exceptions");

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_TRANSITION_OK: partial fresh handoff hidden until GPU publication, dirty GPU-first generation retained atomically, complete CPU recovery retires old GPU output");
        } finally {
            if(batch.commands != null)
                batch.commands.freeBuffer();
            area.releaseBuffers();
            RegionVoxelStore.ENABLED = voxelStoreEnabled;
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
