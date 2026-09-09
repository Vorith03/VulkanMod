package net.vulkanmod.mixin.render;

import net.minecraft.client.Minecraft;
import net.vulkanmod.render.chunk.ResourceReloadMemoryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class ResourceReloadMixin {
    @Unique
    private final ThreadLocal<ArrayDeque<Long>> vulkanmod$resourceReloadGenerations =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD")
    )
    private void vulkanmod$retireTerrainBeforeResourceReload(
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        this.vulkanmod$resourceReloadGenerations.get()
                .addLast(ResourceReloadMemoryManager.beginResourceReload());
    }

    @Inject(
            method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN")
    )
    private void vulkanmod$ensureTerrainAfterResourceReload(
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ArrayDeque<Long> generations = this.vulkanmod$resourceReloadGenerations.get();
        long generation = generations.isEmpty() ? 0L : generations.removeLast();
        if(generations.isEmpty()) {
            this.vulkanmod$resourceReloadGenerations.remove();
        }

        CompletableFuture<Void> reload = cir.getReturnValue();
        if(reload == null) {
            ResourceReloadMemoryManager.completeResourceReload(
                    generation, new IllegalStateException("Minecraft returned a null resource-reload future"));
            return;
        }

        reload.whenComplete((unused, failure) -> {
            Minecraft minecraft = Minecraft.getInstance();
            Runnable completion = () -> ResourceReloadMemoryManager.completeResourceReload(generation, failure);
            if(minecraft != null) {
                minecraft.execute(completion);
            } else {
                completion.run();
            }
        });
    }
}
