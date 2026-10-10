package net.vulkanmod.mixin.profiling;

import net.minecraft.client.particle.ParticleEngine;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import net.vulkanmod.render.profiling.ParticleAttribution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep engine-level tick timing for manual and automated profiling.
 * Per-particle hooks live in a separate mixin, excluded in ordinary gameplay.
 */
@Mixin(ParticleEngine.class)
public class ParticleEnginePerformanceMixin {
    @Unique private long vulkanmod$tickStart;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void vulkanmod$beginTick(CallbackInfo ci) {
        ParticleAttribution.beginEngineTick();
        vulkanmod$tickStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.PARTICLES);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void vulkanmod$endTick(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.PARTICLES, vulkanmod$tickStart);
        vulkanmod$tickStart = 0L;
    }
}
