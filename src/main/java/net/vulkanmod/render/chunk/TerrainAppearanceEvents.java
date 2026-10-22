package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/** Bounded diagnostic chronology. Admission/snapshot events are NOT an input-availability oracle. */
public final class TerrainAppearanceEvents {
    public static final boolean ENABLED = Boolean.getBoolean("vulkanmod.debugTerrainDrawReadback");
    private static final int MAX_SECTIONS = 32768;
    private static final long DURATION = 60_000_000_000L;
    private static WeakReference<Object> owner = new WeakReference<>(null);
    private static WeakReference<Object> world = new WeakReference<>(null);
    private static final Map<Ticket, Integer> seen = new HashMap<>();
    private static long epoch, origin;
    private static boolean incomplete;
    private static int pageBudgetEvents;
    private static String view;

    public enum Stage { NEIGHBOR_GATE_PASSED, REGION_PREPARE_START, REGION_SNAPSHOT_READY,
        ADMITTED, WORKER_START, WORKER_FINISH, PUBLICATION_QUEUED, PUBLICATION_APPLIED,
        GPU_DISPATCH_ATTEMPT, GPU_READY }
    public record Ticket(long epoch, int x, int y, int z, long generation) {}

    private TerrainAppearanceEvents() {}

    /** Main-world setup entry, not server join, GUI dismissal, presentation or first nonempty mesh. */
    public static synchronized boolean begin(Object renderer, Object level) {
        if(!ENABLED || renderer == null || level == null) return false;
        if(owner.get() == renderer && world.get() == level) return false;
        owner = new WeakReference<>(renderer); world = new WeakReference<>(level);
        epoch++; origin = System.nanoTime(); seen.clear(); incomplete = false; view = null; pageBudgetEvents = 0;
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_WORLD_RENDER_ENTER: epoch={} origin_ns={} epoch_ms={}; first main-world renderer setup, NOT first usable/presented frame",
                epoch, origin, System.currentTimeMillis());
        return true;
    }

    /** Exact observed main-view signature; a changed signature invalidates a fixed-view trial. */
    public static synchronized void view(Object renderer, String signature, String intent) {
        if(!ENABLED || origin == 0 || owner.get() != renderer || System.nanoTime() - origin > DURATION) return;
        TerrainDrawReadback.referenceIfDue(epoch, System.nanoTime() - origin);
        if(signature.equals(view)) return;
        view = signature;
        TerrainDrawReadback.viewChanged();
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_VIEW: epoch={} observed_ns={} signature={} intent={}",
                epoch, System.nanoTime(), signature, intent);
    }

    public static synchronized void close(Object renderer) {
        if(!ENABLED || owner.get() != renderer) return;
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_WORLD_TRACE_END: epoch={} sections={} incomplete={}",
                epoch, seen.size(), incomplete);
        owner.clear(); world.clear(); seen.clear(); epoch++; origin = 0;
    }

    public static Ticket capture(RenderSection section) {
        if(!ENABLED || section == null) return null;
        long generation = section.getVoxelGeneration();
        synchronized(TerrainAppearanceEvents.class) {
            if(origin == 0 || owner.get() != section.traceOwner()) return null;
            return new Ticket(epoch, section.xOffset(), section.yOffset(), section.zOffset(), generation);
        }
    }

    public static void stage(RenderSection section, long generation, Stage stage) {
        if(!ENABLED) return;
        Ticket ticket = capture(section);
        if(ticket != null && ticket.generation == generation) stage(ticket, stage);
    }

    public static synchronized void stage(Ticket ticket, Stage stage) {
        if(!ENABLED || ticket == null || ticket.epoch != epoch || origin == 0) return;
        long now = System.nanoTime();
        if(now - origin > DURATION) return;
        int previous = seen.getOrDefault(ticket, 0), bit = 1 << stage.ordinal();
        if((previous & bit) != 0) return;
        if(previous == 0 && seen.size() >= MAX_SECTIONS) {
            if(!incomplete) Initializer.LOGGER.warn("VULKANMOD_TERRAIN_WORLD_TRACE_INCOMPLETE: epoch={} section_generation_cap={}", epoch, MAX_SECTIONS);
            incomplete = true; return;
        }
        seen.put(ticket, previous | bit);
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_SECTION_STAGE: epoch={} section=({},{},{}) generation={} stage={} since_entry_ns={} observed_ns={}",
                epoch, ticket.x, ticket.y, ticket.z, ticket.generation, stage, now - origin, now);
    }

    /** Bounded exact charged-page chronology for allocation churn/retention diagnosis. */
    public static synchronized void pageBudgetEvent(String operation, int usedBytes, int capacityBytes) {
        if(!ENABLED || origin == 0 || System.nanoTime() - origin > DURATION) return;
        if(pageBudgetEvents >= 8192) {
            if(!incomplete) Initializer.LOGGER.warn("VULKANMOD_TERRAIN_WORLD_TRACE_INCOMPLETE: epoch={} page_budget_event_cap=8192", epoch);
            incomplete = true; return;
        }
        pageBudgetEvents++;
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_INPUT_PAGE_BUDGET: epoch={} observed_ns={} operation={} used_bytes={} capacity_bytes={}; charged native input buffers, NOT driver heap reservation",
                epoch, System.nanoTime(), operation, usedBytes, capacityBytes);
    }

    static synchronized boolean lateCensusWindowReached() {
        return ENABLED && origin != 0 && System.nanoTime() - origin >= 61_000_000_000L;
    }

    static synchronized boolean withinWindow(long requestedEpoch) {
        return requestedEpoch == 0 || (origin != 0 && requestedEpoch == epoch
                && System.nanoTime() - origin <= DURATION);
    }

    /** Zero means a non-main/unknown region; never attribute portal observations to the main-world clock. */
    static long regionEpoch(ChunkArea area) {
        Object activeOwner;
        long activeEpoch;
        synchronized(TerrainAppearanceEvents.class) {
            if(!ENABLED || origin == 0) return 0;
            activeOwner = owner.get(); activeEpoch = epoch;
        }
        if(net.vulkanmod.compatibility.ImmersivePortalsLevelRendererCompat.shouldCancelWorldRendererReload()) return 0;
        for(int slot = 0; slot < RegionBatchLayout.MAX_SECTIONS; slot++) {
            RenderSection section = area.getOwnedSection(slot);
            if(section != null) return activeOwner == section.traceOwner() ? activeEpoch : 0;
        }
        return 0;
    }
}
