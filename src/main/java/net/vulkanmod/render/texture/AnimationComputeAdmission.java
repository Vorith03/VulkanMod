package net.vulkanmod.render.texture;

/** Pure configuration admission. ADMITTED here does not mean resources/device are ready. */
public final class AnimationComputeAdmission {
    public enum Reason { ADMITTED, NOT_REQUESTED, BATCH_REQUIRED, SOURCE_POOL_REQUIRED, SHARED_SCRATCH_REQUIRED }
    private AnimationComputeAdmission() {}
    public static Reason shared(boolean requested, boolean batch, boolean sourcePool) {
        if (!requested) return Reason.NOT_REQUESTED;
        if (!batch) return Reason.BATCH_REQUIRED;
        if (!sourcePool) return Reason.SOURCE_POOL_REQUIRED;
        return Reason.ADMITTED;
    }
    public static Reason multi(boolean requested, Reason shared) {
        if (!requested) return Reason.NOT_REQUESTED;
        if (shared == Reason.NOT_REQUESTED) return Reason.SHARED_SCRATCH_REQUIRED;
        return shared;
    }
}
