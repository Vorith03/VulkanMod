# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and the linked design/evidence documents; keep this file focused on what the next work session needs.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; compaction must not rewrite or summarize it except to correct a factual error. Use the user's local calendar date when known, otherwise the repository commit date.
- Once material ages out of that window, collapse it into the durable current-state sections instead of retaining a chronological diary. Preserve only facts that still affect decisions: validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and pointers to focused evidence documents.
- Prefer removing superseded chronology, stale next steps, obsolete artifact/run detail, and repeated implementation narrative already preserved by Git, CI, or focused design/evidence documents.
- Review for compaction only when material has actually aged out of the protected window and shortening the file would materially help. Do not perform a no-op compaction review merely because a chat rolled over. Normally review at most once per calendar day. After compaction, aim for roughly **100 lines or fewer** when the protected recent window permits; correctness and irreplaceable evidence take precedence over size.

## Repository state

- Branch: `forge-1.20.1`.
- **Code checkpoint:** `bc25dde8d296c8a55e40aa175978474093711f4c` (`terrain: pin GPU output until frame completion`). A future session should compare this checkpoint to live HEAD rather than expecting this file to mirror every docs-only commit.
- CI #515, run `35175848665`, is fully green for `bc25dde8`; artifact: `vulkanmod-forge-1.20.1-bc25dde`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- Active roadmap: **Phase 7 — GPU-driven terrain and hybrid meshing**, still **5/11 verified gates**. The current experimental production path is **not yet ready for RX 6900 XT correctness validation** because a later validation audit found a missing device-to-host memory dependency in terrain-result readback; no Phase 7.4 performance or default-path gate is closed yet.
- User priority remains explicit: move repetitive terrain construction from CPU workers to the GPU while preserving conservative CPU fallback for arbitrary Minecraft/Forge semantics. Mesh shaders are optional/later.

## Task-relevant references

Always use `AGENTS.md` and the active `ROADMAP.md` gate. For the current GPU-terrain slice, the primary contracts are `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. Consult the indirect-selection, lighting-demand, voxel-residency, performance, priority-override, or chat-handoff documents only when the active change actually touches those concerns. The ownership note predates the latest production dispatch/draw integration; live code and this checkpoint supersede any statement there that says production dispatch/draw consumption does not yet exist.

## Current GPU-terrain checkpoint

The bounded compute path can classify a section, compact qualified faces, reconstruct complete 20-byte terrain vertices (position, UV, Minecraft-matched AO/color/light), and write them directly into generation-owned persistent `ChunkArea` vertex storage.

`GpuTerrainSectionMesher` is a production-owned dispatcher, and `GpuTerrainSectionMesherBridge` now has a genuinely asynchronous production completion path. Qualified rebuilds may skip ordinary CPU `renderBatched(...)` work for the supported SOLID/CUTOUT subset instead of first building the same geometry on the CPU. The earlier synchronous render-thread fence wait remains available only to the dedicated validation/smoke path; production submission no longer waits for GPU completion on the render thread.

The production draw consumer remains default-off and incremental. `RegionDrawBatch.FrameBatch` can substitute exact-generation `GpuTerrainOutputStore.Residency` once completion publishes it; while a qualified GPU rebuild is pending, the previous CPU mesh stays drawable. Output publication/invalidation advances mesh revision so cached frame batches cannot retain stale CPU/GPU commands. The shared uint16 auto-quad limit remains enforced.

### Completion and allocation lifetime

The in-flight lifetime hole discovered while making completion non-blocking is fixed at `bc25dde8`: logical invalidation of a submitted output no longer makes its physical allocation reusable while GPU work may still write it. Submitted slices remain physically pinned until the frame-fence completion callback retires them. CI #515 includes focused coverage that invalidates a submitted generation, proves a competing maximum allocation cannot reuse/grow through that slice, completes the stale submission without publishing it, and only then permits the capacity to be allocated again.

### Fail-closed CPU-bypass boundary

CPU bypass is deliberately limited to rebuilds that already have a usable CPU terrain fallback:

- fresh/uncompiled sections cannot bypass CPU tessellation;
- sections already requesting GPU-terrain recovery cannot bypass;
- candidate sections must retain a compiled, ready CPU mesh while asynchronous GPU work is pending;
- arbitrary Forge callbacks, dynamic/unsupported or mixed models, block entities, fluids, translucent and tripwire terrain remain CPU-only;
- missing/stale residency, model-generation turnover, unsupported geometry, allocation/dispatch failure, overflow, cancellation, completion error, exact face-count mismatch, draw-residency mismatch, or invalid draw range fails closed;
- a failed current-generation GPU attempt requests normal CPU recovery/rebuild while the previous CPU draw remains resident rather than exposing missing terrain.

All accelerated pieces remain opt-in. Production CPU bypass requires all three properties:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Sparse GPU lighting remains enabled by default unless explicitly disabled separately.

## Validation state

CI #515 is the first checkpoint that covers both of the blockers that previously made a user-machine test premature: non-blocking production completion mechanics and submitted-output allocation pinning through GPU/frame completion. The full workflow is green, including build, renderer regression checks, Vulkan startup paths, indirect-draw smoke, post-chain/depth coverage, lavapipe renderer regression coverage, compatibility source smoke, and the focused GPU-terrain bridge/output-store tests.

**2026-09-17 validation correction:** a later synchronization audit found that `GpuTerrainSectionMesher.submitDispatch()` copies the GPU result into a `HOST_VISIBLE | HOST_COHERENT` readback buffer but does not record a final `VK_ACCESS_TRANSFER_WRITE_BIT -> VK_ACCESS_HOST_READ_BIT` memory dependency before fence completion and host mapping. Vulkan fence wait/status establishes execution completion but does not by itself make device writes visible to host accesses. `HOST_COHERENT` removes the separate invalidate requirement only after the device writes have been made available to the host domain. This affects the current `bc25dde8` base path as well as the in-progress terrain hardening branch. Existing CI does not detect this omission.

This is **not** performance sign-off. No dense-terrain A/B result, CPU p99 reduction, frame-time acceptance, VRAM acceptance, or accelerated-default claim is implied by the green CI.

## Next action — close terrain readback host-visibility blocker

Do **not** ask the user for the RX 6900 XT functional test yet. First add a narrow readback barrier in the terrain helper command buffer after all copies into the readback buffer and before submission/fence signal:

- source access/stage: `VK_ACCESS_TRANSFER_WRITE_BIT` / `VK_PIPELINE_STAGE_TRANSFER_BIT`;
- destination access/stage: `VK_ACCESS_HOST_READ_BIT` / `VK_PIPELINE_STAGE_HOST_BIT`;
- cover the entire readback range, including the validation path's optional copied vertex payload;
- preserve the existing non-blocking fence-poll/frame-callback completion design; this fix must not add a CPU wait.

Then rerun the focused terrain lifecycle/readback coverage plus the relevant Vulkan startup smokes. After that is green, the first RX 6900 XT test should remain deliberately functional rather than performance-oriented: enable the three experimental properties, let nearby terrain build normally, place/break a simple full-cube block in an already rendered qualified section, verify the section remains visually intact, and capture the `VULKANMOD_GPU_TERRAIN_` lines. Only after real RADV completion/draw/lifetime correctness succeeds should Phase 7.4 performance A/B work begin.

## Outstanding RX evidence

The Phase 7 visibility/selection gate still needs a representative movement/churn sample. Prior user evidence remains valid: build #444 activated experimental GPU indirect consumption with eight clean initial comparator samples; F3+T and two world re-entries worked; FTB Chunks large-map terrain remained black due to its null `BlockState` map task; the center-screen/world-edge artifact disappeared when the death marker was removed. Do not repeat already-collected sparse-lighting density telemetry.

The first production CPU-bypass/draw-handoff RX 6900 XT test is **temporarily blocked** by the host-readback synchronization issue above. CI #515 still proves the earlier non-blocking completion and in-flight-allocation lifetime fixes, but it is no longer sufficient evidence by itself to warrant the user-machine test.

## Safety / performance boundary

Do not claim a speedup yet. The accelerated path is still experimental and opt-in, and only a qualified subset can skip CPU geometry emission. The CPU path remains authoritative fallback for unsupported content and for recovery. Performance A/B work begins only after the first real-driver functional test confirms that asynchronous completion, draw handoff, and failure recovery behave correctly on the target RX 6900 XT/RADV stack. Existing Phase 5/6 measurement gates remain open.