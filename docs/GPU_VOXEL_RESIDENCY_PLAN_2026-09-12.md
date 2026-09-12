# Bounded voxel SSBO residency plan — 2026-09-12

This note narrows the next Phase 7 terrain step after CPU voxel snapshot capture. It is intentionally not a compute-meshing design and does not change rendering behavior.

## Groundwork now present

Commit `bd29252` adds an explicit `StorageBuffer` backing type and teaches `AreaBuffer` to distinguish vertex, index, and storage-buffer usages. Unknown usages fail fast instead of silently becoming an `IndexBuffer`.

`MemoryTypes.GPU_MEM` already adds transfer-source and transfer-destination usage to device-local buffers, so a storage buffer created through this path can be staged and copied without adding extra transfer flags at each call site.

No voxel snapshot is uploaded by this commit. Existing CPU fallback and the `vulkanmod.experimentalSectionVoxels` gate are unchanged.

## Do not use a growable region SSBO

The current `AreaBuffer` fallback grows by allocating a larger buffer, draining pending area uploads, synchronously copying the old allocation on the graphics queue, waiting its fence, and retiring the old buffer. That is appropriate as an exceptional geometry-buffer fallback, but it would turn ordinary voxel-residency pressure into a render-thread stall.

The first GPU-residency implementation must therefore use **fixed-capacity pages** or another no-growth allocation mode. Exhaustion falls back to CPU residency; it must not synchronously grow a live page.

## Proposed bounded layout

Use lazily allocated region-owned storage pages under a separate global GPU budget.

- A page is a fixed-capacity `StorageBuffer` and never grows in place.
- Page capacity must be at least `SectionVoxelSnapshot.MAX_BYTES`; an initial 512 KiB page is a reasonable implementation target, but keep the constant isolated so it can be changed from evidence.
- Cap total GPU voxel pages globally. A 32 MiB initial ceiling matches the existing CPU snapshot payload ceiling and makes worst-case residency explicit rather than render-distance-dependent.
- A region may own multiple pages only while the global page budget permits it.
- Each page uses a small best-fit/free-list allocator with coalescing. Allocation failure is normal and leaves that section CPU-only.
- Do not make page allocation depend on terrain mesh-layer lifetime or mesh revision. Voxel residency remains independently invalidated.

The per-region section table should have one fixed entry for each `RegionBatchLayout.MAX_SECTIONS` slot:

```
pageIndex | byteOffset | byteLength | generation | valid
```

`valid` must not become observable until the matching upload is accepted for consumption. A generation mismatch is treated exactly like invalid residency.

## Publication and replacement rules

For a new or rebuilt snapshot:

1. Keep the CPU `SectionVoxelSnapshot` as the authoritative fallback.
2. Reserve a new fixed-page slice. If no bounded slice is available, stop; CPU fallback remains valid.
3. Serialize the exact version-1 snapshot bytes into caller-owned temporary staging memory and record the copy through the existing upload path.
4. Publish page/offset/length/generation only after the copy has been submitted in the ordering domain that will consume it.
5. Retire the previous slice only after the old generation can no longer be consumed.

Do **not** overwrite a currently valid slice in place during the first implementation. Allocate-then-swap makes stale-generation behavior explicit and avoids coupling correctness to future compute scheduling.

A region clear/reposition first invalidates its residency table, then releases its pages through normal deferred Vulkan buffer retirement. CPU snapshot budget release remains independent.

## Queue/barrier boundary

The current terrain upload helper records and submits copies on the graphics queue. A first compute probe should also run in graphics-queue order so copy -> compute ordering can be expressed with a normal Vulkan buffer memory barrier and without introducing queue-family ownership or semaphore complexity.

Before any shader reads a resident snapshot, add an explicit transfer-write -> shader-storage-read barrier for the exact buffer/range or a conservatively correct page range.

If compute later moves to a dedicated compute queue, that is a separate milestone requiring semaphore ordering, queue-family ownership handling when applicable, and retirement rules. Do not infer safety from the present same-graphics-queue upload behavior.

## Required verification before compute consumes data

Add a deterministic startup smoke that exercises real Vulkan storage memory rather than only allocator metadata:

1. build one or more known `SectionVoxelSnapshot` records;
2. upload them into non-overlapping fixed-page slices;
3. submit the copies;
4. copy the resident ranges back into host-visible readback memory;
5. wait only the test copy's fence, not `vkDeviceWaitIdle`;
6. compare every byte against `SectionVoxelSnapshot.writeTo` output;
7. replace one slot with a new generation and prove the table points at the new bytes while the old slice is retired safely;
8. exhaust the page budget in a small test fixture and prove the rejected section remains CPU-only rather than growing a page synchronously.

Keep this smoke behind the existing CI startup path. Production gameplay must not perform readback.

## Stop point for the next implementation slice

The next source milestone is complete when bounded region storage pages, per-slot generation/validity metadata, upload, replacement/retirement, and real readback verification are green in CI while normal rendering remains entirely CPU-meshed.

Do not add compute dispatch, model templates, GPU-generated vertices/indices, GPU visibility, or indirect-command generation in that same commit.
