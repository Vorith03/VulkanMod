package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.GpuTimestampRecorder;
import net.vulkanmod.render.profiling.VulkanCommandTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Correlates a raw command recording id to the already-canonical JFR submission sequence. */
@Mixin(value = GpuTimestampRecorder.class, priority = 850, remap = false)
public abstract class GpuTimestampCommandTraceMixin {
    @Inject(method = "submitted", at = @At("HEAD"))
    private static void vulkanmod$recordCommandSubmission(long commandBuffer, long submissionSequence,
                                                           long queue, long fence, CallbackInfo ci) {
        VulkanCommandTrace.submitted(commandBuffer, submissionSequence, queue, fence);
    }
}
