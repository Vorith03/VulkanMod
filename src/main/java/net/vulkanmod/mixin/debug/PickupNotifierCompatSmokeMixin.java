package net.vulkanmod.mixin.debug;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** CI-only runtime smoke for Pick Up Notifier's transparency framebuffer path. */
@Mixin(Minecraft.class)
public abstract class PickupNotifierCompatSmokeMixin {
    private boolean vulkanmod$pickupNotifierSmokeRan;

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanmod$verifyPickupNotifierFramebufferCompat(boolean tick, CallbackInfo ci) {
        if(this.vulkanmod$pickupNotifierSmokeRan || !Boolean.getBoolean("vulkanmod.ciPickupNotifierSmoke")) {
            return;
        }
        this.vulkanmod$pickupNotifierSmokeRan = true;

        try {
            RenderPass initialPass = Renderer.getInstance().getBoundRenderPass();
            if(initialPass == null || initialPass.getFramebuffer() != Vulkan.getSwapChain()) {
                throw new IllegalStateException("Pick Up Notifier smoke did not start on the Vulkan main framebuffer");
            }

            Class<?> transparencyBuffer = Class.forName(
                    "fuzs.pickupnotifier.client.util.TransparencyBuffer",
                    true,
                    Thread.currentThread().getContextClassLoader());
            Method prepareExtraFramebuffer = transparencyBuffer.getMethod("prepareExtraFramebuffer");
            prepareExtraFramebuffer.invoke(null);

            Field previousFramebuffer = transparencyBuffer.getDeclaredField("previousFramebuffer");
            previousFramebuffer.setAccessible(true);
            if(previousFramebuffer.getInt(null) != 0) {
                throw new IllegalStateException(
                        "Pick Up Notifier did not capture Vulkan's main framebuffer as GL framebuffer 0");
            }

            RenderPass offscreenPass = Renderer.getInstance().getBoundRenderPass();
            if(offscreenPass == null || offscreenPass.getFramebuffer() == Vulkan.getSwapChain()) {
                throw new IllegalStateException("Pick Up Notifier did not bind its off-screen Vulkan RenderTarget");
            }

            // drawExtraFramebuffer restores this exact binding before compositing.
            // Exercise the same call without needing to construct a full GuiGraphics.
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            RenderPass restoredPass = Renderer.getInstance().getBoundRenderPass();
            if(restoredPass == null || restoredPass.getFramebuffer() != Vulkan.getSwapChain()) {
                throw new IllegalStateException("GL framebuffer 0 did not restore Vulkan's main render pass");
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Pick Up Notifier compatibility smoke invocation failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Pick Up Notifier compatibility smoke target was not available", e);
        }

        Initializer.LOGGER.info("Pick Up Notifier 8.0.0 framebuffer compatibility smoke passed");
    }
}
