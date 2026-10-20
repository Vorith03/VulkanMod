package net.vulkanmod.render.texture;

/** Word counts for the existing animation_interpolate.comp ABI; guarded independently in tests. */
public final class AnimationComputeLayout {
    public static final int JOB_WORDS = 12;
    public static final int PUSH_WORDS = 12;
    public static final int MAX_BATCH_JOBS = 32;
    private AnimationComputeLayout() {}
}
