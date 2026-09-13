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

### Live continuation note — 2026-09-13

- Completed GPU-terrain continuation from
  `0ebaeb14971983bdf59bdec3d2db5ec95582dbe9`: source commit
  `9d4b349017a0aa22cc64cf326868b7d06bd68b74`
  (`gpu terrain: verify canonical cube lighting oracle`) compares an independent
  numeric implementation with the real Minecraft 1.20.1
  `ModelBlockRenderer.AmbientOcclusionFace`. It proves 120 exact packed color/light
  vertex results across all six directions, open neighborhoods and every blocked-
  diagonal substitution pair. It also proves that canonical faces sample two blocks
  outward, correcting the proposed one-cell/18-cube halo to a simple 20 x 20 x 20
  bound over section coordinates `[-2, 17]`, and makes section geometry eligibility
  fail closed under Forge's experimental lighting pipeline.
- CI #410 (run `34785863257`, job `103801104012`) is fully green: compilation,
  distributable verification, both Vulkan startup modes and the live lighting oracle,
  post-chain, depth, screenshot, Crash Assistant, Chat Heads, Flywheel, logs and
  artifacts all passed. Snapshot v4, compute shaders, production geometry ownership,
  and `CPU_REQUIRED` remain unchanged.
- Completed bounded artifact diagnosis in source commit
  `2c536ff61510606039eb2433c48fda7aaa17113f`
  (`terrain: diagnose compressed vertex overflow`). The leading explanation for the
  modpack-only artifact with a reachable world-space edge is a custom baked vertex
  overflowing the signed-short x1900 terrain format and producing a large triangle.
  `-Dvulkanmod.debugTerrainVertices=true` now emits at most 32
  `VULKANMOD_TERRAIN_VERTEX_RANGE` warnings with the exact block, state, world
  position, local coordinate and wrapped value. It is restart-only, default-off and
  does not alter geometry. See `docs/MODPACK_TERRAIN_ARTIFACT_DIAGNOSIS_2026-09-13.md`.
- CI #411 (run `34790146735`, job `103812728827`) is fully green across the complete
  build, both startups, render tests, compatibility tests, logs, and artifacts.
- Next safe GPU-terrain checkpoint: prototype and measure a CPU-resolved numeric
  lighting lattice using the proven two-block radius before changing snapshot v4.
  Compare a simple 20-cube encoding with an 18-cube plus sparse second-shell layout;
  include exact packed light, raw shade-brightness values, AO occlusion predicates,
  and a conservative position-offset policy. Reject the design if capture cost or
  retained bytes approach the CPU mesh cost. Do not clear `CPU_REQUIRED` or allocate
  production GPU geometry yet.

- Completed continuation from `4c961082a489c3c0e1c976f196393c9b3b3fc0d3`:
  source commit `7f706b085034a25ce577619443864c4b5fb799ec`
  (`gpu terrain: tighten vertex lighting eligibility`) records the exact CPU output
  dependencies in `docs/GPU_TERRAIN_LIGHTING_AUDIT_2026-09-13.md` and closes two
  qualification holes. Every accepted face must now retain per-face ambient
  occlusion and opaque-white baked vertex colors; otherwise its unrepresented AO or
  baked-color semantics stay on the CPU path. Snapshot v4 and the compute shader are
  unchanged.
- CI #409 (run `34781622935`, job `103789574418`) is fully green: compilation,
  distributable verification, both Vulkan startup modes, post-chain, depth,
  screenshot, Crash Assistant, Chat Heads, Flywheel, logs, and artifacts all passed.
  The startup oracle also confirms that the real vanilla baked-model generation
  retains a nonempty qualified subset under the tighter gates.
- Next safe checkpoint: build a CPU-only oracle fixture for canonical full-cube faces
  across deliberately varied block/sky light and AO neighborhoods. Compare the final
  four packed color and light words emitted by the real renderer with an independent
  numeric reference, and explicitly fail closed when Forge's experimental lighting
  pipeline is enabled. Do not add the proposed 18 x 18 x 18 lighting lattice until
  its exact sampling radius and float operation order are proven. Preserve
  `CPU_REQUIRED` and production CPU meshing.

- Completed continuation after safely rebasing over the independently landed runtime-
  toggle series ending at `8472650431e9df8861a694764e88bfc6ce3faeef`:
  source commit `81e953faefd7e065fcdda19a74e50b22d9a5c3e8`
  (`gpu terrain: pack compact position UV vertices`) extends the joined compact-face
  oracle with a bounded five-word-per-vertex image of VulkanMod's 20-byte compressed
  terrain format. Qualified faces populate signed-short x/y/z at the existing 1900
  scale and unsigned-short atlas UV at the existing 65536 conversion; position
  padding, color, and light remain zero. The independent CPU-format oracle compares
  all five words for all four vertices of every qualified compact candidate and
  requires unqualified and tail output to remain zero.
- CI #408 (run `34781031238`, job `103787958235`) is fully green: compilation,
  distributable verification, both Vulkan startup modes, post-chain, depth,
  screenshot, Crash Assistant, Chat Heads, Flywheel, logs, and artifacts all passed.
  This remains a smoke-only partial vertex image: no production geometry is
  allocated, `CPU_REQUIRED` and CPU meshing remain authoritative, and no performance
  result is claimed.
- Next safe checkpoint: audit the qualified cube path in `ModelBlockRenderer` and its
  collaborators to specify the smallest versioned numeric input needed to reproduce
  CPU color, directional shade, ambient occlusion, and packed light. Establish an
  exact CPU oracle and document the halo/lattice ownership before extending the
  snapshot ABI or shader. The renderer already supplies shared quad indices, so do
  not add a redundant GPU index payload. Do not bypass CPU meshing.

- Completed continuation from `b3cdcef4cecbf960c6d05d0721b904a1c36ffd1c`:
  source commit `7fb9571c0653d81052a36556c208ceb93228c2bc`
  (`gpu terrain: join compact faces to model rows`) adds an optional current-model-
  table binding to the existing voxel classifier/compaction kernel. Every actual
  compact descriptor can now emit its dense-template sentinel plus exact 9-word
  baked face row. The early region-residency smoke retains model lookup disabled;
  the later baked-model smoke verifies 12,288 compact candidates from real state IDs,
  including exact qualified sprite/UV rows and zero rows for deliberately misleading
  unqualified geometry hints, with no missing or duplicate descriptors.
- CI #402 (run `34774188666`, job `103769144899`) is fully green: compilation,
  distributable verification, both Vulkan startup modes, post-chain, depth,
  screenshot, Crash Assistant, Chat Heads, Flywheel, logs and artifacts all passed.
  This remains diagnostic-only; `CPU_REQUIRED` and production terrain ownership are
  unchanged, and no performance result is claimed.
- Next safe checkpoint: combine each qualified compact face's already-verified
  ordered corners and UV row into a bounded partial terrain-vertex payload, proving
  the exact position and packed-UV fields against the CPU format while leaving
  color/light/AO and production allocation untouched. Do not bypass CPU meshing.

- Completed continuation from `7188360b92c44ebd4fe48045b7b8019508e21a6f`:
  source commit `2f35a3aa03856c7e01fbb64b1a9d0910e56d85c7`
  (`gpu terrain: resolve resident voxel face rows`) extends only the diagnostic joined
  compute oracle. Each of 4,096 resident voxels now resolves its current dense baked
  template and one direction-cycled exact face row: sprite slot plus four ordered UV
  pairs. GPU output is compared word-for-word against the CPU table, while unqualified
  states must retain a zero template sentinel and an entirely zero face row. Shader
  palette/index range checks also fail closed for malformed slices.
- CI #401 (run `34773590690`, job `103767512106`) is fully green: compilation and
  distributable checks, both Vulkan startup modes, post-chain, depth, screenshot,
  Crash Assistant, Chat Heads, Flywheel, logs, and artifacts all passed. This remains
  smoke/oracle-only; `CPU_REQUIRED` and production terrain ownership are unchanged,
  and no performance result is claimed.
- Next safe checkpoint: bind the current model table to the existing voxel classifier/
  compaction oracle and resolve the exact face row for its actual compact candidate
  descriptors (voxel index plus direction). Compare every compact GPU row against the
  CPU baked template while preserving the current face/corner assertions. Do not yet
  integrate production terrain buffers or clear `CPU_REQUIRED`.

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
- Corrected source commit: `2f16dd2d8014d2b2f8cec594f4a650c66563c55b`
  (`gpu terrain: isolate model join fixture upload`). CI #400 (run `34750575140`,
  job `103706285736`) passed the full workflow. Both startup modes reported 1,730
  templates, 342 sprites, 24,135 state-index entries, the exact 484,092-byte model
  table, and all 4,096 resident voxel state-ID joins. Post-chain, depth, screenshot,
  Crash Assistant, Chat Heads and Flywheel gates also passed.
- The joined oracle now proves that a current baked-model table can resolve actual
  state IDs directly from the section ABI at a nonzero device-buffer slice offset;
  deliberately wrong/missing `GPU_FULL_CUBE` hints do not affect lookup. This is
  still diagnostic-only and makes no performance claim.
- Next safe checkpoint: use the joined dense-template index to resolve the exact
  face row (sprite slot plus four ordered UV pairs) for bounded candidate face work,
  and compare that GPU output to the baked CPU template. Do not integrate production
  terrain buffers or clear `CPU_REQUIRED` in that step.

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
