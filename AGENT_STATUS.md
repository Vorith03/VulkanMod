# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical implementation detail belongs in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Repository state

- `9624f0be` / CI **#818** / run `36640414458` was the last fully green profiler executable before named-world automation. Artifact: `VulkanMod-Forge-build-818` (ID `11066561828`; artifact ZIP SHA-256 `5f0a2398a21b5d7c0f0509b1953d4935ccc90ba0529fbb7e2131dd46bc25fe99`); smoke log artifact `VulkanMod-Forge-smoke-log-818` (ID `11066711727`). The real startup capture has six windows with zero top-level overlap, valid tick-local CPU/allocation samples, bounded joint frame examples, and measured profiler summary cost. Its smoke interval is 0.25 s, so its ~1–7 ms per-summary overhead is not a measurement of the default five-second capture overhead. This CI does not exercise an in-world terrain workload.
- `b6b63cd0` / CI **#821** / run `36643411536` validated the first unattended benchmark executable (all 31 steps; three private-resource-pack fixture steps skipped as configured). Artifact: `VulkanMod-Forge-build-821` (ID `11067622502`, artifact ZIP SHA-256 `d8d7f3c08ae43ea11c3ac795c7bf7c8cca3f0c0060b4cf475220fd95dd6be35e`); smoke logs `VulkanMod-Forge-smoke-log-821` (ID `11067662223`). This adds opt-in, named-world unattended profiling: integrated-server Spectator teleport, client pose/terrain readiness checks, 60-second settle, configurable measured duration (default 180 seconds), post-staging-cap extension, on-screen countdown, output flush, normal single-player world teardown, then game close. CI #819 caught a nonexistent `Minecraft.disconnect()` call; `81db5de1` uses the compiled Forge 1.20.1 level-disconnect/clear-level path. Startup CI cannot validate the in-world teleport/save/close behavior; the first RX run must. See `docs/PERFORMANCE_PROFILING.md`.
- `384e2d82` / CI **#822** / run `36644976279` passed all 31 steps (three private-resource-pack steps skipped). Artifact: `VulkanMod-Forge-build-822` (ID `11068716720`, artifact ZIP SHA-256 `cc1f94dc4dab7f3888d222e982a9c6b421bea2f86006466c94038164acc816e9`); smoke logs ID `11068730414`. This hardens capture validity and retention. Automated default filenames contain a run UUID and use CREATE_NEW; explicit output paths and normal profiling retain their existing truncate behavior. `capture_identity` records schema=1, UUID, loaded mod version (CI versions contain build/short SHA), and automation mode. Automatic save/close requires successful output flush and close; output failure aborts and leaves the game open. Focus loss, pause, non-local/non-first-person camera, and measured framebuffer resize also abort, with a reason in the capture when healthy and always in `latest.log`. The retained startup capture has six valid windows, a parseable UUID, and `vulkanmod_version=0.3.2-forge.2-build.822-g384e2d82`; its updated output contract passes. An isolated Java 17 probe of the exact production output methods passed normal completion, injected write/flush/close failures, disabled/unstarted capture, and unfinished-frame cases. This verifies completion reporting, not real disk exhaustion or Minecraft/controller integration; in-world validity/save/close still need runtime evidence.
- `3a0e6713` is the current fully green executable. CI **#825** / run `36666321378` passed all 31 steps (three private-resource-pack steps skipped). Artifact: `VulkanMod-Forge-build-825` (ID `11075679651`, artifact ZIP SHA-256 `359bd35b312e1a5c852992c7285d13fd2af60741108722d98ab165c321b45d58`); smoke logs ID `11075724243`. The first user automated run on 2026-09-29 rejected `tp X Y Z yaw pitch` at the rotation arguments and aborted before measured capture. The controller now uses the valid rotated form `tp @s X Y Z yaw pitch`, separates immediate rejection from the 30-second timeout, reports Spectator failure, and avoids repeating Spectator mode when already active. The startup oracle uses Minecraft's actual TeleportCommand, follows the tp alias to its final command context, accepts default/fractional poses, and rejects the old targetless rotation. This is a confirmed controller bug; it supplies no new renderer performance evidence. World teleport execution, settled profiling, and save/close remain pending on the RX machine.
- The profiler now computes `unaccounted` as a per-frame positive remainder with p95/max rather than clamping a difference of window averages. It records tick-local CPU time and allocated bytes, evenly spaced tick frames and slow render-only examples with joint stage timing, player/screen presence, summary overhead, and extra parent/child timing integrity checks. Read `docs/PERFORMANCE_PROFILING.md` before interpreting new fields. Exact per-frame GC attribution, individual mod callbacks, worker latency quantiles, GPU timestamps, and presentation intervals are intentionally gated on what the next RX capture actually shows.
- `401ef894` updated `ROADMAP.md` to reflect verified Phase 4 **7/8** and the current Phase 5 measurement priority. It was an independent documentation commit; the profiler patch was replayed on top without modifying that work.
- `2b634a1f` / CI **#816** first repaired the display-update overlap and passed all 31 steps. Its smoke capture had zero top-level overlap; an ordinary menu window measured `submit_render=2.873 ms`, `display_update=0.013 ms`, and `unaccounted=0.331 ms`, rather than #815's overlapping 3.202/3.223 ms and clamped zero residual.
- #815 was also fully green, but its newly retained profiler smoke capture exposed a measurement error: the old display-update call-site injection enclosed the submit hook from another mixin at the same invocation. #816 times `Window.updateDisplay()` inside VulkanMod's overwrite and checks overlapping top-level totals in CI. Do not add `display_update` to `submit_render` or infer complete phase coverage from `unaccounted=0` in #806/#815. The prior RX tick cadence, broad render cost, and staging-cap recovery evidence remain valid. See `docs/PERFORMANCE_PROFILING.md` and `docs/PERFORMANCE_CAPTURE_2026-09-29.md`.
- `c57151c9` adds a once-per-second on-screen voxel staging count during opt-in profiler capture, without F3. CI **#811** / run `36618961222` is fully green (31 steps). Artifact: `VulkanMod-Forge-build-811` (ID `11057922105`). User capture instructions below supersede the earlier counter-unobservable guidance.
- `7569b45f` corrects a measurement error in the #809-era profiler: `capture_start` dimensions precede the user's window resize. It now records first/last framebuffer dimensions, width/height ranges, and observed changes for every measured summary window. CI **#810** / run `36602819406` is fully green. Artifact: `VulkanMod-Forge-build-810` (ID `11050377216`, SHA-256 `50e50c71d931a3b3423044698308a8a0681936eb0d4858117ab3642fb02f1447`). The #806 stationary in-world resolution is unknown; do not use its initial 854×480 value as the measured-world resolution or claim a resolution-matched render A/B from it.
- Performance diagnostic code at `ac9aa125` and its missing-import correction `477bb9d5` are on `forge-1.20.1`. CI #808 / run `36558345092` failed Java compilation on the missing `ChunkArea` import; CI **#809** / run `36558618489` is fully green for `477bb9d5`, including distributable and all public compatibility smokes. Artifact: `VulkanMod-Forge-build-809` (ID `11029477220`, SHA-256 `a9bd6e6f794bcb5d4dd066b8f12ea9514c7537872b3af09107145bfe073a11c2`). Read `docs/PERFORMANCE_CAPTURE_2026-09-29.md` for the exact #806 capture analysis.
- The user's original performance diagnostic used `0b5e596623591e9e2180c9b67cb572bfc3afcca2` (`perf: split broad runTick profiling phases`).
- Public CI **#806** / run `36552111888` is fully green. Build/distributable, packaged Immersive Portals anchors, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, exact Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-806` (artifact `11025174241`, SHA-256 `348bba3a1ebb0c1f5caaaff5a52aa1aea6379563a30e480638e8820fd90296a5`).
- The adversarial audit remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence.

## Active sequencing

- Live Phase 4 evidence is **7/8**. The only remaining mandatory Phase 4 gate is world enter/leave/re-enter plus resource reload. The user explicitly does **not** want that work prioritized now; treat the lifecycle gate as deferred.
- Phase 5 measurement work is **4/7** and is the current useful priority. Open numeric gates are the RX 6900 XT OpenGL baseline, Vulkan baseline, and hitch/frame-time evidence under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid work remains **6/11**. The first substantial open implementation gate there is correct GPU visibility/section selection, but performance claims still require comparable Phase 5 evidence.
- `ROADMAP.md` now records the verified Phase 4 7/8 state and Phase 5 priority; its remaining lifecycle gate stays open and deferred.

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

`0b5e5966` added broad top-level `runTick()` timing around `Minecraft.tick()`, `GameRenderer.render(...)`, `Window.updateDisplay()`, and `RenderSystem.limitDisplayFPS(...)`. Its display call-site hook inadvertently nested Vulkan submission because of mixin ordering; #816 corrects this inside the window method and emits both overlap counts and excess time. Terrain setup/reposition/uploads remain nested renderer detail. Existing frame-slot/fence/bookkeeping and submit/present timing remain intact.

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

Install artifact `VulkanMod-Forge-build-825` and restart Minecraft for the necessary RX run on a **copy** of the named `VulkanMod Benchmark` world, with the fixed graphics profile in `docs/TERRAIN_PERFORMANCE_BASELINE.md` (render distance 16, stable near-2560×1440 framebuffer), existing GPU-terrain flags, and `-Dvulkanmod.performanceProfiler=true -Dvulkanmod.performanceProfiler.autoBenchmark=true`. Enter that world once, keep Minecraft focused with the HUD visible and first-person camera, do not move the camera or open F3. Resize only before the measured countdown begins. The controller teleports to `0 192 0 -90 30` in Spectator, waits for visible terrain and 60 seconds of settling, records at least 180 seconds plus a measured 60-second post-cap interval if needed, flushes the file, saves/exits the world, and closes Minecraft. Preserve the new `logs/vulkanmod-performance-benchmark-<UUID>.log` announced in `latest.log` and `latest.log` itself, including whether automated save/close completes cleanly; #801 previously showed a separate native abort on normal shutdown. If automation aborts, `latest.log` has the reason and the game stays open. Use stable world windows with player present, no screen, unchanged pose and framebuffer. Conditional tick/render summaries, tick CPU/allocation, joint frame examples, terrain deltas, and overlap diagnostics should distinguish client simulation/packet/mod tick work from the confirmed #806 staging-cap recovery cycle while verifying accounting. Do not assert a numeric render-cost improvement against #806 without its actual in-world resolution.

A second render-distance-32 capture is also useful later as a labeled real-gameplay stress workload, but do not mix it numerically with the fixed render-distance-16 baseline series.
