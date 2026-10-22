package net.vulkanmod.render.chunk;

import net.minecraft.core.BlockPos;
import net.vulkanmod.render.chunk.voxel.RegionVoxelGpuStore;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;
import net.vulkanmod.render.chunk.voxel.GpuSparseLightingSnapshot;
import net.vulkanmod.render.chunk.voxel.GpuRegionCandidateGpuStore;
import net.vulkanmod.render.chunk.voxel.GpuRegionCandidateTable;
import net.vulkanmod.render.chunk.util.ResettableQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import org.joml.FrustumIntersection;
import org.joml.Vector3i;

import java.util.Arrays;

public class ChunkArea {
    public final int index;
    private final byte[] inFrustum = new byte[64];

    final Vector3i position;

    DrawBuffers drawBuffers;
    private RegionVoxelStore voxels;
    private RegionVoxelGpuStore gpuVoxels;
    private GpuTerrainOutputStore gpuTerrainOutputs;
    private final GpuRegionCandidateGpuStore[] gpuCandidates =
            new GpuRegionCandidateGpuStore[TerrainRenderType.VALUES.length];
    private final RenderSection[] ownedSections =
            new RenderSection[RegionBatchLayout.MAX_SECTIONS];

    final ResettableQueue<RenderSection> sectionQueue =
            new ResettableQueue<>(1024, RegionDrawBatch.trackCandidateProvenance());
    private long visibilityRevision;
    // Producer/traversal invalidation for the region candidate fast path. An
    // off-frustum graph visit can change without changing sectionQueue membership.
    private long candidateInputRevision;
    // Changes only when the owned set changes, not merely on a traversal rewrite.
    private long candidateOwnershipRevision;
    private final long[] candidateGeometryRevisions = new long[TerrainRenderType.VALUES.length];
    // Generated ownership, CPU completeness and visible-generation changes are
    // not ordinary mesh revisions. Producer events invalidate candidate reuse.
    private long generatedCandidateRevision;
    // Record graph visits before frustum rejection and draw-queue direct seeds.
    // Only enabled for the existing GPU candidate experiment; normal CPU traversal
    // does not pay the per-visited-section bitset cost.
    private static final boolean TRACK_GPU_CANDIDATE_PROVENANCE =
            RegionDrawBatch.trackCandidateProvenance();
    private final long[] graphVisitBits = new long[RegionBatchLayout.MAX_SECTIONS / Long.SIZE];
    private final long[] directSeedBits = new long[RegionBatchLayout.MAX_SECTIONS / Long.SIZE];
    // Producer-maintained queue containment: each queued identity must occupy
    // one owned graph/direct slot. Draw-time CPU queue iteration is unnecessary
    // when the queue has only been mutated through addSection/resetQueue.
    private final long[] queuedCandidateBits = new long[RegionBatchLayout.MAX_SECTIONS / Long.SIZE];
    private boolean candidateQueueComplete = true;
    private int candidateQueueCount;
    private long candidateQueueRevision;
    private boolean visibilityRewritePending;
    // WorldRenderer's short stamp wraps through every value, including -1.
    // Validity must not consume a value from that production stamp domain.
    private short traversalFrame = -1;
    private boolean traversalFrameValid;

    public ChunkArea(int i, Vector3i origin) {
        this.index = i;
        this.position = origin;
        this.drawBuffers = new DrawBuffers();
    }

    public void updateFrustum(VFrustum frustum) {
        //TODO: maybe move to an aux class
        int frustumResult = frustum.cubeInFrustum(this.position.x(), this.position.y(), this.position.z(),
                this.position.x() + (8 << 4) , this.position.y() + (8 << 4), this.position.z() + (8 << 4));

        //Inner cubes
        if (frustumResult == FrustumIntersection.INTERSECT) {
            int width = 8 << 4;
            int l = width >> 1;

            for(int x1 = 0; x1 < 2; x1++) {
                float xMin = this.position.x() + (x1 * l);
                float xMax = xMin + l;
                for(int y1 = 0; y1 < 2; y1++) {
                    float yMin = this.position.y() + (y1 * l);
                    float yMax = yMin + l;
                    for (int z1 = 0; z1 < 2; z1++) {
                        float zMin = this.position.z() + (z1 * l);
                        float zMax = zMin + l;

                        frustumResult = frustum.cubeInFrustum(xMin, yMin, zMin,
                                xMax , yMax, zMax);

                        int beginIdx = (x1 << 5) + (y1 << 4) + (z1 << 3);
                        if (frustumResult == FrustumIntersection.INTERSECT) {
                            int l2 = width >> 2;
                            for (int x2 = 0; x2 < 2; x2++) {
                                float xMin2 = xMin + x2 * l2;
                                float xMax2 = xMin2 + l2;
                                for (int y2 = 0; y2 < 2; y2++) {
                                    float yMin2 = yMin + y2 * l2;
                                    float yMax2 = yMin2 + l2;
                                    for (int z2 = 0; z2 < 2; z2++) {
                                        float zMin2 = zMin + z2 * l2;
                                        float zMax2 = zMin2 + l2;

                                        frustumResult = frustum.cubeInFrustum(xMin2, yMin2, zMin2,
                                                xMax2, yMax2, zMax2);

                                        int idx = beginIdx + (x2 << 2) + (y2 << 1) + z2;

                                        this.inFrustum[idx] = (byte) frustumResult;
                                    }

                                }
                            }
                        }
                        else {
                            int end = beginIdx + 8;

                            for(int i = beginIdx; i < end; ++i) {
                                this.inFrustum[i] = (byte) frustumResult;
                            }
                        }

                    }
                }
            }
        } else {
            Arrays.fill(inFrustum, (byte) frustumResult);
        }

    }

    public byte getFrustumIndex(BlockPos pos) {
        return getFrustumIndex(pos.getX(), pos.getY(), pos.getZ());
    }

    public byte getFrustumIndex(int x, int y, int z) {
        int dx = x - this.position.x;
        int dy = y - this.position.y;
        int dz = z - this.position.z;

        int i = (dx >> 6 << 5)
                + (dy >> 6 << 4)
                + (dz >> 6 << 3);

        int xSub = (dx >> 3) & 0b100;
        int ySub = (dy >> 4) & 0b10;
        int zSub = (dz >> 5) & 0b1;

        return (byte) (i + xSub + ySub + zSub);
    }

    public byte inFrustum(byte i) {
        return this.inFrustum[i];
    }

    public DrawBuffers getDrawBuffers() {
        if(!this.drawBuffers.isAllocated())
            drawBuffers.allocateBuffers();

        return this.drawBuffers;
    }

    private void allocateDrawBuffers() {
        this.drawBuffers = new DrawBuffers();
    }

    public void addSection(RenderSection section) {
        this.traversalFrame = section.getLastFrame();
        this.traversalFrameValid = true;
        this.sectionQueue.add(section);
        if(TRACK_GPU_CANDIDATE_PROVENANCE) {
            markCandidateGraphVisit(section);
            if(!section.hasMainDirection())
                markCandidateBit(this.directSeedBits, section);
            int slot = voxelSlot(section.xOffset, section.yOffset, section.zOffset);
            candidateQueueCount++;
            if(slot < 0 || candidateQueueCount > RegionBatchLayout.MAX_SECTIONS
                    || section.getChunkArea() != this || ownedSections[slot] != section) {
                candidateQueueComplete = false;
            } else {
                int word = slot >>> 6;
                long bit = 1L << (slot & 63);
                if((queuedCandidateBits[word] & bit) != 0L
                        || (candidateActiveWord(word) & bit) == 0L
                        || (!section.hasMainDirection() && !candidateIsDirectSeed(slot)))
                    candidateQueueComplete = false;
                queuedCandidateBits[word] |= bit;
            }
            candidateQueueRevision = sectionQueue.contentRevision();
        }
    }

    /**
     * Track fine-grid ownership independently of visibility. Old references can be
     * left behind when a ring section moves between still-resident coarse areas;
     * getOwnedSection validates the section's current area and local slot before use.
     */
    void registerSection(RenderSection section, int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        if(slot < 0)
            throw new IllegalStateException("Render section does not belong to selected ChunkArea");
        if(this.ownedSections[slot] != section) {
            // A published queue entry cannot silently change its identity.
            if(TRACK_GPU_CANDIDATE_PROVENANCE
                    && (queuedCandidateBits[slot >>> 6] & (1L << (slot & 63))) != 0L)
                candidateQueueComplete = false;
            this.ownedSections[slot] = section;
            this.candidateInputRevision++;
            this.candidateOwnershipRevision++;
        }
    }

    RenderSection getOwnedSection(int slot) {
        if(slot < 0 || slot >= this.ownedSections.length)
            throw new IndexOutOfBoundsException("Region section slot");
        RenderSection section = this.ownedSections[slot];
        if(section == null)
            return null;
        if(section.getChunkArea() != this
                || voxelSlot(section.xOffset, section.yOffset, section.zOffset) != slot) {
            this.ownedSections[slot] = null;
            this.candidateInputRevision++;
            this.candidateOwnershipRevision++;
            return null;
        }
        return section;
    }

    static boolean trackGpuCandidateProvenance() {
        return TRACK_GPU_CANDIDATE_PROVENANCE;
    }

    private void markCandidateBit(long[] bits, RenderSection section) {
        RegionBatchStats.gpuCandidateProducerMarks++;
        int slot = voxelSlot(section.xOffset, section.yOffset, section.zOffset);
        if(slot < 0 || section.getChunkArea() != this || ownedSections[slot] != section)
            return;
        long bit = 1L << (slot & 63);
        int word = slot >>> 6;
        if((bits[word] & bit) == 0) {
            bits[word] |= bit;
            candidateInputRevision++;
        }
    }

    // RenderSection.setLastFrame runs before WorldRenderer.addNode rejects a
    // graph-visited section by frustum. Those off-frustum marks are authoritative
    // GRAPH_VISIBLE inputs and cannot be inferred from sectionQueue alone.
    void markCandidateGraphVisit(RenderSection section) {
        if(TRACK_GPU_CANDIDATE_PROVENANCE)
            markCandidateBit(graphVisitBits, section);
    }

    // A producer mutation must revoke the proof before a cached draw can bypass
    // getOwnedSection/the per-slot direction audit. Rebuild it on fresh traversal;
    // do not attempt to repair a queue whose captured provenance is now stale.
    void candidateOwnerDeparted() {
        if(!TRACK_GPU_CANDIDATE_PROVENANCE) return;
        candidateInputRevision++;
        candidateOwnershipRevision++;
        candidateQueueComplete = false;
    }

    void candidateDirectionChanged(RenderSection section) {
        if(!TRACK_GPU_CANDIDATE_PROVENANCE) return;
        int slot = voxelSlot(section.xOffset, section.yOffset, section.zOffset);
        if(slot >= 0 && section.getChunkArea() == this && ownedSections[slot] == section
                && (queuedCandidateBits[slot >>> 6] & (1L << (slot & 63))) != 0L) {
            candidateInputRevision++;
            candidateQueueComplete = false;
        }
    }

    long getCandidateOwnershipRevision() {
        return candidateOwnershipRevision;
    }

    void candidateGeometryChanged(RenderSection section, TerrainRenderType type) {
        int slot = voxelSlot(section.xOffset, section.yOffset, section.zOffset);
        if(slot >= 0 && section.getChunkArea() == this && ownedSections[slot] == section)
            candidateGeometryRevisions[type.ordinal()]++;
    }

    long getCandidateGeometryRevision(TerrainRenderType type) {
        return candidateGeometryRevisions[type.ordinal()];
    }

    void generatedCandidateChanged(RenderSection section) {
        if(!TRACK_GPU_CANDIDATE_PROVENANCE) return;
        int slot = voxelSlot(section.xOffset, section.yOffset, section.zOffset);
        if(slot >= 0 && section.getChunkArea() == this && ownedSections[slot] == section)
            generatedCandidateRevision++;
    }

    long getGeneratedCandidateRevision() {
        return generatedCandidateRevision;
    }

    /** Draw-time O(1) gate replacing the old O(queue size) containment audit. */
    boolean candidateQueueCovered() {
        return TRACK_GPU_CANDIDATE_PROVENANCE && candidateQueueComplete
                && candidateQueueCount == sectionQueue.size()
                && candidateQueueRevision == sectionQueue.contentRevision();
    }

    long candidateQueuedWord(int word) {
        return queuedCandidateBits[word];
    }

    void copyCandidateProvenance(long[] graph, long[] seeds) {
        for(int i = 0; i < graphVisitBits.length; ++i) {
            graph[i] = !traversalFrameValid ? 0L : graphVisitBits[i];
            seeds[i] = directSeedBits[i];
        }
    }

    // Candidate v1 can only select producer-observed graph/direct slots.
    // Graph stamps from before frustum rejection must remain in the union.
    long candidateActiveWord(int word) {
        return (!traversalFrameValid ? 0L : graphVisitBits[word]) | directSeedBits[word];
    }

    boolean candidateIsDirectSeed(int slot) {
        return (directSeedBits[slot >>> 6] & (1L << (slot & 63))) != 0L;
    }

    boolean matchesCandidateProvenance(long[] graph, long[] seeds) {
        if(!TRACK_GPU_CANDIDATE_PROVENANCE)
            return false;
        for(int i = 0; i < graphVisitBits.length; ++i) {
            if(graph[i] != (!traversalFrameValid ? 0L : graphVisitBits[i])
                    || seeds[i] != directSeedBits[i])
                return false;
        }
        return true;
    }

    boolean isGraphVisible(RenderSection section) {
        return section != null && this.traversalFrameValid
                && section.getLastFrame() == this.traversalFrame;
    }

    public void resetQueue() {
        this.finishVisibilityRewrite();
        // Every traversal may change graph-visible slots, even when the final
        // CPU draw queue has identical members (addNode marks before frustum).
        this.candidateInputRevision++;
        if(TRACK_GPU_CANDIDATE_PROVENANCE) {
            Arrays.fill(graphVisitBits, 0L);
            Arrays.fill(directSeedBits, 0L);
            Arrays.fill(queuedCandidateBits, 0L);
            candidateQueueComplete = true;
            candidateQueueCount = 0;
        }
        this.sectionQueue.beginRewrite();
        if(TRACK_GPU_CANDIDATE_PROVENANCE)
            candidateQueueRevision = sectionQueue.contentRevision();
        this.visibilityRewritePending = true;
        this.traversalFrame = -1;
        this.traversalFrameValid = false;
    }

    long getCandidateInputRevision() {
        return this.candidateInputRevision;
    }

    long getVisibilityRevision() {
        this.finishVisibilityRewrite();
        return this.visibilityRevision;
    }

    private void finishVisibilityRewrite() {
        if(!this.visibilityRewritePending)
            return;

        if(!this.sectionQueue.endRewrite())
            this.visibilityRevision++;
        this.visibilityRewritePending = false;
    }

    public synchronized void setPosition(int x, int y, int z) {
        this.clearVoxels();
        this.position.set(x, y, z);
    }

    /**
     * Move a coarse ring slot to new world coordinates without throwing away its
     * Vulkan geometry buffers when the old region has already drained completely.
     * ChunkAreaManager deliberately has more coverage than the fine SectionGrid, so
     * the normal wrap path reaches this method after the old region's sections have
     * left the fine grid and released their suballocations.
     *
     * If that invariant is ever false, preserve the old safe behavior: detach the
     * whole allocation, give the recycled region fresh storage immediately, and
     * destroy the retired buffers only after every frame slot has crossed a safe
     * fence boundary.
     */
    synchronized void repositionForReuse(int x, int y, int z) {
        this.clearVoxels();
        if(this.drawBuffers.isAllocated()) {
            if(this.drawBuffers.hasLiveGeometry()) {
                RegionBatchStats.recordRegionBufferFallback();
                DrawBuffers retired = this.drawBuffers;
                this.drawBuffers = new DrawBuffers();
                AreaUploadManager manager = AreaUploadManager.INSTANCE;
                if(manager != null)
                    manager.enqueueFrameRetirement(retired::releaseBuffers);
                else
                    retired.releaseBuffers();
            } else {
                RegionBatchStats.recordRegionBufferReuse();
                this.drawBuffers.prepareForRegionReuse();
            }
        }
        this.position.set(x, y, z);
    }

    /** Compatibility entry point while callers transition to explicit generations. */
    public synchronized boolean canStageVoxels(int x, int y, int z, int size) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return false;
        return voxels == null ? RegionVoxelStore.canAcquireNew(size) : voxels.canPut(slot, size);
    }

    /** Compatibility entry point while callers transition to explicit generations. */
    public synchronized void publishVoxels(int x, int y, int z, SectionVoxelSnapshot snapshot) {
        this.publishVoxels(x, y, z, snapshot, 0L);
    }

    public synchronized void publishVoxels(int x, int y, int z,
                                           SectionVoxelSnapshot snapshot, long generation) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return;
        if (snapshot != null && (snapshot.x() != x || snapshot.y() != y || snapshot.z() != z))
            throw new IllegalArgumentException("Snapshot origin does not match its section");

        if (voxels == null && snapshot != null && RegionVoxelStore.ENABLED)
            voxels = new RegionVoxelStore();

        boolean stored = voxels != null && voxels.put(slot, snapshot);
        if (!stored) {
            if (gpuVoxels != null) {
                gpuVoxels.invalidate(slot, generation);
                gpuVoxels.invalidateLighting(slot, generation);
            }
            recoverGpuTerrainInputFailure(slot, generation,
                    "voxel_snapshot_store_rejected", null);
            return;
        }

        boolean queued;
        try {
            if (gpuVoxels == null)
                gpuVoxels = new RegionVoxelGpuStore();
            queued = gpuVoxels.upload(slot, snapshot, generation);
        } catch(RuntimeException error) {
            recoverGpuTerrainInputFailure(slot, generation,
                    "voxel_upload_exception", error);
            return;
        }
        if(!queued) {
            recoverGpuTerrainInputFailure(slot, generation,
                    "voxel_upload_rejected", null);
        }
    }

    /**
     * Publish lighting only for a section whose CPU voxel snapshot was accepted.
     * Null or rejected lighting explicitly revokes the same generation so a future
     * compute consumer can require matching voxel + lighting residency.
     */
    public synchronized void publishSparseLighting(int x, int y, int z,
                                                   GpuSparseLightingSnapshot snapshot,
                                                   long generation) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return;
        if (snapshot != null && (snapshot.x() != x || snapshot.y() != y || snapshot.z() != z))
            throw new IllegalArgumentException("Sparse lighting origin does not match its section");

        if (gpuVoxels == null || voxels == null || voxels.get(slot) == null) {
            if (gpuVoxels != null)
                gpuVoxels.invalidateLighting(slot, generation);
            recoverGpuTerrainInputFailure(slot, generation,
                    "lighting_without_voxel_input", null);
            return;
        }

        if (snapshot == null) {
            gpuVoxels.invalidateLighting(slot, generation);
            recoverGpuTerrainInputFailure(slot, generation,
                    "lighting_snapshot_missing", null);
            return;
        }

        boolean queued;
        try {
            queued = gpuVoxels.uploadLighting(slot, snapshot, generation);
        } catch(RuntimeException error) {
            recoverGpuTerrainInputFailure(slot, generation,
                    "lighting_upload_exception", error);
            return;
        }
        if(!queued) {
            recoverGpuTerrainInputFailure(slot, generation,
                    "lighting_upload_rejected", null);
            return;
        }

        if(GpuTerrainSectionMesherBridge.enabled()) {
            RenderSection section = getOwnedSection(slot);
            if(section != null) {
                // Voxel and lighting residency become discoverable once their copy
                // command buffer is submitted. Dispatch immediately afterward: the
                // compute submission uses the same graphics queue, so queue order
                // guarantees copy -> compute without waiting a frame fence.
                AreaUploadManager.INSTANCE.enqueuePostSubmitOp(() ->
                        GpuTerrainSectionMesherBridge.dispatch(this, section, generation));
            }
        }
    }

    private void recoverGpuTerrainInputFailure(int slot, long generation,
                                               String reason, RuntimeException error) {
        RenderSection section = getOwnedSection(slot);
        if(section == null)
            return;
        String detail = error == null ? null
                : error.getClass().getName() + ": " + error.getMessage();
        AreaUploadManager manager = AreaUploadManager.INSTANCE;
        if(manager == null) {
            GpuTerrainDiagnostics.record("input_publish", reason + "_recovery_queue_unavailable",
                    section, generation, detail);
            return;
        }

        // Publication normally holds RenderSection and then ChunkArea. Never acquire
        // the section monitor while this synchronized ChunkArea method is still active;
        // defer recovery to the terrain frame queue so the established lock order is
        // preserved and recovery still executes on the render thread.
        SectionVoxelSnapshot rejectedInput = voxels == null ? null : voxels.get(slot);
        manager.enqueueFrameOp(() -> {
            // An input failure never reaches the dispatch-finally consumer. Return
            // its staging too, including complete-CPU/shadow builds that do not
            // need recovery. Identity protects a replacement while this waits.
            if(GpuTerrainSectionMesherBridge.enabled() && rejectedInput != null)
                releaseConsumedVoxels(rejectedInput.x(), rejectedInput.y(), rejectedInput.z(),
                        generation, rejectedInput);
            if(section.getChunkArea() != this
                    || !section.stagedGpuTerrainCpuBypassed(generation))
                return;
            GpuTerrainDiagnostics.record("input_publish", reason,
                    section, generation, detail);
            GpuTerrainSectionMesherBridge.recoverCpuFallback(this, section, generation);
        });
    }

    public synchronized SectionVoxelSnapshot getVoxels(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        return voxels == null || slot < 0 ? null : voxels.get(slot);
    }

    /** Return consumed staging; GPU slices retire at their fence and visible output is untouched. */
    synchronized boolean releaseConsumedVoxels(int x, int y, int z, long generation,
                                               SectionVoxelSnapshot expected) {
        int slot = voxelSlot(x, y, z);
        if(voxels == null || slot < 0 || !voxels.releaseConsumed(slot, expected))
            return false;
        if(gpuVoxels != null)
            gpuVoxels.releaseConsumed(slot, generation);
        return true;
    }

    public synchronized RegionVoxelGpuStore.Residency getGpuVoxelResidency(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        return gpuVoxels == null || slot < 0 ? null : gpuVoxels.getResidency(slot);
    }

    public synchronized RegionVoxelGpuStore.Residency getGpuSparseLightingResidency(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        return gpuVoxels == null || slot < 0 ? null : gpuVoxels.getLightingResidency(slot);
    }

    public synchronized StorageBuffer getGpuVoxelPage(int pageIndex) {
        return gpuVoxels == null ? null : gpuVoxels.getPageBuffer(pageIndex);
    }

    public synchronized long getVoxelRevision() { return voxels == null ? 0L : voxels.revision(); }

    /**
     * Reserve optional GPU-generated terrain output for one section generation.
     * The store deliberately wraps the existing DrawBuffers without forcing an
     * allocation here; unsupported layers and stale generations can therefore fail
     * without creating a terrain buffer merely to discover that CPU fallback wins.
     */
    public synchronized GpuTerrainOutputStore.Reservation reserveGpuTerrainOutput(
            int x, int y, int z, TerrainRenderType type, long generation, int faceCapacity) {
        int slot = voxelSlot(x, y, z);
        if(slot < 0)
            return null;
        if(gpuTerrainOutputs == null)
            gpuTerrainOutputs = new GpuTerrainOutputStore(this.drawBuffers);
        return gpuTerrainOutputs.reserve(slot, type, generation, faceCapacity);
    }

    synchronized GpuTerrainOutputStore.StagedReservation reserveStagedGpuTerrainOutput(
            int x, int y, int z, TerrainRenderType type, long generation, int faceCapacity) {
        int slot = voxelSlot(x, y, z);
        if(slot < 0)
            return null;
        if(gpuTerrainOutputs == null)
            gpuTerrainOutputs = new GpuTerrainOutputStore(this.drawBuffers);
        return gpuTerrainOutputs.reserveStaged(slot, type, generation, faceCapacity);
    }

    public synchronized GpuTerrainOutputStore.Target getGpuTerrainOutputTarget(
            GpuTerrainOutputStore.Reservation reservation) {
        return gpuTerrainOutputs == null ? null : gpuTerrainOutputs.target(reservation);
    }

    public synchronized boolean publishGpuTerrainOutput(
            GpuTerrainOutputStore.Reservation reservation, int writtenFaces,
            boolean overflow) {
        return gpuTerrainOutputs != null
                && gpuTerrainOutputs.publish(reservation, writtenFaces, overflow);
    }

    public synchronized GpuTerrainOutputStore.Residency getGpuTerrainOutputResidency(
            int x, int y, int z, TerrainRenderType type) {
        int slot = voxelSlot(x, y, z);
        return gpuTerrainOutputs == null || slot < 0
                ? null : gpuTerrainOutputs.getResidency(slot, type);
    }

    public synchronized boolean publishGpuCandidates(TerrainRenderType type,
                                                     GpuRegionCandidateTable table) {
        if(type == null)
            throw new IllegalArgumentException("GPU candidate terrain layer must be present");
        if(table == null)
            throw new IllegalArgumentException("GPU region candidate table must be present");
        if(table.regionX() != position.x || table.regionY() != position.y
                || table.regionZ() != position.z)
            throw new IllegalArgumentException("GPU candidate table origin does not match region");
        int layer = type.ordinal();
        if(gpuCandidates[layer] == null)
            gpuCandidates[layer] = new GpuRegionCandidateGpuStore();
        return gpuCandidates[layer].upload(table);
    }

    public synchronized GpuRegionCandidateGpuStore.Residency getGpuCandidateResidency(
            TerrainRenderType type) {
        if(type == null)
            throw new IllegalArgumentException("GPU candidate terrain layer must be present");
        GpuRegionCandidateGpuStore store = gpuCandidates[type.ordinal()];
        return store == null ? null : store.getResidency();
    }

    public synchronized void invalidateGpuCandidates(TerrainRenderType type, long generation) {
        if(type == null)
            throw new IllegalArgumentException("GPU candidate terrain layer must be present");
        GpuRegionCandidateGpuStore store = gpuCandidates[type.ordinal()];
        if(store != null)
            store.invalidate(generation);
    }

    /** Compatibility entry points for the original single-table residency smoke. */
    public synchronized boolean publishGpuCandidates(GpuRegionCandidateTable table) {
        return publishGpuCandidates(TerrainRenderType.SOLID, table);
    }

    public synchronized GpuRegionCandidateGpuStore.Residency getGpuCandidateResidency() {
        return getGpuCandidateResidency(TerrainRenderType.SOLID);
    }

    public synchronized void invalidateGpuCandidates(long generation) {
        invalidateGpuCandidates(TerrainRenderType.SOLID, generation);
    }

    /** Compatibility entry point while callers transition to explicit generations. */
    public synchronized void removeVoxels(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        if(slot < 0) return;
        long generation = 0L;
        if(gpuVoxels != null)
            generation = Math.max(generation,
                    nextGeneration(gpuVoxels.getResidency(slot).generation()));
        if(gpuTerrainOutputs != null)
            generation = Math.max(generation,
                    nextGeneration(gpuTerrainOutputs.getSectionGeneration(slot)));
        this.removeVoxels(x, y, z, generation);
    }

    public synchronized void removeVoxels(int x, int y, int z, long generation) {
        this.removeVoxels(x, y, z, generation, false);
    }

    synchronized void removeVoxels(int x, int y, int z, long generation,
                                   boolean retainGpuTerrainOutput) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return;
        if (voxels != null) voxels.remove(slot);
        if (gpuVoxels != null) {
            gpuVoxels.invalidate(slot, generation);
            gpuVoxels.invalidateLighting(slot, generation);
        }
        if(!retainGpuTerrainOutput && gpuTerrainOutputs != null)
            gpuTerrainOutputs.invalidateSection(slot, generation);
    }

    synchronized void invalidateGpuTerrainOutput(int x, int y, int z, long generation) {
        int slot = voxelSlot(x, y, z);
        if(slot >= 0 && gpuTerrainOutputs != null)
            gpuTerrainOutputs.invalidateSection(slot, generation);
    }

    private static long nextGeneration(long generation) {
        return generation == Long.MAX_VALUE ? Long.MAX_VALUE : generation + 1L;
    }

    private int voxelSlot(int x, int y, int z) {
        int dx = x - position.x, dy = y - position.y, dz = z - position.z;
        // A fine section may be releasing its old origin after this coarse slot wrapped.
        if ((dx | dy | dz) < 0 || dx >= 128 || dy >= 128 || dz >= 128) return -1;
        return RegionBatchLayout.packSection(dx, dy, dz);
    }

    private void clearVoxels() {
        // GPU-generated output suballocates the area vertex buffer, so release those
        // reservations before deciding whether the region draw buffers are empty and
        // reusable at new world coordinates.
        if(gpuTerrainOutputs != null) {
            gpuTerrainOutputs.close();
            gpuTerrainOutputs = null;
        }
        if (voxels != null) voxels.clear();
        if (gpuVoxels != null) {
            gpuVoxels.close();
            gpuVoxels = null;
        }
        for(int layer = 0; layer < gpuCandidates.length; ++layer) {
            if(gpuCandidates[layer] != null) {
                gpuCandidates[layer].close();
                gpuCandidates[layer] = null;
            }
        }
        Arrays.fill(this.ownedSections, null);
        Arrays.fill(graphVisitBits, 0L);
        Arrays.fill(directSeedBits, 0L);
        Arrays.fill(queuedCandidateBits, 0L);
        candidateQueueComplete = false;
        candidateQueueCount = 0;
        this.candidateInputRevision++;
        this.candidateOwnershipRevision++;
        this.traversalFrame = -1;
        this.traversalFrameValid = false;
    }

    public synchronized void releaseBuffers() {
        this.clearVoxels();
        this.drawBuffers.releaseBuffers();
    }
}
