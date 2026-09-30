package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.passes.DefaultMainPass;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Closes the logical raw command stream immediately before the main command buffer ends. */
@Mixin(value = DefaultMainPass.class, priority = 850, remap = false)
public abstract class DefaultMainPassCommandTraceMixin {
    @Inject(
            method = "end",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkEndCommandBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;)I",
                    shift = At.Shift.BEFORE,
                    remap = false))
    private void vulkanmod$endFrameCommandTrace(VkCommandBuffer commandBuffer, CallbackInfo ci) {
        VulkanCommandTrace.end(commandBuffer);
    }
}
