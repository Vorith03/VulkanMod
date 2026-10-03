package net.vulkanmod.render.instancing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Objects;

/** CPU owner/compaction boundary. Call from the render thread after Flywheel's tasks complete. */
public final class InstanceGroup<D> {
    public interface Access<D> {
        Object owner(D data);
        void setOwner(D data, Object owner);
        boolean removed(D data);
        boolean consumeDirty(D data);
        void markDirty(D data);
        void notifyRemoval(Object owner);
        void write(D data, ByteBuffer destination);
    }
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private final int stride;
    private final Object owner;
    private final Access<D> access;
    private final ArrayList<D> data = new ArrayList<>();
    private final IdentityHashMap<D, byte[]> records = new IdentityHashMap<>();
    private boolean retry;
    private boolean closed;

    public InstanceGroup(int stride, Object owner, Access<D> access) {
        if(stride <= 0 || stride % 4 != 0 || stride > MAX_BYTES) throw new IllegalArgumentException("Invalid stride");
        this.stride = stride; this.owner = Objects.requireNonNull(owner); this.access = Objects.requireNonNull(access);
    }

    public void add(D instance) {
        requireOpen();
        if(access.removed(instance)) throw new IllegalArgumentException("Removed instance cannot be resurrected");
        if(records.containsKey(instance) && access.owner(instance) == owner) return;
        if(!records.containsKey(instance) && (long)(data.size()+1) * stride > MAX_BYTES) throw new IllegalStateException("Instance group limit reached");
        Object old = access.owner(instance);
        if(old != null && old != owner) access.notifyRemoval(old);
        access.setOwner(instance, owner);
        access.markDirty(instance);
        if(!records.containsKey(instance)) data.add(instance);
        records.put(instance, null);
    }

    /** Snapshot is detached from instances and older snapshots; failed packing retries every retained record. */
    public ByteBuffer snapshot() {
        requireOpen();
        data.removeIf(instance -> {
            if(!access.removed(instance) && access.owner(instance) == owner) return false;
            records.remove(instance); return true;
        });
        var destination = ByteBuffer.allocate(data.size() * stride).order(ByteOrder.nativeOrder());
        try {
            for(D instance : data) {
                byte[] record = records.get(instance);
                boolean dirty = access.consumeDirty(instance);
                if(retry || record == null || dirty) {
                    var next = ByteBuffer.allocate(stride).order(ByteOrder.nativeOrder());
                    access.write(instance, next);
                    record = next.array(); records.put(instance, record);
                }
                destination.put(record);
            }
            retry = false;
            return destination.flip().asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        } catch(RuntimeException | Error failure) {
            retry = true;
            throw failure;
        }
    }

    /** Matches legacy origin shift: drop membership, then the engine notifies recreation listeners. */
    public void clear() { requireOpen(); data.clear(); records.clear(); retry = false; }
    public void close() { if(!closed) { clear(); closed = true; } }
    public int size() { return data.size(); }
    private void requireOpen() { if(closed) throw new IllegalStateException("Instance owner is closed"); }
}
