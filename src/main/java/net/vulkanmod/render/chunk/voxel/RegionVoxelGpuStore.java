package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.RegionBatchLayout;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Region-owned, fixed-page GPU residency for serialized section voxel snapshots.
 *
 * <p>The CPU {@link RegionVoxelStore} remains authoritative. This store only
 * publishes a section as GPU-resident after its staged copy has been submitted in
 * graphics-queue order. Replacements always allocate a fresh slice; old slices are
 * retired through the frame fence domain before the allocator may reuse them.</p>
 */
public final class RegionVoxelGpuStore {
    static final int PAGE_BYTES = 512 * 1024;
    static final int PAGE_ALIGNMENT = 16;
    static final int GLOBAL_BUDGET_BYTES = 32 * 1024 * 1024;

    private static final PageBudget GLOBAL_BUDGET = new PageBudget(GLOBAL_BUDGET_BYTES);

    private final PageBudget budget;
    private final List<Page> pages = new ArrayList<>();
    private final long[] generations = new long[RegionBatchLayout.MAX_SECTIONS];
    private final Resident[] resident = new Resident[RegionBatchLayout.MAX_SECTIONS];
    private final Pending[] pending = new Pending[RegionBatchLayout.MAX_SECTIONS];
    private boolean closed;

    public RegionVoxelGpuStore() {
        this(GLOBAL_BUDGET);
    }

    RegionVoxelGpuStore(PageBudget budget) {
        this.budget = budget;
    }

    /**
     * Queue a fresh GPU copy for this generation. The previous generation remains
     * physically allocated until the replacement is submitted, but it is no longer
     * discoverable as valid residency for the new generation.
     */
    public synchronized boolean upload(int slot, SectionVoxelSnapshot snapshot, long generation) {
        checkSlot(slot);
        if(snapshot == null)
            throw new IllegalArgumentException("Voxel snapshot must be present");
        if(generation < this.generations[slot])
            return false;

        this.generations[slot] = generation;
        this.pending[slot] = null;

        if(this.closed || !uploadPathReady() || snapshot.byteSize() > PAGE_BYTES) {
            discardResident(slot);
            return false;
        }

        Slice slice = allocateSlice(snapshot.byteSize());
        if(slice == null) {
            discardResident(slot);
            return false;
        }

        Pending token = new Pending(slice, generation);
        this.pending[slot] = token;

        ByteBuffer bytes = MemoryUtil.memAlloc(snapshot.byteSize());
        try {
            snapshot.writeTo(bytes);
            bytes.flip();
            AreaUploadManager.INSTANCE.uploadStorageAsync(
                    slice.page.buffer, slice.allocation.offset, bytes,
                    () -> completeUpload(slot, token));
        } catch(RuntimeException | Error error) {
            if(this.pending[slot] == token)
                this.pending[slot] = null;
            retire(slice);
            throw error;
        } finally {
            MemoryUtil.memFree(bytes);
        }

        return true;
    }

    /** Immediately revoke discoverable residency while retaining GPU-safe lifetime. */
    public synchronized void invalidate(int slot, long generation) {
        checkSlot(slot);
        if(generation < this.generations[slot])
            return;

        this.generations[slot] = generation;
        // A queued upload cannot be recycled until its submission callback runs.
        // Clearing the token makes that callback retire rather than publish it.
        this.pending[slot] = null;
        discardResident(slot);
    }

    public synchronized Residency getResidency(int slot) {
        checkSlot(slot);
        long generation = this.generations[slot];
        Resident current = this.resident[slot];
        if(current == null || current.generation != generation)
            return Residency.invalid(generation);

        return new Residency(current.slice.page.index,
                current.slice.allocation.offset,
                current.slice.allocation.byteLength,
                generation, true);
    }

    public synchronized StorageBuffer getPageBuffer(int pageIndex) {
        if(this.closed || pageIndex < 0 || pageIndex >= this.pages.size())
            return null;
        return this.pages.get(pageIndex).buffer;
    }

    public synchronized int pageCount() {
        return this.pages.size();
    }

    /**
     * Revoke all residency and retire whole pages. Buffer destruction and global
     * budget release share the same frame slot; MemoryManager frees buffers before
     * running frame operations, so capacity is not re-advertised prematurely.
     */
    public synchronized void close() {
        if(this.closed)
            return;
        this.closed = true;

        for(int i = 0; i < this.resident.length; ++i) {
            this.resident[i] = null;
            this.pending[i] = null;
        }

        MemoryManager memoryManager = MemoryManager.getInstance();
        for(Page page : this.pages) {
            page.buffer.freeBuffer();
            memoryManager.addFrameOp(() -> this.budget.release(PAGE_BYTES));
        }
        this.pages.clear();
    }

    static String describeGlobalBudget() {
        return GLOBAL_BUDGET.describe();
    }

    private boolean uploadPathReady() {
        return AreaUploadManager.INSTANCE != null
                && MemoryManager.getInstance() != null
                && MemoryTypes.GPU_MEM != null;
    }

    private Slice allocateSlice(int byteLength) {
        for(Page page : this.pages) {
            RegionVoxelPageAllocator.Allocation allocation = page.allocator.allocate(byteLength);
            if(allocation != null)
                return new Slice(page, allocation);
        }

        if(!this.budget.tryReserve(PAGE_BYTES))
            return null;

        StorageBuffer buffer;
        try {
            buffer = new StorageBuffer(PAGE_BYTES, MemoryTypes.GPU_MEM);
        } catch(RuntimeException error) {
            this.budget.release(PAGE_BYTES);
            // The fixed page is an optimization. A Vulkan allocation failure must
            // leave the section on the existing CPU path instead of growing or
            // turning ordinary memory pressure into a renderer crash.
            if(error.getMessage() != null && error.getMessage().startsWith("Failed to create buffer:"))
                return null;
            throw error;
        }

        Page page = new Page(this.pages.size(), buffer);
        this.pages.add(page);
        RegionVoxelPageAllocator.Allocation allocation = page.allocator.allocate(byteLength);
        if(allocation == null)
            throw new IllegalStateException("Fresh voxel page cannot fit one bounded snapshot");
        return new Slice(page, allocation);
    }

    private synchronized void completeUpload(int slot, Pending token) {
        if(this.closed || this.pending[slot] != token || this.generations[slot] != token.generation) {
            retire(token.slice);
            return;
        }

        this.pending[slot] = null;
        Resident previous = this.resident[slot];
        this.resident[slot] = new Resident(token.slice, token.generation);
        if(previous != null)
            retire(previous.slice);
    }

    private void discardResident(int slot) {
        Resident previous = this.resident[slot];
        this.resident[slot] = null;
        if(previous != null)
            retire(previous.slice);
    }

    private void retire(Slice slice) {
        if(slice == null || this.closed)
            return;

        MemoryManager memoryManager = MemoryManager.getInstance();
        if(memoryManager == null)
            return;

        memoryManager.addFrameOp(() -> {
            synchronized(RegionVoxelGpuStore.this) {
                if(!RegionVoxelGpuStore.this.closed)
                    slice.page.allocator.free(slice.allocation);
            }
        });
    }

    private static void checkSlot(int slot) {
        if(slot < 0 || slot >= RegionBatchLayout.MAX_SECTIONS)
            throw new IllegalArgumentException("Invalid region section slot");
    }

    public record Residency(int pageIndex, int byteOffset, int byteLength,
                            long generation, boolean valid) {
        static Residency invalid(long generation) {
            return new Residency(-1, 0, 0, generation, false);
        }
    }

    static final class PageBudget {
        private final int maxBytes;
        private int usedBytes;
        private long rejectedPages;

        PageBudget(int maxBytes) {
            if(maxBytes <= 0)
                throw new IllegalArgumentException("GPU voxel budget must be positive");
            this.maxBytes = maxBytes;
        }

        synchronized boolean tryReserve(int bytes) {
            if(bytes <= 0)
                throw new IllegalArgumentException("GPU voxel reservation must be positive");
            if(bytes > this.maxBytes - this.usedBytes) {
                this.rejectedPages++;
                return false;
            }
            this.usedBytes += bytes;
            return true;
        }

        synchronized void release(int bytes) {
            if(bytes <= 0 || bytes > this.usedBytes)
                throw new IllegalStateException("GPU voxel budget accounting underflow");
            this.usedBytes -= bytes;
        }

        synchronized int usedBytes() {
            return this.usedBytes;
        }

        synchronized long rejectedPages() {
            return this.rejectedPages;
        }

        synchronized String describe() {
            return "Terrain GPU voxel pages: " + this.usedBytes / 1024 + "/"
                    + this.maxBytes / 1024 + " KiB, rejected pages " + this.rejectedPages;
        }
    }

    private static final class Page {
        final int index;
        final StorageBuffer buffer;
        final RegionVoxelPageAllocator allocator =
                new RegionVoxelPageAllocator(PAGE_BYTES, PAGE_ALIGNMENT);

        Page(int index, StorageBuffer buffer) {
            this.index = index;
            this.buffer = buffer;
        }
    }

    private record Slice(Page page, RegionVoxelPageAllocator.Allocation allocation) {}
    private record Resident(Slice slice, long generation) {}
    private record Pending(Slice slice, long generation) {}
}
