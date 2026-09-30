package net.vulkanmod.mixin.profiling;

import net.minecraft.client.sounds.SoundManager;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerPerformanceMixin {
    @Unique private long vulkanmod$tickStart;

    @Inject(method = "tick(Z)V", at = @At("HEAD"))
    private void vulkanmod$beginTick(boolean paused, CallbackInfo ci) {
        vulkanmod$tickStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.SOUND);
    }

    @Inject(method = "tick(Z)V", at = @At("RETURN"))
    private void vulkanmod$endTick(boolean paused, CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.SOUND, vulkanmod$tickStart);
        vulkanmod$tickStart = 0L;
    }
}
