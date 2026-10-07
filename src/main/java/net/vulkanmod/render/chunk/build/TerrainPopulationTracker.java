package net.vulkanmod.render.chunk.build;

/** Initial section build ownership, separate from maintenance rebuilds of compiled sections. */
public final class TerrainPopulationTracker {
    private final boolean enabled;
    private long epoch;
    private long scheduled;
    private long published;
    private int pending;

    public TerrainPopulationTracker() {
        this(Boolean.getBoolean("vulkanmod.performanceProfiler")
                && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark"));
    }

    TerrainPopulationTracker(boolean enabled) { this.enabled = enabled; }

    public boolean enabled() { return enabled; }

    /** Called at task admission, before queueing; ownership extends through publication. */
    public Ticket begin(boolean initialBuild) {
        if (!enabled || !initialBuild) return null;
        synchronized (this) {
            scheduled++;
            pending++;
            return new Ticket(epoch);
        }
    }

    /** Dispatcher teardown runs after workers stop and queued results are discarded. */
    public synchronized void reset() {
        epoch++;
        scheduled = published = 0L;
        pending = 0;
    }

    public synchronized Snapshot snapshot() { return new Snapshot(epoch, scheduled, published, pending); }

    /** Allocation-free capture guard: a new population task or dispatcher recreation invalidates it. */
    public synchronized boolean unchanged(Snapshot reference) {
        return reference != null && reference.pending == 0 && reference.epoch == epoch && reference.scheduled == scheduled
                && reference.published == published && pending == 0;
    }

    public static boolean stable(Snapshot current, Snapshot previous, int nonEmpty, int previousNonEmpty) {
        return current != null && previous != null && nonEmpty > 0 && nonEmpty == previousNonEmpty
                && current.pending == 0 && previous.pending == 0
                && current.epoch == previous.epoch && current.scheduled == previous.scheduled
                && current.published == previous.published;
    }

    public record Snapshot(long epoch, long scheduled, long published, int pending) {}

    public final class Ticket {
        private final long ownerEpoch;
        private boolean finished;

        private Ticket(long ownerEpoch) { this.ownerEpoch = ownerEpoch; }

        /** Cancellation, failure and publication may race; exactly one retires this ownership. */
        public void finish(boolean acceptedPublication) {
            synchronized (TerrainPopulationTracker.this) {
                if (finished) return;
                finished = true;
                if (ownerEpoch != epoch) return;
                pending--;
                if (acceptedPublication) published++;
            }
        }
    }
}
