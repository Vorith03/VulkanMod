# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current executable

- Latest CI-validated executable commit: `cd3dfe14bdfcfe93400b05c8811f5e15c73f3f0f` (`fix: repair Forge GPU pass profiler boundaries`).
- CI **#932** / run `36948456198` is fully green.
- Build artifact: `VulkanMod-Forge-build-932`, artifact ID `11203082697`, SHA-256 `bc671f71a8be641c4d2ed04393a8878575e224f2f37812976c0085b3d608f9d9`.
- Smoke logs: `VulkanMod-Forge-smoke-log-932`, artifact ID `11203132489`, SHA-256 `5ce928b015e824635a8ce95b068bc12eaa1cccb303d9c103b6fd1123cf200a7c`.
- #932 passed the GPU-profiler hook contract, full Gradle/distributable verification, packaged Immersive Portals anchors, both Forge startup modes, persistent GPU indirect, post-chain/depth-post-chain, screenshot readback, Create Chronicles compatibility, and Crash Assistant.
- The ordinary Lavapipe startup smoke logged `VULKANMOD_GPU_TIMESTAMP_CONTRACT_OK` and a real `VULKANMOD_GPU_TIMESTAMP_SMOKE_OK scope=main_graphics_command_buffer main_graphics_ms=64.606`, proving query-pool creation, real timestamp recording, graphics submission, existing-fence retirement and query readback still work after the repair.
- The ordinary CI startup fixture does not enter a rendered world, so it cannot honestly prove an accepted `gpu_passes` world partition. Do not represent its broad timestamp smoke as hardware/world-pass validation. Deterministic decoder tests cover the coarse-pass validity logic; the next RX benchmark is the representative runtime proof.
- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.

## #931 RX 6900 XT benchmark — latest hardware evidence

The latest completed hardware capture is build **#931** / executable commit `2b6e1ac915965069b63b776b342313993b18fe5f` on the user's RX 6900 XT / RADV system with Create Chronicles. It used the fixed automated stationary benchmark at `0,192,0`, yaw `-90`, pitch `30`, 2552x1374 framebuffer, render distance 16, simulation distance 12, 260 FPS cap, 60 s settle and 180 s measured capture. Distant Horizons rendering was disabled and System OpenAL remained off for continuity with the benchmark series. The run completed cleanly.

### CPU / texture result

- `client_tick_breakdown`: 3,600/3,600 ticks sampled, 0 overlap ticks, client tick **26.807 ms avg / 32.225 ms p95 / 54.110 ms max**; leaf total **22.600 ms avg / 27.644 ms p95 / 49.572 ms max**.
- `client_tick_texture_detail`: texture tick **19.095 ms avg**.
- Complete `SpriteContents.upload()` bodies account for **11.580 ms avg / 14.994 ms p95 / 32.903 ms max**.
- Same-tick non-upload texture remainder is still substantial at **7.515 ms avg / 9.984 ms p95**.
- 4,717,809 sprite-upload calls and 23,288,565 sub-upload calls were observed over the capture: about **1,310.503 sprite calls/tick** and **6,469.046 sub-upload calls/tick**.

This preserves the earlier conclusion that the sustained hitch is primarily client-tick/texture work. Both upload-body work and non-upload texture work are material; do not collapse the texture problem to only one side without further evidence.

### Broad GPU result is valid

The independently measured Vulkan timestamp system worked correctly on RADV:

```text
[VulkanModPerf] benchmark gpu_timestamps requested=true active=true status=active scope=main_graphics_command_buffer includes_helper_submissions=false includes_present=false measured_frames=20641 sampled_frames=20641 sample_cap=65536 dropped_samples=0 read_failures=0 timestamp_valid_bits=64 timestamp_period_ns=10.000000 main_graphics_ms_avg=2.045 main_graphics_ms_p50=1.311 main_graphics_ms_p95=4.539 main_graphics_ms_p99=4.780 main_graphics_ms_max=5.749
```

Interpret this narrowly: main-graphics GPU execution averaged only **2.045 ms** and p95 was **4.539 ms**, while the recurring slow frames remain tied to client ticks. Do not reopen a broad “GPU is the main bottleneck” hypothesis from this run. Helper/upload submissions and presentation remain outside this GPU scope.

### #931 coarse `gpu_passes` result is invalid due to a profiler bug

#931 reported:

```text
breakdown_frames=0 invalid_frames=0 terrain_segments=103205 terrain_segment_drops=0
```

and every fixed pass bucket had zero samples. `103205 / 20641 = 5` terrain segments per measured frame exactly, with zero terrain drops, proving the terrain marker path was active consistently.

The failure mechanism is now established:

1. #931 placed HUD GPU boundaries at `Gui.render()` HEAD/RETURN.
2. Forge 1.20.1 installs `ForgeGui`, whose `render(...)` fully overrides vanilla `Gui.render(...)`; those base-method injections therefore never executed on real Forge frames.
3. `HUD_BEGIN`/`HUD_END` mask bits were absent every frame. `endFrame()` correctly wrote fallback query values so contiguous query readback remained valid, which is why the broad GPU measurement stayed healthy.
4. The coarse decoder only incremented `invalid_frames` after confirming the complete fixed-boundary mask. Missing-marker frames were silently skipped, producing the misleading `breakdown_frames=0 invalid_frames=0` combination.

## #932 coarse GPU profiler repair

`cd3dfe14...` makes the smallest semantic repair:

- HUD CPU/GPU timing now brackets the `GameRenderer -> Gui.render` invocation, so virtual dispatch covers both vanilla `Gui` and Forge `ForgeGui`.
- Base `Gui.render` no longer owns the HUD GPU boundary.
- The outer `GameRenderer.renderLevel` recursion guard is unchanged, so recursive Immersive Portals rendering remains inside the outer `world` interval.
- Terrain remains capped at 32 timestamped `WorldRenderer.renderSectionLayer` segments per frame.
- Missing fixed markers are now explicit invalid frames rather than silent omissions.
- `gpu_passes` now reports `unaccounted_frames` plus reason counters for missing fixed/world/HUD markers, marker order, reconstruction, terrain incompleteness and terrain containment.
- Terrain segment timestamps are explicitly required to lie inside the outer `world` interval before acceptance.
- The broad main-graphics sample remains independently accepted even when detailed pass decoding is invalid.
- Frame-slot/fence-based asynchronous retirement is unchanged; no steady-state synchronous GPU wait or `vkDeviceWaitIdle()` was added.

Regression coverage now checks exact query layout, capture-state behavior, valid-bit wrap arithmetic, complete synthetic fixed-pass durations, the #931 missing-HUD case and reason accounting, out-of-order markers, reconstruction failure, terrain incompleteness, terrain containment and terrain aggregation. CI also statically enforces the Forge-safe HUD call-site hook.

A future run should never again produce measured coarse frames with `breakdown_frames=0 invalid_frames=0` and no explanation. `unaccounted_frames` exists as an additional invariant check.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**, current useful priority. Formal comparable OpenGL/Vulkan baseline and hitch/frame-time evidence remain open under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid: **6/11** and paused for Phase 5 measurement priority. Do not reopen unrelated Phase 7 optimization work while resolving benchmark evidence.
- Immersive Portals current compatibility behavior is RX-confirmed working. Preserve its documented semantic boundary unless new evidence contradicts it.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from this profiler work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Next useful action

Run **build #932** on the user's RX 6900 XT/Create Chronicles environment using the same automated stationary benchmark as #931. This is a narrow profiler-validation/performance capture, not a broad compatibility retest.

Use the same benchmark JVM properties and settings as #931, including:

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

Keep 2552x1374, render distance 16, simulation distance 12, 260 FPS cap, Distant Horizons rendering disabled, and System OpenAL off for direct continuity. Do not set an explicit profiler output path; return the generated `logs/vulkanmod-performance-benchmark-*.log`.

Success criteria for the repaired coarse profiler:

1. `benchmark gpu_timestamps` remains active with real samples and no new read failures/drops.
2. `benchmark gpu_passes` has `breakdown_frames > 0` and nonzero fixed-pass sample counts.
3. `unaccounted_frames=0`.
4. Any rejected detailed frames are fully explained by the explicit reason counters; ideally all reason counters are zero in the stationary workload.
5. Terrain segment count remains consistent with the rendered terrain layers and `terrain_segment_drops=0` unless a real >32-segment workload occurs.
6. Pass partition/reconstruction and terrain-containment validation remain strict; do not weaken them merely to obtain samples.

After that capture, use the pass breakdown only to decide whether any finer GPU investigation is justified. Given #931's 2.045 ms main-graphics average versus ~26.8 ms tick frames and ~19.1 ms texture tick, CPU/texture work remains the evidence-backed performance priority unless #932 contradicts it.
