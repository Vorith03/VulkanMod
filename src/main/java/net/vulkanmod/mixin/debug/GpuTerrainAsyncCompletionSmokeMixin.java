package net.vulkanmod.mixin.debug;

import net.vulkanmod.render.chunk.AreaUploadManagerPostSubmitSmokeTest;
import net.vulkanmod.render.chunk.voxel.GpuTerrainSectionMesherAsyncSmokeTest;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advances GPU-terrain async CI probes only inside a real recording frame. */
@Mixin(value = Renderer.class, remap = false)
public abstract class GpuTerrainAsyncCompletionSmokeMixin {
    @Inject(method = "beginFrame", at = @At("TAIL"), remap = false)
    private void vulkanmod$advanceGpuTerrainAsyncSmoke(CallbackInfo ci) {
        Renderer renderer = (Renderer)(Object)this;
        if(renderer.isRecordingFrame()) {
            AreaUploadManagerPostSubmitSmokeTest.verifyInRecordingFrame();
            GpuTerrainSectionMesherAsyncSmokeTest.onFrameStarted();
        }
    }
}
