# Complete terrain vertex join — interruption checkpoint

Source inspected: `c190c2b2a8e393bb70aca2e210a37b11847903ca`; source CI #467
is green at `7cbe402a`. This checkpoint starts the next implementation slice.
No renderer code has changed in this slice yet.

## Confirmed source contract

`voxel_probe.comp::writeModelFace` already emits four five-word vertices per
compact face. `VoxelComputeProbe.partialVertexBase(capacity)` calculates the
capacity-dependent base. The live `TerrainBufferBuilder.CompressedVertexBuilder`
and `CustomVertexFormat.COMPRESSED_TERRAIN` use these byte offsets:

| Word | Bytes | Existing / required contents |
| --- | --- | --- |
| 0 | 0–3 | signed-short x/y, scaled by 1900 |
| 1 | 4–7 | signed-short z, then unused two-byte padding |
| 2 | 8–11 | packed RGBA color, currently zero in diagnostic geometry |
| 3 | 12–15 | packed unsigned-short texture u/v |
| 4 | 16–19 | packed block/sky light, currently zero in diagnostic geometry |

The CPU writer does not initialize bytes 6–7. A byte oracle must normalize that
unused padding before comparison; it must not diagnose arbitrary CPU padding as a
GPU geometry mismatch. GPU padding can remain deterministically zero.

`SparseLightingComputeProbe` now accepts a block index and emits six faces of four
(color, light) pairs after its 8,000 diagnostic sample records. Its actual-Minecraft
oracle is `CanonicalCubeLightingSmokeTest.sparseGpuFixture(mask)`, using capture
through `GpuSparseLightingSnapshot.tryCapture` and reflected Minecraft AO.

Compacted geometry order is nondeterministic: each invocation reserves slots with
atomicAdd. The stable join key is the face descriptor: voxel index in bits 0–11,
face ordinal in bits 12–14, valid marker in bit 31. Never zip compact slots with
lighting traversal order. Corner order is Minecraft FaceInfo order; AO remapping
has already been checked against Minecraft, and must remain aligned with UV rows.

## Next atomic implementation

1. Extend diagnostic compute to consume a generation-matched voxel/lighting pair
   and the current qualified model table in one bounded output operation. Reuse the
   existing residency validation and descriptor/page-offset conventions. Avoid a
   CPU readback/reupload join: it would not prove the intended GPU data path.
2. Compute lighting for the descriptor's actual voxel/face and fill vertex words
   2 and 4 alongside existing position/UV output. Require explicit success; missing
   lighting or invalid models must not publish superficially complete zero-lit faces.
3. Compare complete vertices against real CPU-written vertices using the captured
   Minecraft fixtures and live model UV table. Normalize only unused padding.
4. Exercise center/corners, distinct UVs and voxel positions, mixed occluders,
   missing samples, generation mismatch, and forced compact overflow. Check exact
   requested/written counts and ensure invalid/overflow output cannot be consumed.
5. Run full CI and checkpoint before attempting production allocation/publication.

Keep CPU_REQUIRED, CPU geometry and existing renderer defaults intact. A successful
join is diagnostic evidence, not permission to bypass renderBatched. Production
allocation, stale-result rejection, lifecycle ownership and fallback remain separate.

## Resume notes

Relevant implementation: VoxelComputeProbe.java, voxel_probe.comp,
SparseLightingComputeProbe.java, sparse_lighting_probe.comp,
GpuTerrainModelTableSmokeTest.java, GpuSparseLightingGpuSmokeTest.java and
TerrainBufferBuilder.java. No extra runtime density test is needed: previously
supplied logs were recovered and summarized in AGENT_STATUS.md.
