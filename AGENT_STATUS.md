# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and the linked design/evidence documents; keep this file focused on what the next work session needs.

## Repository state

- Branch: `forge-1.20.1`.
- Current source commit before this documentation checkpoint: `3946269ec17c783f34f2576135e8e9ad98eede15` (`test: cover uint16 GPU terrain draw bound`).
- CI #493, run `35130177665`, is fully green for `3946269e`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**, still **5/11 verified gates**. The new draw handoff is plumbing toward the hybrid-meshing gate; do not check that gate until production CPU bypass and RX correctness/performance evidence exist.
- User priority remains explicit: move repetitive terrain construction from CPU workers to the GPU while preserving conservative CPU fallback for arbitrary Minecraft/Forge semantics. Mesh shaders are optional/later.

## Required planning/evidence documents

Use `AGENTS.md`, `ROADMAP.md`, `docs/TERRAIN_PRIORITY_OVERRIDE_2026-09-12.md`, `docs/TERRAIN_PERFORMANCE_BASELINE.md`, `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_VOXEL_RESIDENCY_PLAN_2026-09-12.md`, `docs/GPU_TERRAIN_SECTION_SELECTION_PROBE_2026-09-14.md`, `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`, `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, `docs/GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_VERTEX_JOIN_CHECKPOINT_2026-09-15.md`, and `docs/CHAT_HANDOFF_PROTOCOL.md`.

## Current GPU-terrain checkpoint

The bounded compute prototype can classify a section, compact qualified faces, reconstruct complete 20-byte terrain vertices (position, UV, Minecraft-matched AO/color/light), and write them directly into generation-owned persistent `ChunkArea` vertex storage. `GpuTerrainSectionMesherSmokeTest` proves complete persistent publication and forced-overflow fallback without CPU readback/re-upload.

The production draw path now has a real, default-off consumer. `RegionDrawBatch.FrameBatch` queries exact-generation `GpuTerrainOutputStore.Residency` and applies the fail-closed `GpuTerrainDrawHandoff` policy. Successful GPU output publication and invalidation advance the affected `DrawBuffers` mesh revision so cached frame batches cannot retain stale CPU/GPU commands. `RegionBatchSmokeTest` proves CPU command -> exact-generation GPU substitution -> generation invalidation -> CPU fallback through the actual Vulkan frame-batch path.

The handoff also refuses GPU geometry above the shared uint16 auto-quad index limit (16,384 quads / 65,536 vertices) and falls back to the original CPU command. CI #493 covers that bound. GPU-terrain draw substitution remains opt-in via `-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true`.

### Important architectural finding

There is still **no production section-mesher dispatcher**. The full section compute mesher currently lives in `GpuTerrainSectionMesherSmokeTest` plus `section_mesher_probe.comp`; normal gameplay never reserves an output target and dispatches that shader for a rebuilt section. Therefore the new `FrameBatch` handoff can consume valid GPU geometry, but ordinary gameplay does not yet create that geometry.

This means the next safe production step is **not** to clear `CPU_REQUIRED` or skip `BlockRenderDispatcher.renderBatched(...)` yet. Doing so before a production dispatch/failure-completion path exists could make terrain disappear when compute allocation, shader dispatch, generation matching, or overflow fails.

### What is still CPU-authoritative

- `ChunkTask.BuildTask.compile` still executes the ordinary block/model/lighting loop for every renderable block.
- `CPU_REQUIRED` remains intact for all voxel entries.
- Arbitrary Forge callbacks, dynamic/unsupported models, block entities, fluids, translucent and tripwire terrain remain CPU-only.
- GPU indirect section-selection consumption and GPU terrain draw handoff are both default-off experimental paths.
- No performance improvement is claimed.

## Next implementation slice

Build the first production, fail-closed **GPU section-mesher dispatch bridge** before attempting CPU bypass:

1. Extract/reuse the already-proven section-mesher compute pipeline from `GpuTerrainSectionMesherSmokeTest` into a production-owned helper rather than duplicating the shader/descriptor contract.
2. The bridge must accept exact-generation voxel + sparse-lighting residency and a qualified model table, reserve bounded `GpuTerrainOutputStore` space, dispatch outside an active render pass, and publish only after successful completion for the same section generation.
3. On missing/stale input, unsupported model/layer, allocation failure, dispatch failure, overflow, cancellation, or generation turnover, invalidate/release the reservation and leave the CPU mesh untouched.
4. Start default-off. Add a real Vulkan smoke that exercises production helper success, overflow, and stale-generation rejection.
5. Only after that bridge is green should the compile path be split/staged so a completely qualified subset can avoid `renderBatched`. Preserve CPU geometry until the GPU result is known valid; do not introduce a worker-thread Vulkan wait that merely trades CPU meshing for synchronization stalls.

A useful adversarial design constraint for the following CPU-bypass slice: the current `compile` loop discovers voxel qualification and emits CPU geometry in the same pass. True CPU savings will require staging qualification/input capture before geometry emission, or another asynchronous ownership scheme; simply dispatching after the current loop will not improve build time.

## Outstanding RX evidence

The Phase 7 visibility/selection gate still needs a representative movement/churn sample. Prior user evidence remains valid: build #444 activated experimental GPU indirect consumption with eight clean initial comparator samples; F3+T and two world re-entries worked; FTB Chunks large-map terrain remained black due to its null `BlockState` map task; the center-screen/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

## Safety / performance boundary

Do not claim a speedup yet. The expensive CPU block/model/lighting loop remains authoritative in `ChunkTask.BuildTask.compile`. Performance A/B work starts only after qualified production GPU meshing can actually replace that CPU work. Existing Phase 5/6 measurement gates remain open.
