package net.vulkanmod.render.texture;

/** Avoid repeated animated-sprite visibility scans across terrain draw layers. */
public final class TerrainSpriteUsageGate {
    private boolean scanned;

    /** Begin a new renderer setup (also valid for portal/captured views). */
    public void reset() { scanned = false; }

    /** Claim the one visible-sprite scan for this setup. */
    public boolean claim() {
        if(scanned) return false;
        scanned = true;
        return true;
    }
}
