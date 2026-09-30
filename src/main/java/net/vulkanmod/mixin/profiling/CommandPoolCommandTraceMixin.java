package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.GpuTimestampRecorder;
import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.lwjgl.vulkan.VkQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attaches raw command tracing to the universal transient-command-buffer lifecycle. */
@Mixin(value = CommandPool.class, priority = 850, remap = false)
public abstract class CommandPoolCommandTraceMixin {
    @Shadow @Final private int queueFamilyIndex;

    @Inject(method = "beginCommands", at = @At("RETURN"))
    private void vulkanmod$beginCommandTrace(CallbackInfoReturnable<CommandPool.CommandBuffer> cir) {
        CommandPool.CommandBuffer commandBuffer = cir.getReturnValue();
        if(commandBuffer != null)
            VulkanCommandTrace.begin(commandBuffer.getHandle(), queueFamilyIndex);
    }

    @Inject(
            method = "submitCommands(Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;Lorg/lwjgl/vulkan/VkQueue;Z)J",
            at = @At(value = "INVOKE",
                    target = "Lnet/vulkanmod/render/profiling/GpuTimestampRecorder;end(Lorg/lwjgl/vulkan/VkCommandBuffer;)V",
                    shift = At.Shift.BEFORE,
                    remap = false))
    private void vulkanmod$endCommandTrace(CommandPool.CommandBuffer commandBuffer, VkQueue queue,
                                           boolean useSemaphore, CallbackInfoReturnable<Long> cir) {
        VulkanCommandTrace.end(commandBuffer.getHandle());
    }
}
