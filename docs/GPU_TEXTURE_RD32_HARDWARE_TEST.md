# O3/O4 RD32 hardware qualification

This is the next owner-machine gate: RX 6900 XT / RADV, Forge 47.3.0,
Create Chronicles and the existing selected packs. Use fully public-CI-green
`9d333487613e1a466e94f8562cb5ce5b403ad36c`, build **#1002**:
[build #1002](https://github.com/Vorith03/VulkanMod/actions/runs/37454136840).
Install its distributable artifact
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1002-g9d333487-all.jar`. Replace
the existing VulkanMod JAR; do not leave two versions installed.

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
- Check `settle_mode=terrain_quiet`, actual settling time and successful completion.
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
