package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps FTB Library's scissor stack intact while avoiding raw OpenGL state
 * changes when VulkanMod owns rendering and no OpenGL context exists.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.GuiHelper", remap = false)
public abstract class FTBLibraryGuiHelperMixin {

    @Redirect(
            method = "pushScissor(Lcom/mojang/blaze3d/platform/Window;IIII)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false)
    )
    private static void vulkanmod$skipRawScissorEnable(int capability) {
        // The following GL11.glScissor call is redirected to RenderSystem.enableScissor,
        // which both enables and configures VulkanMod's scissor state.
    }

    @Redirect(
            method = "popScissor(Lcom/mojang/blaze3d/platform/Window;)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false)
    )
    private static void vulkanmod$disableScissor(int capability) {
        RenderSystem.disableScissor();
    }
}
