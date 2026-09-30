# Client-tick attribution follow-up — 2026-09-30

## Why this instrumentation exists

The completed RX 6900 XT automated capture from build #825 showed a recurring CPU-side tick hitch rather than a Vulkan submission/fence bottleneck:

- 2,669 / 2,670 frames over 25 ms contained a client tick;
- tick-bearing frames averaged about 28.49 ms versus 2.77 ms for render-only frames;
- `client_tick` averaged about 24.17 ms wall / 23.96 ms CPU;
- existing nested tick buckets explained only about 3.5 ms, leaving roughly 20.66 ms in `client_tick_other`;
- tick-bearing frames allocated about 3.77 MiB on the render thread on average.

The #825 evidence does not identify an individual mod and does not justify optimizing Vulkan submit/fence/upload paths or tuning GC first.

## Validated implementation

Executable commit `da556942d2695711fa80f0e6b031a46268f3dbad` adds automated-benchmark-only leaf attribution inside the already measured `Minecraft.tick()` parent.

CI #830 / run `36685436931` is fully green. It validates the distributable, both Forge startup modes, persistent GPU indirect commands, post-chain/depth-post-chain, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j. The three private-resource-pack steps were skipped because their private fixture configuration was unavailable, as expected.

Artifacts:

- `VulkanMod-Forge-build-830`, artifact ID `11082634645`, SHA-256 `c88cb38ff5cb4d32ade94a22eeb05538cce4cd60ae2c31281018fcdcf7aeebcb`;
- `VulkanMod-Forge-smoke-log-830`, artifact ID `11082859035`, SHA-256 `347cf5ed5362a8f852151a4d7cb3a3a3e83c5e868609afa2bc31ca892f090497`.

The leaf profiler is gated by both `vulkanmod.performanceProfiler=true` and `vulkanmod.performanceProfiler.autoBenchmark=true`. Normal gameplay and manual profiling do not allocate its sample buffers or collect these extra leaf metrics.

## New leaf buckets

The automated benchmark now times these additional client-tick leaves:

- Forge client tick pre/post event dispatch;
- Forge client-level tick pre/post event dispatch;
- GUI tick;
- `GameRenderer.pick`;
- multiplayer game-mode tick;
- texture-manager tick;
- tutorial tick;
- vanilla `LevelRenderer.tick`;
- `LevelRenderer.tickRain`;
- `ClientLevel.animateTick` ambient-world work;
- particle-engine tick;
- music-manager tick;
- sound-manager tick;
- keybind handling.

The existing broad `client_level_tick`, `client_entities_tick`, `client_renderer_tick`, and `client_connection_tick` metrics remain unchanged, so the next capture is comparable to #825 while providing more attribution.

The new leaf profiler also records render-thread allocated bytes for every leaf when the JVM exposes thread allocation counters. It emits aggregate average / p95 / max timing and allocation metrics only after the last measured benchmark frame, so output formatting and flushing do not contaminate the measured capture.

Expected end-of-capture lines include:

```text
[VulkanModPerf] benchmark client_tick_breakdown ...
[VulkanModPerf] benchmark client_tick_leaf_avg_ms ...
[VulkanModPerf] benchmark client_tick_leaf_p95_ms ...
[VulkanModPerf] benchmark client_tick_leaf_max_ms ...
[VulkanModPerf] benchmark client_tick_leaf_allocation_avg_kib ...
[VulkanModPerf] benchmark client_tick_leaf_allocation_p95_kib ...
[VulkanModPerf] benchmark client_tick_leaf_allocation_max_kib ...
```

## Next RX run

Run build #830 with the same automated benchmark world/view/settings as the successful #825 diagnostic. Keep the diagnostic workload unchanged so differences are attributable to instrumentation rather than workload drift. The required benchmark flags remain:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
```

The defaults still target world `VulkanMod Benchmark`, Overworld position `0 192 0`, yaw `-90`, pitch `30`, a 60-second settle, 180-second capture, and at least 60 seconds after a staging-cap observation. Keep Minecraft focused and do not resize during the measured capture.

Return the generated `logs/vulkanmod-performance-benchmark-*.log`. `latest.log` is needed only if automation aborts or another runtime problem appears.

This is still a diagnostic run, not the formal matched Phase 5 OpenGL/Vulkan baseline. The #825 capture used a 260 FPS cap and does not establish complete baseline provenance; preserve its settings for this attribution rerun rather than silently converting the experiment into a different benchmark.

## Decision tree after the capture

1. If `forge_client_pre`, `forge_client_post`, `forge_level_pre`, or `forge_level_post` dominates wall time or allocation, instrument only that Forge EventBus family down to listener/mod ownership. Do not blanket-instrument every event listener.
2. If `level_renderer` or `weather` dominates, inspect whether VulkanMod is still paying redundant vanilla renderer-maintenance cost and whether any of it can be bypassed without breaking Forge/mod semantics.
3. If `textures`, `particles`, `pick`, `game_mode`, `gui`, `ambient_world`, `music`, `sound`, or `keybinds` dominates, investigate that concrete owner/path before changing renderer architecture.
4. If one leaf has disproportionate allocation without comparable wall time, investigate object churn/allocating stacks there before changing GC configuration.
5. If all new leaves are small while `client_tick_other` remains around the #825 level, inspect the remaining direct Forge-patched `Minecraft.tick()` work and add one more bounded attribution layer. Sampling/JFR or EventBus listener attribution should be driven by that evidence.
6. Broad Vulkan GPU timestamp instrumentation remains deferred unless CPU-side tick/render accounting no longer explains frame pacing.

## Performance sequencing

Phase 5 remains 4/7 until matched OpenGL/Vulkan numeric baselines and hitch evidence are captured under the formal fixed benchmark contract. Phase 4 remains 7/8 with world re-entry/resource reload deferred at the user's request. Phase 7 GPU-terrain/hybrid work remains 6/11 and should not receive performance claims from this diagnostic alone.
