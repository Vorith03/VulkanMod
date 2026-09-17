package net.vulkanmod.mixin.debug;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * CI-only runtime smoke for the optional FTB Library compatibility mixins and
 * the generic RenderSystem texture-id bridge used by FTB's image icons.
 * Loading the scissor targets validates every redirect against published FTB
 * bytecode; invoking a nested scissor pair during an active Vulkan frame then
 * exercises enable, set/restore, and disable with no usable OpenGL context.
 */
@Mixin(Minecraft.class)
public abstract class FTBLibraryCompatSmokeMixin {
    private boolean vulkanmod$ftbLibraryScissorSmokeRan;

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanmod$verifyFtbLibraryCompatTargets(boolean tick, CallbackInfo ci) {
        if (this.vulkanmod$ftbLibraryScissorSmokeRan || !Boolean.getBoolean("vulkanmod.ciFtbLibrarySmoke")) {
            return;
        }
        this.vulkanmod$ftbLibraryScissorSmokeRan = true;

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> guiHelper = Class.forName("dev.ftb.mods.ftblibrary.ui.GuiHelper", false, loader);
            Class.forName("dev.ftb.mods.ftblibrary.ui.GuiHelper$Scissor", false, loader);

            Method pushScissor = guiHelper.getMethod(
                    "pushScissor", Window.class, int.class, int.class, int.class, int.class);
            Method popScissor = guiHelper.getMethod("popScissor", Window.class);
            Minecraft minecraft = (Minecraft) (Object) this;
            Window window = minecraft.getWindow();

            // Exercise every raw-GL site in FTB's stack path while Renderer has an
            // active command buffer and bound swapchain framebuffer. FTB accepts
            // partially off-screen rectangles under OpenGL, so cover the negative
            // offset that Vulkan must clip before recording vkCmdSetScissor.
            pushScissor.invoke(null, window, -4, -4, 16, 16);
            popScissor.invoke(null, window);

            // The disjoint nested rectangle crops to an empty rectangle whose
            // retained origin is outside the window. This covers nested set,
            // empty/out-of-bounds clipping, restore, and final disable.
            pushScissor.invoke(null, window, 1, 1, 16, 16);
            pushScissor.invoke(null, window, 1_000_000, 1_000_000, 1, 1);
            popScissor.invoke(null, window);
            popScissor.invoke(null, window);

            // FTB Library 2001.x ImageIcon binds AbstractTexture#getId() through
            // RenderSystem.setShaderTexture(int, int), not the ResourceLocation
            // overload. Exercise that exact published call path and prove the
            // synthetic GL name resolves back to the image's Vulkan backing.
            ResourceLocation imageLocation = new ResourceLocation(
                    "ftblibrary", "textures/gui/missing_image.png");
            Class<?> imageIconClass = Class.forName(
                    "dev.ftb.mods.ftblibrary.icon.ImageIcon", false, loader);
            Object imageIcon = imageIconClass
                    .getConstructor(ResourceLocation.class)
                    .newInstance(imageLocation);
            imageIconClass.getMethod("bindTexture").invoke(imageIcon);

            AbstractTexture abstractTexture = minecraft.getTextureManager().getTexture(imageLocation);
            VulkanImage expectedImage = ((VAbstractTextureI) abstractTexture).getVulkanImage();
            if (expectedImage == null) {
                throw new IllegalStateException("FTB Library ImageIcon had no Vulkan backing texture");
            }
            if (VTextureSelector.getBoundTexture() != expectedImage) {
                throw new IllegalStateException(
                        "FTB Library integer ImageIcon binding did not select its Vulkan texture");
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("FTB Library compatibility smoke invocation failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FTB Library compatibility smoke target was not available", e);
        }

        Initializer.LOGGER.info("FTB Library scissor compatibility mixin smoke passed");
    }
}
