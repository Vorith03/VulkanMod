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
    private static final boolean GPU_INDIRECT_CPU_BYPASS = Boolean.getBoolean(
            "vulkanmod.experimentalGpuIndirectCpuBypass");
    private static final boolean GPU_TERRAIN_DRAW_HANDOFF = Boolean.getBoolean(
            "vulkanmod.experimentalGpuTerrainDrawHandoff");
    private static final int MAX_DRAW_COMMANDS = MAX_SECTIONS * 2;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static boolean loggedGpuIndirectDraw;
    private static boolean loggedGpuTerrainDrawHandoff;
    private static boolean loggedGpuCommandOwner;

    private FrameBatch[][] batches;
    private final boolean[] candidateSupersetComplete = new boolean[TerrainRenderType.VALUES.length];
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

        // The candidate stream is required input metadata, not a duplicate CPU
        // command list. Only build CPU commands after GPU ownership is rejected.
        compareLiveCandidates(area, type);
        publishLiveCandidates(buffers, area, type);
        GpuSectionSelectionShadowStore shadowStore = dispatchShadowCandidates(area, type, frames);
        GpuRegionCandidateTable candidateTable = candidateTables[type.ordinal()];
        VFrustum currentFrustum = VFrustum.currentGpuSelectionFrustum();
        boolean volumeCurrent = currentFrustum != null
                && currentFrustum.sameCullingVolume(candidateFrustums[type.ordinal()]);
        boolean outputCurrent = shadowStore != null && candidateTable != null
                && shadowStore.isValidFor(candidateTable.generation(),
                        area.position.x, area.position.y, area.position.z);
        int rejection = gpuCommandOwnerRejection(
                GPU_INDIRECT_CPU_BYPASS && GPU_INDIRECT_DRAW, GPU_TERRAIN_DRAW_HANDOFF,
                candidateSupersetComplete[type.ordinal()], volumeCurrent, outputCurrent,
                candidateTable == null ? 0 : candidateTable.candidateCount(),
                shadowStore == null ? 0 : shadowStore.commandCapacity());
        boolean gpuOwnsCommands = prepareCommandOwner(batch, buffers, area, type,
                rejection, GPU_TERRAIN_DRAW_HANDOFF);
        if(GPU_INDIRECT_CPU_BYPASS) {
            RegionBatchStats.gpuCommandOwnerAttempts++;
            RegionBatchStats.gpuCommandOwnerReasons[rejection]++;
            if(gpuOwnsCommands) RegionBatchStats.gpuCommandCandidateSlots += candidateTable.candidateCount();
        }
        if(!gpuOwnsCommands) {
            // This frame slot's fence has completed; fallback is rebuilt lazily
            // from current revisions, never consumed from a skipped stale batch.
            if(batch.maxGpuVertexCount > 0) {
                Renderer.getDrawer().getQuadsIndexBuffer().checkCapacity(batch.maxGpuVertexCount);
                Renderer.getDrawer().bindAutoIndexBuffer(Renderer.getCommandBuffer(), 7);
                logGpuTerrainDrawHandoff(area, type, batch.gpuDrawCount, batch.drawCount);
            }
            if(batch.drawCount == 0) return;
            RegionBatchStats.sections += batch.sectionCount;
        }
        boolean useGpuIndirect = gpuOwnsCommands
                || (volumeCurrent && candidateSupersetComplete[type.ordinal()]
                        && canUseGpuIndirectDraw(area, shadowStore, candidateTable, batch));
        long indirectBuffer = useGpuIndirect ? shadowStore.output().getId() : batch.commands.getId();
        long indirectOffset = useGpuIndirect ? shadowStore.commandOffsetBytes() : 0L;
        int indirectCount = useGpuIndirect ? candidateTable.candidateCount() : batch.drawCount;
        if(gpuOwnsCommands && !loggedGpuCommandOwner) {
            loggedGpuCommandOwner = true;
            Initializer.LOGGER.info("VULKANMOD_GPU_COMMAND_OWNERSHIP_ACTIVE: generation={} slots={}; CPU command construction/upload bypassed, graph and candidate metadata retained",
                    candidateTable.generation(), indirectCount);
        }
        if(useGpuIndirect && !gpuOwnsCommands)
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

    /** 0=GPU owner; other values are precise fail-closed CPU fallback reasons. */
    static int gpuCommandOwnerRejection(boolean enabled, boolean hybridHandoff,
                                       boolean complete, boolean currentVolume, boolean currentOutput,
                                       int candidates, int capacity) {
        if(!enabled) return 1;
        if(hybridHandoff) return 2; // candidate v1 does not encode atomic APPEND pairs
        if(!complete) return 3;
        if(!currentVolume) return 4;
        if(!currentOutput) return 5;
        if(candidates <= 0 || candidates > MAX_SECTIONS || candidates > capacity) return 6;
        return 0;
    }

    /** Shared by the production draw and native cache/ownership transition oracle. */
    static boolean prepareCommandOwner(FrameBatch batch, DrawBuffers buffers, ChunkArea area,
                                       TerrainRenderType type, int rejection, boolean hybridHandoff) {
        if(rejection == 0) return true;
        batch.update(buffers, area, type, hybridHandoff);
        return false;
    }

    private static boolean canUseGpuIndirectDraw(ChunkArea area,
                                                  GpuSectionSelectionShadowStore store,
                                                  GpuRegionCandidateTable table,
                                                  FrameBatch cpuBatch) {
        if(store == null || table == null || cpuBatch == null || cpuBatch.gpuDrawCount != 0)
            return false;
        boolean outputValid = store.isValidFor(table.generation(),
                area.position.x, area.position.y, area.position.z);
        return isGpuIndirectPlanSafe(GPU_INDIRECT_DRAW, outputValid,
                table.candidateCount(), cpuBatch.drawCount, store.commandCapacity(), GPU_TERRAIN_DRAW_HANDOFF);
    }

    static boolean isGpuIndirectPlanSafe(boolean enabled, boolean outputValid,
                                         int candidateCount, int cpuDrawCount,
                                         int capacity) {
        return isGpuIndirectPlanSafe(enabled, outputValid, candidateCount, cpuDrawCount, capacity, false);
    }

    static boolean isGpuIndirectPlanSafe(boolean enabled, boolean outputValid,
                                         int candidateCount, int cpuDrawCount,
                                         int capacity, boolean terrainHandoff) {
        // Candidate ABI v1 only describes ordinary complete CPU meshes. Even
        // when this batch has zero published GPU draws, a pending APPEND section
        // may already have CPU exception parameters. Never draw that half alone.
        return !terrainHandoff && enabled && outputValid && cpuDrawCount > 0 && capacity > 0
                && candidateCount >= cpuDrawCount && candidateCount <= capacity;
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

    private static synchronized void logGpuTerrainDrawHandoff(ChunkArea area,
                                                               TerrainRenderType type,
                                                               int gpuDrawCount,
                                                               int totalDrawCount) {
        if(loggedGpuTerrainDrawHandoff)
            return;
        loggedGpuTerrainDrawHandoff = true;
        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_DRAW_HANDOFF_ACTIVE: region=({}, {}, {}) layer={} gpuDraws={} totalDraws={}; CPU fallback remains available when a CPU mesh exists",
                area.position.x, area.position.y, area.position.z, type.ordinal(),
                gpuDrawCount, totalDrawCount);
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
     * default. Direct traversal seeds are already authoritative CPU queue entries,
     * so DIRECT_SEED preserves that provenance and exempts only those candidates
     * from the GPU frustum predicate.
     * The separate indirect-draw gate may consume only a successfully
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

        boolean[] directSeeds = new boolean[MAX_SECTIONS];
        boolean complete = area.sectionQueue.size() <= MAX_SECTIONS;
        var visibleIterator = area.sectionQueue.iterator(false);
        while(visibleIterator.hasNext()) {
            RenderSection visible = visibleIterator.next();
            int packed = packSection(visible.xOffset - area.position.x,
                    visible.yOffset - area.position.y, visible.zOffset - area.position.z);
            if(area.getOwnedSection(packed) != visible) complete = false;
            if(!visible.hasMainDirection()) directSeeds[packed] = true;
        }

        int count = 0;
        for(int slot = 0; slot < MAX_SECTIONS; ++slot) {
            RenderSection section = area.getOwnedSection(slot);
            if(section == null)
                continue;
            DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
            boolean ready = parameters.indexCount != 0
                    && parameters.vertexBufferSegment.isReady();
            boolean graphVisible = area.isGraphVisible(section);
            boolean directSeed = directSeeds[slot];
            int flags = GpuRegionCandidateTable.flags(ready, graphVisible, directSeed, layer);
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
        fingerprint = mix(fingerprint, complete ? 1 : 0);
        VFrustum currentFrustum = VFrustum.currentGpuSelectionFrustum();
        boolean sameVolume = currentFrustum == null ? candidateFrustums[layer] == null
                : currentFrustum.sameCullingVolume(candidateFrustums[layer]);
        if(candidateInitialized[layer] && candidateFingerprints[layer] == fingerprint && sameVolume)
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
        VFrustum frustumSnapshot = currentFrustum == null ? null : currentFrustum.snapshot();
        GpuRegionCandidateTable table = builder.finish();
        long diagnosticToken = frustumSnapshot == null
                ? 0L : GpuLiveSectionSelectionDiagnostic.claim();
        boolean[] cpuExpected = diagnosticToken == 0L ? null : buildCpuExpected(area, type);
        boolean queued = area.publishGpuCandidates(type, table);

        // A rejected upload is transient (for example, the global candidate
        // budget may be released when another area retires). Leave this
        // fingerprint uncommitted so an unchanged CPU queue can retry later.
        candidateSupersetComplete[layer] = queued && complete;
        candidateInitialized[layer] = queued;
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
            candidateSupersetComplete[layer] = false;
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
        boolean gpuTerrainHandoff;
        int drawCount;
        int sectionCount;
        int gpuDrawCount;
        int maxGpuVertexCount;

        boolean update(DrawBuffers buffers, ChunkArea area, TerrainRenderType type) {
            return update(buffers, area, type, GPU_TERRAIN_DRAW_HANDOFF);
        }

        boolean update(DrawBuffers buffers, ChunkArea area, TerrainRenderType type,
                       boolean gpuTerrainHandoff) {
            long currentVisibilityRevision = area.getVisibilityRevision();
            long currentMeshRevision = buffers.getMeshRevision(type);
            if (visibilityRevision == currentVisibilityRevision
                    && meshRevision == currentMeshRevision
                    && this.gpuTerrainHandoff == gpuTerrainHandoff
                    && !pendingUploads) return false;
            rebuild(buffers, area, type, currentVisibilityRevision, currentMeshRevision,
                    gpuTerrainHandoff);
            return true;
        }

        void rebuild(DrawBuffers buffers, ChunkArea area, TerrainRenderType type,
                     long currentVisibilityRevision, long currentMeshRevision) {
            rebuild(buffers, area, type, currentVisibilityRevision, currentMeshRevision,
                    GPU_TERRAIN_DRAW_HANDOFF);
        }

        void rebuild(DrawBuffers buffers, ChunkArea area, TerrainRenderType type,
                     long currentVisibilityRevision, long currentMeshRevision,
                     boolean gpuTerrainHandoff) {
            if (area.sectionQueue.size() > MAX_SECTIONS) {
                throw new IllegalStateException("Region contains more than 512 sections");
            }
            pendingUploads = false;
            drawCount = 0;
            sectionCount = 0;
            gpuDrawCount = 0;
            maxGpuVertexCount = 0;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.malloc(area.sectionQueue.size() * 2 * STRIDE);
                var iterator = area.sectionQueue.iterator(false);
                while (iterator.hasNext()) {
                    RenderSection section = iterator.next();
                    DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);

                    // Default configuration is CPU-only. Keep that path allocation-free
                    // and lock-free instead of constructing GPU handoff state/plan records
                    // simply to rediscover the ordinary CPU command.
                    if(!gpuTerrainHandoff) {
                        if(parameters.indexCount == 0)
                            continue;
                        if(!parameters.vertexBufferSegment.isReady()) {
                            pendingUploads = true;
                            continue;
                        }
                        if(drawCount >= MAX_DRAW_COMMANDS)
                            throw new IllegalStateException("Region contains more than 1024 terrain draw commands");
                        int packedSection = packSection(section.xOffset - area.position.x,
                                section.yOffset - area.position.y,
                                section.zOffset - area.position.z);
                        putCommand(data, parameters.indexCount, parameters.firstIndex,
                                parameters.vertexOffset, packedSection);
                        drawCount++;
                        sectionCount++;
                        continue;
                    }

                    RenderSection.GpuTerrainDrawState drawState = section.gpuTerrainDrawState();
                    long generation = drawState.generation();
                    GpuTerrainOutputStore.Residency residency =
                            area.getGpuTerrainOutputResidency(section.xOffset, section.yOffset,
                                    section.zOffset, type);
                    GpuTerrainDrawHandoff.Ownership ownership = drawState.ownership();
                    GpuTerrainDrawHandoff.DrawPlan plan = GpuTerrainDrawHandoff.plan(
                            true, type, generation, residency,
                            parameters.indexCount, parameters.firstIndex, parameters.vertexOffset,
                            ownership);

                    boolean requiresCpuReady = false;
                    for(int commandIndex = 0; commandIndex < plan.commandCount(); ++commandIndex) {
                        GpuTerrainDrawHandoff.DrawCommand command = plan.command(commandIndex);
                        if(!command.gpuResident() && command.indexCount() > 0) {
                            requiresCpuReady = true;
                            break;
                        }
                    }
                    // A GPU-first CPU mesh may be partial (APPEND) or absent
                    // (fresh REPLACE). Never expose that CPU side by itself while its
                    // matching GPU handoff is missing. pendingUploads deliberately
                    // keeps this frame batch retryable until GPU publication or a
                    // complete CPU recovery changes the mesh revision/state.
                    if(!section.gpuTerrainCpuMeshComplete() && plan.gpuDrawCount() == 0) {
                        pendingUploads = true;
                        continue;
                    }

                    // APPEND is atomic at frame recording: never record the GPU half
                    // without its CPU exception half merely because the CPU upload is
                    // still pending. REPLACE/GPU-only output does not depend on CPU
                    // readiness and retains the established low-latency path.
                    if(requiresCpuReady && !parameters.vertexBufferSegment.isReady()) {
                        pendingUploads = true;
                        continue;
                    }

                    int drawCountBeforeSection = drawCount;
                    int packedSection = packSection(section.xOffset - area.position.x,
                            section.yOffset - area.position.y,
                            section.zOffset - area.position.z);
                    for(int commandIndex = 0; commandIndex < plan.commandCount(); ++commandIndex) {
                        GpuTerrainDrawHandoff.DrawCommand command = plan.command(commandIndex);
                        if(command.indexCount() <= 0)
                            continue;
                        if(command.gpuResident()) {
                            gpuDrawCount++;
                            maxGpuVertexCount = Math.max(maxGpuVertexCount,
                                    command.indexCount() * 2 / 3);
                        }
                        if(drawCount >= MAX_DRAW_COMMANDS)
                            throw new IllegalStateException("Region contains more than 1024 terrain draw commands");
                        putCommand(data, command.indexCount(), command.firstIndex(),
                                command.vertexOffset(), packedSection);
                        drawCount++;
                    }
                    if(drawCount > drawCountBeforeSection)
                        sectionCount++;
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
            this.gpuTerrainHandoff = gpuTerrainHandoff;
        }
    }

    static final class RegionCommands extends IndirectBuffer {
        RegionCommands() {
            super(MAX_DRAW_COMMANDS * STRIDE, MemoryTypes.HOST_MEM);
        }
    }
}
