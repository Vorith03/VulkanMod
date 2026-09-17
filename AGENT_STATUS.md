# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and the linked design/evidence documents; keep this file focused on what the next work session needs.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; compaction must not rewrite or summarize it except to correct a factual error. Use the user's local calendar date when known, otherwise the repository commit date.
- Once material ages out of that window, collapse it into the durable current-state sections instead of retaining a chronological diary. Preserve only facts that still affect decisions: validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and pointers to focused evidence documents.
- Prefer removing superseded chronology, stale next steps, obsolete artifact/run detail, and repeated implementation narrative already preserved by Git, CI, or focused design/evidence documents.
- Review for compaction only when material has actually aged out of the protected window and shortening the file would materially help. Do not perform a no-op compaction review merely because a chat rolled over. Normally review at most once per calendar day. After compaction, aim for roughly **100 lines or fewer** when the protected recent window permits; correctness and irreplaceable evidence take precedence over size.

## Repository state

- Integration target: `forge-1.20.1`.
- GPU-terrain production work is intentionally isolated on `gpu-terrain-continuation-20260917` while compatibility work proceeds concurrently on Forge.
- Terrain-only recovery base: `e7db1ad4d23ebca93d35882f2e28c97a5bf774e3` (`test: restore terrain upload frame after retirement smoke`).
- Current GPU-terrain branch head: `b0beef0ca8bca8871e26293813580a0a4a85daef` (`terrain: dispatch meshing after input upload submission`), plus the preceding lock-order and post-submit infrastructure commits.
- Draft PR #4 targets live `forge-1.20.1` so CI validates the terrain work with the concurrent compatibility changes. At this checkpoint it is mergeable but must remain unmerged until combined CI is fully green and live Forge has been reconciled again.
- CI #589 / run `35267716153` is in progress for `b0beef0`; the Gradle build and first Vulkan startup smoke have passed. Do not call the run green until the remaining smokes finish.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**. No accelerated-default or performance gate is closed yet.

## Task-relevant references

Always use `AGENTS.md` and the active `ROADMAP.md` gate. For the current slice, the primary contracts are `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. Live code supersedes older wording that says production GPU dispatch/draw consumption or fresh-section CPU bypass do not exist.

## Current GPU-terrain checkpoint

The bounded compute path classifies qualified ordinary cubes, reconstructs complete 20-byte terrain vertices, and writes exact-generation output directly into persistent `ChunkArea` vertex storage. Unsupported/mixed Forge content remains on CPU.

`GpuTerrainSectionMesher` has a non-blocking production dispatch path. `GpuTerrainSectionMesherBridge` can now make a fully-qualified **fresh section** GPU-first: the worker captures immutable voxel/lighting/preflight inputs, skips ordinary CPU `renderBatched(...)`, preserves the expected terrain layer in compiled metadata, and lets exact GPU residency become the section's first draw. Rebuilds may likewise bypass new CPU tessellation while retaining an older CPU mesh until replacement succeeds.

The synchronous fence-wait dispatcher remains validation/smoke-only. Production completion is consumed after fence-safe helper execution; output reservations and resident slices remain physically pinned across invalidation until GPU/frame retirement makes reuse safe.

### Fresh-section fail-closed recovery

Fresh GPU-first sections no longer rely on a pre-existing CPU draw for safety. Instead, any current-generation input publication, qualification, reservation, submission, readback, output-count, overflow, or generation failure requests an ordinary CPU recovery rebuild. Recovery disables CPU bypass until that CPU rebuild succeeds.

The September 17 continuation found and fixed a real lock-order hazard in this recovery path. Normal publication holds `RenderSection -> ChunkArea`, while the original input-failure recovery attempted `ChunkArea -> RenderSection`. `08622966` now defers recovery onto the terrain frame queue so the `ChunkArea` monitor is released before section state is acquired. The same fix includes `RegionVoxelGpuStore` construction in the fail-closed upload exception path.

### Upload-to-compute latency

`b0beef0` removes an unnecessary same-frame-slot recycle delay before production meshing. Voxel and sparse-lighting residency is intentionally published once the upload command buffer has been **submitted** in graphics-queue order; it does not require device completion before a later same-queue consumer is submitted.

`AreaUploadManager` now supports post-upload-submission consumers that are made ready only after the upload-residency callbacks run, then executes those consumers outside the manager monitor. `ChunkArea` uses this path for GPU-terrain dispatch. The resulting queue order is upload copy -> transfer-write/shader-read barrier -> compute dispatch, without a render-thread fence wait and without introducing `AreaUploadManager -> ChunkArea` lock inversion.

The compute barrier has been rechecked: voxel, lighting and model inputs use `VK_ACCESS_TRANSFER_WRITE_BIT -> VK_ACCESS_SHADER_READ_BIT` with transfer/compute stages, so the same-queue handoff is explicit Vulkan synchronization rather than relying on submission order alone.

### Remaining completion latency / bounded capacity

Production compute completion is still guaranteed by a frame-slot `MemoryManager` callback. The helper compute command buffer also has its own fence, and `Synchronization.checkFenceStatus(...)` is available. A promising next production slice is to poll those helper fences non-blockingly at a render-frame safe point, publish completed output and release the 32 descriptor slots earlier, while retaining the existing frame-slot callback as the correctness fallback. Do not replace this with a blocking fence wait or an unbounded retry loop.

The fixed `MAX_IN_FLIGHT = 32` pool should not be enlarged speculatively. Earlier completion may solve most transient saturation naturally; use diagnostics/validation evidence before changing the bound or adding a pending-dispatch queue.

## Fail-closed boundary

All accelerated pieces remain opt-in. CPU bypass requires all three properties:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Arbitrary Forge callbacks, unsupported or mixed models, block entities, fluids, translucent/tripwire terrain, stale generations, missing residency, output overflow, invalid draw ranges and failed GPU work must remain CPU/recovery paths. Sparse GPU lighting remains enabled by default unless explicitly disabled separately.

## Validation / parallel-work state

A separate user-authorized validation/CI thread is concurrently reviewing GPU-terrain synchronization, lifetime, race, fallback, allocation and publication hazards and may add focused tests/diagnostics. This production thread remains the owner of substantive GPU-terrain architecture. Treat findings from the validation thread as evidence to integrate, not as a competing implementation.

Do not ask for another RX 6900 XT test merely because this checkpoint changed. First finish combined CI and consume any materially relevant validation-thread findings. A new user-machine test is warranted only when it answers a hardware/runtime question that CI and existing evidence cannot answer.

## Outstanding RX evidence / performance boundary

Prior user evidence remains valid: experimental GPU-indirect consumption had clean initial comparator samples; F3+T and world re-entry worked; FTB Chunks large-map terrain remained black due to its null-`BlockState` map task; the prior center/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

Do not claim a speedup yet. Fresh-section CPU tessellation can now be bypassed for the qualified subset and the upload-to-compute submission delay has been shortened, but representative RADV correctness plus comparable Phase 5/6 frame-time evidence are still required before any performance or accelerated-default conclusion.
