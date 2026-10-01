package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.VulkanCommandTrace;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Begins the raw trace generation for the reusable main-frame command buffer. */
@Mixin(value = Renderer.class, priority = 850, remap = false)
public abstract class RendererCommandTraceLifecycleMixin {
    @Shadow private VkCommandBuffer currentCmdBuffer;

    @Inject(
            method = "beginFrame",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkBeginCommandBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkCommandBufferBeginInfo;)I",
                    shift = At.Shift.AFTER,
                    remap = false))
    private void vulkanmod$beginFrameCommandTrace(CallbackInfo ci) {
        VulkanCommandTrace.begin(currentCmdBuffer, Queue.getQueueFamilies().graphicsFamily);
    }
}
