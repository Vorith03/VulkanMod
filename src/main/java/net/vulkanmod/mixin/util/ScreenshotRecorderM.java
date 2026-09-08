package net.vulkanmod.mixin.util;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import net.vulkanmod.Initializer;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ScreenshotEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class ScreenshotRecorderM {

    @Shadow
    private static void _grab(File directory, String name, RenderTarget target, Consumer<Component> feedback) {
        throw new AssertionError();
    }

    @Inject(method = "_grab", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$deferGrab(File directory, String name, RenderTarget target,
                                           Consumer<Component> feedback, CallbackInfo ci) {
        if(ScreenshotReadback.hasCapture(target)) return;
        ScreenshotReadback.request(target).whenComplete((image, failure) -> {
            if(failure != null)
                feedback.accept(Component.translatable("screenshot.failure", failure.getMessage()));
            else {
                try {
                    ScreenshotReadback.withCapture(target, image, () -> _grab(directory, name, target, feedback));
                } catch(RuntimeException error) {
                    Initializer.LOGGER.warn("Could not save captured screenshot", error);
                    feedback.accept(Component.translatable("screenshot.failure", error.getMessage()));
                }
            }
        });
        ci.cancel();
    }

    // Forge's cancelled branch returns before vanilla's asynchronous writer takes
    // ownership. Release the completed capture in that branch as well.
    @Redirect(method = "_grab", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;onScreenshot(Lcom/mojang/blaze3d/platform/NativeImage;Ljava/io/File;)Lnet/minecraftforge/client/event/ScreenshotEvent;", remap = false))
    private static ScreenshotEvent vulkanmod$screenshotEvent(NativeImage image, File file) {
        ScreenshotEvent event = ForgeHooksClient.onScreenshot(image, file);
        if(event.isCanceled()) image.close();
        return event;
    }

    /**
     * @author
     */
    @Overwrite
    public static NativeImage takeScreenshot(RenderTarget framebuffer) {
        return ScreenshotReadback.takeCapture(framebuffer);
    }
}
