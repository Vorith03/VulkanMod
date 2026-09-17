package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Route IP's direct GlStateManager cull toggles through VulkanMod's RenderSystem bridge. */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.ViewAreaRenderer", remap = false)
public abstract class ImmersivePortalsViewAreaRendererMixin {
    @Redirect(method = "renderPortalArea",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_enableCull()V", remap = false))
    private static void vulkanmod$enableCull() {
        RenderSystem.enableCull();
    }

    @Redirect(method = "renderPortalArea",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_disableCull()V", remap = false))
    private static void vulkanmod$disableCull() {
        RenderSystem.disableCull();
    }
}
