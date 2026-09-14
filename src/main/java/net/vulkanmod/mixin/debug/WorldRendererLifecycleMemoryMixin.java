package net.vulkanmod.mixin.debug;

import net.minecraft.client.multiplayer.ClientLevel;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.memory.LifecycleMemoryTelemetry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, remap = false)
public abstract class WorldRendererLifecycleMemoryMixin {
    @Inject(method = "setLevel", at = @At("HEAD"), remap = false)
    private void vulkanmod$beforeSetLevel(ClientLevel level, CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot(
                "world-level-set-before target=" + (level == null ? "none" : "present"));
    }

    @Inject(method = "setLevel", at = @At("RETURN"), remap = false)
    private void vulkanmod$afterSetLevel(ClientLevel level, CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot(
                "world-level-set-after target=" + (level == null ? "none" : "present"));
    }

    @Inject(method = "cleanUp", at = @At("HEAD"), remap = false)
    private void vulkanmod$beforeWorldRendererCleanup(CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot("world-renderer-cleanup-before");
    }

    @Inject(method = "cleanUp", at = @At("RETURN"), remap = false)
    private void vulkanmod$afterWorldRendererCleanup(CallbackInfo ci) {
        LifecycleMemoryTelemetry.snapshot("world-renderer-cleanup-after");
    }
}
