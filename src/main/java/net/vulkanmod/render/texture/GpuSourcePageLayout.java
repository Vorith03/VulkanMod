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
    private int end;
    private int leases;

    public GpuSourcePageLayout(int capacity) {
        if(capacity <= 0) throw new IllegalArgumentException("Invalid source page size");
        this.capacity = capacity;
    }

    public int reserve(int bytes) {
        if(bytes <= 0) throw new IllegalArgumentException("Invalid source size");
        long aligned = ((long)end + 3L) & ~3L;
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
