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
- **Code/test checkpoint:** `661efe514419ea7d0333c5b75cbf9d761a3a23df` (`test: validate polled GPU terrain completion lifecycle`).
- Draft PR #4 targets `forge-1.20.1`. Keep it unmerged until current compatibility work is reconciled and any newer validation-thread findings are consumed.
- CI #603 / run `35271183491` validated the code/test checkpoint through build, both Vulkan startup smokes, persistent GPU-indirect smoke, vanilla post/depth chains, screenshot, FTB Library, and Pick Up Notifier. The workflow is **not globally green**: it fails later at the independently owned Immersive Portals compatibility smoke; subsequent compatibility fixtures are skipped.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. Active roadmap remains **Phase 7 — GPU-driven terrain and hybrid meshing**; no accelerated-default or performance gate is closed.

## Task-relevant references

Always use `AGENTS.md` and the active `ROADMAP.md` gate. For this subsystem, primary contracts remain `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. Live code supersedes older wording that says production GPU dispatch/draw consumption or fresh-section CPU bypass do not exist.

## Current GPU-terrain checkpoint

The bounded compute path classifies qualified ordinary cubes, reconstructs complete 20-byte terrain vertices, and writes exact-generation output directly into persistent `ChunkArea` vertex storage. Unsupported/mixed Forge content remains CPU-owned.

`GpuTerrainSectionMesherBridge` can make a fully-qualified **fresh section** GPU-first: workers capture immutable voxel/lighting/preflight inputs, skip ordinary CPU `renderBatched(...)`, preserve the expected terrain layer in compiled metadata, and allow exact GPU residency to become the first draw. Rebuilds may likewise bypass new CPU tessellation while retaining an older CPU mesh until replacement succeeds.

The synchronous helper-fence wait remains validation/smoke-only. Production submission and completion are now both non-blocking on the render thread.

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

## Validation evidence

`661efe51` incorporates focused validation derived from the parallel validation thread without importing unrelated compatibility work:

- post-submit input publication precedes the consumer and the consumer runs outside the upload-manager monitor;
- the bounded descriptor pool saturates and rejects the next submission rather than growing unboundedly;
- test-only `Vulkan.waitIdle()` makes helper fences deterministically signaled, after which the production non-blocking poll consumes every accepted completion;
- exact output publishes once;
- the later normal frame-slot callbacks drain without delivering duplicate completion;
- the validation frame mixin is gated by `vulkanmod.smokeTest`, so normal gameplay pays no per-frame test-hook cost.

CI #603 passed both startup variants containing this lifecycle proof, plus the renderer smokes listed above. Its later Immersive Portals failure is a separate compatibility problem, not evidence of terrain failure.

The parallel validation thread independently reached the same later-frame polling direction; no newer production hazard was identified in the latest consumed delta.

## Compatibility intersection

The compatibility branch has added an Immersive Portals `LevelRenderer` opt-out that forces IP's `ip_allowOverrideTerrainSetup()` helper false, keeping VulkanMod terrain visibility/setup authoritative. That boundary is compatible with this GPU-terrain ownership model and does not alter section generations, compute completion, output residency, or CPU recovery.

Do not absorb unrelated Immersive Portals implementation into this terrain branch merely to make PR #4 globally green. Reconcile against live Forge once the compatibility work reaches its own durable/validated point.

## Fail-closed boundary

All accelerated pieces remain opt-in. CPU bypass requires all three properties:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Arbitrary Forge callbacks, unsupported or mixed models, block entities, fluids, translucent/tripwire terrain, stale generations, missing residency, output overflow, invalid draw ranges and failed GPU work must remain CPU/recovery paths. Sparse GPU lighting remains enabled by default unless explicitly disabled separately.

## Next action

Do not request a performance A/B yet. First consume any newer material finding from the validation thread and reconcile the terrain branch with the compatibility result when it is ready. Once a combined build can exercise the target modpack without the known Immersive Portals fixture failure, the next user-machine evidence should be a narrow RX 6900 XT/RADV **functional** test of fresh GPU-first publication, early completion, draw handoff, and CPU recovery—not an FPS benchmark.

If independent production work continues before reconciliation, keep it bounded and evidence-driven. The most useful next architecture work is the remaining Phase 7 hybrid boundary: determine how qualified ordinary-cube GPU output can coexist with CPU-owned unsupported geometry inside a mixed section without weakening Forge callback semantics, output ownership, or fail-closed recovery. Do not begin by simply skipping qualified blocks inside mixed sections; define a safe two-source publication/merge contract first.

## Outstanding RX evidence / performance boundary

Prior user evidence remains valid: experimental GPU-indirect consumption had clean initial comparator samples; F3+T and world re-entry worked; FTB Chunks large-map terrain remained black due to its null-`BlockState` map task; the prior center/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

Do not claim a speedup yet. Fresh-section CPU tessellation can now be bypassed for the fully-qualified subset and both upload-to-compute and compute-to-publication latency are shortened, but representative RADV correctness plus comparable Phase 5/6 frame-time evidence remain required before any performance or accelerated-default conclusion.
