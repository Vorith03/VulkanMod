package net.vulkanmod.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.Path;

@Mixin(GameRenderer.class)
public abstract class GameRendererScreenshotMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private void takeAutoScreenshot(Path file) { throw new AssertionError(); }

    // Keep vanilla's readiness checks and 64x64 crop/write logic. Only defer the
    // actual capture; it is recorded now, before later GUI rendering changes it.
    @Inject(method = "takeAutoScreenshot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;"), cancellable = true)
    private void vulkanmod$deferWorldIcon(Path file, CallbackInfo ci) {
        var target = this.minecraft.getMainRenderTarget();
        if(ScreenshotReadback.hasCapture(target)) return;
        ScreenshotReadback.request(target).whenComplete((image, failure) -> {
            if(failure != null)
                Initializer.LOGGER.warn("Could not capture world icon", failure);
            else {
                try {
                    ScreenshotReadback.withCapture(target, image, () -> this.takeAutoScreenshot(file));
                } catch(RuntimeException error) {
                    Initializer.LOGGER.warn("Could not save captured world icon", error);
                }
            }
        });
        ci.cancel();
    }
}
