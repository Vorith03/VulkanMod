# Codebase reuse audit — 2026-10-20

The follow-up [maintainability review and second reuse pass](MAINTAINABILITY_AND_REUSE_REVIEW_2026-10-20.md) adds native-access, compute-cache/construction and unused-helper findings, plus prioritized maintenance risks.

## Verdict and scope

There are worthwhile opportunities to remove generic/custom infrastructure, but no established drop-in library replacement for this Forge Vulkan renderer. Prioritize obsolete code and dependency removal, library sorting, and shader-front-end evaluation. Evaluate allocator replacement separately; preserve production GPU ownership, synchronization and recovery contracts.

Audited baseline: `forge-1.20.1`, `682b0d6bb59b249c5be66517209f720e54ac6d1d`. Full CI [#1074](https://github.com/Vorith03/VulkanMod/actions/runs/39308824108) completed successfully; full-validation job passed. Private pack configuration/download/smoke steps were skipped. JAR artifact `12214688900`; smoke logs `12214474322`. This is inherited executable evidence, not a fresh test or proof of performance.

This is a repository-wide structural/dependency/duplication audit with focused semantic review of replacement candidates. It is **not** a line-by-line correctness certification of every file, a completed migration, or proof that no other duplication exists. All 406 main Java files (66,067 lines), 196 resources, 29 test files, 41 scripts, the workflow and build configuration were inventoried. Java import/reference scans and a heuristic repeated-method scan covered every main Java file. The accompanying [inventory](REUSE_AUDIT_INVENTORY_2026-10-20.tsv) records hashes, sizes and subsystem classification; inclusion means scanned/inventoried, not every algorithm individually proven.

No runtime source, dependencies, defaults or experimental activation changed. This owner-requested audit is a bounded detour from the active Phase 7 world/portal evidence gate.

## Findings, in recommended order

### 1. Remove the unused liquid-renderer fork before maintaining it further

**Confirmed repository facts:** `render/chunk/build/LiquidRenderer.java` has no consumer found by whole-repository name search. `ChunkTask` uses Minecraft's `blockRenderDispatcher.renderLiquid`; `mixin/render/vertex/LiquidBlockRendererM` adapts the real Forge liquid vertex method. The unused class carries the Fabric `@Environment` annotation. `build.gradle` explicitly retains compile-only `fabric-loader:0.14.21` for this annotation.

**Recommendation:** verify no out-of-repository consumer is supported, delete the obsolete class and remove the annotation-only compile dependency. Check whether the Fabric Maven repository has any remaining consumer before removing it. This removes a redundant implementation and build dependency together; it does not change the current liquid path. Do not substitute the old fork for Forge's patched liquid renderer.

**Validation before merging executable cleanup:** search annotations/metadata/reflective names, compile/package and existing liquid vertex, minimal-client and modpack gates. Static reference absence is strong cleanup evidence, not proof about unknown external integrations.

### 2. Use existing primitive stable sorting instead of owning a mergesort

**Confirmed:** `render/util/SortUtil.java` implements mergesort/insertion sort. Its production consumer is `TerrainBufferBuilder.putSortedQuadIndices`, sorting integer indices by descending float distance. fastutil is already used throughout the project.

**Candidate:** fastutil `IntArrays.stableSort(indices, comparator)` with `Float.compare(distances[right], distances[left])`. This preserves the current descending comparator and avoids boxing; do not use an unstable sort or subtract/cast float differences. Validate exact fastutil API availability in the resolved Minecraft classpath before editing.

**Validation:** differential comparison against current output for empty/singleton, random, equal distances, infinities, signed zero and NaNs; retain stable ties and resulting translucent index order. Check allocation/throughput only if claiming performance. Expected benefit is reduced maintenance; FPS benefit is unmeasured. This is not authorization to resume the superseded BufferBuilder micro-optimization workstream.

### 3. Stop extending the handwritten GLSL grammar without evaluating a parser

**Confirmed:** `vulkan/shader/parser/{GlslConverter,InputOutputParser,UniformParser}.java` parses declarations with line splitting and `StringTokenizer`, assumes a single declaration per line and supports a limited uniform type set through `layout/Field`. `InputOutputParser` synthesizes fragment output `fragColor`. These are actual grammar/compatibility limits, not evidence that a currently loaded shader fails. `ShaderInstanceM` already reuses Minecraft's `GlslPreprocessor` for `#moj_import`; `SPIRVUtils` already uses shaderc for compilation.

**Candidate:** evaluate a pinned Java-17-compatible GLSL AST parser/transformer for declaration extraction and rewriting, retaining our Minecraft resource/uniform semantics. `glsl-transformer` is a real Java parser/transformation library, but its current upstream advertises AGPL-3.0 and separate licensing arrangements and explicitly does not perform general preprocessing or full semantic/type validation. It is an evaluation candidate, **not** an approved dependency or drop-in compiler. Select an exact version and compatible licensing/distribution approach before adoption; don't silently import current upstream or assume an older release's terms.

**Acceptance:** replay bundled core/effect shaders plus actual resource-pack/mod shaders; test multiline/qualified/multiple declarations, arrays, comments and shared samplers; compare generated bindings and native pixels; measure reload time/memory; validate Forge packaging and transitive parser dependencies. Keep Minecraft's resource resolution/preprocessing and shaderc. Do not build our own general compiler or promise that a parser automatically supports arbitrary shaderpacks.

### 4. Evaluate VMA's existing virtual allocator for free-range bookkeeping

**Confirmed:** `AreaBuffer` implements best-fit search, range splitting and coalescing. `voxel/RegionVoxelPageAllocator` separately implements bounded aligned ranges and coalescing. VMA already allocates backing buffers/images through `MemoryManager`. Upstream **LWJGL 3.3.1 source**, fetched through GitHub's contents API, contains `vmaCreateVirtualBlock`, `vmaVirtualAllocate` and `vmaVirtualFree`; this candidate does not inherently require upgrading LWJGL.

**Candidate:** VMA virtual blocks can replace generic free-space algorithms for subranges of a large buffer. Do **not** treat this as replacement for `AreaBuffer.Segment` readiness callbacks, producer invalidation, upload queue order, stable reused offsets, mesh revisions, reserve-without-growth, backing-buffer growth or fence retirement. Those remain renderer policy. Check exact pinned native VMA behavior as well as Java binding presence.

**Disposition:** plausible maintenance improvement, medium/high migration risk, no benchmark or migration qualification. Start with an isolated allocation trace/oracle, including non-power-of-two element alignment, fragmentation, exhaustion, failed reservation, double free, growth and unchanged offsets. A virtual block has a fixed extent: design growth/address preservation explicitly. Native transitions may cost more than the small Java free lists. Keep the current allocator if the experiment does not reduce complexity or demonstrate relevant value.

### 5. Prefer Minecraft widgets over duplicated generic interaction behavior

**Confirmed:** `config/widget/OptionWidget` manually implements `Renderable`, `LayoutElement`, `GuiEventListener` and `NarratableEntry`, including focus/hover/render behavior; `OptionList2` already extends Minecraft's `ContainerObjectSelectionList` but overrides substantial event/render handling. A custom option model supports staged apply behavior.

**Candidate:** incrementally base controls on Minecraft `AbstractWidget`, `CycleButton`, `AbstractSliderButton`, `Button` and existing list behavior. Keep the desired styling and staged Apply semantics. No evidence justifies adding an entire configuration framework solely for these controls or replacing JSON persistence with Forge config now.

**Validation:** keyboard/tab navigation, narration, mouse dragging, disabled/hidden controls, scrolling, resize, cancel/apply and persisted values. This is maintenance/accessibility work, not a rendering performance claim.

### 6. Consolidate small generic helpers; do not import a utility framework

- `MathUtil` duplicates clamp/lerp helpers available through Minecraft/JOML/JDK operations. Current `SwapChain` calls use `(min,max,value)`; Minecraft `Mth.clamp` uses `(value,min,max)`. A mechanical rename would be wrong. Preserve float/NaN semantics where relevant.
- `chunk/util/Util.flooredLog` is a manual bit scan used for positive area dimensions. `31 - Integer.numberOfLeadingZeros(value)` handles positive values directly; specify invalid-input behavior rather than casually changing it.
- `chunk/util/Util.align` and `vulkan/util/VUtil.align` duplicate positive integer alignment arithmetic. A shared checked helper is sufficient; a bitmask formulation is invalid for arbitrary non-power-of-two strides.
- Mutable `vulkan/util/Pair` has an existing consumer in `mixin/chunk/LevelRendererMixin`; use an already-available pair type or a named record only after checking mutation and identity requirements. Too small to justify a dependency.
- `Util.posLongHash` and `MemoryManager.findMemoryType` have no consumers found by symbol search. Two older DebugScreenOverlay mixin classes contain repeated helpers but neither class is named in the current mixin configuration. Verify plugin/reflective registration before deleting them. Do not mistake inactive copies for two active runtime hooks.

These are small cleanup candidates, lower value than removing the whole unused liquid fork.

### 7. Reuse our existing device capability query, not repeated native discovery

The repeated-method scan found matching `graphicsQueueSupportsCompute` implementations in `GpuTextureInterpolationCompute` and `ParticleBillboardCompute`, plus related query helpers in probes. Both production helpers cache a Vulkan queue-family query.

Expose a shared capability through device/queue ownership if both lifetimes really match; preserve uninitialized-device handling and reset it on device replacement/destruction. This is internal consolidation, not a reason to add a Vulkan framework. Keep smoke-test oracles independent where sharing production logic would weaken validation. Likewise, shared lighting-map/telemetry calculations deserve review, but matching source does not by itself prove their oracle roles should be merged.

## Areas already reusing the right tools, or requiring project-specific code

| Area reviewed | Existing reuse / why retain it |
| --- | --- |
| GPU memory and compilation | VMA owns real Vulkan memory allocations; shaderc compiles SPIR-V. Custom Java lifetime wrappers remain necessary. |
| Matrices and frusta | JOML matrices, vectors and `FrustumIntersection` already do the numerical work. `MatrixProduct` delegates to JOML; it is scratch/lifetime glue. `Matrix4fM` changes depth-range policy and must not be removed as a redundant math implementation. |
| Collections and persistence | fastutil, Guava, Gson and JDK concurrency/NIO/security are already used. JSON and atomic writes are not a newly invented serialization system. |
| Shader/pipeline cache | `Pipeline` already uses Vulkan pipeline caches; `CompilationCache` adds bounded disk persistence and compiler/device identity checks. An in-memory cache library would not replace native compatibility, atomic files or corruption checks. |
| Profiling | `CpuGraphicsProfiler` already records JFR; GPU timestamps and ownership/adoption counters measure things JFR does not know. [Open PR #12](https://github.com/Vorith03/VulkanMod/pull/12), head `3db6d0d55ae46fae594696bd271c09c44877d921` when checked, extends generic flight recording. Evaluate that work before adding another recorder; no merge/rebase performed. |
| Chunk building | `ChunkTask` already delegates ordinary block/fluid rendering to Minecraft/Forge and resolves Minecraft `VisGraph`. Custom compressed layout, publication generations and GPU/CPU ownership are backend-specific. |
| GPU terrain | Generic voxel/greedy meshers do not automatically implement baked models, exact light/AO/tint, Forge exceptions, APPEND atomicity, portal frusta and CPU recovery. No compatible drop-in replacement was established. |
| Texture animation / particles | Shared compute execution implements exact Minecraft clocks/mips/interpolation and supported billboard rendering. Generic animation/particle engines do not preserve Forge callbacks and exception/fallback semantics automatically. |
| Render targets / queues / GL bridge | Vulkan synchronization, descriptor/frame lifetime, OpenGL-call interception, post-chain and screenshot adaptation exist because Minecraft and optional mods expect a different backend. LWJGL supplies bindings, not these policies. |
| Create / Flywheel / portals / optional mods | Adapters translate pinned mod APIs and resource/world ownership into Vulkan behavior. Replacing them with OpenGL-only upstream paths would restore incompatibility. Retain default-off/unsupported boundaries. |
| Model and vertex paths | `CubeModel`, `TerrainBufferBuilder`, batching and vertex mixins retain layout/transform/backend behavior. Reusing their generic utilities is separable from replacing the production path. |
| Scheduling / reusable queues | `TaskDispatcher` already uses JDK concurrent queues/futures. Its priorities, completed-native-result backpressure and cancellation/publication rules need more than a generic executor. `ResettableQueue` has identity rewrite/revision semantics absent from `ArrayDeque`; do not blindly substitute it. |
| Resource reload and native diagnostics | Purging delegates to native allocator APIs; generation-safe retirement and pack retention are integration policy. A generic memory monitoring package would not fix ownership. |
| Build / test / CI | ForgeGradle, JarJar, Mixin, Gradle and Python stdlib already supply machinery. Runtime native smoke fixtures deliberately exercise transformed production code; JUnit alone would not replace that coverage. Avoid a test-framework migration for its own sake. |
| Resources / shaders | Minecraft-style shader resources coexist with Vulkan-specific bindings/layouts. Similar shader bodies are not sufficient evidence to collapse assets across direct/indirect/region formats or resource-pack precedence. |

## Sources and next action

External capabilities checked against primary sources on October 20, 2026:

- [VMA virtual allocator](https://gpuopen-librariesandsdks.github.io/VulkanMemoryAllocator/html/virtual_allocator.html)
- [LWJGL 3.3.1 VMA binding source](https://github.com/LWJGL/lwjgl3/blob/3.3.1/modules/lwjgl/vma/src/generated/java/org/lwjgl/util/vma/Vma.java)
- [fastutil IntArrays 8.5.11 API](https://javadoc.io/static/it.unimi.dsi/fastutil/8.5.11/it/unimi/dsi/fastutil/ints/IntArrays.html) — resolved game version/API still requires build-classpath verification
- [glsl-transformer capabilities, preprocessing limits and licensing](https://github.com/IrisShaders/glsl-transformer)
- [SPIRV-Cross](https://github.com/KhronosGroup/SPIRV-Cross) / [LWJGL bindings](https://javadoc.lwjgl.org/org/lwjgl/util/spvc/package-summary.html): possible later compiler-derived layout/reflection validation. Not a source transformer, not currently packaged, and not worth adding native distributions solely to delete a small layout helper without an evidenced need.
- [Java 17 JFR](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/Recording.html)

Recommended implementation order if reuse cleanup is selected: unused liquid fork/dependency removal, stable-sort substitution, small helper consolidation, then a separately bounded shader parser evaluation. Evaluate virtual allocation only against a trace and cost/complexity comparison. Widget migration is separate UX maintenance. These are recommendations, not newly completed roadmap gates or mandatory prerequisites to the active GPU-first evidence gate.

Audit validation: complete inventory/reference/import and repeated-method scans, call-site review of the candidates above, live HEAD/CI/artifact/private-step inspection and primary-source API checks. `git diff --check` applies to this documentation change. No local game, build, migration benchmark or actual-world GPU test was run; no new renderer CI is needed for documentation-only changes.
