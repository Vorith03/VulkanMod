package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.RegionBatchLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * Render-thread-owned CPU staging residency for future region SSBO uploads.
 * Independent of mesh layers and their revisions. Never allocates Vulkan storage.
 */
public final class RegionVoxelStore {
    private static final boolean FORCE_ENABLED = Boolean.getBoolean("vulkanmod.experimentalSectionVoxels")
            || GpuSparseLightingMode.ENABLED;
    public static volatile boolean ENABLED = FORCE_ENABLED
            || (Initializer.CONFIG != null && Initializer.CONFIG.experimentalGpuTerrain);
    private static final Budget GLOBAL_BUDGET = new Budget(32 * 1024 * 1024, 2048);

    private final Budget budget;
    private final Map<Integer, SectionVoxelSnapshot> sections = new HashMap<>();
    private long revision;

    public RegionVoxelStore() { this(GLOBAL_BUDGET); }
    RegionVoxelStore(Budget budget) { this.budget = budget; }

    /**
     * Applies the saved UI setting while preserving the JVM properties as force-on
     * overrides for CI/debugging. Returns true only when the effective runtime state
     * changed and loaded terrain therefore needs to be rebuilt.
     */
    public static boolean setConfigEnabled(boolean enabled) {
        boolean effective = FORCE_ENABLED || enabled;
        boolean changed = ENABLED != effective;
        ENABLED = effective;
        return changed;
    }

    public boolean put(int slot, SectionVoxelSnapshot snapshot) {
        if (slot < 0 || slot >= RegionBatchLayout.MAX_SECTIONS)
            throw new IllegalArgumentException("Invalid region section slot");
        // Remove first: a rejected replacement must never expose an older snapshot.
        remove(slot);
        if (snapshot == null || !budget.acquire(snapshot.byteSize())) return false;
        sections.put(slot, snapshot);
        revision++;
        return true;
    }

    /** Advisory worker preflight; publication still checks the budget atomically. */
    public boolean canPut(int slot, int size) {
        SectionVoxelSnapshot previous = sections.get(slot);
        return budget.canAcquire(size, previous == null ? 0 : previous.byteSize());
    }

    public static boolean canAcquireNew(int size) { return GLOBAL_BUDGET.canAcquire(size, 0); }

    public SectionVoxelSnapshot get(int slot) { return sections.get(slot); }
    public long revision() { return revision; }

    public void remove(int slot) {
        SectionVoxelSnapshot old = sections.remove(slot);
        if (old != null) {
            budget.release(old.byteSize());
            revision++;
        }
    }

    public void clear() {
        if (sections.isEmpty()) return;
        sections.values().forEach(snapshot -> budget.release(snapshot.byteSize()));
        sections.clear();
        revision++;
    }

    public static String describe() { return GLOBAL_BUDGET.describe(); }

    // A payload AND entry cap bounds residency even for huge render distances.
    // In-flight builders/results remain bounded by TaskDispatcher's existing limits.
    static final class Budget {
        private final int maxBytes, maxEntries;
        private int bytes, entries;
        private long rejected;

        Budget(int maxBytes, int maxEntries) {
            this.maxBytes = maxBytes;
            this.maxEntries = maxEntries;
        }

        synchronized boolean acquire(int size) {
            if (size > maxBytes - bytes || entries >= maxEntries) {
                rejected++;
                return false;
            }
            bytes += size;
            entries++;
            return true;
        }

        synchronized boolean canAcquire(int size, int replacingBytes) {
            return size <= maxBytes - bytes + replacingBytes
                    && (replacingBytes > 0 || entries < maxEntries);
        }

        synchronized void release(int size) { bytes -= size; entries--; }
        synchronized String describe() {
            return "Terrain voxel staging: " + entries + "/" + maxEntries + " sections, "
                    + bytes / 1024 + "/" + maxBytes / 1024 + " KiB, rejected " + rejected;
        }
    }

    /** A full store may replace its own slot but must reject a new CPU-bypassed section. */
    public static void verifyCapacityPreflightForCi() {
        Budget budget = new Budget(20, 1);
        if (!budget.canAcquire(10, 0) || !budget.acquire(10)
                || budget.canAcquire(1, 0) || !budget.canAcquire(10, 10)
                || budget.canAcquire(21, 10)) {
            throw new IllegalStateException("Voxel staging preflight must honor entry/byte caps and replacement");
        }
        budget.release(10);
        if (!budget.canAcquire(20, 0))
            throw new IllegalStateException("Released voxel staging capacity must become available");
    }
}
