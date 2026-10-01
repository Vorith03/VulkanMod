# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current executable

- Latest CI-validated executable commit: `2b6e1ac915965069b63b776b342313993b18fe5f` (`perf: harden GPU pass sample validity`).
- CI **#931** / run `36909650317` is fully green.
- Build artifact: `VulkanMod-Forge-build-931`, artifact ID `11186381851`, SHA-256 `9a8bd4cc5d2fb823f7ed39fe92be6584f7f21f7f5490ca532fbdc0ba0263edcc`.
- Smoke logs: `VulkanMod-Forge-smoke-log-931`, artifact ID `11186331840`, SHA-256 `5cf7757a6ffeb16d37c1b7858e59ed703b699270527f61582e3cebd7067388ca`.
- #931 passed distributable verification, packaged Immersive Portals mixin-anchor validation, both Forge startup modes, persistent GPU indirect, post-chain/depth-post-chain, screenshot readback, Create Chronicles compatibility, and Crash Assistant. Private resource-pack fixture steps were skipped as configured.
- The normal Lavapipe startup smoke enables GPU timestamps and requires a real `VULKANMOD_GPU_TIMESTAMP_SMOKE_OK` result. #931 therefore exercises the expanded query pool, timestamp writes, submission, existing-fence retirement and multi-query readback on software Vulkan rather than compile-only code.
- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.

## RX 6900 XT benchmark evidence that still drives the optimization decision

The latest completed hardware capture remains the automated stationary benchmark from build #831 / commit `bbc8dd7aa244db1f138a7329b13b8e5f7ecf5622` on the user's RX 6900 XT / RADV system. The run used the fixed `VulkanMod_Benchmark` world/camera, 60 s settle, 180 s capture, 260 FPS cap, locked 2552x1374 framebuffer, render distance 16 and simulation distance 12. It completed normally.

**Distant Horizons rendering was disabled for #831 and for the existing Phase 5 benchmark captures discussed here. Keep Distant Horizons disabled for the matched #931 capture. A future DH-enabled capture is a different workload and must be explicitly labeled rather than compared directly with this series.**

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

## Texture-tick diagnostic retained in #931

The measured `textures` leaf is the complete `TextureManager.tick()` call. Current VulkanMod already batches animated sprite uploads into one graphics upload command buffer for the selected upload tick:

- `MTextureManager.tick()` starts one graphics recording batch, ticks all tickable textures, transitions touched image layouts, then submits the batch once.
- `MSpriteContents.upload()` suppresses uploads on non-selected catch-up ticks and records the touched Vulkan image for the final transition.
- `VulkanImage.uploadSubTextureAsync()` appends mip copies to that shared command buffer while the batch is active.

Therefore, “batch animated texture submissions” is **not** an evidence-backed optimization; that mechanism already exists.

The diagnostic retained in #931 separates the dominant 17.569 ms texture bucket without changing rendering behavior:

- aggregate time spent inside complete `SpriteContents.upload()` bodies while the texture leaf is active;
- sprite-upload call count and corresponding mip/sub-upload count;
- complete texture average plus sprite-upload average/p95/max;
- same-tick non-upload remainder average/p95, calculated from per-tick remainder samples rather than subtracting unrelated percentiles; and
- only one start/end clock pair per sprite upload, not per mip copy.

The decisive output line is `client_tick_texture_detail`.

## GPU timestamp profiler now present in #931

Enable it with:

```text
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
```

The guaranteed scope remains `main_graphics_command_buffer`:

- frame start is `TOP_OF_PIPE` after the main graphics command buffer begins;
- frame end is `BOTTOM_OF_PIPE` after the final swapchain layout transition and before command-buffer end;
- query pools are owned per Renderer frame slot;
- normal reads occur only after that slot's **existing** frame fence has already been waited, so steady-state profiling adds no new GPU/CPU synchronization point;
- a final pending tail query may use `VK_QUERY_RESULT_WAIT_BIT` after the last measured frame, outside recorded `runTick()` CPU time; and
- graphics timestamp support/period/query failures fail closed rather than fabricating a result.

The final `benchmark gpu_timestamps` line reports capture-wide main-graphics average/p50/p95/p99/max plus measured/sample counts, query failures, timestamp valid bits and timestamp period.

Important boundary: `main_graphics_ms_*` is **not total GPU frame time**. Separate helper/upload command-buffer execution and presentation/display are explicitly excluded.

### Coarse GPU pass breakdown

#931 adds the bounded coarse breakdown requested before the next hardware run. Fixed frame markers partition the main graphics span into:

- `pre_world`: frame start to the outermost `GameRenderer.renderLevel` entry;
- `world`: outermost `renderLevel` entry to exit, including recursive portal-world rendering;
- `between_world_hud`: outer world exit to `Gui.render` entry; this is boundary-defined and must **not** be described as exclusively post-processing;
- `hud`: complete `Gui.render` GPU interval; and
- `tail`: HUD exit to main graphics command-buffer end.

VulkanMod terrain rendering also has a bounded sub-attribution:

- each `WorldRenderer.renderSectionLayer` call receives a timestamp pair, up to **32 terrain segments per frame**;
- `terrain` is the sum of complete terrain segments inside the outer world span;
- `world_other = world - terrain` is a residual containing entities, effects, block entities, weather/sky/cloud work, Forge callbacks, portal rendering and any other non-terrain world commands. It is not an entity-only timer.

The final line is `benchmark gpu_passes`. Each named bucket reports sample count, average, p95 and max. It also reports `breakdown_frames`, invalid frames, terrain segment count and terrain-segment drops.

The profiler validates the partition before accepting a frame: fixed pass durations must add back to the measured main-graphics span within 5 microseconds, and terrain must be complete, non-overflowed and no larger than its enclosing world span. Invalid/incomplete pass frames are omitted from pass averages rather than contaminating them. The main-graphics aggregate remains independently available.

Automated GPU sampling is dormant during startup/menu/teleport/terrain-load/settling and resets/arms at the same transition that starts the CPU capture, so CPU/tick/GPU distributions describe the same measured interval.

Do not add finer per-draw/per-mod GPU probes before reading the #931 RX result. The next layer must still be evidence-driven.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**, current useful priority. Formal OpenGL baseline, Vulkan baseline, and hitch/frame-time evidence remain open under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid: **6/11**. Correct GPU visibility/section selection is the first substantial open implementation gate, but performance claims still require comparable Phase 5 evidence.
- Immersive Portals current compatibility behavior is RX-confirmed working. Preserve its documented semantic boundary in `docs/CREATE_CHRONICLES_COMPATIBILITY.md` unless new evidence contradicts it.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from rendering/performance work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Next useful action

Run **build #931** on the user's RX 6900 XT/Create Chronicles environment using the **same automated stationary benchmark** as the successful build #831 capture.

Use this full benchmark JVM-property set for the matched #931 diagnostic:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
-Dvulkanmod.performanceProfiler.summarySeconds=5
-Dvulkanmod.performanceProfiler.durationSeconds=180
-Dvulkanmod.performanceProfiler.slowFrameMs=25
-Dvulkanmod.performanceProfiler.maxSamples=4096
-Dvulkanmod.performanceProfiler.benchmarkWorld=VulkanMod Benchmark
-Dvulkanmod.performanceProfiler.benchmarkX=0
-Dvulkanmod.performanceProfiler.benchmarkY=192
-Dvulkanmod.performanceProfiler.benchmarkZ=0
-Dvulkanmod.performanceProfiler.benchmarkYaw=-90
-Dvulkanmod.performanceProfiler.benchmarkPitch=30
-Dvulkanmod.performanceProfiler.benchmarkSettleSeconds=60
-Dvulkanmod.performanceProfiler.benchmarkAfterStagingCapSeconds=60
-Dvulkanmod.experimentalSectionVoxels=true
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

`experimentalSectionVoxels=true` is intentionally explicit even though the GPU-terrain CPU-bypass path also forces the required voxel/sparse-lighting staging on. Keeping it in the launch contract makes the intended workload self-describing. Do not set an explicit profiler output path; automated runs then create a unique `logs/vulkanmod-performance-benchmark-<UUID>.log` instead of overwriting earlier captures.

Keep the same locked 2552x1374 framebuffer/settings, render distance 16, simulation distance 12 and 260 FPS cap. **Keep Distant Horizons rendering disabled.** Keep Minecraft focused and do not resize during measurement. Return the generated `logs/vulkanmod-performance-benchmark-*.log`; `latest.log` is only needed if automation aborts or another runtime issue appears.

This one capture now answers three bounded questions:

1. `client_tick_texture_detail` separates the dominant texture tick into complete sprite-upload work versus non-upload ticker/animation work.
2. `benchmark gpu_timestamps` establishes the main graphics GPU execution distribution over the same measured interval.
3. `benchmark gpu_passes` identifies the broad main-command-buffer region responsible if GPU execution is material, including terrain versus non-terrain world GPU work.

Decision rules:

1. If `sprite_upload_ms_avg` explains most of `texture_ms_avg`, follow upload call/mip counts into staging-copy and Vulkan copy-command-recording overhead. Preserve animation semantics.
2. If `non_upload_ms_avg` explains most of `texture_ms_avg`, trace animated texture ticker/interpolation work instead. Do not reduce cadence or visibility semantics until redundant work is demonstrated.
3. If both texture components are substantial, quantify each maximum benefit and attack the larger, safer mechanism first.
4. If `main_graphics_ms_*` is small while CPU tick frames remain slow, keep optimization focused on CPU/tick work; do not add finer GPU probes.
5. If `world_ms_*` dominates and `terrain_ms_*` explains most of it, terrain GPU work becomes the next bounded GPU investigation.
6. If `world_other_ms_*` dominates, investigate the non-terrain world path rather than terrain.
7. If `between_world_hud_ms_*`, `hud_ms_*`, or `tail_ms_*` dominates, trace that exact boundary-defined region next; do not infer a narrower subsystem from the label alone.
8. Helper/upload GPU execution remains outside the current scope. Add it only if main-graphics/pass evidence is insufficient and the texture/upload path or synchronization evidence specifically points there.
9. Treat #831 as the matched pre-diagnostic evidence. #931 contains extra texture and GPU instrumentation, so use the new splits primarily to locate mechanism; do not present a small end-to-end FPS difference between #831 and #931 as a confirmed optimization.

Do not ask for another broad compatibility retest. One #931 stationary benchmark capture is the next hardware evidence needed.
