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

    final ResettableQueue<RenderSection> sectionQueue = new ResettableQueue<>();
    private long visibilityRevision;
    private boolean visibilityRewritePending;
    private short traversalFrame = -1;

    public ChunkArea(int i, Vector3i origin) {
        this.index = i;
        this.position = origin;
        this.drawBuffers = new DrawBuffers();
    }

    public void updateFrustum(VFrustum frustum) {
        int frustumResult = frustum.cubeInFrustum(this.position.x(), this.position.y(), this.position.z(),
                this.position.x() + (8 << 4) , this.position.y() + (8 << 4), this.position.z() + (8 << 4));

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

                        frustumResult = frustum.cubeInFrustum(xMin, yMin, zMin, xMax , yMax, zMax);
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
                        } else {
                            int end = beginIdx + 8;
                            for(int i = beginIdx; i < end; ++i) this.inFrustum[i] = (byte) frustumResult;
                        }
                    }
                }
            }
        } else {
            Arrays.fill(inFrustum, (byte) frustumResult);
        }
    }

    public byte getFrustumIndex(BlockPos pos) { return getFrustumIndex(pos.getX(), pos.getY(), pos.getZ()); }

    public byte getFrustumIndex(int x, int y, int z) {
        int dx = x - this.position.x;
        int dy = y - this.position.y;
        int dz = z - this.position.z;
        int i = (dx >> 6 << 5) + (dy >> 6 << 4) + (dz >> 6 << 3);
        int xSub = (dx >> 3) & 0b100;
        int ySub = (dy >> 4) & 0b10;
        int zSub = (dz >> 5) & 0b1;
        return (byte) (i + xSub + ySub + zSub);
    }

    public byte inFrustum(byte i) { return this.inFrustum[i]; }

    public DrawBuffers getDrawBuffers() {
        if(!this.drawBuffers.isAllocated()) drawBuffers.allocateBuffers();
        return this.drawBuffers;
    }

    public void addSection(RenderSection section) {
        this.traversalFrame = section.getLastFrame();
        this.sectionQueue.add(section);
    }

    void registerSection(RenderSection section, int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        if(slot < 0) throw new IllegalStateException("Render section does not belong to selected ChunkArea");
        this.ownedSections[slot] = section;
    }

    RenderSection getOwnedSection(int slot) {
        if(slot < 0 || slot >= this.ownedSections.length) throw new IndexOutOfBoundsException("Region section slot");
        RenderSection section = this.ownedSections[slot];
        if(section == null) return null;
        if(section.getChunkArea() != this || voxelSlot(section.xOffset, section.yOffset, section.zOffset) != slot) {
            this.ownedSections[slot] = null;
            return null;
        }
        return section;
    }

    boolean isGraphVisible(RenderSection section) {
        return section != null && this.traversalFrame != -1 && section.getLastFrame() == this.traversalFrame;
    }

    public void resetQueue() {
        this.finishVisibilityRewrite();
        this.sectionQueue.beginRewrite();
        this.visibilityRewritePending = true;
        this.traversalFrame = -1;
    }

    long getVisibilityRevision() {
        this.finishVisibilityRewrite();
        return this.visibilityRevision;
    }

    private void finishVisibilityRewrite() {
        if(!this.visibilityRewritePending) return;
        if(!this.sectionQueue.endRewrite()) this.visibilityRevision++;
        this.visibilityRewritePending = false;
    }

    public synchronized void setPosition(int x, int y, int z) {
        this.clearVoxels();
        this.position.set(x, y, z);
    }

    synchronized void repositionForReuse(int x, int y, int z) {
        this.clearVoxels();
        if(this.drawBuffers.isAllocated()) {
            if(this.drawBuffers.hasLiveGeometry()) {
                RegionBatchStats.recordRegionBufferFallback();
                DrawBuffers retired = this.drawBuffers;
                this.drawBuffers = new DrawBuffers();
                AreaUploadManager manager = AreaUploadManager.INSTANCE;
                if(manager != null) manager.enqueueFrameRetirement(retired::releaseBuffers);
                else retired.releaseBuffers();
            } else {
                RegionBatchStats.recordRegionBufferReuse();
                this.drawBuffers.prepareForRegionReuse();
            }
        }
        this.position.set(x, y, z);
    }

    public synchronized void publishVoxels(int x, int y, int z, SectionVoxelSnapshot snapshot) {
        this.publishVoxels(x, y, z, snapshot, 0L);
    }

    public synchronized void publishVoxels(int x, int y, int z,
                                           SectionVoxelSnapshot snapshot, long generation) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return;
        if (snapshot != null && (snapshot.x() != x || snapshot.y() != y || snapshot.z() != z))
            throw new IllegalArgumentException("Snapshot origin does not match its section");
        if (voxels == null && snapshot != null && RegionVoxelStore.ENABLED) voxels = new RegionVoxelStore();
        boolean stored = voxels != null && voxels.put(slot, snapshot);
        if (!stored) {
            if (gpuVoxels != null) {
                gpuVoxels.invalidate(slot, generation);
                gpuVoxels.invalidateLighting(slot, generation);
            }
            return;
        }
        if (gpuVoxels == null) gpuVoxels = new RegionVoxelGpuStore();
        gpuVoxels.upload(slot, snapshot, generation);
    }

    public synchronized void publishSparseLighting(int x, int y, int z,
                                                   GpuSparseLightingSnapshot snapshot,
                                                   long generation) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return;
        if (snapshot != null && (snapshot.x() != x || snapshot.y() != y || snapshot.z() != z))
            throw new IllegalArgumentException("Sparse lighting origin does not match its section");
        if (gpuVoxels == null || voxels == null || voxels.get(slot) == null) {
            if (gpuVoxels != null) gpuVoxels.invalidateLighting(slot, generation);
            return;
        }
        if (snapshot == null) {
            gpuVoxels.invalidateLighting(slot, generation);
            return;
        }
        boolean queued = gpuVoxels.uploadLighting(slot, snapshot, generation);
        if(queued && GpuTerrainSectionMesherBridge.enabled()) {
            RenderSection section = getOwnedSection(slot);
            if(section != null) {
                AreaUploadManager.INSTANCE.enqueueFrameOp(() ->
                        GpuTerrainSectionMesherBridge.dispatch(this, section, generation));
            }
        }
    }

    public synchronized SectionVoxelSnapshot getVoxels(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        return voxels == null || slot < 0 ? null : voxels.get(slot);
    }

    public synchronized RegionVoxelGpuStore.Residency getGpuVoxelResidency(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        return gpuVoxels == null || slot < 0 ? null : gpuVoxels.getResidency(slot);
    }

    public synchronized RegionVoxelGpuStore.Residency getGpuSparseLightingResidency(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        return gpuVoxels == null || slot < 0 ? null : gpuVoxels.getLightingResidency(slot);
    }

    public synchronized StorageBuffer getGpuVoxelPage(int pageIndex) { return gpuVoxels == null ? null : gpuVoxels.getPageBuffer(pageIndex); }
    public synchronized long getVoxelRevision() { return voxels == null ? 0L : voxels.revision(); }

    public synchronized GpuTerrainOutputStore.Reservation reserveGpuTerrainOutput(
            int x, int y, int z, TerrainRenderType type, long generation, int faceCapacity) {
        int slot = voxelSlot(x, y, z);
        if(slot < 0) return null;
        if(gpuTerrainOutputs == null) gpuTerrainOutputs = new GpuTerrainOutputStore(this.drawBuffers);
        return gpuTerrainOutputs.reserve(slot, type, generation, faceCapacity);
    }

    public synchronized GpuTerrainOutputStore.Target getGpuTerrainOutputTarget(GpuTerrainOutputStore.Reservation reservation) {
        return gpuTerrainOutputs == null ? null : gpuTerrainOutputs.target(reservation);
    }

    public synchronized boolean publishGpuTerrainOutput(GpuTerrainOutputStore.Reservation reservation,
                                                        int writtenFaces, boolean overflow) {
        return gpuTerrainOutputs != null && gpuTerrainOutputs.publish(reservation, writtenFaces, overflow);
    }

    public synchronized GpuTerrainOutputStore.Residency getGpuTerrainOutputResidency(
            int x, int y, int z, TerrainRenderType type) {
        int slot = voxelSlot(x, y, z);
        return gpuTerrainOutputs == null || slot < 0 ? null : gpuTerrainOutputs.getResidency(slot, type);
    }

    public synchronized boolean publishGpuCandidates(TerrainRenderType type, GpuRegionCandidateTable table) {
        if(type == null) throw new IllegalArgumentException("GPU candidate terrain layer must be present");
        if(table == null) throw new IllegalArgumentException("GPU region candidate table must be present");
        if(table.regionX() != position.x || table.regionY() != position.y || table.regionZ() != position.z)
            throw new IllegalArgumentException("GPU candidate table origin does not match region");
        int layer = type.ordinal();
        if(gpuCandidates[layer] == null) gpuCandidates[layer] = new GpuRegionCandidateGpuStore();
        return gpuCandidates[layer].upload(table);
    }

    public synchronized GpuRegionCandidateGpuStore.Residency getGpuCandidateResidency(TerrainRenderType type) {
        if(type == null) throw new IllegalArgumentException("GPU candidate terrain layer must be present");
        GpuRegionCandidateGpuStore store = gpuCandidates[type.ordinal()];
        return store == null ? null : store.getResidency();
    }

    public synchronized void invalidateGpuCandidates(TerrainRenderType type, long generation) {
        if(type == null) throw new IllegalArgumentException("GPU candidate terrain layer must be present");
        GpuRegionCandidateGpuStore store = gpuCandidates[type.ordinal()];
        if(store != null) store.invalidate(generation);
    }

    public synchronized boolean publishGpuCandidates(GpuRegionCandidateTable table) { return publishGpuCandidates(TerrainRenderType.SOLID, table); }
    public synchronized GpuRegionCandidateGpuStore.Residency getGpuCandidateResidency() { return getGpuCandidateResidency(TerrainRenderType.SOLID); }
    public synchronized void invalidateGpuCandidates(long generation) { invalidateGpuCandidates(TerrainRenderType.SOLID, generation); }

    public synchronized void removeVoxels(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        if(slot < 0) return;
        long generation = 0L;
        if(gpuVoxels != null) generation = Math.max(generation, nextGeneration(gpuVoxels.getResidency(slot).generation()));
        if(gpuTerrainOutputs != null) generation = Math.max(generation, nextGeneration(gpuTerrainOutputs.getSectionGeneration(slot)));
        this.removeVoxels(x, y, z, generation);
    }

    public synchronized void removeVoxels(int x, int y, int z, long generation) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return;
        if (voxels != null) voxels.remove(slot);
        if (gpuVoxels != null) {
            gpuVoxels.invalidate(slot, generation);
            gpuVoxels.invalidateLighting(slot, generation);
        }
        if(gpuTerrainOutputs != null) gpuTerrainOutputs.invalidateSection(slot, generation);
    }

    private static long nextGeneration(long generation) { return generation == Long.MAX_VALUE ? Long.MAX_VALUE : generation + 1L; }

    private int voxelSlot(int x, int y, int z) {
        int dx = x - position.x, dy = y - position.y, dz = z - position.z;
        if ((dx | dy | dz) < 0 || dx >= 128 || dy >= 128 || dz >= 128) return -1;
        return RegionBatchLayout.packSection(dx, dy, dz);
    }

    private void clearVoxels() {
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
        this.traversalFrame = -1;
    }

    public synchronized void releaseBuffers() {
        this.clearVoxels();
        this.drawBuffers.releaseBuffers();
    }
}
