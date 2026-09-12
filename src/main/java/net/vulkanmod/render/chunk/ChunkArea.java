package net.vulkanmod.render.chunk;

import net.minecraft.core.BlockPos;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;
import net.vulkanmod.render.chunk.util.ResettableQueue;
import org.joml.FrustumIntersection;
import org.joml.Vector3i;

import java.util.Arrays;

public class ChunkArea {
    public final int index;
    private final byte[] inFrustum = new byte[64];

    final Vector3i position;

    DrawBuffers drawBuffers;
    private RegionVoxelStore voxels;

    final ResettableQueue<RenderSection> sectionQueue = new ResettableQueue<>();
    private long visibilityRevision;
    private boolean visibilityRewritePending;

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
        this.sectionQueue.add(section);
    }

    public void resetQueue() {
        this.finishVisibilityRewrite();
        this.sectionQueue.beginRewrite();
        this.visibilityRewritePending = true;
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
     * If that invariant is ever false, preserve the old safe behavior: retire the
     * whole allocation and let the new region lazily allocate fresh storage.
     */
    synchronized void repositionForReuse(int x, int y, int z) {
        this.clearVoxels();
        if(this.drawBuffers.isAllocated()) {
            if(this.drawBuffers.hasLiveGeometry()) {
                RegionBatchStats.recordRegionBufferFallback();
                this.drawBuffers.releaseBuffers();
            } else {
                RegionBatchStats.recordRegionBufferReuse();
                this.drawBuffers.prepareForRegionReuse();
            }
        }
        this.position.set(x, y, z);
    }

    public synchronized void publishVoxels(int x, int y, int z, SectionVoxelSnapshot snapshot) {
        int slot = voxelSlot(x, y, z);
        if (slot < 0) return;
        if (snapshot != null && (snapshot.x() != x || snapshot.y() != y || snapshot.z() != z))
            throw new IllegalArgumentException("Snapshot origin does not match its section");
        if (voxels == null && snapshot != null && RegionVoxelStore.ENABLED)
            voxels = new RegionVoxelStore();
        if (voxels != null) voxels.put(slot, snapshot);
    }

    public synchronized SectionVoxelSnapshot getVoxels(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        return voxels == null || slot < 0 ? null : voxels.get(slot);
    }

    public synchronized long getVoxelRevision() { return voxels == null ? 0L : voxels.revision(); }

    public synchronized void removeVoxels(int x, int y, int z) {
        int slot = voxelSlot(x, y, z);
        if (voxels != null && slot >= 0) voxels.remove(slot);
    }

    private int voxelSlot(int x, int y, int z) {
        int dx = x - position.x, dy = y - position.y, dz = z - position.z;
        // A fine section may be releasing its old origin after this coarse slot wrapped.
        if ((dx | dy | dz) < 0 || dx >= 128 || dy >= 128 || dz >= 128) return -1;
        return RegionBatchLayout.packSection(dx, dy, dz);
    }

    private void clearVoxels() { if (voxels != null) voxels.clear(); }

    public synchronized void releaseBuffers() {
        this.clearVoxels();
        this.drawBuffers.releaseBuffers();
    }
}
