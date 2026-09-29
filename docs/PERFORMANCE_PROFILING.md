# VulkanMod performance profiling

This document describes the opt-in performance/critical-path logger used for Phase 5 measurement and later optimization work.

## Goal

The logger answers a narrower and more useful question than an FPS counter: **where did render-thread wall time go, and what renderer/terrain queues were doing during the same measurement window?**

It is deliberately disabled by default. The enabled hot path uses fixed primitive buffers and `System.nanoTime()`; percentile sorting, string formatting, JVM telemetry, terrain debug snapshots, and file I/O happen only at summary boundaries.

This first version is a **CPU wall-clock profiler**. It does not yet use Vulkan timestamp queries and therefore does not claim to measure GPU execution time. The results should be used to identify which CPU/Vulkan boundary deserves deeper instrumentation next.

## Enable it

Add this JVM argument to the Vulkan run:

```text
-Dvulkanmod.performanceProfiler=true
```

By default profiling writes to a dedicated file under the Minecraft game directory:

```text
logs/vulkanmod-performance.log
```

The normal Forge/Minecraft console receives only a one-time notice that profiling is enabled and where the file is being written. Summary data does not go through the ordinary game logger. If the profiling file cannot be opened or later fails to write, profiling disables itself and reports that failure through the normal logger rather than risking gameplay stability.

Optional controls:

```text
-Dvulkanmod.performanceProfiler.summarySeconds=5
-Dvulkanmod.performanceProfiler.durationSeconds=60
-Dvulkanmod.performanceProfiler.output=logs/vulkanmod-performance-run-a.log
-Dvulkanmod.performanceProfiler.slowFrameMs=25
-Dvulkanmod.performanceProfiler.maxSamples=4096
```

`summarySeconds` controls how often percentile/critical-path windows are flushed. The default is 5 seconds and may be set from 0.25 to 300 seconds.

`durationSeconds` controls total capture duration. `0` (the default) means unlimited and profiling continues until the process ends. A positive value causes the profiler to emit a final partial window when needed, write `capture_complete`, close its file, and become inactive after that many seconds.

`output` may be relative to the Minecraft game directory or an absolute path. The default file is truncated when a new profiling capture begins, so benchmark automation should use distinct output names when multiple captures need to be preserved.

No profiling flag is required for normal gameplay and benchmark control runs where instrumentation itself should be absent.

## Window output

Each summary window reports:

- frame wall time: average, p50, p95, p99, maximum, and slow-frame count;
- average, p95, and maximum time for known render-thread stages;
- the worst frame in the window and its largest known stage;
- an explicit `unaccounted` bucket instead of pretending the initial stage map covers the whole frame;
- GC count/time deltas and current Java heap use;
- the existing VulkanMod terrain/task-dispatch debug line, including build queue/build/handoff/publication and region batching/allocation counters.

The default summary period is five seconds. This keeps capture volume low enough for normal diagnostic runs while still exposing transient changes during traversal or Create-heavy scenes.

## Known stages

| Stage | Meaning |
| --- | --- |
| `frame_slot_wait` | Time spent in `Renderer.resetBuffers()`, principally waiting before frame-slot resources can be recycled. |
| `frame_fence_wait` | Existing `Renderer.beginFrame()` fence/recreation section. Swapchain recreation, when triggered, is included here. |
| `frame_ops` | Per-frame descriptor/command-buffer/upload-manager bookkeeping after image acquisition. |
| `terrain_setup` | VulkanMod terrain camera setup, frustum/visibility traversal, and rebuild scheduling. Multiple nested-world calls accumulate into the same frame. |
| `terrain_reposition` | Camera-region reposition work. This is nested inside `terrain_setup`, so it is reported but not double-counted as top-level accounted frame time. |
| `terrain_uploads` | Render-thread publication of completed terrain builds and the normal terrain upload flush. |
| `submit_render` | Final pending area uploads plus VulkanMod `endFrame()`, queue submission, and presentation path reached by the existing submit hook. |
| `unaccounted` | The remainder of `Minecraft.runTick()`: game logic, entities, GUI, image acquisition and other work not yet assigned to a known stage. |

`unaccounted` is intentionally useful. If it dominates p95/worst frames, the next profiling patch should split the relevant remaining boundary instead of micro-optimizing a stage already shown to be cheap.

## Worker-side interpretation

`WorldRenderer.getChunkStatistics()` is emitted alongside each timing window. Its task dispatcher already tracks separate concepts that must not be conflated:

- queued-before-build time;
- worker build time;
- queued-before-publication time;
- publication work on the render thread;
- total build-to-publication handoff latency;
- active/idle workers and queued work;
- accepted/dropped results and publication backpressure.

A long chunk build is therefore distinguishable from a cheap build that waited in a queue, and both are distinguishable from a render-thread publication stall.

These existing worker statistics are currently cumulative/aggregate diagnostics rather than percentile-window samples. Add deeper worker-window instrumentation only if the first captures show it is needed.

## Using it for optimization

For a diagnostic Vulkan run, use the same fixed Phase 5 world/settings/route and enable this logger. Preserve the dedicated profiler file with the build/commit being tested.

When comparing an optimization:

1. keep the benchmark workload and profiler settings identical;
2. compare p95/p99/max frame time, not only average FPS;
3. compare the targeted stage and relevant terrain/task queue statistics;
4. check whether time merely moved into another stage or into `unaccounted`;
5. keep correctness and memory-safety gates independent of the timing result.

The profiler makes a hypothesis testable; it does not by itself prove that a code change is a performance win.

## Future expansion rule

Add instrumentation where captured evidence points. Likely later layers include Vulkan timestamp-query rings for GPU passes, finer image-acquire/submit/present separation, or windowed worker-build percentiles. Do not blanket-instrument every draw or allocation unless a coarser capture demonstrates that the extra detail is necessary.
