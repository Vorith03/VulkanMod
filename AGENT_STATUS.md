# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical implementation detail belongs in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Repository state

- `7569b45f` corrects a measurement error in the #809-era profiler: `capture_start` dimensions precede the user's window resize. It now records first/last framebuffer dimensions, width/height ranges, and observed changes for every measured summary window. CI **#810** / run `36602819406` is fully green. Artifact: `VulkanMod-Forge-build-810` (ID `11050377216`, SHA-256 `50e50c71d931a3b3423044698308a8a0681936eb0d4858117ab3642fb02f1447`). The #806 stationary in-world resolution is unknown; do not use its initial 854×480 value as the measured-world resolution or claim a resolution-matched render A/B from it.
- Performance diagnostic code at `ac9aa125` and its missing-import correction `477bb9d5` are on `forge-1.20.1`. CI #808 / run `36558345092` failed Java compilation on the missing `ChunkArea` import; CI **#809** / run `36558618489` is fully green for `477bb9d5`, including distributable and all public compatibility smokes. Artifact: `VulkanMod-Forge-build-809` (ID `11029477220`, SHA-256 `a9bd6e6f794bcb5d4dd066b8f12ea9514c7537872b3af09107145bfe073a11c2`). Read `docs/PERFORMANCE_CAPTURE_2026-09-29.md` for the exact #806 capture analysis.
- Last fully green executable before the new performance patch was `0b5e596623591e9e2180c9b67cb572bfc3afcca2` (`perf: split broad runTick profiling phases`).
- Public CI **#806** / run `36552111888` is fully green. Build/distributable, packaged Immersive Portals anchors, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, exact Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-806` (artifact `11025174241`, SHA-256 `348bba3a1ebb0c1f5caaaff5a52aa1aea6379563a30e480638e8820fd90296a5`).
- The adversarial audit remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence.

## Active sequencing

- Live Phase 4 evidence is **7/8**. The only remaining mandatory Phase 4 gate is world enter/leave/re-enter plus resource reload. The user explicitly does **not** want that work prioritized now; treat the lifecycle gate as deferred.
- Phase 5 measurement work is **4/7** and is the current useful priority. Open numeric gates are the RX 6900 XT OpenGL baseline, Vulkan baseline, and hitch/frame-time evidence under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid work remains **6/11**. The first substantial open implementation gate there is correct GPU visibility/section selection, but performance claims still require comparable Phase 5 evidence.
- `ROADMAP.md` still contains stale Phase 4 5/8 prose/checkmarks from before the #801 RX confirmation. Use its gate definitions/order, but use this checkpoint plus current runtime evidence for the live 7/8 count and the user's priority override.

## Current RX 6900 XT compatibility evidence

Build #801 established the current visual compatibility baseline:

- Immersive Portals portal rendering and traversal work correctly, including remote-world framebuffer composition attached/clipped to the portal.
- Creative inventory block/item imagery renders correctly.
- Third-person player and ordinary entity rendering work correctly.
- Create/Flywheel ordinary gameplay and moving contraption rendering work correctly.
- Representative particles/translucency/entities/GUI paths work correctly.
- Do not ask the user to repeat those checks unless a later executable change directly threatens them.

The same #801 session independently reproduced a shutdown/native-lifetime abort after normal Minecraft shutdown (`double free or corruption` / exit 134). Keep that separate from the now-working rendering paths. Do not make speculative native-ownership changes without focused ownership evidence or a native backtrace.

## Performance / critical-path instrumentation

The reusable opt-in profiling layer is documented in `docs/PERFORMANCE_PROFILING.md`.

Enable it with:

```text
-Dvulkanmod.performanceProfiler=true
```

Default output:

```text
logs/vulkanmod-performance.log
```

Useful bounded capture controls include:

```text
-Dvulkanmod.performanceProfiler.summarySeconds=5
-Dvulkanmod.performanceProfiler.durationSeconds=30
-Dvulkanmod.performanceProfiler.output=logs/vulkanmod-performance-stationary.log
-Dvulkanmod.performanceProfiler.slowFrameMs=25
```

The initial RX 6900 XT diagnostic capture supplied on 2026-09-29 was useful but is **not a fixed-contract Phase 5 baseline**:

- VulkanMod reported effective render distance `D: 32`; the fixed baseline contract is render distance 16.
- The active-world portion remained under substantial terrain population/build churn instead of representing the required settled 60-second stationary state.
- Across the sustained active-world windows, frame wall time was roughly 30.3 ms weighted average while roughly 28.0 ms (~92%) remained in the old `unaccounted` bucket.
- Known ordinary costs were much smaller: terrain setup ~2.0 ms average, terrain uploads ~0.14 ms, submit/present ~0.09 ms, and frame waits near zero. This does **not** support optimizing submit/fence/upload code first.
- Some isolated multi-hundred/multi-thousand-ms hitches were GC-heavy (including a ~1.67 s frame paired with ~1.49 s GC), but ordinary sustained 30–45 ms frames were not explained by GC.
- Exact ~16.7 ms periods elsewhere in the capture make an FPS limiter worth distinguishing explicitly, but do not assume their cause until the new limiter stage is observed.

`0b5e5966` therefore adds broad non-overlapping top-level `runTick()` timing around `Minecraft.tick()`, `GameRenderer.render(...)`, `Window.updateDisplay()`, and `RenderSystem.limitDisplayFPS(...)`. Terrain setup/reposition/uploads remain as nested renderer detail and are no longer double-counted against `unaccounted`. Existing frame-slot/fence/bookkeeping and submit/present timing remain intact.

The profiler is still deliberately CPU wall-clock only. Do not add blanket Vulkan GPU timestamp instrumentation until the broad split shows that GPU/pass timing is actually the next missing discriminator.

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

Use artifact `VulkanMod-Forge-build-810` for the next necessary RX run: same world/position and graphics settings as #806, render distance 16, GPU terrain flags unchanged. Start with the profiler enabled, enter the world, keep the camera stationary, and remain in-world for **three minutes after terrain first appears**, then exit normally. The existing F3 screen shows `Terrain voxel staging: N/2048 sections` when GPU terrain staging is enabled; watching it is optional. If using F3 to mark saturation, close it and remain stationary for another 60 seconds so the measured windows have the ordinary HUD. Determine from the log afterward whether/when the cap was reached and select the later stationary windows accordingly. The run remains useful if it never reaches that cap. Leave the profiler unlimited, and preserve its output and `latest.log`. Use the per-window dimensions to identify the actual stationary-world size and any resize window. The tick subphases, dirty/scheduled counts, and recovery diagnostics should distinguish client simulation/packet/mod tick work from the confirmed #806 staging-cap recovery cycle. Do not assert a numeric render-cost improvement against #806 without its actual in-world resolution. For a formal Phase 5 baseline, use the exact fixed profile in `docs/TERRAIN_PERFORMANCE_BASELINE.md`, including a stable near-2560×1440 framebuffer and the 60-second settle period.

A second render-distance-32 capture is also useful later as a labeled real-gameplay stress workload, but do not mix it numerically with the fixed render-distance-16 baseline series.
