# GPU terrain Forge model-instance contract

## Scope

Source commit `3ebc4b2eee14c9c1aa8036068859d910a0f846a0`
closes the reusable baked-template and Java-dependent model-instance qualification
gate for the deliberately narrow ordinary-cube subset. It does not enable GPU
terrain rendering or change production CPU geometry.

## Forge 47.3.0 evidence

The exact Forge 47.3.0 source JAR shows that `ModelBlockRenderer` queries
`BakedModel.getQuads` with `ModelData` and the active `RenderType`, and uses the
state/render-type ambient-occlusion overload. Forge also patches
`SimpleBakedModel` so a baked `RenderTypeGroup` can override the block render-type
set. Therefore checking only Minecraft's legacy three-argument quad method and
`ItemBlockRenderTypes.getChunkRenderType` was not a complete qualification contract.

Forge's defaults for an exact `SimpleBakedModel` have the required static boundary:

- the five-argument quad method delegates to the immutable simple-model quad lists;
- `getModelData` returns its input unchanged, so `ModelData.EMPTY` stays empty;
- the render-type query is either the baked simple-model group or the state default;
- custom/wrapper/dynamic model classes can override these methods and remain rejected
  by the existing exact-class requirement.

## Enforced contract

Qualification now:

1. requires the actual Forge render-type set to contain exactly one entry and for it
   to be `solid`;
2. requires state/render-type ambient occlusion;
3. queries the same five-argument Forge quad overload used by terrain rendering with
   `ModelData.EMPTY` and the solid layer;
4. compares every quad's vertices, tint index, direction, shade/AO flags, and sprite
   identity across seeds `0`, `42`, and `0x6a09e667f3bcc909`;
5. rejects any seed-varying result before it can enter the immutable GPU model table.

The prior gates remain in force: exact `SimpleBakedModel` class, one canonical quad
per direction, no unculled geometry, opaque-white baked colors, no tint, solid
layer, no fluid/block entity, and exact sprite/UV capture. During real section
capture, position-dependent nonzero offsets still reject the voxel. Everything
outside this contract retains `CPU_REQUIRED`.

## Evidence

CI #417 (run `34807713756`, job `103862733527`) is fully green across compilation,
distribution, both Vulkan startups, post-chain, depth, screenshot, Crash Assistant,
Chat Heads, Flywheel, logs, and artifacts. Four startup fixtures each retained the
same qualification result:

- 1,730 qualified templates from 24,135 runtime states;
- 342 referenced sprites;
- 24,135 state-index entries;
- 484,092-byte device-local model table;
- exact CPU ABI/readback, compute state-to-template/UV lookup, compact face rows,
  bounded overflow, and packed position/UV vertex checks.

No vanilla fixture exercised the new rejection reasons, which is expected: exact
vanilla simple models use Forge's default static contract. The checks are primarily
fail-closed protection for resource packs and modded baked render-type groups.

## Remaining boundary

This completes model/template selection for the accepted subset, not final terrain
vertices. Lighting and AO numeric input remain unresolved, and production allocation,
publication, indirect generation, lifecycle integration, and CPU fallback removal
remain open. The next lighting decision still requires real Create Chronicles demand
telemetry.
