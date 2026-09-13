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
    private static final boolean FORCE_ENABLED = Boolean.getBoolean("vulkanmod.experimentalSectionVoxels");
    public static volatile boolean ENABLED = FORCE_ENABLED
            || (Initializer.CONFIG != null && Initializer.CONFIG.experimentalGpuTerrain);
    private static final Budget GLOBAL_BUDGET = new Budget(32 * 1024 * 1024, 2048);

    private final Budget budget;
    private final Map<Integer, SectionVoxelSnapshot> sections = new HashMap<>();
    private long revision;

    public RegionVoxelStore() { this(GLOBAL_BUDGET); }
    RegionVoxelStore(Budget budget) { this.budget = budget; }

    /**
     * Applies the saved UI setting while preserving the JVM property as a force-on
     * override for CI/debugging. Returns true only when the effective runtime state
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

        synchronized void release(int size) { bytes -= size; entries--; }
        synchronized String describe() {
            return "Terrain voxel staging: " + entries + "/" + maxEntries + " sections, "
                    + bytes / 1024 + "/" + maxBytes / 1024 + " KiB, rejected " + rejected;
        }
    }
}
