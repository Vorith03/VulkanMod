# VulkanMod Forge 1.20.1 Performance Status

Last updated: 2026-09-06

This is the current-state companion to `PERFORMANCE_AUDIT.md`. The audit preserves the original findings and rationale; this file records what has actually been implemented on `forge-1.20.1`, what is still open, and which measurements should drive the next change.

## Verified baseline

- Vulkan rendering is locally playable on an AMD RX 6900 XT.
- The Forge 1.20.1 water/liquid UV/alpha regression is fixed and covered by CI.
- CI exercises Gradle build plus normal and no-early-splash Forge/Lavapipe startup smoke tests.
- The terrain path now has instrumentation suitable for a real RX 6900 XT loading/churn comparison.

## Synchronization and submission

### Implemented

- Auxiliary command buffers can signal reusable binary semaphores.
- The main graphics submit waits helper semaphores on the GPU rather than forcing the render thread to wait for converted helper submissions.
- Helper command-buffer reset is deferred until the graphics frame fence retires.
- Unavoidable fence waits are batched in one `vkWaitForFences` call.
- Transfer buffers use concurrent graphics/transfer queue-family sharing when the queue families differ.
- Terrain transfer batches use the helper-semaphore path and can become drawable in the same graphics frame.
- Area-buffer reallocation retains a separate synchronous fence-only flush path so binary semaphores cannot be recycled while signaled but unconsumed.
- `Renderer.resetBuffers()` waits the current frame fence before early run-tick texture/resource work can reuse staging/drawer frame resources.
- Swapchain image acquisition occurs in `beginFrame()` before the main render pass is recorded; `currentFrame` and acquired `imageIndex` are separate values.

### Still retained deliberately

- `Synchronization.waitFences()` remains in `Renderer.submitFrame()` as a legacy safety fallback. Most known graphics/transfer helper producers are semaphore-backed and duplicate registration is suppressed, but branch-wide code search has not been reliable enough to prove there are zero legitimate fence producers.
- `beginFrame()` still waits the current frame fence even though `resetBuffers()` now waits it earlier. This duplicate wait is intentionally kept until local validation confirms the earlier resource-retirement point is stable.

### Open

- Batch/stream additional image-transition work where practical. `GraphicsQueue` already uses semaphore-backed helper submission, so this is now mostly about reducing submission count rather than removing a global CPU drain.
- Instrument helper submit counts/fence fallback usage before deleting any remaining legacy synchronization path.

## Terrain scheduling and publication

### Implemented

- Terrain build scheduling is bounded by total outstanding work, including completed builds waiting for render-thread publication.
- High/low priority scheduling has a global fairness quota: after up to two high-priority rebuilds, low-priority/new terrain gets a chance.
- Replacement builds are promoted when a previous build for the section was cancelled.
- The vanilla 1.20.1 distant-section cardinal-neighbour guard is restored; far sections are no longer built against client placeholder chunks when N/S/E/W neighbours are absent.
- Stale/cancelled worker results are rejected before publication.
- Pending upload buffers are explicitly owned by the dispatcher and released on shutdown rather than retained across world unload.
- Unexpected worker exceptions discard partial builders and are surfaced through Minecraft crash handling instead of silently reducing the worker pool.
- Worker-local native terrain builder packs are freed when worker threads exit.
- Section/global-block-entity state is released when sections are reset and when a section grid is discarded.

### Mesh hot-path cleanup

- One redundant `RenderChunkRegion.getBlockState()` lookup per block was removed (4096 avoided lookups per built section).
- `ModelBlockRenderer` caching is cleared through `finally`, including exceptional builds.
- Compressed translucent quad sorting now uses opposite quad vertices 0 and 2, matching the uncompressed path and newer upstream.
- `TerrainBufferBuilder.discard()` fully resets builder state, including `building`, so aborted builds do not poison a worker builder.

### Deliberately not implemented yet

- Distance-sorted high/low task queues. Current upstream VulkanMod still uses FIFO high/low queues and the branch's graph traversal already tends to schedule visible terrain coherently. Add distance sorting only if queue-latency measurements show it is needed.
- Custom worker-count tuning. The current branch derives worker count from `availableProcessors()`; tune only after real CPU/build-latency data.

## Terrain GPU memory and upload path

### Implemented

- Area-buffer free extents are coalesced to reduce fragmentation-driven growth/copies.
- Area-buffer growth is demand-aware and can satisfy a single upload larger than the normal growth factor.
- Vertex, uniform, indirect, auto-index, and staging buffer growth policies are bounded around actual required capacity rather than aggressive additive doubling.
- Terrain uploads are batched through a per-frame transfer command buffer.
- Normal terrain batches signal a transfer semaphore; the graphics submit waits it, allowing fresh mesh ranges to be referenced in same-frame draw commands.
- Area-buffer growth remains synchronous and waits all recorded transfers before copying/replacing the backing allocation.

## Memory and descriptor robustness

### Implemented

- Memory type capability checks use bit containment rather than exact property equality.
- Device-local creation uses the correct memory-property constant.
- Shared host/device fallback requires coherent memory because mapped writes are not explicitly flushed.
- Persistent mapped `PointerBuffer` wrappers are freed when their backing buffers retire.
- Deferred image retirement drains the correct frame slot.
- Descriptor-set native `LongBuffer` storage is freed on pool replacement and cleanup.
- Descriptor dynamic-offset native storage is freed on cleanup.
- Descriptor update structs are stack-backed; the old audit's Java-array allocation finding no longer reflects current code.
- Pipeline state reuses unchanged snapshots, snapshots mutable logic-op state, and fixes hash/equality behavior for cached pipeline keys.

### Low-priority cleanup

- Descriptor pools currently emit multiple `VkDescriptorPoolSize` entries for the same descriptor type rather than aggregating counts. This is more validation/cleanliness work than a proven terrain throughput issue.

## Current F3 terrain metrics

The chunk statistics line now exposes:

- `iT`: idle terrain worker threads
- `aT`: active terrain tasks
- `qH`: queued high-priority tasks
- `qL`: queued low-priority tasks
- `uQ`: completed results waiting for render-thread publication
- `okR`: accepted results
- `dropR`: cancelled/stale results rejected
- `lat(q/b/h)`: average queue wait / mesh build / render-thread handoff latency in milliseconds
- `up(rdy/size/avg)`: last terrain transfer record-to-ready latency / batch KiB / average ready latency
- `stg:<high>/<cap>MiB r:<count>`: peak staging usage / current maximum staging capacity / total staging resizes

Under the new semaphore path, `up(rdy)` is the CPU handoff point at which a terrain range is safe to include in the current frame because the graphics queue will wait its transfer semaphore. It is not a CPU fence-completion time.

## Next RX 6900 XT validation

Use a build only after its full CI run is green. Then exercise:

1. ordinary movement through already-loaded terrain;
2. sustained sprint/fly into new chunks;
3. a long teleport that causes section-grid churn;
4. world reload and dimension change;
5. render-distance change / renderer rebuild;
6. water and other translucent terrain while moving.

Capture the F3 terrain-stat line during the loading/churn portions.

### Interpreting the counters

- High `q` with modest `b`: scheduling/backlog or worker-count issue.
- High `b`: CPU mesh generation is the next target.
- High `h` or persistent `uQ`: render-thread publication/upload processing is the bottleneck.
- Frequent `dropR`: movement/invalidation is causing wasted builds; improve cancellation/scheduling locality.
- `stg` close to capacity or nonzero resize growth: staging pressure deserves tuning.
- Low queue/build/handoff times but terrain still appears late: inspect GPU transfer/draw dependency and visibility invalidation rather than adding workers.

## Next code priorities after measurement

1. Use the real F3 measurements to choose scheduler vs mesh-build vs publication/upload work.
2. Count semaphore helper submits and legacy fence-fallback registrations; remove the fallback CPU drain only after proving it is unused in steady state.
3. If build time dominates, profile the per-block meshing loop before larger architecture changes.
4. If publication dominates, phase/budget render-thread result application and reduce per-result upload bookkeeping.
5. Continue image-transition batching if texture-heavy gameplay still shows helper-submit churn.
6. Keep renderer correctness and Forge compatibility ahead of speculative Nvidium-style rewrites.
