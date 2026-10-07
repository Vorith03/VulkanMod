package net.vulkanmod.render.profiling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;

import java.util.ArrayList;
import java.util.List;

/** ForgeGui overrides Gui.render, so diagnostics must be a registered Forge overlay. */
@Mod.EventBusSubscriber(modid = Initializer.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BenchmarkHud {
    private static long nextRefresh;
    private static int lastWidth;
    private static List<FormattedCharSequence> lines = List.of();

    private BenchmarkHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("profiler", BenchmarkHud::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.level == null || minecraft.options.hideGui) return;
        if (ProfilerOverlay.shouldRender && !minecraft.options.renderDebug && ProfilerOverlay.INSTANCE != null) {
            ProfilerOverlay.INSTANCE.render(graphics.pose());
        }
        if (!PerformanceProfiler.isEnabled() && !AutomatedBenchmark.enabled()) return;
        long now = System.nanoTime();
        if (now >= nextRefresh || width != lastWidth) {
            List<FormattedCharSequence> refreshed = new ArrayList<>();
            add(minecraft, refreshed, RegionVoxelStore.ENABLED ? RegionVoxelStore.overlayCount() : null, width);
            add(minecraft, refreshed, AutomatedBenchmark.statusLine(), width);
            add(minecraft, refreshed, AutomatedBenchmark.terrainStatusLine(), width);
            lines = refreshed;
            lastWidth = width;
            nextRefresh = now + 1_000_000_000L;
        }
        int y = 6;
        for (FormattedCharSequence line : lines) {
            int x = Math.max(6, width - minecraft.font.width(line) - 6);
            graphics.fill(x - 2, y - 1, width - 4, y + minecraft.font.lineHeight, 0xA0000000);
            graphics.drawString(minecraft.font, line, x, y, 0xFFFFFF);
            y += minecraft.font.lineHeight + 3;
        }
    }

    private static void add(Minecraft minecraft, List<FormattedCharSequence> lines, String text, int width) {
        if (text != null) lines.addAll(minecraft.font.split(Component.literal(text), Math.max(1, width - 12)));
    }
}
