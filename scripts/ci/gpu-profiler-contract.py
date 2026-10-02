#!/usr/bin/env python3
"""Static contract for Forge-safe coarse GPU HUD timestamp boundaries."""

from pathlib import Path


root = Path(__file__).resolve().parents[2]
client_renderer = (root / "src/main/java/net/vulkanmod/mixin/profiling/ClientRendererPerformanceMixin.java").read_text(encoding="utf-8")
gui_mixin = (root / "src/main/java/net/vulkanmod/mixin/profiling/GuiMixin.java").read_text(encoding="utf-8")

target = 'target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"'
assert client_renderer.count(target) == 2, "HUD timing must bracket the GameRenderer -> Gui.render call site"
assert client_renderer.count("GpuTimestampProfiler.Boundary.HUD_BEGIN") == 1, "expected one HUD_BEGIN boundary"
assert client_renderer.count("GpuTimestampProfiler.Boundary.HUD_END") == 1, "expected one HUD_END boundary"
assert "shift = At.Shift.AFTER" in client_renderer, "HUD_END must execute after Gui.render returns"
assert "GpuTimestampProfiler.Boundary.HUD_BEGIN" not in gui_mixin, "base Gui.render must not own GPU HUD timing"
assert "GpuTimestampProfiler.Boundary.HUD_END" not in gui_mixin, "base Gui.render must not own GPU HUD timing"

print("GPU timestamp Forge HUD boundary contract passed")
