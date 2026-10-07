#!/usr/bin/env python3
"""Exercise the production overlay through ForgeGui's registered callback, not Gui.render."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = 'net/vulkanmod/render/profiling/'
sources = {
    package + 'BenchmarkHud.java': (root / 'src/main/java' / package / 'BenchmarkHud.java').read_text(),
    'net/minecraftforge/api/distmarker/Dist.java': 'package net.minecraftforge.api.distmarker; public enum Dist { CLIENT }',
    'net/minecraftforge/eventbus/api/SubscribeEvent.java': 'package net.minecraftforge.eventbus.api; public @interface SubscribeEvent {}',
    'net/minecraftforge/fml/common/Mod.java': '''package net.minecraftforge.fml.common;
public @interface Mod { public @interface EventBusSubscriber {
String modid(); net.minecraftforge.api.distmarker.Dist[] value(); Bus bus(); enum Bus { MOD }
}}''',
    'net/minecraftforge/client/gui/overlay/ForgeGui.java': '''package net.minecraftforge.client.gui.overlay;
public class ForgeGui {
public net.minecraft.client.Minecraft getMinecraft() { return net.minecraft.client.Minecraft.INSTANCE; }
}''',
    'net/minecraftforge/client/event/RegisterGuiOverlaysEvent.java': '''package net.minecraftforge.client.event;
public class RegisterGuiOverlaysEvent {
public Callback callback; public String id;
public void registerAboveAll(String id, Callback callback) { this.id=id; this.callback=callback; }
public interface Callback { void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
net.minecraft.client.gui.GuiGraphics graphics, float tick, int width, int height); }
}''',
    'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
public class Minecraft {
public static final Minecraft INSTANCE = new Minecraft();
public Object level = new Object(); public final Options options = new Options();
public final net.minecraft.client.gui.Font font = new net.minecraft.client.gui.Font();
public static class Options { public boolean hideGui, renderDebug; }
}''',
    'net/minecraft/util/FormattedCharSequence.java': 'package net.minecraft.util; public record FormattedCharSequence(String text) {}',
    'net/minecraft/network/chat/Component.java': '''package net.minecraft.network.chat;
public record Component(String text) { public static Component literal(String text) { return new Component(text); } }''',
    'net/minecraft/client/gui/Font.java': '''package net.minecraft.client.gui;
public class Font { public int lineHeight=9, splitCalls;
public int width(net.minecraft.util.FormattedCharSequence text) { return text.text().length(); }
public java.util.List<net.minecraft.util.FormattedCharSequence> split(net.minecraft.network.chat.Component text, int width) {
splitCalls++; var result=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
for(int offset=0; offset<text.text().length(); offset+=width) result.add(new net.minecraft.util.FormattedCharSequence(
text.text().substring(offset, Math.min(offset+width, text.text().length())))); return result; }
}''',
    'net/minecraft/client/gui/GuiGraphics.java': '''package net.minecraft.client.gui;
public class GuiGraphics {
public java.util.List<String> lines = new java.util.ArrayList<>();
public Object pose() { return this; }
public void fill(int left, int top, int right, int bottom, int color) {}
public void drawString(Font font, net.minecraft.util.FormattedCharSequence line, int x, int y, int color) { lines.add(line.text()); }
}''',
    'net/vulkanmod/Initializer.java': 'package net.vulkanmod; public class Initializer { public static final String MOD_ID="vulkanmod"; }',
    'net/vulkanmod/render/chunk/voxel/RegionVoxelStore.java': '''package net.vulkanmod.render.chunk.voxel;
public class RegionVoxelStore { public static boolean ENABLED=true;
public static String overlayCount() { return "staging"; } }''',
    package + 'ProfilerOverlay.java': '''package net.vulkanmod.render.profiling;
public class ProfilerOverlay { public static boolean shouldRender; public static ProfilerOverlay INSTANCE;
public int calls; public void render(Object pose) { calls++; } }''',
    package + 'PerformanceProfiler.java': '''package net.vulkanmod.render.profiling;
public class PerformanceProfiler { public static boolean enabled;
public static boolean isEnabled() { return enabled; } }''',
    package + 'AutomatedBenchmark.java': '''package net.vulkanmod.render.profiling;
public class AutomatedBenchmark { public static boolean enabled=true; public static String phase="warming", terrain="active=2";
public static boolean enabled() { return enabled; }
public static String statusLine() { return phase; }
public static String terrainStatusLine() { return terrain; } }''',
    package + 'HudContract.java': '''package net.vulkanmod.render.profiling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
public class HudContract {
static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
public static void main(String[] args) throws Exception {
var event = new RegisterGuiOverlaysEvent(); BenchmarkHud.register(event);
check(event.id.equals("profiler") && event.callback != null, "Forge overlay missing");
var gui = new ForgeGui(); var graphics = new GuiGraphics();
// No Gui.render exists in this fixture: the override's registered callback must own drawing.
event.callback.render(gui, graphics, 0, 600, 400);
check(graphics.lines.equals(java.util.List.of("staging", "warming", "active=2")), "warmup diagnostics missing");
var refresh = BenchmarkHud.class.getDeclaredField("nextRefresh"); refresh.setAccessible(true);
refresh.setLong(null, 0); graphics.lines.clear();
AutomatedBenchmark.phase="stopped: terrain convergence timed out";
AutomatedBenchmark.terrain="active=0 high=0 low=0 backlog=0 scheduled_delta=1";
event.callback.render(gui, graphics, 0, 600, 400);
check(graphics.lines.contains(AutomatedBenchmark.phase) && graphics.lines.contains(AutomatedBenchmark.terrain), "abort diagnostics missing");
graphics.lines.clear(); var cachedSplits=Minecraft.INSTANCE.font.splitCalls;
event.callback.render(gui, graphics, 0, 600, 400);
check(Minecraft.INSTANCE.font.splitCalls==cachedSplits, "HUD allocated wrapped lines every frame");
graphics.lines.clear(); event.callback.render(gui, graphics, 0, 40, 400);
check(graphics.lines.size()>3 && graphics.lines.stream().allMatch(line -> line.length()<=28), "scaled width lost diagnostics");
Minecraft.INSTANCE.options.hideGui=true; graphics.lines.clear();
event.callback.render(gui, graphics, 0, 600, 400); check(graphics.lines.isEmpty(), "hidden HUD rendered");
Minecraft.INSTANCE.options.hideGui=false; Minecraft.INSTANCE.level=null;
event.callback.render(gui, graphics, 0, 600, 400); check(graphics.lines.isEmpty(), "menu HUD rendered");
Minecraft.INSTANCE.level=new Object(); AutomatedBenchmark.enabled=false;
event.callback.render(gui, graphics, 0, 600, 400); check(graphics.lines.isEmpty(), "disabled profiler rendered");
ProfilerOverlay.INSTANCE=new ProfilerOverlay(); ProfilerOverlay.shouldRender=true;
event.callback.render(gui, graphics, 0, 600, 400);
check(ProfilerOverlay.INSTANCE.calls==1, "keyboard profiler overlay bypassed by ForgeGui");
}
}''',
}
# Forge's automatic subscriber is the real lifecycle entry point; the callback test
# above proves it no longer depends on the bypassed vanilla implementation.
hud = sources[package + 'BenchmarkHud.java']
assert '@Mod.EventBusSubscriber(' in hud and 'bus = Mod.EventBusSubscriber.Bus.MOD' in hud
assert '@SubscribeEvent' in hud
assert '@Inject(method = "render"' not in (root / 'src/main/java/net/vulkanmod/mixin/profiling/GuiMixin.java').read_text()
with tempfile.TemporaryDirectory(prefix='vulkanmod-hud-') as folder:
    path = Path(folder)
    for name, source in sources.items():
        target = path / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '--release', '17', '-d', str(path / 'classes'), *[str(path / name) for name in sources]], check=True, timeout=30)
    subprocess.run(['java', '-cp', str(path / 'classes'), 'net.vulkanmod.render.profiling.HudContract'], check=True, timeout=30)
print('Forge benchmark HUD contract passed: registered callback, warmup, abort, counters, visibility, profiler toggle')
