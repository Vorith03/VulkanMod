package net.vulkanmod.mixin.render;

import net.minecraft.client.Minecraft;
import net.vulkanmod.render.chunk.ResourceReloadMemoryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class ResourceReloadMixin {
    @Inject(
            method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD")
    )
    private void vulkanmod$retireTerrainBeforeResourceReload(
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ResourceReloadMemoryManager.prepareForReload();
    }

    @Inject(
            method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN")
    )
    private void vulkanmod$ensureTerrainAfterResourceReload(
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        CompletableFuture<Void> reload = cir.getReturnValue();
        if(reload == null) {
            ResourceReloadMemoryManager.ensureTerrainReadyAfterReload();
            return;
        }

        reload.whenComplete((unused, failure) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if(minecraft != null) {
                minecraft.execute(ResourceReloadMemoryManager::ensureTerrainReadyAfterReload);
            }
        });
    }
}
