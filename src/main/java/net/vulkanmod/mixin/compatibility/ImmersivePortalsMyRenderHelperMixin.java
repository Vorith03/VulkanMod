package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.vulkanmod.compatibility.ImmersivePortalsPortalMatrixCompat;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
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
 * setup task runs, so rebuild the full IP-aware shader set once at RETURN.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.MyRenderHelper", remap = false)
public abstract class ImmersivePortalsMyRenderHelperMixin {
    @Unique
    private static boolean vulkanmod$startupShaderReloaded;

    @Inject(method = "init()V", at = @At("RETURN"))
    private static void vulkanmod$rebuildShadersAfterPortalShaderInit(CallbackInfo ci) {
        if(vulkanmod$startupShaderReloaded) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft == null || minecraft.gameRenderer == null) {
            return;
        }

        vulkanmod$startupShaderReloaded = true;
        ImmersivePortalsShaderCompat.markRenderHelperReady();
        ImmersivePortalsShaderCompat.rebuildShaders(minecraft);
    }

    /**
     * Immersive Portals' early remote-world upload is a vanilla renderer
     * pre-pass: it walks portal-world LevelRenderers and asks their vanilla
     * ChunkRenderDispatcher to upload pending chunk meshes. VulkanMod replaces
     * that terrain renderer, so those LevelRenderers intentionally have no
     * vanilla dispatcher. Letting the pre-pass run therefore dereferences a
     * null dispatcher before VulkanMod can render the frame. Vulkan terrain
     * upload/publication is owned by VulkanMod's chunk renderer instead.
     */
    @Inject(method = "earlyRemoteUpload()V", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$skipVanillaRemoteChunkUpload(CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * RendererUsingFrameBuffer sets portal-specific matrices on its custom
     * framebuffer shader and then calls ShaderInstance.apply(). VulkanMod's
     * converted-legacy apply path mirrors global RenderSystem matrices, so put
     * IP's explicit matrices back after apply and immediately before the portal
     * triangles are submitted. Anchor on IP's own buffer-build call so this is
     * stable in the packaged Forge runtime as well as the development runtime.
     */
    @Inject(
            method = "drawPortalAreaWithFramebuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lqouteall/imm_ptl/core/render/ViewAreaRenderer;buildPortalViewAreaTrianglesBuffer(Lnet/minecraft/world/phys/Vec3;Lqouteall/imm_ptl/core/render/PortalRenderable;Lnet/minecraft/world/phys/Vec3;F)V",
                    shift = At.Shift.BEFORE,
                    remap = false
            )
    )
    private static void vulkanmod$restoreFramebufferPortalMatrices(
            @Coerce Object portal,
            RenderTarget textureProvider,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci) {
        ImmersivePortalsPortalMatrixCompat.restoreExplicitPortalMatrices(
                modelViewMatrix, projectionMatrix);
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
