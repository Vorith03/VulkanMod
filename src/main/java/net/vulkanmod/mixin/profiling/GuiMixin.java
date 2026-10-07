package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import net.vulkanmod.render.profiling.ProfilerOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Unique private long vulkanmod$guiTickStart;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void createProfilerOverlay(Minecraft minecraft, ItemRenderer itemRenderer, CallbackInfo ci) {
        ProfilerOverlay.createInstance(minecraft);
    }

    @Inject(method = "tick(Z)V", at = @At("HEAD"))
    private void vulkanmod$beginGuiTick(boolean paused, CallbackInfo ci) {
        vulkanmod$guiTickStart = ClientTickBreakdown.begin(ClientTickBreakdown.Stage.GUI);
    }

    @Inject(method = "tick(Z)V", at = @At("RETURN"))
    private void vulkanmod$endGuiTick(boolean paused, CallbackInfo ci) {
        ClientTickBreakdown.end(ClientTickBreakdown.Stage.GUI, vulkanmod$guiTickStart);
        vulkanmod$guiTickStart = 0L;
    }

}
