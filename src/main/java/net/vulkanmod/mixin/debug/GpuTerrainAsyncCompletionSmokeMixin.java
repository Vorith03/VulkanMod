package net.vulkanmod.mixin.debug;

import net.vulkanmod.render.chunk.voxel.GpuTerrainSectionMesherAsyncSmokeTest;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starts the async terrain completion smoke only inside a real recording frame. */
@Mixin(value = Renderer.class, remap = false)
public abstract class GpuTerrainAsyncCompletionSmokeMixin {
    @Inject(method = "beginFrame", at = @At("TAIL"), remap = false)
    private void vulkanmod$startGpuTerrainAsyncSmoke(CallbackInfo ci) {
        Renderer renderer = (Renderer)(Object)this;
        if(renderer.isRecordingFrame())
            GpuTerrainSectionMesherAsyncSmokeTest.onFrameStarted();
    }
}
