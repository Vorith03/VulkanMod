package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Narrow bridge used to rebuild Vulkan shader pipelines after Immersive Portals
 * finishes loading its resource-driven shader transformation table.
 */
@Mixin(GameRenderer.class)
public interface ImmersivePortalsGameRendererInvoker {
    @Invoker("reloadShaders")
    void vulkanmod$reloadShaders(ResourceProvider resourceProvider);
}
