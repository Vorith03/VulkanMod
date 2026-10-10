# O3/O4 RD32 hardware qualification

This is the next owner-machine gate: RX 6900 XT / RADV, Forge 47.3.0,
Create Chronicles and the existing selected packs. Use fully public-CI-green
`084dc8183ab6612134ddff762fac2ea9b56bf8fd`, build **#1006**:
[build #1006](https://github.com/Vorith03/VulkanMod/actions/runs/37997041921).
This artifact includes the now native-verified 2026-10-09 prebenchmark renderer
audit, including the legacy-FBO pixel/lifetime smoke. Install its distributable
artifact `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1006-g084dc818-all.jar`. Replace
the existing VulkanMod JAR; do not leave two versions installed.

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
   -Dvulkanmod.performanceProfiler.benchmarkSettleSeconds=60
   -Dvulkanmod.performanceProfiler.benchmarkQuietSeconds=10
   -Dvulkanmod.performanceProfiler.benchmarkMaxSettleSeconds=300
   -Dvulkanmod.performanceProfiler.durationSeconds=180
   -Dvulkanmod.gpuAnimatedTextureCopies=true
   -Dvulkanmod.gpuAnimatedTextureInterpolation=true
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

Return the newest `logs/vulkanmod-performance-benchmark-<UUID>.log` and
`logs/latest.log` from that instance. Also report any visibly frozen, incorrect
or flickering animations. No deferred reload/re-entry or unrelated experimental
terrain/Flywheel test is part of this capture.

## What the capture decides

- Confirm both offload request flags and enabled `shaderFloat64` provenance.
- Check `settle_mode=initial_population_stable`, actual settling time and successful completion.
  Examine terrain windows for any remaining population/churn before interpreting
  steady performance.
- Read client/texture tick mean and p95, allocations, sprite-upload work,
  tickable-loop and outer batch CPU costs. Do not identify all remaining
  non-upload time as Java clock iteration: GPU interpolation command preparation
  also lives outside `SpriteContents.upload`.
- Use measured sprite upload routes and standard distinct-frame interpolation
  routes for CPU/GPU coverage. Correlate CPU routes with request/capability,
  admission/rejection/mutation fields; disabled/custom paths are not GPU failures.
- Read `texture_upload_gpu` alongside main graphics, keeping their different
  timestamp scopes separate. Inspect unresolved/dropped/query-error counters;
  this whole-batch timer includes dependencies and is not isolated shader time.
- Inspect admitted source and allocated scratch payload peaks plus the combined
  residency budget. These lifetime counters include warmup and pending release.
- Use bounded particle class/provider/source timing/allocation/churn to select
  concrete adapters if particles remain the next significant transferable cost.
  Preserve the rejected Immersive Portals-conflicting render-hook boundary.

This first run establishes representative hardware correctness/coverage and
chooses the next GPU-offload target. The old fixed-settle RD32 run mixed initial
terrain population with measurement and cannot supply a whole-run steady-state
speedup comparison. Paired disabled/enabled captures with this same converged
settling policy remain required before adoption or a quantitative speedup claim.

## Later paired CPU pilot comparison

After collecting the controlled O3/O4 evidence above, the particle profiling
hook gate and topology-neutral terrain publication changes may be compared
against a qualified pre-pilot executable with exactly matched settings. The
older #988 benchmark is not a paired measurement; no FPS gain is asserted
from it. This pilot must first pass the full Forge/native/compatibility suite.
