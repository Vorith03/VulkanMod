# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime
evidence always wins if this file is stale. Historical detail remains in Git and the
linked design/evidence documents; keep this file focused on what the next work
session needs.

## Repository state

- Branch: `forge-1.20.1`.
- Current source commit: `7cbe402ae94b242bd0ab948ac7816ae9838c0439`
  (`gpu terrain: compare captured GPU lighting with Minecraft AO`).
- CI #467, run `34960611507`, job `104353079945`, is fully green. Build,
  distributable, both startups, persistent indirect, post/depth, screenshot and all
  compatibility gates passed. Decoded logs contain seven successful Minecraft AO
  oracle markers and 63 successful center/corner fixture markers.
- Build artifact: `VulkanMod-Forge-build-467`, id `10392909339`,
  `sha256:ba8c4ca22e4ce38de15bcfa6c10c0586f193c9a18eb57703410b26842802a248`.
- Previous source `73014289a5f5db6b128ee56bd99bfb8c4388acb7` passed CI #465,
  run `34957645827`, job `104343440574`. Full workflow and sparse compute markers
  inspected: exact sparse decode and 24 canonical color/light vertex outputs.
- Local Gradle validation could not start: uncached 8.1.1 distribution download
  fails with `java.net.SocketException: Network is unreachable`. Whitespace check passed.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**.
- Phase 7 remains **5/11 verified gates**. Sparse-lighting capture/residency is now
  verified plumbing, but production GPU geometry construction is still not active and
  the longer RX live-selection validation remains outstanding.
- User priority remains explicit: move repetitive terrain construction from CPU
  workers to the GPU while preserving conservative CPU fallback for arbitrary
  Minecraft/Forge semantics. Mesh shaders are optional/later.

## Required planning/evidence documents

Use these together:

- `AGENTS.md` — engineering/evidence protocol;
- `ROADMAP.md` — canonical phases and gates;
- `docs/TERRAIN_PRIORITY_OVERRIDE_2026-09-12.md` — user-approved GPU-terrain priority;
- `docs/TERRAIN_PERFORMANCE_BASELINE.md` — benchmark contract;
- `docs/GPU_TERRAIN_BOUNDARY.md` — CPU/GPU responsibility boundary;
- `docs/GPU_VOXEL_RESIDENCY_PLAN_2026-09-12.md` — bounded section residency design;
- `docs/GPU_TERRAIN_SECTION_SELECTION_PROBE_2026-09-14.md` — live section-selection groundwork;
- `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md` — indirect-draw handoff;
- `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md` — qualified baked-model subset;
- `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md` — bounded compact face/vertex output;
- `docs/GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md` — sparse-lighting evidence gate;
- `docs/CHAT_HANDOFF_PROTOCOL.md` — timeout-safe continuation protocol.

## Current GPU-terrain checkpoint

### Live section selection and indirect draw

The section-selection track is production-shaped but remains default-off.

- Each `ChunkArea` tracks the full 8x8x8 fine-section ownership superset independently
  of CPU frustum rejection.
- Per terrain layer, `RegionDrawBatch` publishes bounded generation-owned candidate
  tables containing exact indexed-indirect words, upload readiness, CPU graph/smart-
  cull visibility, layer and packed section identity.
- The GPU owns the experimental section-level six-plane frustum predicate while the
  CPU graph stamp remains the conservative traversal boundary.
- `GpuSectionSelectionShadowStore` owns persistent device-local storage+indirect output
  with a four-word header and 512-command capacity. Whole-output zeroing leaves a safe
  zero-count tail after compute compaction.
- Production GPU indirect consumption requires both:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
```

- CPU `FrameBatch` construction remains intact and is used whenever the GPU plan is
  disabled, stale, invalid, undersized or over capacity.
- Build #444 on the user's RX 6900 XT proved the production indirect path could become
  active and produced eight clean live-comparator samples. Those samples were consumed
  in the first ~4 seconds after world entry, so the **live visibility/selection gate
  remains open** until a longer movement/region-churn sample is captured.

### Sparse lighting input — newly verified

A bounded exact sparse-lighting input now exists behind the default-off restart flag:

```text
-Dvulkanmod.experimentalGpuSparseLighting=true
```

Enabling it also enables the existing host voxel-snapshot infrastructure. CPU terrain
rendering remains authoritative throughout this path.

- `GpuSparseLightingSnapshot.tryCapture(...)` captures only exact lighting samples
  demanded by qualified GPU full-cube faces after the normal CPU section build.
- The ABI has a hard **2,000-sample** cap. Over-cap sections return `null`; data is
  never truncated. Maximum serialized payload is 18,576 bytes.
- Capture failure is fail-closed: CPU terrain output survives and sparse lighting is
  simply absent. The first successful live capture emits
  `VULKANMOD_GPU_SPARSE_LIGHTING_CAPTURE_ACTIVE`.
- Voxel and sparse-lighting records share the bounded `RegionVoxelGpuStore` page
  allocator but publish as separate fresh slices under the **same section generation**.
- A lighting upload is accepted only while its exact voxel generation is pending or
  resident. Unpaired lighting is rejected without advancing the lighting generation.
- Starting a replacement voxel generation immediately revokes lighting tied to the
  prior voxel contents. This prevents stale lighting from remaining discoverable while
  a new voxel payload is pending or if that voxel upload later fails.
- Replacements remain allocate-then-publish and old slices retire through the existing
  frame-fence lifetime domain.
- Section removal/rejected publication invalidates both voxel and lighting residency;
  coarse-area teardown closes the shared store.

CI #461 caught an earlier stale-light turnover ambiguity in the real Vulkan smoke. The
production-shaped lifecycle was corrected in `1019ea95` and the smoke rewritten in
`6c3b7ddc` to exercise actual paired section-generation turnover rather than an
artificial lighting-only replacement.

CI #463 now proves on real Vulkan:

- matching voxel + lighting records remain undiscoverable before upload submission;
- both become resident after submission under the same generation;
- their shared-page slices do not overlap;
- exact serialized sparse-lighting bytes survive GPU upload/readback;
- voxel turnover revokes old lighting immediately;
- unpaired lighting is rejected;
- lighting invalidated before submission cannot publish stale data.

The first Vulkan startup emits:

```text
VULKANMOD_GPU_SPARSE_LIGHTING_RESIDENCY_OK: shared terrain input page, exact bytes, paired turnover, voxel-driven light revocation, unpaired rejection, stale-generation rejection
```

and then the aggregate terrain lifecycle smoke marker.

### Diagnostic lighting reconstruction

Commits `197da4f3` and `73014289` already implemented sparse GPU decoding and
canonical AO/color/light reconstruction; do not recreate them. Commit `2c23d7ed`
parameterizes the section block index, gives face output its own 48-word range after
all 8,000 lattice records, and validates each sample coordinate before flattening.
The real Vulkan smoke now checks a center cube plus all eight section corners at
negative X/Z world origin, with fresh paired generations and exact full-output
readback. Follow-up `7cbe402a` captures inputs through `tryCapture` and compares
384 face-vertex color/light pairs directly against Minecraft's actual reflected AO
implementation across 16 tangent-occluder masks. CI #467 owns the combined evidence.
This remains diagnostic: neither shader output nor the lighting capture loop
replaces production CPU meshing.

### Hybrid GPU meshing boundary

The larger CPU-meshing replacement remains the active objective.

- Section snapshots encode bounded numeric voxel/state input while every voxel still
  retains `CPU_REQUIRED`.
- Qualified reusable baked-model templates are resolved through Forge's real
  ModelData/render-type API and reject seed-varying/dynamic/unsupported semantics.
- GPU classification, compact face work, exact model-row joins, ordered UV rows and
  partial compressed terrain vertices are capacity-bounded and checked against CPU
  oracles.
- Sparse exact lighting input is now capturable and GPU-resident, but the compute path
  does **not yet consume it to generate production lighting/color/AO fields**.
- No qualified voxel bypasses `BlockRenderDispatcher.renderBatched(...)` yet. CPU mesh
  construction and publication remain authoritative.
- Production geometry allocation/publication and hybrid CPU/GPU fallback integration
  are still unresolved.

The user's observation that chunks were still somewhat slow to appear during the
indirect-draw test is expected at this checkpoint: indirect selection/submission acts
on already CPU-meshed geometry and does not remove the expensive block/model/lighting
work in `ChunkTask.BuildTask.compile`.

## Safety boundary

Do not overclaim the current work.

- Normal terrain **geometry is still CPU-meshed**.
- `CPU_REQUIRED` must not be removed merely because a voxel/model is GPU-qualified.
- Arbitrary Forge callbacks, unsupported models, block entities and other unencoded
  semantics stay on CPU.
- Translucent/tripwire terrain stays on the established renderer until separately
  implemented and validated.
- Sparse-lighting capture itself adds CPU work while enabled; do not treat a run with
  this flag as a performance comparison.
- Do not enable GPU indirect draw or GPU meshing by default before broader RX visual
  correctness and comparable A/B evidence.

## Latest user runtime evidence

Keep these observations separate from synthetic CI evidence:

- Build #444 / `67dafa92`, Create Chronicles on the RX 6900 XT: experimental GPU
  indirect consumption became active. Eight initial comparator samples were all
  `...SELECTION_OK`; sampled selected counts progressed 0, 0, 1, 0, 14, 30, 36, 45.
- The user reported that chunks were still a little slow to render, consistent with
  CPU-authoritative mesh construction.
- F3+T completed normally and leaving/re-entering the world twice worked.
- FTB Chunks' large map opens and the minimap is present, but large-map terrain is
  black. The full log records an FTB Chunks `MapTask` failure in `HeightUtils.isWater`
  because a `BlockState` is null. Keep this parked unless it blocks active GPU work.
- The previously reported center-screen/world-edge artifact disappeared when the
  death marker was removed. Treat death-marker/overlay behavior as the leading
  explanation unless it reappears without that marker.

## Outstanding RX correctness evidence

The live selection gate still needs a sample spread across real movement rather than
only initial world entry. A later user run can use the build containing current source
and at least:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
-Dvulkanmod.debugGpuSectionSelection=true
-Dvulkanmod.debugGpuSectionSelectionSamples=64
```

Spend at least ~35 seconds moving/rotating, cross region boundaries, do a short fast
chunk-churn pass and, if practical, briefly test above normal build height. This is a
correctness run, not a performance benchmark. Close the live visibility/selection gate
only if samples remain clean across movement/churn with no visible terrain regression.

## Current blockers / next actions

1. **Completed validation:** CI #467, run `34960611507`, passed at published source
   `7cbe402ae94b242bd0ab948ac7816ae9838c0439`. It covers nine synthetic center/corner
   fixtures (216 pairs) plus 16 captured Minecraft AO fixtures (384 pairs), repeated
   across seven smoke invocations. No production terrain output changed.
   #466 passed its initial Vulkan gates before being superseded/cancelled.
2. **Recovered user evidence:** Personal Context retrieved September 14 debug.log
   and latest.log density runs through 9,088 sections, unique averages 599 down to
   524, point/CPU-mesh ratios 12% down to 11%, brick ratios 27% down to 26%, and
   final density buckets `4775/3736/575/2/0`. These are retrieved log facts, not
   freshly re-parsed raw files. Do not ask for duplicate density collection. They
   support continued bounded sparse-input work, not a performance claim.
3. Resume from `docs/GPU_TERRAIN_VERTEX_JOIN_CHECKPOINT_2026-09-15.md` for the
   inspected five-word ABI, descriptor join key, padding caveat and test sequence.
   No vertex-join implementation is pending in the working tree.
   Next join the bounded model/position/UV
   stream with sparse lighting, prove complete packed vertices against the production
   renderer, then implement bounded allocation/publication and explicit fallback.
   Missing/stale samples, unsupported semantics and allocation/overflow failures must
   leave `CPU_REQUIRED` intact. Diagnostic Java/GPU agreement is not a production
   meshing correctness or performance claim.
4. **P7 visibility/selection:** obtain the 64-sample RX movement/churn evidence above.
   If clean, close that roadmap gate and advance Phase 7 to 6/11.
5. **Performance:** after correctness gates are clean and production GPU meshing can
   actually replace CPU work, run fixed-route CPU-vs-GPU comparisons. Do not use
   diagnostic sparse-lighting capture runs as performance evidence.
6. Keep Phase 4 compatibility work parked behind the user's terrain priority except
   for regressions that make active GPU work unsafe.

Live Git/CI always supersedes commit/run numbers written here.
