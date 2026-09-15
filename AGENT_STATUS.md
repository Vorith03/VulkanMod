# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime
evidence always wins if this file is stale. Historical detail remains in Git and the
linked design/evidence documents; keep this file focused on what the next work
session needs.

## Repository state

- Branch: `forge-1.20.1`.
- Latest source commit: `67dafa92c5a9f568616e10a54c504d9285f8f514`
  (`gpu terrain: verify indirect draw fallback contract`).
- Production-consumer source commit immediately before it:
  `d91de023159f7a22a2b49f00392ca6e133cfd095`
  (`gpu terrain: gate GPU-selected indirect draws`).
- Latest source CI: **#444**, run `34927562475`, fully green at `67dafa92`.
- Previous source CI **#443**, run `34925846288`, is fully green at `d91de023` across
  build/distribution, both Vulkan startups, persistent GPU indirect shadow, post/depth
  post-chain, screenshot, FTB Library, Crash Assistant, Chat Heads and Flywheel.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**.
- Phase 7 remains **5/11 verified gates**. The first real RX indirect-draw run is clean
  but its eight comparator samples were exhausted during the first ~4 seconds after
  world entry, so it is not enough to close the live visibility/selection gate yet.
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
- `docs/GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md` — real-section lighting-demand gate.

## Current GPU-terrain checkpoint

### Live section candidate path

The section-selection track is no longer synthetic-only.

- Each `ChunkArea` tracks the full 8x8x8 fine-section ownership superset independently
  of CPU frustum rejection.
- Per terrain layer, `RegionDrawBatch` publishes a bounded, generation-owned candidate
  table from live draw metadata: exact indexed-indirect words, upload readiness,
  CPU graph/smart-cull visibility, layer and packed region section.
- The current production `VFrustum` is frozen with that generation. The GPU owns the
  section-level six-plane frustum predicate in the experimental path while the CPU
  graph stamp remains the conservative traversal boundary.
- Region candidate tables live in device-local storage with allocate-then-publish
  replacement, lifecycle invalidation and a global budget. Missing/stale/budget-failed
  residency leaves CPU rendering authoritative.
- The rate-limited diagnostic compares the live GPU-selected set and every five-word
  indirect command against the authoritative CPU `sectionQueue` without changing
  normal rendering.

The first Create Chronicles/RX 6900 XT run on build #444 proved the production
indirect path active and produced eight clean `VULKANMOD_GPU_LIVE_SECTION_SELECTION_OK`
samples with selected counts from 0 through 45, plus no mismatch/error markers in the
full log. However, those eight samples all occurred from 21:32:00.374 through
21:32:04.478, mostly in region `(-128,-64,384)`. Because the diagnostic budget was
consumed before the later movement/chunk-churn portion, the **GPU visibility/section-
selection roadmap gate remains open** until a longer-spread sample covers actual
movement/region churn and, if practical, the unusual camera-outside-build-height path.

### Persistent GPU indirect output

The indirect-command mechanism has a real persistent production-shaped output.

- `GpuSectionSelectionShadowStore` owns a device-local storage+indirect buffer with a
  four-word header and capacity for 512 indexed-indirect commands.
- Every replacement zeroes the complete output, then compute compacts selected
  commands from slot zero. The unused tail therefore consists of safe zero-count
  commands.
- Same-graphics-queue helper submission plus explicit prior-indirect->transfer,
  transfer->compute and compute->indirect barriers makes the output eligible for the
  later main-frame draw without a synchronous count readback.
- The CI Vulkan smoke reads the real persistent output back and proves exact command
  metadata/membership, uniqueness, full-capacity bounds and zero tail. The fixture
  produces **99 exact commands from 512 candidates**.

`d91de023` adds a separate default-off production consumer. GPU indirect commands are
used only when both are set at process start:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
```

The CPU `FrameBatch` is still built first. Production ownership switches only for a
successfully dispatched/existing shadow generation with exact region/generation
identity, a candidate count at least as large as the CPU draw set, and a count within
persistent output capacity. Otherwise the same draw uses the CPU indirect buffer.

`67dafa92` extracts that host decision into the production predicate and adds smoke
assertions for disabled, stale/invalid, undersized, over-capacity and empty-CPU cases,
plus exact/bounded-superset success cases. CI #444 verifies the commit.

The **Phase 7 bounded GPU indirect-command + fallback gate is verified**. The RX run
also proves that the production consumer can become active on RADV/NAVI21 without an
immediate visual/crash failure. This does not yet close live selection correctness or
the final RX A/B gate, and the production consumer stays default-off.

### Hybrid GPU meshing groundwork

The larger CPU-meshing replacement remains separate from section-selection ownership.

- Default-off section snapshots encode bounded numeric voxel/state input while every
  voxel still retains `CPU_REQUIRED`.
- Qualified reusable baked-model templates are resolved through Forge's real
  ModelData/render-type API and reject seed-varying/dynamic/unsupported semantics.
- GPU classification, compact face work, exact model-row joins, ordered UV rows and
  partial compressed terrain vertices are capacity-bounded and checked against CPU
  oracles.
- Canonical cube AO/light sampling was audited against Minecraft's real renderer. A
  simple dense two-block-radius lighting lattice is too expensive; the opt-in
  lighting-demand estimator is ready to measure sparse point/brick density against
  actual CPU terrain bytes.
- No qualified voxel bypasses CPU face/vertex emission yet. Lighting/color/AO input,
  production geometry allocation/publication and hybrid fallback integration remain
  unresolved.

The user's observation that chunks were still somewhat slow to appear during the
indirect-draw test is expected at this checkpoint: the new path selects/submits
already CPU-meshed geometry; it does not yet remove the block/model/lighting work in
`ChunkTask.BuildTask.compile` that constructs new terrain meshes. Treat that
observation as motivation for the hybrid-meshing track, not as evidence that the
indirect path failed.

## Safety boundary

Do not overclaim the current work.

- Normal terrain **geometry is still CPU-meshed**. The new indirect path only changes
  which bounded command buffer may submit already-built region geometry.
- `CPU_REQUIRED` must not be removed merely because a voxel/model is GPU-qualified.
- Arbitrary Forge callbacks, unsupported models, block entities and other unencoded
  semantics stay on CPU.
- Translucent/tripwire terrain stays on the established renderer until separately
  implemented and validated.
- No FPS/frame-time improvement is claimed from the RX smoke. The bounded zero-tail
  draw plan avoids a CPU readback but may have driver-dependent submission cost.
- Do not enable GPU indirect draw or GPU meshing by default before broader RX visual
  correctness and comparable A/B evidence.

## Latest user runtime evidence

Keep these observations separate from synthetic CI evidence:

- Build #444 / `67dafa92`, Create Chronicles on the RX 6900 XT: experimental GPU
  indirect production consumption became active. Eight initial comparator samples
  were all `...SELECTION_OK`; the full log contains no live selection mismatch/error
  marker. The sampled selected counts progressed 0, 0, 1, 0, 14, 30, 36, 45.
- The user reported that chunks were still a little slow to render. This is compatible
  with the current design because terrain mesh construction remains CPU-authoritative.
- On the integrated Create Chronicles build, F3+T completed normally and leaving/
  re-entering the world twice worked.
- FTB Chunks' large map opens and the minimap is present, but large-map terrain is
  black. The #444 full log additionally records an FTB Chunks `MapTask` failure in
  `HeightUtils.isWater` because a `BlockState` is null; keep that compatibility issue
  parked unless it blocks active GPU work.
- The previously reported center-screen/world-edge artifact disappeared when the
  death marker was removed. Treat death-marker/overlay behavior as the leading
  explanation unless new evidence recreates the artifact without that marker.

## RX follow-up now useful — same build #444

No new JAR is required for the next evidence pass. Reuse build #444 and enable both
the longer section-selection sample and the already-shipped lighting-demand estimator:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
-Dvulkanmod.debugGpuSectionSelection=true
-Dvulkanmod.debugGpuSectionSelectionSamples=64
-Dvulkanmod.experimentalSectionVoxels=true
-Dvulkanmod.debugGpuLightingDemand=true
```

This run is **diagnostic, not a performance comparison**: the lighting-demand
estimator deliberately adds worker work.

In the normal Create Chronicles test world:

1. spend at least ~35 seconds moving/rotating through ordinary terrain so the 500 ms
   comparator cadence spans actual gameplay instead of only initial world entry;
2. cross several region boundaries and do a short fast spectator/chunk-churn pass;
3. if practical, briefly move above normal build height, rotate/move, then return;
4. continue into previously unseen terrain long enough to build several hundred
   sections so the lighting estimator emits multiple 128-section aggregate samples;
5. watch for missing terrain, holes, flicker, stale chunks, device loss or any visible
   difference from CPU-driven rendering;
6. return the full `latest.log`, or at minimum:

```bash
grep -E 'VULKANMOD_GPU_(INDIRECT_DRAW_ACTIVE|LIVE_SECTION_SELECTION_(OK|MISMATCH|ERROR)|INDIRECT_SHADOW|LIGHTING_DEMAND)' latest.log
```

Selection success requires the active marker, clean samples spread across movement/
region churn, no unresolved mismatch/error and no visible terrain regression. The
lighting lines decide whether sparse point/brick capture is materially cheaper than
the corresponding CPU meshes; do not choose that ABI before seeing the real ratios.

## Current blockers / next actions

1. **P7 visibility/selection:** use the 64-sample follow-up above. If clean across
   movement/churn, close the live selection gate and advance Phase 7 to 6/11.
2. **Hybrid meshing:** use the same run's `VULKANMOD_GPU_LIGHTING_DEMAND` ratios to
   choose a bounded sparse lighting input with explicit dense-demand CPU fallback, or
   reject CPU-resolved lighting capture if the real sections are too dense.
3. **Performance:** only after correctness is clean, run fixed-route CPU-vs-GPU
   comparisons. Do not interpret the lighting-telemetry run as performance evidence.
4. Continue production GPU terrain construction toward actually bypassing qualified
   CPU model/face work; indirect draw submission by itself is not the chunk-appearance
   bottleneck the user wants reduced.
5. Keep Phase 4 compatibility work parked behind the user's terrain priority, except
   for regressions that make the active GPU work unsafe.

Live Git/CI always supersedes commit/run numbers written here.