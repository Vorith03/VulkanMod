# GPU terrain bounded compute output

## Scope

Source commit `c8c409265f271f2f35da48de62268fd07746645d`
makes the diagnostic compact face/vertex output allocation capacity-driven. This is
groundwork for the Phase 7 hybrid-meshing fallback contract; it does not publish GPU
terrain, clear `CPU_REQUIRED`, or alter production CPU geometry.

## Contract

The caller supplies `compactCapacity` in the compute push constants. Fixed per-voxel
face descriptors remain available for the complete 4,096-voxel section, while every
compact-dependent region is sized from that capacity:

| Region | Base/size in 32-bit words |
| --- | --- |
| Header | 6 |
| Fixed descriptors | `4096 * 6` |
| Compact descriptors | `compactCapacity` |
| Unit-cube corners | `compactCapacity * 4` |
| Optional model face rows | `compactCapacity * 10` |
| Partial 20-byte vertices | `compactCapacity * 4 * 5` |

The six header words are state sum, flag sum, checksum, requested candidate count,
written compact count, and overflow flag. Workgroups atomically reserve nonoverlapping
candidate ranges. A reserved slot at or above capacity still contributes to the
requested count and leaves its fixed descriptor, but does not write any compact,
corner, model, or vertex word. It atomically sets overflow instead.

This means a future production caller can reject the result and retain/rebuild the
CPU mesh when `overflow != 0`, without trusting truncated geometry or risking a GPU
buffer overrun.

## Verified bounds

- Capacity zero still allocates the full fixed diagnostic region: 24,582 words /
  98,328 bytes.
- The forced-overflow fixture uses capacity 127: 29,027 words / 116,108 bytes.
- The existing maximum diagnostic capacity remains 24,576 faces: 884,742 words /
  3,538,968 bytes.
- Java rejects negative capacities and capacities above the six-faces-per-voxel
  maximum before allocating or dispatching.

The forced-overflow oracle independently validates all fixed descriptors, exact
requested/written counts, the overflow flag, 127 unique real compact candidates,
and every emitted corner. It also requires all disabled model/partial-vertex output
to remain zero.

Follow-on source commit `5550d6e3247c87609b1990f7cd352f7333c76f67`
uses capacity 131 with a live baked-model table. For every nondeterministically
selected compact candidate it verifies the exact dynamic corner/model/partial-vertex
bases, model sentinel, nine-word face row, and all five words of each packed partial
vertex against the independent CPU oracle. Unsupported states must retain zero model
and vertex rows.

## Evidence

CI #415 (run `34803346357`, job `103850253149`) is fully green. It compiled and
packaged the distributable, compiled the shader at runtime, and passed both Vulkan
startups. The startup log reported `VULKANMOD_VOXEL_COMPUTE_OK` with 7,936 candidate
faces and the bounded-output overflow contract. Post-chain, depth, screenshot,
Crash Assistant, Chat Heads, Flywheel, log upload, and build artifact upload also
passed.

CI #416 (run `34803738729`, job `103851375878`) is also fully green after the joined
bounded-model extension, across the same complete workflow.

## Remaining boundary

This proves safe capacity handling inside the current oracle only. It does not yet
provide production output allocation, draw publication, indirect commands, or
lighting/color completion. The unresolved lighting ABI still depends on real Create
Chronicles density telemetry described in
`GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md`.
