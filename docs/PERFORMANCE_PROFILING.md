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

- a monotonic window ID, epoch timestamps, elapsed time, world/menu/transition context, dimension, framebuffer ranges, graphics settings, and first/last player pose with changed-frame count;
- frame wall time: average, p50, p95, p99, maximum, and slow-frame count;
- separate tick-bearing and render-only frame distributions, tick-call count (including catch-up ticks), and tick-stage p95 computed **only over frames that ticked**;
- inter-frame loop-gap percentiles, which expose main-loop work or scheduling time between `runTick()` calls; the profiler excludes its own periodic output flush from this gap;
- average, p95, and maximum time for known render-thread stages;
- the worst frame in the window and its largest known stage;
- an explicit `unaccounted` bucket rather than pretending stage coverage is complete;
- GC count/time deltas, current Java heap use, and render-thread CPU/allocated-byte deltas when the JVM exposes them (`-1` means unavailable);
- aligned per-window terrain build/publication/scheduling deltas, queue depths, staging occupancy/rejections, and selected GPU fallback/recovery reasons;
- the existing VulkanMod terrain/task-dispatch debug line, including build queue/build/handoff/publication and region batching/allocation counters.

The default summary period is five seconds. This keeps capture volume low enough for diagnostic runs while still exposing transient changes during traversal or Create-heavy scenes.

The one-time `environment` line records JVM, OS, logical CPU count, Vulkan device name, and the relevant GPU-terrain JVM flags. Keep the build/artifact number alongside the file; the mod JAR does not currently embed the Git commit ID.

Windows split when the client level changes. A frame that enters or leaves a world is labeled `transition` and kept separate from stable `menu` and `world` frames. Use `context=world`, stable framebuffer ranges, and the player-pose line to select a stationary slice. The player pose is a movement check, not a full camera trace for recursive portal views. Epoch timestamps allow correlation with `latest.log`; elapsed time remains monotonic if the system clock changes. A terrain delta of `-1` means no comparable prior window or a counter reset, as indicated by `delta_valid`/`counters_reset`. `terrain_window` samples asynchronously changing worker counters at the window boundary, so it is a rate indicator rather than an exact per-frame attribution.

The ordinary `stage_p95_ms` line still includes zero values from frames that did not execute a given stage. Use `tick_stage_p95_ms` to interpret client-tick cost at high FPS. `tick_stage_avg_ms` likewise divides by tick-bearing frames, while `stage_avg_ms` divides by all frames. Tick-detail stages may nest; `client_tick_other` subtracts the **union** of their measured intervals to avoid double-counting. The `accounting_overlap_frames` line flags nested tick detail, impossible top-level/world sums, or an unbalanced tick probe. Never add nested stage timings to the top-level stage totals.

`loop_gap` measures time from the end of profiler bookkeeping for one `runTick()` call to the start of the next. It can include main-loop tasks or OS scheduling and is separate from `frame_ms`; it is not a GPU timestamp. The first captured frame has no preceding gap. Per-frame profiling work and summary output are excluded so the profiler does not create a periodic false gap.

`capture_start initial_framebuffer_px` reports only the size at profiler startup; Prism's launcher window may resize later. Each `window` line therefore reports the measured frames' first/last framebuffer dimensions, width/height ranges, and number of observed dimension changes. The profiler samples both boundaries of every frame. Use a window with matching first/last dimensions, constant ranges, and zero changes for resolution-sensitive comparisons. An older capture that records only the initial size does not establish its in-world resolution.

With profiling enabled and GPU terrain staging active, the ordinary in-world HUD shows `VulkanMod staging: N/2048` at the top right, refreshed once per second. The count is the number of section snapshots resident in the bounded CPU voxel store; the log has the full byte, rejection, and build counters. No F3 screen or profiler keyboard shortcut is needed for a stationary capture. If the counter reaches 2048, remain stationary for at least another 60 seconds. If it never reaches 2048, keep the run for analysis rather than waiting indefinitely.

## Known stages

The profiler has broad, non-overlapping `runTick()` phases plus narrower nested terrain detail. Nested stages are reported but are **not** subtracted again when computing `unaccounted`.

| Stage | Accounting | Meaning |
| --- | --- | --- |
| `frame_slot_wait` | top-level | `Renderer.resetBuffers()`, principally waiting before frame-slot resources can be recycled. |
| `frame_fence_wait` | top-level | Existing `Renderer.beginFrame()` fence/recreation section. Swapchain recreation, when triggered, is included here. |
| `image_acquire` | top-level | CPU wait inside `vkAcquireNextImageKHR`; this previously fell into `unaccounted`. |
| `frame_ops` | top-level | Per-frame descriptor/command-buffer/upload-manager bookkeeping after image acquisition. |
| `client_tick` | top-level | The vanilla `Minecraft.tick()` call and synchronous client/game logic performed there. |
| `client_level_tick`, `client_entities_tick`, `client_renderer_tick`, `client_connection_tick` | nested in `client_tick` | Client world, entities, renderer tick (not render), and packet-listener tick calls. `client_tick_other` is the remaining average, including GUI/mod callbacks/tasks; these four nested stages do not exhaust the tick automatically. |
| `game_render` | top-level | The complete `GameRenderer.render(...)` call. This is the broad CPU rendering bucket for world, entities, GUI, terrain orchestration, and mod render callbacks. |
| `world_render` | nested in `game_render` | Outermost `GameRenderer.renderLevel(...)`, including any recursive portal worlds; `game_render_other` is the remainder. |
| `hud_render` | nested in `game_render` | Vanilla `Gui.render(...)`, including the opt-in staging readout. It is excluded from `game_render_other`, which still contains screens, post-processing, and other callbacks. |
| `terrain_setup` | nested in `game_render` | VulkanMod terrain camera setup, frustum/visibility traversal, and rebuild scheduling. Multiple nested-world calls accumulate into the same frame. |
| `terrain_reposition` | nested in `terrain_setup` | Camera-region reposition work. |
| `terrain_uploads` | nested in `game_render` | Render-thread publication of completed terrain builds and the normal terrain upload flush. |
| `terrain_draw` | nested in `world_render` | VulkanMod's terrain-layer render calls across all layers, excluding Forge's render-stage callback after each call. CPU time recording draws, not GPU execution. |
| `block_entity_render` | nested in `world_render` | VulkanMod's section block-entity rendering loop. Other entity and mod render work remains in `world_render_other`. |
| `submit_render` | top-level | Final pending area uploads plus VulkanMod `endFrame()`, queue submission, and presentation path reached by the existing submit hook. |
| `queue_submit`, `present` | nested in `submit_render` | CPU time inside the Vulkan queue-submit and present API calls. These are not GPU-duration measurements. |
| `display_update` | top-level | Vanilla `Window.updateDisplay()` processing after Vulkan submission. |
| `frame_limit` | top-level | Time inside `RenderSystem.limitDisplayFPS(...)` when vanilla deliberately rate-limits the client. |
| `unaccounted` | remainder | The part of `Minecraft.runTick()` outside the top-level stages above. |

`world_render_other` is `world_render` minus terrain setup, uploads, draw, and VulkanMod's block-entity loop; it still includes vanilla entities, particles, weather, Forge callbacks, and other world passes. These derived *averages* are residuals, not independently sampled p95s. `terrain_window` includes cumulative `dirty` notices (direct `setSectionDirty` calls), `scheduled` build requests, and their window deltas. Recovery builds can mark a section dirty through a different path, so compare these with the explicit preflight/full, publication-rejection, and CPU-recovery counters. The worker queue/build/handoff averages in the old terrain debug line remain cumulative.

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

Add instrumentation where captured evidence points. This profiler still cannot say how long Vulkan commands take **on the GPU** or distinguish individual mod callbacks within the residual tick/world stages. If CPU render cost and wait stages remain small while frame time or GPU use is high, the next layer is a validated, asynchronous Vulkan timestamp-query ring for a few broad passes, with query lifetime tied to the existing frame fences. Worker-window build percentiles or targeted mod callbacks should be added only when the conditional and terrain-delta evidence points there. Do not blanket-instrument every draw or allocation.
