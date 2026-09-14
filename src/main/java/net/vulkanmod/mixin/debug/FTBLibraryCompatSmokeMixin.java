package net.vulkanmod.mixin.debug;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * CI-only runtime smoke for the optional FTB Library compatibility mixins.
 * Loading both targets validates every redirect against published FTB bytecode;
 * invoking a nested scissor pair during an active Vulkan frame then exercises
 * enable, set/restore, and disable with no usable OpenGL context.
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
            Window window = ((Minecraft) (Object) this).getWindow();

            // Exercise every raw-GL site in FTB's stack path while Renderer has an
            // active command buffer and bound swapchain framebuffer: first push
            // enables and sets the scissor, nested push sets it again, first pop
            // restores the outer rectangle, and final pop disables scissoring.
            pushScissor.invoke(null, window, 1, 1, 16, 16);
            pushScissor.invoke(null, window, 4, 4, 8, 8);
            popScissor.invoke(null, window);
            popScissor.invoke(null, window);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("FTB Library compatibility smoke target was not available", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("FTB Library scissor compatibility smoke invocation failed", cause);
        }

        Initializer.LOGGER.info("FTB Library scissor compatibility mixin smoke passed");
    }
}
