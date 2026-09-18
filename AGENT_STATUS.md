# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. When the protected window permits, aim for roughly 100 lines or fewer; correctness wins over size.

## Repository state

- Integration target: `forge-1.20.1`; latest consumed live tip is `328018e731baea07ae0245519d4f16953fe0c5ba`.
- GPU-terrain production work remains isolated on `gpu-terrain-continuation-20260917` while compatibility work proceeds concurrently.
- Fresh-hybrid implementation checkpoint before compatibility reconciliation: `8240768ae25c8800edd76be46795c3f36c9a1d05` (`terrain: omit qualified cubes on fresh hybrid builds`).
- First compatibility reconciliation merge: `5327fe80fdbd1058a27ae84261642fc95dd3e490`, with parents `8240768a` and Forge `39f9d9f2`. Its only content conflict was `src/main/resources/vulkanmod.mixins.json`; the resolution preserves Forge compatibility registrations plus `debug.GpuTerrainAsyncCompletionSmokeMixin`.
- Forge then advanced by two documentation commits to `328018e7`; the terrain branch reconciled that checkpoint and fixed both validation findings it carried.
- Latest terrain checkpoint is `ead33f4c2b18332c5dc60d16079133779f5f190f` (`test: isolate current-generation GPU staging oracle`). Draft PR #4 remains isolated from `forge-1.20.1`.
- CI #664 / run `35314625964` at `ead33f4c` passes build/distributable, both Vulkan startup smokes, persistent GPU-indirect, vanilla post/depth chains, screenshot readback, FTB Library, and Pick Up Notifier. Startup-dependent logs repeatedly emit `VULKANMOD_GPU_TERRAIN_CPU_STAGE_OK`, `VULKANMOD_GPU_TERRAIN_APPEND_TRANSACTION_OK`, and `VULKANMOD_GPU_TERRAIN_TRANSITION_OK`. Its only failure is the independently owned old Immersive Portals `Program.compileShaderInternal` call-site conflict still present on PR #4.
- Mixed APPEND rebuild omission is now implemented behind the existing experimental hybrid gate: replacement CPU exception geometry and GPU output stage independently while the previous complete draw remains visible, then commit together only after both halves are ready. Stale/failure paths discard staged allocations and retain/recover CPU-safe output. Terrain-side CI validates the transaction machinery; PR #4 is still not merge-ready as a combined modpack branch because current Immersive Portals helper-shader/reload closure remains on compatibility PR #6.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. Active roadmap remains **Phase 7 — GPU-driven terrain and hybrid meshing**; no accelerated-default or performance gate is closed.

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

These findings no longer block a terrain-only RX functional test. A Create Chronicles test still needs a combined artifact containing the remaining independently validated Immersive Portals compatibility stack.

## Validation evidence

`661efe51` covers post-submit ordering/outside-lock execution, bounded descriptor saturation, deterministic signaled-helper polling, exact single publication, duplicate suppression when later frame callbacks drain, and validation-hook gating behind `vulkanmod.smokeTest`.

Hybrid-focused commits add tests for conservative cell ownership/demotion, filtered-v4 preservation of all non-ownership data and halo, generation-scoped REPLACE/APPEND staging, and actual mapped APPEND batch layout including CPU-before-GPU ordering, atomic pending-upload suppression, retry, and stale-GPU CPU fallback.

`b0e0f241` additionally proves that both actual section-mesher readback paths record the required transfer-to-host barrier during CI smoke execution, and that the production face-ownership comparison cannot silently invert authoritative visibility semantics. CI #620 exercised these tests successfully.

## Compatibility intersection

Live Forge remains `328018e7`. Compatibility work remains independently owned by draft PR #6 (`compat-audit-20260917`), currently observed at `590b8546d72cb49a84e2d9f2298cb765e969c12e` (`compat: preprocess transformed core shader imports`). CI #663 / run `35314367365` passes build, both startup smokes, GPU-indirect, post/depth, screenshot, FTB Library, and Pick Up Notifier. The stronger clipping proof is now **green in the log**: `VULKANMOD_IP_CLIPPING_SHADER_OK: rendertype_solid transformed source, live clipping uniform, Vulkan pipeline`. The IP step still fails later during the compatibility-owned shader rebuild because VulkanMod's uniform layout rejects `mat3` (`RuntimeException: not admitted type: mat3`), after which the smoke reports helper shader `drawFbInAreaShader` was not installed. Treat helper-shader/reload closure, not clipping proof, as the remaining compatibility blocker.

To move PR #4's combined smoke far enough to validate the terrain fixes, this branch selectively consumed the first two compatibility fixes: `1c7e0e1a` preserves vanilla `MainTarget.createFrameBuffer` call sites and `7ff35811` preserves `LevelRenderer.allChanged` call sites while cancelling the unsupported vanilla paths at runtime. CI #620 then reached the next known IP-owned boundary: VulkanMod's `ProgramM` overwrite removes the shader-source call site IP wraps. PR #6 already fixes that and later shader/uniform/clip-distance compatibility; do not duplicate the remainder into terrain production merely to make PR #4 green. Reconcile when the compatibility branch lands or when an explicit combined validation branch is appropriate.

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

1. Keep PR #4 isolated while compatibility PR #6 is active. Terrain mixed-rebuild validation is green through CI #664; do not duplicate PR #6's remaining helper-shader/reload work into terrain merely to turn PR #4 green. Re-fetch live Forge and PR #6 before reconciliation.
2. Wait for PR #6 to close the current `mat3`/helper-shader reload failure and obtain a green Immersive Portals compatibility step. The clipping-pipeline proof itself is already present in CI #663.
3. Once a combined artifact contains that green compatibility head plus terrain `ead33f4c` or its reconciled descendant, perform the first narrow RX 6900 XT/RADV **functional** terrain test with REPLACE + APPEND enabled. Include at least one dirty mixed-section rebuild so pair-to-pair atomic replacement is exercised; check visual completeness, recovery behavior, and logs. Do not treat it as an FPS benchmark.
4. If hardware correctness is clean, collect comparable Phase 5/6 performance evidence before making any speedup/default-path claim.
5. Do not enlarge the 32-slot descriptor pool or add dispatch retries without saturation evidence.

Do not ask the user to remove Immersive Portals merely to test the target pack; prefer waiting for/reconciling the compatibility stack after its new clipping-pipeline proof is green.

## Outstanding RX evidence / performance boundary

Prior user evidence remains valid: experimental GPU-indirect consumption had clean initial comparator samples; F3+T and world re-entry worked; FTB Chunks large-map terrain remained black due to its null-`BlockState` map task; the prior center/world-edge artifact disappeared when the death marker was removed. Do not repeat sparse-lighting density telemetry.

Do not claim a speedup yet. CPU tessellation can be bypassed for the fully qualified subset, APPEND now covers fresh sections and transactionally staged mixed rebuilds behind the additional experimental gate, upload-to-compute/completion latency is shortened, and terrain-side CI covers the 2026-09-17 correctness findings plus the atomic rebuild ownership contract. Representative RX 6900 XT/RADV correctness and comparable Phase 5/6 frame-time evidence are still required before any performance or default-path conclusion.
