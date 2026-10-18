package net.vulkanmod.render.texture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fixed-budget animation admission. Plan once per inventory change, never evict in-flight sources. */
public final class GpuAnimationAdmission {
    private static final boolean REQUESTED = Boolean.getBoolean("vulkanmod.experimentalGpuAnimationAdmission");
    private static final List<Ticket> CANDIDATES = new ArrayList<>();
    private static boolean ciEnabled;
    private static boolean dirty = true;
    private static long plans;
    private static int selected;
    private static long selectedBytes;

    private GpuAnimationAdmission() {}
    public static boolean enabled() { return REQUESTED || ciEnabled; }

    static void setCiEnabled(boolean enabled) {
        if(!Boolean.getBoolean("vulkanmod.ciScreenshotSmoke"))
            throw new IllegalStateException("Admission override is CI-only");
        ciEnabled = enabled;
        dirty = true;
    }

    public static synchronized Ticket register(String id, long bytes, double interpolationPixelsPerTick) {
        if(!enabled() || bytes <= 0 || !Double.isFinite(interpolationPixelsPerTick)
                || interpolationPixelsPerTick < 0) return null;
        Ticket ticket = new Ticket(id, bytes, interpolationPixelsPerTick);
        CANDIDATES.add(ticket);
        dirty = true;
        return ticket;
    }

    /** Call before the outer texture tick, after all atlas tickers are registered. */
    public static synchronized void prepare(long limit, long chargedBytes) {
        if(!enabled() || !dirty) return;
        long available = Math.max(0L, limit - Math.min(limit, Math.max(0L, chargedBytes)));
        List<Ticket> pending = new ArrayList<>();
        selected = 0;
        selectedBytes = 0;
        for(Ticket ticket : CANDIDATES) {
            ticket.selected = ticket.resident;
            if(ticket.resident) {
                selected++;
                selectedBytes += ticket.bytes;
            } else if(!ticket.failed) pending.add(ticket);
        }
        // Interpolation removes a pixel loop on every interior tick. Discrete copies
        // save only staging at boundaries; admit them after useful interpolation.
        pending.sort(Comparator.<Ticket>comparingDouble(t -> t.pixelsPerTick / t.bytes)
                .reversed().thenComparing(t -> t.id));
        for(Ticket ticket : pending) {
            if(ticket.bytes > available) continue; // smaller later candidates may fit
            ticket.selected = true;
            available -= ticket.bytes;
            selected++;
            selectedBytes += ticket.bytes;
        }
        dirty = false;
        plans++;
    }

    public static synchronized void memoryReleased() { dirty = true; }
    public static synchronized Stats stats() {
        return new Stats(enabled(), plans, CANDIDATES.size(), selected, selectedBytes);
    }
    public record Stats(boolean enabled, long plans, int candidates, int selected, long selectedBytes) {}

    public static final class Ticket implements AutoCloseable {
        private final String id;
        private final long bytes;
        private final double pixelsPerTick;
        private boolean selected, resident, failed, closed;
        private Ticket(String id, long bytes, double pixelsPerTick) {
            this.id = id;
            this.bytes = bytes;
            this.pixelsPerTick = pixelsPerTick;
        }
        public boolean selected() { return selected && !closed && !failed; }
        public void admitted() { resident = true; }
        public void failed() { failed = true; resident = false; selected = false; dirty = true; }
        @Override public void close() {
            synchronized(GpuAnimationAdmission.class) {
                if(closed) return;
                closed = true;
                CANDIDATES.remove(this);
                dirty = true;
            }
        }
    }
}
