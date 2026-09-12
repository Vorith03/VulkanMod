# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale.

## Required planning documents

Use these together:

- `AGENTS.md` — development/evidence protocol;
- `ROADMAP.md` — canonical phases and gates;
- `docs/TERRAIN_PRIORITY_OVERRIDE_2026-09-12.md` — explicit user-approved sequencing override;
- `docs/TERRAIN_PERFORMANCE_BASELINE.md` — benchmark contract;
- `docs/TERRAIN_LIFECYCLE_AUDIT_2026-09-12.md` — Phase 6 lifecycle audit;
- `docs/GPU_TERRAIN_BOUNDARY.md` — GPU terrain input architecture and CPU/GPU responsibility boundary;
- `docs/GPU_VOXEL_RESIDENCY_PLAN_2026-09-12.md` — current bounded SSBO residency design and stop conditions.

## Current checkpoint — 2026-09-12

- Branch: `forge-1.20.1`.
- Live source head before this documentation-only update: `05c042d6aa5de442d51da54bf1aab676aea5c1da`.
- CI **#365 fully green** for that head (run `34714258914`, job `103608457107`). Build/distribution, both Vulkan startup paths, color/depth post-chain, screenshot readback, Crash Assistant, Chat Heads and Flywheel smoke tests all passed.
- Current build artifact: `VulkanMod-Forge-build-365` from run `34714258914`.
- The immediately preceding source commits are:
  - `5e9410a` — `terrain: add fixed voxel page allocator`;
  - `b2f0670` — `test: cover fixed voxel page allocation`;
  - `05c042d` — `terrain: support fixed storage uploads`.
- CI #364 was superseded/cancelled by the next push; #365 is the authoritative source validation for this slice.
- Highest demonstrated legacy milestone remains **6 — playable world**.
- Phase 3: 11/11. Phase 4: parked 3/8. Phase 5: 3/7. Phase 6: 7/10; high-churn RX visual and comparable performance gates remain open.
- Phase 7 GPU terrain is the active implementation direction. P7.1 (input ABI and conservative CPU fallback infrastructure) remains verified; bounded GPU voxel residency is now **in progress**, not complete.
- User priority is now explicit: move as much repetitive terrain work off the CPU as practical while retaining conservative CPU fallback for arbitrary Minecraft/Forge semantics. Mesh shaders remain optional/later; compute-driven residency/meshing comes first.

### Latest RX 6900 XT gameplay evidence

The user tested build **#362** in the full Create Chronicles workload, including fast spectator traversal.

- Normal/static terrain publication no longer looked like the dominant bottleneck. During the stressed capture the terrain publication queue was empty (`publish 0/14`) and no workers were blocked on publication (`pubWait 0`), while several workers were idle.
- Fast spectator movement still struggled badly because the integrated server/world-generation side fell behind and section work churned rapidly as the camera outran useful terrain.
- The terrain result drop count rose sharply during the traversal, consistent with large amounts of completed or in-flight section work becoming stale/cancelled before it could remain useful.
- Persistent terrain geometry capacity also expanded substantially during the high-churn traversal. Treat that as retained reusable capacity, not proof of a leak, but keep it in view while GPU residency is added.
- This evidence is one reason not to spend the next work session polishing the CPU publication path further unless new profiling contradicts it. The active goal is to remove CPU terrain construction work rather than optimize it indefinitely.

## GPU terrain groundwork now present

Read `docs/GPU_TERRAIN_BOUNDARY.md` and `docs/GPU_VOXEL_RESIDENCY_PLAN_2026-09-12.md` before extending this path.

### P7.1 section input ABI — verified

- Default-off `-Dvulkanmod.experimentalSectionVoxels=true` captures a compact state palette, packed voxel indices and four CPU-resolved flag planes during the existing CPU compile loop.
- All voxels remain explicitly `CPU_REQUIRED`; no state is implicitly assumed safe for GPU meshing.
- Immutable numeric snapshots use the existing cancellation-checked publication queue and region-owned CPU staging. They do not retain worlds, models or `BlockState` objects.
- Section generations reject stale data on dirty/reset/release. Dirty can originate on a worker; generation/store invalidation is serialized with publication.
- Region CPU snapshot staging remains independently bounded at 32 MiB payload / 2048 entries. Budget failure invalidates old data and retains CPU rendering.

### Fixed GPU-page allocation groundwork — verified in CI #365

- `RegionVoxelPageAllocator` now provides fixed-capacity, aligned best-fit suballocation for future GPU voxel pages.
- It never grows a page synchronously. Exhaustion is an expected failure mode so the caller can keep the section on CPU fallback.
- Freed ranges coalesce and can satisfy larger later replacements; double-free/invalid ownership checks are covered by tests.
- This allocator is metadata only. It does **not** yet allocate region-owned Vulkan voxel pages by itself.

### Fixed storage upload groundwork — verified in CI #365

- `AreaUploadManager.uploadStorageAsync(...)` can now stage and record a copy into a fixed Vulkan `Buffer` without pretending the destination is an `AreaBuffer.Segment`.
- The upload path uses the existing graphics-queue ordering domain.
- A submission callback can publish CPU-side residency metadata once the copy command has been submitted in queue order; the callback does **not** imply device completion.
- Storage buffers already carry storage plus transfer-source/transfer-destination usage so future upload/readback operations are legal across the supported memory backends.
- Existing geometry uploads continue using the established `AreaBuffer.Segment` ownership path.

### Not implemented yet

Do not overclaim this checkpoint. There is still:

- no region-owned `StorageBuffer` voxel page manager;
- no global GPU voxel-page budget enforcement wired to real Vulkan allocations;
- no per-section GPU residency table (`pageIndex`, offset, length, generation, valid);
- no production `SectionVoxelSnapshot` serialization/upload into a resident GPU page;
- no allocate-then-swap replacement/retirement path for old voxel slices;
- no real GPU readback verification for resident snapshots;
- no transfer-write -> shader-storage-read barrier for a compute consumer;
- no compute pipeline/descriptor-backed voxel decoder;
- no qualified template registry, GPU-generated vertices/indices, GPU visibility, or GPU meshing.

Normal rendering is therefore still CPU-meshed. The new source is infrastructure that makes the first real residency implementation possible without introducing grow-and-stall behavior.

## Immediate next action

1. **Wire actual bounded region-owned GPU voxel pages.** Use fixed-capacity `StorageBuffer` pages under an explicit global GPU budget; never use the growable geometry-buffer fallback for ordinary voxel pressure.
2. **Add the per-section residency table and publication contract.** Track page, byte offset/length, generation and validity. Serialize the existing snapshot ABI into a newly reserved slice, upload it through `uploadStorageAsync`, and publish validity only after the matching copy is submitted in the consuming queue order.
3. **Implement safe replacement/retirement.** Allocate-then-swap; do not overwrite a currently valid slice in place. Old slices must remain unavailable to reuse until the old generation can no longer be consumed.
4. **Add real Vulkan readback verification.** Upload known snapshots, copy the exact resident ranges back to host-visible memory, compare every byte, exercise replacement/generation changes, and prove budget exhaustion falls back instead of growing synchronously. Wait only the test copy fence; never add `vkDeviceWaitIdle` to gameplay terrain updates.
5. **Then add the first compute consumer.** Decode/reduce the resident ABI on the GPU with an explicit transfer-write -> shader-storage-read barrier in graphics-queue order.
6. **After decode correctness is proven, begin removing CPU terrain work.** First target a very small explicitly qualified ordinary-cube/template subset for GPU face rejection and vertex/index emission. Unsupported/mod-dependent geometry remains CPU fallback.

Do not divert back into small CPU publication/worker micro-optimizations unless profiling identifies a new material blocker. The stated direction is to move bulk terrain processing to the GPU.

## Terrain work already verified in CI

### Persistent geometry residency

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

- Normal terrain copies submit on the **graphics queue**, not the dedicated transfer queue.
- Persistent in-place writes are therefore ordered by the same `VkQueue` as old/new terrain draws: old draw -> copy -> new draw.
- Normal terrain uploads do not require a cross-queue transfer semaphore.
- Area-buffer growth copies are also graphics-queue ordered; the synchronous growth path waits its helper fence and explicitly retires that helper before the old buffer is retired.
- CI smoke verifies the normal same-queue path adds no transfer wait semaphore and preserves existing reuse/growth behavior.

## Current F3 terrain measurements

The F3 terrain rows now expose the publication path separately. Important fields include:

- `idle` / `active` / `pubWait` — idle workers, active workers, and workers waiting on publication capacity;
- `queue H/L` — high/low build queues;
- `publish current/limit` — completed results awaiting render-thread publication versus the bounded backlog limit;
- `Terrain build: ok/drop` — accepted versus cancelled/stale completed build publications;
- `ms queue/build/handoff` — average task queue, CPU mesh build and total completed-result handoff latency;
- `Terrain publish: ms wait/work` — completed-result publication queue wait versus actual publication work;
- `early wakes` — workers resumed while a publication drain is still in progress;
- `Terrain upload` — staged terrain-upload statistics;
- `Terrain draw` / `Terrain memory` — current draw-command and persistent geometry residency summaries.

Older `iT/aT/qH/qL/uQ/lat(...)` documentation refers to the previous compact single-line format and should not be used to interpret current screenshots.

## Parked resource-reload issue

The full-pack F3+T memory investigation remains intentionally parked, not solved by the terrain work. Build #310 demonstrated that pre-decode native allocator reclamation can materially reduce RSS after old static atlas retirement, but the later replacement decode still reached the unchanged system/process safety floor under the tested host-memory conditions while GPU/GTT residency rose. Do not weaken the guard or reopen that investigation unless the user reprioritizes it or new evidence makes it block the terrain track.

## Evidence limits

- CI #365 proves compilation, tests, Vulkan startup and the existing compatibility/readback smoke suite under the CI software Vulkan environment. It does **not** prove AMD gameplay performance or GPU voxel residency, because no real voxel pages/consumer exist yet.
- The build #362 RX 6900 XT screenshots are runtime diagnostic evidence for the current CPU terrain pipeline, not an A/B benchmark and not evidence that the new #365 storage-upload plumbing improves performance.
- No FPS, frame-time, chunk-loading or OpenGL-vs-Vulkan performance improvement is claimed for the new voxel-page allocator/upload groundwork.
- Do not claim GPU meshing until a qualified section path bypasses the normal CPU geometry build and renders validated GPU-generated geometry correctly.
