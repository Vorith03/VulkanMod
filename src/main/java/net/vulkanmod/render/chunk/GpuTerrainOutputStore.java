package net.vulkanmod.render.chunk;

import net.vulkanmod.render.vertex.TerrainRenderType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Non-consuming ownership for bounded GPU-generated terrain vertices.
 *
 * <p>The CPU mesh remains authoritative. Reservations use only already-free space in
 * the persistent area vertex buffer and never grow it. A newer section generation
 * immediately revokes every terrain-layer result for that section; same-generation
 * replacement keeps the last valid result discoverable until the replacement
 * publishes successfully. Successfully submitted reservations remain physically
 * pinned until their completion callback retires, even after logical invalidation.
 * Published resident slices are likewise kept physically pinned until every frame
 * slot has crossed a fence-safe retirement boundary, because older in-flight/cached
 * draw commands may still retain their vertex offsets after logical invalidation.</p>
 */
public final class GpuTerrainOutputStore implements AutoCloseable {
    public static final int MAX_FACES = 4096 * 6;
    public static final int VERTICES_PER_FACE = 4;
    public static final int VERTEX_BYTES = 20;
    public static final int BYTES_PER_FACE = VERTICES_PER_FACE * VERTEX_BYTES;

    private static long nextOwnerId = 1L;

    private final DrawBuffers drawBuffers;
    private final Consumer<Runnable> residentRetirement;
    private final Entry[] entries = new Entry[RegionBatchLayout.MAX_SECTIONS
            * TerrainRenderType.VALUES.length];
    private final long[] sectionGenerations = new long[RegionBatchLayout.MAX_SECTIONS];
    private final Map<Long, Pending> submittedReservations = new HashMap<>();
    private final Map<Long, StagedPending> stagedReservations = new HashMap<>();
    private final long ownerId;
    private long nextToken = 1L;
    private boolean closed;

    public GpuTerrainOutputStore(DrawBuffers drawBuffers) {
        this(drawBuffers, defaultResidentRetirement());
    }

    GpuTerrainOutputStore(DrawBuffers drawBuffers, Consumer<Runnable> residentRetirement) {
        if(drawBuffers == null)
            throw new IllegalArgumentException("GPU terrain output requires area draw buffers");
        if(residentRetirement == null)
            throw new IllegalArgumentException("GPU terrain resident retirement scheduler must be present");
        this.drawBuffers = drawBuffers;
        this.residentRetirement = residentRetirement;
        this.ownerId = claimOwnerId();
        Arrays.fill(this.sectionGenerations, -1L);
    }

    private static Consumer<Runnable> defaultResidentRetirement() {
        return runnable -> {
            AreaUploadManager manager = AreaUploadManager.INSTANCE;
            if(manager == null) {
                runnable.run();
                return;
            }
            // A stale draw may exist in any submitted frame slot, or in the command
            // buffer currently being recorded. Wait for every slot to cross a
            // fence-safe updateFrame boundary before physically reusing the slice.
            manager.enqueueFrameRetirement(runnable);
        };
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

        long sectionGeneration = this.sectionGenerations[packedSection];
        if(generation < sectionGeneration)
            return null;
        if(generation > sectionGeneration)
            advanceSectionGeneration(packedSection, generation);

        Entry entry = entry(packedSection, type, true);
        // Preserve an already-published same-generation result until a retry
        // actually succeeds. Only the superseded pending reservation is dropped.
        // Submitted work is detached logically but its segment stays pinned until
        // the corresponding frame-fence completion callback arrives.
        discardPending(entry);

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
        return new Reservation(this, ownerId, token, packedSection, type,
                generation, faceCapacity);
    }

    /**
     * Reserve a future-generation output without advancing section visibility.
     * Mixed APPEND rebuilds use this to keep the previous complete generation
     * drawable while GPU work for the replacement generation is still in flight.
     */
    public synchronized StagedReservation reserveStaged(int packedSection, TerrainRenderType type,
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
        if(closed || generation <= this.sectionGenerations[packedSection])
            return null;

        for(StagedPending staged : stagedReservations.values()) {
            if(staged.packedSection == packedSection && staged.type == type
                    && !staged.cancelled)
                return null;
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
        stagedReservations.put(token, new StagedPending(token, packedSection, type,
                generation, faceCapacity, segment));
        return new StagedReservation(this, ownerId, token, packedSection, type,
                generation, faceCapacity);
    }

    /**
     * Resolve a snapshot of the current backing-buffer handle. Callers that submit
     * GPU work must use {@link Reservation#submitWithTarget(Function)} so the target
     * remains stable through submission and the reservation is pinned before either
     * ownership lock is released.
     */
    public synchronized Target target(Reservation reservation) {
        Pending pending = pendingFor(reservation);
        if(pending == null || !drawBuffers.isAllocated())
            return null;
        return targetFor(pending);
    }

    /**
     * Run a non-submitting target operation while holding the same AreaBuffer monitor
     * used by CPU uploads and backing-buffer growth. GPU submissions must use
     * {@link #submitWithTarget(Reservation, Function)} instead.
     */
    private synchronized <T> T withTarget(Reservation reservation,
                                          Function<Target, T> operation) {
        if(operation == null)
            throw new IllegalArgumentException("GPU terrain target operation must be present");
        Pending pending = pendingFor(reservation);
        if(pending == null || !drawBuffers.isAllocated() || drawBuffers.vertexBuffer == null)
            return null;

        synchronized(drawBuffers.vertexBuffer) {
            // Store -> AreaBuffer is the same lock order used by reserve/discard.
            // Re-check while both locks are held so a stale token never obtains a
            // dispatchable handle.
            pending = pendingFor(reservation);
            if(pending == null)
                return null;
            return operation.apply(targetFor(pending));
        }
    }

    /**
     * Submit work against a stable target and atomically pin the reservation when the
     * caller reports a successful submission. A true result means the reservation's
     * segment cannot return to the AreaBuffer free list until complete() is called.
     */
    private synchronized boolean submitWithTarget(Reservation reservation,
                                                  Function<Target, Boolean> operation) {
        if(operation == null)
            throw new IllegalArgumentException("GPU terrain submission operation must be present");
        Pending pending = pendingFor(reservation);
        if(pending == null || !drawBuffers.isAllocated() || drawBuffers.vertexBuffer == null)
            return false;

        synchronized(drawBuffers.vertexBuffer) {
            pending = pendingFor(reservation);
            if(pending == null)
                return false;
            boolean submitted = Boolean.TRUE.equals(operation.apply(targetFor(pending)));
            if(submitted)
                submittedReservations.put(pending.token, pending);
            return submitted;
        }
    }

    private Target targetFor(Pending pending) {
        int offset = pending.segment.getOffset();
        if(offset < 0 || offset % VERTEX_BYTES != 0)
            throw new IllegalStateException("GPU terrain output reservation lost vertex alignment");
        return new Target(drawBuffers.vertexBuffer.getId(), offset,
                pending.segment.getSize(), offset / VERTEX_BYTES);
    }

    private synchronized boolean submitStagedWithTarget(
            StagedReservation reservation, Function<Target, Boolean> operation) {
        if(operation == null)
            throw new IllegalArgumentException("GPU terrain staged submission operation must be present");
        StagedPending pending = stagedPending(reservation);
        if(pending == null || pending.submitted || pending.ready
                || !drawBuffers.isAllocated() || drawBuffers.vertexBuffer == null)
            return false;

        synchronized(drawBuffers.vertexBuffer) {
            pending = stagedPending(reservation);
            if(pending == null || pending.submitted || pending.ready)
                return false;
            Target target = targetFor(pending.segment);
            boolean submitted = Boolean.TRUE.equals(operation.apply(target));
            if(submitted)
                pending.submitted = true;
            return submitted;
        }
    }

    private Target targetFor(AreaBuffer.Segment segment) {
        int offset = segment.getOffset();
        if(offset < 0 || offset % VERTEX_BYTES != 0)
            throw new IllegalStateException("GPU terrain output reservation lost vertex alignment");
        return new Target(drawBuffers.vertexBuffer.getId(), offset,
                segment.getSize(), offset / VERTEX_BYTES);
    }

    private synchronized boolean completeStaged(StagedReservation reservation,
                                                int writtenFaces, boolean overflow) {
        StagedPending pending = stagedPending(reservation);
        if(pending == null)
            return false;

        pending.submitted = false;
        if(pending.cancelled || closed || overflow || writtenFaces <= 0
                || writtenFaces > pending.faceCapacity) {
            stagedReservations.remove(pending.token);
            discard(pending.segment);
            return false;
        }

        pending.writtenFaces = writtenFaces;
        pending.ready = true;
        pending.segment.setReady();
        return true;
    }

    private synchronized boolean commitStaged(StagedReservation reservation) {
        StagedPending pending = stagedPending(reservation);
        if(pending == null || !pending.ready || pending.cancelled || closed)
            return false;
        if(pending.generation <= this.sectionGenerations[pending.packedSection])
            return false;

        stagedReservations.remove(pending.token);
        advanceSectionGeneration(pending.packedSection, pending.generation);
        Entry entry = entry(pending.packedSection, pending.type, true);
        entry.resident = new Resident(pending.generation, pending.writtenFaces,
                Math.multiplyExact(pending.writtenFaces, BYTES_PER_FACE), pending.segment);
        drawBuffers.markMeshChanged(pending.type);
        return true;
    }

    private synchronized void discardStaged(StagedReservation reservation) {
        StagedPending pending = stagedPending(reservation);
        if(pending == null)
            return;
        if(pending.submitted && !pending.ready) {
            pending.cancelled = true;
            return;
        }
        stagedReservations.remove(pending.token);
        discard(pending.segment);
    }

    private synchronized boolean stagedReady(StagedReservation reservation) {
        StagedPending pending = stagedPending(reservation);
        return pending != null && pending.ready && !pending.cancelled;
    }

    /**
     * Publish a completed output. Overflow, zero output, stale tokens, and counts
     * beyond the reservation fail closed without disturbing a valid same-generation
     * resident result. Submitted-but-stale reservations still reach this path so their
     * physically pinned AreaBuffer segment can be released only after GPU completion.
     */
    public synchronized boolean publish(Reservation reservation, int writtenFaces,
                                        boolean overflow) {
        Pending pending = pendingForCompletion(reservation);
        if(pending == null)
            return false;
        Entry entry = entry(reservation.packedSection(), reservation.type(), false);
        boolean current = !closed
                && this.sectionGenerations[reservation.packedSection()] == reservation.generation()
                && entry != null && entry.pending == pending;

        submittedReservations.remove(pending.token, pending);
        if(!current) {
            discard(pending.segment);
            return false;
        }

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
        // A cached FrameBatch may still contain the CPU command for this section.
        // Publication must therefore make the affected terrain layer observable on
        // the next frame before the new GPU residency can be consumed.
        drawBuffers.markMeshChanged(reservation.type());
        return true;
    }

    public synchronized Residency getResidency(int packedSection, TerrainRenderType type) {
        checkSection(packedSection);
        if(type == null)
            throw new IllegalArgumentException("GPU terrain output layer must be present");
        long generation = this.sectionGenerations[packedSection];
        if(closed || !supported(type))
            return Residency.invalid(generation);

        Entry entry = entry(packedSection, type, false);
        if(entry == null || entry.resident == null
                || entry.resident.generation != generation)
            return Residency.invalid(generation);

        Resident resident = entry.resident;
        int offset = resident.segment.getOffset();
        if(offset < 0 || offset % VERTEX_BYTES != 0)
            return Residency.invalid(generation);
        return new Residency(resident.generation, offset, resident.byteLength,
                resident.faceCount, offset / VERTEX_BYTES, true);
    }

    public synchronized long getSectionGeneration(int packedSection) {
        checkSection(packedSection);
        return this.sectionGenerations[packedSection];
    }

    /** Immediately make all terrain-layer output for this section generation stale. */
    public synchronized void invalidateSection(int packedSection, long generation) {
        checkSection(packedSection);
        if(generation < 0L)
            throw new IllegalArgumentException("GPU terrain output generation must be non-negative");
        if(generation < this.sectionGenerations[packedSection])
            return;
        advanceSectionGeneration(packedSection, generation);
    }

    @Override
    public synchronized void close() {
        if(closed)
            return;
        closed = true;
        for(int index = 0; index < entries.length; ++index) {
            Entry entry = entries[index];
            if(entry == null)
                continue;
            // Submitted segments intentionally remain owned until their frame-fence
            // callback invokes Reservation.complete(), even though the store is now
            // logically closed and cannot publish them.
            discardPending(entry);
            if(entry.resident != null) {
                TerrainRenderType type = TerrainRenderType.VALUES[
                        index / RegionBatchLayout.MAX_SECTIONS];
                drawBuffers.markMeshChanged(type);
            }
            discardResident(entry);
        }
        for(StagedPending staged : stagedReservations.values()) {
            if(staged.submitted && !staged.ready) {
                staged.cancelled = true;
            } else {
                discard(staged.segment);
            }
        }
        stagedReservations.entrySet().removeIf(entry ->
                !entry.getValue().submitted || entry.getValue().ready);
        Arrays.fill(entries, null);
        Arrays.fill(sectionGenerations, -1L);
    }

    private Pending pendingFor(Reservation reservation) {
        if(closed || reservation == null || reservation.owner != this
                || reservation.ownerId != ownerId)
            return null;
        if(reservation.type == null || !supported(reservation.type))
            return null;
        if(reservation.packedSection < 0
                || reservation.packedSection >= RegionBatchLayout.MAX_SECTIONS)
            return null;
        if(this.sectionGenerations[reservation.packedSection] != reservation.generation)
            return null;
        Entry entry = entry(reservation.packedSection, reservation.type, false);
        Pending pending = entry == null ? null : entry.pending;
        if(!matches(pending, reservation)
                || submittedReservations.containsKey(reservation.token))
            return null;
        return pending;
    }

    private Pending pendingForCompletion(Reservation reservation) {
        if(reservation == null || reservation.owner != this
                || reservation.ownerId != ownerId)
            return null;
        if(reservation.type == null || !supported(reservation.type))
            return null;
        if(reservation.packedSection < 0
                || reservation.packedSection >= RegionBatchLayout.MAX_SECTIONS)
            return null;

        Pending submitted = submittedReservations.get(reservation.token);
        if(matches(submitted, reservation))
            return submitted;
        if(closed || this.sectionGenerations[reservation.packedSection] != reservation.generation)
            return null;

        Entry entry = entry(reservation.packedSection, reservation.type, false);
        Pending pending = entry == null ? null : entry.pending;
        return matches(pending, reservation) ? pending : null;
    }

    private static boolean matches(Pending pending, Reservation reservation) {
        return pending != null && pending.generation == reservation.generation
                && pending.token == reservation.token
                && pending.faceCapacity == reservation.faceCapacity;
    }

    private StagedPending stagedPending(StagedReservation reservation) {
        if(reservation == null || reservation.owner != this
                || reservation.ownerId != ownerId)
            return null;
        StagedPending pending = stagedReservations.get(reservation.token);
        if(pending == null
                || pending.packedSection != reservation.packedSection
                || pending.type != reservation.type
                || pending.generation != reservation.generation
                || pending.faceCapacity != reservation.faceCapacity)
            return null;
        return pending;
    }

    private void advanceSectionGeneration(int packedSection, long generation) {
        this.sectionGenerations[packedSection] = generation;

        // Any staged replacement at or behind the new authoritative generation can
        // no longer commit. Retire it here rather than depending on a delayed
        // completion/transaction owner to notice the turnover. Submitted work stays
        // physically pinned until completion, exactly like ordinary reservations.
        stagedReservations.entrySet().removeIf(entry -> {
            StagedPending staged = entry.getValue();
            if(staged.packedSection != packedSection || staged.generation > generation)
                return false;
            if(staged.submitted && !staged.ready) {
                staged.cancelled = true;
                return false;
            }
            discard(staged.segment);
            return true;
        });

        for(TerrainRenderType type : TerrainRenderType.VALUES) {
            Entry entry = entry(packedSection, type, false);
            if(entry == null)
                continue;
            discardPending(entry);
            // Logical invalidation is immediate, but the retired resident remains
            // physically pinned until every frame slot has crossed a fence-safe
            // boundary. Mark the mesh revision now so later recordings cannot add
            // another stale command while that retirement barrier is outstanding.
            if(entry.resident != null)
                drawBuffers.markMeshChanged(type);
            discardResident(entry);
        }
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
        if(!submittedReservations.containsKey(pending.token))
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
        if(resident == null)
            return;
        AreaBuffer.Segment segment = resident.segment;
        AreaBuffer vertexBuffer = drawBuffers.vertexBuffer;
        if(vertexBuffer == null) {
            segment.reset();
            return;
        }
        // Capture the exact allocator that owns this segment. Retirement callbacks
        // execute from the terrain frame queue without holding this store's monitor.
        residentRetirement.accept(() -> releaseResidentSegment(vertexBuffer, segment));
    }

    private static void releaseResidentSegment(AreaBuffer vertexBuffer,
                                               AreaBuffer.Segment segment) {
        vertexBuffer.setSegmentFree(segment);
        segment.reset();
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
        Pending pending;
        Resident resident;
    }

    private record Pending(long token, long generation, int faceCapacity,
                           AreaBuffer.Segment segment) {}

    private record Resident(long generation, int faceCount, int byteLength,
                            AreaBuffer.Segment segment) {}

    private static final class StagedPending {
        final long token;
        final int packedSection;
        final TerrainRenderType type;
        final long generation;
        final int faceCapacity;
        final AreaBuffer.Segment segment;
        boolean submitted;
        boolean ready;
        boolean cancelled;
        int writtenFaces;

        StagedPending(long token, int packedSection, TerrainRenderType type,
                      long generation, int faceCapacity, AreaBuffer.Segment segment) {
            this.token = token;
            this.packedSection = packedSection;
            this.type = type;
            this.generation = generation;
            this.faceCapacity = faceCapacity;
            this.segment = segment;
        }
    }

    public static final class Reservation {
        private final GpuTerrainOutputStore owner;
        private final long ownerId;
        private final long token;
        private final int packedSection;
        private final TerrainRenderType type;
        private final long generation;
        private final int faceCapacity;

        private Reservation(GpuTerrainOutputStore owner, long ownerId, long token,
                            int packedSection, TerrainRenderType type,
                            long generation, int faceCapacity) {
            this.owner = owner;
            this.ownerId = ownerId;
            this.token = token;
            this.packedSection = packedSection;
            this.type = type;
            this.generation = generation;
            this.faceCapacity = faceCapacity;
        }

        public long ownerId() { return ownerId; }
        public long token() { return token; }
        public int packedSection() { return packedSection; }
        public TerrainRenderType type() { return type; }
        public long generation() { return generation; }
        public int faceCapacity() { return faceCapacity; }

        public <T> T withTarget(Function<Target, T> operation) {
            return owner.withTarget(this, operation);
        }

        public boolean submitWithTarget(Function<Target, Boolean> operation) {
            return owner.submitWithTarget(this, operation);
        }

        boolean complete(int writtenFaces, boolean overflow) {
            return owner.publish(this, writtenFaces, overflow);
        }
    }

    public static final class StagedReservation {
        private final GpuTerrainOutputStore owner;
        private final long ownerId;
        private final long token;
        private final int packedSection;
        private final TerrainRenderType type;
        private final long generation;
        private final int faceCapacity;

        private StagedReservation(GpuTerrainOutputStore owner, long ownerId, long token,
                                  int packedSection, TerrainRenderType type,
                                  long generation, int faceCapacity) {
            this.owner = owner;
            this.ownerId = ownerId;
            this.token = token;
            this.packedSection = packedSection;
            this.type = type;
            this.generation = generation;
            this.faceCapacity = faceCapacity;
        }

        public long generation() { return generation; }
        public TerrainRenderType type() { return type; }
        public int faceCapacity() { return faceCapacity; }

        public boolean submitWithTarget(Function<Target, Boolean> operation) {
            return owner.submitStagedWithTarget(this, operation);
        }

        boolean complete(int writtenFaces, boolean overflow) {
            return owner.completeStaged(this, writtenFaces, overflow);
        }

        boolean commit() {
            return owner.commitStaged(this);
        }

        void discard() {
            owner.discardStaged(this);
        }

        boolean ready() {
            return owner.stagedReady(this);
        }
    }

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
