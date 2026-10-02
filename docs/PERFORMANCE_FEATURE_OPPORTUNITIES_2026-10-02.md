# Performance feature opportunities beyond local optimization

Research date: 2026-10-02. Source checkpoint: `6814449721f9a89ecaa0bbe6a1aedb20fe4ed9ef`, Forge 47.3.0 / Minecraft 1.20.1 / Java 17. This is a research shortlist, not implemented functionality or measured gains. It complements [GPU offload plan](GPU_OFFLOAD_INVESTIGATION_PLAN.md); active Phase 5 measurement and deferred lifecycle gates remain unchanged.

## Evidence and interpretation

Latest comparable hardware evidence is #935: client tick 25.208 ms, texture tick 17.474 ms, 1310.502 sprite uploads/tick, 6469.043 subuploads/tick. Main graphics GPU averaged 1.277 ms, but excludes helper texture-upload command buffers. #946 is CI-green with subsequent upload/allocation fixes and corrected attribution; its matched hardware result is pending. Do not treat #935 as current candidate performance.

The strongest opportunities change which work happens, retain reusable results, or restore an accelerated compatibility path. All rankings below are engineering judgments from source inspection and this workload. Steady FPS, tick capacity, hitch reduction, loading speed and visual quality are separate outcomes.

## Ranked investigations

| Priority | Feature | Intended benefit | First evidence needed |
| --- | --- | --- | --- |
| 1 | Usage-driven animated textures | Remove hidden sprite pixel work and transfers | Animated bytes/cost actually sampled across all views |
| 2 | Persistent SPIR-V/pipeline caches and prewarming | Reduce first-use, startup and reload compilation hitches | Cold/warm creation timings and hitch correlation |
| 3 | Vulkan Create/Flywheel instancing adapter | Retain repeated machine geometry; update instance data | Dense running factory/contraption CPU render attribution |
| 4 | Conservative entity/block-entity occlusion compatibility | Avoid expensive hidden render callbacks | Installed mod/version audit and enclosed-factory A/B |
| 5 | Adaptive chunk scheduling and presentation budgets | Better traversal frame times | Build/upload queue, worker contention and p99 correlation |
| Conditional | World pregeneration / separate simulation host | Reduce exploration/server contention | New-terrain versus pregenerated route; server MSPT |
| Conditional | Render scaling/upscaling; distant LOD | Reduce cost in GPU-bound/high-distance scenes | Matching GPU-bound evidence; separate quality profile |

### 1. Usage-driven animated textures

`SpriteUtil.shouldUpload()` currently has one global upload flag; no per-sprite usage gate was found. Investigate advancing animation metadata normally while materializing pixels only for sprites used by rendering. Sodium's official options include visible-only animation, establishing a precedent rather than a drop-in Forge API [S1].

Start with instrumentation. Track sprite references from visible terrain, entities, block entities, items/GUI, particles and every portal view; unknown/custom consumers remain eligible. A sprite absent from the main camera may still be visible in a secondary world. Skipping only upload after CPU interpolation leaves much of the opportunity untouched.

Maintain clocks and refresh the correct current frame before a newly visible sprite is sampled. Use conservative marking/grace behavior until first-use timing is proven; invalidate marks on resource generation changes. Keep custom tickers on fallback. Compare camera-turn and portal-entry behavior, not just stationary counters.

Potential savings depend on hidden work, not the count of hidden textures. Illustratively, removing 4 ms from #935's 25.208 ms tick would reduce tick time about 16% and increase tick processing capacity about 19%; this is arithmetic, not a predicted FPS gain. Combine with GPU residency only after separate A/B measurement: both may eliminate the same work, so benefits cannot be added.

### 2. Persistent compilation caches

`Pipeline.createPipelineCache()` creates an empty runtime Vulkan cache; destruction does not export it. No `vkGetPipelineCacheData` or initialization from saved data was found. `SPIRVUtils.compileShader()` invokes shaderc without an application disk-cache lookup. Existing runtime caching must be retained.

Khronos documents persistence and recording observed variants for load-time warmup [S2]. Proposed additions: bounded SPIR-V cache keyed by complete effective source/includes, stage, compiler/options and target; driver pipeline cache validated against device identity/cache UUID; atomic writes and graceful cache rejection. Warm only observed compatible variants, under a loading budget. Resource reload must invalidate changed inputs. Measure alongside the existing RADV driver cache to establish incremental benefit. Expect fewer compilation hitches, not an automatic steady FPS gain.

### 3. Vulkan Create/Flywheel adapter

`FlywheelBackendMixin` cancels OpenGL backend probing and forces `Backend.isOn()` false. Flywheel remains installed for Create's API and working fallback rendering. Therefore the current compatibility path does not provide Flywheel's accelerated backend.

Flywheel's own project describes instanced entity/block-entity rendering [S3]. A scoped Vulkan adapter could retain shared models and update transforms/light/material instance data for repeated machines. This is a substantive integration project, not permission to remove the OpenGL guard. Pin investigation to Create 0.5.1.j / Flywheel 0.6.11-13; the modern upstream README does not prove its current APIs fit that legacy version.

Attribute dense moving Create scenes first, then prototype one vanilla-like instance family. Require correct lighting, translucent ordering, model reload, moving contraptions and portal world ownership. Unsupported custom shaders/formats retain fallback. No factory-scene gain can be inferred from the stationary texture benchmark.

### 4. Entity/block-entity occlusion

EntityCulling demonstrates asynchronous visibility checks for hidden renderers, with whitelist requirements for unusual bounds such as Create pulleys and beacons [S4]. Verify the actual installed Forge 1.20.1 version and its effective hooks before deciding whether this needs compatibility glue, configuration, or new code. Do not assume the mod is absent or Vulkan-compatible from the upstream README.

Start with render culling; retain simulation and mod callbacks. Visibility must cover all active portal cameras/worlds and show unknown/stale results conservatively. Disable or whitelist problematic bounds, nametags and effects. Measure CPU time avoided against visibility-worker cost. Treat client tick culling as a separate semantic investigation.

### 5. Adaptive chunk presentation

Sodium exposes deferred chunk updates with a documented visual-delay tradeoff [S1]. VulkanMod already has worker tasks and persistent terrain infrastructure; inspect existing admission policy before adding a scheduler.

Measure queue age and frame cost, then investigate deadlines for near/player-edited sections, motion-aware priority, bounded upload admission and adaptive worker concurrency. This can improve p99 while delaying distant geometry. Never reduce gameplay tick frequency to meet a render budget. If command recording itself becomes material, immutable draw snapshots with per-thread/per-frame pools are a conditional next experiment; Vulkan supports parallel recording but many tiny secondary buffers can hurt [S5].

### Conditional deployment and quality changes

- **Pregenerate exploration regions.** Chunky moves terrain generation ahead of gameplay [S6]. This can reduce exploration hitches, not client mesh construction or recurring sprite tick work. Compare the same traversal on generated/ungenerated terrain; account for preparation time and disk use.
- **Separate server simulation.** Hosting the world elsewhere can reduce integrated-server competition when server timings prove it matters. Client animation and rendering remain local; include network latency and equal simulation settings in the comparison.
- **Dynamic render resolution / spatial upscaling.** AMD documents FSR1 as using the current frame without motion vectors/history [S7]. A lower-resolution world with native-resolution GUI is a plausible first prototype for GPU-bound scenes. Validate post chains, depth effects, screenshots and portals. This is a visual-quality mode and will not remove the measured texture tick bottleneck. Temporal reconstruction and frame generation require separate contracts; generated display frames do not speed simulation.
- **Far-terrain LOD.** Potentially reduce full-resolution geometry at long distance while retaining a horizon. Distant Horizons currently does not work in this setup; neither its availability nor CI startup proves working Vulkan LOD rendering. This is a substantial compatibility feature and a separate quality benchmark.

## Recommended next sequence

1. Preserve the matched #946 hardware comparison as the immediate measurement gate.
2. Add bounded sprite-usage/cost and pipeline-creation attribution; these can establish eligibility without changing rendering.
3. Select one visible-animation or compilation-cache pilot from that evidence.
4. Run the established Create-heavy scene and audit existing culling before committing to a Flywheel adapter.
5. Evaluate traversal scheduling and quality/deployment modes against their own workloads.

Use paired runs with unchanged settings and report frame average/p95/p99, client/server tick, affected subsystem, GPU scopes and memory. Resource reload/re-entry and portal correctness remain adoption gates. Keep experimental paths opt-in; reuse the existing offload plan's comparison/adoption framework. Do not sum savings from overlapping techniques or mix lower-quality runs into canonical results.

## Primary sources

- [S1: Sodium official option definitions](https://github.com/CaffeineMC/sodium/blob/dev/common/src/main/resources/assets/sodium/lang/en_us.json)
- [S2: Khronos pipeline caching and warmup](https://docs.vulkan.org/samples/latest/samples/performance/pipeline_cache/README.html)
- [S3: Flywheel official project](https://github.com/Engine-Room/Flywheel)
- [S4: EntityCulling official project](https://github.com/tr7zw/EntityCulling)
- [S5: Khronos parallel command recording](https://docs.vulkan.org/samples/latest/samples/performance/command_buffer_usage/README.html)
- [S6: Chunky official pregeneration guide](https://github.com/pop4959/Chunky/wiki/Pregeneration)
- [S7: AMD spatial upscaling manual](https://gpuopen.com/manuals/fidelityfx_sdk/techniques/super-resolution-spatial/)

Sources consulted 2026-10-02. Modern upstream examples establish feature mechanisms; exact Forge 1.20.1 compatibility is unverified unless stated in local project evidence.
