package net.vulkanmod.mixin.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererPostChainsAccessor {
    @Accessor("entityEffect") PostChain vulkanmod$getEntityEffect();
    @Accessor("transparencyChain") PostChain vulkanmod$getTransparencyChain();
}
