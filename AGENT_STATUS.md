# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current executable

- Latest CI-validated executable commit: `e545e32cae13ec2698bea97b609dfa8e0621814a` (`perf: arm GPU timestamps with benchmark capture`).
- CI **#928** / run `36901279213` is fully green.
- Build artifact: `VulkanMod-Forge-build-928`, artifact ID `11181958146`, SHA-256 `838f01b785eda4037b8e1060bab92b9d1242330c5a6b5eecb2adad8c0212755d`.
- Smoke logs: `VulkanMod-Forge-smoke-log-928`, artifact ID `11181813280`, SHA-256 `97821992e3a2dd05d86322e548da6a8410a86c5f918fbfdae969ddc63c4ad68a`.
- #928 passed distributable verification, packaged Immersive Portals mixin-anchor validation, both Forge startup modes, persistent GPU indirect, post-chain/depth-post-chain, screenshot readback, Create Chronicles compatibility, and Crash Assistant. Private resource-pack fixture steps were skipped as configured.
- The normal Lavapipe startup smoke now enables the optional GPU timestamp profiler, requires a real `VULKANMOD_GPU_TIMESTAMP_SMOKE_OK` result, and validates truthful GPU active/requested/scope fields in the profiler capture. This proves query-pool creation, timestamp writes, submission, existing-fence retirement and query readback on software Vulkan.
- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.

## RX 6900 XT benchmark evidence that still drives the optimization decision

The latest completed hardware capture remains the automated stationary benchmark from build #831 / commit `bbc8dd7aa244db1f138a7329b13b8e5f7ecf5622` on the user's RX 6900 XT / RADV system. The run used the fixed `VulkanMod_Benchmark` world/camera, 60 s settle, 180 s capture, 260 FPS cap, locked 2552x1374 framebuffer, render distance 16 and simulation distance 12. It completed normally.

Across the 180 s capture:

- 28,877 frames were recorded in about 179.94 s of summary windows, about 160.48 frames/s.
- There were exactly 3,600 tick-bearing frames and 25,277 render-only frames.
- Tick-bearing frames averaged about **28.924 ms**; render-only frames averaged about **2.991 ms**.
- 2,966 / 3,600 tick-bearing frames exceeded 25 ms, versus only 1 / 25,277 render-only frames. The sustained frame-time problem is therefore tied to the 20 Hz client-tick path, not ordinary render-only frames.
- Whole-capture client tick averaged **24.274 ms**, p95 **28.945 ms**, max **47.883 ms**.
- The leaf profiler accounted for **20.605 ms/tick** on average with **0 overlap ticks**, so direct leaf attribution is internally consistent.
- `TextureManager.tick()` dominates: **17.569 ms/tick average**, **21.834 ms p95**, **28.911 ms max**. That is about 72% of client-tick elapsed time and about 351 ms of render-thread elapsed time per second at 20 TPS.
- The next named cost is particles at **1.635 ms/tick average**. Texture-manager allocation is about **274.9 KiB/tick**; particles allocate much more (~2.56 MiB/tick) but consume far less elapsed time, so allocation volume alone does not explain the texture cost.
- GPU timestamps were disabled in #831. Vulkan frame-slot/fence/acquire/submit/display waits were small in the available CPU-side timings, but that capture cannot establish GPU execution cost.
- Terrain worker queues were normally idle in the stationary capture and terrain upload/wait timings were small. Do not reopen terrain staging as the primary explanation for this recurring hitch without contradictory evidence.

## Texture-tick diagnostic now present in #928

The measured `textures` leaf is the complete `TextureManager.tick()` call. Current VulkanMod already batches animated sprite uploads into one graphics upload command buffer for the selected upload tick:

- `MTextureManager.tick()` starts one graphics recording batch, ticks all tickable textures, transitions touched image layouts, then submits the batch once.
- `MSpriteContents.upload()` suppresses uploads on non-selected catch-up ticks and records the touched Vulkan image for the final transition.
- `VulkanImage.uploadSubTextureAsync()` appends mip copies to that shared command buffer while the batch is active.

Therefore, “batch animated texture submissions” is **not** an evidence-backed optimization; that mechanism already exists.

The #844 diagnostic, retained in #928, separates the dominant 17.569 ms texture bucket without changing rendering behavior:

- aggregate time spent inside complete `SpriteContents.upload()` bodies while the texture leaf is active;
- sprite-upload call count and corresponding mip/sub-upload count;
- complete texture average plus sprite-upload average/p95/max;
- same-tick non-upload remainder average/p95, calculated from per-tick remainder samples rather than subtracting unrelated percentiles; and
- only one start/end clock pair per sprite upload, not per mip copy.

The decisive output line is `client_tick_texture_detail`.

## Optional GPU timestamp profiler now present in #928

Build #928 also adds the next broad discriminator that the CPU profiler could not answer.

Enable it with:

```text
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
```

The timestamp scope is intentionally `main_graphics_command_buffer`:

- `TOP_OF_PIPE` after the main graphics command buffer begins;
- `BOTTOM_OF_PIPE` after the final swapchain layout transition and before command-buffer end;
- one two-query pool per Renderer frame slot;
- normal reads occur only after that slot's **existing** frame fence has already been waited, so steady-state profiling adds no new GPU/CPU synchronization point;
- a final pending tail query may use `VK_QUERY_RESULT_WAIT_BIT` after the last measured frame, outside recorded `runTick()` CPU time;
- graphics timestamp support/period/query failures fail closed rather than fabricating a result.

The final automated line is `benchmark gpu_timestamps` with capture-wide main-graphics average/p50/p95/p99/max, measured/sample counts, query failures, timestamp valid bits and device timestamp period.

Important interpretation boundary:

- `main_graphics_ms_*` is **not total GPU frame time**;
- separate helper/upload command-buffer execution is explicitly excluded;
- presentation/display interval is explicitly excluded; and
- individual render-pass GPU timing is not yet instrumented.

Automated GPU sampling is dormant during startup/menu/teleport/terrain-load/settling and resets/arms at the same transition that starts the measured CPU capture. This correction was made before #928 was accepted so the GPU distribution and CPU/tick distribution describe the same benchmark interval.

Do not add per-pass/helper GPU timestamps until the RX result establishes that broad GPU execution is materially relevant. The next layer must be evidence-driven.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**, current useful priority. Formal OpenGL baseline, Vulkan baseline, and hitch/frame-time evidence remain open under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid: **6/11**. Correct GPU visibility/section selection is the first substantial open implementation gate, but performance claims still require comparable Phase 5 evidence.
- Immersive Portals current compatibility behavior is RX-confirmed working. Preserve its documented semantic boundary in `docs/CREATE_CHRONICLES_COMPATIBILITY.md` unless new evidence contradicts it.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from rendering/performance work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Next useful action

Run **build #928** on the user's RX 6900 XT/Create Chronicles environment using the **same automated stationary benchmark** as the successful build #831 capture.

Required flags:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
```

Keep the same benchmark world/camera, locked 2552x1374 framebuffer/settings, render distance 16, simulation distance 12 and 260 FPS cap. Keep Minecraft focused and do not resize during measurement. Return the generated `logs/vulkanmod-performance-benchmark-*.log`; `latest.log` is only needed if automation aborts or another runtime issue appears.

This one capture now answers two bounded questions:

1. `client_tick_texture_detail` separates the dominant texture tick into complete sprite-upload work versus non-upload ticker/animation work.
2. `benchmark gpu_timestamps` establishes whether main graphics GPU execution is itself substantial during the same measured interval.

Decision rules:

1. If `sprite_upload_ms_avg` explains most of `texture_ms_avg`, follow upload call/mip counts into staging-copy and Vulkan copy-command-recording overhead. Preserve animation semantics.
2. If `non_upload_ms_avg` explains most of `texture_ms_avg`, trace animated texture ticker/interpolation work instead. Do not reduce cadence or visibility semantics until redundant work is demonstrated.
3. If both texture components are substantial, quantify each maximum benefit and attack the larger, safer mechanism first.
4. If `main_graphics_ms_*` is small while CPU tick frames remain slow, keep optimization focused on CPU/tick work and do not add per-pass GPU probes.
5. If `main_graphics_ms_*` is materially large, add the minimum next timestamp boundaries needed to separate broad GPU passes. Helper/upload GPU timing is a separate scope and should be added only if the evidence points there.
6. Treat #831 as the matched pre-diagnostic evidence. #928 contains extra texture and GPU instrumentation, so use the new splits primarily to locate mechanism; do not present a small end-to-end FPS difference between #831 and #928 as a confirmed optimization.

Do not ask for another broad compatibility retest. One #928 stationary benchmark capture is the next hardware evidence needed.
