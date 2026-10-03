package net.vulkanmod.render.scale;

/** Resolution policy is independent of framebuffer/resource ownership. */
public final class RenderScaleDimensions {
    private RenderScaleDimensions() {}
    public static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.5, Math.min(1.0, value)) : 1.0;
    }
    public static int extent(int nativeExtent, double scale) {
        if(nativeExtent <= 0) throw new IllegalArgumentException("Framebuffer extent must be positive");
        return Math.max(1, (int)Math.ceil(nativeExtent * clamp(scale)));
    }
}
