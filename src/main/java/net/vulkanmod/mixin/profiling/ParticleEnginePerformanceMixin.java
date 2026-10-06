package net.vulkanmod.mixin.profiling;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import net.vulkanmod.render.profiling.ParticleAttribution;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ParticleEngine.class)
public class ParticleEnginePerformanceMixin {
    @Shadow @Final private Map<ResourceLocation, ParticleProvider<?>> providers;

    @Unique private long vulkanmod$tickStart;
    @Unique private int vulkanmod$particleTickToken;
    @Unique private long vulkanmod$particleTickStart;
    @Unique private long vulkanmod$particleAllocationStart;

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

    @Inject(method = "tickParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"))
    private void vulkanmod$beginParticleTick(Particle particle, CallbackInfo ci) {
        int token = ParticleAttribution.beginParticleTick(particle);
        this.vulkanmod$particleTickToken = token;
        if(token > 0) {
            this.vulkanmod$particleTickStart = System.nanoTime();
            this.vulkanmod$particleAllocationStart = ParticleAttribution.allocatedBytes();
        } else {
            this.vulkanmod$particleTickStart = 0L;
            this.vulkanmod$particleAllocationStart = -1L;
        }
    }

    @Inject(method = "tickParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("RETURN"))
    private void vulkanmod$endParticleTick(Particle particle, CallbackInfo ci) {
        int token = this.vulkanmod$particleTickToken;
        long elapsed = token > 0 && this.vulkanmod$particleTickStart != 0L
                ? Math.max(0L, System.nanoTime() - this.vulkanmod$particleTickStart)
                : 0L;
        long allocationEnd = token > 0 ? ParticleAttribution.allocatedBytes() : -1L;
        long allocated = this.vulkanmod$particleAllocationStart >= 0L
                && allocationEnd >= this.vulkanmod$particleAllocationStart
                ? allocationEnd - this.vulkanmod$particleAllocationStart
                : -1L;
        ParticleAttribution.endParticleTick(token, elapsed, allocated, !particle.isAlive());
        this.vulkanmod$particleTickToken = 0;
        this.vulkanmod$particleTickStart = 0L;
        this.vulkanmod$particleAllocationStart = -1L;
    }

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"))
    private void vulkanmod$recordParticleAdd(Particle particle, CallbackInfo ci) {
        ParticleAttribution.recordAdded(particle, particle == null ? null : particle.getRenderType());
    }

    @Inject(
            method = "makeParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN")
    )
    private <T extends ParticleOptions> void vulkanmod$recordParticleSource(
            T options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
            CallbackInfoReturnable<Particle> cir) {
        Particle particle = cir.getReturnValue();
        if(particle == null || options == null) {
            return;
        }
        ResourceLocation source = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        ParticleProvider<?> provider = source == null ? null : this.providers.get(source);
        ParticleAttribution.recordCreated(particle, source, provider);
    }
}
