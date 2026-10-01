# VulkanMod performance profiling

This document describes the opt-in performance/critical-path profiler used for Phase 5 measurement and later optimization work.

## Purpose

The profiler is designed to answer progressively narrower questions without blanket-instrumenting the renderer:

1. where did `Minecraft.runTick()` wall time go;
2. which client-tick leaf or renderer/terrain stage explains a recurring hitch;
3. what terrain queues, staging limits, GC/allocation behavior, or synchronization state coincided with it; and
4. when explicitly enabled, how much GPU execution time was spent inside VulkanMod's **main graphics command buffer** during the same automated benchmark interval.

Profiling is disabled by default. The CPU hot path uses fixed primitive buffers and `System.nanoTime()`; percentile sorting, formatting, JVM telemetry, terrain snapshots, and file I/O happen only at summary boundaries. GPU timing is also opt-in and uses frame-slot-owned Vulkan timestamp-query pools.

This is not a full tracing profiler. It deliberately starts broad and adds narrower probes only when measured evidence identifies a dominant residual.

## Enable CPU profiling

Add:

```text
-Dvulkanmod.performanceProfiler=true
```

By default output is written under the Minecraft game directory:

```text
logs/vulkanmod-performance.log
```

The ordinary Forge/Minecraft logger receives only a one-time notice naming the profiler file. If the file cannot be opened, written, flushed, or closed cleanly, profiling disables itself and reports the failure through the normal logger rather than risking gameplay stability or claiming a successful benchmark.

Optional controls:

```text
-Dvulkanmod.performanceProfiler.summarySeconds=5
-Dvulkanmod.performanceProfiler.durationSeconds=60
-Dvulkanmod.performanceProfiler.output=logs/vulkanmod-performance-run-a.log
-Dvulkanmod.performanceProfiler.slowFrameMs=25
-Dvulkanmod.performanceProfiler.maxSamples=4096
```

`summarySeconds` defaults to 5 seconds and may be set from 0.25 to 300 seconds. `durationSeconds=0` means unlimited in ordinary/manual profiling. `maxSamples` bounds the per-window primitive sample arrays.

Automated benchmark runs without an explicit output path create a unique file:

```text
logs/vulkanmod-performance-benchmark-<run UUID>.log
```

and never overwrite an earlier run.

## Optional Vulkan GPU timestamps

For the automated Vulkan benchmark, add:

```text
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
```

The useful benchmark combination is therefore:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
```

### What the GPU number means

The current GPU scope is explicitly:

```text
main_graphics_command_buffer
```

One timestamp is written at `TOP_OF_PIPE` after the main graphics command buffer begins. The second is written at `BOTTOM_OF_PIPE` after the final swapchain presentation-layout transition and before `vkEndCommandBuffer`.

The final automated-benchmark summary reports:

```text
benchmark gpu_timestamps ...
    scope=main_graphics_command_buffer
    includes_helper_submissions=false
    includes_present=false
    main_graphics_ms_avg=...
    main_graphics_ms_p50=...
    main_graphics_ms_p95=...
    main_graphics_ms_p99=...
    main_graphics_ms_max=...
```

It also records `measured_frames`, `sampled_frames`, the 65,536-sample cap, dropped samples, query-read failures, `timestamp_valid_bits`, and the device timestamp period.

Do **not** describe `main_graphics_ms_*` as total GPU frame time. It excludes separate helper/upload command-buffer execution and the presentation/display interval. It also does not yet identify individual GPU passes.

### Synchronization/lifetime design

Each Renderer frame slot owns a two-query timestamp pool. Results are normally read only after `Renderer.resetBuffers()` has already waited that slot's existing frame fence. Consequently, steady-state GPU profiling does **not** add a new fence wait, `vkDeviceWaitIdle()`, or synchronous per-frame query wait.

At the end of an automated benchmark, any still-pending tail query may be resolved with `VK_QUERY_RESULT_WAIT_BIT`. This occurs after the final measured `runTick()` frame and therefore is not charged to the recorded CPU frame time.

If the graphics queue exposes no timestamp bits, the timestamp period is invalid, query-pool creation fails, or a query read fails, the GPU profiler fails closed and reports its status rather than fabricating a duration.

### Capture boundary

Automated GPU sampling is dormant during launcher/menu activity, benchmark teleport, terrain loading, and the 60-second settle period. It resets and arms at the same `SETTLING -> CAPTURING` transition that arms the CPU profiler. The final GPU distribution therefore describes the measured benchmark interval, not process lifetime.

`capture_start` records:

- `gpu_timestamps=<active>`;
- `gpu_timestamps_requested=<requested>`; and
- `gpu_timestamp_scope=main_graphics_command_buffer`.

The final aggregate is currently an automated-benchmark feature. Manual profiling remains primarily CPU/window diagnostic output; do not assume that enabling GPU timestamps in an arbitrary manual capture gives a periodically aligned GPU trace.

## Automated stationary benchmark

For the named single-player benchmark world only:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
```

The target display name defaults to `VulkanMod Benchmark` in the Overworld. When that world and its player exist, VulkanMod asks the integrated server to switch the player to Spectator and teleport to the fixed pose:

- position: `0 192 0`;
- yaw: `-90`;
- pitch: `30`.

It waits for the client to receive the exact pose and for terrain to become visible, settles for 60 seconds, then starts a new measured capture. The default measured duration is 180 seconds. If the 2048-entry voxel staging cap is observed during settling or capture, the controller keeps measuring for at least 60 seconds after the first cap observation.

When the deadline is reached, VulkanMod flushes the CPU/tick/GPU summaries, closes the capture, invokes Minecraft's normal single-player disconnect/save path, waits for the integrated server to disappear, and then closes the client. An on-screen status line reports the current benchmark phase and remaining time; F3 and manual `/tp` are not required.

Optional controls:

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

Automation is off by default because it changes the target world's player position/game mode and eventually exits Minecraft. Use a copied/pristine benchmark world for formal A/B captures because saving persists the teleport and Spectator mode.

### Benchmark validity guards

During the measured interval VulkanMod aborts rather than accepting a contaminated benchmark if:

- the target world changes;
- the player/camera leaves the exact benchmark pose;
- a screen opens;
- Minecraft loses focus;
- the client pauses;
- the camera is not first-person on the local player;
- framebuffer dimensions change; or
- profiler output fails.

Resize is allowed during settling; the framebuffer is locked only when measurement starts. A failed/aborted run leaves Minecraft open and records the reason in `latest.log`; if the capture file is still healthy it is flushed with an interrupted/abort marker. Discard interrupted runs as formal benchmark evidence.

The benchmark uses Minecraft's actual command dispatcher. CI parses the rotated form `tp @s X Y Z yaw pitch` and deliberately rejects the old targetless `tp X Y Z yaw pitch` form that previously caused the automation failure.

This controller automates the stationary Vulkan workload. It does not yet automate the formal OpenGL control or the 1024-block traversal route.

## CPU/window output

Each summary window records:

- monotonic window ID, epoch range, elapsed time, menu/world/transition context and dimension;
- first/last framebuffer dimensions, width/height ranges and change count;
- render distance, simulation distance, VSync and FPS cap;
- player/screen presence and first/last screen type;
- first/last player pose and changed-frame count;
- frame average, p50, p95, p99, max and slow-frame count;
- separate tick-bearing and render-only frame distributions;
- tick-call count, including catch-up ticks;
- inter-frame loop-gap average/p50/p95/p99/max;
- stage average/p95/max;
- tick-only stage average/p95;
- explicit per-frame positive `unaccounted` remainder;
- bounded joint frame examples;
- the worst frame and its largest known stage;
- tick-local render-thread CPU time and allocations where the JVM exposes them;
- window-level GC count/time deltas, heap use, render-thread CPU delta and allocations;
- terrain build/publication/staging deltas and current queue depths; and
- profiler summary overhead.

The default five-second window is intentionally long enough to keep diagnostic volume/overhead bounded while still localizing transient behavior.

### Context and framebuffer rules

Windows split when the client level changes. A frame that enters or leaves a world is classified as `transition`; stable frames are `menu` or `world`.

For a stationary slice, require at least:

- `context=world`;
- `player_frames=frames`;
- `screen_frames=0`;
- constant framebuffer dimensions; and
- stable player pose.

A world object can exist while a loading screen remains open, so `context=world` alone is not sufficient.

`capture_start initial_framebuffer_px` is only the size at capture start. Per-window first/last/range/change fields are authoritative for resolution-sensitive comparisons.

## Frame classes, loop gaps and examples

Tick-bearing frames and render-only frames are intentionally separated because a 20 Hz client-tick hitch can disappear inside an otherwise high average FPS.

`tick_stage_p95_ms` and `tick_stage_avg_ms` are calculated only over frames that actually ticked. The ordinary `stage_p95_ms` includes zeros from frames where a stage did not execute.

`loop_gap` is the time from the end of profiler bookkeeping for one `runTick()` call to the beginning of the next. It can contain main-loop work or OS scheduling. It is neither frame time nor a GPU timestamp. Per-frame profiler bookkeeping and periodic summary I/O are excluded from the next gap.

Each window can emit up to six evenly spaced `frame_example class=tick` lines and six `class=slow_render` examples. A joint example contains CPU stage timings, preceding loop gap, tick CPU/allocation when available, and an epoch timestamp. They are diagnostic examples, not a full trace or independent percentile sample. The separate `worst` record preserves the isolated maximum.

`profiler_overhead summary_ms` reports the periodic formatting/snapshot/first-flush cost. That pause occurs in the real client loop but is deliberately outside the measured frame and next loop-gap value, preventing a five-second logging artifact from being misidentified as gameplay work.

## Accounting rules

Top-level stages are intended to partition `Minecraft.runTick()`. Nested stages add detail but must **not** be summed again with their parents.

`unaccounted` is the positive per-frame remainder after top-level stages. It is sampled per frame and reported as average/p95/max. Overlap/excess is reported separately, so one overlapping frame cannot cancel an unexplained gap in another frame.

`accounting_overlap_frames` detects:

- top-level stages exceeding the complete frame;
- overlapping tick-detail intervals;
- tick children exceeding their parent;
- game-render children exceeding their parent;
- world-detail children exceeding world render; and
- unbalanced tick probes.

`client_tick_other` subtracts the **union** of nested tick-detail intervals from the parent tick rather than blindly summing children. `game_render_other` and `world_render_other` are residual averages, not independently sampled p95 distributions.

## Known CPU stages

| Stage | Accounting | Meaning |
| --- | --- | --- |
| `frame_slot_wait` | top-level | `Renderer.resetBuffers()`, principally existing frame-slot retirement before resources can be reused. |
| `frame_fence_wait` | top-level | `Renderer.beginFrame()` fence/recreation section. |
| `image_acquire` | top-level | CPU time in `vkAcquireNextImageKHR`. |
| `frame_ops` | top-level | Descriptor/command-buffer/upload-manager bookkeeping after acquisition. |
| `client_tick` | top-level | Complete vanilla `Minecraft.tick()` and synchronous client/game logic. |
| `client_level_tick` | nested tick detail | Client-level tick. |
| `client_entities_tick` | nested tick detail | Client entity tick. |
| `client_renderer_tick` | nested tick detail | Renderer tick, not render. |
| `client_connection_tick` | nested tick detail | Client packet-listener tick. |
| `game_render` | top-level | Complete `GameRenderer.render(...)`. |
| `world_render` | nested | Outermost `GameRenderer.renderLevel(...)`, including recursive portal-world work. |
| `hud_render` | nested | Vanilla `Gui.render(...)`, including the opt-in benchmark/staging readout. |
| `terrain_setup` | nested | VulkanMod terrain camera setup, visibility traversal and rebuild scheduling. |
| `terrain_reposition` | nested | Camera-region reposition work inside terrain setup. |
| `terrain_uploads` | nested | Render-thread publication of completed terrain builds plus normal terrain upload flush. |
| `terrain_draw` | nested | CPU time recording VulkanMod terrain-layer draws; not GPU execution duration. |
| `block_entity_render` | nested | VulkanMod section block-entity rendering loop. |
| `submit_render` | top-level | Final pending area uploads plus VulkanMod `endFrame()`, submit and presentation path. |
| `queue_submit` | nested | CPU API time in Vulkan queue submission. |
| `present` | nested | CPU API time in Vulkan presentation call. |
| `display_update` | top-level | `Window.updateDisplay()`/`RenderSystem.flipFrame` and fullscreen update work. |
| `frame_limit` | top-level | Time in vanilla FPS limiting. |
| `unaccounted` | remainder | Positive `runTick()` wall time not covered by the top-level stages. |

CPU `queue_submit` and `present` durations are not substitutes for GPU timestamps.

## Automated client-tick leaf profiler

`ClientTickBreakdown` is enabled only for automated benchmark captures. It adds direct leaf attribution inside `Minecraft.tick()` for:

- Forge client pre;
- GUI;
- picking;
- game mode;
- textures;
- tutorial;
- Forge level pre;
- level renderer;
- weather;
- ambient world;
- particles;
- music;
- sound;
- keybinds;
- Forge level post; and
- Forge client post.

It emits whole-tick and summed-leaf average/p95/max, overlap count, each leaf's average/p95/max, and per-leaf allocation average/p95/max when thread allocation counters are available. The complete capture is held in bounded primitive arrays and emitted only after the final measured frame so its final formatting/I/O cannot perturb the measured interval.

### Texture-tick discriminator

When the `textures` leaf is active, the profiler additionally times complete `SpriteContents.upload()` bodies and counts corresponding mip/sub-upload calls. The final line is:

```text
client_tick_texture_detail
```

with:

- total ticks and upload-active ticks;
- overlap count;
- sprite-upload and sub-upload call counts/calls-per-tick;
- complete `TextureManager.tick()` average;
- sprite-upload average/p95/max; and
- same-tick non-upload remainder average/p95.

The probe brackets one complete sprite upload rather than every mip copy to keep observer overhead bounded.

Interpretation:

- upload time dominates -> follow staging-copy and Vulkan copy-command-recording overhead without changing animation semantics;
- non-upload time dominates -> inspect ticker/animation/interpolation work;
- both substantial -> quantify each maximum benefit and attack the larger, safer mechanism first.

Do not subtract unrelated percentiles; the non-upload p95 comes from per-tick remainder samples.

## Terrain/worker output

Each world window can align timing with:

- visible/nonempty section count;
- dirty notices and window delta;
- scheduled builds and delta;
- worker builds/published/accepted/dropped totals and deltas;
- high/low queue depths;
- active workers;
- publication waiters/queue;
- voxel staging entries, capacity, bytes and rejection deltas;
- GPU-terrain preflight-full events;
- publication rejection; and
- CPU recovery requests.

Counter resets or renderer/world changes invalidate deltas rather than manufacturing negative work. These boundary snapshots are rate/backlog indicators, not exact per-frame worker attribution.

`WorldRenderer.getChunkStatistics()` is also preserved. Its worker statistics distinguish queued-before-build time, worker build time, queued-before-publication time, render-thread publication work, handoff latency, worker occupancy and accepted/dropped results. Some of these are lifetime/cumulative averages rather than per-window percentiles.

With profiling enabled and voxel staging active, the in-world HUD shows `VulkanMod staging: N/2048`, refreshed once per second. If the cap is reached, the automated benchmark extension ensures the capture includes a post-cap interval rather than requiring indefinite manual waiting.

## JVM/GC interpretation

`tick_resources` measures render-thread CPU time and allocated bytes around tick-bearing frames where the JVM exposes those counters. If tick wall time greatly exceeds tick CPU time, waiting or global pauses become stronger hypotheses. Allocation spikes strengthen a GC-pressure hypothesis but do not prove a specific frame was stopped by GC.

The window-level `jvm` line records GC count/time deltas and heap/render-thread resource deltas. Exact collector-pause-to-frame correlation still requires targeted JFR/GC-notification instrumentation if a future capture remains ambiguous.

## CI validation

The normal software-Vulkan startup smoke enables the CPU profiler at a short summary interval and also enables GPU timestamps. CI requires:

- a real `VULKANMOD_GPU_TIMESTAMP_SMOKE_OK` result from Lavapipe, proving query-pool creation, command-buffer timestamp writes, submission, frame-slot retirement and result readback;
- truthful `capture_start` GPU active/requested/scope metadata;
- parseable capture identity and UUID;
- internally consistent frame classes, tick-resource sample counts and accounting-overlap fields; and
- bounded frame-example output.

Other renderer smoke gates then exercise the same executable through no-early-splash startup, persistent indirect commands, post/depth-post chains, screenshot readback, Create Chronicles compatibility and Crash Assistant.

CI validates the mechanism. Only the user's RX 6900 XT/Create Chronicles benchmark establishes representative hardware/workload behavior.

## Using captures for optimization

For an A/B optimization claim:

1. keep the benchmark workload, framebuffer, graphics settings and profiler flags identical;
2. compare p95/p99/max frame time, not only average FPS;
3. compare tick-bearing and render-only frames separately;
4. compare the targeted CPU leaf/stage and relevant terrain/worker counters;
5. when enabled, compare `main_graphics_ms_*` without treating it as total GPU frame time;
6. check whether CPU time simply moved into another stage or `unaccounted`;
7. keep correctness and memory-safety validation independent of performance results.

Profiler instrumentation has nonzero overhead. When a newer build adds diagnostic probes, use the new split principally to locate mechanism; do not call a small end-to-end FPS difference versus an older, less-instrumented build an optimization.

Formal Phase 5 baseline numbers still follow `docs/TERRAIN_PERFORMANCE_BASELINE.md`. A diagnostic capture outside that fixed contract can identify a bottleneck, but it is not automatically an apples-to-apples baseline result.

## Remaining discriminators and expansion rule

Add narrower instrumentation only when current captures leave an important decision ambiguous.

| Missing discriminator | Current evidence available | Add it when |
| --- | --- | --- |
| GPU execution per individual render pass | Capture-wide main graphics command-buffer average/p50/p95/p99/max | Main graphics GPU time is materially large and CPU timings cannot identify which pass is responsible. |
| Helper/upload GPU execution | Helper CPU/synchronization counters plus explicit `includes_helper_submissions=false` GPU scope | Main graphics time is small but GPU/transfer behavior still explains observed pacing or resource pressure. |
| Actual presentation/display interval | CPU acquire/present/display stages and loop gaps | Main graphics execution is small yet visible frame pacing remains unexplained. |
| Per-frame CPU/GPU timeline correlation | CPU frame examples plus capture-wide GPU distribution | Isolated spikes require proving whether a specific CPU hitch coincided with GPU work. |
| Exact GC pause overlapping one frame | Tick CPU/allocation and window GC deltas | Isolated wall-time spikes remain ambiguous after existing JVM evidence. |
| Individual mod callback time | `client_tick_other`, render residuals and joint examples | A sustained residual dominates and a specific Forge event family needs attribution. |
| Worker latency percentiles per window | Window build/publish deltas, queue depth and cumulative latency averages | Worker churn/backlog remains high without a clear saturation/recovery cause. |
| Full event trace | Bounded examples plus worst-frame record | A rare pathology cannot be reconstructed from aggregate windows and bounded examples. |

Do not blanket-instrument every draw, allocation, Forge callback, worker task, or GPU pass. The profiler's purpose is to turn the next optimization decision into a measurable hypothesis with the least observer effect necessary.
