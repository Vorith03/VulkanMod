package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * The normal Immersive Portals renderer depends on an OpenGL stencil buffer and
 * fixed-function stencil state. VulkanMod does not emulate that contract. When
 * IP is left at its default normal mode, select IP's own framebuffer-based
 * compatibility renderer instead. Explicit compatibility/debug/none choices are
 * left untouched.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.PortalRenderer", remap = false)
public abstract class ImmersivePortalsPortalRendererMixin {
    private static boolean vulkanmod$loggedFramebufferFallback;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "switchToCorrectRenderer()V", at = @At("HEAD"))
    private static void vulkanmod$selectFramebufferRenderer(CallbackInfo ci) {
        try {
            Class<?> globalClass = Class.forName("qouteall.imm_ptl.core.IPGlobal");
            Field renderModeField = globalClass.getField("renderMode");
            Object current = renderModeField.get(null);
            if(current instanceof Enum<?> currentMode && "normal".equals(currentMode.name())) {
                Class<? extends Enum> enumClass = currentMode.getDeclaringClass();
                Object compatibility = Enum.valueOf(enumClass, "compatibility");
                renderModeField.set(null, compatibility);
                if(!vulkanmod$loggedFramebufferFallback) {
                    vulkanmod$loggedFramebufferFallback = true;
                    Initializer.LOGGER.info(
                            "Immersive Portals normal stencil renderer is unavailable under Vulkan; using its framebuffer compatibility renderer");
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Unable to select Immersive Portals framebuffer compatibility renderer", e);
        }
    }
}
