# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and the linked design/evidence documents; keep this file focused on what the next work session needs.

## Repository state

- Branch: `forge-1.20.1`.
- Current source commit before this documentation checkpoint: `83763f697521a12c9aada7fe984eda28c91d9c59` (`test: initialize game version before terrain bootstrap`).
- CI #486 exposed a second standalone-test bootstrap prerequisite after `d282b23d`: `Bootstrap.bootStrap()` reached `DataFixers` with `SharedConstants.getCurrentVersion()` unset and failed with `IllegalStateException: Game version not set`. `83763f69` now calls `SharedConstants.tryDetectVersion()` immediately before `Bootstrap.bootStrap()`. CI #487, run `35127777126`, has passed the complete Gradle build/test stage (including `testRegionBatchLayout`) and normal Vulkan startup smokes; remaining smoke stages were still running at this checkpoint. Last fully green source CI remains #482 at `943537d6` until #487 completes.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**, still **5/11 verified gates**. Do not check the hybrid-meshing gate merely from synthetic CI; production CPU bypass and RX correctness/performance evidence remain open.
- User priority remains explicit: move repetitive terrain construction from CPU workers to the GPU while preserving conservative CPU fallback for arbitrary Minecraft/Forge semantics. Mesh shaders are optional/later.

## Required planning/evidence documents

Use `AGENTS.md`, `ROADMAP.md`, `docs/TERRAIN_PRIORITY_OVERRIDE_2026-09-12.md`, `docs/TERRAIN_PERFORMANCE_BASELINE.md`, `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_VOXEL_RESIDENCY_PLAN_2026-09-12.md`, `docs/GPU_TERRAIN_SECTION_SELECTION_PROBE_2026-09-14.md`, `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`, `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, `docs/GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_VERTEX_JOIN_CHECKPOINT_2026-09-15.md`, and `docs/CHAT_HANDOFF_PROTOCOL.md`.

## Current GPU-terrain checkpoint

A real Vulkan compute dispatch can classify a bounded section, compact qualified faces, reconstruct complete 20-byte terrain vertices (position, UV, Minecraft-matched AO/color/light), and write them directly into generation-owned persistent `ChunkArea` vertex storage. `GpuTerrainSectionMesherSmokeTest` at `943537d6` proves complete persistent publication and forced-overflow fallback without CPU readback/re-upload.

The first production draw-handoff policy is encoded in `GpuTerrainDrawHandoff` (`2ae2fdcb`). It is deliberately fail-closed and does not mutate CPU `DrawParameters`: only enabled, supported opaque layers with valid, nonempty, exact-generation `GpuTerrainOutputStore.Residency` produce an auto-quad GPU command; disabled, stale, missing, invalid/overflow, translucent and tripwire cases return the CPU command byte-for-byte. `RegionBatchLayoutTest` covers those policy cases and generated index/vertex offsets. The standalone test now explicitly performs the same minimal version/bootstrap prerequisites needed before touching vanilla `RenderType`: `SharedConstants.tryDetectVersion()` then `Bootstrap.bootStrap()`.

### What is still not production

- `RegionDrawBatch.FrameBatch` does **not yet consume** `GpuTerrainDrawHandoff`; CPU commands remain authoritative in normal rendering.
- Normal terrain geometry is still CPU-meshed. No qualified voxel bypasses `BlockRenderDispatcher.renderBatched(...)` yet.
- `CPU_REQUIRED` must remain intact until a later handoff proves a complete qualified subset and preserves CPU geometry for every unsupported/failed case.
- Arbitrary Forge callbacks, dynamic/unsupported models, block entities, translucent and tripwire terrain remain CPU-only.
- GPU indirect draw remains opt-in/default-off pending broader RX movement/churn evidence.

## Next implementation slice

First inspect the final result of CI #487; its Gradle build/test stage already passed, so only investigate further if a later smoke fails. If green, wire the already-tested `GpuTerrainDrawHandoff` policy into `RegionDrawBatch.FrameBatch` under a new/default-off experimental gate:

1. For each visible supported opaque section, query `ChunkArea.getGpuTerrainOutputResidency(...)` and pass `RenderSection.getVoxelGeneration()` plus the untouched CPU command to the planner.
2. When the planner returns `gpuResident=true`, record its command and ensure `Renderer.getDrawer().getQuadsIndexBuffer().checkCapacity(indexCount * 2 / 3)` before drawing. `WorldRenderer.renderSectionLayer` already binds the auto-index buffer before the region-batching path, so GPU quad commands can share that binding with ordinary auto-indexed terrain commands. Otherwise record the original CPU command unchanged.
3. Keep GPU section-selection/candidate generation based on CPU `DrawParameters` for this first slice; do not mix GPU-substituted geometry into the existing CPU-vs-GPU selection safety comparison.
4. Make successful GPU output publication invalidate the affected terrain-layer command cache via the existing `DrawBuffers` mesh-revision mechanism, so a newly published residency cannot remain invisible behind a cached CPU `FrameBatch`.
5. Extend the Vulkan region smoke to prove exact-generation substitution and stale/missing/unsupported fallback through the actual `FrameBatch` path. Keep the gate default-off.

Only after that draw handoff is green should a later slice move GPU dispatch earlier enough to let qualified blocks skip CPU `renderBatched`; until then this remains draw-path correctness plumbing, not a performance feature.

## Outstanding RX evidence

The Phase 7 visibility/selection gate still needs a movement/churn sample. Prior user evidence remains valid: build #444 activated experimental GPU indirect consumption with eight clean initial comparator samples; F3+T and two world re-entries worked; FTB Chunks large-map terrain remained black due to its null `BlockState` map task; the center-screen/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

## Safety / performance boundary

Do not claim a speedup yet. The expensive CPU block/model/lighting loop remains authoritative in `ChunkTask.BuildTask.compile`. Performance A/B work starts only after qualified production GPU meshing can actually replace that CPU work. Existing Phase 5/6 measurement gates remain open.
