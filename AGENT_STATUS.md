# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. When the protected window permits, aim for roughly 100 lines or fewer; correctness wins over size.

## Repository state

- Latest executable checkpoint is `ac2a9a28b0f5244bcb077e4cff6aed806fc88c65` (`test: lock hybrid section statistics`), validated by CI #676; subsequent `forge-1.20.1` commits are documentation-only `[skip ci]` reconciliation.
- The former GPU-terrain, compatibility, and validation workstreams are consolidated. PR #4 is merged by fast-forward to the exact green head; no merge-only content was introduced.
- CI #676 / run `35337095457` at `ac2a9a28` is fully green: build/distributable, both Vulkan startup smokes, persistent GPU-indirect, vanilla post/depth chains, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Crash Assistant, Chat Heads, and Flywheel all pass.
- The Immersive Portals blocker is closed. VulkanMod now preserves the required vanilla mixin call sites, transforms and compiles IP-aware core shaders through the Vulkan path, rebuilds IP helper shaders after IP initialization/resource reload, and supports std140 `mat3` uniforms without under-sizing or over-reading source storage.
- Still-relevant validation is in the production history. The real Minecraft/Forge `Block.shouldRenderFace` glass/glass disagreement oracle runs in the main smoke flow; current async-completion and dirty-transition coverage supersede the old isolated validation branches.
- Mixed APPEND rebuild omission remains experimental/default-off and transactionally stages CPU exception geometry plus GPU output while retaining the previous complete draw until replacement is ready. Stale/failure paths remain fail-closed.
- `RegionBatchStats.sections` now counts rendered sections rather than indirect commands, so one hybrid APPEND section emitting CPU+GPU commands is counted once; `RegionBatchSmokeTest` locks pending/ready/fallback/visibility cases.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. Active roadmap remains **Phase 7 — GPU-driven terrain and hybrid meshing**; accelerated-default and performance gates remain open.

## Task-relevant references

Always use `AGENTS.md` and the active `ROADMAP.md` gate. Primary terrain contracts remain `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. Live code supersedes older wording that says production GPU dispatch/draw consumption or fresh-section CPU bypass do not exist.

## Current GPU-terrain checkpoint

The bounded compute path classifies qualified ordinary cubes, reconstructs complete 20-byte terrain vertices, and writes exact-generation output directly into persistent `ChunkArea` vertex storage. Unsupported Forge content remains CPU-owned.

`GpuTerrainSectionMesherBridge` can make a fully-qualified **fresh section** GPU-first: workers capture immutable voxel/lighting/preflight inputs, skip ordinary CPU `renderBatched(...)`, preserve expected terrain-layer metadata, and allow exact GPU residency to become the first draw. Qualified REPLACE rebuilds can skip new CPU tessellation while retaining an older complete CPU mesh. Mixed APPEND rebuilds can now omit the conservative GPU-owned subset too: the output-layer CPU exceptions and matching GPU output are staged out of band and atomically replace either a complete CPU fallback or a retained complete APPEND pair.

The synchronous helper-fence wait is validation/smoke-only. Production submission and completion are non-blocking on the render thread.

### Fail-closed publication and lock order

Any current-generation publication, qualification, reservation, submission, readback, output-count, overflow, or generation failure requests ordinary CPU recovery for a GPU-first section. Recovery disables CPU bypass until CPU reconstruction succeeds.

`08622966` fixed a real lock inversion: normal publication takes `RenderSection -> ChunkArea`, while input failure previously attempted `ChunkArea -> RenderSection`. Recovery is deferred until the area monitor is released, and `RegionVoxelGpuStore` construction is inside the fail-closed upload exception path.

### Upload -> compute handoff

`b0beef0` removed the same-frame-slot recycle delay before compute. `AreaUploadManager` publishes input residency after its copy command buffer is submitted, then runs post-submit consumers outside its monitor. `ChunkArea` dispatches meshing from that path.

Voxel, lighting, and model inputs use explicit transfer-write -> shader-read barriers on the same graphics queue, so production order is upload copy -> barrier -> compute without a render-thread fence wait or an `AreaUploadManager -> ChunkArea` lock edge.

### Compute completion and bounded capacity

`1fdae3f`, `27e765ae`, and `49395be` complete the non-blocking lifecycle. Each helper owns a `PendingCompletion` token and fence. Once per render frame the mesher checks helper fences with non-blocking `Synchronization.checkFenceStatus(...)`.

A signaled helper can read its result, publish through exact-generation checks, release result/readback ownership, and return its descriptor slot early. The original `MemoryManager` frame callback remains the guaranteed fallback; an exactly-once token makes it a no-op after early completion. Command-buffer recycling remains owned by the existing main-frame retirement path.

The fixed `MAX_IN_FLIGHT = 32` descriptor pool remains unchanged. Do not enlarge it or add pending-dispatch retries without evidence that saturation materially matters.

## Mixed-section hybrid contract

APPEND supports both fresh/uncompiled sections and qualified rebuilds. Fresh sections may publish their first CPU-exception/GPU pair once both halves are available. Rebuilds use an atomic two-source replacement protocol: stage a generation-bound CPU output-layer allocation (including an explicit empty allocation when no CPU opaque exceptions remain) plus a staged GPU reservation, keep the previous complete draw visible, then switch both halves together on the render thread only after exact GPU completion and CPU upload readiness.

`GpuTerrainHybridMask` derives a conservative ownership plan over all 4096 section cells. Qualified ordinary cubes may become GPU-owned only when interior and not adjacent to visible CPU-owned exception geometry. Visible unsupported block-model geometry, fluids, and block entities remain CPU-owned; invisible exceptions do not poison unrelated neighbors, and boundary demotion does not recursively propagate inward.

APPEND intentionally avoids a voxel ABI bump. The worker creates a filtered **v4** snapshot where only the GPU-owned subset retains `GPU_FULL_CUBE`; state IDs, non-ownership semantic flags, and the exact halo remain unchanged. Sparse-lighting capture runs after filtering.

`RenderSection` stages explicit generation-scoped ownership:

- `REPLACE`: whole-section GPU ownership; legacy/default preflights remain REPLACE.
- `APPEND`: CPU exception geometry and GPU ordinary-cube geometry coexist for one generation.

APPEND is never inferred from CPU mesh presence. Generation invalidation clears/stales the staged ownership contract.

`RegionDrawBatch.FrameBatch` can emit CPU then GPU indirect commands for APPEND. Capacity is bounded at 1024 commands (two per 512 sections). If the CPU exception upload is pending, **neither** half is recorded; both retry together when ready. Stale/missing exact GPU residency keeps/falls back to the CPU side rather than drawing an unmatched GPU half.

The worker prefers stronger whole-section REPLACE qualification first. APPEND is considered only when all three existing acceleration gates plus `-Dvulkanmod.experimentalGpuTerrainHybrid=true` are enabled, REPLACE did not take ownership, and the conservative subset/model/lighting checks succeed. A rebuild may omit the GPU-owned subset only when it has either a complete CPU fallback or an exact retained APPEND pair to keep visible during staging. Repeated dirty APPEND rebuilds may replace pair-to-pair even while CPU recovery is flagged, provided the retained old pair is still exact and complete. Pre-omission failures restore the original snapshot and complete CPU tessellation; later staging/dispatch/completion failures retire staged work and request CPU recovery without exposing an unmatched half.

### Atomic APPEND rebuild transaction

`DrawBuffers` now supports non-visible generation-bound CPU staging, including explicit empty output-layer state; `GpuTerrainOutputStore` supports non-visible staged GPU output for a future generation or the already-advanced current generation. Explicit same-generation invalidation still revokes staged work. `RenderSection` owns the pending CPU stage so generation turnover cannot accidentally commit stale geometry.

`GpuTerrainAppendRebuildTransaction` prevalidates section/generation/ownership, CPU readiness, and GPU readiness. Its render-thread commit swaps GPU residency, CPU draw parameters, and visible APPEND handoff without a fallible operation after the visibility switch. Buffer growth remains safe because `AreaBuffer` drains prior uploads, copies the complete old backing allocation in graphics-queue order, and preserves segment offsets before retiring the old buffer.

CI #662 exposed only a smoke-state collision: a new current-generation oracle reused section slot 7 and advanced it from generation 10 to 70 before an older generation-10 retry assertion. `ead33f4c` isolates that oracle on slot 4. CI #664 then passes both startup variants and the full terrain/renderer sequence through Pick Up Notifier.

## Dirty GPU-first transition contract

`6482799d` fixes a correctness hole in fresh GPU-first sections: their CPU mesh can be absent (REPLACE) or intentionally partial (APPEND), so a dirty rebuild must not revoke the last complete GPU handoff before complete replacement geometry exists. `RenderSection` now distinguishes the build/input generation from the generation currently safe to draw, retains a complete visible GPU handoff during forced CPU recovery, keeps incomplete CPU geometry hidden when its matching GPU half is unavailable, and retires the old GPU output only when complete CPU geometry publishes.

`RegionBatchSmokeTest` covers the APPEND transition end to end: partial fresh CPU exceptions remain hidden, matching GPU publication exposes one complete CPU+GPU pair, dirty invalidation retains that previous pair while advancing the input generation, and complete CPU recovery retires the old GPU output and exposes one complete CPU command. CI #624 exposed a harness-only problem because the normal startup smoke leaves `RegionVoxelStore.ENABLED` false; `f7633ee1` scopes that gate on only for this oracle and restores it afterward. CI #627 passes both startup variants and logs `VULKANMOD_GPU_TERRAIN_TRANSITION_OK` repeatedly. No production fallback semantics were loosened by the harness fix.

## Resolved validation blockers from 2026-09-17

The validation workstream found two real correctness gaps; both are now fixed and regression-covered.

1. **Device-to-host readback visibility:** `e629121e` adds a buffer dependency over the full actual readback range after the transfer copies and before submission: source `TRANSFER / TRANSFER_WRITE`, destination `HOST / HOST_READ`. Production header-only and validation full-payload readbacks use the same helper. `b0e0f241` adds smoke-only execution counting plus a locked stage/access contract; CI #620 ran those assertions successfully without introducing a CPU wait.
2. **Authoritative face semantics:** `56f317e9` makes worker capture compare every candidate direction with `Block.shouldRenderFace(...)` while `RenderChunkRegion` and its halo are available. Any disagreement clears GPU ownership, which makes REPLACE fail closed and APPEND retain that cell plus conservatively protected neighbors on CPU. `b0e0f241` factors the shader/authoritative equivalence predicate into shared production code and exhaustively truth-table tests all four boolean cases.

These findings no longer block the combined Create Chronicles RX functional test. The required Immersive Portals compatibility stack is integrated on the same executable checkpoint and green in CI #676.

## Validation evidence

`661efe51` covers post-submit ordering/outside-lock execution, bounded descriptor saturation, deterministic signaled-helper polling, exact single publication, duplicate suppression when later frame callbacks drain, and validation-hook gating behind `vulkanmod.smokeTest`.

Hybrid-focused commits add tests for conservative cell ownership/demotion, filtered-v4 preservation of all non-ownership data and halo, generation-scoped REPLACE/APPEND staging, and actual mapped APPEND batch layout including CPU-before-GPU ordering, atomic pending-upload suppression, retry, and stale-GPU CPU fallback.

`b0e0f241` additionally proves that both actual section-mesher readback paths record the required transfer-to-host barrier during CI smoke execution, and that the production face-ownership comparison cannot silently invert authoritative visibility semantics. CI #620 exercised these tests successfully.

## Compatibility intersection

Compatibility is no longer an independent workstream. The relevant Forge 1.20.1 compatibility stack is integrated into `forge-1.20.1` at `ac2a9a28` and validated by CI #676.

Immersive Portals 3.0.7 now passes its full runtime smoke, including the stronger clipping proof `VULKANMOD_IP_CLIPPING_SHADER_OK` and helper-shader installation. The prior `RuntimeException: not admitted type: mat3` was the underlying remaining failure; `Field` now represents std140 `mat3` as three vec4-aligned columns and packs Minecraft's nine source floats without source over-read or following-field misalignment.

FTB Library, Pick Up Notifier, Crash Assistant, Chat Heads, and Flywheel compatibility smokes are also green on the same combined head. The old compatibility PR and isolated validation PRs are superseded by the landed production tree and should not be treated as active ownership boundaries.

## Fail-closed boundary

Whole-section CPU bypass requires:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Mixed-section APPEND (fresh or rebuild) additionally requires:

```text
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Arbitrary Forge callbacks, unsupported model work outside the proven ordinary-cube subset, block entities, fluids, translucent/tripwire terrain, stale generations, missing residency, output overflow, invalid ranges, face-predicate disagreement, and failed GPU work must remain CPU/recovery paths.

## Next action

1. Perform the first narrow RX 6900 XT/RADV **functional** terrain test from the landed combined artifact with REPLACE + APPEND enabled:
   `-Dvulkanmod.experimentalGpuTerrainMesher=true`
   `-Dvulkanmod.experimentalGpuTerrainCpuBypass=true`
   `-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true`
   `-Dvulkanmod.experimentalGpuTerrainHybrid=true`
2. In Create Chronicles, exercise normal terrain plus at least one dirty mixed-section rebuild so pair-to-pair APPEND replacement is actually used. Check for missing/duplicated terrain, stale geometry, flicker during rebuild, portal/render regressions, and CPU-recovery behavior. Preserve the relevant log if anything is wrong.
3. Treat this as a correctness test, not an FPS benchmark. If hardware correctness is clean, collect comparable Phase 5/6 performance evidence before making any speedup/default-path claim.
4. Do not enlarge the 32-slot descriptor pool or add dispatch retries without saturation evidence.

Prior user evidence remains valid; do not repeat settled sparse-lighting density collection or unrelated compatibility tests unless new hardware evidence contradicts them.

## Outstanding RX evidence / performance boundary

Prior user evidence remains valid: experimental GPU-indirect consumption had clean initial comparator samples; F3+T and world re-entry worked; FTB Chunks large-map terrain remained black due to its null-`BlockState` map task; the prior center/world-edge artifact disappeared when the death marker was removed. Do not repeat sparse-lighting density telemetry.

Do not claim a speedup yet. CPU tessellation can be bypassed for the fully qualified subset, APPEND now covers fresh sections and transactionally staged mixed rebuilds behind the additional experimental gate, upload-to-compute/completion latency is shortened, and terrain-side CI covers the 2026-09-17 correctness findings plus the atomic rebuild ownership contract. Representative RX 6900 XT/RADV correctness and comparable Phase 5/6 frame-time evidence are still required before any performance or default-path conclusion.
