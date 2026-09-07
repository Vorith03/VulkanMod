package net.vulkanmod.mixin.compatibility;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crash Assistant 1.9.x probes the active GPU with OpenGL's glGetString during
 * Minecraft initialization. VulkanMod deliberately creates a GLFW NO_API window,
 * so there is no OpenGL context and LWJGL aborts the JVM before Java can recover.
 *
 * Newer Crash Assistant versions already guard this call with
 * glfwGetCurrentContext(). Keep the same guard here so older modpacks remain safe
 * without changing behavior when an OpenGL context actually exists.
 */
@Pseudo
@Mixin(targets = "dev.kostromdan.mods.crash_assistant.common.utils.CurrentGPUDetector", remap = false)
public abstract class CrashAssistantCurrentGPUDetectorMixin {

    @Inject(method = "writeCurrentGPU()V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void vulkanmod$skipGpuProbeWithoutOpenGlContext(CallbackInfo ci) {
        if (GLFW.glfwGetCurrentContext() == 0L) {
            ci.cancel();
        }
    }
}
