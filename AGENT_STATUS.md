# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current executable

- Latest CI-validated executable commit: `e8caca7594d9a61232bd834507fb788972149e1a` (`ci: build distributable before temporary compat graph`).
- This commit is a CI-only child of the new texture-tick diagnostic commits `9ae1ba1628697f72d092a327b23b78576784cdf3` and `528903351750cc88a222e5b42b6a402d58754e04`; the performance instrumentation is therefore included in the artifact.
- CI **#844** / run `36709595017` is fully green.
- Build artifact: `VulkanMod-Forge-build-844`, artifact ID `11094470026`, SHA-256 `4d1cedcd9e57534df8b488997016d3ae1736772d1190bf594055bfe816e0eabf`.
- Smoke logs: `VulkanMod-Forge-smoke-log-844`, artifact ID `11094355606`, SHA-256 `1c30f7933015fdf3d88bfcec79dd85d1191fabaf966fb35cfa7e15988ef01138`.
- #844 passed distributable verification, packaged Immersive Portals mixin-anchor validation, both Forge startup modes, persistent GPU indirect, post-chain/depth-post-chain, screenshot readback, Create Chronicles compatibility, and Crash Assistant. Private resource-pack fixture steps were skipped as configured.
- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.

## RX 6900 XT benchmark evidence that drives the next action

The latest completed runtime capture is the automated stationary benchmark from build #831 / commit `bbc8dd7aa244db1f138a7329b13b8e5f7ecf5622` on the user's RX 6900 XT / RADV system. The run used the fixed `VulkanMod_Benchmark` world/camera, 60 s settle, 180 s capture, 260 FPS cap, locked 2552x1374 framebuffer, render distance 16 and simulation distance 12. It completed normally.

Across the 180 s capture:

- 28,877 frames were recorded in about 179.94 s of summary windows, about 160.48 frames/s.
- There were exactly 3,600 tick-bearing frames and 25,277 render-only frames.
- Tick-bearing frames averaged about **28.924 ms**; render-only frames averaged about **2.991 ms**.
- 2,966 / 3,600 tick-bearing frames exceeded 25 ms, versus only 1 / 25,277 render-only frames. The sustained frame-time problem is therefore tied to the 20 Hz client-tick path, not ordinary render-only frames.
- Whole-capture client tick averaged **24.274 ms**, p95 **28.945 ms**, max **47.883 ms**.
- The leaf profiler accounted for **20.605 ms/tick** on average with **0 overlap ticks**, so the direct leaf attribution is internally consistent.
- `TextureManager.tick()` dominates: **17.569 ms/tick average**, **21.834 ms p95**, **28.911 ms max**. That is about 72% of client-tick elapsed time and about 351 ms of render-thread elapsed time per second at 20 TPS.
- The next named cost is particles at **1.635 ms/tick average**. Texture-manager allocation is about **274.9 KiB/tick**; particles allocate much more (~2.56 MiB/tick) but consume far less elapsed time, so allocation volume alone does not explain the texture cost.
- GPU timestamps were disabled in this capture. Vulkan frame-slot/fence/acquire/submit/display waits were small in the available CPU-side stage timings. Do not convert this evidence into a claim about measured GPU execution time.
- Terrain worker queues were normally idle in the stationary capture and terrain upload/wait timings were small. Do not reopen terrain staging as the primary explanation for this recurring hitch without contradictory evidence.

## Texture-tick interpretation and bounded diagnostic

The measured `textures` leaf is the complete `TextureManager.tick()` call. Current VulkanMod already batches animated sprite uploads into one graphics upload command buffer for the selected upload tick:

- `MTextureManager.tick()` starts one graphics recording batch, ticks all tickable textures, transitions touched image layouts, then submits the batch once.
- `MSpriteContents.upload()` suppresses uploads on non-selected catch-up ticks and records the touched Vulkan image for the final transition.
- `VulkanImage.uploadSubTextureAsync()` appends mip copies to that shared command buffer while the batch is active.

Therefore, “batch animated texture submissions” is **not** an evidence-backed optimization; that mechanism already exists.

The remaining important uncertainty is inside the 17.569 ms texture bucket:

1. CPU-side texture ticker / animation / interpolation work before `SpriteContents.upload()`, versus
2. the actual sprite/mip upload path: staging copy plus Vulkan copy-command recording.

Changing animation cadence, interpolation, or visibility semantics before separating those costs would risk resource-pack correctness for an unproven benefit. The build #844 diagnostic resolves that distinction without changing rendering behavior:

- `ClientTickBreakdown` now records aggregate time spent inside complete `SpriteContents.upload()` bodies while the texture leaf is active.
- It records the number of sprite upload calls and the corresponding mip/sub-upload count.
- It derives the per-tick non-upload remainder from the actual same-tick texture total minus same-tick sprite-upload time; p95 is calculated from per-tick remainder samples rather than by subtracting independent percentiles.
- Timing adds only one start/end clock pair per sprite upload, not per mip copy. All counters remain automated-benchmark-only and normal gameplay/manual profiling stay unaffected.
- The new end-of-capture line is `client_tick_texture_detail` with `active_upload_ticks`, `sprite_upload_calls`, `sub_upload_calls`, calls-per-tick, `texture_ms_avg`, sprite-upload avg/p95/max, and non-upload avg/p95.

CI #844 proves the diagnostic compiles, packages, applies through normal Forge startup, and preserves the existing smoke/compatibility matrix. It does **not** establish a performance improvement yet; this is the smallest experiment needed before changing the hot path.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**, current useful priority. Formal OpenGL baseline, Vulkan baseline, and hitch/frame-time evidence remain open under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid: **6/11**. Correct GPU visibility/section selection is the first substantial open implementation gate, but performance claims still require comparable Phase 5 evidence.
- Immersive Portals current compatibility behavior is RX-confirmed working. Preserve its documented semantic boundary in `docs/CREATE_CHRONICLES_COMPATIBILITY.md` unless new evidence contradicts it.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from rendering/performance work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Next useful action

Run **build #844** on the user's RX 6900 XT/Create Chronicles environment using the **same automated stationary benchmark** as the successful build #831 capture.

Required flags:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
```

Keep the same benchmark world, camera, locked 2552x1374 framebuffer/settings, render distance 16, simulation distance 12 and 260 FPS cap. Keep Minecraft focused and do not resize during measurement. Return the generated `logs/vulkanmod-performance-benchmark-*.log`; `latest.log` is only needed if automation aborts or another runtime issue appears.

The decisive line is `client_tick_texture_detail`:

1. If `sprite_upload_ms_avg` explains most of `texture_ms_avg`, follow upload call count/mip count into staging-copy and Vulkan copy-command recording. The likely high-value experiment is reducing per-region CPU/Vulkan command overhead without changing animation semantics.
2. If `non_upload_ms_avg` explains most of `texture_ms_avg`, trace the animated texture ticker/interpolation path instead. Preserve animation cadence and resource-pack behavior until redundant work is demonstrated.
3. If both are substantial, quantify the maximum benefit of each and attack the larger, safer mechanism first.
4. Treat #831 as the matched pre-diagnostic evidence. The #844 instrumentation has nonzero timing overhead, so use the new split primarily to locate the mechanism; do not present a small end-to-end FPS difference between #831 and #844 as a confirmed optimization.

Do not ask for another broad compatibility retest. One #844 stationary benchmark capture answers the specific remaining performance question.