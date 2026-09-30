# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current executable

- Latest validated executable commit: `bbc8dd7aa244db1f138a7329b13b8e5f7ecf5622` (`perf: reduce render allocations and reject unready terrain tasks before capture`).
- CI **#831** / run `36704070880` is fully green.
- Build artifact: `VulkanMod-Forge-build-831`, artifact ID `11091298348`, SHA-256 `cc3efb35ed782b08bc537045a5fd6137f2d556295b9daeb03350b8715a488db0`.
- Smoke logs: `VulkanMod-Forge-smoke-log-831`, artifact ID `11091313224`, SHA-256 `f16ef3daef9bdbe2293dc1628651c9a40f8a1bd547442fa7a2e14eb9286a0dcd`.
- #831 passed distributable verification, both Forge startup modes, persistent GPU indirect, post-chain/depth-post-chain, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j. The three private-resource-pack fixture steps were skipped as configured.
- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.

## RX 6900 XT benchmark evidence that drives the next action

The completed automated build #825 stationary diagnostic is the current runtime performance evidence. Detailed evidence is in `docs/PERFORMANCE_CAPTURE_BUILD_825_2026-09-29.md`.

- 2,669 / 2,670 frames over 25 ms contained a client tick.
- Tick-bearing frames averaged about 28.49 ms; render-only frames about 2.77 ms.
- `client_tick` averaged about 24.17 ms wall / 23.96 ms CPU.
- Existing nested tick stages left about 20.66 ms in `client_tick_other`; entity tick was about 3.46 ms.
- Tick-bearing frames allocated about 3.77 MiB on the render thread on average; whole-run render-thread allocation was about 316.6 MiB/s.
- Vulkan submission/fence/image-acquire/upload waits were small. Do not optimize those first based on current evidence.
- The 2048-entry staging store stayed full but the earlier repeated rejection/recovery cycle was absent. Do not attribute the recurring tick hitch to staging-cap churn without new evidence.
- OpenAL failed before measurement and sound was disabled. Keep that startup/audio failure separate from the recurring measured tick cost.
- The capture completed automated save/exit correctly. It is diagnostic rather than a formal matched Phase 5 baseline because its FPS cap was 260 and complete OpenGL/control provenance is not established.

## CPU allocation and terrain-admission fixes in #831

Commit `bbc8dd7aa244db1f138a7329b13b8e5f7ecf5622` removes temporary matrices/FloatBuffer views from MVP calculation, removes pipeline-hash varargs/boxing, and rejects neighbor-unready terrain tasks before region capture. The section stays dirty with a traversal retry; the worker's unload recheck remains. Details and limitations: `docs/PERFORMANCE_CPU_ALLOCATION_FIXES_2026-09-30.md`.

- Java 17 focused checks and the full local `build` passed, including distributable/module verification and all regression tasks.
- 1,000 matrix cases match the old output bit-for-bit, including nonzero buffer positions, scratch reuse and output aliasing; 10,000 randomized component hashes preserve legacy values.
- The isolated warmed MVP probe measured 0 allocated bytes for 100,000 new calls versus 27,200,000 bytes for the old path. This is an isolated allocation result, not an RX FPS claim.
- CI #831 / run `36704070880` is fully green; the new artifact is ready for the RX diagnostic. In-world neighbor arrival/unload behavior and RX performance still need runtime evidence.
- The ~20.66 ms unnamed tick remainder is still unresolved. Do not label these fixes as its repair or change callback/entity semantics without leaf attribution.

## New client-tick attribution retained from #830

Build #830 adds automated-benchmark-only leaf timing and allocation attribution while preserving the existing broad profiler stages. See `docs/PERFORMANCE_TICK_ATTRIBUTION_2026-09-30.md`.

New leaf buckets cover:

- Forge client tick pre/post event dispatch;
- Forge client-level tick pre/post event dispatch;
- GUI tick;
- `GameRenderer.pick`;
- multiplayer game-mode tick;
- texture-manager tick;
- tutorial tick;
- vanilla `LevelRenderer.tick`;
- `LevelRenderer.tickRain`;
- ambient-world `ClientLevel.animateTick`;
- particles;
- music;
- sound;
- keybind handling.

For each leaf, the automated benchmark reports average / p95 / max wall time and, when supported by the JVM, average / p95 / max render-thread allocated KiB. Summary formatting/output occurs only after the final measured frame. The extra leaf profiler is gated by both `vulkanmod.performanceProfiler=true` and `vulkanmod.performanceProfiler.autoBenchmark=true`; normal gameplay and manual profiling do not allocate its sample buffers or collect its leaf metrics.

Expected end-of-capture lines include `client_tick_breakdown`, `client_tick_leaf_avg_ms`, `client_tick_leaf_p95_ms`, `client_tick_leaf_max_ms`, and corresponding `client_tick_leaf_allocation_*_kib` lines.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**, current useful priority. Formal OpenGL baseline, Vulkan baseline, and hitch/frame-time evidence remain open under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid: **6/11**. Correct GPU visibility/section selection is the first substantial open implementation gate, but performance claims still require comparable Phase 5 evidence.
- Immersive Portals current compatibility behavior is RX-confirmed working. Preserve its documented semantic boundary in `docs/CREATE_CHRONICLES_COMPATIBILITY.md` unless new evidence contradicts it.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from rendering/performance work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Next useful action

Run **build #831** on the user's RX 6900 XT/Create Chronicles environment with the same automated stationary benchmark workload/settings used for the successful #825 diagnostic.

Required flags:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.autoBenchmark=true
```

Keep the same benchmark world, view, framebuffer/settings and 260 FPS cap for this diagnostic rerun so the retained tick attribution and the new allocation/admission fixes can be compared to #825. Do not silently convert this into the formal Phase 5 baseline. Keep Minecraft focused; do not resize during measurement. Return the generated `logs/vulkanmod-performance-benchmark-*.log`. `latest.log` is only necessary if automation aborts or another runtime issue appears.

Interpret the result using this order:

1. If a Forge pre/post event bucket dominates time/allocation, instrument only that EventBus family down to listener/mod ownership.
2. If vanilla `level_renderer` / `weather` dominates, investigate redundant renderer-maintenance work and its required Forge/mod semantics.
3. If another named leaf dominates, investigate that concrete subsystem rather than renderer-wide tuning.
4. If a leaf mainly dominates allocation, investigate object churn there before GC tuning.
5. If all leaves are small but `client_tick_other` remains large, inspect the remaining Forge-patched `Minecraft.tick()` work and add one more bounded attribution layer.
6. Keep broad Vulkan GPU timestamps deferred unless CPU accounting no longer explains frame pacing.

No unchanged repeat of build #825 or intermediate build #830 is needed. One #831 capture supplies the retained tick attribution plus evidence for the allocation/admission fixes. Read the new evidence document before interpreting scheduling deltas; admission skips are not completed builds.
