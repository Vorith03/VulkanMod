package net.vulkanmod.render.chunk;

import net.vulkanmod.render.vertex.TerrainRenderType;

import java.util.Arrays;

/**
 * Non-consuming ownership for bounded GPU-generated terrain vertices.
 *
 * <p>The CPU mesh remains authoritative. Reservations use only already-free space in
 * the persistent area vertex buffer and never grow it. A newer section generation
 * immediately revokes older GPU output; same-generation replacement keeps the last
 * valid result discoverable until the replacement publishes successfully.</p>
 */
public final class GpuTerrainOutputStore implements AutoCloseable {
    public static final int MAX_FACES = 4096 * 6;
    public static final int VERTICES_PER_FACE = 4;
    public static final int VERTEX_BYTES = 20;
    public static final int BYTES_PER_FACE = VERTICES_PER_FACE * VERTEX_BYTES;

    private static long nextOwnerId = 1L;

    private final DrawBuffers drawBuffers;
    private final Entry[] entries = new Entry[RegionBatchLayout.MAX_SECTIONS
            * TerrainRenderType.VALUES.length];
    private final long ownerId;
    private long nextToken = 1L;
    private boolean closed;

    public GpuTerrainOutputStore(DrawBuffers drawBuffers) {
        if(drawBuffers == null)
            throw new IllegalArgumentException("GPU terrain output requires area draw buffers");
        this.drawBuffers = drawBuffers;
        this.ownerId = claimOwnerId();
    }

    /**
     * Reserve vertex capacity for one section generation. Returns {@code null} when
     * the layer is intentionally CPU-only, the generation is stale, or no current
     * area-buffer free range can hold the bounded output without growth.
     */
    public synchronized Reservation reserve(int packedSection, TerrainRenderType type,
                                            long generation, int faceCapacity) {
        checkSection(packedSection);
        if(type == null)
            throw new IllegalArgumentException("GPU terrain output layer must be present");
        if(!supported(type))
            return null;
        if(generation < 0L)
            throw new IllegalArgumentException("GPU terrain output generation must be non-negative");
        if(faceCapacity <= 0 || faceCapacity > MAX_FACES)
            throw new IllegalArgumentException("GPU terrain face capacity is outside the bounded section limit");
        if(closed)
            return null;

        Entry entry = entry(packedSection, type, true);
        if(generation < entry.generation)
            return null;

        if(generation > entry.generation) {
            entry.generation = generation;
            discardPending(entry);
            discardResident(entry);
        } else {
            // Preserve an already-published same-generation result until a retry
            // actually succeeds. Only the superseded pending reservation is dropped.
            discardPending(entry);
        }

        if(!drawBuffers.isAllocated())
            drawBuffers.allocateBuffers();

        int byteCapacity = Math.multiplyExact(faceCapacity, BYTES_PER_FACE);
        AreaBuffer.Segment segment = new AreaBuffer.Segment();
        if(!drawBuffers.vertexBuffer.tryReserve(byteCapacity, segment))
            return null;

        long token = nextToken++;
        if(token == 0L)
            token = nextToken++;
        entry.pending = new Pending(token, generation, faceCapacity, segment);
        return new Reservation(ownerId, token, packedSection, type, generation, faceCapacity);
    }

    /** Resolve the current backing-buffer handle immediately before a future dispatch. */
    public synchronized Target target(Reservation reservation) {
        Pending pending = pendingFor(reservation);
        if(pending == null || !drawBuffers.isAllocated())
            return null;
        int offset = pending.segment.getOffset();
        if(offset < 0 || offset % VERTEX_BYTES != 0)
            throw new IllegalStateException("GPU terrain output reservation lost vertex alignment");
        return new Target(drawBuffers.vertexBuffer.getId(), offset,
                pending.segment.getSize(), offset / VERTEX_BYTES);
    }

    /**
     * Publish a completed output. Overflow, zero output, stale tokens, and counts
     * beyond the reservation fail closed without disturbing a valid same-generation
     * resident result.
     */
    public synchronized boolean publish(Reservation reservation, int writtenFaces,
                                        boolean overflow) {
        Pending pending = pendingFor(reservation);
        if(pending == null)
            return false;
        Entry entry = entry(reservation.packedSection, reservation.type, false);

        if(overflow || writtenFaces <= 0 || writtenFaces > pending.faceCapacity) {
            discardPending(entry);
            return false;
        }

        int byteLength = Math.multiplyExact(writtenFaces, BYTES_PER_FACE);
        pending.segment.setReady();
        Resident previous = entry.resident;
        entry.resident = new Resident(pending.generation, writtenFaces, byteLength,
                pending.segment);
        entry.pending = null;
        discard(previous);
        return true;
    }

    public synchronized Residency getResidency(int packedSection, TerrainRenderType type) {
        checkSection(packedSection);
        if(type == null)
            throw new IllegalArgumentException("GPU terrain output layer must be present");
        Entry entry = entry(packedSection, type, false);
        long generation = entry == null ? -1L : entry.generation;
        if(closed || entry == null || entry.resident == null
                || entry.resident.generation != entry.generation)
            return Residency.invalid(generation);

        Resident resident = entry.resident;
        int offset = resident.segment.getOffset();
        if(offset < 0 || offset % VERTEX_BYTES != 0)
            return Residency.invalid(generation);
        return new Residency(resident.generation, offset, resident.byteLength,
                resident.faceCount, offset / VERTEX_BYTES, true);
    }

    /** Immediately make all terrain-layer output for this section generation stale. */
    public synchronized void invalidateSection(int packedSection, long generation) {
        checkSection(packedSection);
        if(generation < 0L)
            throw new IllegalArgumentException("GPU terrain output generation must be non-negative");
        for(TerrainRenderType type : TerrainRenderType.VALUES) {
            Entry entry = entry(packedSection, type, false);
            if(entry == null || generation < entry.generation)
                continue;
            entry.generation = generation;
            discardPending(entry);
            discardResident(entry);
        }
    }

    @Override
    public synchronized void close() {
        if(closed)
            return;
        closed = true;
        for(Entry entry : entries) {
            if(entry == null)
                continue;
            discardPending(entry);
            discardResident(entry);
        }
        Arrays.fill(entries, null);
    }

    private Pending pendingFor(Reservation reservation) {
        if(closed || reservation == null || reservation.ownerId != ownerId)
            return null;
        if(reservation.type == null || !supported(reservation.type))
            return null;
        if(reservation.packedSection < 0
                || reservation.packedSection >= RegionBatchLayout.MAX_SECTIONS)
            return null;
        Entry entry = entry(reservation.packedSection, reservation.type, false);
        Pending pending = entry == null ? null : entry.pending;
        if(pending == null || entry.generation != reservation.generation
                || pending.generation != reservation.generation
                || pending.token != reservation.token
                || pending.faceCapacity != reservation.faceCapacity)
            return null;
        return pending;
    }

    private Entry entry(int packedSection, TerrainRenderType type, boolean create) {
        int index = type.ordinal() * RegionBatchLayout.MAX_SECTIONS + packedSection;
        Entry entry = entries[index];
        if(entry == null && create) {
            entry = new Entry();
            entries[index] = entry;
        }
        return entry;
    }

    private void discardPending(Entry entry) {
        if(entry == null || entry.pending == null)
            return;
        Pending pending = entry.pending;
        entry.pending = null;
        discard(pending.segment);
    }

    private void discardResident(Entry entry) {
        if(entry == null || entry.resident == null)
            return;
        Resident resident = entry.resident;
        entry.resident = null;
        discard(resident);
    }

    private void discard(Resident resident) {
        if(resident != null)
            discard(resident.segment);
    }

    private void discard(AreaBuffer.Segment segment) {
        if(segment == null)
            return;
        if(drawBuffers.isAllocated() && drawBuffers.vertexBuffer != null)
            drawBuffers.vertexBuffer.setSegmentFree(segment);
        segment.reset();
    }

    private static boolean supported(TerrainRenderType type) {
        return type != TerrainRenderType.TRANSLUCENT && type != TerrainRenderType.TRIPWIRE;
    }

    private static void checkSection(int packedSection) {
        if(packedSection < 0 || packedSection >= RegionBatchLayout.MAX_SECTIONS)
            throw new IllegalArgumentException("GPU terrain output section is outside its region");
    }

    private static synchronized long claimOwnerId() {
        long id = nextOwnerId++;
        if(id == 0L)
            id = nextOwnerId++;
        return id;
    }

    private static final class Entry {
        long generation = -1L;
        Pending pending;
        Resident resident;
    }

    private record Pending(long token, long generation, int faceCapacity,
                           AreaBuffer.Segment segment) {}

    private record Resident(long generation, int faceCount, int byteLength,
                            AreaBuffer.Segment segment) {}

    public record Reservation(long ownerId, long token, int packedSection,
                              TerrainRenderType type, long generation,
                              int faceCapacity) {}

    /** Whole-buffer descriptor binding is intentional; byteOffset is a shader base. */
    public record Target(long bufferId, int byteOffset, int byteCapacity,
                         int vertexOffset) {}

    public record Residency(long generation, int byteOffset, int byteLength,
                            int faceCount, int vertexOffset, boolean valid) {
        static Residency invalid(long generation) {
            return new Residency(generation, -1, 0, 0, 0, false);
        }
    }
}
