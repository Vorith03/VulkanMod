package net.vulkanmod.render.texture;

public final class AnimationComputeAdmissionTest {
    public static void main(String[] args) {
        for (int bits=0; bits<16; bits++) {
            boolean shared=(bits&1)!=0, batch=(bits&2)!=0, pool=(bits&4)!=0, multi=(bits&8)!=0;
            var sharedReason=AnimationComputeAdmission.shared(shared,batch,pool);
            var multiReason=AnimationComputeAdmission.multi(multi,sharedReason);
            if ((sharedReason==AnimationComputeAdmission.Reason.ADMITTED)!=(shared&&batch&&pool))
                throw new AssertionError("Shared prerequisites changed");
            if ((multiReason==AnimationComputeAdmission.Reason.ADMITTED)!=(multi&&shared&&batch&&pool))
                throw new AssertionError("Multi prerequisites changed");
        }
        if (AnimationComputeAdmission.shared(false,false,false)!=AnimationComputeAdmission.Reason.NOT_REQUESTED
                || AnimationComputeAdmission.shared(true,false,true)!=AnimationComputeAdmission.Reason.BATCH_REQUIRED
                || AnimationComputeAdmission.shared(true,true,false)!=AnimationComputeAdmission.Reason.SOURCE_POOL_REQUIRED
                || AnimationComputeAdmission.multi(true,AnimationComputeAdmission.Reason.NOT_REQUESTED)
                    !=AnimationComputeAdmission.Reason.SHARED_SCRATCH_REQUIRED)
            throw new AssertionError("Rejection reasons");
        System.out.println("Animation configuration admission matrix passed without renderer initialization");
    }
}
