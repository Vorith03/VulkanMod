package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.compatibility.ImmersivePortalsPortalMatrixCompat;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Translate Immersive Portals' direct cull state and preserve its explicit
 * portal-area matrices across VulkanMod's converted legacy shader bridge.
 */
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

    /**
     * The visibility/depth portal-area pass supplies its own model-view and
     * projection matrices before ShaderInstance.apply(). VulkanMod's converted
     * legacy apply path mirrors global RenderSystem matrices, so put IP's values
     * back after apply and before the portal triangles are submitted.
     *
     * Keep the injection anchor entirely inside Immersive Portals. This mixin
     * targets an optional third-party class with remap=false; anchoring on the
     * Mojmap-named ShaderInstance.apply() invocation works in runClient but not
     * in a reobfuscated production Forge runtime. IP's own buffer-builder call
     * is the stable 3.0.7 boundary immediately after apply().
     */
    @Inject(
            method = "renderPortalArea",
            at = @At(
                    value = "INVOKE",
                    target = "Lqouteall/imm_ptl/core/render/ViewAreaRenderer;buildPortalViewAreaTrianglesBuffer(Lnet/minecraft/world/phys/Vec3;Lqouteall/imm_ptl/core/render/PortalRenderable;Lnet/minecraft/world/phys/Vec3;F)V",
                    remap = false
            )
    )
    private static void vulkanmod$restorePortalAreaMatrices(
            @Coerce Object portalRenderable,
            Vec3 fogColor,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            boolean doFaceCulling,
            boolean doModifyColor,
            boolean doModifyDepth,
            boolean doClip,
            CallbackInfo ci) {
        ImmersivePortalsPortalMatrixCompat.restoreExplicitPortalMatrices(
                modelViewMatrix, projectionMatrix);
    }
}
