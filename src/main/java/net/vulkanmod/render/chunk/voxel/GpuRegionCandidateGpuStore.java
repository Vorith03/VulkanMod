package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.RegionBatchLayout;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** Generation-owned device-local residency for one region candidate table. */
public final class GpuRegionCandidateGpuStore implements AutoCloseable {
    static final int MAX_TABLE_BYTES = (GpuRegionCandidateTable.HEADER_WORDS
            + RegionBatchLayout.MAX_SECTIONS * GpuRegionCandidateTable.RECORD_WORDS)
            * Integer.BYTES;
    static final int GLOBAL_BUDGET_BYTES = 16 * 1024 * 1024;
    private static final Budget GLOBAL_BUDGET = new Budget(GLOBAL_BUDGET_BYTES);

    private final Budget budget;
    private long generation = -1L;
    private Pending pending;
    private Resident resident;
    private boolean closed;

    public GpuRegionCandidateGpuStore() {
        this(GLOBAL_BUDGET);
    }

    GpuRegionCandidateGpuStore(Budget budget) {
        this.budget = budget;
    }

    /** Queue an allocate-then-publish replacement; stale generations are ignored. */
    public synchronized boolean upload(GpuRegionCandidateTable table) {
        if(table == null)
            throw new IllegalArgumentException("GPU region candidate table must be present");
        if(table.generation() < generation)
            return false;

        generation = table.generation();
        retire(pending);
        pending = null;
        if(closed || !uploadPathReady() || table.byteSize() > MAX_TABLE_BYTES) {
            discardResident();
            return false;
        }
        if(!budget.tryReserve(MAX_TABLE_BYTES)) {
            discardResident();
            return false;
        }

        StorageBuffer buffer;
        try {
            buffer = new StorageBuffer(MAX_TABLE_BYTES, MemoryTypes.GPU_MEM);
        } catch(RuntimeException error) {
            budget.release(MAX_TABLE_BYTES);
            discardResident();
            if(error.getMessage() != null && error.getMessage().startsWith("Failed to create buffer:"))
                return false;
            throw error;
        }

        Pending token = new Pending(buffer, table.generation(), table.regionX(), table.regionY(),
                table.regionZ(), table.byteSize());
        pending = token;
        ByteBuffer bytes = MemoryUtil.memAlloc(table.byteSize());
        try {
            table.writeTo(bytes);
            bytes.flip();
            AreaUploadManager.INSTANCE.uploadStorageAsync(buffer, 0L, bytes,
                    () -> completeUpload(token));
        } catch(RuntimeException | Error error) {
            if(pending == token) pending = null;
            retire(token);
            throw error;
        } finally {
            MemoryUtil.memFree(bytes);
        }
        return true;
    }

    /** Revoke the table immediately; physical storage remains fence-retired. */
    public synchronized void invalidate(long generation) {
        if(generation < this.generation)
            return;
        this.generation = generation;
        retire(pending);
        pending = null;
        discardResident();
    }

    public synchronized Residency getResidency() {
        if(closed || resident == null || resident.generation != generation)
            return Residency.invalid(generation);
        return new Residency(resident.buffer, resident.byteLength, resident.generation,
                resident.regionX, resident.regionY, resident.regionZ, true);
    }

    @Override
    public synchronized void close() {
        if(closed) return;
        closed = true;
        retire(pending);
        pending = null;
        discardResident();
    }

    static String describeGlobalBudget() {
        return GLOBAL_BUDGET.describe();
    }

    private boolean uploadPathReady() {
        return AreaUploadManager.INSTANCE != null
                && MemoryManager.getInstance() != null
                && MemoryTypes.GPU_MEM != null;
    }

    private synchronized void completeUpload(Pending token) {
        if(closed || pending != token || generation != token.generation) {
            retire(token);
            return;
        }
        pending = null;
        Resident previous = resident;
        resident = new Resident(token.buffer, token.generation, token.regionX,
                token.regionY, token.regionZ, token.byteLength);
        token.transferred = true;
        retire(previous);
    }

    private void discardResident() {
        Resident previous = resident;
        resident = null;
        retire(previous);
    }

    private void retire(Allocation allocation) {
        if(allocation == null || allocation.retired || allocation.transferred)
            return;
        allocation.retired = true;
        allocation.buffer.freeBuffer();
        MemoryManager manager = MemoryManager.getInstance();
        if(manager != null)
            manager.addFrameOp(() -> budget.release(MAX_TABLE_BYTES));
        else
            budget.release(MAX_TABLE_BYTES);
    }

    public record Residency(StorageBuffer buffer, int byteLength, long generation,
                            int regionX, int regionY, int regionZ, boolean valid) {
        static Residency invalid(long generation) {
            return new Residency(null, 0, generation, 0, 0, 0, false);
        }
    }

    static final class Budget {
        private final int maxBytes;
        private int usedBytes;
        private long rejectedAllocations;

        Budget(int maxBytes) {
            if(maxBytes <= 0)
                throw new IllegalArgumentException("GPU candidate budget must be positive");
            this.maxBytes = maxBytes;
        }

        synchronized boolean tryReserve(int bytes) {
            if(bytes <= 0)
                throw new IllegalArgumentException("GPU candidate reservation must be positive");
            if(bytes > maxBytes - usedBytes) {
                rejectedAllocations++;
                return false;
            }
            usedBytes += bytes;
            return true;
        }

        synchronized void release(int bytes) {
            if(bytes <= 0 || bytes > usedBytes)
                throw new IllegalStateException("GPU candidate budget accounting underflow");
            usedBytes -= bytes;
        }

        synchronized String describe() {
            return "Terrain GPU candidate buffers: " + usedBytes / 1024 + "/"
                    + maxBytes / 1024 + " KiB, rejected allocations " + rejectedAllocations;
        }
    }

    private abstract static class Allocation {
        final StorageBuffer buffer;
        final long generation;
        final int regionX;
        final int regionY;
        final int regionZ;
        final int byteLength;
        boolean retired;
        boolean transferred;

        Allocation(StorageBuffer buffer, long generation, int regionX, int regionY,
                   int regionZ, int byteLength) {
            this.buffer = buffer;
            this.generation = generation;
            this.regionX = regionX;
            this.regionY = regionY;
            this.regionZ = regionZ;
            this.byteLength = byteLength;
        }
    }

    private static final class Pending extends Allocation {
        Pending(StorageBuffer buffer, long generation, int regionX, int regionY,
                int regionZ, int byteLength) {
            super(buffer, generation, regionX, regionY, regionZ, byteLength);
        }
    }

    private static final class Resident extends Allocation {
        Resident(StorageBuffer buffer, long generation, int regionX, int regionY,
                 int regionZ, int byteLength) {
            super(buffer, generation, regionX, regionY, regionZ, byteLength);
        }
    }
}
