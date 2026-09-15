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
 * Region-owned, fixed-page GPU residency for serialized section terrain inputs.
 *
 * <p>Voxel and sparse-lighting records share the same bounded page allocator but
 * keep independent generation/publication state. A record is discoverable only
 * after its staged copy has been submitted in graphics-queue order. Replacements
 * always allocate a fresh slice; old slices retire through the frame fence domain
 * before the allocator may reuse them.</p>
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
    private final long[] lightingGenerations = new long[RegionBatchLayout.MAX_SECTIONS];
    private final Resident[] lightingResident = new Resident[RegionBatchLayout.MAX_SECTIONS];
    private final Pending[] lightingPending = new Pending[RegionBatchLayout.MAX_SECTIONS];
    private boolean closed;

    public RegionVoxelGpuStore() {
        this(GLOBAL_BUDGET);
    }

    RegionVoxelGpuStore(PageBudget budget) {
        this.budget = budget;
    }

    /**
     * Queue a fresh GPU copy for this voxel generation. The previous generation
     * remains physically allocated until the replacement is submitted, but it is
     * no longer discoverable as valid residency for the new generation.
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

    /** Queue a bounded sparse-lighting record into the same region input pages. */
    public synchronized boolean uploadLighting(int slot, GpuSparseLightingSnapshot snapshot,
                                               long generation) {
        checkSlot(slot);
        if(snapshot == null)
            throw new IllegalArgumentException("Sparse lighting snapshot must be present");
        if(generation < this.lightingGenerations[slot])
            return false;

        this.lightingGenerations[slot] = generation;
        this.lightingPending[slot] = null;

        if(this.closed || !uploadPathReady() || snapshot.byteSize() > PAGE_BYTES) {
            discardLightingResident(slot);
            return false;
        }

        Slice slice = allocateSlice(snapshot.byteSize());
        if(slice == null) {
            discardLightingResident(slot);
            return false;
        }

        Pending token = new Pending(slice, generation);
        this.lightingPending[slot] = token;

        ByteBuffer bytes = MemoryUtil.memAlloc(snapshot.byteSize());
        try {
            snapshot.writeTo(bytes);
            bytes.flip();
            AreaUploadManager.INSTANCE.uploadStorageAsync(
                    slice.page.buffer, slice.allocation.offset, bytes,
                    () -> completeLightingUpload(slot, token));
        } catch(RuntimeException | Error error) {
            if(this.lightingPending[slot] == token)
                this.lightingPending[slot] = null;
            retire(slice);
            throw error;
        } finally {
            MemoryUtil.memFree(bytes);
        }

        return true;
    }

    /** Immediately revoke discoverable voxel residency while retaining GPU-safe lifetime. */
    public synchronized void invalidate(int slot, long generation) {
        checkSlot(slot);
        if(generation < this.generations[slot])
            return;

        this.generations[slot] = generation;
        this.pending[slot] = null;
        discardResident(slot);
    }

    /** Immediately revoke discoverable lighting residency while retaining GPU-safe lifetime. */
    public synchronized void invalidateLighting(int slot, long generation) {
        checkSlot(slot);
        if(generation < this.lightingGenerations[slot])
            return;

        this.lightingGenerations[slot] = generation;
        this.lightingPending[slot] = null;
        discardLightingResident(slot);
    }

    public synchronized Residency getResidency(int slot) {
        checkSlot(slot);
        return residency(this.generations[slot], this.resident[slot]);
    }

    /**
     * True while this exact voxel generation is either queued for upload or already
     * resident. Sparse lighting may only be queued while this contract is true.
     */
    public synchronized boolean hasVoxelInput(int slot, long generation) {
        checkSlot(slot);
        if(this.closed || this.generations[slot] != generation)
            return false;
        Pending queued = this.pending[slot];
        if(queued != null && queued.generation == generation)
            return true;
        Resident current = this.resident[slot];
        return current != null && current.generation == generation;
    }

    public synchronized Residency getLightingResidency(int slot) {
        checkSlot(slot);
        return residency(this.lightingGenerations[slot], this.lightingResident[slot]);
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
            this.lightingResident[i] = null;
            this.lightingPending[i] = null;
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
            if(error.getMessage() != null && error.getMessage().startsWith("Failed to create buffer:"))
                return null;
            throw error;
        }

        Page page = new Page(this.pages.size(), buffer);
        this.pages.add(page);
        RegionVoxelPageAllocator.Allocation allocation = page.allocator.allocate(byteLength);
        if(allocation == null)
            throw new IllegalStateException("Fresh terrain input page cannot fit one bounded snapshot");
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

    private synchronized void completeLightingUpload(int slot, Pending token) {
        if(this.closed || this.lightingPending[slot] != token
                || this.lightingGenerations[slot] != token.generation) {
            retire(token.slice);
            return;
        }

        this.lightingPending[slot] = null;
        Resident previous = this.lightingResident[slot];
        this.lightingResident[slot] = new Resident(token.slice, token.generation);
        if(previous != null)
            retire(previous.slice);
    }

    private void discardResident(int slot) {
        Resident previous = this.resident[slot];
        this.resident[slot] = null;
        if(previous != null)
            retire(previous.slice);
    }

    private void discardLightingResident(int slot) {
        Resident previous = this.lightingResident[slot];
        this.lightingResident[slot] = null;
        if(previous != null)
            retire(previous.slice);
    }

    private static Residency residency(long generation, Resident current) {
        if(current == null || current.generation != generation)
            return Residency.invalid(generation);
        return new Residency(current.slice.page.index,
                current.slice.allocation.offset,
                current.slice.allocation.byteLength,
                generation, true);
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
                throw new IllegalArgumentException("GPU terrain input budget must be positive");
            this.maxBytes = maxBytes;
        }

        synchronized boolean tryReserve(int bytes) {
            if(bytes <= 0)
                throw new IllegalArgumentException("GPU terrain input reservation must be positive");
            if(bytes > this.maxBytes - this.usedBytes) {
                this.rejectedPages++;
                return false;
            }
            this.usedBytes += bytes;
            this.entries++;
            return true;
        }

        private int entries;

        synchronized void release(int bytes) {
            if(bytes <= 0 || bytes > this.usedBytes)
                throw new IllegalStateException("GPU terrain input budget accounting underflow");
            this.usedBytes -= bytes;
            this.entries--;
        }

        synchronized int usedBytes() {
            return this.usedBytes;
        }

        synchronized long rejectedPages() {
            return this.rejectedPages;
        }

        synchronized String describe() {
            return "Terrain GPU input pages: " + this.usedBytes / 1024 + "/"
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
