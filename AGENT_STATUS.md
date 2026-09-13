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
- `docs/GPU_VOXEL_RESIDENCY_PLAN_2026-09-12.md` — bounded SSBO residency design and stop conditions.

## Current checkpoint — 2026-09-12

### Live continuation note — 2026-09-13 (in progress)

- Continuation session baseline: `40ab0350234b4ddc4fda7d83b1a9d4fadfdef5de`;
  CI #398 (run `34749852306`, job `103704325922`) is fully green, including
  compilation, both startup modes, model-table compute decode/readback, post-chain,
  screenshot, Crash Assistant, Chat Heads and Flywheel gates.
- Current bounded candidate (do not build beyond it until CI proves it): extend the model-table
  compute oracle to bind one real `RegionVoxelGpuStore` residency at the same time.
  Build the fixture from actual registered qualified/unqualified state IDs and
  verify all 4,096 state-ID -> dense-template lookups. Deliberately vary the legacy
  `GPU_FULL_CUBE` hint independently so the current baked-model table is proven to
  be authoritative. Keep this diagnostic-only; do not alter `CPU_REQUIRED`, normal
  meshing, production output ownership, or draw/upload integration.
- First candidate was published as `e554306d4402777b5ef43cb10b33a23d00fca63d`.
  CI #399 compiled and packaged it, then the first startup correctly rejected
  `RegionVoxelGpuStore.upload` because baked-model reload occurs outside the
  `AreaUploadManager` frame domain. The follow-up candidate must not weaken that
  invariant: it uses the existing fence-owned immediate storage upload for an
  isolated smoke-only voxel page at a nonzero slice offset. The already-green
  `SectionVoxelGpuSmokeTest` remains authoritative for region upload/publication.

- Live branch head at session start: `5f35881d5342e14933f31c2a5c5e689019f5d9a4`
  (`gpu terrain: upload model table outside frame staging`).
- CI #395 (run `34748685616`) compiled and passed the existing voxel compute/readback
  oracle, then failed during the Crash Assistant compatibility smoke after the
  startup smoke was extended through baked-model publication. The raw production/SRG
  Crash Assistant JAR calls `Minecraft.m_91087_()` inside the Mojmap-named `runClient`
  environment, causing `NoSuchMethodError`; this is a development-runtime remapping
  fixture issue exposed by the later smoke exit, not a GPU model-table assertion.
- The bounded repair adds `vulkanmod.smokeExitAtConstructor` only to the dedicated
  Crash Assistant fixture, retaining its historical constructor-boundary exit after
  Vulkan/compatibility checks. Ordinary startup fixtures still continue through
  baked-model publication and validate the GPU model table. Shell syntax and
  `git diff --check` pass; local Gradle remains blocked because this checkout cannot
  reach the uncached Gradle 8.1.1 distribution. Commit/push this repair, then inspect
  the resulting live CI before expanding GPU terrain scope.
- Repair commit was published as `e5cfc7c5600a13faf05ef7948f66861516299c51`.
- The next bounded checkpoint was published as
  `9dea481088f6fed3ce801a33944c86fa021b0c85`
  (`gpu terrain: decode baked templates in compute`). Its smoke-only probe dispatches
  one GPU invocation per dense baked template, verifies sparse state-ID reverse
  lookup, and reads every ordered face/sprite/UV word through compute. It does not
  publish terrain or change CPU fallback.
- CI #396 (repair only, run `34749580353`) and #397 (compute decode, run
  `34749765897`) remained queued without a runner at handoff time. Local Gradle is
  also blocked because the uncached 8.1.1 distribution cannot be reached here.
  **Resume by inspecting live CI first.** If #397 fails, fix it before new terrain
  work. If green, the next safe design task is joining a resident section voxel
  stream to this model table in one oracle dispatch so actual voxel state IDs, not
  the CPU-captured `GPU_FULL_CUBE` flag alone, select the dense face template.

- Branch: `forge-1.20.1`.
- Source head immediately before this documentation refresh: `3d85f800d8e5dae685c037b3f3012a27dd6a679c` (`terrain: generate GPU cube face corners`).
- Last fully verified CI before the newest compaction/geometry commits: **#378**, run `34719727902`, green at `adff3d2163b37d4e1a632cd1a89e93b8c54b3db7`.
- Current CI for the newest source is intentionally treated as pending until the push-triggered workflow proves it. Do not infer success from this file; inspect live Actions.
- Highest demonstrated legacy milestone remains **6 — playable world**. Phase 7 GPU terrain is the active implementation direction.
- User priority remains explicit: move repetitive terrain construction from CPU workers to the GPU while preserving conservative CPU fallback for arbitrary Minecraft/Forge semantics. Mesh shaders are optional/later; compute-driven residency/meshing comes first.

## GPU terrain path now present

### P7.1 section input ABI and fallback contract

- Default-off `-Dvulkanmod.experimentalSectionVoxels=true` captures an immutable numeric section snapshot with palette/state IDs, packed voxel indices, and CPU-resolved flag planes.
- Every voxel still carries `CPU_REQUIRED`; no current GPU result owns production rendering.
- Live model qualification can additionally set `GPU_FULL_CUBE` only for a deliberately narrow baked-model subset whose six directional quads are canonical unit-cube faces and pass the registry's conservative checks.
- Unsupported, dynamic, mod-dependent, tinted, non-solid, block-entity, fluid, non-simple, or otherwise unqualified model geometry stays on the CPU path.

### Bounded GPU voxel residency

- Region-owned fixed-capacity `StorageBuffer` pages and aligned best-fit suballocation are implemented under explicit bounded behavior.
- Per-section residency tracks page, byte offset/length, generation, and validity.
- Snapshot replacement is allocate-then-swap rather than overwriting a currently published slice.
- Generation checks reject stale uploads/publication; pressure can retain CPU fallback rather than synchronously growing ordinary voxel storage.
- Real Vulkan smoke coverage uploads resident snapshots, reads exact bytes back, checks submission-gated publication, replacement, and stale-generation rejection.

### Compute consumer and synchronization

- A real Vulkan compute pipeline binds a resident voxel page as storage, passes the section slice offset/length through push constants, and decodes the ABI on the GPU.
- Transfer writes are made visible to compute shader reads with an explicit transfer-to-compute buffer barrier; compute output is made visible to transfer readback with an explicit compute-to-transfer barrier.
- The CI smoke process performs full output readback and compares GPU results against independent CPU expectations.

### GPU full-cube face classification

The current production renderer still ignores this output; it is a correctness oracle only.

- `GPU_FULL_CUBE` voxels are classified on the GPU against six in-section neighbors.
- A face remains a candidate whenever its neighbor is outside the section or is not itself `GPU_FULL_CUBE`.
- This is deliberately **not** a replacement for Minecraft/Forge `shouldRenderFace`: section halo, semantic occlusion and other final rendering inputs are not encoded yet.
- The alternating-x smoke fixture produces exactly **4,608** candidate faces and verifies aggregate classification against a CPU oracle.

### GPU face descriptors and dense face-work compaction

- Each candidate face first receives a deterministic fixed-slot descriptor encoding the section-local voxel index and face direction.
- Commit `73635224cde570f67750edf7623337c8c4edd20a` (`terrain: compact GPU cube face work`) additionally uses the GPU face-count atomic as a range allocator to write a dense candidate-face work list.
- Each voxel invocation reserves one contiguous range for its own candidate faces; slots do not overlap.
- Compact ordering is intentionally nondeterministic across invocations. Smoke validation sorts the compact descriptor set and compares it against the independent CPU oracle, proving no missing or duplicate faces while retaining the old exact fixed-slot oracle.
- Unused compact output is required to remain zero.

### GPU unit-cube face-corner generation

- Commit `3d85f800d8e5dae685c037b3f3012a27dd6a679c` (`terrain: generate GPU cube face corners`) expands every dense face-work item into four GPU-generated section-local unit-cube corner coordinates.
- Coordinates are packed as three 5-bit integers because a section-grid corner ranges from 0 through 16 on each axis.
- The smoke oracle decodes each actual compact descriptor, independently builds the expected four-corner set for that voxel/face, and compares the GPU-generated set for that compact slot.
- Validation intentionally ignores corner ordering. `FullCubeGeometry` currently qualifies the four unit-face corners, but it does **not** establish baked-quad winding or UV orientation.
- Unused geometry output is required to remain zero.

## Critical safety boundary

Do not overclaim the current GPU path.

Normal terrain is still CPU-meshed and CPU-rendered. `ChunkTask`/Minecraft model semantics remain authoritative, and the regular CPU `renderBatched` path still emits the geometry actually uploaded/drawn. The compute probe is smoke/oracle work only.

Before a qualified GPU cube can safely bypass CPU face/vertex emission, the GPU path still needs enough validated information for at least:

- neighboring-section halo/occlusion inputs rather than assuming section-edge exposure;
- final face-visibility semantics compatible with Minecraft/Forge behavior;
- baked-quad winding/index order or a separately qualified replacement convention;
- UV/template orientation and atlas/material data;
- light/AO inputs and required shading behavior;
- any remaining vertex attributes required by VulkanMod's terrain format;
- a production output buffer/lifetime/publication contract rather than smoke-only readback storage;
- draw/upload integration with an immediate conservative CPU fallback when qualification or capacity fails.

Do not remove `CPU_REQUIRED` merely because a voxel has `GPU_FULL_CUBE`.

## Immediate next actions

1. Inspect live CI for the compact-face and face-corner commits. Fix any compiler/shader/smoke failure before expanding the scope.
2. Qualify a deterministic face winding/index convention separately from the existing corner-set check. Do not infer winding from `FullCubeGeometry.isUnitFace(...)`.
3. Extend the qualified full-cube template metadata only with attributes proven stable across the accepted baked-model set (beginning with UV/template orientation if practical).
4. Design the section-neighbor halo/occlusion input needed for authoritative GPU face rejection. Keep section-edge faces conservative until that exists.
5. Only after those semantics are proved should a tiny qualified subset bypass CPU face/vertex emission and feed production terrain buffers. Unsupported geometry must remain CPU fallback.

## Runtime evidence still relevant

Build #362 on the user's RX 6900 XT showed that normal terrain-result publication was not the dominant bottleneck during stressed traversal: the publication queue could be empty while fast spectator movement still outran useful section work and caused substantial stale/cancelled build churn. That remains the reason to prioritize eliminating repetitive CPU terrain construction rather than endlessly micro-optimizing the publication path.

Persistent terrain geometry capacity can expand during high-churn traversal; treat retained reusable capacity as distinct from a leak, but continue to observe it while GPU residency/meshing grows.

## Evidence limits

- CI software-Vulkan smoke proves compilation, startup, synchronization contracts and the explicit GPU oracle assertions it exercises. It does not prove RX 6900 XT gameplay performance.
- The current GPU classifier, compact face list and face corners are not production-rendered terrain and therefore do not yet prove an FPS/frame-time improvement.
- No CPU terrain bypass is claimed until a qualified section path actually stops emitting the corresponding CPU geometry and renders validated GPU-generated output correctly.
- Live Git and CI always supersede commit/run numbers written here.
