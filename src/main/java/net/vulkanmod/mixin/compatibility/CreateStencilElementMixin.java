package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.compatibility.CreateStencilCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Create 0.5.1.j renders several GUI elements through a dedicated stencil
 * RenderTarget, but StencilElement toggles GL_STENCIL_TEST directly through
 * LWJGL. VulkanMod owns a GLFW_NO_API window, so those calls must be redirected
 * at the Create call site while preserving its RenderSystem stencil mask,
 * function, operation, and clear sequence.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.gui.element.StencilElement", remap = false)
public abstract class CreateStencilElementMixin {
    @Redirect(
            method = {
                    "prepareStencil(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    "prepareElement(Lcom/mojang/blaze3d/vertex/PoseStack;)V"
            },
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false)
    )
    private void vulkanmod$enableStencilTest(int capability) {
        CreateStencilCompat.enableStencilTest(capability);
    }

    @Redirect(
            method = {
                    "prepareStencil(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    "cleanUp(Lcom/mojang/blaze3d/vertex/PoseStack;)V"
            },
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false)
    )
    private void vulkanmod$disableStencilTest(int capability) {
        CreateStencilCompat.disableStencilTest(capability);
    }
}
