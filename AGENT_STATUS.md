# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale.

## Required planning documents

Use these together:

- `AGENTS.md` — development/evidence protocol;
- `ROADMAP.md` — canonical phases and gates;
- `docs/TERRAIN_PRIORITY_OVERRIDE_2026-09-12.md` — explicit user-approved sequencing override;
- `docs/TERRAIN_PERFORMANCE_BASELINE.md` — benchmark contract;
- `docs/TERRAIN_LIFECYCLE_AUDIT_2026-09-12.md` — Phase 6 lifecycle audit;
- `docs/GPU_TERRAIN_BOUNDARY.md` — current GPU input architecture and local checkpoint.

## Current checkpoint — 2026-09-12

- Branch: `forge-1.20.1`.
- Verified runtime source: `387afc076e187ce539b9f494d374831e599896bc`, CI **#353 fully green**
  (run 34710104092, job 103597196130); actual jobs and decoded logs inspected.
- Published input commits: `2e38d13` and `22e4956` (identical trees to local `41ed707`
  and `1cc1ace`). Follow-up `387afc0` fixes an early-startup test-fixture assumption.
- CI #352 passed build/distribution and ABI tests, but failed the new fixture because
  Forge state IDs were not finalized at its hook. #353 uses explicit fixture IDs;
  actual Minecraft traversal and publication/cancellation/lifecycle checks still run.
- Artifact: [VulkanMod-Forge-build-353](https://github.com/Vorith03/VulkanMod/actions/runs/34710104092/artifacts/10303446018),
  `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.353-g387afc07-all.jar`.
- Documentation-only checkpoint commits after this source use `[skip ci]`.
- Highest demonstrated legacy milestone remains **6 — playable world**.
- Phase 3: 11/11. Phase 4: parked 3/8. Phase 5: 3/7. Phase 6: 7/10;
  high-churn RX visual and comparable performance gates remain open.
- User explicitly advanced the active implementation direction to bounded GPU-driven
  terrain groundwork. Mesh shaders remain optional and later.
  P7.1 (input format and conservative fallback infrastructure) is now verified, 1/11.

### New bounded source slice

Read `docs/GPU_TERRAIN_BOUNDARY.md` for the architecture investigation, CPU/GPU
responsibility table, exact ABI, template design, limits and next milestones.

- Default-off `-Dvulkanmod.experimentalSectionVoxels=true` captures a compact state
  palette, packed voxel indices and four CPU-resolved flag planes during the existing
  CPU compile loop. All voxels remain explicitly CPU_REQUIRED.
- Immutable numeric snapshots use the existing cancellation-checked publication queue
  and region-owned staging. They do not retain worlds, models or BlockState objects.
- Section generations reject stale data on dirty/reset/release. Dirty can originate
  on a worker; generation/store invalidation is serialized with publication.
- Region staging is independent of mesh layer revisions, capped globally at 32 MiB
  payload / 2048 entries. Budget failure invalidates old data and retains CPU rendering.
- No GPU voxel allocations, compute dispatch, lighting/tint/halo stream, qualified
  template compiler or GPU meshing exists yet. CPU rendering and all existing memory
  safety floors / Vulkan synchronization paths are preserved.
- Local Java 17 ABI/store tests passed through the installed compiler module, plus
  shell syntax and diff checks. Full Gradle bootstrap failed on a blocked network
  download. CI #353 passed both capture modes and the full existing suite.

## Immediate next action

1. Next source slice: bounded region SSBO residency/readback for the existing input
   ABI. Read `docs/GPU_TERRAIN_BOUNDARY.md` before touching AreaBuffer; its usage
   switch currently creates an IndexBuffer for non-vertex usage.
2. Add explicit validity/generation and barrier/retirement coverage before any compute
   consumer can use the resident data. Keep absent/unsupported input CPU-only.
3. Optional RX 6900 XT test: same route/settings with capture off/on, record F3 voxel
   residency/rejections and build/handoff/heap overhead, then world exit/reentry and
   render-distance changes. This measures overhead/lifecycle, not a speedup.

No full GPU mesher, qualified template compiler, GPU voxel allocation or RX A/B
performance result is claimed. Do not reopen the parked F3+T investigation.

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

## Parked resource-reload issue

The full-pack F3+T memory investigation is intentionally parked, not solved by the terrain work. Build #310 demonstrated that pre-decode native allocator reclamation can materially reduce RSS after old static atlas retirement, but the later replacement decode still reached the unchanged system/process safety floor under the tested host-memory conditions while GPU/GTT residency rose. Do not weaken the guard or reopen that investigation unless the user reprioritizes it or new evidence makes it block the terrain track.

## Evidence limits

- CI proves compilation, Vulkan startup, synthetic/real-buffer terrain invariants and the existing renderer compatibility smokes under Lavapipe; it does not prove AMD gameplay performance.
- No FPS, frame-time, chunk-loading or OpenGL-vs-Vulkan performance improvement is claimed yet.
- The latest RX 6900 XT full-modpack evidence still establishes playable Vulkan world entry, not the new Phase 6 performance result.
