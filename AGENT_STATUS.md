# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical implementation detail belongs in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Repository state

- Current executable/test candidate is `90a9c6498853e3f6cae0b64a554b996455a16199` (`docs: document dedicated performance capture output`). The executable profiler-output change immediately underneath is `2d91a085d3d54cc60cda4c68165c157d0b781ff7` (`feat: write performance profiling to dedicated capture file`). The initial critical-path implementation is `3d28f5076a69ec52ea208d4bfb11f6ff3f93cda6` with the terrain statistics API correction in `5c06b5f9a6b08c77fd8856d3d84834d61e0e86d6`.
- Public CI **#805** / run `36529808812` is fully green. Build/distributable, packaged Immersive Portals anchors, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, exact Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-805` (artifact `11015748642`, SHA-256 `e4826efea983ea5876d07b1c61690d902aea05700c5f7c947ada7953cb67c8db`).
- The adversarial audit remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence.

## Active sequencing

- Live Phase 4 evidence is **7/8**. The only remaining mandatory Phase 4 gate is world enter/leave/re-enter plus resource reload. The user explicitly does **not** want that work prioritized now; treat the lifecycle gate as deferred rather than as a reason to keep retesting already-working visual paths.
- Phase 5 measurement work is **4/7** and is the current useful priority. Open numeric gates are the RX 6900 XT OpenGL baseline, Vulkan baseline, and hitch/frame-time evidence under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid work remains **6/11**. The first substantial open implementation gate there is correct GPU visibility/section selection, but performance claims must still use comparable Phase 5 evidence.
- `ROADMAP.md` still contains stale Phase 4 5/8 prose/checkmarks from before the #801 RX confirmation. Until reconciled, use the gate definitions/order there but use this checkpoint plus current runtime evidence for the live 7/8 count and user priority override.

## Current RX 6900 XT evidence — build #801

The user's 2026-09-28 Create Chronicles run established the current visual compatibility baseline:

- Immersive Portals portal rendering and traversal work correctly, including remote-world framebuffer composition attached/clipped to the portal.
- Creative inventory block/item imagery renders correctly.
- Third-person player and ordinary entity rendering work correctly.
- Create/Flywheel ordinary gameplay and moving contraption rendering work correctly.
- Representative particles/translucency/entities/GUI paths work correctly.
- Do not ask the user to repeat those checks unless a later executable change directly threatens them.

The same #801 session independently reproduced a shutdown/native-lifetime abort after normal Minecraft shutdown (`double free or corruption` / exit 134). Keep that separate from the now-working rendering paths. Do not make speculative native-ownership changes without focused ownership evidence or a native backtrace.

## Performance / critical-path instrumentation

The reusable opt-in profiling layer is documented in `docs/PERFORMANCE_PROFILING.md`. `2d91a085` moves profiler summaries out of the normal Forge/Minecraft logger and into a dedicated capture file while preserving the low-overhead timing contract.

Enable it for a Vulkan diagnostic run with:

```text
-Dvulkanmod.performanceProfiler=true
```

Default output, relative to the Minecraft game directory:

```text
logs/vulkanmod-performance.log
```

The ordinary console/log receives only a one-time notice that profiling is enabled and the resolved output path. Periodic `[VulkanModPerf]` summaries are written to the dedicated file instead of cluttering `latest.log`. Output failures disable profiling and report the failure through the normal logger rather than risking gameplay stability.

Optional controls:

```text
-Dvulkanmod.performanceProfiler.summarySeconds=5
-Dvulkanmod.performanceProfiler.durationSeconds=60
-Dvulkanmod.performanceProfiler.output=logs/vulkanmod-performance-run-a.log
-Dvulkanmod.performanceProfiler.slowFrameMs=25
-Dvulkanmod.performanceProfiler.maxSamples=4096
```

`summarySeconds` controls the summary-window period; default is 5 seconds. `durationSeconds=0` means unlimited; a positive value emits a final partial summary, writes `capture_complete`, closes the capture file, and makes the profiler inactive when the requested duration elapses. `output` can be a game-directory-relative or absolute path, allowing benchmark automation to preserve separate captures.

Current instrumentation contract:

- whole-`Minecraft.runTick()` CPU wall-time distribution: average, p50, p95, p99, max, and slow-frame count;
- render-thread stage timing for frame-slot recycling wait, frame-fence/recreation work, frame bookkeeping, terrain setup/culling, camera-region reposition, terrain publication/uploads, and final submit/present;
- nested reposition time is reported but not double-counted in top-level accounted time;
- an explicit `unaccounted` bucket exposes missing stage coverage instead of falsely attributing it;
- worst-frame stage attribution is retained per summary window;
- JVM GC count/time deltas and heap use are sampled only at summary boundaries;
- existing terrain region-batch and task-dispatch queue/build/handoff/publication statistics are emitted beside the timing window;
- the old `Profiler2` allocation-heavy timing tree now runs only while its Alt+F8 overlay is visible instead of allocating every normal frame.

The profiler is disabled by default. Its hot enabled path uses fixed primitive sample buffers and `System.nanoTime()`; percentile sorting, string formatting, JVM telemetry, and file I/O occur only at summary boundaries.

This is intentionally a **CPU wall-clock critical-path layer**, not a claim of GPU execution timing. Vulkan GPU timestamps are feasible: device `timestampPeriod` is available and frame fences provide a non-blocking completed-slot readback boundary. Add a query-ring layer only when a capture shows GPU/pass timing is the next missing discriminator rather than blanket-instrumenting every pass preemptively.

## Immersive Portals retained compatibility boundary

The IP 3.0.7 investigation is closed on current hardware evidence. Retain these semantic boundaries unless new evidence contradicts them:

- cancel IP's obsolete vanilla `earlyRemoteUpload()` terrain prepass;
- remove only the stale merged vanilla terrain-camera dispatcher dependency;
- keep the geometry callback while bypassing unsupported OpenGL query-result handling;
- use VulkanMod's generic sampled `RenderTarget` path for IP's secondary framebuffer;
- support depth clamp through the Vulkan pipeline feature gate;
- restore IP's explicit portal matrices after legacy `ShaderInstance.apply()` and immediately before the IP-owned portal mesh submission boundary;
- keep the IP custom-shader reload ownership bridge even though actual F3+T testing remains deferred.

Focused details remain in `docs/CREATE_CHRONICLES_COMPATIBILITY.md` and `docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`.

## GPU-terrain flags / safety

Whole-section accelerated bypass requires:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Mixed-section APPEND additionally requires:

```text
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Keep accelerated consumption default-off until representative RX correctness and comparable frame-time evidence are complete. Preserve CPU/fail-closed handling for unsupported Forge terrain callbacks/content. Do not weaken production memory safety or ownership rules merely to make a test pass.

## Next useful action

Use the dedicated performance capture file to establish a Vulkan critical-path capture under the fixed Phase 5 stationary/traversal/Create-heavy workloads, alongside the comparable OpenGL/Vulkan baseline process in `docs/TERRAIN_PERFORMANCE_BASELINE.md`. Use the resulting p95/p99/worst-frame and queue/build/upload evidence to choose the next optimization target. If the capture points at GPU execution rather than CPU submission/terrain work, add the bounded Vulkan timestamp-query ring next; otherwise instrument only the dominant `unaccounted` CPU boundary or resume the relevant Phase 6/7 gate.
