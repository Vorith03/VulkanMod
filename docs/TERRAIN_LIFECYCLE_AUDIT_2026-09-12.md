# Terrain lifecycle audit — 2026-09-12

This audit closes the code-level lifecycle gate for Phase 6 persistent region batching. It does **not** close the separate RX 6900 XT high-churn visual gate or the measured-performance gate.

Verified source checkpoint: `2f415efba0478be3af0be862574f4a0e6ae550df`, CI #342. Build, distribution, both Vulkan startups, vanilla color/depth PostChain execution, screenshot/readback synchronization validation, Crash Assistant 1.9.7 and Flywheel 0.6 all passed.

## Rebuild and section reuse

- `RenderSection.setOrigin` resets a reused fine-grid section before assigning its new world coordinates. Reset cancels rebuild/sort tasks, clears global block entities, returns live draw-parameter suballocations to the owning region when that backing buffer still exists, and marks the section uncompiled/dirty.
- Worker results retain their originating `ChunkTask`. Render-thread publication rejects a result if that task was cancelled; cancelled compile results release their native upload buffers instead of publishing stale geometry.
- `RenderRegionCache` is scoped to one visibility/scheduling traversal. Tasks capture their `RenderChunkRegion` snapshot; the renderer does not retain a long-lived LevelChunk-backed cache across later world changes.

## Coarse region-ring reuse

- The 8×8×8-section `ChunkArea` ring has margin beyond the fine section grid. When a coarse slot wraps, `repositionForReuse` preserves its Vulkan vertex/index allocations only if no live suballocations remain.
- The common drained case clears cached region draw state/revisions but retains the physical buffers, so subsequent terrain can reuse established residency without reallocating those Vulkan buffers.
- If a wrapping coarse slot unexpectedly still owns live geometry, it takes the conservative fallback and releases its draw buffers instead of reinterpreting live data at a new world origin.
- CI startup smoke exercises both the preserve-handle path and the live-geometry fallback with real Vulkan buffers.

## Visibility and command-cache ordering

`WorldRenderer.setupRenderer` repositions the section/region grids when the camera section changes. The same camera movement sets `needsUpdate`; before terrain rendering consumes the queues, the renderer clears the old area/section visibility queues and performs the new culling/traversal. Therefore an old visible-region queue is not intentionally rendered under a newly recycled region position.

Region indirect command caches are per layer and per frame. They rebuild when the ordered visible-section set changes, that layer's mesh revision changes, or a previously pending upload becomes ready. Equivalent visibility rewrites retain cached commands. A mesh edit in one terrain layer no longer invalidates unrelated layer caches.

## GPU ownership and retirement

- Normal terrain mesh writes now record and submit on the graphics queue. For an in-place persistent slice, queue order is therefore: older graphics reads -> terrain copy -> newer main-frame draw. No transfer-queue semaphore is required for this terrain hot path.
- Transfer-capable buffers may still use concurrent queue-family sharing when graphics and transfer families differ. That handles family ownership but was not relied upon as execution synchronization for persistent in-place overwrites.
- Synchronous area-buffer growth first flushes prior terrain uploads, performs the old-buffer -> new-buffer copy on the graphics queue, waits that helper fence, explicitly retires that helper command buffer, and only then retires the old backing allocation.
- Ordinary buffer destruction remains deferred through `MemoryManager` frame retirement rather than destroying a Vulkan buffer immediately while an older submitted frame may reference it.

## World, render-distance and shutdown transitions

- Render-distance/all-changed reconstruction releases the old section grid's region buffers before replacing the grid and clears queued terrain work.
- Setting the client level to null releases the section grid and then stops/drains terrain workers and pending upload ownership.
- Region command buffers are freed with their owning `DrawBuffers`; swapchain/frame-count changes recreate per-frame command resources through the existing idle/recreation path.

## Evidence and remaining limits

The lifecycle design is backed by green CI #342 plus startup smoke assertions for suballocation reuse/growth, per-layer cache isolation, region-ring buffer preservation/fallback and same-graphics-queue terrain submission. CI does not reproduce sustained RX 6900 XT camera traversal in the full Create Chronicles world.

Accordingly:

- Phase 6 lifecycle-audit gate: **complete**.
- Phase 6 high-chunk-churn visual gate: **open**.
- Phase 6 measured-performance gate: **open**.
- No FPS, frame-time or chunk-loading improvement is claimed from this audit alone.
