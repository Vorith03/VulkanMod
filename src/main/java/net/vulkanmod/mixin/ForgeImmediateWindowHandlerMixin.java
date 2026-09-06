package net.vulkanmod.mixin;

import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge continues to call ImmediateWindowHandler.renderTick() directly during
 * parts of client bootstrap, even after its early OpenGL window has handed off
 * to Minecraft. Once VulkanMod replaces that window with a GLFW_NO_API window,
 * the old DisplayWindow provider must not attempt any more OpenGL rendering.
 */
@Mixin(value = ImmediateWindowHandler.class, remap = false)
public abstract class ForgeImmediateWindowHandlerMixin {
    private static volatile boolean vulkanmod$vulkanWindowActive;

    public static void vulkanmod$disableEarlyDisplayTicks() {
        vulkanmod$vulkanWindowActive = true;
    }

    @Inject(method = "renderTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void vulkanmod$skipEarlyOpenGLTick(CallbackInfo ci) {
        if (vulkanmod$vulkanWindowActive) {
            ci.cancel();
        }
    }
}
