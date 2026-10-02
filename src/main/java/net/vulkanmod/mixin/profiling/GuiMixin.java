package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;
import net.vulkanmod.render.profiling.AutomatedBenchmark;
import net.vulkanmod.render.profiling.ClientTickBreakdown;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import net.vulkanmod.render.profiling.ProfilerOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Shadow @Final private Minecraft minecraft;

    @Unique private long vulkanmod$nextStagingRefresh;
    @Unique private String vulkanmod$stagingLine;
    @Unique private String vulkanmod$benchmarkLine;
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

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderEffects(Lnet/minecraft/client/gui/GuiGraphics;)V", shift = At.Shift.AFTER))
    private void renderProfilerOverlay(GuiGraphics guiGraphics, float f, CallbackInfo ci) {
        if(ProfilerOverlay.shouldRender && !this.minecraft.options.renderDebug)
            ProfilerOverlay.INSTANCE.render(guiGraphics.pose());

        if((PerformanceProfiler.isEnabled() || AutomatedBenchmark.enabled()) && this.minecraft.level != null) {
            long now = System.nanoTime();
            if(now >= this.vulkanmod$nextStagingRefresh) {
                this.vulkanmod$stagingLine = RegionVoxelStore.ENABLED ? RegionVoxelStore.overlayCount() : null;
                this.vulkanmod$benchmarkLine = AutomatedBenchmark.statusLine();
                this.vulkanmod$nextStagingRefresh = now + 1_000_000_000L;
            }
            if (this.vulkanmod$stagingLine != null) {
                int x = this.minecraft.getWindow().getGuiScaledWidth() - this.minecraft.font.width(this.vulkanmod$stagingLine) - 6;
                guiGraphics.drawString(this.minecraft.font, this.vulkanmod$stagingLine, x, 6, 0xFFFFFF);
            }
            if (this.vulkanmod$benchmarkLine != null) {
                int x = this.minecraft.getWindow().getGuiScaledWidth() - this.minecraft.font.width(this.vulkanmod$benchmarkLine) - 6;
                guiGraphics.drawString(this.minecraft.font, this.vulkanmod$benchmarkLine, x, 18, 0xFFFFFF);
            }
        }
    }
}
