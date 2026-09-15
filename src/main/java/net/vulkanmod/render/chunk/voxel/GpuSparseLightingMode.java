package net.vulkanmod.render.chunk.voxel;

/** Restart-time gate for live sparse-lighting capture. CPU terrain remains authoritative. */
public final class GpuSparseLightingMode {
    public static final String PROPERTY = "vulkanmod.experimentalGpuSparseLighting";
    public static final boolean ENABLED = Boolean.getBoolean(PROPERTY);

    private GpuSparseLightingMode() {}
}
