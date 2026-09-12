package net.vulkanmod.render.chunk.voxel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fixed-capacity suballocator for one GPU voxel storage page.
 *
 * <p>The allocator never grows the page. Failed allocations are expected under
 * pressure and allow the caller to keep the section on the CPU fallback path.
 * Free ranges are kept coalesced so rebuild churn can reuse space without turning
 * ordinary pressure into a synchronous Vulkan buffer resize.</p>
 */
final class RegionVoxelPageAllocator {
    private final int capacityBytes;
    private final int alignmentBytes;
    private final List<Range> freeRanges = new ArrayList<>();
    private int usedBytes;

    RegionVoxelPageAllocator(int capacityBytes, int alignmentBytes) {
        if(capacityBytes <= 0)
            throw new IllegalArgumentException("Page capacity must be positive");
        if(alignmentBytes <= 0 || (alignmentBytes & (alignmentBytes - 1)) != 0)
            throw new IllegalArgumentException("Alignment must be a positive power of two");
        if((capacityBytes & (alignmentBytes - 1)) != 0)
            throw new IllegalArgumentException("Page capacity must be alignment-sized");

        this.capacityBytes = capacityBytes;
        this.alignmentBytes = alignmentBytes;
        this.freeRanges.add(new Range(0, capacityBytes));
    }

    Allocation allocate(int byteLength) {
        if(byteLength <= 0)
            throw new IllegalArgumentException("Allocation size must be positive");

        int reservedBytes = alignUp(byteLength, this.alignmentBytes);
        if(reservedBytes > this.capacityBytes)
            return null;

        int bestIndex = -1;
        int bestLength = Integer.MAX_VALUE;
        for(int i = 0; i < this.freeRanges.size(); ++i) {
            Range range = this.freeRanges.get(i);
            if(range.length >= reservedBytes && range.length < bestLength) {
                bestIndex = i;
                bestLength = range.length;
            }
        }

        if(bestIndex < 0)
            return null;

        Range range = this.freeRanges.get(bestIndex);
        int offset = range.offset;
        if(range.length == reservedBytes) {
            this.freeRanges.remove(bestIndex);
        } else {
            range.offset += reservedBytes;
            range.length -= reservedBytes;
        }

        this.usedBytes += reservedBytes;
        return new Allocation(offset, byteLength, reservedBytes);
    }

    void free(Allocation allocation) {
        if(allocation == null)
            return;
        if(allocation.offset < 0 || allocation.reservedBytes <= 0
                || allocation.offset + allocation.reservedBytes > this.capacityBytes
                || (allocation.offset & (this.alignmentBytes - 1)) != 0
                || (allocation.reservedBytes & (this.alignmentBytes - 1)) != 0)
            throw new IllegalArgumentException("Allocation does not belong to this page");

        Range returned = new Range(allocation.offset, allocation.reservedBytes);
        for(Range range : this.freeRanges) {
            int returnedEnd = returned.offset + returned.length;
            int rangeEnd = range.offset + range.length;
            if(returned.offset < rangeEnd && range.offset < returnedEnd)
                throw new IllegalStateException("Allocation range was already free");
        }

        this.freeRanges.add(returned);
        this.freeRanges.sort(Comparator.comparingInt(range -> range.offset));
        for(int i = 0; i + 1 < this.freeRanges.size();) {
            Range left = this.freeRanges.get(i);
            Range right = this.freeRanges.get(i + 1);
            if(left.offset + left.length == right.offset) {
                left.length += right.length;
                this.freeRanges.remove(i + 1);
            } else {
                ++i;
            }
        }

        this.usedBytes -= allocation.reservedBytes;
        if(this.usedBytes < 0)
            throw new IllegalStateException("Voxel page allocator accounting underflow");
    }

    int usedBytes() {
        return this.usedBytes;
    }

    int freeBytes() {
        return this.capacityBytes - this.usedBytes;
    }

    int capacityBytes() {
        return this.capacityBytes;
    }

    boolean isEmpty() {
        return this.usedBytes == 0;
    }

    int freeRangeCount() {
        return this.freeRanges.size();
    }

    private static int alignUp(int value, int alignment) {
        long aligned = ((long)value + alignment - 1L) & -((long)alignment);
        if(aligned > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Aligned allocation size overflows int");
        return (int)aligned;
    }

    static final class Allocation {
        final int offset;
        final int byteLength;
        final int reservedBytes;

        Allocation(int offset, int byteLength, int reservedBytes) {
            this.offset = offset;
            this.byteLength = byteLength;
            this.reservedBytes = reservedBytes;
        }
    }

    private static final class Range {
        int offset;
        int length;

        Range(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }
    }
}
