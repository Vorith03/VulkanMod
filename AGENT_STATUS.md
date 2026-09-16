# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and the linked design/evidence documents; keep this file focused on what the next work session needs.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; compaction must not rewrite or summarize it except to correct a factual error. Use the user's local calendar date when known, otherwise the repository commit date.
- Once material ages out of that window, collapse it into the durable current-state sections instead of retaining a chronological diary. Preserve only facts that still affect decisions: validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and pointers to focused evidence documents.
- Prefer removing superseded chronology, stale next steps, obsolete artifact/run detail, and repeated implementation narrative already preserved by Git, CI, or focused design/evidence documents.
- Review for compaction only when material has actually aged out of the protected window and shortening the file would materially help. Do not perform a no-op compaction review merely because a chat rolled over. Normally review at most once per calendar day. After compaction, aim for roughly **100 lines or fewer** when the protected recent window permits; correctness and irreplaceable evidence take precedence over size.

## Repository state

- Branch: `forge-1.20.1`.
- **Checkpoint commit:** `91f825d1a83cf297ca48ba422e7f40c66126bb3b` (`test: use visible partial model in bridge fallback smoke`). A future session should compare this checkpoint to live HEAD rather than expecting this file to mirror every commit.
- CI #503, run `35153213009`, is fully green for `91f825d1`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**, still **5/11 verified gates**. The production bridge advances plumbing toward the hybrid-meshing gate; do not check that gate until qualified GPU meshing can actually replace CPU geometry and required correctness/performance evidence exists.
- User priority remains explicit: move repetitive terrain construction from CPU workers to the GPU while preserving conservative CPU fallback for arbitrary Minecraft/Forge semantics. Mesh shaders are optional/later.

## Task-relevant references

Always use `AGENTS.md` and the active `ROADMAP.md` gate. For the next GPU-terrain slice, the primary contracts are `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. Consult the indirect-selection, lighting-demand, voxel-residency, performance, priority-override, or chat-handoff documents only when the active change actually touches those concerns.

## Current GPU-terrain checkpoint

The bounded compute path can classify a section, compact qualified faces, reconstruct complete 20-byte terrain vertices (position, UV, Minecraft-matched AO/color/light), and write them directly into generation-owned persistent `ChunkArea` vertex storage.

`GpuTerrainSectionMesher` is now a reusable production-owned dispatcher rather than smoke-only code. `GpuTerrainSectionMesherBridge` is the first production bridge from exact-generation section inputs to persistent GPU terrain output. It is default-off via `-Dvulkanmod.experimentalGpuTerrainMesher=true`, runs as a later render-thread operation outside an active render pass, accepts only fully-qualified visible block-model sections, requires current model qualification and exact CPU/GPU face-count agreement, and publishes only a same-generation non-overflow result. Missing/stale residency, mixed or unsupported visible geometry, fluids, allocation/dispatch failure, model-generation turnover, count mismatch, or other failure leaves the CPU mesh authoritative.

The production draw path remains a separate default-off consumer. `RegionDrawBatch.FrameBatch` can substitute exact-generation `GpuTerrainOutputStore.Residency`, while output publication/invalidation advances mesh revision so cached frame batches cannot retain stale CPU/GPU commands. The shared uint16 auto-quad limit remains enforced. CI #503 covers the current bridge/policy state, including visible unsupported-model fallback.

### Important architectural finding

The project has crossed the earlier “no production dispatcher” boundary, but it has **not** crossed the CPU-savings boundary. `ChunkTask.BuildTask.compile` still discovers qualification and emits ordinary CPU geometry first. The bridge then optionally dispatches GPU compute after that CPU result exists, and the initial `GpuTerrainSectionMesher` deliberately performs a synchronous fence wait on the render thread so publication has an unambiguous success/error boundary.

Therefore simply enabling the bridge cannot improve chunk-build CPU time and may add synchronization cost. The next safe design problem is to separate qualification/input capture from geometry emission and establish a completion/ownership path that can eventually let a completely-qualified subset avoid `BlockRenderDispatcher.renderBatched(...)` without risking missing terrain when dispatch, allocation, overflow, cancellation, or generation validation fails.

### What is still CPU-authoritative

- `ChunkTask.BuildTask.compile` still executes the ordinary block/model/lighting geometry loop for every renderable block.
- `CPU_REQUIRED` remains intact for all voxel entries.
- Arbitrary Forge callbacks, dynamic/unsupported or mixed models, block entities, fluids, translucent and tripwire terrain remain CPU-only.
- GPU indirect section-selection consumption, GPU terrain draw handoff, and the new section-mesher bridge are all default-off experimental paths.
- No performance improvement is claimed.

## Next implementation slice

Design and implement the first **staged, fail-closed CPU-bypass path** for completely-qualified ordinary-cube sections without turning CPU meshing into a Vulkan synchronization stall:

1. Split or stage the current compile flow so qualification/input capture can be completed before ordinary block-model geometry emission for a candidate fully-qualified section.
2. Preserve enough ownership/generation information to dispatch the existing production mesher outside an active render pass without requiring a chunk worker to block on Vulkan completion.
3. Keep CPU geometry authoritative until the new completion path has a valid same-generation success result; define explicit recovery for missing/stale input, unsupported or mixed geometry, allocation failure, dispatch failure, overflow, cancellation, resource reload, and generation turnover.
4. Add focused automated coverage for qualification staging, stale/overflow/failure fallback, and successful same-generation publication through the production path.
5. Keep the path default-off. Only after the staged path is green should a completely-qualified subset actually skip `renderBatched(...)`; then obtain representative RX 6900 XT correctness and A/B performance evidence before considering any accelerated default.

Do not “optimize” by dispatching after the existing CPU loop and calling that CPU offload, or by adding a worker-thread fence wait that simply trades meshing work for synchronization latency.

## Outstanding RX evidence

The Phase 7 visibility/selection gate still needs a representative movement/churn sample. Prior user evidence remains valid: build #444 activated experimental GPU indirect consumption with eight clean initial comparator samples; F3+T and two world re-entries worked; FTB Chunks large-map terrain remained black due to its null `BlockState` map task; the center-screen/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

The new production mesher bridge has not yet earned a user-machine test request because it still leaves CPU geometry authoritative and the next engineering question is the staged/completion design. Request RX 6900 XT testing when a new change creates an observation that CI cannot answer.

## Safety / performance boundary

Do not claim a speedup yet. The expensive CPU block/model/lighting geometry loop remains authoritative in `ChunkTask.BuildTask.compile`, and the current bridge adds a synchronous render-thread completion boundary when explicitly enabled. Performance A/B work becomes meaningful only when qualified production GPU meshing can actually replace CPU geometry work. Existing Phase 5/6 measurement gates remain open.
