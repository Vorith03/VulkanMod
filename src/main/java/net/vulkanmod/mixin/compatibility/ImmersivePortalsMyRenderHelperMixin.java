package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VulkanMod does not currently expose front/back cull-face selection. For the
 * mirror-only reversal path, temporarily disabling culling is conservative and
 * avoids a raw glCullFace call with no OpenGL context.
 *
 * Immersive Portals initializes its shader transformation table immediately
 * before MyRenderHelper.init() and registers its extra shader listeners inside
 * this method. VulkanMod's initial shader reload can finish before that client
 * setup task runs, so rebuild the shader set once at RETURN when clipping is
 * actually active. This gives both IP's dynamic clipping uniforms and its extra
 * shaders a live Vulkan pipeline on first launch; later resource-pack reloads
 * remain covered by ImmersivePortalsResourceReloadMixin.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.MyRenderHelper", remap = false)
public abstract class ImmersivePortalsMyRenderHelperMixin {
    @Unique
    private static boolean vulkanmod$startupShaderReloaded;

    @Inject(method = "init()V", at = @At("RETURN"))
    private static void vulkanmod$rebuildShadersAfterPortalShaderInit(CallbackInfo ci) {
        if(vulkanmod$startupShaderReloaded
                || !ImmersivePortalsShaderCompat.shouldTransform("rendertype_solid")) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft == null || minecraft.gameRenderer == null) {
            return;
        }

        vulkanmod$startupShaderReloaded = true;
        ((ImmersivePortalsGameRendererInvoker)(Object)minecraft.gameRenderer)
                .vulkanmod$reloadShaders(minecraft.getResourceManager());
    }

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
