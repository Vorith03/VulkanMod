package net.vulkanmod.render.chunk;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.build.ChunkTask;
import net.vulkanmod.render.chunk.build.CompiledSection;
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.chunk.voxel.GpuSparseLightingMode;
import net.vulkanmod.render.chunk.voxel.GpuSparseLightingSnapshot;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class RenderSection {
    static final Map<RenderSection, Set<BlockEntity>> globalBlockEntitiesMap = new Reference2ReferenceOpenHashMap<>();

    private ChunkArea chunkArea;
    private final RenderSection[] neighbours = new RenderSection[6];
    public byte frustumIndex;
    private short lastFrame = -1;
    private short lastFrame2 = -1;

    private final CompileStatus compileStatus = new CompileStatus();

    private boolean dirty = true;
    private long voxelGeneration;
    private long gpuTerrainPreflightGeneration = Long.MIN_VALUE;
    private long gpuTerrainPreflightModelGeneration = Long.MIN_VALUE;
    private int gpuTerrainPreflightFaceCount = -1;
    private GpuTerrainDrawHandoff.Ownership gpuTerrainPreflightOwnership =
            GpuTerrainDrawHandoff.Ownership.REPLACE;
    private boolean gpuTerrainPreflightCpuBypassed;
    // CPU terrain is incomplete after a fresh GPU-first REPLACE/APPEND build until
    // either its matching GPU output publishes or a complete CPU recovery publishes.
    private boolean gpuTerrainCpuMeshComplete = true;
    // A visible GPU handoff may intentionally remain on the previous build generation
    // while a dirty GPU-first section reconstructs a complete CPU fallback.
    private long gpuTerrainVisibleGeneration = Long.MIN_VALUE;
    private GpuTerrainDrawHandoff.Ownership gpuTerrainVisibleOwnership =
            GpuTerrainDrawHandoff.Ownership.REPLACE;
    private boolean forceCpuTerrainUntilSuccess;
    private long gpuTerrainAppendCpuStageGeneration = Long.MIN_VALUE;
    private DrawBuffers.StagedDrawParameters gpuTerrainAppendCpuStage;
    private boolean playerChanged;

    private boolean completelyEmpty = true;
    private long visibility;

    int xOffset, yOffset, zOffset;

    private final DrawBuffers.DrawParameters[] drawParametersArray;

    //Graph-info
    public Direction mainDir;
    public byte directions;
    public byte step;
    public byte directionChanges;
    byte sourceDirs;

    public RenderSection(int index, int x, int y, int z) {
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;

        this.drawParametersArray = new DrawBuffers.DrawParameters[TerrainRenderType.VALUES.length];
        for(int i = 0; i < this.drawParametersArray.length; ++i) {
            this.drawParametersArray[i] = new DrawBuffers.DrawParameters(TerrainRenderType.VALUES[i]);
        }
    }

    public void setOrigin(int x, int y, int z) {
        this.reset();
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;
    }

    public RenderSection setGraphInfo(@Nullable Direction from, byte step) {
        mainDir = from;
        sourceDirs = (byte) (from != null ? 1 << from.ordinal() : 0);
        this.step = step;
        this.directions = 0;
        this.directionChanges = 0;
        return this;
    }

    public void addDir(Direction direction) {
        if(sourceDirs == 0) return;
        sourceDirs |= 1 << direction.ordinal();
    }

    public void setDirections(byte p_109855_, Direction p_109856_) {
        this.directions = (byte)(this.directions | p_109855_ | 1 << p_109856_.ordinal());
    }

    void setDirectionChanges(byte i) { this.directionChanges = i; }

    public boolean hasDirection(Direction p_109860_) {
        return (this.directions & 1 << p_109860_.ordinal()) > 0;
    }

    public boolean hasMainDirection() { return this.sourceDirs != 0; }

    public boolean resortTransparency(TerrainRenderType renderType, TaskDispatcher taskDispatcher) {
        CompiledSection compiledSection1 = this.getCompiledSection();
        if (this.compileStatus.sortTask != null) this.compileStatus.sortTask.cancel();
        if (!compiledSection1.renderTypes.contains(renderType)) return false;
        this.compileStatus.sortTask = new ChunkTask.SortTransparencyTask(this);
        taskDispatcher.schedule(this.compileStatus.sortTask);
        return true;
    }

    public void rebuildChunkAsync(TaskDispatcher dispatcher, RenderRegionCache renderRegionCache) {
        ChunkTask.BuildTask chunkCompileTask = this.createCompileTask(renderRegionCache);
        dispatcher.schedule(chunkCompileTask);
    }

    public void rebuildChunkSync(TaskDispatcher dispatcher, RenderRegionCache renderRegionCache) {
        ChunkTask.BuildTask chunkCompileTask = this.createCompileTask(renderRegionCache);
        chunkCompileTask.doTask(dispatcher.fixedBuffers);
    }

    public ChunkTask.BuildTask createCompileTask(RenderRegionCache renderRegionCache) {
        boolean flag = this.cancelTasks();
        BlockPos blockpos = new BlockPos(this.xOffset, this.yOffset, this.zOffset).immutable();
        RenderChunkRegion renderchunkregion = renderRegionCache.createRegion(WorldRenderer.getLevel(), blockpos.offset(-1, -1, -1), blockpos.offset(16, 16, 16), 1);
        boolean flag1 = this.compileStatus.compiledSection == CompiledSection.UNCOMPILED;
        this.compileStatus.rebuildTask = new ChunkTask.BuildTask(this, renderchunkregion, !flag1 || flag);
        return this.compileStatus.rebuildTask;
    }

    protected boolean cancelTasks() {
        boolean flag = false;
        if (this.compileStatus.rebuildTask != null) {
            this.compileStatus.rebuildTask.cancel();
            this.compileStatus.rebuildTask = null;
            flag = true;
        }
        if (this.compileStatus.sortTask != null) {
            this.compileStatus.sortTask.cancel();
            this.compileStatus.sortTask = null;
        }
        return flag;
    }

    void release() {
        this.invalidateVoxels(false);
        synchronized(this) { this.forceCpuTerrainUntilSuccess = false; }
        this.cancelTasks();
        this.clearGlobalBlockEntities();
    }

    public void setNotDirty() {
        this.dirty = false;
        this.playerChanged = false;
    }

    public boolean isDirty() { return this.dirty; }

    public boolean isDirtyFromPlayer() { return this.dirty && this.playerChanged; }

    public int xOffset() { return xOffset; }
    public int yOffset() { return yOffset; }
    public int zOffset() { return zOffset; }

    public DrawBuffers.DrawParameters getDrawParameters(TerrainRenderType renderType) {
        return drawParametersArray[renderType.ordinal()];
    }

    public void setNeighbour(int index, @Nullable RenderSection chunk) { this.neighbours[index] = chunk; }
    public RenderSection getNeighbour(Direction dir) { return this.neighbours[dir.ordinal()]; }
    public RenderSection getNeighbour(int i) { return this.neighbours[i]; }

    public void setChunkArea(ChunkArea chunkArea) {
        this.chunkArea = chunkArea;
        this.frustumIndex = chunkArea.getFrustumIndex(xOffset, yOffset, zOffset);
    }

    public ChunkArea getChunkArea() { return this.chunkArea; }
    public CompiledSection getCompiledSection() { return compileStatus.compiledSection; }
    public boolean isCompiled() { return this.compileStatus.compiledSection != CompiledSection.UNCOMPILED; }
    public void setVisibility(long visibility) { this.visibility = visibility; }
    public void setCompletelyEmpty(boolean b) { this.completelyEmpty = b; }

    public boolean visibilityBetween(Direction dir1, Direction dir2) {
        return (this.visibility & (1L << ((dir1.ordinal() << 3) + dir2.ordinal()))) != 0;
    }

    public boolean isCompletelyEmpty() { return this.completelyEmpty; }

    private boolean doesChunkExistAt(int chunkX, int chunkZ) {
        var level = WorldRenderer.getLevel();
        return level != null && level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }

    public boolean hasXYNeighbours() {
        Vec3 cameraPos = WorldRenderer.getCameraPos();
        if(cameraPos == null) return true;
        double dx = this.xOffset + 8.0D - cameraPos.x;
        double dy = this.yOffset + 8.0D - cameraPos.y;
        double dz = this.zOffset + 8.0D - cameraPos.z;
        if(dx * dx + dy * dy + dz * dz <= 576.0D) return true;
        int chunkX = this.xOffset >> 4;
        int chunkZ = this.zOffset >> 4;
        return this.doesChunkExistAt(chunkX - 1, chunkZ)
                && this.doesChunkExistAt(chunkX, chunkZ - 1)
                && this.doesChunkExistAt(chunkX + 1, chunkZ)
                && this.doesChunkExistAt(chunkX, chunkZ + 1);
    }

    public void updateGlobalBlockEntities(Collection<BlockEntity> fullSet) {
        Set<BlockEntity> newSet = Sets.newHashSet(fullSet);
        Set<BlockEntity> oldSet;
        synchronized(globalBlockEntitiesMap) {
            oldSet = globalBlockEntitiesMap.get(this);
            if(oldSet == null) oldSet = Collections.emptySet();
            if(oldSet.size() == newSet.size() && oldSet.containsAll(newSet)) return;
            if(newSet.isEmpty()) globalBlockEntitiesMap.remove(this);
            else globalBlockEntitiesMap.put(this, newSet);
        }
        Set<BlockEntity> removed = Sets.newHashSet(oldSet);
        removed.removeAll(newSet);
        Set<BlockEntity> added = Sets.newHashSet(newSet);
        added.removeAll(oldSet);
        Minecraft.getInstance().levelRenderer.updateGlobalBlockEntities(removed, added);
    }

    private void clearGlobalBlockEntities() {
        Set<BlockEntity> removed;
        synchronized(globalBlockEntitiesMap) { removed = globalBlockEntitiesMap.remove(this); }
        if(removed != null && !removed.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            if(minecraft.levelRenderer != null)
                minecraft.levelRenderer.updateGlobalBlockEntities(removed, Collections.emptySet());
        }
    }

    private void reset() {
        this.invalidateVoxels(false);
        synchronized(this) { this.forceCpuTerrainUntilSuccess = false; }
        this.cancelTasks();
        this.clearGlobalBlockEntities();
        this.compileStatus.compiledSection = CompiledSection.UNCOMPILED;
        this.dirty = true;
        this.visibility = 0;
        this.completelyEmpty = true;
        this.resetDrawParameters();
    }

    private void resetDrawParameters() {
        for(DrawBuffers.DrawParameters drawParameters : this.drawParametersArray)
            drawParameters.reset(this.chunkArea);
    }

    public void setDirty(boolean playerChanged) {
        this.invalidateVoxels(true);
        this.playerChanged = playerChanged || this.dirty && this.playerChanged;
        this.dirty = true;
        WorldRenderer.getInstance().setNeedsUpdate();
    }

    public synchronized long getVoxelGeneration() { return this.voxelGeneration; }

    public static boolean gpuTerrainMesherEnabled() { return GpuTerrainSectionMesherBridge.enabled(); }
    public static boolean gpuTerrainCpuBypassEnabled() { return GpuTerrainSectionMesherBridge.cpuBypassEnabled(); }
    public static boolean gpuTerrainHybridEnabled() { return GpuTerrainSectionMesherBridge.hybridEnabled(); }
    public static TerrainRenderType gpuTerrainOutputLayer() { return GpuTerrainSectionMesherBridge.outputLayer(); }

    public static boolean gpuTerrainCpuBypassEligible(@Nullable GpuTerrainPreflight preflight) {
        return preflight != null && GpuTerrainSectionMesherBridge.supportsCpuBypass(preflight.faceCount());
    }

    public synchronized boolean hasReadyGpuTerrainCpuFallback() {
        if(!this.gpuTerrainCpuMeshComplete || !this.isCompiled()) return false;
        DrawBuffers.DrawParameters parameters = this.getDrawParameters(gpuTerrainOutputLayer());
        return parameters.indexCount > 0 && parameters.vertexBufferSegment.isReady();
    }

    synchronized boolean gpuTerrainCpuMeshComplete() {
        return this.gpuTerrainCpuMeshComplete;
    }

    public synchronized boolean gpuTerrainCpuRecoveryRequired() { return this.forceCpuTerrainUntilSuccess; }

    /**
     * Record whether the CPU draw retained by this generation is independently
     * complete. GPU-first fresh builds deliberately publish false here; a REPLACE
     * rebuild may still report true when it retained a previously complete CPU mesh.
     */
    public synchronized void setGpuTerrainCpuMeshComplete(long generation, boolean complete) {
        if(generation == this.voxelGeneration)
            this.gpuTerrainCpuMeshComplete = complete;
    }

    /**
     * A complete CPU publication becomes the safe transition point away from any
     * retained older GPU-first handoff. Input/build generation can advance earlier,
     * but the old complete pair stays drawable until this point.
     */
    public void completeGpuTerrainCpuRecovery(long generation) {
        ChunkArea area;
        synchronized(this) {
            if(generation != this.voxelGeneration)
                return;
            this.forceCpuTerrainUntilSuccess = false;
            this.gpuTerrainCpuMeshComplete = true;
            this.clearGpuTerrainVisibleHandoff();
            area = this.chunkArea;
        }
        if(area != null)
            area.invalidateGpuTerrainOutput(this.xOffset, this.yOffset, this.zOffset, generation);
    }

    @Nullable
    public static GpuTerrainPreflight qualifyGpuTerrain(SectionVoxelSnapshot snapshot) {
        GpuTerrainSectionMesherBridge.Qualification qualification = GpuTerrainSectionMesherBridge.qualify(snapshot);
        return qualification == null ? null
                : new GpuTerrainPreflight(qualification.modelGeneration(), qualification.faceCount());
    }

    @Nullable
    public static GpuTerrainPreflight qualifyHybridGpuTerrain(SectionVoxelSnapshot snapshot) {
        GpuTerrainSectionMesherBridge.Qualification qualification =
                GpuTerrainSectionMesherBridge.qualifyHybrid(snapshot);
        return qualification == null ? null
                : new GpuTerrainPreflight(qualification.modelGeneration(), qualification.faceCount(),
                GpuTerrainDrawHandoff.Ownership.APPEND);
    }

    public void stageGpuTerrainPreflight(@Nullable GpuTerrainPreflight preflight, long generation) {
        this.stageGpuTerrainPreflight(preflight, generation, false);
    }

    public synchronized void stageGpuTerrainPreflight(@Nullable GpuTerrainPreflight preflight,
                                                      long generation, boolean cpuBypassed) {
        if(generation != this.voxelGeneration) return;
        if(preflight == null) {
            this.clearGpuTerrainPreflight();
            return;
        }
        this.gpuTerrainPreflightGeneration = generation;
        this.gpuTerrainPreflightModelGeneration = preflight.modelGeneration();
        this.gpuTerrainPreflightFaceCount = preflight.faceCount();
        this.gpuTerrainPreflightOwnership = preflight.ownership();
        this.gpuTerrainPreflightCpuBypassed = cpuBypassed;
    }

    synchronized boolean matchesStagedGpuTerrainPreflight(long generation,
                                                          long modelGeneration, int faceCount) {
        return generation == this.voxelGeneration
                && this.gpuTerrainPreflightGeneration == generation
                && this.gpuTerrainPreflightModelGeneration == modelGeneration
                && this.gpuTerrainPreflightFaceCount == faceCount;
    }

    synchronized boolean matchesStagedGpuTerrainPreflight(long generation,
                                                          long modelGeneration, int faceCount,
                                                          GpuTerrainDrawHandoff.Ownership ownership) {
        return ownership != null
                && this.matchesStagedGpuTerrainPreflight(generation, modelGeneration, faceCount)
                && this.gpuTerrainPreflightOwnership == ownership;
    }

    synchronized GpuTerrainDrawHandoff.Ownership stagedGpuTerrainOwnership(long generation) {
        if(generation != this.voxelGeneration || this.gpuTerrainPreflightGeneration != generation)
            return GpuTerrainDrawHandoff.Ownership.REPLACE;
        return this.gpuTerrainPreflightOwnership;
    }

    synchronized GpuTerrainDrawState gpuTerrainDrawState() {
        if(this.gpuTerrainVisibleGeneration != Long.MIN_VALUE) {
            return new GpuTerrainDrawState(
                    this.gpuTerrainVisibleGeneration, this.gpuTerrainVisibleOwnership);
        }
        return new GpuTerrainDrawState(
                this.voxelGeneration, this.stagedGpuTerrainOwnership(this.voxelGeneration));
    }

    synchronized boolean publishGpuTerrainDrawHandoff(
            long generation, GpuTerrainDrawHandoff.Ownership ownership) {
        if(ownership == null
                || generation != this.voxelGeneration
                || this.gpuTerrainPreflightGeneration != generation
                || this.gpuTerrainPreflightOwnership != ownership)
            return false;
        this.gpuTerrainVisibleGeneration = generation;
        this.gpuTerrainVisibleOwnership = ownership;
        return true;
    }

    synchronized boolean canCommitGpuTerrainAppendRebuild(long generation, int faceCount) {
        return generation == this.voxelGeneration
                && faceCount > 0
                && this.gpuTerrainPreflightGeneration == generation
                && this.gpuTerrainPreflightFaceCount == faceCount
                && this.gpuTerrainPreflightOwnership == GpuTerrainDrawHandoff.Ownership.APPEND
                && this.gpuTerrainPreflightCpuBypassed;
    }

    /**
     * Final logical visibility switch for a prevalidated atomic APPEND rebuild.
     * The CPU and GPU staged allocations must already have committed successfully
     * on the render thread before this no-fail state transition is invoked.
     */
    public synchronized boolean stageGpuTerrainAppendCpu(
            long generation, DrawBuffers.StagedDrawParameters staged) {
        if(staged == null || generation != this.voxelGeneration
                || staged.section != this || staged.generation() != generation)
            return false;
        if(this.gpuTerrainAppendCpuStage != null)
            return false;
        this.gpuTerrainAppendCpuStageGeneration = generation;
        this.gpuTerrainAppendCpuStage = staged;
        return true;
    }

    synchronized DrawBuffers.StagedDrawParameters gpuTerrainAppendCpuStage(long generation) {
        return generation == this.voxelGeneration
                && this.gpuTerrainAppendCpuStageGeneration == generation
                ? this.gpuTerrainAppendCpuStage : null;
    }

    synchronized boolean matchesGpuTerrainAppendCpuStage(
            long generation, DrawBuffers.StagedDrawParameters staged) {
        return staged != null
                && generation == this.voxelGeneration
                && this.gpuTerrainAppendCpuStageGeneration == generation
                && this.gpuTerrainAppendCpuStage == staged;
    }

    public void discardGpuTerrainAppendCpuStage(long generation) {
        DrawBuffers.StagedDrawParameters staged;
        ChunkArea area;
        synchronized(this) {
            if(this.gpuTerrainAppendCpuStageGeneration != generation
                    || this.gpuTerrainAppendCpuStage == null)
                return;
            staged = this.gpuTerrainAppendCpuStage;
            this.gpuTerrainAppendCpuStage = null;
            this.gpuTerrainAppendCpuStageGeneration = Long.MIN_VALUE;
            area = this.chunkArea;
        }
        if(area != null)
            area.drawBuffers.discardStaged(staged);
    }

    synchronized void commitGpuTerrainAppendRebuildHandoff(
            long generation, int faceCount, DrawBuffers.StagedDrawParameters stagedCpu) {
        if(!this.canCommitGpuTerrainAppendRebuild(generation, faceCount)
                || !this.matchesGpuTerrainAppendCpuStage(generation, stagedCpu))
            throw new IllegalStateException("GPU APPEND rebuild handoff became stale during commit");
        this.gpuTerrainVisibleGeneration = generation;
        this.gpuTerrainVisibleOwnership = GpuTerrainDrawHandoff.Ownership.APPEND;
        this.forceCpuTerrainUntilSuccess = false;
        this.gpuTerrainCpuMeshComplete = false;
        this.gpuTerrainAppendCpuStage = null;
        this.gpuTerrainAppendCpuStageGeneration = Long.MIN_VALUE;
    }

    synchronized boolean stagedGpuTerrainCpuBypassed(long generation) {
        return generation == this.voxelGeneration
                && this.gpuTerrainPreflightGeneration == generation
                && this.gpuTerrainPreflightCpuBypassed;
    }

    public boolean requestGpuTerrainCpuRecovery(long generation) {
        synchronized(this) {
            if(generation != this.voxelGeneration
                    || this.gpuTerrainPreflightGeneration != generation
                    || !this.gpuTerrainPreflightCpuBypassed)
                return false;
            this.forceCpuTerrainUntilSuccess = true;
        }
        this.setDirty(false);
        return true;
    }

    private void clearGpuTerrainPreflight() {
        this.gpuTerrainPreflightGeneration = Long.MIN_VALUE;
        this.gpuTerrainPreflightModelGeneration = Long.MIN_VALUE;
        this.gpuTerrainPreflightFaceCount = -1;
        this.gpuTerrainPreflightOwnership = GpuTerrainDrawHandoff.Ownership.REPLACE;
        this.gpuTerrainPreflightCpuBypassed = false;
    }

    public synchronized void publishVoxels(SectionVoxelSnapshot snapshot, long generation) {
        this.publishVoxels(snapshot, null, generation);
    }

    public synchronized void publishVoxels(SectionVoxelSnapshot snapshot,
                                           GpuSparseLightingSnapshot sparseLighting,
                                           long generation) {
        if (!RegionVoxelStore.ENABLED) return;
        if (generation == this.voxelGeneration && this.chunkArea != null) {
            this.chunkArea.publishVoxels(xOffset, yOffset, zOffset, snapshot, generation);
            if (GpuSparseLightingMode.ENABLED)
                this.chunkArea.publishSparseLighting(xOffset, yOffset, zOffset, sparseLighting, generation);
        }
    }

    synchronized void invalidateVoxels() {
        this.invalidateVoxels(false);
    }

    /**
     * Dirty rebuilds preserve an already-published GPU handoff only when the current
     * CPU mesh is not independently complete. This mirrors vanilla's old-mesh-until-
     * rebuild behavior: stale complete geometry may remain visible briefly, while
     * partial CPU geometry is never exposed by itself.
     */
    synchronized void invalidateVoxels(boolean preserveIncompleteGpuHandoff) {
        if(this.gpuTerrainAppendCpuStage != null) {
            DrawBuffers.StagedDrawParameters staged = this.gpuTerrainAppendCpuStage;
            this.gpuTerrainAppendCpuStage = null;
            this.gpuTerrainAppendCpuStageGeneration = Long.MIN_VALUE;
            if(this.chunkArea != null)
                this.chunkArea.drawBuffers.discardStaged(staged);
        }

        boolean cpuIncomplete = !this.gpuTerrainCpuMeshComplete;
        boolean retainVisibleGpu = preserveIncompleteGpuHandoff
                && cpuIncomplete
                && this.gpuTerrainVisibleGeneration != Long.MIN_VALUE;

        if(preserveIncompleteGpuHandoff && cpuIncomplete)
            this.forceCpuTerrainUntilSuccess = true;

        this.clearGpuTerrainPreflight();
        if (!RegionVoxelStore.ENABLED) {
            if(!retainVisibleGpu)
                this.clearGpuTerrainVisibleHandoff();
            return;
        }

        this.voxelGeneration++;
        if(!retainVisibleGpu)
            this.clearGpuTerrainVisibleHandoff();
        if (this.chunkArea != null)
            this.chunkArea.removeVoxels(xOffset, yOffset, zOffset,
                    this.voxelGeneration, retainVisibleGpu);
    }

    private void clearGpuTerrainVisibleHandoff() {
        this.gpuTerrainVisibleGeneration = Long.MIN_VALUE;
        this.gpuTerrainVisibleOwnership = GpuTerrainDrawHandoff.Ownership.REPLACE;
    }

    public void setCompiledSection(CompiledSection compiledSection) { this.compileStatus.compiledSection = compiledSection; }

    public boolean setLastFrame(short i) {
        boolean res = i == this.lastFrame;
        if(!res) this.lastFrame = i;
        return res;
    }

    public boolean setLastFrame2(short i) {
        boolean res = i == this.lastFrame2;
        if(!res) this.lastFrame2 = i;
        return res;
    }

    public short getLastFrame() { return this.lastFrame; }

    record GpuTerrainDrawState(long generation,
                               GpuTerrainDrawHandoff.Ownership ownership) {}

    public record GpuTerrainPreflight(long modelGeneration, int faceCount,
                                      GpuTerrainDrawHandoff.Ownership ownership) {
        public GpuTerrainPreflight(long modelGeneration, int faceCount) {
            this(modelGeneration, faceCount, GpuTerrainDrawHandoff.Ownership.REPLACE);
        }
        public GpuTerrainPreflight {
            if(ownership == null)
                throw new IllegalArgumentException("GPU terrain preflight ownership must be present");
        }
    }

    static class CompileStatus {
        CompiledSection compiledSection = CompiledSection.UNCOMPILED;
        ChunkTask.BuildTask rebuildTask;
        ChunkTask.SortTransparencyTask sortTask;
    }
}