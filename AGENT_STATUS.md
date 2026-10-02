# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current build / executable state

- Latest CI-validated branch commit: `ebc70c95e9d1d31015802850799e5a8881042da3` (`ci: publish build jar directly`). This commit changes only CI artifact publication relative to the #932 profiler executable path; renderer/profiler source behavior is unchanged.
- CI **#933** / run `36966117197` is fully green.
- #933 direct distributable artifact: `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.933-gebc70c95-all.jar`, artifact ID `11209439135`, SHA-256 `1d99a0c5eef04c386a11c99ff2458bc635c725f123f5dcf5d62f668c1f916a19`.
- #933 smoke logs: `VulkanMod-Forge-smoke-log-933`, artifact ID `11209464027`, SHA-256 `542ad2384f7a2917acaed380c3ada96a544cdbbed8a5b922f3cc8af692eb1164`.
- `actions/upload-artifact@v7` with `archive: false` now publishes the single distributable as a direct `.jar` artifact instead of requiring the user to unpack the build artifact ZIP. Multi-file smoke/debug evidence remains archived normally.
- Latest RX 6900 XT / RADV hardware-validated executable behavior is build **#932**, commit `cd3dfe14bdfcfe93400b05c8811f5e15c73f3f0f` (`fix: repair Forge GPU pass profiler boundaries`). #933 does not alter that runtime code path.
- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.

## #932 RX 6900 XT benchmark — current hardware evidence

Build **#932** / executable commit `cd3dfe14...` completed the fixed automated stationary benchmark on the user's RX 6900 XT / RADV system with Create Chronicles.

Benchmark contract:

- fixed world/camera: `0,192,0`, yaw `-90`, pitch `30`
- framebuffer: `2552x1374`
- render distance 16 / simulation distance 12
- FPS cap 260, vsync off
- Distant Horizons rendering disabled
- System OpenAL off for continuity
- 60 s settle / 180 s measured capture
- GPU-terrain voxel staging / mesher / CPU bypass / draw handoff / hybrid enabled

The run completed cleanly for **180.003 s** with the camera unchanged and framebuffer stable.

### Coarse Vulkan GPU profiler is now RX-validated

The #932 repair succeeded completely on real hardware:

```text
[VulkanModPerf] benchmark gpu_timestamps requested=true active=true status=active scope=main_graphics_command_buffer includes_helper_submissions=false includes_present=false measured_frames=20405 sampled_frames=20405 sample_cap=65536 dropped_samples=0 read_failures=0 timestamp_valid_bits=64 timestamp_period_ns=10.000000 main_graphics_ms_avg=1.260 main_graphics_ms_p50=1.248 main_graphics_ms_p95=1.380 main_graphics_ms_p99=1.415 main_graphics_ms_max=1.476
```

```text
[VulkanModPerf] benchmark gpu_passes scope=main_graphics_command_buffer breakdown_frames=20405 invalid_frames=0 unaccounted_frames=0 missing_fixed_marker_frames=0 missing_world_begin_frames=0 missing_world_end_frames=0 missing_hud_begin_frames=0 missing_hud_end_frames=0 marker_order_failures=0 reconstruction_failures=0 terrain_incomplete_failures=0 terrain_containment_failures=0 terrain_segments=102025 terrain_segment_drops=0 max_terrain_segments_per_frame=32 pre_world_samples=20405 pre_world_ms_avg=0.010 pre_world_ms_p95=0.010 pre_world_ms_max=0.175 world_samples=20405 world_ms_avg=1.195 world_ms_p95=1.314 world_ms_max=1.415 terrain_samples=20405 terrain_ms_avg=0.932 terrain_ms_p95=1.056 terrain_ms_max=1.141 world_other_samples=20405 world_other_ms_avg=0.263 world_other_ms_p95=0.306 world_other_ms_max=0.448 between_world_hud_samples=20405 between_world_hud_ms_avg=0.002 between_world_hud_ms_p95=0.002 between_world_hud_ms_max=0.006 hud_samples=20405 hud_ms_avg=0.051 hud_ms_p95=0.053 hud_ms_max=0.072 tail_samples=20405 tail_ms_avg=0.002 tail_ms_p95=0.002 tail_ms_max=0.053
```

All **20,405** measured main-graphics frames produced accepted pass breakdowns. There were:

- 0 invalid frames
- 0 unaccounted frames
- 0 missing fixed/world/HUD markers
- 0 marker-order failures
- 0 reconstruction failures
- 0 terrain-incomplete failures
- 0 terrain-containment failures
- 0 timestamp read failures
- 0 dropped broad samples
- 0 terrain segment drops

`102025 / 20405 = exactly 5` timestamped terrain segments per measured frame, matching #931's observed terrain-marker cadence and proving the repaired fixed markers integrate correctly with the already-working terrain markers.

The coarse GPU split is therefore trustworthy for this benchmark. Average main-graphics GPU execution was **1.260 ms**:

- pre-world: **0.010 ms** (~0.8%)
- world total: **1.195 ms** (~94.8%)
  - terrain: **0.932 ms** (~74.0% of total main graphics)
  - world-other: **0.263 ms** (~20.9% of total main graphics)
- between world/HUD: **0.002 ms** (~0.2%)
- HUD: **0.051 ms** (~4.0%)
- tail: **0.002 ms** (~0.2%)

Do **not** interpret terrain's 74% share as evidence that terrain is the current performance bottleneck. The entire measured main-graphics command buffer is only ~1.26 ms average / 1.38 ms p95, far below the recurring CPU tick cost. A finer GPU probe is not justified by this capture unless later evidence changes that conclusion.

The #932 broad GPU average is lower than #931's 2.045 ms and its p95 is much lower than #931's 4.539 ms. Treat that delta as run-to-run/workload variation unless independently reproduced; the profiler repair itself was not a rendering optimization.

### CPU / texture result remains the dominant performance evidence

#932 measured:

```text
[VulkanModPerf] benchmark client_tick_breakdown ticks=3600 sampled_ticks=3600 sample_cap=8192 overlap_ticks=0 tick_ms_avg=26.085 tick_ms_p95=31.597 tick_ms_max=51.573 leaf_ms_avg=21.883 leaf_ms_p95=27.030 leaf_ms_max=46.592 leaf_allocation_available=true
```

```text
[VulkanModPerf] benchmark client_tick_texture_detail ticks=3600 active_upload_ticks=3598 overlap_ticks=0 sprite_upload_calls=4715367 sub_upload_calls=23276451 sprite_calls_per_tick=1309.824 sub_upload_calls_per_tick=6465.681 texture_ms_avg=18.542 sprite_upload_ms_avg=11.232 sprite_upload_ms_p95=14.763 sprite_upload_ms_max=28.390 non_upload_ms_avg=7.310 non_upload_ms_p95=9.717
```

The result reproduces #931 closely rather than overturning it:

- client tick average: **26.085 ms** (#931: 26.807 ms)
- client tick p95: **31.597 ms** (#931: 32.225 ms)
- leaf total average: **21.883 ms** (#931: 22.600 ms)
- texture tick average: **18.542 ms** (#931: 19.095 ms)
- complete sprite-upload body average: **11.232 ms** (#931: 11.580 ms)
- non-upload texture remainder average: **7.310 ms** (#931: 7.515 ms)
- sprite calls/tick: **1309.824** (#931: 1310.503)
- sub-upload calls/tick: **6465.681** (#931: 6469.046)

The call rates differ by only about 0.05% from #931, while CPU/texture timings are within roughly 2–3%. This is strong reproducibility evidence that the sustained hitch is primarily client-tick texture work, not main-graphics GPU execution.

Texture work remains split materially between:

1. `SpriteContents.upload()` bodies (~11.23 ms average), and
2. same-tick non-upload texture work (~7.31 ms average).

Do not optimize only one side and assume the other is negligible.

## #931 profiler failure — settled historical context

#931's broad Vulkan timestamps were valid, but its coarse `gpu_passes` aggregation reported `breakdown_frames=0 invalid_frames=0` despite exactly five terrain segments per frame.

Root cause is settled:

1. HUD GPU boundaries were injected into vanilla `Gui.render()` HEAD/RETURN.
2. Forge 1.20.1 uses `ForgeGui.render(...)`, a full override, so those base-method injections never executed on real Forge frames.
3. Fallback query writes preserved contiguous broad timestamp readback but did not set HUD presence bits.
4. The decoder skipped incomplete fixed-marker frames before incrementing `invalid_frames`, silently producing zero accepted and zero rejected frames.

`cd3dfe14...` repaired the semantic boundary by bracketing the virtual `GameRenderer -> Gui.render` invocation, made missing markers explicit invalid reasons, added `unaccounted_frames`, and tightened terrain containment. #932 hardware evidence now proves that repair works as designed. Do not reopen this investigation unless new evidence contradicts it.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**, current useful priority. Formal comparable OpenGL/Vulkan baseline and hitch/frame-time evidence remain open under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid: **6/11** and paused for Phase 5 measurement priority. Do not reopen unrelated Phase 7 optimization work while resolving benchmark evidence.
- Immersive Portals current compatibility behavior is RX-confirmed working. Preserve its documented semantic boundary unless new evidence contradicts it.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from this profiler/performance work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Next useful action

The coarse GPU-profiler validation gate is satisfied. Do **not** spend the next pass adding finer GPU timestamps merely because terrain is the largest GPU bucket; total main-graphics GPU time is too small to explain the observed hitch.

Resume evidence-driven Phase 5 performance work on the **client-tick texture path**. Start from the reproducible #931/#932 evidence:

- ~1,310 `SpriteContents.upload()` calls/tick
- ~6,466 sub-upload calls/tick
- ~11.23 ms/tick inside complete sprite-upload bodies
- ~7.31 ms/tick in the same texture tick outside those bodies
- ~18.54 ms/tick total texture work versus ~26.09 ms total client tick

Investigate the concrete call path and ownership causing that repeated texture activity before changing behavior. Prefer eliminating redundant work, batching/coalescing safe repeated operations, or reducing repeated per-call overhead while preserving texture animation/update semantics and mod compatibility. Keep upload-body and non-upload costs separately measurable so any optimization can be attributed rather than guessed.

Any implementation should be validated with focused tests/CI first, then the same fixed RX benchmark only when a hardware run is necessary to quantify the effect.
