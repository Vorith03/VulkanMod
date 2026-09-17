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
- Forge then advanced by two documentation commits to `328018e7`. That delta changes only `AGENT_STATUS.md` but carries two material terrain-validation findings documented below; consume them before any RX test.
- Draft PR #4 targets `forge-1.20.1`. Keep it unmerged until the reconciled hybrid head is validated and any newer material validation-thread findings are consumed.
- Last fully observed terrain validation before hybrid expansion: CI #603 / run `35271183491` at `661efe51`. It passed build, both Vulkan startup smokes, persistent GPU-indirect smoke, vanilla post/depth chains, screenshot, FTB Library, and Pick Up Notifier, then failed at the independently owned Immersive Portals compatibility smoke.
- **The fresh-hybrid path has not yet completed a reconciled CI run.** Do not treat `8240768a` or its descendants as validated merely because earlier terrain checkpoints passed.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. Active roadmap remains **Phase 7 — GPU-driven terrain and hybrid meshing**; no accelerated-default or performance gate is closed.

## Task-relevant references

Always use `AGENTS.md` and the active `ROADMAP.md` gate. Primary terrain contracts remain `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. Live code supersedes older wording that says production GPU dispatch/draw consumption or fresh-section CPU bypass do not exist.

## Current GPU-terrain checkpoint

The bounded compute path classifies qualified ordinary cubes, reconstructs complete 20-byte terrain vertices, and writes exact-generation output directly into persistent `ChunkArea` vertex storage. Unsupported Forge content remains CPU-owned.

`GpuTerrainSectionMesherBridge` can make a fully-qualified **fresh section** GPU-first: workers capture immutable voxel/lighting/preflight inputs, skip ordinary CPU `renderBatched(...)`, preserve expected terrain-layer metadata, and allow exact GPU residency to become the first draw. Qualified rebuilds can likewise skip new CPU tessellation while retaining an older CPU mesh until replacement succeeds.

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

## Fresh mixed-section hybrid contract

The first hybrid implementation is deliberately restricted to **fresh/uncompiled sections**. Mixed-section rebuilds remain CPU-complete because publishing a new partial CPU mesh before its matching GPU half is ready would create transient holes; an atomic two-source replacement protocol is required before rebuild omission is safe.

`GpuTerrainHybridMask` derives a conservative ownership plan over all 4096 section cells. Qualified ordinary cubes may become GPU-owned only when interior and not adjacent to visible CPU-owned exception geometry. Visible unsupported block-model geometry, fluids, and block entities remain CPU-owned; invisible exceptions do not poison unrelated neighbors, and boundary demotion does not recursively propagate inward.

APPEND intentionally avoids a voxel ABI bump. The worker creates a filtered **v4** snapshot where only the GPU-owned subset retains `GPU_FULL_CUBE`; state IDs, non-ownership semantic flags, and the exact halo remain unchanged. Sparse-lighting capture runs after filtering.

`RenderSection` stages explicit generation-scoped ownership:

- `REPLACE`: whole-section GPU ownership; legacy/default preflights remain REPLACE.
- `APPEND`: CPU exception geometry and GPU ordinary-cube geometry coexist for one generation.

APPEND is never inferred from CPU mesh presence. Generation invalidation clears/stales the staged ownership contract.

`RegionDrawBatch.FrameBatch` can emit CPU then GPU indirect commands for APPEND. Capacity is bounded at 1024 commands (two per 512 sections). If the CPU exception upload is pending, **neither** half is recorded; both retry together when ready. Stale/missing exact GPU residency keeps/falls back to the CPU side rather than drawing an unmatched GPU half.

The worker prefers stronger whole-section REPLACE qualification first. APPEND is considered only when all three existing acceleration gates plus `-Dvulkanmod.experimentalGpuTerrainHybrid=true` are enabled, the section is fresh, REPLACE did not take ownership, and the conservative subset/model/lighting checks succeed. Pre-omission failures restore the original snapshot and complete CPU tessellation; later GPU failures request complete CPU recovery.

## Material validation blockers discovered 2026-09-17

Two correctness issues found by the validation workstream block RX testing even if ordinary CI passes:

1. **GPU result readback needs an explicit device-to-host dependency.** `GpuTerrainSectionMesher.submitDispatch()` copies result data into a `HOST_VISIBLE | HOST_COHERENT` readback buffer but currently lacks a final `VK_ACCESS_TRANSFER_WRITE_BIT -> VK_ACCESS_HOST_READ_BIT` barrier before fence completion/host mapping. Fence completion establishes execution completion; host coherence does not replace the availability/visibility dependency. Add a buffer memory barrier over the entire readback range (including validation-only vertex payload), source stage/access `TRANSFER / TRANSFER_WRITE`, destination `HOST / HOST_READ`, without introducing a CPU wait.
2. **CPU-bypass face decisions are not yet proven equivalent to Minecraft/Forge semantics.** Captured `SOLID_RENDER` is a VisGraph property, not proof that `Block.shouldRenderFace(...)`, `skipRendering`, or face-occlusion callbacks agree with the GPU shader's neighbor predicate. While the worker still owns `RenderChunkRegion` plus halo, compare every candidate cube/direction against authoritative `Block.shouldRenderFace(currentState, region, pos, direction, neighborPos)`. REPLACE may omit CPU tessellation only when every candidate face agrees; APPEND must demote candidates whose face decisions disagree. Arbitrary Forge callbacks stay CPU-owned.

Do not request user-machine terrain validation until both issues are fixed and regression-covered.

## Validation evidence

`661efe51` covers post-submit ordering/outside-lock execution, bounded descriptor saturation, deterministic signaled-helper polling, exact single publication, duplicate suppression when later frame callbacks drain, and validation-hook gating behind `vulkanmod.smokeTest`.

Hybrid-focused commits add tests for conservative cell ownership/demotion, filtered-v4 preservation of all non-ownership data and halo, generation-scoped REPLACE/APPEND staging, and actual mapped APPEND batch layout including CPU-before-GPU ordering, atomic pending-upload suppression, retry, and stale-GPU CPU fallback.

Those hybrid tests are committed but **not yet backed by a completed reconciled CI run**. Existing CI does not prove the two blockers above.

## Compatibility intersection

The terrain branch has consumed Forge compatibility through `39f9d9f2`; the subsequent `39f9d9f2 -> 328018e7` delta is documentation-only and exists to carry the validation findings above. Immersive Portals implementation remains owned by the compatibility workstream. Do not redesign or weaken terrain ownership merely to make an unrelated compatibility smoke pass.

## Fail-closed boundary

Whole-section CPU bypass requires:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Fresh mixed-section APPEND additionally requires:

```text
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Arbitrary Forge callbacks, unsupported model work outside the proven ordinary-cube subset, block entities, fluids, translucent/tripwire terrain, stale generations, missing residency, output overflow, invalid ranges, face-predicate disagreement, and failed GPU work must remain CPU/recovery paths.

## Next action

1. Merge live Forge `328018e7` into the isolated terrain branch while preserving this combined checkpoint.
2. Add the narrow transfer-write -> host-read barrier to terrain readback and focused regression/smoke coverage.
3. Add worker-side authoritative face-predicate validation while `RenderChunkRegion` is available; make REPLACE fail closed and APPEND demote unsafe candidates.
4. Restore combined PR CI and fix the smallest terrain-side compile/test/smoke issue. If later failure is only the independently owned compatibility fixture, record that distinction instead of widening terrain scope.
5. Do not expand APPEND to mixed rebuilds until an atomic CPU+GPU replacement protocol is explicitly designed and covered.

Do not request a performance A/B yet. The first RX 6900 XT/RADV test should occur only after both correctness blockers and reconciled CI are green enough to leave a genuinely hardware-specific question, and it should be functional rather than an FPS benchmark.

## Outstanding RX evidence / performance boundary

Prior user evidence remains valid: experimental GPU-indirect consumption had clean initial comparator samples; F3+T and world re-entry worked; FTB Chunks large-map terrain remained black due to its null-`BlockState` map task; the prior center/world-edge artifact disappeared when the death marker was removed. Do not repeat sparse-lighting density telemetry.

Do not claim a speedup yet. Fresh-section CPU tessellation can be bypassed for the fully qualified subset, fresh APPEND exists behind an additional experimental gate, and upload-to-compute/completion latency is shortened, but representative RADV correctness and comparable Phase 5/6 frame-time evidence are still required before any performance or default-path conclusion.
