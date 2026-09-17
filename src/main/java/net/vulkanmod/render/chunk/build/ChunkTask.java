package net.vulkanmod.render.chunk.build;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.vulkanmod.render.chunk.voxel.GpuTerrainHybridMask;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelRegistry;
import net.vulkanmod.render.chunk.voxel.GpuLightingDemandTelemetry;
import net.vulkanmod.render.chunk.voxel.GpuSparseLightingMode;
import net.vulkanmod.render.chunk.voxel.GpuSparseLightingSnapshot;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.VisibilitySetExtended;
import net.vulkanmod.render.chunk.GpuTerrainDiagnostics;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.chunk.TerrainShaderManager;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkTask {
    private static TaskDispatcher taskDispatcher;

    protected AtomicBoolean cancelled = new AtomicBoolean(false);
    protected final RenderSection renderSection;
    public boolean highPriority = false;

    ChunkTask(RenderSection renderSection) {
        this.renderSection = renderSection;
    }

    public String name() { return "generic_chk_task"; }

    public CompletableFuture<Result> doTask(ThreadBuilderPack builderPack) { return null; }

    public void cancel() { this.cancelled.set(true); }

    public static void setTaskDispatcher(TaskDispatcher dispatcher) { taskDispatcher = dispatcher; }

    public static class BuildTask extends ChunkTask {
        private static final AtomicBoolean SPARSE_LIGHTING_ACTIVE_LOGGED = new AtomicBoolean();
        private static final AtomicBoolean SPARSE_LIGHTING_FAILURE_LOGGED = new AtomicBoolean();
        private static final AtomicBoolean GPU_PREFLIGHT_FAILURE_LOGGED = new AtomicBoolean();
        private static final AtomicBoolean GPU_CPU_BYPASS_LOGGED = new AtomicBoolean();

        @Nullable
        protected RenderChunkRegion region;
        private final long voxelGeneration;
        private final boolean gpuTerrainCpuRecoveryRequired;
        private final boolean gpuTerrainHadReadyCpuFallback;
        private final boolean gpuTerrainCpuBypassCandidate;
        private final boolean gpuTerrainHybridFreshCandidate;

        //debug
        private float buildTime;
        private boolean submitted = false;

        public BuildTask(RenderSection renderSection, RenderChunkRegion renderChunkRegion, boolean highPriority) {
            super(renderSection);
            this.region = renderChunkRegion;
            this.voxelGeneration = renderSection.getVoxelGeneration();
            this.gpuTerrainCpuRecoveryRequired = renderSection.gpuTerrainCpuRecoveryRequired();
            this.gpuTerrainHadReadyCpuFallback = renderSection.hasReadyGpuTerrainCpuFallback();
            this.gpuTerrainCpuBypassCandidate = RenderSection.gpuTerrainCpuBypassEnabled()
                    && !this.gpuTerrainCpuRecoveryRequired;
            // Mixed omission is fresh-section-only for now. A rebuild already has a
            // complete CPU mesh; publishing a new partial CPU mesh before its matching
            // GPU half would create a transient hole unless both halves are swapped
            // atomically. Keep rebuilds fully CPU-authored until that protocol exists.
            this.gpuTerrainHybridFreshCandidate = RenderSection.gpuTerrainHybridEnabled()
                    && !renderSection.isCompiled() && !this.gpuTerrainCpuRecoveryRequired;
            this.highPriority = highPriority;
        }

        public String name() { return "rend_chk_rebuild"; }

        public CompletableFuture<Result> doTask(ThreadBuilderPack chunkBufferBuilderPack) {
            this.submitted = true;
            long startTime = System.nanoTime();

            if (this.cancelled.get()) {
                return CompletableFuture.completedFuture(Result.CANCELLED);
            } else if (!this.renderSection.hasXYNeighbours()) {
                this.region = null;
                this.renderSection.setDirty(false);
                this.cancelled.set(true);
                return CompletableFuture.completedFuture(Result.CANCELLED);
            } else if (this.cancelled.get()) {
                return CompletableFuture.completedFuture(Result.CANCELLED);
            } else {
                Vec3 vec3 = WorldRenderer.getCameraPos();
                float f = (float)vec3.x;
                float g = (float)vec3.y;
                float h = (float)vec3.z;
                CompileResults compileResults = this.compile(f, g, h, chunkBufferBuilderPack);

                if (this.cancelled.get()) {
                    compileResults.renderedLayers.values().forEach(UploadBuffer::release);
                    return CompletableFuture.completedFuture(Result.CANCELLED);
                } else {
                    CompiledSection compiledChunk = new CompiledSection();
                    compiledChunk.visibilitySet = compileResults.visibilitySet;
                    compiledChunk.renderableBlockEntities.addAll(compileResults.blockEntities);
                    compiledChunk.transparencyState = compileResults.transparencyState;

                    if(!compileResults.renderedLayers.isEmpty() || compileResults.gpuTerrainCpuBypassed)
                        compiledChunk.isCompletelyEmpty = false;

                    compiledChunk.renderTypes.addAll(compileResults.renderedLayers.keySet());
                    TerrainRenderType preservedLayer = null;
                    if(compileResults.gpuTerrainCpuBypassed) {
                        preservedLayer = RenderSection.gpuTerrainOutputLayer();
                        compiledChunk.renderTypes.add(preservedLayer);
                    }

                    final TerrainRenderType retainedCpuLayer = preservedLayer;
                    taskDispatcher.scheduleSectionUpdate(this, renderSection,
                            compileResults.renderedLayers, retainedCpuLayer, () -> {
                        this.renderSection.updateGlobalBlockEntities(compileResults.globalBlockEntities);
                        this.renderSection.setCompiledSection(compiledChunk);
                        this.renderSection.setVisibility(((VisibilitySetExtended)compiledChunk.visibilitySet).getVisibility());
                        this.renderSection.setCompletelyEmpty(compiledChunk.isCompletelyEmpty);
                        this.renderSection.stageGpuTerrainPreflight(
                                compileResults.gpuTerrainPreflight, this.voxelGeneration,
                                compileResults.gpuTerrainCpuBypassed);
                        if(!compileResults.gpuTerrainCpuBypassed)
                            this.renderSection.completeGpuTerrainCpuRecovery(this.voxelGeneration);
                        this.renderSection.publishVoxels(compileResults.voxels,
                                compileResults.sparseLighting, this.voxelGeneration);
                    });

                    this.buildTime = (System.nanoTime() - startTime) * 0.000001f;
                    return CompletableFuture.completedFuture(Result.SUCCESSFUL);
                }
            }
        }

        private CompileResults compile(float camX, float camY, float camZ,
                                       ThreadBuilderPack chunkBufferBuilderPack) {
            CompileResults compileResults = new CompileResults();
            BlockPos blockPos = new BlockPos(renderSection.xOffset(), renderSection.yOffset(), renderSection.zOffset()).immutable();
            BlockPos blockPos2 = blockPos.offset(15, 15, 15);
            VisGraph visGraph = new VisGraph();
            RenderChunkRegion renderChunkRegion = this.region;
            this.region = null;
            PoseStack poseStack = new PoseStack();
            if (renderChunkRegion != null) {
                SectionVoxelSnapshot.Builder voxels = RegionVoxelStore.ENABLED
                        ? new SectionVoxelSnapshot.Builder(blockPos.getX(), blockPos.getY(), blockPos.getZ()) : null;

                // Capture immutable GPU inputs before model tessellation. Full-section
                // REPLACE remains preferred and may accelerate fresh builds/rebuilds.
                // Only if that path does not activate may a fresh section derive the
                // conservative APPEND subset and omit those selected block models.
                if(voxels != null && RenderSection.gpuTerrainMesherEnabled()) {
                    try {
                        GpuTerrainCapture capture = captureGpuTerrainInputs(
                                renderChunkRegion, blockPos, blockPos2);
                        SectionVoxelSnapshot original = capture.snapshot();
                        RenderSection.GpuTerrainPreflight fullPreflight =
                                RenderSection.qualifyGpuTerrain(original);

                        if(this.gpuTerrainCpuBypassCandidate
                                && RenderSection.gpuTerrainCpuBypassEligible(fullPreflight)) {
                            compileResults.voxels = original;
                            compileResults.gpuTerrainPreflight = fullPreflight;
                            captureSparseLighting(compileResults, renderChunkRegion, blockPos);
                            compileResults.gpuTerrainCpuBypassed =
                                    compileResults.sparseLighting != null;
                        }

                        if(!compileResults.gpuTerrainCpuBypassed
                                && this.gpuTerrainHybridFreshCandidate) {
                            GpuTerrainHybridMask.Plan hybridPlan = GpuTerrainHybridMask.plan(
                                    original, capture.visibleCpuBlockModels());
                            if(hybridPlan.ownedCount() > 0) {
                                SectionVoxelSnapshot filtered = hybridPlan.filteredSnapshot();
                                RenderSection.GpuTerrainPreflight hybridPreflight =
                                        RenderSection.qualifyHybridGpuTerrain(filtered);
                                if(RenderSection.gpuTerrainCpuBypassEligible(hybridPreflight)) {
                                    compileResults.voxels = filtered;
                                    compileResults.gpuTerrainPreflight = hybridPreflight;
                                    compileResults.sparseLighting = null;
                                    captureSparseLighting(compileResults, renderChunkRegion, blockPos);
                                    if(compileResults.sparseLighting != null) {
                                        compileResults.gpuTerrainCpuBypassed = true;
                                        compileResults.gpuTerrainHybridPlan = hybridPlan;
                                    }
                                }
                            }
                        }

                        // Failed/disabled acceleration must restore the unfiltered v4
                        // snapshot so the ordinary CPU path and any shadow diagnostics
                        // see the complete captured section, not a half-prepared subset.
                        if(!compileResults.gpuTerrainCpuBypassed) {
                            compileResults.voxels = original;
                            compileResults.gpuTerrainPreflight = fullPreflight;
                            compileResults.gpuTerrainHybridPlan = null;
                            compileResults.sparseLighting = null;
                            captureSparseLighting(compileResults, renderChunkRegion, blockPos);
                        }

                        if(RenderSection.gpuTerrainCpuBypassEnabled()) {
                            if(compileResults.gpuTerrainCpuBypassed) {
                                String reason = compileResults.gpuTerrainHybridPlan != null
                                        ? "worker_bypass_hybrid_fresh"
                                        : (this.gpuTerrainHadReadyCpuFallback
                                        ? "worker_bypass_rebuild" : "worker_bypass_fresh");
                                GpuTerrainDiagnostics.recordSuccess(reason,
                                        this.renderSection, this.voxelGeneration,
                                        compileResults.gpuTerrainPreflight.faceCount(), true);
                            } else if(this.gpuTerrainCpuRecoveryRequired) {
                                GpuTerrainDiagnostics.record("worker_preflight", "cpu_recovery_required",
                                        this.renderSection, this.voxelGeneration, null);
                            } else if(compileResults.gpuTerrainPreflight == null) {
                                GpuTerrainDiagnostics.recordQualificationFailure("worker_preflight",
                                        compileResults.voxels, this.renderSection, this.voxelGeneration);
                            } else if(compileResults.sparseLighting == null) {
                                GpuTerrainDiagnostics.record("worker_preflight", "sparse_lighting_unavailable",
                                        this.renderSection, this.voxelGeneration,
                                        "faces=" + compileResults.gpuTerrainPreflight.faceCount());
                            } else if(compileResults.gpuTerrainPreflight.faceCount() <= 0) {
                                GpuTerrainDiagnostics.record("worker_preflight", "face_count_empty",
                                        this.renderSection, this.voxelGeneration, null);
                            } else if(!RenderSection.gpuTerrainCpuBypassEligible(
                                    compileResults.gpuTerrainPreflight)) {
                                GpuTerrainDiagnostics.record("worker_preflight", "face_count_unsupported",
                                        this.renderSection, this.voxelGeneration,
                                        "faces=" + compileResults.gpuTerrainPreflight.faceCount());
                            } else {
                                GpuTerrainDiagnostics.record("worker_preflight", "bypass_gate_rejected",
                                        this.renderSection, this.voxelGeneration, null);
                            }
                        }

                        if(compileResults.gpuTerrainCpuBypassed
                                && GPU_CPU_BYPASS_LOGGED.compareAndSet(false, true)) {
                            Initializer.LOGGER.info(
                                    "VULKANMOD_GPU_TERRAIN_CPU_BYPASS_ACTIVE: section=({}, {}, {}) faces={} ownership={} priorCpuFallback={}; worker omitted GPU-owned block-model tessellation pending GPU publication",
                                    blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                                    compileResults.gpuTerrainPreflight.faceCount(),
                                    compileResults.gpuTerrainPreflight.ownership(),
                                    this.gpuTerrainHadReadyCpuFallback);
                        }
                        voxels = null;
                    } catch(RuntimeException error) {
                        compileResults.voxels = null;
                        compileResults.sparseLighting = null;
                        compileResults.gpuTerrainPreflight = null;
                        compileResults.gpuTerrainHybridPlan = null;
                        compileResults.gpuTerrainCpuBypassed = false;
                        GpuTerrainDiagnostics.record("worker_preflight", "exception",
                                this.renderSection, this.voxelGeneration,
                                error.getClass().getName() + ": " + error.getMessage());
                        if(GPU_PREFLIGHT_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Initializer.LOGGER.warn(
                                    "VULKANMOD_GPU_TERRAIN_PREFLIGHT_FAILED: retaining ordinary CPU terrain path",
                                    error);
                        }
                    }
                }

                ModelBlockRenderer.enableCaching();
                try {
                    Set<RenderType> set = new ReferenceArraySet<>(RenderType.chunkBufferLayers().size());
                    RandomSource randomSource = RandomSource.create();
                    BlockRenderDispatcher blockRenderDispatcher = Minecraft.getInstance().getBlockRenderer();

                    for(BlockPos blockPos3 : BlockPos.betweenClosed(blockPos, blockPos2)) {
                        BlockState blockState = renderChunkRegion.getBlockState(blockPos3);
                        boolean solidRender = blockState.isSolidRender(renderChunkRegion, blockPos3);
                        if (solidRender) visGraph.setOpaque(blockPos3);

                        boolean hasBlockEntity = blockState.hasBlockEntity();
                        if (hasBlockEntity) {
                            BlockEntity blockEntity = renderChunkRegion.getBlockEntity(blockPos3);
                            if (blockEntity != null) this.handleBlockEntity(compileResults, blockEntity);
                        }

                        FluidState fluidState = blockState.getFluidState();
                        if (voxels != null) {
                            int flags = SectionVoxelSnapshot.CPU_REQUIRED;
                            if (solidRender) flags |= SectionVoxelSnapshot.SOLID_RENDER;
                            if (hasBlockEntity) flags |= SectionVoxelSnapshot.HAS_BLOCK_ENTITY;
                            if (!fluidState.isEmpty()) flags |= SectionVoxelSnapshot.HAS_FLUID;
                            boolean gpuFullCube = GpuTerrainModelRegistry.isFullCubeGeometry(blockState)
                                    && hasZeroPositionOffset(blockState, renderChunkRegion, blockPos3);
                            if (gpuFullCube) flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
                            voxels.add(Block.getId(blockState), flags);
                            if (gpuFullCube)
                                captureSolidRenderBoundaryHalo(voxels, renderChunkRegion, blockPos3);
                        }

                        int localIndex = SectionVoxelSnapshot.blockIndex(
                                blockPos3.getX() & 15, blockPos3.getY() & 15, blockPos3.getZ() & 15);
                        boolean hybridGpuOwned = compileResults.gpuTerrainHybridPlan != null
                                && compileResults.gpuTerrainHybridPlan.owns(localIndex);
                        boolean fullGpuOwned = compileResults.gpuTerrainCpuBypassed
                                && compileResults.gpuTerrainHybridPlan == null;

                        RenderType renderType;
                        TerrainBufferBuilder bufferBuilder;
                        // Hybrid ownership never includes fluids, so APPEND retains the
                        // complete ordinary CPU liquid path. REPLACE remains fluid-free
                        // by qualification and can skip the whole CPU terrain pass.
                        if (!fullGpuOwned && !fluidState.isEmpty()) {
                            renderType = compactRenderTypes(ItemBlockRenderTypes.getRenderLayer(fluidState));
                            bufferBuilder = chunkBufferBuilderPack.builder(renderType);
                            if (set.add(renderType))
                                bufferBuilder.begin(VertexFormat.Mode.QUADS, TerrainShaderManager.TERRAIN_VERTEX_FORMAT);
                            blockRenderDispatcher.renderLiquid(blockPos3, renderChunkRegion,
                                    bufferBuilder, blockState, fluidState);
                        }

                        boolean skipCpuBlockModel = fullGpuOwned || hybridGpuOwned;
                        if (!skipCpuBlockModel
                                && blockState.getRenderShape() != RenderShape.INVISIBLE) {
                            renderType = compactRenderTypes(ItemBlockRenderTypes.getChunkRenderType(blockState));
                            bufferBuilder = chunkBufferBuilderPack.builder(renderType);
                            if (set.add(renderType))
                                bufferBuilder.begin(VertexFormat.Mode.QUADS, TerrainShaderManager.TERRAIN_VERTEX_FORMAT);

                            poseStack.pushPose();
                            poseStack.translate(blockPos3.getX() & 15, blockPos3.getY() & 15, blockPos3.getZ() & 15);
                            if (TerrainBufferBuilder.DEBUG_COMPRESSED_VERTEX_RANGE) {
                                bufferBuilder.setDebugBlockContext(blockPos3, blockState);
                                try {
                                    blockRenderDispatcher.renderBatched(blockState, blockPos3,
                                            renderChunkRegion, poseStack, bufferBuilder, true, randomSource);
                                } finally {
                                    bufferBuilder.clearDebugBlockContext();
                                }
                            } else {
                                blockRenderDispatcher.renderBatched(blockState, blockPos3,
                                        renderChunkRegion, poseStack, bufferBuilder, true, randomSource);
                            }
                            poseStack.popPose();
                        }
                    }

                    if (voxels != null) {
                        compileResults.voxels = voxels.finish();
                        captureSparseLighting(compileResults, renderChunkRegion, blockPos);
                    }

                    if (set.contains(RenderType.translucent())) {
                        TerrainBufferBuilder bufferBuilder2 = chunkBufferBuilderPack.builder(RenderType.translucent());
                        if (!bufferBuilder2.isCurrentBatchEmpty()) {
                            bufferBuilder2.setQuadSortOrigin(camX - (float)blockPos.getX(),
                                    camY - (float)blockPos.getY(), camZ - (float)blockPos.getZ());
                            compileResults.transparencyState = bufferBuilder2.getSortState();
                        }
                    }

                    for(RenderType renderType2 : set) {
                        TerrainBufferBuilder.RenderedBuffer renderedBuffer =
                                chunkBufferBuilderPack.builder(renderType2).endOrDiscardIfEmpty();
                        if (renderedBuffer != null) {
                            UploadBuffer uploadBuffer = new UploadBuffer(renderedBuffer);
                            compileResults.renderedLayers.put(TerrainRenderType.get(renderType2), uploadBuffer);
                            renderedBuffer.release();
                        }
                    }
                    if(compileResults.voxels != null && !compileResults.gpuTerrainCpuBypassed)
                        GpuLightingDemandTelemetry.record(compileResults.voxels,
                                cpuMeshBytes(compileResults.renderedLayers));
                } finally {
                    ModelBlockRenderer.clearCache();
                }
            }

            compileResults.visibilitySet = visGraph.resolve();
            return compileResults;
        }

        private static GpuTerrainCapture captureGpuTerrainInputs(RenderChunkRegion region,
                                                                 BlockPos blockPos,
                                                                 BlockPos blockPos2) {
            SectionVoxelSnapshot.Builder voxels = new SectionVoxelSnapshot.Builder(
                    blockPos.getX(), blockPos.getY(), blockPos.getZ());
            boolean[] visibleCpuBlockModels = new boolean[SectionVoxelSnapshot.BLOCK_COUNT];
            for(BlockPos pos : BlockPos.betweenClosed(blockPos, blockPos2)) {
                BlockState blockState = region.getBlockState(pos);
                boolean solidRender = blockState.isSolidRender(region, pos);
                boolean hasBlockEntity = blockState.hasBlockEntity();
                FluidState fluidState = blockState.getFluidState();
                int index = SectionVoxelSnapshot.blockIndex(
                        pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                visibleCpuBlockModels[index] = blockState.getRenderShape() != RenderShape.INVISIBLE;

                int flags = SectionVoxelSnapshot.CPU_REQUIRED;
                if(solidRender) flags |= SectionVoxelSnapshot.SOLID_RENDER;
                if(hasBlockEntity) flags |= SectionVoxelSnapshot.HAS_BLOCK_ENTITY;
                if(!fluidState.isEmpty()) flags |= SectionVoxelSnapshot.HAS_FLUID;

                boolean gpuFullCube = GpuTerrainModelRegistry.isFullCubeGeometry(blockState)
                        && hasZeroPositionOffset(blockState, region, pos)
                        && hasGpuFacePredicateEquivalence(blockState, region, pos);
                if(gpuFullCube) flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
                voxels.add(Block.getId(blockState), flags);
                if(gpuFullCube)
                    captureSolidRenderBoundaryHalo(voxels, region, pos);
            }
            return new GpuTerrainCapture(voxels.finish(), visibleCpuBlockModels);
        }

        /**
         * Prove that the shader's current face predicate matches the authoritative
         * Minecraft/Forge culling result while the worker still owns its region halo.
         * SOLID_RENDER is a visibility/occlusion fact, not by itself permission to
         * replace Block.shouldRenderFace(...). Any disagreement keeps this cube CPU-owned.
         */
        private static boolean hasGpuFacePredicateEquivalence(BlockState state,
                                                               RenderChunkRegion region,
                                                               BlockPos pos) {
            for(Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                boolean neighborSolidRender = region.getBlockState(neighborPos)
                        .isSolidRender(region, neighborPos);
                boolean gpuWouldRender = !neighborSolidRender;
                boolean authoritative = Block.shouldRenderFace(
                        state, region, pos, direction, neighborPos);
                if(authoritative != gpuWouldRender)
                    return false;
            }
            return true;
        }

        private static void captureSparseLighting(CompileResults compileResults,
                                                  RenderChunkRegion renderChunkRegion,
                                                  BlockPos blockPos) {
            if(!GpuSparseLightingMode.ENABLED || compileResults.voxels == null)
                return;
            try {
                GpuSparseLightingSnapshot sparseLighting = GpuSparseLightingSnapshot.tryCapture(
                        renderChunkRegion, blockPos, compileResults.voxels);
                if (sparseLighting != null && sparseLighting.sampleCount() > 0) {
                    compileResults.sparseLighting = sparseLighting;
                    if (SPARSE_LIGHTING_ACTIVE_LOGGED.compareAndSet(false, true)) {
                        Initializer.LOGGER.info(
                                "VULKANMOD_GPU_SPARSE_LIGHTING_CAPTURE_ACTIVE: section=({}, {}, {}) samples={} bytes={}; CPU terrain fallback remains available",
                                blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                                sparseLighting.sampleCount(), sparseLighting.byteSize());
                    }
                }
            } catch (RuntimeException error) {
                compileResults.sparseLighting = null;
                if (SPARSE_LIGHTING_FAILURE_LOGGED.compareAndSet(false, true)) {
                    Initializer.LOGGER.warn(
                            "VULKANMOD_GPU_SPARSE_LIGHTING_CAPTURE_FAILED: retaining CPU terrain path",
                            error);
                }
            }
        }

        private static void captureSolidRenderBoundaryHalo(SectionVoxelSnapshot.Builder voxels,
                                                           RenderChunkRegion region,
                                                           BlockPos pos) {
            int x = pos.getX() & 15;
            int y = pos.getY() & 15;
            int z = pos.getZ() & 15;
            int index = SectionVoxelSnapshot.blockIndex(x, y, z);

            if (y == 0) {
                BlockPos neighbor = pos.below();
                voxels.setBoundaryNeighborSolidRender(index, 0,
                        region.getBlockState(neighbor).isSolidRender(region, neighbor));
            }
            if (y == 15) {
                BlockPos neighbor = pos.above();
                voxels.setBoundaryNeighborSolidRender(index, 1,
                        region.getBlockState(neighbor).isSolidRender(region, neighbor));
            }
            if (z == 0) {
                BlockPos neighbor = pos.north();
                voxels.setBoundaryNeighborSolidRender(index, 2,
                        region.getBlockState(neighbor).isSolidRender(region, neighbor));
            }
            if (z == 15) {
                BlockPos neighbor = pos.south();
                voxels.setBoundaryNeighborSolidRender(index, 3,
                        region.getBlockState(neighbor).isSolidRender(region, neighbor));
            }
            if (x == 0) {
                BlockPos neighbor = pos.west();
                voxels.setBoundaryNeighborSolidRender(index, 4,
                        region.getBlockState(neighbor).isSolidRender(region, neighbor));
            }
            if (x == 15) {
                BlockPos neighbor = pos.east();
                voxels.setBoundaryNeighborSolidRender(index, 5,
                        region.getBlockState(neighbor).isSolidRender(region, neighbor));
            }
        }

        private static boolean hasZeroPositionOffset(BlockState state,
                                                     RenderChunkRegion region,
                                                     BlockPos pos) {
            var offset = state.getOffset(region, pos);
            return offset.x == 0.0D && offset.y == 0.0D && offset.z == 0.0D;
        }

        private static long cpuMeshBytes(Map<TerrainRenderType, UploadBuffer> layers) {
            long bytes = 0L;
            for(UploadBuffer upload : layers.values()) {
                if(upload.getVertexBuffer() != null)
                    bytes += upload.getVertexBuffer().remaining();
                if(upload.getIndexBuffer() != null)
                    bytes += upload.getIndexBuffer().remaining();
            }
            return bytes;
        }

        private RenderType compactRenderTypes(RenderType renderType) {
            if(Initializer.CONFIG.uniqueOpaqueLayer) {
                if (renderType != RenderType.translucent()) {
                    if(renderType != RenderType.tripwire()) renderType = RenderType.cutoutMipped();
                    else renderType = RenderType.translucent();
                }
            } else {
                if (renderType != RenderType.translucent() && renderType != RenderType.cutoutMipped()) {
                    if(renderType != RenderType.tripwire()) renderType = RenderType.cutout();
                    else renderType = RenderType.translucent();
                }
            }
            return renderType;
        }

        private <E extends BlockEntity> void handleBlockEntity(CompileResults compileResults, E blockEntity) {
            BlockEntityRenderer<E> blockEntityRenderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
            if (blockEntityRenderer != null) {
                compileResults.blockEntities.add(blockEntity);
                if (blockEntityRenderer.shouldRenderOffScreen(blockEntity))
                    compileResults.globalBlockEntities.add(blockEntity);
            }
        }

        private record GpuTerrainCapture(SectionVoxelSnapshot snapshot,
                                         boolean[] visibleCpuBlockModels) {}

        private static final class CompileResults {
            public final List<BlockEntity> globalBlockEntities = new ArrayList<>();
            public final List<BlockEntity> blockEntities = new ArrayList<>();
            public final EnumMap<TerrainRenderType, UploadBuffer> renderedLayers = new EnumMap<>(TerrainRenderType.class);
            public VisibilitySet visibilitySet = new VisibilitySet();
            public SectionVoxelSnapshot voxels;
            public GpuSparseLightingSnapshot sparseLighting;
            public RenderSection.GpuTerrainPreflight gpuTerrainPreflight;
            public GpuTerrainHybridMask.Plan gpuTerrainHybridPlan;
            public boolean gpuTerrainCpuBypassed;
            @org.jetbrains.annotations.Nullable
            public TerrainBufferBuilder.SortState transparencyState;
        }
    }

    public static class SortTransparencyTask extends ChunkTask {
        CompiledSection compiledSection;

        public SortTransparencyTask(RenderSection renderSection) {
            super(renderSection);
            this.compiledSection = renderSection.getCompiledSection();
        }

        public String name() { return "rend_chk_sort"; }

        public CompletableFuture<Result> doTask(ThreadBuilderPack builderPack) {
            if (this.cancelled.get()) {
                return CompletableFuture.completedFuture(Result.CANCELLED);
            } else if (!renderSection.hasXYNeighbours()) {
                this.cancelled.set(true);
                return CompletableFuture.completedFuture(Result.CANCELLED);
            } else {
                Vec3 vec3 = WorldRenderer.getCameraPos();
                float f = (float)vec3.x;
                float f1 = (float)vec3.y;
                float f2 = (float)vec3.z;
                TerrainBufferBuilder.SortState transparencyState = this.compiledSection.transparencyState;
                if (transparencyState != null && this.compiledSection.renderTypes.contains(TerrainRenderType.TRANSLUCENT)) {
                    TerrainBufferBuilder bufferbuilder = builderPack.builder(RenderType.translucent());
                    bufferbuilder.begin(VertexFormat.Mode.QUADS, TerrainShaderManager.TERRAIN_VERTEX_FORMAT);
                    bufferbuilder.restoreSortState(transparencyState);
                    bufferbuilder.setQuadSortOrigin(f - (float) this.renderSection.xOffset(),
                            f1 - (float) renderSection.yOffset(), f2 - (float) renderSection.zOffset());
                    TerrainBufferBuilder.SortState newTransparencyState = bufferbuilder.getSortState();
                    TerrainBufferBuilder.RenderedBuffer renderedBuffer = bufferbuilder.end();
                    if (this.cancelled.get()) {
                        renderedBuffer.release();
                        return CompletableFuture.completedFuture(Result.CANCELLED);
                    } else {
                        UploadBuffer uploadBuffer = new UploadBuffer(renderedBuffer);
                        renderedBuffer.release();
                        taskDispatcher.scheduleUploadChunkLayer(this, renderSection,
                                TerrainRenderType.get(RenderType.translucent()), uploadBuffer,
                                () -> this.compiledSection.transparencyState = newTransparencyState);
                        return CompletableFuture.completedFuture(Result.SUCCESSFUL);
                    }
                } else {
                    return CompletableFuture.completedFuture(Result.CANCELLED);
                }
            }
        }
    }

    public enum Result {
        CANCELLED,
        SUCCESSFUL;
    }
}
