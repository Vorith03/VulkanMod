package net.vulkanmod.mixin.profiling;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separates packet integration from other client tick work. */
@Mixin(ClientPacketListener.class)
public class ClientConnectionPerformanceMixin {
    @Unique private long vulkanmod$connectionTickStart;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void vulkanmod$beginConnectionTick(CallbackInfo ci) {
        vulkanmod$connectionTickStart = PerformanceProfiler.begin(PerformanceProfiler.Stage.CLIENT_CONNECTION_TICK);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void vulkanmod$endConnectionTick(CallbackInfo ci) {
        PerformanceProfiler.end(PerformanceProfiler.Stage.CLIENT_CONNECTION_TICK, vulkanmod$connectionTickStart);
        vulkanmod$connectionTickStart = 0L;
    }
}
