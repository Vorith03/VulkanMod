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

## Optional unattended stationary run

The ordinary profiler still starts at client startup and never moves the player. For the **named single-player benchmark world only**, the following additional JVM argument enables an automated stationary run:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
```

The target display name defaults to `VulkanMod Benchmark` in the Overworld. After that world and its player are available, VulkanMod asks the integrated server to switch the player to Spectator and execute the fixed benchmark teleport (`0 192 0`, yaw `-90`, pitch `30`). It waits for the client to receive that pose and for terrain to become visible, settles for 60 seconds, then starts a **new profiler capture**. The default measured duration is 180 seconds. If the 2048-entry voxel staging cap is observed during settling or capture, it also keeps measuring for at least 60 seconds after the first cap observation. When the deadline is reached, VulkanMod flushes and closes the capture, calls Minecraft's normal single-player disconnect/save path, then closes the client after the integrated server is gone. The on-screen HUD shows the phase and remaining time; no F3 or manual `/tp` is needed.

Optional JVM arguments:

```text
-Dvulkanmod.performanceProfiler.benchmarkWorld=VulkanMod Benchmark
-Dvulkanmod.performanceProfiler.benchmarkX=0
-Dvulkanmod.performanceProfiler.benchmarkY=192
-Dvulkanmod.performanceProfiler.benchmarkZ=0
-Dvulkanmod.performanceProfiler.benchmarkYaw=-90
-Dvulkanmod.performanceProfiler.benchmarkPitch=30
-Dvulkanmod.performanceProfiler.benchmarkSettleSeconds=60
-Dvulkanmod.performanceProfiler.durationSeconds=180
-Dvulkanmod.performanceProfiler.benchmarkAfterStagingCapSeconds=60
```

Use one JVM argument per line in Prism; quote/escape a custom world name with spaces according to Prism's argument editor. The target name must match the world's display name exactly; other worlds and multiplayer sessions are untouched. Coordinates and duration can be configured for a separate, clearly labeled workload. Automation is **off by default** even when normal profiling is enabled, because it changes the target world's player position and game mode and eventually exits Minecraft. If the teleport fails, terrain does not appear, the player/camera moves, a screen opens, or the target world changes, the automatic run stops without closing the game and records the reason in `latest.log`. An interrupted measured run is flushed with `reason=interrupted`; discard it as a benchmark.

The file begins at the end of the settle period, so `capture_start initial_framebuffer_px` belongs to the measured world rather than launcher startup. Its `benchmark_config` and `benchmark complete` lines preserve the requested pose and actual duration. Automated `durationSeconds` is measured from capture start; in normal profiler mode, `durationSeconds=0` still means unlimited. Use a copied/pristine world for formal A/B runs, because saving persists the player teleport and Spectator mode. This is the stationary Vulkan case; it does not automate the OpenGL control or traversal route.

## Window output

Each summary window reports:

- a monotonic window ID, epoch timestamps, elapsed time, world/menu/transition context, dimension, framebuffer ranges, graphics settings, player-present count, screen-present count and first/last screen type, and first/last player pose with changed-frame count;
- frame wall time: average, p50, p95, p99, maximum, and slow-frame count;
- separate tick-bearing and render-only frame distributions, tick-call count (including catch-up ticks), and tick-stage p95 computed **only over frames that ticked**;
- CPU time and allocated bytes measured around `Minecraft.tick()` for tick-bearing frames when the JVM exposes these counters;
- up to six evenly spaced tick-frame examples and six slow render-only examples per window, with epoch time and a joint stage breakdown;
- inter-frame loop-gap percentiles, which expose main-loop work or scheduling time between `runTick()` calls; the profiler excludes its own periodic output flush from this gap;
- the cost of each profiler summary, which can itself interrupt actual frame cadence even though it is excluded from measured frame time and loop gap;
- average, p95, and maximum time for known render-thread stages;
- the worst frame in the window and its largest known stage;
- an explicit `unaccounted` bucket rather than pretending stage coverage is complete;
- GC count/time deltas, current Java heap use, and render-thread CPU/allocated-byte deltas when the JVM exposes them (`-1` means unavailable);
- aligned per-window terrain build/publication/scheduling deltas, queue depths, staging occupancy/rejections, and selected GPU fallback/recovery reasons;
- the existing VulkanMod terrain/task-dispatch debug line, including build queue/build/handoff/publication and region batching/allocation counters.

The default summary period is five seconds. This keeps capture volume low enough for diagnostic runs while still exposing transient changes during traversal or Create-heavy scenes.

The one-time `environment` line records JVM, OS, logical CPU count, Vulkan device name, and the relevant GPU-terrain JVM flags. Keep the build/artifact number alongside the file; the mod JAR does not currently embed the Git commit ID.

CI's normal software-Vulkan startup smoke enables the profiler at a short summary interval and checks the output schema and frame-class counts. This validates menu/startup logging and mixin application; only the user's RX run exercises the real world, terrain, and modpack workload.

Windows split when the client level changes. A frame that enters or leaves a world is labeled `transition` and kept separate from stable `menu` and `world` frames. Use `context=world`, `player_frames=frames`, `screen_frames=0`, stable framebuffer ranges, and the player-pose line to select a stationary slice. A world level can exist while loading screens remain open, so `context=world` alone is insufficient. The player pose is a movement check, not a full camera trace for recursive portal views. Epoch timestamps allow correlation with `latest.log`; elapsed time remains monotonic if the system clock changes. A terrain delta of `-1` means no comparable prior window or a counter reset, as indicated by `delta_valid`/`counters_reset`. `terrain_window` samples asynchronously changing worker counters at the window boundary, so it is a rate indicator rather than an exact per-frame attribution.

The ordinary `stage_p95_ms` line still includes zero values from frames that did not execute a given stage. Use `tick_stage_p95_ms` to interpret client-tick cost at high FPS. `tick_stage_avg_ms` likewise divides by tick-bearing frames, while `stage_avg_ms` divides by all frames. Tick-detail stages may nest; `client_tick_other` subtracts the **union** of their measured intervals to avoid double-counting. `tick_resources` reports CPU time and allocation *per tick-bearing frame* (multiple catch-up tick calls are accumulated); sample counts indicate availability, with `-1` for unavailable metrics. If tick wall time is much larger than tick CPU time, investigate waiting or global pauses; allocation spikes strengthen, but do not prove, a GC-pressure hypothesis. The render-thread JVM line covers the full window and must not be treated as tick-local. The `accounting_overlap_frames` line flags nested tick detail, children exceeding their parent, impossible top-level/world sums, or unbalanced tick probes. Its `top_level_excess_ms` sums the time by which top-level stages exceeded whole frames, while `top_level_max_excess_ms` identifies the worst single frame. `unaccounted` is now the **per-frame positive remainder** averaged across the window, with p95 and max also reported. Excess time is reported separately, so overlapping frames cannot cancel the gaps of other frames. Never add nested stage timings to the top-level stage totals.

`frame_example class=tick` samples evenly across **all** tick-bearing frames in the window, not just outliers; `class=slow_render` samples render-only frames over the configured slow threshold. Each line preserves joint timing, the preceding gap, tick CPU/allocation if available, and a frame-end epoch timestamp for comparison with `latest.log`. These are at most six observations per class, not a full trace or a statistically representative percentile. The separate `worst` line catches the isolated maximum. `profiler_overhead summary_ms` measures the summary's formatting, snapshot, and first flush; it excludes its own line and final flush. This pause occurs in the real client loop but is deliberately excluded from `frame_ms` and `loop_gap`, so do not misattribute a recurring five-second capture artifact to gameplay.

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
| `display_update` | top-level | VulkanMod's `Window.updateDisplay()` processing after Vulkan submission, timed inside the method overwrite. This includes `RenderSystem.flipFrame` and a pending fullscreen update. |
| `frame_limit` | top-level | Time inside `RenderSystem.limitDisplayFPS(...)` when vanilla deliberately rate-limits the client. |
| `unaccounted` | remainder | The part of `Minecraft.runTick()` outside the top-level stages above. |

`world_render_other` is `world_render` minus terrain setup, uploads, draw, and VulkanMod's block-entity loop; it still includes vanilla entities, particles, weather, Forge callbacks, and other world passes. These derived *averages* are residuals, not independently sampled p95s. `terrain_window` includes cumulative `dirty` notices (direct `setSectionDirty` calls), `scheduled` build requests, and their window deltas. Recovery builds can mark a section dirty through a different path, so compare these with the explicit preflight/full, publication-rejection, and CPU-recovery counters. The worker queue/build/handoff averages in the old terrain debug line remain cumulative.

The broad-phase split was added after the first RX 6900 XT diagnostic capture showed that ordinary slow frames were dominated by the old `unaccounted` bucket while terrain setup/uploads, frame waits, and submit/present were individually cheap. The new split is intended to determine whether the remaining CPU time is principally `game_render`, `client_tick`, window/display work, or deliberate FPS limiting before adding still finer instrumentation.

The #815 startup smoke capture exposed a systematic timing error in the earlier broad split: an injector before the `Window.updateDisplay()` call enclosed another mixin's `submitRender` injector at the same call site. In ordinary menu windows, `display_update` and `submit_render` were each about 3 ms and every frame reported top-level overlap. Timing the actual window method fixes the boundary independent of mixin ordering; CI now checks a real startup capture for substantial accounting overlap. Older captures with this hook can still establish tick cadence and terrain counter behavior, but do not add their `display_update` and `submit_render` values or treat their window-average, zero-clamped `unaccounted=0` as proof of complete coverage.

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

The adversarial review before the next RX capture identified these remaining limitations and decision rules:

| Missing discriminator | Why it is not in every capture yet | Add it when |
| --- | --- | --- |
| Exact GC pause overlapping a specific frame | Window-level GC deltas and tick-local CPU/allocation distinguish sustained CPU work from plausible pauses, but cannot prove a particular frame was a GC pause. Collector notifications or a bounded JFR recording would add another clock and collection-specific behavior. | Isolated wall-time spikes remain ambiguous after comparing tick CPU time, allocations, and GC deltas. |
| Individual mod callback time | Forge/mod callback boundaries are numerous, and timing each callback can change the workload. `client_tick_other`, world residuals, and joint examples first identify the responsible broad path. | A sustained residual dominates normal tick or render frames; instrument that specific event family or use a sampling profile. |
| Worker latency percentiles per window | Existing per-window build/publish deltas and queue depths show churn and backlog; the debug-line latency averages are lifetime totals and cannot localize a new regression to one five-second interval. | Build/publish churn remains high without a clear saturation/recovery reason, or the queue grows while render-thread stages remain cheap. |
| GPU execution per pass and actual display intervals | CPU API calls and `runTick()` are not GPU timestamps or presentation timestamps. Vulkan query lifetime and synchronization need a separate validated design. | CPU frame/loop costs are insufficient to explain the observed frame pacing while the GPU is busy. |

The new `frame_example` lines are deliberately bounded and should not be read as a full event trace. The `window` graphics settings are sampled at summary time; a setting changed mid-window still requires selecting later stable windows for comparison.
