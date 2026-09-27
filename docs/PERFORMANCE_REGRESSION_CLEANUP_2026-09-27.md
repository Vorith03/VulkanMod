# Post-#284 Performance Regression Cleanup — 2026-09-27

## Scope

This checkpoint records the static performance-regression cleanup performed against public CI build **#284** (`ef0c0fc0426a9e058312773bfd529aca9ebb1a12`, 2026-09-08).

The validated executable after this cleanup is public CI **#784** / `d8a3f390314f9e90b98e56c25e8fd3e3b77d2f4e`. Build/distributable, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-resource-pack steps were skipped as expected.

This was a hostile/static audit and repair pass. It proves that unnecessary allocations, synchronization, diagnostics, timing, and redundant state reconciliation were removed or gated from default hot paths. It does **not** establish a numerical FPS or frame-time improvement; comparable RX 6900 XT measurements remain required for numerical performance claims.

## Removed or default-gated overhead

### Disabled GPU-terrain draw handoff

`ae258b97d040a2101a2830d49033aaf603dcf15b` restores the direct CPU `RegionDrawBatch` path when GPU-terrain draw handoff is disabled. The default-disabled path no longer queries synchronized GPU draw state or constructs `GpuTerrainDrawState`, `DrawCommand`, or `DrawPlan` objects before emitting the same CPU indirect command.

### Terrain upload profiling and disabled completion polling

`ee83ac515f67155c99a31ef8e9170cb5abd20a7f` makes per-copy upload timing opt-in with:

```text
-Dvulkanmod.profileTerrainUploadCopies=true
```

Normal terrain staging copies no longer pay two `System.nanoTime()` calls each. The same change also avoids entering GPU-terrain completion polling when the experimental mesher is disabled.

### NEW_ENTITY diagnostic tracing

`f7d41a5ba2d44b822b376b5065439a19c8ea32b7` makes the bounded ordinary `NEW_ENTITY` draw probe opt-in with:

```text
-Dvulkanmod.traceNewEntityDraws=true
```

Normal gameplay no longer allocates the trace-state `HashSet` or executes the diagnostic trace path. The probe remains available for targeted RX visual diagnosis.

### Fixed Sampler0/1/2 reconciliation

The sampler repair introduced for visual correctness remains intact, but repeated identical ordinary draws no longer re-resolve all three synthetic GL texture ids into Vulkan images.

- `ef1f09c8191f12f13d4a0f7d775a4cad372874d3` introduces fixed-selector mutation versioning.
- `b0dc3f39e45c886a264841aa85f8dc3130e10779` caches the last authoritative Sampler0/1/2 ids plus selector mutation version and returns early when both are unchanged.
- `7033eba95410f713480149999814bf072cf31f87` and `7fce3a2f3829de0202ade0212622f0d346054028` preserve the established shader-slot/legacy-unit mapping contracts while isolating mutation bookkeeping.
- `84c81d611cef4f3523fa8cc27f9348df261dd799` extends bytecode contracts to require mutation invalidation on every Sampler0/1/2 selector path and to require the reconciliation cache to consult it.

Temporary legacy binds and texture-image replacement under a stable synthetic GL id still invalidate the cache, so the #745 correctness repair is preserved.

### Packaged core ShaderInstance lifecycle

`62e9f54ff8ac21d361a7e8b064fe71ac5ed8762f` removes redundant `ShaderInstance.apply()/clear()` calls from packaged Vulkan core-shader draws. Those shaders already read VulkanMod render state directly and `BufferUploader` already has the active shader instance.

Converted legacy/Forge/Immersive-Portals shaders retain the full `apply() -> draw -> clear()` lifecycle because that path publishes live matrices, colors, clipping state, named samplers, fog, lights, game time, screen size, and related state.

`fac40deec34bb4a29fc3c1584810aec9791502b9` wires the existing `BufferUploaderShaderLifecycleContractTest` into Gradle `check`, so the converted-shader lifecycle, fixed-sampler bookkeeping, and exceptional cleanup contracts are exercised by normal CI builds.

### Manual legacy/effect sampler preparation

`f3d3e6768c35c21a3a31a9b227c8743987862757` and `d8a3f390314f9e90b98e56c25e8fd3e3b77d2f4e` remove unconditional stream-built temporary `VulkanImage[]` arrays from converted legacy and effect draws.

Both paths now use `RenderTargetManager.preparePipelineTextures(activePipeline)`, which scans the pipeline first and only materializes a sampled-image array if an attachment actually requires a layout transition. Texture resolution still flows through the active legacy/effect shader state, so named-sampler behavior is unchanged.

## Intentionally retained / measurement-dependent work

The hostile audit also identified costs that are real or plausible but are **not** safe rollback candidates without stronger evidence:

- Ordinary-draw framebuffer attachment scanning remains. Converted Forge shaders can bind arbitrary `RenderTarget`s under arbitrary sampler names, so Sampler0/1/2 state alone is not a sound cache key.
- Forge-safe per-vertex `instanceof ExtendedVertexBuilder` checks remain. Caching wrapper capability can trade the type check for branches, fields, and possibly additional arrays; measure before changing it.
- Terrain upload sequencing remains on the graphics queue where current persistent-allocation overwrite ordering is known-correct. Transfer-vs-graphics queue behavior should be tested as a separate controlled experiment rather than reverted statically.
- Forge terrain render-stage callbacks remain because they are required compatibility behavior.
- Some GPU-terrain bookkeeping still exists in large build/lifecycle files. The highest-confidence default-off costs were removed without broad rewrites; any remaining source-level work should be changed only with a small safe patch surface or measurement showing material cost.

## Performance measurement boundary

For any numerical performance claim, compare exact build **#284** (`ef0c0fc...`) against executable **#784** (`d8a3f390...`) using the controlled procedure in `docs/TERRAIN_PERFORMANCE_BASELINE.md`:

- seed `2026092601`;
- 2560x1440 profile;
- prescribed stationary-camera capture;
- prescribed 1024-block eastbound spectator route;
- identical resource packs, mods, JVM settings, render distance, and GPU-terrain feature flags.

Do not enable `vulkanmod.traceNewEntityDraws` or `vulkanmod.profileTerrainUploadCopies` during normal A/B measurement. If transfer-vs-graphics terrain upload ordering is investigated later, keep it as a separate experiment so it does not confound the #284/current comparison.
