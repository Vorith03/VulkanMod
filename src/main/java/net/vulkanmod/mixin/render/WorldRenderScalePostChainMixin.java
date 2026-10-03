package net.vulkanmod.mixin.render;

import net.minecraft.client.renderer.PostChain;
import net.vulkanmod.render.scale.WorldRenderScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostChain.class)
public abstract class WorldRenderScalePostChainMixin {
    @Inject(method = "resize", at = @At("RETURN"))
    private void vulkanmod$forgetExternalResize(int width, int height, CallbackInfo ci) {
        // Window/mod resize can restore native extents while the next rounded
        // scale extent is unchanged. The controller caches only its own resize.
        WorldRenderScale.forgetChainExtent((PostChain)(Object)this);
    }
}
