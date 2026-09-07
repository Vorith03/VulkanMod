package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Crash Assistant 1.9.x injects a CurrentGPUDetector.writeCurrentGPU() call at
 * the end of Minecraft's constructor. That detector calls OpenGL glGetString(),
 * which fatally aborts LWJGL when VulkanMod owns a GLFW NO_API window.
 *
 * Crash Assistant's detector is an interface with a static method, which Mixin
 * 0.8.5 cannot safely target with a normal injector. Apply after Crash
 * Assistant's priority-900 Minecraft mixin instead and redirect only the
 * injected GPU-probe call. Newer Crash Assistant releases already perform the
 * same GLFW current-context check themselves, so this remains harmless there.
 */
@Mixin(value = Minecraft.class, priority = 800)
public abstract class CrashAssistantMinecraftMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/kostromdan/mods/crash_assistant/common/utils/CurrentGPUDetector;writeCurrentGPU()V",
                    remap = false
            ),
            require = 0
    )
    private void vulkanmod$guardCrashAssistantGpuProbe() {
        if (GLFW.glfwGetCurrentContext() == 0L) {
            return;
        }

        try {
            Class<?> detector = Class.forName("dev.kostromdan.mods.crash_assistant.common.utils.CurrentGPUDetector");
            detector.getMethod("writeCurrentGPU").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke Crash Assistant GPU detector", e);
        }
    }
}
