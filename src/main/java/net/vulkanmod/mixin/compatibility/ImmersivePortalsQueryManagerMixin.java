package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Immersive Portals' OpenGL occlusion-query optimization with a
 * conservative visibility decision. VulkanMod has no OpenGL context, so neither
 * the GL query nor its query-only geometry draw is meaningful here. The callback
 * supplied by RendererUsingFrameBuffer.testShouldRenderPortal() only renders
 * ViewAreaRenderer geometry between glBeginQuery/glEndQuery; once the query is
 * removed, executing it can only add redundant work and exercise GL-only shader
 * paths. Report portals visible and let the real portal render proceed.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.QueryManager", remap = false)
public abstract class ImmersivePortalsQueryManagerMixin {
    @Inject(method = "renderAndGetDoesAnySamplePass(Ljava/lang/Runnable;)Z", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$assumeVisibleWithoutGlBooleanQuery(Runnable renderingFunc,
                                                                     CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "renderAndGetSampleCountPassed(Ljava/lang/Runnable;)I", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$assumeVisibleWithoutGlSampleQuery(Runnable renderingFunc,
                                                                    CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(1);
    }
}
