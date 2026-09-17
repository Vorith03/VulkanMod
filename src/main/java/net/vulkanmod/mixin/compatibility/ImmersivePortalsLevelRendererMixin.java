package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Immersive Portals normally substitutes vanilla ViewArea visibility discovery
 * while rendering a portal. VulkanMod owns terrain visibility/setup itself, so
 * allowing that substitution would cast the vanilla ViewArea to IP's
 * MyBuiltChunkStorage and cancel VulkanMod's setupRender path.
 *
 * Apply after IP's default-priority LevelRenderer mixin and override only the
 * merged opt-in helper. All of IP's other LevelRenderer portal hooks remain in
 * place. require=0 keeps this inert when Immersive Portals is not installed.
 */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class ImmersivePortalsLevelRendererMixin {

    @Dynamic("Added to LevelRenderer by Immersive Portals 3.0.7 MixinLevelRenderer")
    @Inject(
            method = "ip_allowOverrideTerrainSetup()Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void vulkanmod$keepVulkanTerrainSetup(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
