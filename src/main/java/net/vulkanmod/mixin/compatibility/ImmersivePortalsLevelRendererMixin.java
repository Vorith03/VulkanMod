package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /**
     * Immersive Portals' setupRender HEAD callback moves the vanilla
     * ChunkRenderDispatcher camera for remote dimensions before consulting
     * ip_allowOverrideTerrainSetup(). VulkanMod intentionally replaces that
     * dispatcher with its own WorldRenderer, so the field is null and the
     * camera update is both unusable and unsafe.
     *
     * This mixin applies after IP. Scan the merged LevelRenderer methods rather
     * than naming IP's decorated callback handler, whose generated name is not a
     * stable API, and suppress only the exact vanilla dispatcher camera call.
     * Other IP setup/render hooks remain untouched. require=0 keeps the mixin
     * inert when IP is absent.
     */
    @Dynamic("Targets a ChunkRenderDispatcher call merged by Immersive Portals 3.0.7")
    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;setCamera(Lnet/minecraft/world/phys/Vec3;)V"
            ),
            require = 0
    )
    private void vulkanmod$skipVanillaChunkDispatcherCamera(
            ChunkRenderDispatcher dispatcher,
            Vec3 cameraPosition
    ) {
        // VulkanMod's WorldRenderer owns terrain camera/setup state.
    }
}
