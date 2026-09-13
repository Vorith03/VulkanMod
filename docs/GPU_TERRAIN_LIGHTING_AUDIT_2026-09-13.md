# GPU terrain lighting and color audit

## Scope and evidence

This audit follows the diagnostic partial-vertex checkpoint at source commit
`81e953faefd7e065fcdda19a74e50b22d9a5c3e8`, verified by CI #408. That checkpoint
proves canonical face position and atlas-UV packing only. Color and light remain
zero, CPU terrain output remains authoritative, and every voxel retains
`CPU_REQUIRED`.

The traced runtime path is `ChunkTask` -> `BlockRenderDispatcher.renderBatched` ->
Forge 47.3.0's patched `ModelBlockRenderer` -> VulkanMod's `VertexConsumerM` ->
`TerrainBufferBuilder.compressedVertex`. The Forge 47.3.0 source patch and its
`ForgeModelBlockRenderer`, `QuadLighter`, `SmoothQuadLighter`, and
`FlatQuadLighter` implementations are the version-specific evidence. The optional
Forge experimental light pipeline defaults to disabled, but its output algorithm is
different and must be treated as a separate capability.

## Exact output obligations

The 20-byte compressed vertex is not complete until the following values match the
CPU path:

| Field | CPU source | Requirement for the qualified subset |
| --- | --- | --- |
| Position | baked position, block pose, and `BlockState.getOffset` translation | Preserve the Java float-to-short conversion at scale 1900; canonical template corners alone are insufficient for offset blocks |
| Color | baked vertex color x tint color x per-vertex AO x directional shade | Tint-indexed faces are already rejected; baked color must also be identity until it is represented |
| UV | exact baked atlas UV | Proven by CI #408 at the Java conversion scale 65536 |
| Light | four per-vertex packed light values | Preserve block and sky halves plus emissive/max behavior before writing the two shorts |

`VertexConsumerM.putBulkData` always consumes baked quad color data for this path.
Therefore `quad.isTinted() == false` does not prove white output. The qualification
predicate now requires all four baked color words to be opaque white. Forge also
allows a quad to disable ambient occlusion independently of the model-wide AO flag;
the predicate now requires `BakedQuad.hasAmbientOcclusion()` so the validated subset
does not silently take Forge's flat-face fallback.

The accepted subset continues to require a shaded, non-tinted, canonical unit face
on every direction. For such faces the remaining RGB multiplier is grayscale:
per-vertex AO multiplied by `BlockAndTintGetter.getShade(direction, true)`. Alpha is
one. Packing must use `VertexUtil.packColor`'s Java truncation, not rounding.

## Neighborhood data boundary

State IDs are not an adequate lighting ABI. Minecraft and Forge call position-aware
methods and hooks while resolving AO, light emission, occlusion, and offsets. GPU
code must receive CPU-resolved numeric data and never infer these semantics from a
block ID.

Inspection of the official Minecraft 1.20.1 client bytecode plus Forge 47.3.0's
patches proves that a canonical boundary face samples as far as two blocks from its
source voxel. The AO corner openness checks read `origin + face + tangent + face`.
For a section-edge source, a one-cell halo is therefore insufficient.

The smallest simple rectangular source is a 20 x 20 x 20 lattice covering section
coordinates `[-2, 17]` on each axis. A more compact layout could combine an 18-cube
core with six sparse second-shell slabs, but the extra indexing complexity should be
justified by measurement. Each lattice point is expected to need, at minimum:

- the exact packed result used by `LevelRenderer.getLightColor`, including emission;
- the raw float bits of the state's shade-brightness/AO contribution;
- the CPU-resolved occlusion predicates used for AO corner substitution.

Six raw directional shade values and each qualified voxel's three raw offset floats
are separate numeric inputs. Offsets are position-dependent and must either be
captured or conservatively disqualify the voxel. The existing six 16 x 16
`SOLID_RENDER` boundary planes are sufficient only for the current conservative face
rejection experiment; they are not an AO/light halo.

This is deliberately a design bound, not snapshot v5. The exact canonical-face
sampling order, corner substitution, light blending and vertex remap are now covered
by a CPU-only oracle against the runtime `ModelBlockRenderer.AmbientOcclusionFace`.
The remaining ABI decision is how to encode the resolved values and position offsets
without making CPU capture as expensive as the meshing work being replaced.

## Forge pipeline split and fail-closed rules

When `experimentalForgeLightPipelineEnabled` is true, Forge replaces vanilla AO with
`SmoothQuadLighter`. It samples a full 3 x 3 x 3 neighborhood, resolves transparency,
packed block/sky light, AO values and shape occlusion, then applies a different
interpolation function. A production GPU capability must do one of the following:

1. prove a separate exact oracle and advertise a distinct lighting ABI/capability; or
2. leave every voxel `CPU_REQUIRED` while the experimental pipeline is enabled.

It must not reuse a vanilla-oracle result under that setting. Configuration reload
must invalidate eligibility or be checked at dispatch time.

Other fail-closed conditions are unresolved position offsets, a non-white baked
vertex color, per-face AO disabled, tint, unshaded faces, custom/model-data geometry,
unsupported render layers, fluids, and block entities. The model registry handles
the baked-model conditions; world-position conditions belong to section capture.

## Next implementation checkpoint

Use the proven two-block radius to prototype a bounded CPU-resolved numeric lattice
and measure its capture cost and packed size before changing the snapshot ABI. Include
position offsets or disqualify offset states. Only after that result is favorable
should color/light compute output be added. Snapshot v4, the current compute shader,
production geometry allocation, and `CPU_REQUIRED` remain unchanged by this audit.
