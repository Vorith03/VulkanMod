package net.vulkanmod.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * Restores matrices explicitly supplied by Immersive Portals after a converted
 * legacy ShaderInstance has been applied.
 *
 * VulkanMod's legacy ShaderInstance bridge mirrors the current global
 * RenderSystem matrices during apply(). That is correct for ordinary
 * BufferUploader draws, but Immersive Portals' portal-area shaders deliberately
 * set camera-relative model-view/projection matrices before calling apply().
 * Re-publish those explicit values before the portal geometry is submitted so
 * the Vulkan UBO sees the same matrices as the vanilla/OpenGL shader would.
 */
public final class ImmersivePortalsPortalMatrixCompat {
    private ImmersivePortalsPortalMatrixCompat() {
    }

    public static void restoreExplicitPortalMatrices(Matrix4f modelView, Matrix4f projection) {
        ShaderInstance shader = RenderSystem.getShader();
        if(shader == null) {
            throw new IllegalStateException(
                    "Immersive Portals portal-area draw has no active ShaderInstance");
        }
        if(shader.MODEL_VIEW_MATRIX == null || shader.PROJECTION_MATRIX == null) {
            throw new IllegalStateException(
                    "Immersive Portals portal-area shader is missing ModelViewMat/ProjMat uniforms: "
                            + shader.getName());
        }

        shader.MODEL_VIEW_MATRIX.set(modelView);
        shader.PROJECTION_MATRIX.set(projection);
    }
}
