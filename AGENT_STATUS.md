# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and the linked design/evidence documents; keep this file focused on what the next work session needs.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; compaction must not rewrite or summarize it except to correct a factual error. Use the user's local calendar date when known, otherwise the repository commit date.
- Once material ages out of that window, collapse it into the durable current-state sections instead of retaining a chronological diary. Preserve only facts that still affect decisions: validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and pointers to focused evidence documents.
- Prefer removing superseded chronology, stale next steps, obsolete artifact/run detail, and repeated implementation narrative already preserved by Git, CI, or focused design/evidence documents.
- Review for compaction only when material has actually aged out of that window and shortening the file would materially help. Normally review at most once per calendar day.

## Repository state

- Integration target: `forge-1.20.1`; last checked live tip `39f9d9f2cc78b1ca6db193804f3a6bb6f976c6ac`.
- GPU-terrain production work remains isolated on `gpu-terrain-continuation-20260917` while compatibility work proceeds concurrently.
- Terrain-only recovery base: `e7db1ad4d23ebca93d35882f2e28c97a5bf774e3`.
- Fresh-hybrid implementation checkpoint before reconciliation: `8240768ae25c8800edd76be46795c3f36c9a1d05` (`terrain: omit qualified cubes on fresh hybrid builds`).
- Compatibility reconciliation merge: `5327fe80fdbd1058a27ae84261642fc95dd3e490` (`merge: reconcile Forge compatibility with GPU terrain`), with parents `8240768a` and live Forge `39f9d9f2`. The only content conflict was `src/main/resources/vulkanmod.mixins.json`; the resolution preserves Forge's current compatibility registrations plus `debug.GpuTerrainAsyncCompletionSmokeMixin`.
- Draft PR #4 targets `forge-1.20.1`. Keep it unmerged until the reconciled hybrid head is validated and any newer material validation-thread findings are consumed.
- The last fully observed terrain validation before hybrid expansion was CI #603 / run `35271183491` at `661efe51`; it passed build, both Vulkan startup smokes, persistent GPU-indirect smoke, vanilla post/depth chains, screenshot, FTB Library, and Pick Up Notifier, then failed at the independently owned Immersive Portals compatibility smoke.
- **The fresh-hybrid/reconciled head has not yet completed CI.** Do not treat `8240768a`, `5327fe80`, or later documentation-only descendants as validated until a new PR run exercises them.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. Active roadmap remains **Phase 7 — GPU-driven terrain and hybrid meshing**; no accelerated-default or performance gate is closed.

## Task-relevant references

Always use `AGENTS.md` and the active `ROADMAP.md` gate. For this subsystem, primary contracts remain `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. Live code supersedes older wording that says production GPU dispatch/draw consumption or fresh-section CPU bypass do not exist.

## Current GPU-terrain checkpoint

The bounded compute path classifies qualified ordinary cubes, reconstructs complete 20-byte terrain vertices, and writes exact-generation output directly into persistent `ChunkArea` vertex storage. Unsupported Forge content remains CPU-owned.

`GpuTerrainSectionMesherBridge` can make a fully-qualified **fresh section** GPU-first: workers capture immutable voxel/lighting/preflight inputs, skip ordinary CPU `renderBatched(...)`, preserve the expected terrain layer in compiled metadata, and allow exact GPU residency to become the first draw. Rebuilds may likewise bypass new CPU tessellation while retaining an older CPU mesh until replacement succeeds.

The synchronous helper-fence wait remains validation/smoke-only. Production submission and completion are both non-blocking on the render thread.

### Fail-closed publication and lock order

Any current-generation input publication, qualification, reservation, submission, readback, output-count, overflow, or generation failure requests an ordinary CPU recovery rebuild for a GPU-first section. Recovery disables CPU bypass until that CPU rebuild succeeds.

`08622966` fixed a real publication/recovery lock inversion: normal publication takes `RenderSection -> ChunkArea`, while input failure previously attempted `ChunkArea -> RenderSection`. Recovery is now deferred until the area monitor is released. `RegionVoxelGpuStore` construction also sits inside the fail-closed upload exception path.

### Upload -> compute handoff

`b0beef0` removed the old same-frame-slot recycle delay before compute. `AreaUploadManager` publishes input residency after the upload command buffer is submitted, then runs post-submit consumers outside its monitor. `ChunkArea` submits meshing from that post-submit path.

The Vulkan dependency is explicit: voxel, lighting and model inputs use transfer-write -> shader-read barriers, and upload plus compute use the same graphics queue. The production order is therefore upload copy -> explicit barrier -> compute, without a render-thread fence wait or an `AreaUploadManager -> ChunkArea` lock edge.

### Compute completion and bounded capacity

`1fdae3f`, `27e765ae`, and `49395be` complete the non-blocking production lifecycle. Each submitted helper retains its own `PendingCompletion` token and Vulkan fence. Once per render frame, after existing terrain frame operations and before terrain draw recording, the mesher polls those fences with `Synchronization.checkFenceStatus(...)` only.

A signaled helper can read back its header, publish through the existing exact-generation bridge checks, release result/readback ownership, and return its descriptor slot immediately. An unsignaled helper remains untouched. The original `MemoryManager` frame callback is still scheduled for every helper and is the guaranteed fallback; an exactly-once token guard makes it a no-op after early polling.

Helper **command-buffer recycling remains owned by the existing main-frame retirement path**; early completion does not reset/reuse the command buffer. This keeps fence/descriptor/resource lifetime responsibilities separated.

The fixed `MAX_IN_FLIGHT = 32` descriptor pool remains unchanged. Do not enlarge it or add a pending-dispatch retry queue without evidence that saturation materially matters; earlier completion should first be evaluated on the target driver.

## Fresh mixed-section hybrid contract

The first hybrid implementation is deliberately restricted to **fresh/uncompiled sections**. Existing mixed-section rebuilds remain CPU-complete because publishing a new partial CPU mesh before its matching GPU half is ready would create transient holes; an atomic two-source replacement protocol is required before rebuild omission is safe.

`GpuTerrainHybridMask` derives a conservative ownership plan over all 4096 section cells. A qualified ordinary cube can be GPU-owned only when it remains interior and is not adjacent to visible CPU-owned exception geometry. Visible unsupported block-model geometry, fluids, and block entities remain CPU-owned and conservatively demote neighboring GPU candidates where required. Invisible exceptions do not poison unrelated neighbors, and boundary demotion does not recursively propagate inward.

The implementation intentionally avoids a voxel ABI bump. For APPEND ownership, the worker creates a filtered **v4** snapshot in which only the GPU-owned subset retains `GPU_FULL_CUBE`; state IDs, all non-ownership semantic flags, and the exact halo are preserved. Existing shader decoding therefore emits only the retained GPU subset without new descriptor/version plumbing. Sparse-lighting capture runs after filtering, so lighting demand follows the GPU-owned subset.

`RenderSection` stages an explicit generation-scoped ownership mode:

- `REPLACE`: existing whole-section GPU ownership; legacy/default preflights remain REPLACE.
- `APPEND`: CPU exception geometry and GPU ordinary-cube geometry coexist for the same section generation.

APPEND is never inferred from the presence of a CPU mesh. Generation invalidation clears/stales the ownership contract just like the other staged preflight data.

The live region batch can emit CPU then GPU indirect commands for APPEND sections. Capacity is bounded at 1024 commands (at most two commands for each of 512 sections). If the CPU exception upload is still pending, the batch records **neither** half; both are retried together when ready. If exact GPU residency is stale/unavailable, the draw path retains/falls back to the CPU side rather than drawing an unmatched GPU half.

The worker always prefers the stronger whole-section REPLACE qualification first. Hybrid APPEND is considered only when:

1. the three existing experimental acceleration gates are enabled;
2. `-Dvulkanmod.experimentalGpuTerrainHybrid=true` is also enabled;
3. the section is fresh/uncompiled;
4. full REPLACE qualification did not take ownership;
5. the conservative hybrid planner produces a valid non-empty GPU subset and all subset-specific bridge/model/lighting checks pass.

If hybrid planning, model qualification, filtered lighting capture, publication, dispatch, completion, or output validation fails, the path remains fail-closed: before CPU omission the worker restores the original snapshot and performs complete CPU tessellation; after GPU-first publication has been staged, failure requests a complete CPU recovery rebuild.

## Validation evidence

`661efe51` incorporates focused asynchronous-completion validation derived from the parallel validation thread:

- post-submit input publication precedes the consumer and the consumer runs outside the upload-manager monitor;
- the bounded descriptor pool saturates and rejects the next submission rather than growing unboundedly;
- test-only `Vulkan.waitIdle()` makes helper fences deterministically signaled, after which the production non-blocking poll consumes every accepted completion;
- exact output publishes once;
- the later normal frame-slot callbacks drain without delivering duplicate completion;
- the validation frame mixin is gated by `vulkanmod.smokeTest`, so normal gameplay pays no per-frame test-hook cost.

Hybrid-focused tests added after that checkpoint cover:

- conservative cell ownership/demotion, including non-propagating boundary demotion and visible-vs-invisible CPU exceptions;
- filtered-v4 projection preserving every state/semantic/halo value except `GPU_FULL_CUBE` ownership;
- generation-scoped REPLACE/APPEND staging and invalidation;
- the real mapped `FrameBatch` APPEND layout, CPU-before-GPU ordering, atomic suppression while the CPU upload is pending, retry when ready, and stale-GPU CPU fallback.

These hybrid tests are committed but are **not yet backed by a completed reconciled CI run**. Treat them as implementation evidence, not a closed validation gate.

The parallel validation thread independently reached the same later-frame polling direction; no newer production hazard had been identified at the latest consumed delta.

## Compatibility intersection

Live Forge compatibility changes through `39f9d9f2` are now present in the isolated terrain branch via the two-parent reconciliation merge. The merge graph has live Forge as its exact merge base and the resulting diff against Forge contains only the intended terrain files.

Immersive Portals compatibility remains independently owned by the compatibility workstream. Do not redesign or weaken terrain ownership merely to make an unrelated compatibility smoke pass. If the reconciled PR reaches the same later Immersive Portals failure after its terrain/build/Vulkan gates pass, classify that result against the live compatibility evidence rather than treating it as a terrain regression.

## Fail-closed boundary

All accelerated pieces remain opt-in. Whole-section CPU bypass requires all three properties:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Fresh mixed-section APPEND additionally requires:

```text
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Arbitrary Forge callbacks, unsupported model work outside the filtered ordinary-cube subset, block entities, fluids, translucent/tripwire terrain, stale generations, missing residency, output overflow, invalid draw ranges and failed GPU work must remain CPU/recovery paths. Sparse GPU lighting remains enabled by default unless explicitly disabled separately.

## Next action

Restore and inspect combined PR CI for the reconciled fresh-hybrid head before adding another production architecture slice. Fix the smallest terrain-side compile/test/smoke failure if one appears. If all terrain/build/Vulkan gates pass and the run stops only at a known independently owned compatibility fixture, record that distinction rather than widening terrain scope.

After the fresh-hybrid path has a combined green terrain validation baseline, add any missing worker-level smoke/diagnostic coverage needed to prove fresh APPEND omission and fail-closed recovery. Do **not** expand APPEND to mixed rebuilds until an atomic CPU+GPU replacement/publication protocol is explicitly designed and regression-covered.

Do not request a performance A/B yet. A user-machine test should be requested only when CI/validation leaves a genuinely hardware-specific correctness question. The first such RX 6900 XT/RADV test should be narrowly functional—fresh GPU-first/APPEND publication, early completion, draw handoff, and CPU recovery—not an FPS benchmark.

## Outstanding RX evidence / performance boundary

Prior user evidence remains valid: experimental GPU-indirect consumption had clean initial comparator samples; F3+T and world re-entry worked; FTB Chunks large-map terrain remained black due to its null-`BlockState` map task; the prior center/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

Do not claim a speedup yet. Fresh-section CPU tessellation can now be bypassed for the fully-qualified subset, a fresh mixed-section APPEND path exists behind an additional experimental gate, and both upload-to-compute and compute-to-publication latency are shortened. Representative RADV correctness plus comparable Phase 5/6 frame-time evidence remain required before any performance or accelerated-default conclusion.
