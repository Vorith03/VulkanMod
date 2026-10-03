package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Existing synthetic names, without sampler lookup/remapping side effects. */
@Mixin(RenderTarget.class)
public interface RenderTargetTextureNamesAccessor {
    @Accessor("colorTextureId") int vulkanmod$getColorTextureName();
    @Accessor("depthBufferId") int vulkanmod$getDepthTextureName();
}
