# VulkanMod performance profiling

This document describes the opt-in performance/critical-path logger used for Phase 5 measurement and later optimization work.

## Goal

The logger answers a narrower question than an FPS counter: **where did `Minecraft.runTick()` wall time go, and what renderer/terrain queues were doing during the same measurement window?**

It is deliberately disabled by default. The enabled hot path uses fixed primitive buffers and `System.nanoTime()`; percentile sorting, string formatting, JVM telemetry, terrain debug snapshots, and file I/O happen only at summary boundaries.

This is a **CPU wall-clock profiler**. It does not yet use Vulkan timestamp queries and therefore does not claim to measure GPU execution time. Captures should determine which CPU/Vulkan boundary deserves deeper instrumentation next.

## Enable it

Add this JVM argument to the Vulkan run:

```text
-Dvulkanmod.performanceProfiler=true
```

By default profiling writes to a dedicated file under the Minecraft game directory:

```text
logs/vulkanmod-performance.log
```

The ordinary Forge/Minecraft console receives only a one-time notice that profiling is enabled and where the file is being written. Summary data does not go through the normal game logger. If the profiling file cannot be opened or later fails to write, profiling disables itself and reports the failure through the normal logger rather than risking gameplay stability.

Optional controls:

```text
-Dvulkanmod.performanceProfiler.summarySeconds=5
-Dvulkanmod.performanceProfiler.durationSeconds=60
-Dvulkanmod.performanceProfiler.output=logs/vulkanmod-performance-run-a.log
-Dvulkanmod.performanceProfiler.slowFrameMs=25
-Dvulkanmod.performanceProfiler.maxSamples=4096
```

`summarySeconds` controls how often percentile/critical-path windows are flushed. The default is 5 seconds and may be set from 0.25 to 300 seconds.

`durationSeconds` controls total capture duration. `0` (the default) means unlimited. A positive value emits a final partial window when needed, writes `capture_complete`, closes the file, and makes the profiler inactive after that many seconds.

`output` may be relative to the Minecraft game directory or an absolute path. The default file is truncated when a new profiling capture begins, so automation should use distinct names when multiple captures need to be preserved.

No profiling flag is required for normal gameplay or benchmark control runs where instrumentation itself should be absent.

## Window output

Each summary window reports:

- frame wall time: average, p50, p95, p99, maximum, and slow-frame count;
- average, p95, and maximum time for known render-thread stages;
- the worst frame in the window and its largest known stage;
- an explicit `unaccounted` bucket rather than pretending stage coverage is complete;
- GC count/time deltas and current Java heap use;
- the existing VulkanMod terrain/task-dispatch debug line, including build queue/build/handoff/publication and region batching/allocation counters.

The default summary period is five seconds. This keeps capture volume low enough for diagnostic runs while still exposing transient changes during traversal or Create-heavy scenes.

## Known stages

The profiler has broad, non-overlapping `runTick()` phases plus narrower nested terrain detail. Nested stages are reported but are **not** subtracted again when computing `unaccounted`.

| Stage | Accounting | Meaning |
| --- | --- | --- |
| `frame_slot_wait` | top-level | `Renderer.resetBuffers()`, principally waiting before frame-slot resources can be recycled. |
| `frame_fence_wait` | top-level | Existing `Renderer.beginFrame()` fence/recreation section. Swapchain recreation, when triggered, is included here. |
| `frame_ops` | top-level | Per-frame descriptor/command-buffer/upload-manager bookkeeping after image acquisition. |
| `client_tick` | top-level | The vanilla `Minecraft.tick()` call and synchronous client/game logic performed there. |
| `game_render` | top-level | The complete `GameRenderer.render(...)` call. This is the broad CPU rendering bucket for world, entities, GUI, terrain orchestration, and mod render callbacks. |
| `terrain_setup` | nested in `game_render` | VulkanMod terrain camera setup, frustum/visibility traversal, and rebuild scheduling. Multiple nested-world calls accumulate into the same frame. |
| `terrain_reposition` | nested in `terrain_setup` | Camera-region reposition work. |
| `terrain_uploads` | nested in `game_render` | Render-thread publication of completed terrain builds and the normal terrain upload flush. |
| `submit_render` | top-level | Final pending area uploads plus VulkanMod `endFrame()`, queue submission, and presentation path reached by the existing submit hook. |
| `display_update` | top-level | Vanilla `Window.updateDisplay()` processing after Vulkan submission. |
| `frame_limit` | top-level | Time inside `RenderSystem.limitDisplayFPS(...)` when vanilla deliberately rate-limits the client. |
| `unaccounted` | remainder | The part of `Minecraft.runTick()` outside the top-level stages above. |

The broad-phase split was added after the first RX 6900 XT diagnostic capture showed that ordinary slow frames were dominated by the old `unaccounted` bucket while terrain setup/uploads, frame waits, and submit/present were individually cheap. The new split is intended to determine whether the remaining CPU time is principally `game_render`, `client_tick`, window/display work, or deliberate FPS limiting before adding still finer instrumentation.

## Worker-side interpretation

`WorldRenderer.getChunkStatistics()` is emitted alongside each timing window. Its task dispatcher already tracks concepts that must not be conflated:

- queued-before-build time;
- worker build time;
- queued-before-publication time;
- publication work on the render thread;
- total build-to-publication handoff latency;
- active/idle workers and queued work;
- accepted/dropped results and publication backpressure.

A long chunk build is therefore distinguishable from a cheap build that waited in a queue, and both are distinguishable from a render-thread publication stall.

These worker statistics are cumulative/aggregate diagnostics rather than percentile-window samples. Add deeper worker-window instrumentation only if captures show it is needed.

## Using it for optimization

For a diagnostic Vulkan run, use the same fixed Phase 5 world/settings/route and enable this logger. Preserve the dedicated profiler file with the build/commit being tested.

When comparing an optimization:

1. keep the benchmark workload and profiler settings identical;
2. compare p95/p99/max frame time, not only average FPS;
3. compare the targeted stage and relevant terrain/task queue statistics;
4. check whether time merely moved into another stage or into `unaccounted`;
5. keep correctness and memory-safety gates independent of the timing result.

The profiler makes a hypothesis testable; it does not by itself prove that a code change is a performance win.

For formal Phase 5 baseline numbers, follow `docs/TERRAIN_PERFORMANCE_BASELINE.md`. A diagnostic capture that deviates from that fixed contract is still useful for finding bottlenecks, but it is not an apples-to-apples baseline result.

## Future expansion rule

Add instrumentation where captured evidence points. Likely later layers include Vulkan timestamp-query rings for GPU passes, finer image-acquire/submit/present separation, or windowed worker-build percentiles. Do not blanket-instrument every draw or allocation unless a coarser capture demonstrates that the extra detail is necessary.
