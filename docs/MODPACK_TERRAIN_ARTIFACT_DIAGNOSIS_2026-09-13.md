# Modpack world-space artifact diagnosis

## Observed behavior

The reported artifact appears visually close to the center of the screen, occurs only
in the Create Chronicles modpack, and has a physical edge that can be approached by
flying. That last observation makes a purely screen-space overlay or post-process less
likely: the leading class of failure is malformed world-space geometry large enough
to dominate the view from a distance.

The earlier pasted log and screenshot bytes were not available in the continuation
workspace, so this is a code-path diagnosis rather than identification of one exact
block or mod.

## Leading cause

All baked block terrain, including Forge/modded models, ultimately reaches
`TerrainBufferBuilder.CompressedVertexBuilder`. Section-local floating-point XYZ is
multiplied by 1900 and cast directly to signed 16-bit values. The representable input
range is only about -17.25 through +17.25, and the old path had no finite/range check.

Vanilla section-local block geometry is normally near 0 through 16 and fits. A modded
baked quad can legally contain unusual transformed coordinates. If one component is
outside the compressed range, Java's narrowing conversion wraps it into another
signed-short position. One bad vertex attached to otherwise normal vertices produces
a large triangle or plane with a real world-space edge. This explains all three
important observations: modpack-only, apparently fixed in the view at long distance,
and an edge that can be reached.

This is not caused by the new GPU-terrain experiment: that path is still diagnostic,
all voxels retain `CPU_REQUIRED`, and it allocates no production geometry.

## Opt-in confirmation

Start the affected instance with this additional JVM argument:

```text
-Dvulkanmod.debugTerrainVertices=true
```

Reproduce the artifact and force nearby chunks to rebuild if necessary. Search
`latest.log` for:

```text
VULKANMOD_TERRAIN_VERTEX_RANGE
```

The warning includes the block registry ID, full state, world block position,
section-local input coordinate, and wrapped packed coordinate. Warnings stop after 32
entries. The flag is read at startup and disabled by default; it does not clamp, skip,
or otherwise change rendered geometry.

If a marker appears, the block/state and position identify the source model. The fix
should then be scoped to preserving that model's coordinates (or conservatively
routing it through a compatible wider terrain format), not globally clamping vertices
and distorting geometry.

If no marker appears while the affected chunks are rebuilt, the next suspects are a
mod block-entity/custom renderer or Flywheel-managed Create geometry using an
incompatible transform. A corrupted terrain allocation/section offset is less likely
because it should usually align to chunk/region boundaries and also affect vanilla
content. A post-process or sky/fog effect is less likely because those do not normally
have a reachable world-space edge.
