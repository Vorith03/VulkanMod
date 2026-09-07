package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Crash Assistant 1.9.x probes the active GPU with OpenGL's glGetString during
 * Minecraft initialization. VulkanMod deliberately owns a GLFW NO_API window,
 * so there is no OpenGL context and that native call aborts the JVM.
 *
 * The detector itself is an interface with a static method. Use an interface
 * mixin and a public static overwrite (supported by Mixin 0.8.5) rather than a
 * callback injector, because static interface injection is not supported by
 * this Mixin generation. Under VulkanMod there is intentionally no OpenGL
 * context to query, so the legacy OpenGL-only detector is simply disabled.
 */
@Pseudo
@Mixin(targets = "dev.kostromdan.mods.crash_assistant.common.utils.CurrentGPUDetector", remap = false)
public interface CrashAssistantCurrentGPUDetectorMixin {

    @Overwrite(remap = false)
    static void writeCurrentGPU() {
        // VulkanMod uses a GLFW NO_API window; Crash Assistant's legacy OpenGL
        // renderer probe is invalid in this process and must not be executed.
    }
}
