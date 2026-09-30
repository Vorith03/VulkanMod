package net.vulkanmod.mixin.profiling;

import net.minecraft.client.tutorial.Tutorial;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tutorial.class)
public class TutorialPerformanceMixin {
    @Unique private long vulkanmod$tickStart;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void vulkanmod$beginTick(CallbackInfo ci) {
        vulkanmod$tickStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.TUTORIAL);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void vulkanmod$endTick(CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.TUTORIAL, vulkanmod$tickStart);
        vulkanmod$tickStart = 0L;
    }
}
