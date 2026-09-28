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
     * The visibility/depth portal-area pass also supplies its own model-view and
     * projection matrices before ShaderInstance.apply(). Restore those values
     * immediately after apply so Vulkan's UBO upload uses IP's camera-relative
     * portal transform instead of the outer global RenderSystem matrices.
     */
    @Inject(
            method = "renderPortalArea",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V",
                    shift = At.Shift.AFTER,
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
