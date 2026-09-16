# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and the linked design/evidence documents; keep this file focused on what the next work session needs.

## Repository state

- Branch: `forge-1.20.1`.
- Current source commit before this documentation checkpoint: `943537d6bc57a087bae4d819842b1c5879dc0f0c` (`gpu terrain: join section faces into persistent vertices`).
- CI #482, run `35094424891`, is fully green at `943537d6`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**, still **5/11 verified gates**. Do not check the hybrid-meshing gate merely from synthetic CI; production CPU bypass and RX correctness/performance evidence remain open.
- User priority remains explicit: move repetitive terrain construction from CPU workers to the GPU while preserving conservative CPU fallback for arbitrary Minecraft/Forge semantics. Mesh shaders are optional/later.

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
- `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md` — bounded output contract;
- `docs/GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md` — sparse-lighting evidence;
- `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md` — persistent GPU output ownership;
- `docs/GPU_TERRAIN_VERTEX_JOIN_CHECKPOINT_2026-09-15.md` — packed-vertex ABI/oracle background;
- `docs/CHAT_HANDOFF_PROTOCOL.md` — timeout-safe continuation protocol.

## Current GPU-terrain checkpoint

The diagnostic path has now crossed an important architectural boundary: a real Vulkan compute dispatch can classify a whole bounded section, compact qualified faces, reconstruct complete 20-byte terrain vertices (position, UV, Minecraft-matched AO/color/light), and write those vertices directly into generation-owned persistent `ChunkArea` vertex storage. No CPU readback/re-upload join is required.

`GpuTerrainSectionMesherSmokeTest` at `943537d6` proves four isolated qualified voxels including a section corner, 24 complete faces, exact descriptor-based oracle joining, persistent publication, and forced overflow fallback. The output reservation holds the same `AreaBuffer` monitor used by CPU upload/growth while the command buffer referencing the backing `VkBuffer` is submitted, closing the buffer-growth race. A failed same-generation overflow retry preserves the previous valid GPU residency.

This builds on the already verified pieces: bounded region voxel/state residency, exact sparse-lighting residency and turnover, qualified baked-model templates, GPU AO/color/light reconstruction against Minecraft, persistent GPU output ownership, generation invalidation, and bounded GPU indirect-command generation/fallback.

### What is still not production

- Normal terrain geometry is still CPU-meshed. No qualified voxel bypasses `BlockRenderDispatcher.renderBatched(...)` yet.
- CPU `DrawParameters` remain authoritative in normal rendering. Persistent GPU mesh residency is not yet selected by the production draw/candidate path.
- `CPU_REQUIRED` must remain intact until an explicit production handoff can prove a complete qualified subset and preserve CPU geometry for every unsupported/failed case.
- Arbitrary Forge callbacks, dynamic/unsupported models, block entities, translucent and tripwire terrain remain CPU-only.
- Sparse-lighting diagnostic/capture modes add CPU work and are not performance evidence.
- GPU indirect draw remains opt-in/default-off pending broader RX movement/churn evidence.

## Next implementation slice

The next safe slice is **an opt-in, fail-closed production draw handoff for already-published GPU meshes**, not immediate CPU-mesher removal.

1. Add a generation-checked way for the region draw/candidate builder to obtain a section's current `GpuTerrainOutputStore.Residency` for supported opaque layers.
2. Under a new/default-off experimental gate, substitute GPU residency only when its section generation exactly matches `RenderSection.getVoxelGeneration()`, the residency is valid/nonempty, the layer is supported, and the generated face count can be represented by the existing auto-quad index path. Derive `vertexOffset` from the persistent residency byte offset; do not mutate or discard CPU `DrawParameters`.
3. Preserve the CPU command unchanged whenever GPU residency is absent, stale, overflowed, unsupported, or otherwise invalid. Translucent/tripwire must never enter this handoff.
4. Add direct regression/smoke coverage for exact-generation success plus stale generation, unsupported layer, missing residency, overflow publication failure and CPU fallback. Ensure the quad index buffer capacity covers the generated face count.
5. Only after that handoff is green should a later slice move GPU dispatch earlier enough to let qualified blocks skip CPU `renderBatched`; until then this is draw-path correctness plumbing, not a performance feature.

Be careful that `RegionDrawBatch` currently builds both the authoritative CPU `FrameBatch` and GPU candidate tables from CPU `DrawParameters`. Do not accidentally make GPU section-selection safety checks compare a GPU-substituted candidate count against a CPU batch with different geometry semantics. Keep the first handoff narrowly scoped and separately gated.

## Outstanding RX evidence

The Phase 7 visibility/selection gate still needs a movement/churn sample rather than only initial world entry. A later user run can use:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
-Dvulkanmod.debugGpuSectionSelection=true
-Dvulkanmod.debugGpuSectionSelectionSamples=64
```

Move/rotate for ~35 seconds, cross region boundaries, do a short fast chunk-churn pass and, if practical, briefly test above normal build height. This is correctness evidence, not a performance benchmark.

Prior user evidence remains valid: build #444 activated experimental GPU indirect consumption with eight clean initial comparator samples; F3+T and two world re-entries worked; FTB Chunks large-map terrain remained black due to its null `BlockState` map task; the center-screen/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

## Safety / performance boundary

Do not claim a speedup yet. The expensive CPU block/model/lighting loop remains authoritative in `ChunkTask.BuildTask.compile`. Performance A/B work starts only after qualified production GPU meshing can actually replace that CPU work. Existing Phase 5/6 measurement gates remain open.
