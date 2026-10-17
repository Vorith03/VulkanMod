# Unified GPU texture RD32 hardware qualification

## Bulk CPU interpolation comparison (await full/native qualification)

The owner #1021 UUID `f4741fc5` capture is received. It shows cached terrain
adoption (439 traversals/6,457 valid frames, 785 cached rebuilds, 1,604 retained
notices); do not repeat that unchanged candidate. See
[analysis and fallback implementation](RX_RD32_CAPTURE_1021_AND_CPU_INTERPOLATION_2026-10-17.md).

Candidate executable `b3625355238f98fe9d5402dfc692e19a819f2d10`, build #1022,
adds an opt-in bulk version of the standard CPU interpolation fallback. Full
Forge/native qualification is **pending**; do not use an unqualified artifact.
After all gates pass, use one copy of the qualified JAR and preserve the #1021
settings, texture pilot flags, fixed framebuffer/camera and 10 ms JFR sampler.
Only change this flag between control and candidate:

```text
-Dvulkanmod.experimentalCpuSpriteInterpolation=false
-Dvulkanmod.experimentalCpuSpriteInterpolation=true
```

Use **one line per launch**, not both. A control/candidate pair distinguishes
whole-texture CPU cost from changed-world/workload differences better than a
candidate-only historical comparison. For formal benefit qualification, restore
the same world snapshot before each launch and run three alternating pairs.
A single candidate-only capture can establish adoption, not matched benefit.

Return each UUID benchmark log and neighboring raw JFR plus latest/debug and
any wrong/stale/flickering animation observations. Read
`standard_interpolation_cpu_bulk_updates / standard_interpolation_cpu_updates`
for adoption; the numerator is a subset, not an additional route. Compare whole
texture/client-tick and frame distributions, CPU interpolation stacks and
separate upload GPU time. GPU first refusal, budgets/admission and the transfer
path are unchanged. No new terrain flags or reload/re-entry tests are required.

The following #1021 request is historical and superseded by capture receipt.

## Current gate: #1021 terrain backlog repair

The first #1020 source-grouping capture is **received** (UUID `e12d7c14`).
Do not repeat that unchanged build. Analysis and the new bounded terrain repair:
[measured evidence and regression](RX_RD32_CAPTURE_1020_AND_TERRAIN_REPAIR_2026-10-17.md).

Executable `8ff928dd02deb6ad2d9098210a82fa23bfdbf5a6` passed full
[CI #1021](https://github.com/Vorith03/VulkanMod/actions/runs/39070465187), job
`117507019602`: all 22 contracts, Forge production packaging, both startup modes,
indirect/post/depth/screenshot pixels and synchronization, public Create Chronicles
and Crash Assistant. Private packs skipped; RX benefit remains unmeasured.
Download [artifact 12115062566](https://github.com/Vorith03/VulkanMod/actions/runs/39070465187/artifacts/12115062566):
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1021-g8ff928dd-all.jar`.
Replace the old VulkanMod JAR; install only one version.

Run the same automated benchmark **once**, preserving the #1020 flags, both texture
pilots enabled, 10 ms JFR sampler, GPU timestamps, RD32/simulation 12, 2552x1374,
selected packs/world and fixed camera. No new JVM flag is needed. Keep the game
focused until normal automatic save/exit. Return the UUID benchmark log, neighboring
JFR and latest.log (debug.log if errors), plus any missing/stale terrain or animation
observations. Do not add reload/re-entry or new experimental terrain options.

Decision: in baseline-valid stationary windows, does `graph_traversals_delta`
fall below the frame count, while pending dirty notices remain retained and
`cached_graph_schedules_delta` admits eligible maintenance? Compare terrain setup,
full tick/frame and texture times. Unready edge work may remain at 1,604 and cached
schedules may stay low if no work becomes eligible; queue disappearance is not a
success criterion. Other topology invalidations can still require a full traversal.
This single capture qualifies adoption/screening, not a formal matched-world speedup.
For that claim use restored-world #1020/#1021 pairs with identical settings; three
alternating pairs are required by the existing policy.

The sections below retain historical texture-pilot contracts; their old first-
capture requests are superseded by this section.

## October 17 source-grouping repair comparison

Implementation: `e0807c626bfa27416ebadc53eddff519f9ac29ef`, full CI
[#1020](https://github.com/Vorith03/VulkanMod/actions/runs/39038204099).
Full public/native job `117412382928` passed all contracts, Forge packaging,
renderer/pixel/sync and public compatibility gates. Private packs skipped.
Use artifact [12103455469](https://github.com/Vorith03/VulkanMod/actions/runs/39038204099/artifacts/12103455469),
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1020-ge0807c62-all.jar`. Replace the
existing VulkanMod JAR; do not install both versions together. The executable repairs source-buffer-switch fragmentation;
see [cause, proof and boundaries](TEXTURE_SOURCE_GROUPING_REPAIR_2026-10-17.md).
Qualified distributable version: `0.3.2-forge.2-build.1020-ge0807c62`.

For the first candidate capture, retain the exact #1019 RD32 settings below,
including both texture pilots **enabled**, GPU timestamps and the 10ms JFR
sampler. Match the recorded 2552x1374 framebuffer, simulation distance 12 and
fixed camera. Return the UUID log, neighboring JFR and latest.log, and report
stale/flickering/wrong animations. The discriminators are zero source-switch
flushes in unified mode, emitted `copy_commands` relative to `copy_regions`,
texture/client-tick CPU time, upload GPU time, and unchanged animation cadence.
An increase in necessary overlap flushes is possible: these now see all pending
sources, rather than just the last source group.

For an actual net-benefit claim, compare #1019 against #1020 on identically
restored world snapshots with **both pilots enabled in both**, preserving all
other executable settings. One pair screens; three alternating matched pairs
qualify a formal claim. Pilot-off changes multiple mechanisms and is not a
repair-only control. The existing #1019 attachment establishes the mechanism,
not a time-matched control for a later evolving world. A new candidate-only
capture can verify adoption without being called a measured speedup.

## Historical #1019 capture contract

This was the #1019 owner-machine gate: RX 6900 XT / RADV, Forge 47.3.0,
Create Chronicles and the existing selected packs. Candidate executable
`ce9d66e1219e13139e3b0b6b7de531082f7a04c7`, build **#1019**:
[build #1019](https://github.com/Vorith03/VulkanMod/actions/runs/38972838076).
Full public/native job `117215415389` passed all 22 offline contracts, Forge
packaging, native pixel/synchronization gates, real Forge JFR activation/dump,
combined Create Chronicles and Crash Assistant. Log/JAR uploads succeeded;
private real-pack fixtures were skipped. Install the qualified distributable
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1019-gce9d66e1-all.jar`.
Replace the existing VulkanMod JAR; do not leave two versions installed.

The candidate retains the CPU/terrain work already exercised by the owner's
completed #1014 capture and adds the #1016 original-source identity repair,
explicit fallback/flush attribution, shared immutable GPU source pages, and
one bounded queue for CPU-staged and GPU-resident atlas copies. It also adds
automatic CPU graphics JFR discovery: sampled render/mod/worker call chains,
ranked categories and an accompanying raw JFR. Conservative
transfer dependencies cover capacity/direct writes and both O3-to-O4 and
O4-to-O3 ordering. O3/O4 and the two pilots remain opt-in; GPU compute dispatch
coalescing is not implemented. Native correctness does not establish hardware
adoption, convergence, or speedup, and does not qualify deferred reload/re-entry.

The owner's #1014 capture completed successfully and is the existing historical
evidence, not a test to repeat with #1014. Its CPU-only copy-flush counters are
not directly comparable to the new pilot's mixed CPU/GPU transfer denominator.

## Failed #1002 attempt and retry

The owner reported no benchmark HUD. The supplied `latest(6).log` and
`debug(3).log` prove automation did start warming at **2026-10-06 19:30:50.905**,
then aborted at **19:35:50.935** because terrain never met the 10-second quiet
condition before the 300-second deadline. Last sample: scheduled 28419,
published 28371, non-empty 6559. No capture started, so this attempt supplies no
O3/O4 hardware performance or route-coverage evidence. These cumulative counts
do not identify whether the final blocker was workers, queues or recurring work.
World-generation/server backlog appears in the same log but is not sufficient
to attribute the last failed quiet sample.

The status drawing was still hooked into vanilla Gui.render, which ForgeGui
fully overrides. The fix uses the pinned Forge 47.3.0 RegisterGuiOverlaysEvent
API, preserves the keyboard profiler overlay, and adds bounded settling logs
and visible counters/abort reason. A focused callback contract exercises warmup,
timeout, hidden/menu/disabled guards and the profiler toggle. Build #1003 passed the
new Forge HUD contract, Java/Forge compilation, packaged startup, native
validation animation/screenshot tests, Create Chronicles, Crash Assistant and
JAR/log uploads. Private pack fixtures skipped. Visible HUD and representative
convergence/offload measurement still require the owner-machine retry.

The subsequent #1003 attempt also timed out at 900 seconds, despite a constant
6,559 non-empty render-graph count from about 80 seconds onward. It revealed
recurrent scheduling/publication after queues had drained. The zero-all-work
rule is superseded by initial-population ownership; see
BENCHMARK_TERRAIN_CONVERGENCE_2026-10-07.md. This still blocks queued/building/
publishing initial sections and rejects resumed population during capture, while
including normal already-compiled-section updates in the measured workload.
The corrected candidate #1005 is fully public-CI green (run `37600141289`,
job `112722310635`), including the new initial ownership/HUD contracts, Java/Forge
compilation, packaged startup, native Vulkan validation, Create Chronicles,
Crash Assistant and JAR/log upload. Private packs skipped. Owner-world convergence
and O3/O4 performance still require the new capture. Maximum settle returns to
300 seconds; 60-second minimum and
10-second stability window stay unchanged. Do not rerun #1003 or extend its timeout.

Evidence SHA256:
- latest(6).log: `5200be84c39dab0e9fcfa99e835f404b0fd28294668d15ae53c3fde3687269c7`
- debug(3).log: `b52980722f6a0b8c5970f6b6986bc0eb506b1122afa718ab022ee854c16b413d`

## One controlled capture

1. Keep the previous RD32 stationary workload: the same world copy, modpack,
   selected packs/order, framebuffer, simulation distance and graphics settings.
   **Render distance stays 32**, Max Framerate Unlimited and VSync off. Keep
   Embeddium/Oculus excluded and DH rendering disabled. Preserve texture-usage
   gating and other settings; do not add unrelated experimental terrain,
   indirect/hybrid, Flywheel or prewarming options for this run.
2. Add/update these JVM arguments in Prism (remove conflicting duplicate values):

   ```text
   -Dvulkanmod.performanceProfiler=true
   -Dvulkanmod.performanceProfiler.autoBenchmark=true
   -Dvulkanmod.performanceProfiler.gpuTimestamps=true
   -Dvulkanmod.performanceProfiler.cpuGraphicsSamples=true
   -Dvulkanmod.performanceProfiler.cpuGraphicsPeriodMs=10
   -Dvulkanmod.performanceProfiler.benchmarkSettleSeconds=60
   -Dvulkanmod.performanceProfiler.benchmarkQuietSeconds=10
   -Dvulkanmod.performanceProfiler.benchmarkMaxSettleSeconds=300
   -Dvulkanmod.performanceProfiler.durationSeconds=180
   -Dvulkanmod.gpuAnimatedTextureCopies=true
   -Dvulkanmod.gpuAnimatedTextureInterpolation=true
   -Dvulkanmod.experimentalUnifiedTextureUploads=true
   -Dvulkanmod.experimentalGpuTextureSourcePool=true
   ```

   Keep the existing benchmark-world override if the world is not named
   `VulkanMod Benchmark`. Otherwise the default named-world/Overworld camera is
   `0 192 0`, yaw `-90`, pitch `30`. Use a copied benchmark world because the
   automated run saves Spectator mode and the teleport.
3. Load that world and keep the game focused. Do not move, rotate the camera,
   resize, open F3/screens or change settings. The HUD handles placement,
   minimum warmup, terrain convergence and 180-second measurement. A successful
   run saves and exits automatically. If convergence has not been reached after
   300 seconds, or another guard aborts, the game remains open; report the abort
   rather than changing the workload to force a result.

Return the newest `logs/vulkanmod-performance-benchmark-<UUID>.log`, its
neighboring `*.cpu-graphics-*.jfr` file, and `logs/latest.log` from that instance.
The log names its JFR in `cpu_graphics_recording raw_file`; JFR retains a rolling
64 MiB of chunks, with possible chunk-sized overshoot. Also report any visibly frozen, incorrect
or flickering animations. No deferred reload/re-entry or unrelated experimental
terrain/Flywheel test is part of this capture.

## What the capture decides

- Confirm both offload request flags and enabled `shaderFloat64` provenance.
- Check `settle_mode=initial_population_stable`, actual settling time and successful completion.
  Examine terrain windows for any remaining population/churn before interpreting
  steady performance.
- Use `terrain_window`'s `graph_traversals_delta`,
  `cached_graph_schedules_delta`, `pending_dirty`, worker/queue and publication
  counters to distinguish traversal invalidation from cached rebuild admission
  and backlog. Only interpret deltas when `delta_valid=true`; a shrinking pending
  queue is expected when capacity or neighbors recover. Frequent full traversals
  require their measured context, not an assumed hardware speedup.
- Confirm `cpu_graphics_recording requested=true status=active`, a final
  `cpu_graphics_samples status=complete`, and a nonempty raw JFR. Read ranked
  render/graphics-worker stacks, mod callers, unclassified render work and
  overflow/missing/truncated samples. Java execution and native-boundary
  samples have separate denominators and are not exact per-method timers or
  GPU execution. This broad census chooses where existing phase/route timing
  needs a closer look; it does not prove all sampled CPU work can move to GPU.
- Read client/texture tick mean and p95, allocations, sprite-upload work,
  tickable-loop and outer batch CPU costs. Do not identify all remaining
  non-upload time as Java clock iteration: GPU interpolation command preparation
  also lives outside `SpriteContents.upload`.
- Use source-mip versus temporary-mip upload routes and standard distinct-frame
  interpolation GPU/CPU routes for coverage. Read the disjoint first-failure
  interpolation buckets and copy-flush causes before selecting another change.
  Disabled/custom CPU paths are not GPU failures.
- Confirm `gpu_source_pool_enabled=true` and inspect its page/lease/capacity and
  retirement counters. Check `resident_queued_copy_calls/regions` against
  `resident_direct_copy_calls/regions`; the mixed `copy_flushes/regions` count
  describes emitted Vulkan groups, while queued calls describe input requests.
- Read `texture_upload_gpu` alongside main graphics, keeping their different
  timestamp scopes separate. Inspect unresolved/dropped/query-error counters;
  this whole-batch timer includes dependencies and is not isolated shader time.
- Inspect admitted source and allocated scratch payload peaks plus the combined
  residency budget. These lifetime counters include warmup and pending release.
- Use bounded particle class/provider/source timing/allocation/churn to select
  concrete adapters if particles remain the next significant transferable cost.
  Preserve the rejected Immersive Portals-conflicting render-hook boundary.

This run establishes the new pilot's representative hardware correctness and
coverage. For its effectiveness comparison, use this same JAR and O3/O4 settings
for a control capture with only these two arguments changed to `false`:

```text
-Dvulkanmod.experimentalUnifiedTextureUploads=false
-Dvulkanmod.experimentalGpuTextureSourcePool=false
```

Keep convergence, world, packs, settings and profiler flags identical, including
CPU graphics sampling and its period in both variants. Sampling has observer
cost; #1018 cannot substitute for a same-JAR observer-matched control. Compare
CPU texture/client tick and frame times, GPU main-frame and separate upload-batch
times, route coverage, flush causes, and memory/capacity counters. A correctness
pass or one unusually fast run is not adoption evidence; the Phase 5 performance
claim gate remains three matched pairs. Existing #1014 data supplies historical
context, not a same-executable pilot control.

## Later paired CPU pilot comparison

After collecting the controlled O3/O4 evidence above, the completed particle
and terrain CPU pilot may be compared against a qualified pre-pilot executable
with exactly matched settings and the same initial-population settling policy.
The older #988 benchmark is not a paired measurement; no FPS gain is asserted
from it. Full Forge/native/compatibility qualification is required before this
hardware test; private-pack CI coverage, when skipped, remains unestablished.
