# Unified GPU texture RD32 hardware qualification

This is the next owner-machine gate: RX 6900 XT / RADV, Forge 47.3.0,
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
