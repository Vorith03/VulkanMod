package net.vulkanmod.render.chunk;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.voxel.GpuLiveSectionSelectionDiagnostic;
import net.vulkanmod.render.chunk.voxel.GpuRegionCandidateGpuStore;
import net.vulkanmod.render.chunk.voxel.GpuRegionCandidateTable;
import net.vulkanmod.render.chunk.voxel.GpuSectionSelectionShadowStore;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static net.vulkanmod.render.chunk.RegionBatchLayout.*;
import static org.lwjgl.vulkan.VK10.*;

/** Opaque commands survive camera movement; each frame owns its writable copy. */
final class RegionDrawBatch {
    private static final boolean LIVE_GPU_SECTION_SELECTION = Boolean.getBoolean(
            "vulkanmod.experimentalGpuSectionSelection")
            || GpuSectionSelectionShadowStore.enabled();
    private static final boolean GPU_INDIRECT_DRAW = Boolean.getBoolean(
            "vulkanmod.experimentalGpuIndirectDraw")
            && GpuSectionSelectionShadowStore.enabled();
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static boolean loggedGpuIndirectDraw;

    private FrameBatch[][] batches;
    private final boolean[] candidateInitialized = new boolean[TerrainRenderType.VALUES.length];
    private final long[] candidateFingerprints = new long[TerrainRenderType.VALUES.length];
    private final long[] candidateGenerations = new long[TerrainRenderType.VALUES.length];
    private final GpuRegionCandidateTable[] candidateTables =
            new GpuRegionCandidateTable[TerrainRenderType.VALUES.length];
    private final VFrustum[] candidateFrustums =
            new VFrustum[TerrainRenderType.VALUES.length];
    private final GpuRegionCandidateTable[] diagnosticTables =
            new GpuRegionCandidateTable[TerrainRenderType.VALUES.length];
    private final VFrustum[] diagnosticFrustums =
            new VFrustum[TerrainRenderType.VALUES.length];
    private final boolean[][] diagnosticExpected =
            new boolean[TerrainRenderType.VALUES.length][];
    private final long[] diagnosticTokens = new long[TerrainRenderType.VALUES.length];
    private final GpuSectionSelectionShadowStore[] shadowStores =
            new GpuSectionSelectionShadowStore[TerrainRenderType.VALUES.length];
    private final boolean[] shadowStoreAttempted = new boolean[TerrainRenderType.VALUES.length];

    void draw(DrawBuffers buffers, ChunkArea area, Pipeline pipeline, RenderType renderType,
              double camX, double camY, double camZ) {
        int frames = Renderer.getFramesNum();
        if (batches == null || batches[0].length != frames) {
            free();
            batches = new FrameBatch[TerrainRenderType.VALUES.length][frames];
        }
        TerrainRenderType type = TerrainRenderType.get(renderType);
        int frame = Renderer.getCurrentFrame();
        FrameBatch batch = batches[type.ordinal()][frame];
        if (batch == null) batches[type.ordinal()][frame] = batch = new FrameBatch();

        // Renderer.beginFrame has waited this frame's fence. Other frames' command
        // buffers remain untouched, even during edits or visibility changes.
        batch.update(buffers, area, type);
        compareLiveCandidates(area, type);
        publishLiveCandidates(buffers, area, type);
        GpuSectionSelectionShadowStore shadowStore = dispatchShadowCandidates(area, type, frames);
        if (batch.drawCount == 0) return;
        RegionBatchStats.sections += batch.drawCount;

        GpuRegionCandidateTable candidateTable = candidateTables[type.ordinal()];
        boolean useGpuIndirect = canUseGpuIndirectDraw(area, shadowStore, candidateTable, batch);
        long indirectBuffer = useGpuIndirect ? shadowStore.output().getId() : batch.commands.getId();
        long indirectOffset = useGpuIndirect ? shadowStore.commandOffsetBytes() : 0L;
        int indirectCount = useGpuIndirect ? candidateTable.candidateCount() : batch.drawCount;
        if(useGpuIndirect)
            logGpuIndirectDraw(area, type, candidateTable, batch.drawCount, indirectCount);

        type.setCutoutUniform();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var commandBuffer = Renderer.getCommandBuffer();
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(buffers.vertexBuffer.getId()), stack.longs(0));
            // Subtract in double precision before conversion, preserving precision
            // in distant and negative-coordinate regions.
            vkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0,
                    stack.floats((float) (area.position.x - camX), (float) (area.position.y - camY),
                            (float) (area.position.z - camZ)));
            pipeline.bindDescriptorSets(commandBuffer, frame);
            int limit = drawLimit(Device.deviceProperties.limits().maxDrawIndirectCount());
            for (int first = 0; first < indirectCount; first += limit) {
                RegionBatchStats.calls++;
                vkCmdDrawIndexedIndirect(commandBuffer, indirectBuffer,
                        indirectOffset + (long) first * STRIDE,
                        Math.min(limit, indirectCount - first), STRIDE);
            }
        }
    }

    private static boolean canUseGpuIndirectDraw(ChunkArea area,
                                                  GpuSectionSelectionShadowStore store,
                                                  GpuRegionCandidateTable table,
                                                  FrameBatch cpuBatch) {
        if(!GPU_INDIRECT_DRAW || store == null || table == null || cpuBatch == null)
            return false;
        if(table.candidateCount() < cpuBatch.drawCount
                || table.candidateCount() > store.commandCapacity())
            return false;
        return store.isValidFor(table.generation(), area.position.x, area.position.y, area.position.z);
    }

    private static synchronized void logGpuIndirectDraw(ChunkArea area, TerrainRenderType type,
                                                        GpuRegionCandidateTable table,
                                                        int cpuDrawCount, int issuedCommands) {
        if(loggedGpuIndirectDraw)
            return;
        loggedGpuIndirectDraw = true;
        Initializer.LOGGER.info(
                "VULKANMOD_GPU_INDIRECT_DRAW_ACTIVE: region=({}, {}, {}) layer={} generation={} cpuDraws={} boundedCommands={}; CPU batch remains built as fallback",
                area.position.x, area.position.y, area.position.z, type.ordinal(), table.generation(),
                cpuDrawCount, issuedCommands);
    }

    private void compareLiveCandidates(ChunkArea area, TerrainRenderType type) {
        int layer = type.ordinal();
        GpuRegionCandidateTable table = diagnosticTables[layer];
        VFrustum frustum = diagnosticFrustums[layer];
        boolean[] cpuExpected = diagnosticExpected[layer];
        long token = diagnosticTokens[layer];
        if(table == null || frustum == null || cpuExpected == null || token == 0L)
            return;
        if(!GpuLiveSectionSelectionDiagnostic.isCurrent(token)) {
            diagnosticTables[layer] = null;
            diagnosticFrustums[layer] = null;
            diagnosticExpected[layer] = null;
            diagnosticTokens[layer] = 0L;
            return;
        }
        GpuRegionCandidateGpuStore.Residency residency = area.getGpuCandidateResidency(type);
        if(residency == null || !residency.valid()
                || residency.generation() != table.generation())
            return;
        if(GpuLiveSectionSelectionDiagnostic.compare(
                token, residency, table, layer, frustum, cpuExpected)) {
            diagnosticTables[layer] = null;
            diagnosticFrustums[layer] = null;
            diagnosticExpected[layer] = null;
            diagnosticTokens[layer] = 0L;
        }
    }

    /**
     * Generate persistent GPU indirect commands in shadow mode. The helper compute
     * submission is outside the active render pass. When the separate production
     * draw gate is disabled, the CPU FrameBatch below remains the only consumer.
     */
    private GpuSectionSelectionShadowStore dispatchShadowCandidates(ChunkArea area,
                                                                     TerrainRenderType type,
                                                                     int frames) {
        if(!GpuSectionSelectionShadowStore.enabled())
            return null;
        int layer = type.ordinal();
        GpuRegionCandidateTable table = candidateTables[layer];
        VFrustum frustum = candidateFrustums[layer];
        if(table == null || frustum == null)
            return null;
        GpuRegionCandidateGpuStore.Residency residency = area.getGpuCandidateResidency(type);
        if(residency == null || !residency.valid()
                || residency.generation() != table.generation())
            return null;

        GpuSectionSelectionShadowStore store = shadowStores[layer];
        if(store == null && !shadowStoreAttempted[layer]) {
            shadowStoreAttempted[layer] = true;
            store = GpuSectionSelectionShadowStore.tryCreate(frames);
            shadowStores[layer] = store;
        }
        return store != null && store.dispatch(residency, table, layer, frustum) ? store : null;
    }

    /**
     * Diagnostic-only bridge from the full live region section set to the
     * generation-owned GPU candidate ABI. GRAPH_VISIBLE is the CPU graph/smart-cull
     * result before the frustum check; the GPU probe therefore owns the frustum
     * decision over a true superset while production rendering stays CPU-driven by
     * default. The separate indirect-draw gate may consume only a successfully
     * dispatched, generation-matched shadow result.
     */
    private void publishLiveCandidates(DrawBuffers buffers, ChunkArea area, TerrainRenderType type) {
        if(!LIVE_GPU_SECTION_SELECTION)
            return;

        int layer = type.ordinal();
        long generation = candidateGenerations[layer] + 1L;
        GpuRegionCandidateTable.Builder builder = new GpuRegionCandidateTable.Builder(
                generation, area.position.x, area.position.y, area.position.z);
        long fingerprint = FNV_OFFSET_BASIS;
        fingerprint = mix(fingerprint, area.getVisibilityRevision());
        fingerprint = mix(fingerprint, buffers.getMeshRevision(type));
        fingerprint = mix(fingerprint, area.position.x);
        fingerprint = mix(fingerprint, area.position.y);
        fingerprint = mix(fingerprint, area.position.z);
        fingerprint = mix(fingerprint, layer);

        int count = 0;
        for(int slot = 0; slot < MAX_SECTIONS; ++slot) {
            RenderSection section = area.getOwnedSection(slot);
            if(section == null)
                continue;
            DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
            boolean ready = parameters.indexCount != 0
                    && parameters.vertexBufferSegment.isReady();
            boolean graphVisible = area.isGraphVisible(section);
            int flags = GpuRegionCandidateTable.flags(ready, graphVisible, layer);
            builder.add(parameters.indexCount, 1, parameters.firstIndex,
                    parameters.vertexOffset, slot, flags);

            fingerprint = mix(fingerprint, parameters.indexCount);
            fingerprint = mix(fingerprint, parameters.firstIndex);
            fingerprint = mix(fingerprint, parameters.vertexOffset);
            fingerprint = mix(fingerprint, slot);
            fingerprint = mix(fingerprint, flags);
            count++;
        }
        fingerprint = mix(fingerprint, count);

        if(candidateInitialized[layer] && candidateFingerprints[layer] == fingerprint)
            return;

        long oldToken = diagnosticTokens[layer];
        if(oldToken != 0L) {
            GpuLiveSectionSelectionDiagnostic.cancel(oldToken);
            diagnosticTokens[layer] = 0L;
            diagnosticTables[layer] = null;
            diagnosticFrustums[layer] = null;
            diagnosticExpected[layer] = null;
        }

        // The CPU queue, graph-visible flags and frustum are one traversal snapshot.
        // Candidate residency is asynchronous, so freeze the frustum now rather than
        // comparing or dispatching this generation against a later camera state.
        VFrustum currentFrustum = VFrustum.currentGpuSelectionFrustum();
        VFrustum frustumSnapshot = currentFrustum == null ? null : currentFrustum.snapshot();
        GpuRegionCandidateTable table = builder.finish();
        long diagnosticToken = frustumSnapshot == null
                ? 0L : GpuLiveSectionSelectionDiagnostic.claim();
        boolean[] cpuExpected = diagnosticToken == 0L ? null : buildCpuExpected(area, type);
        boolean queued = area.publishGpuCandidates(type, table);

        candidateInitialized[layer] = true;
        candidateFingerprints[layer] = fingerprint;
        candidateGenerations[layer] = generation;
        candidateTables[layer] = queued ? table : null;
        candidateFrustums[layer] = queued ? frustumSnapshot : null;

        if(diagnosticToken != 0L) {
            if(queued) {
                diagnosticTokens[layer] = diagnosticToken;
                diagnosticTables[layer] = table;
                diagnosticFrustums[layer] = frustumSnapshot;
                diagnosticExpected[layer] = cpuExpected;
            } else {
                GpuLiveSectionSelectionDiagnostic.cancel(diagnosticToken);
            }
        }
    }

    private static boolean[] buildCpuExpected(ChunkArea area, TerrainRenderType type) {
        boolean[] expected = new boolean[MAX_SECTIONS];
        var iterator = area.sectionQueue.iterator(false);
        while(iterator.hasNext()) {
            RenderSection section = iterator.next();
            DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
            if(parameters.indexCount == 0 || !parameters.vertexBufferSegment.isReady())
                continue;
            int packed = packSection(section.xOffset - area.position.x,
                    section.yOffset - area.position.y, section.zOffset - area.position.z);
            expected[packed] = true;
        }
        return expected;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * FNV_PRIME;
    }

    void free() {
        if (batches != null) {
            for (FrameBatch[] layer : batches) {
                for (FrameBatch batch : layer) {
                    if (batch != null && batch.commands != null) batch.commands.freeBuffer();
                }
            }
            batches = null;
        }
        for(int layer = 0; layer < candidateInitialized.length; ++layer) {
            candidateInitialized[layer] = false;
            candidateFingerprints[layer] = 0L;
            candidateTables[layer] = null;
            candidateFrustums[layer] = null;
            if(diagnosticTokens[layer] != 0L)
                GpuLiveSectionSelectionDiagnostic.cancel(diagnosticTokens[layer]);
            diagnosticTokens[layer] = 0L;
            diagnosticTables[layer] = null;
            diagnosticFrustums[layer] = null;
            diagnosticExpected[layer] = null;
            if(shadowStores[layer] != null) {
                shadowStores[layer].close();
                shadowStores[layer] = null;
            }
            shadowStoreAttempted[layer] = false;
        }
    }

    static final class FrameBatch {
        RegionCommands commands;
        long visibilityRevision = -1;
        long meshRevision = -1;
        boolean pendingUploads;
        int drawCount;

        boolean update(DrawBuffers buffers, ChunkArea area, TerrainRenderType type) {
            long currentVisibilityRevision = area.getVisibilityRevision();
            long currentMeshRevision = buffers.getMeshRevision(type);
            if (visibilityRevision == currentVisibilityRevision && meshRevision == currentMeshRevision
                    && !pendingUploads) return false;
            rebuild(buffers, area, type, currentVisibilityRevision, currentMeshRevision);
            return true;
        }

        void rebuild(DrawBuffers buffers, ChunkArea area, TerrainRenderType type,
                     long currentVisibilityRevision, long currentMeshRevision) {
            if (area.sectionQueue.size() > MAX_SECTIONS) {
                throw new IllegalStateException("Region contains more than 512 sections");
            }
            pendingUploads = false;
            drawCount = 0;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.malloc(area.sectionQueue.size() * STRIDE);
                var iterator = area.sectionQueue.iterator(false);
                while (iterator.hasNext()) {
                    RenderSection section = iterator.next();
                    DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
                    if (parameters.indexCount == 0) continue;
                    // Do not cache the absence of a new upload indefinitely. Retry
                    // until AreaUploadManager has observed its completion fence.
                    if (!parameters.vertexBufferSegment.isReady()) {
                        pendingUploads = true;
                        continue;
                    }
                    putCommand(data, parameters.indexCount, parameters.firstIndex, parameters.vertexOffset,
                            packSection(section.xOffset - area.position.x, section.yOffset - area.position.y,
                                    section.zOffset - area.position.z));
                    drawCount++;
                }
                if (drawCount != 0) {
                    if (commands == null) commands = new RegionCommands();
                    data.flip();
                    commands.reset();
                    commands.recordCopyCmd(data);
                    RegionBatchStats.commandUploads++;
                    RegionBatchStats.commandBytes += drawCount * STRIDE;
                }
            }
            visibilityRevision = currentVisibilityRevision;
            meshRevision = currentMeshRevision;
        }
    }

    static final class RegionCommands extends IndirectBuffer {
        RegionCommands() {
            super(MAX_SECTIONS * STRIDE, MemoryTypes.HOST_MEM);
        }
    }
}
