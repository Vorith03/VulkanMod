# GPU terrain output ownership

## Scope

This checkpoint covers the first non-consuming production-shaped ownership layer for
GPU-generated terrain vertices. CPU geometry and draw parameters remain authoritative.
The output owner only reserves bounded space in the persistent area vertex buffer and
tracks whether an optional GPU result is current for a section generation.

## Allocation contract

Persistent terrain vertex buffers carry both vertex-input and storage-buffer usage.
Generic `VertexBuffer` allocations remain vertex-only.

GPU output reservations:

- use the existing `AreaBuffer` suballocator;
- are aligned to the 20-byte compressed terrain vertex size;
- reserve `faceCapacity * 4 * 20` bytes;
- never grow the area buffer;
- return failure under allocation pressure so the existing CPU mesh stays usable;
- keep translucent and tripwire terrain on CPU.

A worst-case 24,576-face section therefore reserves 1,966,080 bytes. The CI smoke
proves one such reservation fits the initial 3.5 MB area vertex buffer and a second
one fails without triggering area-buffer growth.

## Generation contract

Generation ownership is section-wide, not per terrain layer. `RenderSection` already
owns the monotonic `voxelGeneration`, so GPU geometry for SOLID, CUTOUT_MIPPED and
CUTOUT must all become stale together when that section generation advances.

The owner therefore tracks one generation per packed section. A newer generation:

- immediately revokes every older resident/pending layer for that section;
- prevents a never-before-used layer from accepting an older generation later;
- permits same-generation retries without discarding an already valid resident result
  until the replacement actually publishes;
- rejects overflow, zero output, stale tokens and counts beyond the reservation.

This generation rule is required before the store can be wired into `ChunkArea`
rebuild/unload lifecycle.

## Evidence

CI #474 (run `35067928589`, job `104702390759`) is fully green at source
`7657ae0725d7472fcc2c1ddbd2f55e1a2f22359a`. It covers the storage-capable terrain
vertex allocation contract, bounded/no-growth output reservations, same-generation
retry fallback, generation turnover, unsupported-layer fallback and the complete
Forge/Vulkan/compatibility workflow.

The follow-on section-global generation correction must receive its own green CI
before this ownership layer is treated as lifecycle-ready.

## Remaining boundary

No production compute dispatch writes into these reserved segments yet, and no draw
path consumes them. The next safe sequence is:

1. prove section-global generation invalidation across multiple terrain layers;
2. attach one output owner to each `ChunkArea` and revoke it from the existing
   voxel-generation invalidation/area teardown paths;
3. dispatch the already-proven complete packed-vertex compute into a reserved segment
   with explicit compute-to-vertex synchronization and fail-closed publication;
4. only then add an opt-in draw consumer while preserving CPU fallback.
