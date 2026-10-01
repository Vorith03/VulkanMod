package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.GpuTimestampRecorder;
import net.vulkanmod.vulkan.passes.DefaultMainPass;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Writes the generic end timestamp immediately before the main command buffer closes. */
@Mixin(value = DefaultMainPass.class, priority = 900, remap = false)
public abstract class DefaultMainPassFlightRecorderMixin {
    @Inject(
            method = "end",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkEndCommandBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;)I",
                    shift = At.Shift.BEFORE,
                    remap = false))
    private void vulkanmod$endFrameGpuTimestamp(VkCommandBuffer commandBuffer, CallbackInfo ci) {
        GpuTimestampRecorder.end(commandBuffer);
    }
}
