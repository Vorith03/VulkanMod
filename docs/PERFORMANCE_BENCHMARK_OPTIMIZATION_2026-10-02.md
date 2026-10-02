# Build #935 benchmark optimization pass — 2026-10-02

## Source run

This pass is based on the user's fixed 180-second RX 6900 XT / RADV automated benchmark of build #935, executable commit `48b0f06b4c36b01ead161b05c18da8688d8a637f`.

The run preserved the canonical stationary contract: fixed world/camera, stable framebuffer, render distance 16 / simulation distance 12, FPS cap 260, vsync off, and the same profiler/terrain configuration used for build #932.

Final benchmark aggregates:

- client tick: **25.208 ms average**, **31.030 ms p95**;
- texture tick: **17.474 ms average**;
- complete `SpriteContents.upload()` bodies: **9.834 ms average**, **13.057 ms p95**;
- texture work outside those upload bodies: **7.640 ms average**, **10.330 ms p95**;
- animated sprite uploads: **1310.502/tick**;
- texture subuploads: **6469.043/tick**;
- texture allocation: **305.737 KiB/tick average**;
- particle tick: **1.883 ms/tick average**, allocating **2547.872 KiB/tick average**;
- main graphics GPU execution: **1.277 ms average**, **1.362 ms p95**;
- GPU terrain: **0.921 ms average**;
- GPU world-other: **0.290 ms average**.

Compared with #932, #935 reduced:

- `SpriteContents.upload()` average from **11.232 -> 9.834 ms** (**12.4%**);
- texture tick average from **18.542 -> 17.474 ms** (**5.8%**);
- client tick average from **26.085 -> 25.208 ms** (**3.4%**).

Sprite/subupload call rates stayed essentially unchanged, so the improvement was not obtained by throttling animation work. The #935 mip-copy batching optimization is therefore retained.

## Bottleneck conclusion

The benchmark is decisively CPU-side for this scene. A ~25.2 ms client tick dominates while the complete main graphics command buffer costs only ~1.28 ms on the RX 6900 XT. Terrain GPU work, queue submission, present, and frame-slot synchronization are not evidence-backed optimization priorities for this capture.

The largest VulkanMod-owned recurring cost is animated texture work. The next changes therefore attack repeated CPU/LWJGL work in that exact path without changing animation cadence, selected frames, pixel bytes, staging budgets, layout ownership, or texture visibility semantics.

## Implemented optimization 1 — batch copies across the whole texture tick

Commit: `061bc6edd5b9250b0508e267e393d22b1aaa3066` (`perf: batch animated sprite copies across texture ticks`).

Build #935 batches mip regions inside each individual sprite upload. However, `MSpriteContents` closes that nested scope after every sprite, which still forces a copy-batch flush roughly once per sprite.

`MTextureManager.tick()` now holds one outer `VTextureSelector` sprite-upload batch for the complete animated-texture tick. The existing per-sprite scopes remain nested and therefore preserve their ownership contract. Pending copies still flush when:

- the destination Vulkan image changes;
- the staging buffer changes;
- the fixed region array fills;
- the outer texture-tick batch ends.

This turns the existing mip batching into atlas-scale batching without changing the staged data or upload ordering. Given ~1310 sprites and ~6469 subuploads per tick, this removes a large fraction of the remaining `vkCmdCopyBufferToImage` Java/LWJGL call overhead.

## Implemented optimization 2 — skip repeated atlas transition set lookups

Commit: `322f8b15e8a62081554321bcb0131b7645386858` (`perf: skip duplicate animated atlas transition lookups`).

Every `SpriteContents.upload()` previously called `HashSet.add()` for its bound Vulkan image, even when hundreds of adjacent animated sprites belonged to the same atlas. `SpriteUtil` now keeps a one-entry identity fast path for the most recently marked image.

Consecutive duplicates skip the hash-table lookup while the existing set still deduplicates non-consecutive images and remains the authority for the final shader-read transitions.

## Implemented optimization 3 — reuse the mapped staging ByteBuffer view

Commit: `d1da0cc298e6ab46c99f06bb86ec89fcaf8cb26a` (`perf: reuse mapped texture staging buffer view`).

`StagingBuffer.copyTexture()` previously called `MemoryUtil.memByteBuffer(mappedAddress, capacity)` for every texture subupload, constructing a fresh Java direct-buffer wrapper around the same mapped allocation.

The benchmark reports **305.737 KiB/tick** of texture allocation over **6469.043 subuploads/tick**, or about **48.4 bytes per subupload**. That closely matches a small direct-buffer wrapper allocation and makes this repeated view construction a strong allocation target.

`StagingBuffer` now caches that Java view for the lifetime of the backing mapped staging allocation and invalidates it only when `resizeBuffer()` replaces the allocation. The native address, staging contents and copy behavior are unchanged.

## Investigated but not changed

### Texture memory-pressure sampling

`MemoryDiagnostics.enforceSystemMemorySafety()` is called for each texture upload but internally admits a system-memory sample only every 250 ms. It is plausible that its repeated `nanoTime`/atomic fast path costs measurable CPU at ~6469 calls/tick.

A partial attempt to move the sample to the outer texture batch was deliberately reverted because the existing per-upload gate would still execute; leaving that change would add work rather than remove it. Any future version must suppress the inner checks while preserving resource-loading/unbatched safety semantics. No performance claim is made here.

### `TextureUploadLayout` object creation

Each subupload also creates a small `TextureUploadLayout`. It remains a possible secondary allocation source, but the observed ~48.4 bytes/subupload has a much more direct match in the mapped-ByteBuffer wrapper removed above. Do not complicate validation/layout code until the next benchmark shows meaningful texture allocation remains.

### Particles

Particles are the second-largest tick leaf at **1.883 ms/tick** and by far the largest measured leaf allocator at **~2.49 MiB/tick**. The current benchmark only brackets `ParticleEngine.tick()` as a whole and does not identify the allocating particle class/mod/provider. That is real optimization evidence, but not enough to justify changing particle semantics or generic Minecraft collections from VulkanMod. If this remains important after the texture pass, add bounded per-particle/provider attribution first.

### Client entities / tick remainder

The leaf-tick summary leaves about 4.3 ms between the total client tick and named leaves, but the broader profiler separately measures `client_entities_tick` at roughly **3.8–4.4 ms/tick** in steady-state windows. That explains most of the apparent remainder. It is not an unknown VulkanMod bucket.

### CPU `world_render_other`

CPU `world_render_other` is commonly around **2–3 ms/frame** in the run. The current stage is a residual containing multiple vanilla/Forge/mod render activities outside terrain setup/upload/draw and block entities; the benchmark does not identify a specific VulkanMod redundancy inside it. The corresponding GPU world-other work is only **0.290 ms average**, so this is primarily CPU dispatch/setup. Finer attribution is required before changing rendering behavior.

### GPU terrain / synchronization

No optimization is justified from this capture. Main graphics GPU execution is ~1.28 ms average, terrain ~0.92 ms, and queue/present/fence costs are tiny. Optimizing those paths before the CPU tick bottleneck would target the wrong constraint.

## Validation / next measurement

The final effective code candidate contains the three optimizations above. CI must be green before hardware testing is requested.

The next matched RX benchmark should compare against #935 and focus on:

1. `textures` average/p95 and total client tick average/p95;
2. `client_tick_texture_detail.sprite_upload_ms_avg/p95`;
3. `client_tick_leaf_allocation_avg_kib textures` — expected to fall sharply if the direct-buffer wrapper was the measured allocator;
4. sprite/subupload calls per tick — must remain near #935 to prove unchanged animation semantics;
5. `non_upload_ms_avg/p95` — determines whether animation/ticker work becomes the next texture target;
6. particle time/allocation and CPU `world_render_other` only as next-attribution candidates, not as proof of a VulkanMod optimization opportunity.
