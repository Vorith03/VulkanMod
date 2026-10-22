package net.vulkanmod.render.chunk;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.ImmersivePortalsLevelRendererCompat;
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
    static boolean trackCandidateProvenance() {
        return LIVE_GPU_SECTION_SELECTION;
    }

    private static final boolean GPU_INDIRECT_DRAW = Boolean.getBoolean(
            "vulkanmod.experimentalGpuIndirectDraw")
            && GpuSectionSelectionShadowStore.enabled();
    private static final boolean GPU_INDIRECT_CPU_BYPASS = Boolean.getBoolean(
            "vulkanmod.experimentalGpuIndirectCpuBypass");
    private static final boolean GPU_TERRAIN_DRAW_HANDOFF = Boolean.getBoolean(
            "vulkanmod.experimentalGpuTerrainDrawHandoff");
    private static final boolean GPU_GENERATED_INDIRECT_REPLACE = Boolean.getBoolean(
            "vulkanmod.experimentalGpuGeneratedIndirectReplace");
    private static final boolean GPU_GENERATED_INDIRECT_APPEND = GPU_GENERATED_INDIRECT_REPLACE
            && Boolean.getBoolean("vulkanmod.experimentalGpuGeneratedIndirectAppend");
    private static final boolean PROFILE_GPU_CANDIDATES =
            Boolean.getBoolean("vulkanmod.performanceProfiler");
    // Optional actual-client-world recording witness, never enabled by the
    // generated indirect experiments themselves. This does NOT prove pixels.
    private static final boolean DEBUG_GENERATED_WORLD_DRAW =
            Boolean.getBoolean("vulkanmod.debugGpuGeneratedWorldDraw");
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static boolean loggedGpuIndirectDraw;
    private static boolean loggedGpuTerrainDrawHandoff;
    private static boolean loggedGpuCommandOwner;
    // Bit 0=REPLACE, 1=APPEND pair, 2=APPEND GPU-only; portal view uses bits 3..5.
    private static int witnessedGeneratedWorldModes;

    private FrameBatch[][] batches;
    private enum ProducerMode { ORDINARY, GENERATED_REPLACE, GENERATED_APPEND }

    /** One lifetime allocation per layer, never per draw or candidate. */
    private static final class CandidateCache {
        boolean complete;
        int maxGpuVertices;
        int generatedReplace;
        int generatedAppendPairs;
        int generatedAppendGpuOnly;
        boolean initialized;
        long fingerprint;
        long generation;
        long inputRevision;
        long ownershipRevision;
        long meshRevision;
        long generatedRevision;
        ProducerMode producerMode = ProducerMode.ORDINARY;
        long visibilityRevision;
        GpuRegionCandidateTable table;
        VFrustum frustum;
        final long[] graphVisits = new long[MAX_SECTIONS / Long.SIZE];
        final long[] directSeeds = new long[MAX_SECTIONS / Long.SIZE];

        void invalidate() {
            initialized = false;
            complete = false;
            generatedReplace = generatedAppendPairs = generatedAppendGpuOnly = 0;
            table = null;
            frustum = null;
        }
    }

    private final CandidateCache[] candidateCaches = createCandidateCaches();

    private static CandidateCache[] createCandidateCaches() {
        CandidateCache[] caches = new CandidateCache[TerrainRenderType.VALUES.length];
        for (int i = 0; i < caches.length; i++) caches[i] = new CandidateCache();
        return caches;
    }
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
    private final long[] shadowStoreFailureRevision = new long[TerrainRenderType.VALUES.length];

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
        GpuSectionSelectionShadowStore shadowStore = dispatchShadowCandidates(buffers, area, type, frames);
        GpuRegionCandidateTable candidateTable = candidateCaches[type.ordinal()].table;
        VFrustum currentFrustum = VFrustum.currentGpuSelectionFrustum();
        boolean volumeCurrent = currentFrustum != null
                && currentFrustum.sameCullingVolume(candidateCaches[type.ordinal()].frustum);
        boolean outputCurrent = shadowStore != null && candidateTable != null
                && shadowStore.isValidFor(candidateTable.generation(),
                        area.position.x, area.position.y, area.position.z);
        int rejection = gpuCommandOwnerRejection(
                GPU_INDIRECT_CPU_BYPASS && GPU_INDIRECT_DRAW,
                GPU_TERRAIN_DRAW_HANDOFF && !GPU_GENERATED_INDIRECT_REPLACE,
                candidateCaches[type.ordinal()].complete, volumeCurrent, outputCurrent,
                candidateTable == null ? 0 : candidateTable.candidateCount(),
                shadowStore == null ? 0 : shadowStore.commandCapacity());
        boolean gpuOwnsCommands = prepareCommandOwner(batch, buffers, area, type,
                rejection, GPU_TERRAIN_DRAW_HANDOFF,
                GPU_TERRAIN_DRAW_HANDOFF && GPU_GENERATED_INDIRECT_REPLACE);
        if(GPU_INDIRECT_CPU_BYPASS) {
            RegionBatchStats.gpuCommandOwnerAttempts++;
            RegionBatchStats.gpuCommandOwnerReasons[rejection]++;
            if(gpuOwnsCommands) RegionBatchStats.gpuCommandCandidateSlots += candidateTable.candidateCount();
        }
        if(gpuOwnsCommands && candidateCaches[type.ordinal()].maxGpuVertices > 0) {
            // Generated quads share the area's vertex buffer but use auto indices.
            // This capacity check must also run when FrameBatch is bypassed.
            Renderer.getDrawer().getQuadsIndexBuffer().checkCapacity(candidateCaches[type.ordinal()].maxGpuVertices);
            Renderer.getDrawer().bindAutoIndexBuffer(Renderer.getCommandBuffer(), 7);
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
                || (volumeCurrent && candidateCaches[type.ordinal()].complete
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
            if(DEBUG_GENERATED_WORLD_DRAW && gpuOwnsCommands) {
                reportGeneratedWorldRecording(area, type, candidateTable, indirectCount);
            }
            if(TerrainDrawReadback.active())
                TerrainDrawReadback.request(area, type.ordinal(), indirectBuffer, indirectOffset,
                        indirectCount, useGpuIndirect, new TerrainDrawReadback.Revisions(
                                candidateTable == null ? Long.MIN_VALUE : candidateTable.generation(),
                                area.getVisibilityRevision(), buffers.getMeshRevision(type),
                                area.getGeneratedCandidateRevision(), area.getCandidateGeometryRevision(type),
                                indirectCount));
        }
    }

    // This reports a genuinely recorded live-world vkCmdDrawIndexedIndirect
    // consuming current GPU-generated candidates; frustum compaction may still
    // zero individual commands. It is NOT GPU count readback or raster/pixel proof.
    static int generatedWorldWitnessModes(boolean worldLoaded, boolean gpuOwner,
                                          boolean drawRecorded, int replaces,
                                          int appendPairs, int appendGpuOnly) {
        if(!worldLoaded || !gpuOwner || !drawRecorded) return 0;
        return (replaces > 0 ? 1 : 0) | (appendPairs > 0 ? 2 : 0)
                | (appendGpuOnly > 0 ? 4 : 0);
    }

    private void reportGeneratedWorldRecording(ChunkArea area, TerrainRenderType type,
                                               GpuRegionCandidateTable table, int indirectCount) {
        WorldRenderer world = WorldRenderer.getInstance();
        int layer = type.ordinal();
        int modes = generatedWorldWitnessModes(world != null && world.getLevel() != null,
                true, indirectCount > 0, candidateCaches[layer].generatedReplace,
                candidateCaches[layer].generatedAppendPairs, candidateCaches[layer].generatedAppendGpuOnly);
        if(modes == 0) return;
        boolean portalApi = ImmersivePortalsLevelRendererCompat.isAvailable();
        boolean portalView = portalApi
                && ImmersivePortalsLevelRendererCompat.shouldCancelWorldRendererReload();
        GpuLoadedWorldSmokeTest.recordGeneratedDraw(portalView);
        int shiftedModes = modes << (portalView ? 3 : 0);
        int newlyWitnessed;
        synchronized(RegionDrawBatch.class) {
            newlyWitnessed = shiftedModes & ~witnessedGeneratedWorldModes;
            witnessedGeneratedWorldModes |= newlyWitnessed;
        }
        if(newlyWitnessed == 0) return;
        Initializer.LOGGER.info(
                "VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED: region=({}, {}, {}) layer={} generation={} candidates={} replace={} appendPairs={} appendGpuOnly={} portalApi={} portalView={} newModeBits=0x{}; live-world Vulkan indirect recording only, NOT selected-command readback or pixel equivalence",
                area.position.x, area.position.y, area.position.z, layer, table.generation(),
                indirectCount, candidateCaches[layer].generatedReplace, candidateCaches[layer].generatedAppendPairs,
                candidateCaches[layer].generatedAppendGpuOnly, portalApi, portalView,
                Integer.toHexString(newlyWitnessed));
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
        if(candidates <= 0 || candidates > MAX_DRAW_COMMANDS || candidates > capacity) return 6;
        return 0;
    }

    /** Shared by the production draw and native cache/ownership transition oracle. */
    static boolean prepareCommandOwner(FrameBatch batch, DrawBuffers buffers, ChunkArea area,
                                       TerrainRenderType type, int rejection, boolean hybridHandoff) {
        return prepareCommandOwner(batch, buffers, area, type, rejection, hybridHandoff, false);
    }

    static boolean prepareCommandOwner(FrameBatch batch, DrawBuffers buffers, ChunkArea area,
                                       TerrainRenderType type, int rejection,
                                       boolean hybridHandoff, boolean generatedPlan) {
        if(rejection == 0) return true;
        if(generatedPlan) {
            // Generated ownership may change without an ordinary mesh or visibility
            // revision. Do not reuse a stale cached CPU FrameBatch after revocation.
            // An accepted GPU owner still skips this entire CPU rebuild.
            batch.rebuild(buffers, area, type, area.getVisibilityRevision(),
                    buffers.getMeshRevision(type), hybridHandoff);
        } else {
            batch.update(buffers, area, type, hybridHandoff);
        }
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
    private GpuSectionSelectionShadowStore dispatchShadowCandidates(DrawBuffers buffers, ChunkArea area,
                                                                     TerrainRenderType type,
                                                                     int frames) {
        if(!GpuSectionSelectionShadowStore.enabled())
            return null;
        int layer = type.ordinal();
        GpuRegionCandidateTable table = candidateCaches[layer].table;
        VFrustum frustum = candidateCaches[layer].frustum;
        if(table == null || frustum == null)
            return null;
        GpuRegionCandidateGpuStore.Residency residency = area.getGpuCandidateResidency(type);
        if(residency == null || !residency.valid()
                || residency.generation() != table.generation()) {
            // Changing main/portal views cannot wait for a later frame's upload
            // drain. Submit once, then revalidate proofs changed by callbacks.
            AreaUploadManager.INSTANCE.submitUploads();
            publishLiveCandidates(buffers, area, type);
            table = candidateCaches[layer].table;
            frustum = candidateCaches[layer].frustum;
            residency = area.getGpuCandidateResidency(type);
            if(table == null || frustum == null || residency == null || !residency.valid()
                    || residency.generation() != table.generation())
                return null;
        }

        GpuSectionSelectionShadowStore store = shadowStores[layer];
        if(store == null && (!shadowStoreAttempted[layer]
                || shadowStoreFailureRevision[layer] != GpuSectionSelectionShadowStore.retirementRevision())) {
            shadowStoreAttempted[layer] = true;
            store = GpuSectionSelectionShadowStore.tryCreate(frames);
            shadowStores[layer] = store;
            if(store == null)
                shadowStoreFailureRevision[layer] = GpuSectionSelectionShadowStore.retirementRevision();
        }
        return store == null ? null : store.dispatchView(residency, table, layer, frustum);
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
    // No candidate metadata scan/allocation for an unchanged owned region. A
    // traversal always changes inputRevision, because graph visits outside the
    // draw queue (and direct-seed provenance) are still candidate inputs.
    static boolean isCandidateSnapshotCurrent(boolean initialized, boolean pendingReadiness,
                                              long publishedInputs, long currentInputs,
                                              long publishedMesh, long currentMesh,
                                              long publishedVisibility, long currentVisibility,
                                              boolean sameVolume) {
        return initialized && !pendingReadiness && sameVolume
                && publishedInputs == currentInputs && publishedMesh == currentMesh
                && publishedVisibility == currentVisibility;
    }

    // Traversal revisions alone are not proof of changed GPU input. The
    // producer captures full graph provenance including off-frustum visits,
    // and direct seeds independently of queue order. Stable mesh/owner/readiness
    // and exact culling volume allow reusing the already published GPU table.
    static boolean canReuseCandidateProvenance(boolean initialized, boolean pendingReadiness,
                                               long publishedOwner, long currentOwner,
                                               long publishedMesh, long currentMesh,
                                               long publishedVisibility, long currentVisibility,
                                               boolean sameVolume, boolean provenanceMatches) {
        return initialized && !pendingReadiness && sameVolume && provenanceMatches
                && publishedOwner == currentOwner && publishedMesh == currentMesh
                && publishedVisibility == currentVisibility;
    }

    private void publishLiveCandidates(DrawBuffers buffers, ChunkArea area, TerrainRenderType type) {
        publishLiveCandidates(buffers, area, type,
                GPU_TERRAIN_DRAW_HANDOFF, GPU_GENERATED_INDIRECT_REPLACE,
                GPU_GENERATED_INDIRECT_APPEND);
    }

    void publishLiveCandidates(DrawBuffers buffers, ChunkArea area, TerrainRenderType type,
                               boolean terrainHandoff, boolean generatedReplace) {
        publishLiveCandidates(buffers, area, type, terrainHandoff, generatedReplace, false);
    }

    void publishLiveCandidates(DrawBuffers buffers, ChunkArea area, TerrainRenderType type,
                               boolean terrainHandoff, boolean generatedReplace,
                               boolean generatedAppend) {
        if(!LIVE_GPU_SECTION_SELECTION)
            return;
        boolean generatedPlan = terrainHandoff && generatedReplace;
        ProducerMode producerMode = !generatedPlan ? ProducerMode.ORDINARY
                : generatedAppend ? ProducerMode.GENERATED_APPEND : ProducerMode.GENERATED_REPLACE;
        long preparationStart = PROFILE_GPU_CANDIDATES ? System.nanoTime() : 0L;
        RegionBatchStats.gpuCandidateAttempts++;

        int layer = type.ordinal();
        // A bypass writer, duplicate producer slot, or stale queued ownership
        // invalidates ALL GPU command reuse before checking cached revisions.
        // The CPU FrameBatch remains the authoritative fallback.
        if(!area.candidateQueueCovered()) {
            candidateCaches[layer].invalidate();
            recordCandidatePreparation(preparationStart);
            return;
        }
        long inputRevision = area.getCandidateInputRevision();
        long visibilityRevision = area.getVisibilityRevision();
        // Both sources advance monotonically: mesh producer changes and segment
        // readiness transitions invalidate cached GPU input without active polling.
        long meshRevision = buffers.getMeshRevision(type) + area.getCandidateGeometryRevision(type);
        long generatedRevision = generatedPlan ? area.getGeneratedCandidateRevision() : 0L;
        boolean sameProducer = candidateCaches[layer].producerMode == producerMode
                && (!generatedPlan || candidateCaches[layer].generatedRevision == generatedRevision);
        VFrustum currentFrustum = VFrustum.currentGpuSelectionFrustum();
        boolean sameVolume = currentFrustum == null ? candidateCaches[layer].frustum == null
                : currentFrustum.sameCullingVolume(candidateCaches[layer].frustum);
        // Producer-owned generated-state invalidation complements ordinary mesh,
        // readiness, queue/graph provenance and exact portal culling volume.
        // A changed state can never reuse a previously complete APPEND pair.
        if(sameProducer && isCandidateSnapshotCurrent(candidateCaches[layer].initialized,
                false, candidateCaches[layer].inputRevision, inputRevision,
                candidateCaches[layer].meshRevision, meshRevision,
                candidateCaches[layer].visibilityRevision, visibilityRevision, sameVolume)) {
            RegionBatchStats.gpuCandidateDrawReuses++;
            recordCandidatePreparation(preparationStart);
            return;
        }
        long ownershipRevision = area.getCandidateOwnershipRevision();
        if(sameProducer && canReuseCandidateProvenance(candidateCaches[layer].initialized, false,
                candidateCaches[layer].ownershipRevision, ownershipRevision,
                candidateCaches[layer].meshRevision, meshRevision,
                candidateCaches[layer].visibilityRevision, visibilityRevision,
                sameVolume, area.matchesCandidateProvenance(
                        candidateCaches[layer].graphVisits, candidateCaches[layer].directSeeds))) {
            candidateCaches[layer].inputRevision = inputRevision;
            RegionBatchStats.gpuCandidateTraversalReuses++;
            recordCandidatePreparation(preparationStart);
            return;
        }
        RegionBatchStats.gpuCandidateRebuilds++;
        long generation = candidateCaches[layer].generation + 1L;
        GpuRegionCandidateTable.Builder builder = new GpuRegionCandidateTable.Builder(
                generation, area.position.x, area.position.y, area.position.z);
        long fingerprint = FNV_OFFSET_BASIS;
        fingerprint = mix(fingerprint, visibilityRevision);
        fingerprint = mix(fingerprint, meshRevision);
        fingerprint = mix(fingerprint, area.position.x);
        fingerprint = mix(fingerprint, area.position.y);
        fingerprint = mix(fingerprint, area.position.z);
        fingerprint = mix(fingerprint, layer);

        // Every producer-enqueued queue slot was checked at insertion, with
        // duplicate/foreign ownership and untracked queue writes failing closed.
        // Validate current owned identity again while walking active producer
        // bits: no independent O(queue length) CPU audit is required here.
        // GPU section compaction is order-independent across sections. Never
        // apply generated handoff ownership to sorted/unsupported terrain layers.
        boolean complete = !terrainHandoff || (generatedReplace
                && type != TerrainRenderType.TRANSLUCENT
                && type != TerrainRenderType.TRIPWIRE);
        int maxGpuVertices = 0;
        int recordedReplaceCount = 0;
        int generatedAppendPairs = 0;
        int generatedAppendGpuOnly = 0;

        // Iterate only the producer's graph-visited or direct-seeded slots.
        // Off-frustum graph stamps remain included, unlike the CPU draw queue.
        int count = 0;
        for(int word = 0; word < MAX_SECTIONS / Long.SIZE; ++word) {
            long active = area.candidateActiveWord(word);
            while(active != 0L) {
                int bit = Long.numberOfTrailingZeros(active);
                active &= active - 1L;
                int slot = (word << 6) + bit;
                RenderSection section = area.getOwnedSection(slot);
                if(section == null) {
                    complete = false; // stale producer bit/owner generation
                    continue;
                }
                IndirectCandidate candidate = generatedPlan
                        ? indirectCandidate(area, section, type, true, generatedAppend) : null;
                DrawBuffers.CandidateGeometry geometry = generatedPlan
                        ? candidate.geometry() : section.getCandidateGeometry(type);
                if(candidate != null) {
                    complete &= candidate.representable();
                    maxGpuVertices = Math.max(maxGpuVertices, candidate.maxGpuVertices());
                    if(candidate.representable() && candidate.maxGpuVertices() > 0) {
                        if(section.gpuTerrainDrawState().ownership()
                                == GpuTerrainDrawHandoff.Ownership.APPEND) {
                            if(candidate.secondGeometry() != null) generatedAppendPairs++;
                            else generatedAppendGpuOnly++;
                        } else {
                            recordedReplaceCount++;
                        }
                    }
                    // Generated producer state is not included in the ordinary
                    // mesh revision. Identical numeric offsets at a *new*
                    // generation must not silently reuse prior residency.
                    fingerprint = mix(fingerprint, candidate.stateStamp());
                    // Equal numeric offsets do not imply equal resource ownership.
                    // A retired resident can fall back to numerically identical
                    // CPU geometry; never reuse its prior generated-world counts,
                    // GPU vertex bound, or candidate generation on that basis.
                    fingerprint = mix(fingerprint, candidate.representable() ? 1L : 0L);
                    fingerprint = mix(fingerprint, candidate.maxGpuVertices());
                }
                boolean graphVisible = area.isGraphVisible(section);
                boolean directSeed = area.candidateIsDirectSeed(slot);
                // A post-enqueue direction change may create a new direct seed;
                // never silently omit that now-authoritative CPU queued draw.
                if((area.candidateQueuedWord(word) & (1L << bit)) != 0L
                        && !section.hasMainDirection() && !directSeed)
                    complete = false;
                int flags = GpuRegionCandidateTable.flags(geometry.ready(), graphVisible, directSeed, layer);
                if(candidate != null) {
                    if(candidate.secondGeometry() != null) {
                        DrawBuffers.CandidateGeometry second = candidate.secondGeometry();
                        builder.addPair(geometry.indexCount(), geometry.firstIndex(), geometry.vertexOffset(),
                                second.indexCount(), second.firstIndex(), second.vertexOffset(), slot, flags);
                        fingerprint = mix(fingerprint, second.indexCount());
                        fingerprint = mix(fingerprint, second.firstIndex());
                        fingerprint = mix(fingerprint, second.vertexOffset());
                        count++;
                    } else {
                        builder.add(geometry.indexCount(), 1, geometry.firstIndex(),
                                geometry.vertexOffset(), slot, flags);
                    }
                } else {
                    builder.add(geometry.indexCount(), 1, geometry.firstIndex(),
                            geometry.vertexOffset(), slot, flags);
                }
                fingerprint = mix(fingerprint, geometry.indexCount());
                fingerprint = mix(fingerprint, geometry.firstIndex());
                fingerprint = mix(fingerprint, geometry.vertexOffset());
                fingerprint = mix(fingerprint, slot);
                fingerprint = mix(fingerprint, flags);
                count++;
            }
        }
        RegionBatchStats.gpuCandidatePreparedSlots += count;
        // If a producer changes ownership while candidates are being prepared,
        // do not publish the mixed-generation result. Next draw retries safely.
        if(generatedPlan && generatedRevision != area.getGeneratedCandidateRevision()) {
            candidateCaches[layer].invalidate();
            recordCandidatePreparation(preparationStart);
            return;
        }
        fingerprint = mix(fingerprint, count);
        fingerprint = mix(fingerprint, complete ? 1 : 0);
        candidateCaches[layer].maxGpuVertices = maxGpuVertices;
        // A new traversal can be byte-for-byte identical. Record its revision
        // without re-uploading the table; the next draw then takes the fast path.
        if(candidateCaches[layer].initialized && candidateCaches[layer].fingerprint == fingerprint && sameVolume
                && candidateCaches[layer].producerMode == producerMode) {
            candidateCaches[layer].generatedRevision = generatedRevision;
            candidateCaches[layer].inputRevision = inputRevision;
            candidateCaches[layer].ownershipRevision = area.getCandidateOwnershipRevision();
            candidateCaches[layer].meshRevision = meshRevision;
            candidateCaches[layer].visibilityRevision = visibilityRevision;
            area.copyCandidateProvenance(candidateCaches[layer].graphVisits, candidateCaches[layer].directSeeds);
            recordCandidatePreparation(preparationStart);
            return;
        }

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
        // The old oracle compares ordinary CPU mesh membership, not generated
        // replacement ownership; do not report it as a generated selection oracle.
        long diagnosticToken = frustumSnapshot == null || generatedPlan
                ? 0L : GpuLiveSectionSelectionDiagnostic.claim();
        boolean[] cpuExpected = diagnosticToken == 0L ? null : buildCpuExpected(area, type);
        boolean queued = area.publishGpuCandidates(type, table);

        // A rejected upload is transient (for example, the global candidate
        // budget may be released when another area retires). Leave this
        // fingerprint uncommitted so an unchanged CPU queue can retry later.
        candidateCaches[layer].complete = queued && complete;
        candidateCaches[layer].initialized = queued;
        candidateCaches[layer].generatedReplace = queued ? recordedReplaceCount : 0;
        candidateCaches[layer].generatedAppendPairs = queued ? generatedAppendPairs : 0;
        candidateCaches[layer].generatedAppendGpuOnly = queued ? generatedAppendGpuOnly : 0;
        candidateCaches[layer].fingerprint = fingerprint;
        candidateCaches[layer].producerMode = producerMode;
        candidateCaches[layer].generatedRevision = generatedRevision;
        candidateCaches[layer].generation = generation;
        candidateCaches[layer].inputRevision = inputRevision;
        candidateCaches[layer].ownershipRevision = area.getCandidateOwnershipRevision();
        candidateCaches[layer].meshRevision = meshRevision;
        candidateCaches[layer].visibilityRevision = visibilityRevision;
        if(queued)
            area.copyCandidateProvenance(candidateCaches[layer].graphVisits, candidateCaches[layer].directSeeds);
        candidateCaches[layer].table = queued ? table : null;
        candidateCaches[layer].frustum = queued ? frustumSnapshot : null;
        if(queued) {
            RegionBatchStats.gpuCandidatePublications++;
            RegionBatchStats.gpuCandidatePublishedSlots += count;
        }

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
        recordCandidatePreparation(preparationStart);
    }

    GpuRegionCandidateTable publishedCandidates(TerrainRenderType type) {
        return candidateCaches[type.ordinal()].table;
    }

    boolean hasCompleteCandidateSuperset(TerrainRenderType type) {
        return candidateCaches[type.ordinal()].complete;
    }

    /** Producer-visible REPLACE or exact-generation atomic APPEND pair. */
    static IndirectCandidate indirectCandidate(ChunkArea area, RenderSection section,
                                               TerrainRenderType type, boolean terrainHandoff) {
        return indirectCandidate(area, section, type, terrainHandoff,
                GPU_GENERATED_INDIRECT_APPEND);
    }

    static IndirectCandidate indirectCandidate(ChunkArea area, RenderSection section,
                                               TerrainRenderType type, boolean terrainHandoff,
                                               boolean generatedAppend) {
        DrawBuffers.CandidateGeometry cpu = section.getCandidateGeometry(type);
        if(!terrainHandoff)
            return new IndirectCandidate(cpu, true, 0);
        RenderSection.GpuTerrainDrawState state = section.gpuTerrainDrawState();
        boolean cpuComplete = section.gpuTerrainCpuMeshComplete();
        long stamp = mix(mix(mix(FNV_OFFSET_BASIS, state.generation()),
                state.ownership().ordinal()), cpuComplete ? 1L : 0L);
        if(state.ownership() != GpuTerrainDrawHandoff.Ownership.REPLACE
                && !(generatedAppend && state.ownership() == GpuTerrainDrawHandoff.Ownership.APPEND))
            return new IndirectCandidate(cpu, false, 0, null, stamp);
        GpuTerrainOutputStore.Residency residency = area.getGpuTerrainOutputResidency(
                section.xOffset, section.yOffset, section.zOffset, type);
        // Missing, retired or different-generation residency can never produce a
        // GPU command. Avoid constructing a CPU DrawCommand and DrawPlan for
        // each ordinary section in mixed GPU-owned generated candidate regions.
        if(residency == null || !residency.valid()
                || residency.generation() != state.generation())
            return new IndirectCandidate(cpu, cpuComplete, 0, null, stamp);
        GpuTerrainDrawHandoff.DrawPlan plan = GpuTerrainDrawHandoff.plan(true, type,
                state.generation(), residency, cpu.indexCount(), cpu.firstIndex(),
                cpu.vertexOffset(), state.ownership());
        if(plan.gpuDrawCount() == 0)
            return new IndirectCandidate(cpu, cpuComplete, 0, null, stamp);
        // Never expose a GPU half without a ready CPU exception command. A
        // zero-exception APPEND is the planner's single GPU-only command.
        if(plan.commandCount() == 2 && !cpu.ready())
            return new IndirectCandidate(cpu, false, 0, null, stamp);
        GpuTerrainDrawHandoff.DrawCommand first = plan.command(0);
        GpuTerrainDrawHandoff.DrawCommand gpu = plan.command(plan.commandCount() - 1);
        DrawBuffers.CandidateGeometry second = null;
        if(plan.commandCount() == 2) {
            GpuTerrainDrawHandoff.DrawCommand append = plan.command(1);
            second = new DrawBuffers.CandidateGeometry(append.indexCount(),
                    append.firstIndex(), append.vertexOffset(), true);
        }
        return new IndirectCandidate(new DrawBuffers.CandidateGeometry(first.indexCount(),
                first.firstIndex(), first.vertexOffset(), true), true,
                gpu.indexCount() * 2 / 3, second, stamp);
    }

    record IndirectCandidate(DrawBuffers.CandidateGeometry geometry,
                             boolean representable, int maxGpuVertices,
                             DrawBuffers.CandidateGeometry secondGeometry,
                             long stateStamp) {
        IndirectCandidate(DrawBuffers.CandidateGeometry geometry,
                          boolean representable, int maxGpuVertices) {
            this(geometry, representable, maxGpuVertices, null, 0L);
        }
    }

    private static void recordCandidatePreparation(long start) {
        if(PROFILE_GPU_CANDIDATES)
            RegionBatchStats.gpuCandidatePreparationNanos += System.nanoTime() - start;
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
        for(int layer = 0; layer < candidateCaches.length; ++layer) {
            candidateCaches[layer].initialized = false;
            candidateCaches[layer].complete = false;
            candidateCaches[layer].generatedReplace = 0;
            candidateCaches[layer].generatedAppendPairs = 0;
            candidateCaches[layer].generatedAppendGpuOnly = 0;
            candidateCaches[layer].fingerprint = 0L;
            candidateCaches[layer].inputRevision = 0L;
            candidateCaches[layer].ownershipRevision = 0L;
            candidateCaches[layer].meshRevision = 0L;
            candidateCaches[layer].visibilityRevision = 0L;
            candidateCaches[layer].table = null;
            candidateCaches[layer].frustum = null;
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
