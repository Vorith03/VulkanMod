package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.Minecraft;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Immersive Portals loads its shader transformation table as a resource reload
 * listener. VulkanMod's early core-shader construction can therefore run before
 * IP knows which shaders need clipping support. Rebuild only the shader set once
 * the enclosing resource reload has completed so the transformed Vulkan variants
 * are created from the now-live IP transformation table.
 */
@Mixin(Minecraft.class)
public abstract class ImmersivePortalsResourceReloadMixin {
    @Inject(
            method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN")
    )
    private void vulkanmod$rebuildShadersAfterImmersivePortalsReload(
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if(!ImmersivePortalsShaderCompat.isAvailable()) {
            return;
        }

        CompletableFuture<Void> reload = cir.getReturnValue();
        if(reload == null) {
            return;
        }

        reload.whenComplete((unused, failure) -> {
            if(failure != null) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if(minecraft == null) {
                return;
            }

            minecraft.execute(() ->
                    ((ImmersivePortalsGameRendererInvoker)(Object)minecraft.gameRenderer)
                            .vulkanmod$reloadShaders(minecraft.getResourceManager()));
        });
    }
}
