#!/usr/bin/env python3
"""Execute the real attribution helpers against a synthetic capture lifecycle."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path("net/vulkanmod/render/profiling")
profiler = (root / "src/main/java" / package / "PerformanceProfiler.java").read_text()
finish = profiler.split("public static boolean finishAutomatedCapture(String reason) {", 1)[1].split("public static void abortAutomatedCapture", 1)[0]
assert finish.index("return false;") < finish.index("TextureTickAttribution.emitSummary()")
assert finish.index("return false;") < finish.index("WorldRenderAttribution.emitSummary()")
assert finish.index("return false;") < finish.index("ParticleAttribution.emitSummary()")
assert "return active && frameActive;" in profiler
assert "return isFrameCapturing() && clientTickActive;" in profiler

stub = '''package net.vulkanmod.render.profiling;
import java.util.ArrayList;
public class PerformanceProfiler {
    static boolean frame, tick;
    static final ArrayList<String> lines = new ArrayList<>();
    public static boolean isFrameCapturing() { return frame; }
    public static boolean isClientTickCapturing() { return frame && tick; }
    public static void benchmarkEvent(String line) { lines.add(line); }
}
'''
harness = '''package net.vulkanmod.render.profiling;
public class AttributionCaptureContract {
    static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    static void textureTick() {
        long tick = TextureTickAttribution.beginTick();
        TextureTickAttribution.recordSpriteUploadRoute(true);
        TextureTickAttribution.recordSpriteUploadRoute(false);
        TextureTickAttribution.recordStandardInterpolationRoute(true);
        TextureTickAttribution.recordStandardInterpolationRoute(false);
        long phase = TextureTickAttribution.begin(TextureTickAttribution.Phase.BATCH_DRAIN);
        long copy = TextureTickAttribution.beginSpriteCopyFlush();
        TextureTickAttribution.endSpriteCopyFlush(copy, 3);
        TextureTickAttribution.end(TextureTickAttribution.Phase.BATCH_DRAIN, phase);
        TextureTickAttribution.endTick(tick);
    }
    static void world() {
        WorldRenderAttribution.beginWorldRender();
        WorldRenderAttribution.begin(WorldRenderAttribution.Category.FORGE_RENDER_STAGE);
        WorldRenderAttribution.beginWorldRender();
        for (int i = 0; i < 40; i++)
            WorldRenderAttribution.begin(WorldRenderAttribution.Category.ENTITY_RENDER);
        for (int i = 0; i < 40; i++)
            WorldRenderAttribution.end(WorldRenderAttribution.Category.ENTITY_RENDER);
        WorldRenderAttribution.endWorldRender();
        WorldRenderAttribution.end(WorldRenderAttribution.Category.FORGE_RENDER_STAGE);
        WorldRenderAttribution.endWorldRender();
    }
    static void emit() {
        TextureTickAttribution.emitSummary();
        WorldRenderAttribution.emitSummary();
    }
    public static void main(String[] args) {
        // Memory includes warmup allocations until their real retirement; work samples do not.
        TextureTickAttribution.residentAdmitted(3072);
        TextureTickAttribution.residentSourceAdmitted(2048);
        TextureTickAttribution.residentScratchAllocated(512);
        // Warmup work must produce neither captured counters nor a summary.
        textureTick(); world(); emit();
        check(PerformanceProfiler.lines.isEmpty(), "warmup contamination");
        PerformanceProfiler.frame = true;
        // A texture call outside the admitted client tick still must not count.
        textureTick(); emit();
        check(PerformanceProfiler.lines.isEmpty(), "unmeasured client tick contamination");
        PerformanceProfiler.tick = true;
        textureTick(); world();
        PerformanceProfiler.tick = false;
        PerformanceProfiler.frame = false;
        // Calls after the captured frame must not add samples.
        textureTick(); world(); emit();
        check(PerformanceProfiler.lines.size() == 2, "missing measured summaries");
        String texture = PerformanceProfiler.lines.get(0);
        check(texture.contains("ticks=1 "), texture);
        check(texture.contains("sprite_upload_requests=2 gpu_sprite_uploads=1 cpu_sprite_uploads=1 "), texture);
        check(texture.contains("standard_interpolation_updates=2 standard_interpolation_gpu_updates=1 standard_interpolation_cpu_updates=1 "), texture);
        check(texture.contains("resident_current_kib=3.000 resident_peak_kib=3.000 "), texture);
        check(texture.contains("resident_source_current_kib=2.000 resident_source_peak_kib=2.000 "), texture);
        check(texture.contains("resident_scratch_current_kib=0.500 resident_scratch_peak_kib=0.500 "), texture);
        check(texture.contains("copy_flushes=1 copy_regions=3 "), texture);
        check(texture.contains("outer_copy_flushes=1 outer_copy_regions=3 "), texture);
        String world = PerformanceProfiler.lines.get(1);
        check(world.contains("world_calls=1 "), world);
        check(world.contains("category_stack_errors=0 "), world);
        check(world.contains("nested_category_suppressions=40 "), world);
        check(world.contains("forge_render_stage_calls=1 "), world);
        check(world.contains("entity_render_calls=0 "), world);
        emit();
        check(PerformanceProfiler.lines.size() == 2, "duplicate summary after reset");
        TextureTickAttribution.residentReleased(3072);
        TextureTickAttribution.residentSourceReleased(2048);
        TextureTickAttribution.residentScratchReleased(512);
        PerformanceProfiler.frame = PerformanceProfiler.tick = true;
        textureTick(); world(); emit();
        check(PerformanceProfiler.lines.size() == 4, "second capture lost");
        check(PerformanceProfiler.lines.get(2).contains("ticks=1 "), "prior tick leaked");
        check(PerformanceProfiler.lines.get(2).contains("sprite_upload_requests=2 gpu_sprite_uploads=1 cpu_sprite_uploads=1 "), "prior upload route leaked");
        check(PerformanceProfiler.lines.get(2).contains("standard_interpolation_updates=2 standard_interpolation_gpu_updates=1 standard_interpolation_cpu_updates=1 "), "prior interpolation route leaked");
        check(PerformanceProfiler.lines.get(2).contains("resident_source_current_kib=0.000 resident_source_peak_kib=2.000 "), "source retirement/peak lost");
        check(PerformanceProfiler.lines.get(2).contains("resident_scratch_current_kib=0.000 resident_scratch_peak_kib=0.500 "), "scratch retirement/peak lost");
        check(PerformanceProfiler.lines.get(3).contains("world_calls=1 "), "prior world leaked");
        System.out.println("Attribution capture contract passed: warmup, parent scope, nested/overflow world, copy counters, reset");
    }
}
'''
with tempfile.TemporaryDirectory(prefix="vulkanmod-attribution-") as directory:
    destination = Path(directory)
    sources = destination / package
    sources.mkdir(parents=True)
    for name in ("TextureTickAttribution.java", "WorldRenderAttribution.java"):
        (sources / name).write_text((root / "src/main/java" / package / name).read_text())
    (sources / "PerformanceProfiler.java").write_text(stub)
    (sources / "AttributionCaptureContract.java").write_text(harness)
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home) / "bin/javac") if java_home else "javac"
    java = str(Path(java_home) / "bin/java") if java_home else "java"
    subprocess.run([javac, "--release", "17", "-d", directory, *map(str, sources.glob("*.java"))], check=True, timeout=30)
    subprocess.run([java, "-Dvulkanmod.performanceProfiler=true", "-Dvulkanmod.performanceProfiler.autoBenchmark=true", "-cp", directory, "net.vulkanmod.render.profiling.AttributionCaptureContract"], check=True, timeout=30)
