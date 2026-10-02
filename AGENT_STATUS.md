# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current executable / CI state

- Latest hardware-validated executable is build **#935**, commit `48b0f06b4c36b01ead161b05c18da8688d8a637f`.
- #935 is fully CI-green and completed the canonical RX 6900 XT / RADV 180-second stationary benchmark.
- Current performance candidate adds three benchmark-driven optimizations after #935:
  1. `061bc6edd5b9250b0508e267e393d22b1aaa3066` — batch animated sprite copies across the complete texture tick;
  2. `322f8b15e8a62081554321bcb0131b7645386858` — skip repeated same-atlas transition `HashSet` lookups;
  3. `d1da0cc298e6ab46c99f06bb86ec89fcaf8cb26a` — reuse the mapped texture-staging `ByteBuffer` view instead of allocating a wrapper for every subupload.
- `282d6c00...` was an incomplete attempt to move texture memory-pressure sampling to the outer batch. It was immediately reverted by `4f0f0e0e7a59d95f7282f60d85819610a5dea5fb`; do not treat it as an active optimization.
- CI **#940** / run `36998527537` validates the final effective code state at `4f0f0e0e...`; it was still running when this checkpoint was written. The following docs-only commit does not change executable source: `1de161918a42387b08272dcf5b52be5c54707c61`.
- Focused benchmark/optimization evidence: `docs/PERFORMANCE_BENCHMARK_OPTIMIZATION_2026-10-02.md`.
- Previous mip-copy batching design/evidence: `docs/PERFORMANCE_TEXTURE_UPLOAD_BATCHING_2026-10-01.md`.

## Build #935 RX benchmark — authoritative current performance evidence

The fixed automated stationary benchmark completed cleanly for **180.003 s** on the user's RX 6900 XT / RADV Create Chronicles setup with unchanged camera/framebuffer and the canonical benchmark configuration.

Final aggregates:

- client tick: **25.208 ms average**, **31.030 ms p95**;
- texture tick: **17.474 ms average**;
- complete `SpriteContents.upload()` bodies: **9.834 ms average**, **13.057 ms p95**;
- texture work outside those bodies: **7.640 ms average**, **10.330 ms p95**;
- sprite uploads: **1310.502/tick**;
- texture subuploads: **6469.043/tick**;
- texture allocation: **305.737 KiB/tick average**;
- particle tick: **1.883 ms/tick average**, allocating **2547.872 KiB/tick average**;
- main graphics GPU: **1.277 ms average**, **1.362 ms p95**;
- terrain GPU: **0.921 ms average**;
- GPU world-other: **0.290 ms average**.

Compared with build #932, #935 improved:

- `SpriteContents.upload()` average **11.232 -> 9.834 ms** (~**12.4%**);
- texture tick average **18.542 -> 17.474 ms** (~**5.8%**);
- client tick average **26.085 -> 25.208 ms** (~**3.4%**).

Sprite/subupload call rates stayed effectively unchanged, validating that #935's gain came from reducing upload overhead rather than suppressing animation work. Keep #935's mip-copy batching.

## Current bottleneck interpretation

This scene is CPU-tick limited, not main-graphics-GPU limited. Do not prioritize terrain GPU, queue/present, or frame-fence micro-optimization from this evidence while a ~25 ms client tick remains.

Animated texture work is the dominant VulkanMod-owned recurring cost. The current post-#935 candidate therefore removes three additional repeated operations while preserving animation cadence, selected frames, interpolation, staged bytes, staging limits, image-layout ownership and visibility behavior.

### Why the mapped-view reuse is particularly strong

The benchmark reports **305.737 KiB/tick** of texture allocation over **6469.043 subuploads/tick**, approximately **48.4 bytes/subupload**. `StagingBuffer.copyTexture()` created one `MemoryUtil.memByteBuffer(...)` direct-buffer wrapper per subupload. The current candidate caches that wrapper for the lifetime of the mapped staging allocation and invalidates it on resize. The next RX benchmark should show whether this accounts for most of the measured texture allocation.

## Other optimization evidence — do not guess

### Particles

Particles are a real secondary CPU/allocation hotspot (**1.883 ms/tick**, ~**2.49 MiB/tick** allocation), but the current profiler only attributes the aggregate `ParticleEngine.tick()`. It does not identify the responsible particle class/provider/mod. Do not change generic particle semantics or collections from this benchmark alone. If still material after the texture pass, add bounded owner/type attribution first.

### Client entities / apparent tick remainder

The client-tick leaf summary leaves roughly 4.3 ms outside its named leaves. The broader profiler separately measures `client_entities_tick` around **3.8–4.4 ms/tick** in steady windows, explaining most of that difference. Do not reopen it as an unknown bucket.

### CPU world-render-other

`world_render_other` is commonly roughly **2–3 ms/frame** on CPU, while corresponding GPU world-other is only **0.290 ms average**. The CPU residual contains multiple vanilla/Forge/mod rendering activities and is not yet fine enough to justify a VulkanMod behavior change. If it becomes the next priority, split attribution before optimizing.

### Texture memory-pressure gate / layout record

Per-subupload `MemoryDiagnostics.enforceSystemMemorySafety()` fast-path work and `TextureUploadLayout` construction remain possible secondary overheads. The attempted outer memory check was reverted because it did not suppress the existing inner checks and therefore did not actually remove work. Revisit only with a complete semantic change and new measurements after the mapped-buffer allocation is removed.

## Settled compatibility / project context

- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.
- Immersive Portals current nested-world compatibility behavior is RX-confirmed working; preserve its semantic compatibility boundary unless new evidence contradicts it.
- Distant Horizons currently does **not** work in the user's setup. Treat compatibility as unresolved and do not assume it is functional in performance or roadmap testing.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from this benchmark work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7** and current priority. Continue comparable benchmark-driven optimization and hitch/frame-time evidence.
- Phase 7 GPU-terrain/hybrid: **6/11** and paused for Phase 5 measurement priority. The #935 GPU profile gives no reason to reopen unrelated terrain optimization now.

## Next useful action

1. Confirm CI #940 completes green for `4f0f0e0e...` (same executable source as current docs-only HEAD).
2. Once green, run that build with the exact same automated stationary RX benchmark contract used for #935.
3. Compare in this order:
   - total texture tick and client tick average/p95;
   - `sprite_upload_ms_avg/p95`;
   - texture allocation KiB/tick — primary validation for mapped-view reuse;
   - sprite/subupload calls per tick — must remain near #935 to prove unchanged semantics;
   - `non_upload_ms_avg/p95` — determines whether ticker/interpolation work becomes the next texture target.
4. If texture cost is no longer dominant, use the same run to choose between particle owner/type attribution and finer CPU `world_render_other` attribution. Do not preselect either before seeing the new profile.
