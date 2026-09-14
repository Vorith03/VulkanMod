package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Routes FTB Library's already-computed framebuffer scissor rectangle through
 * Minecraft's render abstraction, which VulkanMod implements natively.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.GuiHelper$Scissor", remap = false)
public abstract class FTBLibraryScissorMixin {

    @Redirect(
            method = "scissor(Lcom/mojang/blaze3d/platform/Window;)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScissor(IIII)V", remap = false)
    )
    private void vulkanmod$setScissor(int x, int y, int width, int height) {
        RenderSystem.enableScissor(x, y, width, height);
    }
}
