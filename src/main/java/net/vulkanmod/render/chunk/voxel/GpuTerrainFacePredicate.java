package net.vulkanmod.render.chunk.voxel;

/**
 * Shared proof predicate for replacing Minecraft/Forge face culling in the GPU terrain path.
 */
public final class GpuTerrainFacePredicate {
    private GpuTerrainFacePredicate() {}

    /**
     * The current compute shader emits a cube face exactly when the adjacent state is
     * not SOLID_RENDER. CPU omission is safe only when the authoritative
     * Block.shouldRenderFace(...) answer is identical to that shader decision.
     */
    public static boolean matches(boolean neighborSolidRender,
                                  boolean authoritativeShouldRender) {
        return authoritativeShouldRender == !neighborSolidRender;
    }
}
