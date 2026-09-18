package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * Narrow bridge used to rebuild Vulkan shader pipelines after Immersive Portals
 * finishes loading its resource-driven shader transformation table and to install
 * IP's extra shaders after VulkanMod's cancellable reload hook bypasses IP's own
 * RETURN injection.
 */
@Mixin(GameRenderer.class)
public interface ImmersivePortalsGameRendererInvoker {
    @Invoker("reloadShaders")
    void vulkanmod$reloadShaders(ResourceProvider resourceProvider);

    @Accessor("shaders")
    Map<String, ShaderInstance> vulkanmod$getShaders();
}
