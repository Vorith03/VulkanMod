package net.vulkanmod.mixin.debug;

import net.vulkanmod.render.chunk.ResourceReloadMemoryManager;
import net.vulkanmod.vulkan.memory.LifecycleMemoryTelemetry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ResourceReloadMemoryManager.class, remap = false)
public abstract class ResourceReloadLifecycleMemoryMixin {
    @Inject(method = "beginResourceReload", at = @At("HEAD"), remap = false)
    private static void vulkanmod$beforeResourceReload(CallbackInfoReturnable<Long> cir) {
        LifecycleMemoryTelemetry.snapshot("resource-reload-begin-before");
    }

    @Inject(method = "beginResourceReload", at = @At("RETURN"), remap = false)
    private static void vulkanmod$afterResourceReloadBegin(CallbackInfoReturnable<Long> cir) {
        LifecycleMemoryTelemetry.snapshot(
                "resource-reload-begin-after generation=" + cir.getReturnValue());
    }

    @Inject(method = "completeResourceReload", at = @At("HEAD"), remap = false)
    private static void vulkanmod$beforeResourceReloadComplete(
            long generation, Throwable failure, CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot(
                "resource-reload-complete-before generation=" + generation +
                        " result=" + (failure == null ? "success" : "failure"));
    }

    @Inject(method = "completeResourceReload", at = @At("RETURN"), remap = false)
    private static void vulkanmod$afterResourceReloadComplete(
            long generation, Throwable failure, CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot(
                "resource-reload-complete-after generation=" + generation +
                        " result=" + (failure == null ? "success" : "failure"));
    }
}
