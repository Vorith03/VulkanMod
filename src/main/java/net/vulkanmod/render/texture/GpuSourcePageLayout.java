package net.vulkanmod.render.texture;

/**
 * Monotonic 4-byte-aligned suballocation inside one device-local texture-source
 * page. Releasing a slot never reuses its bytes while the page remains live:
 * GPU commands may still refer to that immutable source region.
 *
 * The parent page can be retired only when all leases have been released.
 */
public final class GpuSourcePageLayout {
    private final int capacity;
    private final int alignment;
    private int end;
    private int leases;

    public GpuSourcePageLayout(int capacity, int alignment) {
        if(capacity <= 0 || alignment < 4 || (alignment & (alignment - 1)) != 0)
            throw new IllegalArgumentException("Invalid source page size/alignment");
        this.capacity = capacity;
        this.alignment = alignment;
    }

    public GpuSourcePageLayout(int capacity) { this(capacity, 4); }

    public int reserve(int bytes) {
        if(bytes <= 0) throw new IllegalArgumentException("Invalid source size");
        long aligned = ((long)end + alignment - 1L) & ~(alignment - 1L);
        if(aligned + bytes > capacity) return -1;
        end = (int)(aligned + bytes);
        leases++;
        return (int)aligned;
    }

    public void release() {
        if(leases <= 0) throw new IllegalStateException("Source page lease underflow");
        leases--;
    }

    public boolean empty() { return leases == 0; }
    public int used() { return end; }
    public int capacity() { return capacity; }
}
