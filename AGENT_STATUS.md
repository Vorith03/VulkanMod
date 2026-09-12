# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale.

## Required planning documents

Use these together:

- `AGENTS.md` — development/evidence protocol;
- `ROADMAP.md` — canonical phases and gates;
- `docs/TERRAIN_PRIORITY_OVERRIDE_2026-09-12.md` — explicit user-approved sequencing override;
- `docs/TERRAIN_PERFORMANCE_BASELINE.md` — benchmark contract;
- `docs/TERRAIN_LIFECYCLE_AUDIT_2026-09-12.md` — Phase 6 lifecycle audit.

## Current verified checkpoint — 2026-09-12

- Branch: `forge-1.20.1`.
- Last verified source commit: `2f415efba0478be3af0be862574f4a0e6ae550df`.
- GitHub Actions: **CI #342 — fully green**. Build/distribution, normal and no-early-splash Vulkan startup, vanilla color/depth PostChain, screenshot/readback synchronization validation, Crash Assistant 1.9.7 and Flywheel 0.6 all passed.
- Highest demonstrated legacy milestone: **6 — playable world**.
- Phase 3 remains complete, 11/11.
- Phase 4 Create Chronicles compatibility is **parked at 3/8** by explicit user priority override; it is not complete.
- Phase 5 measurement discipline is **3/7**: terrain traversal stress case, Create-heavy stress case, and benchmark/acceptance procedure are defined. Exact route plus OpenGL/Vulkan/frame-time baselines remain open.
- Active implementation phase: **Phase 6 — persistent region batching, 7/10 gates** after the code-level lifecycle audit. High-churn RX visual evidence, measured indirect/multi-draw benefit, and reproducible performance improvement remain open.

Documentation-only commits after `2f415efb` use `[skip ci]`; treat #342 as the verified source checkpoint beneath them.

## Terrain work verified in CI

### Persistent GPU residency

- Region geometry already uses region-scoped Vulkan vertex/index buffers.
- Rebuilt terrain reuses an existing suballocation in place when the replacement mesh fits its reservation; larger replacements retain the relocation/growth fallback.
- Free extents coalesce; buffer growth is demand-aware.
- Drained coarse 8×8 region-ring slots keep their physical Vulkan buffers when recycled to new world coordinates. A slot with unexpected live geometry falls back to release/reallocate instead.
- Resident region allocation/used/capacity is exposed through the `rm` F3 counter so bounded residency can be checked during traversal.

### Draw-command persistence

- Opaque region/layer draws use cached multi-draw indexed-indirect command streams.
- Cache revisions are layer-local; editing one terrain layer does not invalidate unrelated layers.
- Equivalent visibility rewrites keep cached commands.
- Translucent and tripwire rendering remain on the established fallback path.

### Terrain upload synchronization

- Normal terrain copies now submit on the **graphics queue**, not the dedicated transfer queue.
- Persistent in-place writes are therefore ordered by the same VkQueue as old/new terrain draws: old draw -> copy -> new draw.
- Normal terrain uploads no longer require a cross-queue transfer semaphore.
- Area-buffer growth copies are also graphics-queue ordered; the synchronous growth path waits its helper fence and explicitly retires that helper before the old buffer is retired.
- CI smoke verifies the normal same-queue path adds no transfer wait semaphore and preserves the existing reuse/growth behavior.

## Current F3 terrain measurements

The chunk-stat line is intentionally compact enough for the user's 2560×1440 window. Important fields:

- `iT` / `aT` — idle / active terrain workers;
- `qH` / `qL` / `uQ` — queued high, queued low, and completed results awaiting publication;
- `lat(q/b/h)` — average queue / mesh-build / handoff latency in ms;
- `hc:<count>/<MiB>/<ms>` — cumulative worker-builder -> compact `UploadBuffer` native copies;
- `up:<ready>/<KiB>/<avg>` — terrain upload batch CPU record-to-ready timing and size;
- `sc:<count>/<MiB>/<ms>` — cumulative `UploadBuffer` -> mapped Vulkan staging CPU copies;
- `stg:<high>/<capacity>/<resizes>` — terrain/texture staging pressure summary;
- `sync ...` — helper synchronization counters;
- `R:<sections>/<calls>` — current visible region sections / region draw calls;
- `cmd:<updates>/<bytes>` — indirect command-cache rebuild work;
- `mesh:<uploads>/<KiB>` and `r/n/g` — mesh bytes plus reused/new/growth reservations for the current update;
- `ar/f` — cumulative coarse-region buffer reuse / safety fallback;
- `rm:<allocated>/<total> <used>/<capacity>M` — current persistent region residency.

`hc` and `sc` reset over the same terrain batch/world-reset window, so their byte/time totals are directly comparable.

## Why the next source decision is measurement-driven

Terrain meshing currently performs two CPU copies after geometry is built:

1. worker `TerrainBufferBuilder` backing memory -> compact native `UploadBuffer` (`hc`);
2. `UploadBuffer` -> mapped Vulkan staging (`sc`).

Keeping the compact handoff copy has a real memory/lifetime advantage: the worker builder can immediately be reused instead of pinning an entire mutable builder backing allocation while the render thread catches up. A zero-copy handoff therefore needs a deliberate ref-counted/pool-backed ownership design, not simply retaining `RenderedBuffer` slices whose backing memory a worker may resize.

Do **not** implement that larger ownership change until `hc`/`sc` and build/handoff latency show which copy is material on the RX 6900 XT workload.

## Immediate next action

Use the #342 artifact in the real Create Chronicles instance for the bounded terrain benchmark from `docs/TERRAIN_PERFORMANCE_BASELINE.md`:

1. same 2560×1440 window/settings/resource packs;
2. capture F3 after terrain settles at the route start;
3. fly a recorded ~1024-block straight route through already-generated terrain;
4. capture F3 again after the traversal and note any stale/missing/corrupt terrain, especially leaves/water and region-boundary crossings;
5. retain the route coordinates/facing so this becomes the fixed Phase 5 comparison route.

This one run is useful now: it simultaneously supplies the first real copy-cost/residency evidence and exercises the still-open Phase 6 high-churn visual gate. Do not run F3+T for this terrain checkpoint.

## Parked resource-reload issue

The full-pack F3+T memory investigation is intentionally parked, not solved by the terrain work. Build #310 demonstrated that pre-decode native allocator reclamation can materially reduce RSS after old static atlas retirement, but the later replacement decode still reached the unchanged system/process safety floor under the tested host-memory conditions while GPU/GTT residency rose. Do not weaken the guard or reopen that investigation unless the user reprioritizes it or new evidence makes it block the terrain track.

## Evidence limits

- CI proves compilation, Vulkan startup, synthetic/real-buffer terrain invariants and the existing renderer compatibility smokes under Lavapipe; it does not prove AMD gameplay performance.
- No FPS, frame-time, chunk-loading or OpenGL-vs-Vulkan performance improvement is claimed yet.
- The latest RX 6900 XT full-modpack evidence still establishes playable Vulkan world entry, not the new Phase 6 performance result.
