package net.vulkanmod.render.chunk.build;

/** Budget admission between indivisible publications, never within a mesh update. */
public final class FrameWorkBudget {
    private long frame = Long.MIN_VALUE;
    private long start;
    private long allowance;
    private int limit;
    private int admitted;

    public void begin(long frameId, long now, long requestedNanos, int requestedLimit,
                      double frameMs, double targetMs) {
        if(frame == frameId) return;
        frame = frameId; start = now; admitted = 0;
        double pressure = Math.max(1.0, frameMs / Math.max(1.0, targetMs));
        allowance = Math.max(100_000L, (long)(requestedNanos / Math.min(4.0, pressure)));
        limit = Math.max(1, Math.min(64, requestedLimit));
    }
    public boolean admit(long now) {
        // At least one result per owner/frame prevents starvation under long frames.
        if(admitted > 0 && (admitted >= limit || now - start >= allowance)) return false;
        admitted++;
        return true;
    }
    public static int workerLimit(int configured, double frameMs, double targetMs) {
        double ratio = Math.max(1.0, frameMs / Math.max(1.0, targetMs));
        return Math.max(1, Math.min(configured, (int)Math.ceil(configured / Math.min(4.0, ratio))));
    }
}
