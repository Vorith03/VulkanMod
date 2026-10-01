# VulkanMod performance profiling

This document describes the opt-in performance/critical-path profiler used for Phase 5 measurement and later optimization work.

## Purpose

The profiler is designed to answer progressively narrower questions without blanket-instrumenting the renderer:

1. where `Minecraft.runTick()` wall time went;
2. which client-tick leaf or renderer/terrain stage explains a recurring hitch;
3. what terrain queues, staging limits, allocation/GC behavior, or synchronization state coincided with it;
4. how much GPU execution occurred inside VulkanMod's **main graphics command buffer** during the same automated benchmark interval; and
5. if that GPU span is material, which broad recording region owns it and whether VulkanMod terrain explains the world portion.

Profiling is disabled by default. CPU hot-path probes use fixed primitive buffers and `System.nanoTime()`; formatting, percentile sorting, JVM telemetry, terrain snapshots and file I/O occur at summary boundaries. GPU timing is separately opt-in and uses frame-slot-owned Vulkan timestamp-query pools.

This is deliberately not a full event trace. Add narrower probes only when captured evidence leaves a decision ambiguous.

## Enable CPU profiling

Add:

```text
-Dvulkanmod.performanceProfiler=true
```

Default output:

```text
logs/vulkanmod-performance.log
```

Optional controls:

```text
-Dvulkanmod.performanceProfiler.summarySeconds=5
-Dvulkanmod.performanceProfiler.durationSeconds=60
-Dvulkanmod.performanceProfiler.output=logs/vulkanmod-performance-run-a.log
-Dvulkanmod.performanceProfiler.slowFrameMs=25
-Dvulkanmod.performanceProfiler.maxSamples=4096
```

`summarySeconds` defaults to 5 seconds and may be set from 0.25 to 300 seconds. `durationSeconds=0` means unlimited in ordinary/manual mode. Automated runs without an explicit output path use a unique file:

```text
logs/vulkanmod-performance-benchmark-<run UUID>.log
```

If profiler output cannot be opened, written, flushed, or closed cleanly, profiling disables/aborts rather than destabilizing gameplay or claiming a successful benchmark.

## Automated stationary benchmark

For the named single-player benchmark world:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
```

The target defaults to `VulkanMod Benchmark` in the Overworld. VulkanMod asks the integrated server to switch the player to Spectator and teleport to:

- position `0 192 0`;
- yaw `-90`;
- pitch `30`.

It waits for the client pose and visible terrain, settles for 60 seconds, then starts a new measured capture. Default measurement is 180 seconds. If the 2048-entry voxel staging cap is observed during settling or capture, measurement continues for at least 60 seconds after the first cap observation.

On successful completion VulkanMod emits the final CPU/tick/GPU aggregates, closes the capture, follows Minecraft's normal single-player disconnect/save path, waits for the integrated server to stop, and closes the client. The HUD shows the automation phase; no F3 or manual `/tp` is required.

Optional benchmark controls:

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

Use a copied/pristine formal benchmark world because the normal save path persists the teleport and Spectator mode.

### Validity guards

During measurement the automated run aborts rather than accepting a contaminated capture if:

- the target world changes;
- player pose/camera changes;
- a screen opens;
- focus is lost;
- the client pauses;
- the camera is not first-person on the local player;
- framebuffer dimensions change; or
- profiler output fails.

Resize during settling is allowed; framebuffer dimensions lock at capture start. An abort leaves the game open and records its reason. Discard interrupted captures as formal benchmark evidence.

CI parses Minecraft's actual rotated teleport syntax, `tp @s X Y Z yaw pitch`, and rejects the old targetless form.

The controller automates the stationary Vulkan workload. It does not yet automate the formal OpenGL baseline or traversal route.

## CPU/window output

Each summary window includes:

- window/epoch identity and menu/world/transition context;
- framebuffer first/last/ranges/change count;
- render distance, simulation distance, VSync and FPS cap;
- player/screen presence and player pose stability;
- frame average/p50/p95/p99/max and slow-frame count;
- separate tick-bearing and render-only frame distributions;
- tick-call/catch-up count;
- inter-frame loop-gap percentiles;
- known stage average/p95/max;
- tick-only stage average/p95;
- explicit positive per-frame `unaccounted` remainder;
- bounded joint tick and slow-render examples plus worst frame;
- tick-local render-thread CPU/allocation counters when available;
- GC/heap/render-thread JVM deltas;
- terrain build/publication/staging counters and queue depths; and
- profiler summary overhead.

Windows split on level changes. For a stationary slice require `context=world`, player on every frame, no screen, stable framebuffer and stable pose. A world can exist behind a loading screen, so `context=world` alone is insufficient.

`loop_gap` is time between profiler bookkeeping at the end of one `runTick()` and the beginning of the next. It can contain main-loop work or OS scheduling and is not GPU time. Summary formatting/I/O is measured separately and excluded from the next loop gap.

Top-level CPU stages partition `runTick()`; nested stages must not be summed again with their parents. `accounting_overlap_frames` exposes impossible overlap/excess, and `unaccounted` remains a positive per-frame residual instead of allowing overlap in one frame to cancel missing coverage in another.

### Known CPU stages

| Stage | Accounting | Meaning |
| --- | --- | --- |
| `frame_slot_wait` | top-level | Existing frame-slot retirement/resource-reuse wait. |
| `frame_fence_wait` | top-level | `Renderer.beginFrame()` fence/recreation section. |
| `image_acquire` | top-level | CPU time in `vkAcquireNextImageKHR`. |
| `frame_ops` | top-level | Descriptor/command-buffer/upload-manager bookkeeping after acquire. |
| `client_tick` | top-level | Complete `Minecraft.tick()`. |
| `client_level_tick`, `client_entities_tick`, `client_renderer_tick`, `client_connection_tick` | nested tick | Direct tick-path detail. |
| `game_render` | top-level | Complete `GameRenderer.render(...)`. |
| `world_render` | nested | Outermost `GameRenderer.renderLevel(...)`, including recursive portal worlds. |
| `hud_render` | nested | Complete vanilla `Gui.render(...)`. |
| `terrain_setup` | nested | Terrain camera/visibility/rebuild scheduling. |
| `terrain_reposition` | nested | Camera-region reposition. |
| `terrain_uploads` | nested | Render-thread terrain publication/upload flush. |
| `terrain_draw` | nested | **CPU command-recording** time for VulkanMod terrain layers. |
| `block_entity_render` | nested | VulkanMod section block-entity loop. |
| `submit_render` | top-level | Final pending uploads plus endFrame/submit/present path. |
| `queue_submit`, `present` | nested | CPU time inside Vulkan queue API calls; not GPU duration. |
| `display_update` | top-level | `Window.updateDisplay()`/flip/fullscreen work. |
| `frame_limit` | top-level | Vanilla FPS limiting. |
| `unaccounted` | remainder | Positive `runTick()` wall time outside top-level stages. |

`client_tick_other` subtracts the union of nested tick intervals. `game_render_other` and CPU `world_render_other` are residual averages rather than independently sampled p95 distributions.

## Automated client-tick leaf profiler

`ClientTickBreakdown` is enabled for automated benchmark captures and directly attributes tick time to Forge client pre/post, GUI, picking, game mode, textures, tutorial, Forge level pre/post, level renderer, weather, ambient world, particles, music, sound and keybinds.

It emits whole-tick and summed-leaf average/p95/max, overlap count, per-leaf average/p95/max and per-leaf allocations where supported. Capture-wide arrays are bounded and emitted after the measured interval.

### Texture-tick discriminator

While the `textures` leaf is active, complete `SpriteContents.upload()` bodies are timed and sprite/mip-sub-upload calls counted. The final line:

```text
client_tick_texture_detail
```

contains complete `TextureManager.tick()` timing, sprite-upload avg/p95/max, and the same-tick non-upload remainder avg/p95. Only one start/end clock pair is added per complete sprite upload rather than per mip copy.

Interpretation:

- sprite-upload time dominates -> investigate staging-copy and Vulkan copy-command-recording overhead while preserving animation semantics;
- non-upload remainder dominates -> inspect ticker/animation/interpolation work;
- both substantial -> quantify the maximum benefit of each and attack the larger safer mechanism first.

Do not subtract unrelated percentiles; the non-upload p95 is calculated from per-tick remainder samples.

## Optional Vulkan GPU timestamps

Enable with the CPU profiler using:

```text
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
```

The useful stationary benchmark combination is:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
```

### Guaranteed main-graphics scope

The guaranteed GPU scope is:

```text
main_graphics_command_buffer
```

A frame-start timestamp is recorded at `TOP_OF_PIPE` after the main graphics command buffer begins. A frame-end timestamp is recorded at `BOTTOM_OF_PIPE` after the final swapchain presentation-layout transition and before `vkEndCommandBuffer`.

The final line:

```text
benchmark gpu_timestamps ...
```

reports `main_graphics_ms_avg`, p50, p95, p99 and max, plus measured/sample counts, the 65,536-sample cap, dropped samples, query failures, timestamp valid bits and device timestamp period.

**Do not call this total GPU frame time.** The scope explicitly excludes separate helper/upload command-buffer execution and the presentation/display interval.

### Synchronization and lifetime

Each Renderer frame slot owns its timestamp query pool. Normal results are read only after `Renderer.resetBuffers()` has already waited that slot's existing frame fence. GPU profiling therefore adds no new steady-state fence wait, `vkDeviceWaitIdle()`, or synchronous per-frame query wait.

At automated-benchmark completion, pending tail data may use `VK_QUERY_RESULT_WAIT_BIT` after the final measured `runTick()` frame so the tail is not silently discarded.

Unsupported timestamp bits, invalid timestamp period, query-pool creation failure or query-read failure fail closed instead of fabricating measurements.

Automated GPU sampling remains dormant through menu/teleport/terrain-load/settling and resets/arms at the same transition as the CPU measured capture. CPU/tick/GPU aggregates therefore cover the same stationary interval.

## Coarse GPU pass breakdown

The same query pool carries fixed high-level markers. The final line:

```text
benchmark gpu_passes ...
```

reports sample count, average, p95 and max for these non-overlapping regions of the **main graphics command buffer**:

| GPU bucket | Boundary-defined meaning |
| --- | --- |
| `pre_world` | Main command-buffer start -> outermost `GameRenderer.renderLevel` entry. |
| `world` | Outermost `renderLevel` entry -> exit. Recursive portal worlds remain inside this span. |
| `between_world_hud` | Outer world exit -> `Gui.render` entry. Do not assume this is exclusively post-processing. |
| `hud` | Complete `Gui.render` interval. |
| `tail` | HUD exit -> main command-buffer end. |

VulkanMod terrain receives a bounded nested attribution:

- each `WorldRenderer.renderSectionLayer` call can receive one begin/end timestamp pair;
- at most **32 terrain segments per frame** are retained;
- `terrain` is the sum of complete terrain segments; and
- `world_other = world - terrain` is a residual for all non-terrain world GPU commands, including entities/effects/block entities/weather/sky/clouds/Forge callbacks/portal work where applicable. It is not an entity-only timer.

The report also includes `breakdown_frames`, `invalid_frames`, terrain segment count and terrain-segment drops.

### GPU breakdown validity

A pass frame is accepted only when the fixed boundary durations add back to the independently measured main-graphics span within **5 microseconds**. Terrain must also be complete, non-overflowed and no larger than its enclosing `world` span. Invalid/incomplete pass frames are omitted from pass averages instead of being treated as valid measurements.

The independent `benchmark gpu_timestamps` main-graphics aggregate remains available even when a frame lacks a usable detailed partition.

Timestamp boundaries use broad pipeline completion markers; they are intended to decide **which region deserves the next probe**, not to replace RenderDoc/RGP-style per-draw analysis.

## Terrain/worker diagnostics

Each world window aligns timings with visible/nonempty sections, dirty/scheduled/build/publish deltas, worker queue depths, active workers, publication waiters, staging entries/bytes/rejections, GPU-terrain preflight-full events, publication rejection and CPU recovery requests.

`WorldRenderer.getChunkStatistics()` also preserves queue-before-build, build, queue-before-publication, publication and total handoff diagnostics. Some worker latency data is cumulative/aggregate rather than per-window percentiles.

The in-world staging HUD shows `VulkanMod staging: N/2048` once per second when relevant. Boundary snapshots are backlog/rate indicators, not exact per-frame worker attribution.

## JVM/GC interpretation

Tick-local CPU/allocation and window-level GC/heap deltas can strengthen or weaken a GC hypothesis but cannot prove that one particular frame overlapped a collector pause. Add bounded JFR/collector-event correlation only if a future isolated hitch remains ambiguous after current evidence.

## CI validation

The normal software-Vulkan startup smoke enables CPU profiling and GPU timestamps. CI requires a real:

```text
VULKANMOD_GPU_TIMESTAMP_SMOKE_OK
```

from Lavapipe, proving query-pool creation, timestamp recording, graphics submission, existing-fence retirement and query readback. With the coarse profiler this also exercises the expanded query pool and multi-query readback path. Capture metadata must truthfully report requested/active/scope state.

The complete CI matrix additionally validates distributable packaging, Immersive Portals anchors, no-early-splash startup, persistent GPU-indirect commands, post/depth-post chains, screenshot readback, Create Chronicles compatibility and Crash Assistant.

CI validates mechanism/correctness. Only the user's RX 6900 XT/Create Chronicles run establishes representative hardware/workload timings.

## Using captures for optimization

For an A/B performance claim:

1. keep world/camera/framebuffer/settings/profiler flags identical;
2. compare p95/p99/max as well as average FPS;
3. keep tick-bearing and render-only CPU frames separate;
4. compare the targeted CPU leaf/stage and relevant worker counters;
5. interpret `main_graphics_ms_*` only within its documented scope;
6. use `gpu_passes` to select the next GPU subsystem rather than blanket-instrumenting all draws;
7. check whether CPU time merely moved into another stage or `unaccounted`; and
8. keep correctness/memory-safety gates independent of timing improvements.

Profiler instrumentation has nonzero observer cost. A build with additional diagnostic probes should be used primarily to locate a mechanism; a small FPS difference versus a less-instrumented build is not by itself an optimization result.

Formal Phase 5 baseline numbers still follow `docs/TERRAIN_PERFORMANCE_BASELINE.md`.

## Remaining discriminators and expansion rule

Add narrower instrumentation only when the current capture leaves an important decision ambiguous.

| Missing discriminator | Current evidence | Add it when |
| --- | --- | --- |
| Finer GPU work inside a coarse bucket | Main graphics + coarse pass + terrain/world residuals | One coarse GPU bucket is materially dominant. |
| Helper/upload GPU execution | Explicitly excluded from main graphics scope; CPU upload/sync diagnostics exist | Main graphics is insufficient and texture/upload/synchronization evidence specifically points to helper submissions. |
| Actual presentation/display interval | CPU acquire/present/display stages and loop gaps | Main graphics is small but visible pacing remains unexplained. |
| Per-frame CPU/GPU correlation | CPU joint examples + capture-wide GPU distributions | Rare spikes require proving CPU/GPU coincidence. |
| Exact GC pause per frame | Tick CPU/allocation + window GC deltas | Isolated wall spikes remain ambiguous. |
| Individual mod callback time | Tick/world residuals | A sustained residual points to a specific Forge event family. |
| Worker latency percentiles per window | Worker queues/deltas + cumulative latencies | Worker backlog remains high without a clear saturation cause. |
| Full event trace | Bounded frame examples + worst record | A rare pathology cannot be reconstructed from aggregates. |

Do not blanket-instrument every draw, allocation, Forge callback, worker task, or GPU pass. The profiler's job is to make the next optimization decision measurable with the least observer effect necessary.
