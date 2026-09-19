# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. When the protected window permits, aim for roughly 100 lines or fewer; correctness wins over size.

## Repository state

- Latest executable checkpoint is `94c459f17f452320193e532535ed31137e794801` (`compat: restore Forge vertex consumer contracts`), fully validated by CI #691 / run `35424374238`.
- The former GPU-terrain, compatibility, and validation workstreams are consolidated. PR #4 is merged by fast-forward; subsequent compatibility/preflight work is directly on `forge-1.20.1`.
- CI #691 is fully green on one coherent executable tree: build/distributable, both Vulkan startup smokes, persistent GPU-indirect, vanilla post/depth chains, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, and Flywheel all pass. The cluster-1 vertex/Forge smoke proves ordinary non-Vulkan `VertexConsumer` fallback, exact non-`NEW_ENTITY` layout/stride behavior, Forge baked lighting/normals, and per-vertex alpha.
- Preflight after the first RX/Create Chronicles IP crash closed additional downstream issues before another user test: per-`LevelRenderer` terrain/world/camera/dispatcher ownership for IP secondary dimensions, IP recursive `RenderBuffers` use for block entities, Vulkan terrain clip-plane support in direct/indirect/region pipelines, preservation of IP's reload guards/fan-out despite VulkanMod cancelling vanilla `allChanged()`, and the debug lifecycle mixin constructor descriptor after the renderer refactor.
- The first RX 6900 XT/Create Chronicles attempt remains useful evidence: it reached IP's framebuffer compatibility renderer and stopped at a raw `GL11.glDisable(GL_STENCIL_TEST)` before terrain evidence. That raw-GL path and the downstream preflight issues above are now regression-covered in CI #690; a repeat full-pack hardware run is still required.
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

These terrain findings no longer block the combined Create Chronicles RX functional test. The Immersive Portals framebuffer raw-GL regression discovered by the first attempted full-pack run is now fixed and directly exercised in CI #678; the hardware terrain test itself remains pending because that attempted run stopped before GPU-terrain evidence was reached.

## Validation evidence

`661efe51` covers post-submit ordering/outside-lock execution, bounded descriptor saturation, deterministic signaled-helper polling, exact single publication, duplicate suppression when later frame callbacks drain, and validation-hook gating behind `vulkanmod.smokeTest`.

Hybrid-focused commits add tests for conservative cell ownership/demotion, filtered-v4 preservation of all non-ownership data and halo, generation-scoped REPLACE/APPEND staging, and actual mapped APPEND batch layout including CPU-before-GPU ordering, atomic pending-upload suppression, retry, and stale-GPU CPU fallback.

`b0e0f241` additionally proves that both actual section-mesher readback paths record the required transfer-to-host barrier during CI smoke execution, and that the production face-ownership comparison cannot silently invert authoritative visibility semantics. CI #620 exercised these tests successfully.

## Compatibility intersection

Compatibility is no longer an independent workstream. The relevant Forge 1.20.1 compatibility stack is integrated into the current `forge-1.20.1` executable head `8e553bb6` and validated by CI #690.

Immersive Portals 3.0.7 passes shader transformation, all three helper-shader Vulkan-pipeline checks, `VULKANMOD_IP_CLIPPING_SHADER_OK`, renderer-mode selection, the real framebuffer renderer's `prepareRendering()` path under a no-OpenGL-context Vulkan window, construction/cleanup of a second real `LevelRenderer`, and the portal reload-hook API check. VulkanMod terrain state is per `LevelRenderer`; async tasks retain their owning world/camera/dispatcher, recursive block-entity rendering uses the active IP-swapped `RenderBuffers`, and the direct/legacy-indirect/region terrain shaders consume IP's camera-relative clip equation. IP reload cancellation during recursive rendering and secondary-renderer reload fan-out are explicitly preserved despite VulkanMod's HEAD cancellation of vanilla `allChanged()`.

Distant Horizons 3.2.0-b currently operates fail-closed: its OpenGL LOD draw/fade passes are suppressed under Vulkan while DH data and render-thread maintenance remain active. CI #690 logs that suppression and passes the DH compatibility smoke; do not describe this as working DH LOD rendering.

FTB Library, Pick Up Notifier, Crash Assistant, Chat Heads, and Flywheel compatibility smokes are green on the same executable head. The old compatibility PR and isolated validation PRs are superseded by the landed production tree and should not be treated as active ownership boundaries.

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

## Completed adversarial codebase audit

The full adversarial audit inserted before the pending RX 6900 XT/Create Chronicles functional run is complete. The durable report is `docs/CODEBASE_AUDIT_2026-09-18.md`.

The report is pinned to executable SHA `8e553bb6991c16f86098227aa35639189d4e4aaf`. Repair cluster 1 (A10/A11/A12: vertex layout corruption, Forge `putBulkData` semantics, and unsafe private-interface casts) is closed at `94c459f17f452320193e532535ed31137e794801`, fully green in CI #691. Four repair clusters remain; hardware testing is still deferred while statically provable audit defects remain.

Highest-priority repair clusters are:

- generic/Forge rendering contracts: non-`NEW_ENTITY` BufferBuilder fast-path corruption, lost Forge baked-light/normal/alpha semantics, unsafe `ExtendedVertexBuilder` casts, missing Forge shader-registration lifecycle, false stencil capability, and lost block-layer render-stage events;
- runtime/lifetime correctness: mutable section-origin/captured-region worker race, missing/failure-unsafe GPU mesher lifecycle, framebuffer dependent-object retirement ordering, `VulkanImage` failure propagation, shaderc/SPIR-V reload leaks, and VMA map error handling;
- general correctness/data safety: UINT16 automatic-index overflow, sampler wrap-state loss, and suppressed Minecraft emergency save;
- CI quality: smoke fixtures are not fully hermetic and no current oracle covers several Forge public contracts or native failure paths.

Several suspicious areas were explicitly cleared: frame-slot vs image-index semaphore ownership is correct; GPU-terrain async completion/descriptor return is exactly-once; worker shutdown joins producers before publication cleanup; in-flight GPU reservations protect region reuse; the Java/GLSL terrain ABIs and inspected barriers match; temporary stale CPU fallback publication was not an ownership violation; and Immersive Portals cull redirects do update Vulkan pipeline state.

## Next action

1. Continue repair cluster 2 from `docs/CODEBASE_AUDIT_2026-09-18.md`: A13 shader registration/reload/namespaced construction, A15 block-layer `RenderLevelStageEvent`, then A14 truthful stencil capability.
2. Do not reopen completed cluster 1 unless new evidence contradicts CI #691 or the repaired contracts.
3. Preserve current fail-closed GPU-terrain boundaries while fixing ownership/lifetime issues.
4. Keep CI green between repair slices and make compatibility/failure-path tests assert public contracts rather than implementation markers.
5. Only after significant proven findings are repaired should the pending narrow RX 6900 XT/RADV Create Chronicles correctness run resume, unless one repair specifically requires hardware evidence.

## Outstanding RX evidence / performance boundary

Prior user evidence remains valid: experimental GPU-indirect consumption had clean initial comparator samples; F3+T and world re-entry worked; FTB Chunks large-map terrain remained black due to its null-`BlockState` map task; the prior center/world-edge artifact disappeared when the death marker was removed. Do not repeat sparse-lighting density telemetry.

Do not claim a speedup yet. CPU tessellation can be bypassed for the fully qualified subset, APPEND now covers fresh sections and transactionally staged mixed rebuilds behind the additional experimental gate, upload-to-compute/completion latency is shortened, and terrain-side CI covers the 2026-09-17 correctness findings plus the atomic rebuild ownership contract. Representative RX 6900 XT/RADV correctness and comparable Phase 5/6 frame-time evidence are still required before any performance or default-path conclusion.
