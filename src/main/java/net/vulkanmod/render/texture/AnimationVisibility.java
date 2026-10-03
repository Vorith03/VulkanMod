package net.vulkanmod.render.texture;

/** Clock-independent eligibility. Hidden ticks continue advancing vanilla metadata. */
public final class AnimationVisibility {
    private long lastUseNanos;
    private boolean dirty;
    private boolean closed;

    public AnimationVisibility(long now) { lastUseNanos = now; }
    public void use(long now) { if(!closed) lastUseNanos = now; }
    public boolean materialize(long now, long graceNanos, boolean enabled) {
        return !closed && (!enabled || now - lastUseNanos <= graceNanos);
    }
    public void skipped() { if(!closed) dirty = true; }
    public boolean needsRefresh() { return !closed && dirty; }
    public void refreshed() { dirty = false; }
    public void close() { closed = true; dirty = false; }
}
