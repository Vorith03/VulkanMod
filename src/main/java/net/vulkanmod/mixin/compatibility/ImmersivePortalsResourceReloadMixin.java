package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.Minecraft;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Once Immersive Portals is active, rebuild both VulkanMod's core shaders and
 * IP's helper shaders after manual resource-pack reloads. Startup initialization
 * is handled separately from MyRenderHelper.init(), because Minecraft's initial
 * resource load does not pass through reloadResourcePacks().
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

            minecraft.execute(() -> ImmersivePortalsShaderCompat.rebuildShaders(minecraft));
        });
    }
}
