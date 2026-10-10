#!/usr/bin/env python3
"""Exercise bounded particle attribution without Minecraft classes."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path("net/vulkanmod/render/profiling")

stub = r'''package net.vulkanmod.render.profiling;
import java.util.ArrayList;
public class PerformanceProfiler {
    static boolean frame, tick;
    static final ArrayList<String> lines = new ArrayList<>();
    public static boolean isFrameCapturing() { return frame; }
    public static boolean isClientTickCapturing() { return frame && tick; }
    public static void benchmarkEvent(String line) { lines.add(line); }
}
'''

harness = r'''package net.vulkanmod.render.profiling;
public class ParticleAttributionContract {
    static final class ParticleA {}
    static final class ParticleB {}
    static final class ProviderA {}
    static final class ProviderB {}

    static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    static void oneCapture() {
        PerformanceProfiler.frame = PerformanceProfiler.tick = true;
        ParticleAttribution.beginEngineTick();

        ParticleA a = new ParticleA();
        ParticleB b = new ParticleB();
        ProviderA providerA = new ProviderA();
        ProviderB providerB = new ProviderB();

        ParticleAttribution.recordCreated(a, "minecraft:test_a", providerA);
        ParticleAttribution.recordAdded(a, "particle_sheet_translucent");
        int aTick = ParticleAttribution.beginParticleTick(a);
        check(aTick > 0, "first A tick should be sampled");
        ParticleAttribution.endParticleTick(aTick, 8_000L, 1_024L, false);

        ParticleAttribution.recordCreated(b, "testmod:test_b", providerB);
        ParticleAttribution.recordAdded(b, "custom");
        int bTick = ParticleAttribution.beginParticleTick(b);
        check(bTick > 0, "first B tick should be sampled");
        ParticleAttribution.endParticleTick(bTick, 20_000L, 4_096L, true);

        // Exercise exact call counting without forcing every call through timing.
        for (int i = 0; i < 8; ++i) {
            int token = ParticleAttribution.beginParticleTick(a);
            ParticleAttribution.endParticleTick(token, token > 0 ? 10_000L : 0L,
                    token > 0 ? 2_048L : -1L, false);
        }

        ParticleAttribution.beginRenderPass();

        PerformanceProfiler.frame = PerformanceProfiler.tick = false;
        ParticleAttribution.emitSummary();
    }

    public static void main(String[] args) {
        // Warmup/out-of-capture calls must not contaminate the capture.
        ParticleAttribution.beginEngineTick();
        ParticleAttribution.recordAdded(new ParticleA(), "warmup");
        ParticleAttribution.emitSummary();
        check(PerformanceProfiler.lines.isEmpty(), "warmup contamination");

        oneCapture();
        check(PerformanceProfiler.lines.size() == 3, "expected summary plus two class lines");
        String summary = PerformanceProfiler.lines.get(0);
        check(summary.contains("engine_ticks=1 "), summary);
        check(summary.contains("render_passes=1 "), summary);
        check(summary.contains("render_cost_scope=aggregate_world_attribution"), summary);
        check(summary.contains("class_slots=2 "), summary);
        check(summary.contains("tick_calls=10 "), summary);
        check(summary.contains("additions=2 "), summary);
        check(summary.contains("creations=2 "), summary);
        check(summary.contains("removals=1 "), summary);
        check(summary.contains("overflow_tick_calls=0 "), summary);

        String classes = PerformanceProfiler.lines.get(1) + "\n" + PerformanceProfiler.lines.get(2);
        check(classes.contains("ParticleA"), classes);
        check(classes.contains("ParticleB"), classes);
        check(classes.contains("source=minecraft:test_a"), classes);
        check(classes.contains("source=testmod:test_b"), classes);
        check(classes.contains("ProviderA"), classes);
        check(classes.contains("ProviderB"), classes);
        check(classes.contains("render_type=particle_sheet_translucent"), classes);
        check(classes.contains("render_type=custom"), classes);

        ParticleAttribution.emitSummary();
        check(PerformanceProfiler.lines.size() == 3, "duplicate summary after reset");

        oneCapture();
        check(PerformanceProfiler.lines.size() == 6, "second capture lost or leaked state");
        check(PerformanceProfiler.lines.get(3).contains("engine_ticks=1 "), "prior capture leaked");
        System.out.println("Particle attribution contract passed: capture scope, bounded slots, sampled tick cost/allocation, churn, source/provider/render identity, aggregate render boundary, reset");
    }
}
'''

# Remove the per-particle injected HEAD/RETURN hooks from normal gameplay
# without disabling engine-level timing or automated benchmark attribution.
fine = (root / 'src/main/java/net/vulkanmod/mixin/profiling/ParticleEngineAttributionMixin.java').read_text()
coarse = (root / 'src/main/java/net/vulkanmod/mixin/profiling/ParticleEnginePerformanceMixin.java').read_text()
plugin = (root / 'src/main/java/net/vulkanmod/mixin/MixinPlugin.java').read_text()
mixins = (root / 'src/main/resources/vulkanmod.mixins.json').read_text()
assert fine.count('@Inject(') == 4
assert 'method = "tickParticle(' in fine and 'method = "makeParticle(' in fine
assert 'beginParticleTick' not in coarse and 'endParticleTick' not in coarse
assert 'ClientTickBreakdown.Stage.PARTICLES' in coarse
assert 'ParticleEngineAttributionMixin' in plugin and 'ParticleEngineAttributionMixin' in mixins
assert 'Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark")' in plugin

with tempfile.TemporaryDirectory(prefix="vulkanmod-particle-attribution-") as directory:
    destination = Path(directory)
    sources = destination / package
    sources.mkdir(parents=True)
    (sources / "ParticleAttribution.java").write_text(
        (root / "src/main/java" / package / "ParticleAttribution.java").read_text()
    )
    (sources / "PerformanceProfiler.java").write_text(stub)
    (sources / "ParticleAttributionContract.java").write_text(harness)
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home) / "bin/javac") if java_home else "javac"
    java = str(Path(java_home) / "bin/java") if java_home else "java"
    subprocess.run([javac, "--release", "17", "-d", directory, *map(str, sources.glob("*.java"))],
                   check=True, timeout=30)
    subprocess.run([
        java,
        "-Dvulkanmod.performanceProfiler=true",
        "-Dvulkanmod.performanceProfiler.autoBenchmark=true",
        "-cp", directory,
        "net.vulkanmod.render.profiling.ParticleAttributionContract"
    ], check=True, timeout=30)
