# October 21 owner RX 6900 XT automated benchmarks — two configurations, one executable

## October 21 — texture and particle repairs internally qualified in full #1092

Final executable `272b7f81f09a05e4796e008d64a33c73a5270505`, full **#1092 /39716738464 SUCCESS**.
JAR12400315181,11,103,766bytes,SHA256
`2eedcf35295de61e4ecac2fad97d19b84c5813f2734838d7a8db884079fc6e88`; native logs12399703822.
Unsigned-limit correction and actual-uploader nonzero-window result passed native
oracles. Particles647 GPU quads/131 batches/2 nonzero windows; original CPU recovery
and pool retirement passed. Texture exact mip pixels/transformed CPU bypass,
shared resources and322+ accepted multi jobs passed. Independently checked18 exact
854x480 CPU/GPU world/portal phase pairs;0 Vulkan validation/synchronization errors
in14 top-level logs. All required public native/build/compatibility gates passed;
three private fixture steps skipped. Experiments remain OFF by default.
**Implemented and internally qualified; owner adoption/visuals/performance/release
remain OPEN.** The earlier signed-workgroup exclusion is superseded. Final evidence,
source-backed diagnosis, safety, exact artifact and minimal owner package:
[GPU_ADOPTION_REPAIRS_2026-10-21.md](GPU_ADOPTION_REPAIRS_2026-10-21.md).

## Evidence and scope

Owner-supplied full benchmark logs, latest.log, debug.log and JFR for two **completed** automated 180-second stationary runs on the **same** #1088 JAR `0.3.2-forge.2-build.1088-g95221419`, Java17, Forge47.3.0, Create Chronicles (~302 mods), Linux RADV RX6900XT. No CPU-allfalse same-build baseline, owner screenshots, gameplay-motion correctness, or portal scene has been supplied. User subjectively reported faster world loading and reduced cloud/day/night stutter in first partial-GPU run; not controlled measurements.

Capture IDs/files:
- Partial GPU, UUID `20c49c53-5e7c-468d-ab90-817dddc1daa6`, `vulkanmod-performance-benchmark-20c49c53-5e7c-468d-ab90-817dddc1daa6.log` + adjacent `.cpu-graphics-15274765674410075348.jfr`, latest/debug 20261021-2356.
- Integrated flags, UUID `221fd7a2-9d5b-4fe4-958b-0e13dac88d87`, `vulkanmod-performance-benchmark-221fd7a2-9d5b-4fe4-958b-0e13dac88d87.log` + adjacent `.cpu-graphics-14463888111330668847.jfr`, latest/debug 20261022-0030.

Both are `VulkanMod_Benchmark` overworld, spectator `0,192,0`, yaw -90/pitch30, `2552x1374` locked framebuffer, D32/S12, vsync false, cap260, settle actual 71.764s versus 71.700s, formal capture180.014s, identical mod build; GPU timestamp scopes explicitly separate main graphics and texture-upload batches. **Run A** properties mesher/cpu_bypass/draw_handoff/hybrid false, GPU resident/interpolation requested, admission/batching false. **Run B** all19 integrated properties true with CPU sprite interpolation and particle frustum fastpath retained. Each run includes heavy initial ~10sec post-convergence catchup; compare both full and post-30sec windows. Single trial per mode; no randomized/order-balanced repetitions. These are descriptive observations, not a proven causal gain or control-vs-GPU result.

## Quantitative comparison

| Window/metric | Partial A | Integrated B | Interpretation |
|---|---:|---:|---|
| 180s frames | 10545 | 12835 | +2290 frames, +21.7% |
| 180s FPS = frames/duration | 58.60 | 71.34 | +21.7% |
| final150s FPS, complete 5s windows 7..36 | 62.03 | 75.71 | +22.1% |
| final90s FPS, windows19..36 | 60.44 | 77.17 | +27.7% |
| frames exceeding 25ms | 3510/10545=33.29% | 3497/12835=27.25% | -6.04 percentage points; principally tick-bearing frames |
| average client tick, ms/tick | 24.521 | 23.795 | -2.96% |
| CPU world-render attribution, ms/call | 7.589 | 6.527 | -14.0% |
| CPU particle-render attribution, ms/call | 3.467 | 2.811 | -18.9%, GPU particle computation **not** adopted |
| CPU texture tick, ms/tick | 13.949 | 13.403 | -3.9%, still major bottleneck |
| sprite-upload CPU ms/tick | 9.313 | 8.864 | -4.8% |
| main-graphics GPU avg, ms/frame | 2.472 | 2.862 | +15.8%; more GPU work, not a GPU speedup |
| main-graphics GPU p95, ms/frame | 2.569 | 2.935 | separate main scope |
| texture-upload GPU avg, ms/batch | 2.163 | 2.164 | unchanged; **do not add** to per-frame time |
| sampled Java relevant graphics JFR events | 9990 | 10172 | statistical samples, not milliseconds |

Frame p95 in each 5s window is not a global merged percentile. Both runs retain tick spikes, especially during opening catchup. No numeric time-to-world-render measurement exists; initial load-speed impression is subjective.

## Actual ownership and adoption

**Terrain and commands genuinely engaged** in integrated B:
- `VULKANMOD_GPU_TERRAIN_CPU_BYPASS_ACTIVE`, `VULKANMOD_GPU_TERRAIN_MESHER_ACTIVE`, `VULKANMOD_GPU_TERRAIN_DRAW_HANDOFF_ACTIVE`, `VULKANMOD_GPU_COMMAND_OWNERSHIP_ACTIVE` all present.
- GPU-generated live-world Vulkan indirect recording witnessed for APPEND then REPLACE/APPEND, including an example `replace=2 appendPairs=10`; *example counts, not capture aggregates or pixel proof*.
- Run B debug's final diagnostic count `success/published/cpuBypassed=true=727` plus `published_atomic_append_rebuild=1`; worker fallbacks for unsupported models/lighting/budget. `gpuCmdBypass:66/66` appears in terrain lifetime snapshot; contextual quota, not percentage of all world draws.
- `dispatch/output_reservation_failed=10`, `recovery/cpu_rebuild_requested=19`; CPU recovery logged and no crash observed. Voxel staging `2048/2048` and lifetime rejects ~7472; sampled busy D32 scene and memory cap constrain GPU terrain coverage. Do not remove fail-closed guards.
- No user-side same-view screenshot pair, moving camera, dirty/portal/reload or long-play visual comparison yet; no release sign-off.

**Animated textures did not GPU interpolate:**
- Integrated B enabled admission and batch, `gpu_admission_selected_at_plan=1510`, `resident_admitted=1505`, GPU-resident copies `1182964/4586228` sprite upload requests (~25.8%), but `standard_interpolation_gpu_updates=0 / 2879091`, `resident_interpolation_dispatches=0`, `gpu_multi_dispatch_total=0`. `interpolation_fallback_dispatch_failed=2810697`; `interpolation_fallback_no_upload_batch=68394`. CPU `CpuSpriteInterpolation.tryInterpolate` remains a top sampled hotspot. **Enabled flags and admission are not actual interpolation replacement.** Source `SpriteAnimationState.tryGpuInterpolation` receives false from `GpuAnimatedTextureResidency.interpolateToAtlas`; determine exact first rejection site with bounded diagnostic or suitable existing evidence before speculative algorithm edits. Prior partial run also had GPU interpolation0 and admission off.

**Particles did not GPU expand:**
- Integrated B lifetime `exact_class_eligible=126249312`, `gpu_quads=0`, `cpu_recovery_quads=126249312`, `compute_batches=0`, `capacity_rejected_batches=15426`, `descriptor_rejected_batches=0`, `unavailable_checks=0`.
- `ParticleBillboardCompute.expand` rejects before dispatch on group-count or required vertex-buffer span exceeding `maxStorageBufferRange` / Java int capacity. Counter merges these two: **do not assume which guard**. Distinguish actual range versus workgroup limit and whether rejected buffer offset includes unrelated queued vertex data. Preserve fallback.

Other: user latest/debug for B contain no searched `Validation Error`/`SYNC-HAZARD` strings, but these files alone do not prove Vulkan validation was enabled. No fatal exception or benchmark abort; capture_complete benchmark_complete both.

## Disposition and bounded next actions

**Partially GPU-adopted, measured +21.7% across two configurations, NOT fully GPU-effective or qualified for default/release.** Correct previous stale `owner unmeasured` status. Do not attribute all throughput to GPU: no true CPU baseline and single trials. Open **concrete implementation investigation**, limited to (1) failed GPU interpolation after successful admission and (2) all-capacity-rejected GPU particle expansion; keep exact fallbacks and do not tweak unrelated subsystems. Terrain already has production owner witnesses; capacity/recovery are real but no rendered visual defect shown. Read relevant existing contracts and CI tests; add bounded reason telemetry only if source/instrumentation cannot distinguish preflight. Run focused native correctness/CI before issuing any changed owner build.

Once bounded defects are fixed and internally qualified, request **one** small matched owner check with exact changed JAR vs #1088 control, then same-JAR CPU-allfalse vs integrated19 benchmark for causal gain. Visual same-save screenshots and real-view motion/dirty/reload/portal remain unverified. Preserve original uploaded UUID/logs/JFR outside Git (do not commit private full logs), but these derived counters and provenance in Git. After each session, update AGENT_STATUS, roadmap/ledger, evidence and NEXT_SESSION_PROMPT and create a new complete rolling prompt.
