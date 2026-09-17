package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * VulkanMod does not currently expose front/back cull-face selection. For the
 * mirror-only reversal path, temporarily disabling culling is conservative and
 * avoids a raw glCullFace call with no OpenGL context.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.MyRenderHelper", remap = false)
public abstract class ImmersivePortalsMyRenderHelperMixin {
    @Redirect(method = "applyMirrorFaceCulling()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCullFace(I)V", remap = false))
    private static void vulkanmod$disableCullForMirror(int mode) {
        RenderSystem.disableCull();
    }

    @Redirect(method = "recoverFaceCulling()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCullFace(I)V", remap = false))
    private static void vulkanmod$restoreCullAfterMirror(int mode) {
        RenderSystem.enableCull();
    }
}
