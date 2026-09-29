package net.vulkanmod.mixin.profiling;

import net.minecraft.client.multiplayer.ClientLevel;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Isolates client world/entity simulation within Minecraft.tick(). */
@Mixin(ClientLevel.class)
public class ClientLevelPerformanceMixin {
    @Unique private long vulkanmod$levelTickStart;
    @Unique private long vulkanmod$entityTickStart;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void vulkanmod$beginLevelTick(CallbackInfo ci) {
        vulkanmod$levelTickStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.CLIENT_LEVEL_TICK);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    private void vulkanmod$endLevelTick(CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.CLIENT_LEVEL_TICK, vulkanmod$levelTickStart);
        vulkanmod$levelTickStart = 0L;
    }

    @Inject(method = "tickEntities()V", at = @At("HEAD"))
    private void vulkanmod$beginEntities(CallbackInfo ci) {
        vulkanmod$entityTickStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.CLIENT_ENTITIES_TICK);
    }

    @Inject(method = "tickEntities()V", at = @At("RETURN"))
    private void vulkanmod$endEntities(CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.CLIENT_ENTITIES_TICK, vulkanmod$entityTickStart);
        vulkanmod$entityTickStart = 0L;
    }
}
