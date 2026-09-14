# GPU terrain lighting-lattice prototype

## Result

The first exact dense-lighting candidates are rejected as a production section ABI.
They are useful as correctness references, but they retain too much data per section
before GPU geometry exists.

Source commit `6b0e85141d46d04c085e2cb30b1731070580520d` adds a startup-only numeric
prototype. `SectionVoxelSnapshot` remains version 4, every voxel remains
`CPU_REQUIRED`, and no production geometry or lighting storage is allocated.
CI #412 (run `34790944136`, job `103814917365`) passed the complete workflow.

## Exact captured semantics

Each lattice point captures values already resolved through the Minecraft/Forge
world interfaces:

- the exact packed result from `LevelRenderer.getLightColor`, including emission;
- raw `float` bits from position-aware `BlockState.getShadeBrightness`;
- the AO diagonal-substitution predicate
  `!isViewBlocking(...) || getLightBlock(...) == 0`.

The prototype also captures the six raw directional shade values. It never attempts
to recreate those semantics from numeric block-state IDs.

Qualified section voxels now additionally require an exactly zero position offset at
their actual world position. Offset models remain on the CPU path rather than adding
three position-dependent floats per qualified voxel to this candidate.

## Layout comparison

The startup oracle compares every sample address used by all six canonical faces of
all 4,096 source voxels. Layout indices must be unique and bounded, and both layouts
must return bit-exact light, shade-brightness, AO-predicate, and directional-shade
values.

| Candidate | Samples | Exact numeric payload | Difference |
| --- | ---: | ---: | ---: |
| rectangular coordinates `[-2, 17]^3` | 8,000 | 65,024 bytes | baseline |
| 18-cube core plus six 18x18 second-shell slabs | 7,776 | 63,204 bytes | -1,820 bytes (-2.80%) |

The slab layout omits only second-shell edges and corners. Canonical AO does not read
those 224 points, but the 2.80% reduction does not justify a second indexing scheme.
The deterministic startup fixture logs warmed capture times for diagnostic context;
those synthetic timings have no acceptance threshold and are not a gameplay
performance claim. The four CI startup fixtures measured 3.516-7.602 ms, with the
two layouts overlapping rather than demonstrating a sparse-layout advantage.

The rectangular payload alone is more than half of the 122,880 compressed vertex
bytes needed for the exposed boundary faces of a completely closed 16-cube section.
It is also over twice the maximum current version-4 voxel snapshot. Retaining it for
every eligible section would move a large CPU capture and memory cost ahead of a GPU
path that has not yet displaced the CPU mesh.

## Decision and next bounded checkpoint

Do not add either dense candidate to snapshot version 4 or create snapshot version 5
from it.

The next bounded experiment should use the already available qualified-voxel and
conservative face-culling information to measure how many unique lighting points are
actually demanded by surviving canonical face candidates in real sections. Compare
a demand bitset and coarse fixed-size lighting bricks, including worst-case fallback,
without yet capturing or retaining numeric lighting values in production. Continue
only if representative sections reduce the retained points enough to remain clearly
cheaper than their CPU terrain mesh.

That estimator is now implemented and documented in
`GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md`. Its corrected exact maximum is
7,752 reachable points; real Create Chronicles density evidence is still required.

Production CPU meshing, arbitrary Forge callbacks, unsupported models, translucent
and tripwire geometry, resource-generation ownership, and all existing fallback
rules remain unchanged.
