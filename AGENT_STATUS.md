# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current executable / CI state

- Current GPU-offload candidate `f1ddc72397ed132dab16b5e953db369ba76a6ed3` is fully public-CI green in **#998**, run `37447468935`, job `112215647015`, first attempt. O3 now keeps exact-class/Forge-metadata-free animated source mips in bounded device-local residency and performs discrete frame changes with GPU buffer-to-image copies. O4 adds opt-in same-graphics-queue compute interpolation using `shaderFloat64`, device-local scratch and compute-to-transfer atlas writes while Forge retains CPU clock/frame ownership. The validation-enabled native oracle passed reordered/repeated/implicit/filtered metadata, odd/rectangular and zero-extent mips, exact ABGR/alpha/Java-double-truncation pixels, hidden/first-use refresh, source-mutation fallback and all existing public Vulkan/compatibility gates. #997 proved the exact pixels but exposed missing transfer-write ordering; #998 adds the required transfer-write -> transfer-write dependency and is validation-clean. Properties remain opt-in: `vulkanmod.gpuAnimatedTextureCopies` and `vulkanmod.gpuAnimatedTextureInterpolation`; no hardware speedup/default-adoption claim exists yet.
- Bounded particle owner/cost attribution is also qualified through **#995** after removing a per-particle render redirect that conflicted with Immersive Portals. Automated captures now report bounded per-class tick timing/allocation, creation/add/removal churn, source/provider identity and render type; render CPU time remains the existing aggregate world attribution to preserve portal semantics. This evidence is intended to select one narrow GPU particle adapter rather than replace arbitrary Java particle behavior.
- Current executable `3d01d50a6e73fc84f0daec067d248000f65d3d16` is fully public-CI green in **#988**, run `37372339264`, job `111972319995`, first attempt. The O2 diagnostic numeric texture-animation reference passed all seven actual transformed Forge/native cases: exact ABGR/alpha/double truncation, upload cadence, hidden clocks/first-use refresh, reordered/repeated/implicit/filtered metadata, odd rectangular/all-positive-mip pixels, Forge zero-extent guards and custom subclass CPU exclusion. Every existing public gate and JAR/log upload passed; private packs were skipped. Local Java vectors pass and reject alpha/interpolation/boundary mutants. Generated source was unavailable; downloaded transformed SpriteContents/Ticker/InterpolationData bytecode was inspected and confirms the reference. This is render-thread qualification, not immutable source admission, an implemented GPU copy/compute path, a loaded-world Flywheel result or a hardware speedup. Contract: `docs/TEXTURE_ANIMATION_OFFLOAD_CONTRACT.md`. No CI is pending for this slice. The next hardware gate remains the exact matched **#946** RX stationary run described below.
- Pipeline retirement fix executable `cd88b978098d2613387f41b000a0262f49eed1b2` is fully public-CI green in **#987**, run `37356098042`, attempt 2, job `111922107549`. Cold-history recording, immutable retirement-safe PipelineState keys, persisted native replay/exact mask/topology handle reuse, rendered pixels and compatible resize all passed; every existing public gate and JAR/log upload passed; private packs were skipped. Local Java regressions pass and fail against each former implementation. Attempt 1 completed depth rendering successfully but timed out after its pass marker with exit 124; one bounded unchanged rerun passed. The post-pass stall has no backtrace or established cause and does not close the separate #801 shutdown abort. No CI remains pending for this slice. Prewarming remains off by default; hardware hitch/adoption gates remain open. Details: `docs/PERFORMANCE_FEATURE_IMPLEMENTATION.md`, observed prewarming section.
- CPU event precision executable `3e71a7eda3d3edd3dc40a9afdc425dcf2a6eda38` is fully public-CI green in **#975**, run `37220910466`, job `111490885523`. The actual optional RenderLayerEvent constructor/mixin fixture exposes the old distant float precision loss and checks repaired rotated pose/normal, fractional origin, caller/copy isolation, invalid inputs and exact-world reference-counted capture retirement. Disabled backend events allocate no matrix snapshots. All public/native/combined compatibility checks and JAR/log uploads passed; private packs were skipped.
- An opt-in `vulkanmod.flywheelWorldProbe` implements the next real ClientLevel/RenderLayerEvent mixed GPU/Oriented CPU raw-pixel probe. It waits for a loaded client/shader and an inactive frame, uses synthetic white images under the live atlas/lightmap identities, draws two distant-origin/recreation frames, checks foreign task/world/crumbling rejection and restores caller mappings/state. Compilation/default-startup CI is green through #984; loaded-world runtime evidence remains uncollected. Global backend activation is unchanged. Instructions and exact boundaries: docs/PERFORMANCE_FEATURE_IMPLEMENTATION.md, loaded-world probe section.

- Latest feature-code executable `2a7fc3391ae6e11f5c5598d1f4127d78b64cd12f` is fully public-CI green in **#974**, run `37183077787`, job `111379372847`. Implementation `eadbe477e068d8c131c40df5a3fbd1ec5b9bea9d` adds an explicitly callable transformed Engine option, exact matching solid/cutout RenderType admission, origin-relative event scene composition and caller-state restoration. Six native state/event frames (including edge/gap witnesses at 30 million blocks), actual pinned MaterialManager -> ModelData -> lazy shared mesh -> native RGBA pixels, recorded-owner retirement, CPU fallback and every existing public gate passed. No validation errors/SYNC hazards were reported; JAR/log uploads succeeded; private packs were skipped. #973 compiled but its startup fixture checked world atlas/lightmap availability before supplying synthetic images; the follow-up checks missing textures at actual descriptor-consuming draws. The ordinary constructor remains CPU-first and Backend/InstanceWorld remain unchanged. Details: docs/PERFORMANCE_FEATURE_IMPLEMENTATION.md, final section. No CI is pending for this atomic slice.

- Latest hardware-validated executable is build **#935**, commit `48b0f06b4c36b01ead161b05c18da8688d8a637f`.
- #935 is fully CI-green and completed the canonical RX 6900 XT / RADV 180-second stationary benchmark.
- Current performance candidate adds three benchmark-driven optimizations after #935:
  1. `061bc6edd5b9250b0508e267e393d22b1aaa3066` — batch animated sprite copies across the complete texture tick;
  2. `322f8b15e8a62081554321bcb0131b7645386858` — skip repeated same-atlas transition `HashSet` lookups;
  3. `d1da0cc298e6ab46c99f06bb86ec89fcaf8cb26a` — reuse the mapped texture-staging `ByteBuffer` view instead of allocating a wrapper for every subupload.
- `282d6c00...` was an incomplete attempt to move texture memory-pressure sampling to the outer batch. It was immediately reverted by `4f0f0e0e7a59d95f7282f60d85819610a5dea5fb`; do not treat it as an active optimization.
- Matched benchmark candidate is `1dc5cf4cf074cc3bb35b726e5ca3d0bf46d2e937`, validated by completed successful CI **#946** / run `37066271693` (live Actions job evidence inspected 2026-10-02). It retains the three post-#935 optimizations and adds benchmark-only texture outer-batch/copy-flush efficiency and nesting-safe world-render attribution, with warmup exclusion. Earlier attribution runs #942/#943 failed; signature fixes were validated by #945 and the capture-boundary correction by #946. Hardware validation of this executable remains pending.
- Model/instance ownership prerequisite `437fff77ef38722de85edd5dd9ec36e802c26935` is fully public-CI green in **#967**, run `37135821753`, job `111240032366`. It qualifies shared-model import, actual legacy Flywheel instance lifecycle/record encoding, normalized light bytes, UINT32 indices above 65535, caller-slice preservation and append-only uploads/shared-mesh retirement. #966 exposed an unsafe JOML heap-buffer write in the actual Flywheel fixture; explicit numeric matrix writes corrected it and #967 passed. Flywheel remains on its working fallback. Private real-resource-pack fixtures were skipped. Hardware adoption remains pending; use #946 for the unchanged-workload comparison.
- Material ownership `ab4a87def00d79f8232f3b1f91d4aedefe157964` is fully public-CI green in **#968**, run `37142141537`, job `111258615844`. Actual MaterialManager/MaterialGroup/Material API, supplier-once/material/layer/state/world-generation caching, owned CPU release, unsupported delegation and origin recreation/stale-handle checks passed. JAR/log uploads succeeded; private packs were skipped. This supplies a callable ownership/delegation layer, not a production engine or qualified fallback renderer. Backend availability is unchanged.
- Callable CPU-first Engine and actual Batched/Params vertex-consumer fallback `9e7bca9f6d199b60c0d421e5446c31a7d27753c5` are fully public-CI green in **#969**, run `37155602660`, job `111298239954`. Actual emitted ModelType/OrientedType vertices and ownership/origin/task routing passed; JAR/log uploads succeeded. Private packs were skipped. This CPU slice does not register InstanceWorld, enable the backend or qualify universal custom fallback rendering; GPU shader qualification is the separate #972 gate.
- Native CPU batching lifetime `5fb3d7024b2f4d0d0f55018e18648debfa319496` is fully public-CI green in **#970**, run `37156114140`, attempt 2, job `111300814405`. Lazy private allocation, grown-buffer release on emission abort/retirement, fresh-source recreation and idempotent/stale-builder checks passed the actual optional fixture. Attempt 1 failed before those checks in Forge early-display union-filesystem class loading (`FileSystemNotFoundException`); one bounded rerun passed without code changes. JAR/log uploads succeeded; private packs were skipped.
- Prior shader qualification executable `f6400db90f81a4e33dd681e81ad141816c45090e` is fully public-CI green in **#972**, run `37157165558`, job `111302811813`. It qualifies the pinned transformed/world/block shader, instance color/light override, normalized XYZ diffuse, atlas/light/fog alpha separation, alpha discard, model/world matrices, mixed-axis cylindrical linear fog, per-draw dynamic UBO isolation, resize/zero count and frame-fence pipeline retirement. All nine raw attachment pixel cases and Vulkan synchronization validation passed, together with actual Flywheel CPU/lifetime checks and every existing public gate. JAR/log uploads succeeded; private packs were skipped. #971 used the deliberately opaque screenshot API in an alpha oracle; a test-only raw transfer corrected that fixture without changing production screenshots. This shader qualification is now integrated by the experimental constructor in #974; the default Engine remains CPU-first and no InstanceWorld/backend adoption is enabled. No CI result remains pending for this atomic slice.
- Focused benchmark/optimization evidence: `docs/PERFORMANCE_BENCHMARK_OPTIMIZATION_2026-10-02.md`.
- Previous mip-copy batching design/evidence: `docs/PERFORMANCE_TEXTURE_UPLOAD_BATCHING_2026-10-01.md`.

## Build #935 RX benchmark — authoritative current performance evidence

Frame-limit provenance correction (2026-10-05): the user has never enabled a 260 FPS cap. `config/Options.java` maps raw value **260** to **Unlimited**. The historical profiler field `fps_cap` prints `options.framerateLimit().get()` directly, so `fps_cap=260` records the Unlimited option and must not be interpreted as an active cap. The #825/#932/#935 comparison settings and next #946 capture remain **Unlimited**, with VSync off. Preserve the user’s existing setting; prior notes describing a 260 FPS cap were corrected. No executable or benchmark measurement changed.

The fixed automated stationary benchmark completed cleanly for **180.003 s** on the user's RX 6900 XT / RADV Create Chronicles setup with unchanged camera/framebuffer and the canonical benchmark configuration.

Final aggregates:

- client tick: **25.208 ms average**, **31.030 ms p95**;
- texture tick: **17.474 ms average**;
- complete `SpriteContents.upload()` bodies: **9.834 ms average**, **13.057 ms p95**;
- texture work outside those bodies: **7.640 ms average**, **10.330 ms p95**;
- sprite uploads: **1310.502/tick**;
- texture subuploads: **6469.043/tick**;
- texture allocation: **305.737 KiB/tick average**;
- particle tick: **1.883 ms/tick average**, allocating **2547.872 KiB/tick average**;
- main graphics GPU: **1.277 ms average**, **1.362 ms p95**;
- terrain GPU: **0.921 ms average**;
- GPU world-other: **0.290 ms average**.

Compared with build #932, #935 improved:

- `SpriteContents.upload()` average **11.232 -> 9.834 ms** (~**12.4%**);
- texture tick average **18.542 -> 17.474 ms** (~**5.8%**);
- client tick average **26.085 -> 25.208 ms** (~**3.4%**).

Sprite/subupload call rates stayed effectively unchanged, validating that #935's gain came from reducing upload overhead rather than suppressing animation work. Keep #935's mip-copy batching.

## Current bottleneck interpretation

This scene is CPU-tick limited, not main-graphics-GPU limited. Do not prioritize terrain GPU, queue/present, or frame-fence micro-optimization from this evidence while a ~25 ms client tick remains.

Animated texture work is the dominant VulkanMod-owned recurring cost. The current post-#935 candidate therefore removes three additional repeated operations while preserving animation cadence, selected frames, interpolation, staged bytes, staging limits, image-layout ownership and visibility behavior.

### Why the mapped-view reuse is particularly strong

The benchmark reports **305.737 KiB/tick** of texture allocation over **6469.043 subuploads/tick**, approximately **48.4 bytes/subupload**. `StagingBuffer.copyTexture()` created one `MemoryUtil.memByteBuffer(...)` direct-buffer wrapper per subupload. The current candidate caches that wrapper for the lifetime of the mapped staging allocation and invalidates it on resize. The next RX benchmark should show whether this accounts for most of the measured texture allocation.

## Other optimization evidence — do not guess

### Particles

Particles are a real secondary CPU/allocation hotspot (**1.883 ms/tick**, ~**2.49 MiB/tick** allocation), but the current profiler only attributes the aggregate `ParticleEngine.tick()`. It does not identify the responsible particle class/provider/mod. Do not change generic particle semantics or collections from this benchmark alone. If still material after the texture pass, add bounded owner/type attribution first.

### Client entities / apparent tick remainder

The client-tick leaf summary leaves roughly 4.3 ms outside its named leaves. The broader profiler separately measures `client_entities_tick` around **3.8–4.4 ms/tick** in steady windows, explaining most of that difference. Do not reopen it as an unknown bucket.

### CPU world-render-other

`world_render_other` is commonly roughly **2–3 ms/frame** on CPU, while corresponding GPU world-other is only **0.290 ms average**. The CPU residual contains multiple vanilla/Forge/mod rendering activities and is not yet fine enough to justify a VulkanMod behavior change. If it becomes the next priority, split attribution before optimizing.

### Texture memory-pressure gate / layout record

Per-subupload `MemoryDiagnostics.enforceSystemMemorySafety()` fast-path work and `TextureUploadLayout` construction remain possible secondary overheads. The attempted outer memory check was reverted because it did not suppress the existing inner checks and therefore did not actually remove work. Revisit only with a complete semantic change and new measurements after the mapped-buffer allocation is removed.

## Settled compatibility / project context

- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.
- Immersive Portals current nested-world compatibility behavior is RX-confirmed working; preserve its semantic compatibility boundary unless new evidence contradicts it.
- Distant Horizons currently does **not** work in the user's setup. Treat compatibility as unresolved and do not assume it is functional in performance or roadmap testing.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from this benchmark work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**. Its comparable measurement gates remain open.
- The owner explicitly reprioritized the current engineering detour toward moving safely transferable CPU work onto the GPU. Continue O3/O4 measurement/adoption, measured particle adapters, existing GPU terrain/indirect/hybrid qualification, and the experimental Vulkan Flywheel engine where exact fallback boundaries are proven. Do not reinterpret this as permission to move arbitrary Forge/Java callbacks or gameplay simulation without a semantic oracle.
- Phase 7 GPU-terrain/hybrid remains **6/11**. Its implementation is substantial; the next visibility/indirect and hybrid adoption gates require RX movement/portal/dirty-rebuild evidence rather than another speculative rewrite.

## Attribution capture-boundary correction

A follow-up source review found that the #945 helpers admitted warmup texture/world calls, unlike the parent profiler. The correction gates texture samples on the captured client-tick scope and world samples on captured frames. Attribution summaries now execute inside `finishAutomatedCapture` after its admission guard, rather than from a mixin HEAD that could emit/reset on a rejected finish. Added an executable Java 17 contract covering warmup exclusion, out-of-tick calls, recursive/overflow world ownership, copy-flush counters and reset; CI runs it before the full build. Local Java compilation failed because the available external JDK crashed with SIGBUS; no local Java test pass is claimed. CI **#946** passed the new Java contract, Forge build/distributable checks, packaged portal anchors, both Vulkan startup paths, indirect shadows, ordinary/depth post chains, screenshot readback, combined Create Chronicles compatibility and Crash Assistant. The JAR upload succeeded. Private real-pack fixture steps were skipped, so this run does not establish real-pack coverage. Use #946 for the next hardware benchmark.

## GPU offload investigation plan

The owner requested a plan for investigation and implementation of GPU texture animation, terrain visibility/indirect commands, hybrid meshing and qualified particle work. `docs/GPU_OFFLOAD_INVESTIGATION_PLAN.md` defines O1–O8, eligibility/ownership/oracles, GPU timing scope, bounded memory, fallback and adoption gates. O1 is the matched #946 comparison; O2 is the exact Forge animation contract/oracle and can proceed while hardware evidence is pending. No offload implementation/default or Phase 7 gate changed.

## Next useful action

The owner authorized implementation of the complete performance feature shortlist.
`docs/PERFORMANCE_FEATURE_IMPLEMENTATION.md` tracks actual paths and adoption gates.
Published slices:

- Persistent SPIR-V and Vulkan driver caches: full public CI **#947** green.
- Usage-driven animated textures: opt-in, native clock/all-mip oracle **#948** green on one bounded retry; `3ae78f7f...` conservatively marks all block-atlas usage for visible GPU-only terrain. Custom raw-UV consumers and hardware savings remain open.
- Adaptive chunk scheduling: `cb2c1a282d2c6c458757cdd47458738a301ddefa`, off by default; **#950** green including real queue/worker ownership.
- Optional EntityCulling bridge: `addbbf009a0926c36a79595644c5698c85eef78d`, **#952** green including actual Forge 1.7.2 dispatch and guard-before-cancellation ordering. User installation/version and full portal/custom-bounds effectiveness are unknown.
- Separate-server/pregeneration offline tooling: **#954** green. Contracts cover parity, Forge launch/version, record locks, copied staging/rollback, JVM and bounded generation commands. Python 3.11 is explicit in CI. No matching server distribution, user world or host is available; actual deployment/generation has not occurred.
- World scaling: `worldRenderScale=1.0` defaults native; opt-in 0.5–1.0 bilinear world composition before native GUI. Full public CI **#961** passed native attachments, resized pixels/orientation, native overlay, world-icon timing, camera/transparency effects/depth, external chain resize invalidation and abort restoration. Transformed-bytecode checks passed world/post-effect/GUI order and original IP redirect retention. Both scale wrappers are excluded with IP installed, so the user's portal pack retains native resolution. Production adds no host readback/device-idle wait. User-world/reload/visual quality/hardware adoption remains open.
- Vulkan instancing prerequisite: `ffc6422adbb8a937666199fbaadb35e8e01ccf49` adds immutable binding-1 matrix/packed-field layouts, device admission checks and bounded indexed instance drawing. Descriptor-free pool fix `56e62c2222c58c0f6fe23b8753b92229faa91417` qualified it in full public CI **#963**, including native slice-offset, matrix/color/light, firstInstance, zero-count, resize and subsequent ordinary-draw pixels. This is not an enabled Flywheel engine. Native 32-bit-index coverage is now qualified by the model/ownership slice in #967.

The published model/instance ownership slice adds:

- owned, bounded built-in BlockModel CPU import without GL EBO/pool/VAO/writer calls;
- range-validated immutable sequential/custom indices and 16/32-bit selection;
- immutable shared Vulkan model buffers with idempotent deferred retirement;
- actual optional legacy Instancer/ModelData ownership, aligned 108-byte numeric records,
  normalized shifted light bytes, dirty updates, removal/compaction, transfer/back,
  failed-packing retries and origin membership clear;
- append-only per-frame instance uploads through Drawer's existing fence-qualified arena,
  including caller cursor preservation and multiple changing snapshots within a frame.

Full public CI **#967** / run `37135821753`, job `111240032366`, qualified the
corrected slice at `437fff77ef38722de85edd5dd9ec36e802c26935`. The preceding #966
passed native pixels but exposed JOML's unsafe heap-buffer matrix write in the actual
Flywheel CPU fixture. Explicit ByteBuffer.putFloat component encoding fixes it;
#967 passed the actual BlockModel sequential/custom index and shading imports,
actual ModelData dirty/removal/transfer/origin checks, native index/light/upload/
retirement pixels, distributable packaging and every existing public gate. JAR and
smoke-log artifact uploads succeeded; private resource-pack fixtures were skipped.
Local Java ownership/input-admission contracts pass. Full local Gradle build is
unavailable because the distribution download is network-blocked. All implementation
is published and locally synchronized. No CI result is pending for this atomic slice;
no measured hardware speedup is established.

**Next implementation stage:** qualify actual ClientLevel/event rendering of the
experimental transformed + CPU Engine, including exact world/task/origin ownership
and mixed native/CPU material routing. The bounded native state/scene/material draw
is green in #974; its pixel fixtures use synthetic atlas/lightmap images after real
RenderType setup and do not establish loaded-world or portal rendering. Preserve the
ordinary CPU-first constructor and existing working fallback. Do not enable
Backend.isOn or register an InstanceWorld engine until world/event rendering and
complete fallback coverage qualify. #968 covers material/source/origin ownership,
#969 callable CPU transforms, #970 native CPU batch lifetime, #972 transformed
shader pixels and #974 native state/event composition plus actual material drawing.
Resource reload/re-entry remains deferred by the user, not completed by these fixtures.

The CPU fallback qualifies exact owned built-in BlockModels with sequential quad
topology and Batched numeric transforms. Its external unsupported interface has
ownership/delegation/clear/close fixtures, not a qualified renderer. Nonsequential or
unknown model/program/format rendering, crumbling, translucent state/ordering,
reload, world and portal ownership still need integration. The pinned GPU shader's
diffuse formula differs from the CPU fallback's unshaded/constant-ambient rules;
preserve the documented boundary. Details and evidence are in
`docs/PERFORMANCE_FEATURE_IMPLEMENTATION.md`. Do not call GL model pools/VAOs,
custom Model.createEBO or GL-bearing model.delete from the Vulkan path.

Observed graphics-pipeline variant prewarming is implemented and native-qualified
in #987 but stays opt-in; hardware hitch/adoption evidence remains open.
Far-terrain LOD/DH numeric-data adapters remain unimplemented. DH suppression smoke is not functional LOD.
The original GPU-offload investigation/implementation O1–O8 in
`docs/GPU_OFFLOAD_INVESTIGATION_PLAN.md` also remains in scope; existing terrain/
indirect/hybrid infrastructure and paused Phase 7 gates must be preserved.

The instance-input, ownership, callable CPU-engine/lifetime, native material
shader and experimental state/event dispatcher slices are completed atomic milestones; no CI result is pending for these slices. Continue under AGENTS.md section 3A and this checkpoint. The full feature
request remains unfinished; do not convert prerequisite qualification into backend adoption.
The matched hardware benchmark remains the independent next user-machine gate:

1. Use CI-green build **#946** for the exact same automated stationary RX benchmark contract used for #935.
2. The latest supplied `08881808-771c-499a-b9bd-81f1db2c468c` capture is the already-recorded #935 evidence, not a post-optimization result; do not mistake it for candidate validation.
3. Compare in this order:
   - total texture tick and client tick average/p95;
   - `sprite_upload_ms_avg/p95`;
   - texture allocation KiB/tick — primary validation for mapped-view reuse;
   - sprite/subupload calls per tick — must remain near #935 to prove unchanged semantics;
   - `non_upload_ms_avg/p95` — determines whether ticker/interpolation work becomes the next texture target.
4. Inspect the new `texture_outer_batch_attribution` summary to separate outer batch drain/layout/submission from animation iteration and assess regions per copy flush; inspect world-render attribution for CPU residual ownership. Do not subtract independent p95 values as though they were a residual percentile.
5. If texture cost is no longer dominant, use the same run to choose between particle owner/type attribution and finer CPU `world_render_other` attribution. Do not preselect either before seeing the new profile.
