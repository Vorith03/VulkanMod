package net.vulkanmod.mixin.debug;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.LifecycleMemoryTelemetry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Vulkan.class, remap = false)
public abstract class VulkanLifecycleMemoryMixin {
    @Inject(method = "cleanUp", at = @At("HEAD"), remap = false)
    private static void vulkanmod$beforeVulkanCleanup(CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot("vulkan-cleanup-before");
    }

    @Inject(method = "cleanUp", at = @At("RETURN"), remap = false)
    private static void vulkanmod$afterVulkanCleanup(CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot("vulkan-cleanup-after");
    }
}
