# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Terrain backpressure and benchmark attribution — 2026-10-10

Full native CI #1012 passed at `ab8e3de12b06124d4b3228b5c49009aace52a449`
(run `38028819261`), including Forge compilation/packaging,
offline contracts, startup, Vulkan native rendering/readback,
Create Chronicles and Crash Assistant. RX hardware performance remains
unmeasured.

The next bounded follow-up addresses an avoidable repeat traversal:
both normal/spectator full-graph passes previously set `needsUpdate`
when the worker backlog reached its scheduling limit, and neighbour
readiness failures did the same. These are now retained in the existing
concurrent dirty queue for admission once capacity/loaded neighbours
recover. Genuine camera, frustum, world, off-graph, visibility and
empty/nonempty changes still perform full traversal. New profiler
`terrain_window` fields report full traversals, direct cached
admissions and pending dirty depth, including valid same-renderer
deltas, for an objective RX comparison. Validation of this follow-up
remains pending; no measured speedup is asserted.

## Cached-graph dirty rebuild admission — 2026-10-09

Following fully green native/public #1010 at commit
`ecf7278be54f5cdcdcd7801908ef4cbb5fa8e673`, the next bounded terrain
slice avoids redundant breadth-first traversal for dirty sections already
present in the last visible graph. RenderSection now queues dirty notices
in a concurrent identity set. The render thread checks ring/area traversal
ownership and a 512-unique-section cap before selecting the cached graph.
It rebuilds only dirty members using the existing capacity, neighbor-readiness,
generation, and TaskDispatcher publication contracts. Both preflight and
per-notice consumption verify graph/area membership, preventing async additions
from being submitted against recycled regions. Notices are removed
before task submission so concurrent redirties remain observable; backpressure
and missing neighbors retain the pending notice without forcing another BFS.

Camera/frustum/world changes, topology-changing publications, off-graph
sections and large dirty bursts still take the full graph path. Full traversals,
cached rebuild admissions and pending queue depth are added to terrain stats
for RX measurement. No change to existing GPU terrain or particle defaults,
no claimed speedup prior to matched RX evidence. Full CI validation pending
for this slice.

## Bounded particle and terrain CPU pilot — 2026-10-09

The owner explicitly requested both next optimization tracks. The older #988 RX
RD32 benchmark reported particle tick/render attribution near 4.48 ms/tick and
4.76 ms/frame and terrain setup about 3.8 ms/frame. It used different/earlier
settings than the new O3/O4 candidate and cannot prove any new speedup.

An implementation now avoids full terrain graph traversal solely after an
in-place section upload. Published visibility-mask and empty/nonempty changes
explicitly request graph invalidation; dirty sections, camera/frustum,
repositioning and world reload retain their existing invalidations. Reuse does
not suppress worker work, queue ownership, Forge callbacks or changing draws.

The particle slice isolates per-particle creation/add/tick attribution into an
automated-benchmark-only Mixin. Normal gameplay therefore avoids two expensive
per-particle injected tick callbacks plus source/add hooks; the coarse tick
profiler and full automated per-class capture remain intact. No particle
simulation/tick or rendering logic is rewritten, and the previously conflicting
Immersive Portals per-particle render redirect remains absent.

**Full hosted #1009 succeeded** on 2026-10-09 (commit
`db99a21e198e3df34e761c82ba64f7b7614aa25b`, run `38016373761`):
both offline contracts, Forge packaging, native Vulkan startup, timestamp,
indirect/post/depth, screenshot/legacy FBO, Create Chronicles and Crash
Assistant checks and distributable/log artifact uploads all passed.
A new paired RX capture is required before claiming gains.

A subsequent coarse-frustum caching slice is being validated separately:
exactly equal projection matrix and adjusted camera offsets retain the
last region-frustum classification through graph-only updates. Changed
camera/projection, render-distance/world reinitialization and new coarse
area grids fail closed to full classification. The independent snapshot
avoids comparing a mutable live frustum with itself. The existing
O5 texture-offload and convergence test remain open; do not enable accelerated
renderer defaults or ask for an unrelated hardware test based only on this pilot.

## CI execution optimization — 2026-10-09

The owner requested an aggressive CI speed/execution pass. Implemented
[scope-aware execution and qualification](docs/CI_EXECUTION_POLICY.md): a one-CPU
planner coalesces rapid full pushes, compares the entire range from an actual
successful native ancestor, and allows fast tooling/test scopes or explicit
`CI-Scope: quick` intermediate feedback. Ready PRs and runtime/build/unknown/CI
control changes retain all native gates. Partial results cannot publish qualified
JARs or become full-suite baselines. The 19 offline contracts run with four
bounded workers, complete per-contract logs and subprocess-group cleanup.
Private-pack failures now occur before expensive setup; artifact retention is
bounded. No native fixture was combined or removed. Workflow steps: 40 to 26.

Local selector/failure/publication/process-lifetime contracts and actionlint pass.
The live API lookup recognizes #1005 as the native baseline and requires the
full suite for outstanding audit/CI changes. See the policy for measured local
CPU timings and final validation. **Full hosted #1006 is green** (run `37997041921`, exact executable
`084dc8183ab6612134ddff762fac2ea9b56bf8fd`). The previous fork Actions
usage block is cleared. All 19 contracts, production package, native Vulkan
fixtures, Create Chronicles, Crash Assistant and qualified JAR upload passed;
the new legacy-FBO screenshot oracle printed its pass marker. This also
qualifies the pending prebenchmark audit. A follow-up CI optimization rotates
the stale immutable Gradle cache key to seed the combined mod fixture, expands
the full-suite lookup beyond six partial runs and strengthens baseline gate
checks; validate its controller/CI changes before attributing any speedup.
Keep O5 benchmarking and accelerated-default gates unchanged.

## Prebenchmark audit — 2026-10-09

User requested an adversarial performance/legacy/nonfunctional-code audit before
another benchmark. Baseline `b5034ac` / executable #1005 is unchanged by the
intervening documentation. See [audit findings and validation scope](docs/PREBENCHMARK_AUDIT_2026-10-09.md).

Confirmed fixes cover transparency admission/quota, unused camera profiling,
per-polygon normal allocation, healthy memory-sample work, exceptional texture
batch cleanup, and falsely successful legacy framebuffer operations. Supported
mip-0 texture-backed legacy FBOs now use real Vulkan LOAD passes and borrowed
attachment lifetimes; unsupported renderbuffers fail explicitly. CPU regression
contracts pass and a transformed/native pixel-lifetime oracle is wired into the
existing screenshot gate. Executable audit commit `2eb98ff20118615bcefb9f78b210c339864a598c` is published.
The original audit commits failed to start Actions because the fork was
usage-blocked, despite the workflow resource itself appearing `active`.
[The captured diagnosis](docs/WORKSPACE_VALIDATION_2026-10-09.md#confirmed-ci-blocker)
remains useful history; **#1006 now fully validates the audit natively**.
Local Gradle/Java recovery is recorded below. Use #1006 or a newer full-green
artifact for the next O3/O4 hardware capture, not #1005. O3/O4 defaults and
benchmark convergence rules remain unchanged; DH LOD and automatic Flywheel
adoption remain unimplemented/unqualified. RX hardware performance and
convergence evidence are still pending.

## Audit follow-up and local build recovery — 2026-10-09

Published executable `8f4b2fd262aaa7465f339b2c6829feb2e56e31e1` repairs
bound incomplete legacy-FBO deletion: deleting an object with no native backing
must still restore framebuffer zero/the main render pass. The CPU regression
fails against the previous code and now passes for empty, detached, unbound and
outside-frame deletion. The transformed/native oracle includes this boundary.
`gradlew` now has its executable bit for the documented local check command.
`90d9c004c671f7c7f6ebb6de1d47bd532b3abef6` explicitly requires the legacy
oracle completion marker in the existing screenshot gate. Focused audit/FBO
and the existing prebuild CPU contracts pass. No accelerated defaults or
benchmark rules changed.

Real Forge compilation exposed a defect missed by the isolated audit harness:
`RenderSection` accessed package-private `CompiledSection.transparencyState`
across package boundaries. Published fix
`cbb198364d1ddb31f4bd3f65b80fca1073851b74` uses a narrow public
`hasTransparencyState()` predicate; mutable state retains package ownership.
The audit harness now extracts that production accessor and passes. Real Java/Forge
main/test compilation, processed resources/Mixins, JarJar reobfuscation and
`verifyDistribution` now pass locally, including nested LWJGL module linkage.
All 21 Gradle-check JavaExec regressions completed across the initial build and
an explicit remaining-gates run (`BUILD SUCCESSFUL in 25s`, 23 tasks). The initial
build had no final summary, so its observed completed gates were reconciled with
the explicit successful remaining-gates run. This is local compilation/packaging/
CPU-contract evidence, not full native CI or RX qualification.

[Local recovery and validation boundary](docs/WORKSPACE_VALIDATION_2026-10-09.md)
records the Java 17/Gradle 8.1.1 setup and proxy correction. Native execution
remains unavailable here: scratch software-Vulkan dependencies resolve, but
Xvfb cannot establish display sockets on either the ordinary or alternate TCP
route, before any Minecraft launch. Both publication mechanisms (Git object/ref
and Contents API) still yielded zero exact-SHA Actions runs. Do not retry an old
run as validation of this code. Re-enable the observed fork Actions usage block
before attempting another CI trigger, or use a runtime-capable executor. The
default `dev` branch and repository/billing settings have not been changed.

## Actions efficiency policy — 2026-10-08

VulkanMod remains public on standard Ubuntu hosted runners, so its execution does not use the owner's private-repository minute allowance. Existing docs filters, Gradle cache, concurrency and full renderer/compatibility/packaging gates remain unchanged. AGENTS §16 now requires batching and focused local checks before a milestone push, evidence reuse for docs-only checkpoints and bounded retries for external quota blocks. See [the measured samples and policy](docs/ACTIONS_EFFICIENCY_2026-10-08.md). This is documentation-only work; it adds no renderer/runtime evidence and does not change the O5 hardware gate or defaults.

## Immediate continuation — O5 hardware qualification

This section supersedes the historical #946-only benchmark and Flywheel-first
next-action notes below. The owner explicitly requests safe GPU-first renderer
work and one controlled RD32 O3/O4 capture before selecting the next adapter.

- Recovered branch at `74472d1cefd16ed91a94b2bd794ca187fcca34c6`, the sole
  executable delta after checkpoint `db71db61e446490395cf6eea6cf6875e1130ac7c`.
  Its terrain-convergence settling logic is fully public-CI green in **#1000**,
  run `37448951240`, job `112220508518`. The prior checkpoint #999 also passed.
  Minimum warmup 60 s, continuous terrain quiet 10 s, maximum settle 300 s;
  nonconvergence aborts and actual settling appears in provenance.
- `1080620979a5d38a5a609cd464f76adfa451e305` is fully public-CI green in
  **#1001**, run `37453506436`, job `112235430128`. Adds separate bounded GPU
  timing for explicit graphics texture-upload batches, covering CPU-staged/O3
  copies, O4 compute and atlas dependencies, without a steady-state wait. The
  validation-enabled native animation oracle now requires real query results,
  64-pending-range overflow and completed-query reuse. All public/native and
  compatibility gates plus JAR/log uploads passed. Private real-pack tests skipped.
- Coverage executable `9d333487613e1a466e94f8562cb5ce5b403ad36c` is fully
  public-CI green in **#1002**, run `37454136840`, job `112237469104`. It adds captured CPU/GPU upload-route denominators,
  standard distinct-frame interpolation coverage and separate admitted-source /
  allocated-scratch payload peaks. Local Java query-ownership and attribution
  contracts pass, including warmup, delayed availability, failed-read quarantine,
  capture reset, route counters and retirement/peak accounting. Full local Gradle
  remains unavailable because its distribution download is network-blocked.
  Java/Forge compilation, distributable packaging, validation-enabled animation
  and query oracles, both startup paths, indirect, post/depth chains, combined
  Create Chronicles, Crash Assistant and JAR/log uploads all passed. Private
  real-pack fixtures skipped. No executable CI remains pending for this slice.
- Owner #1002 runtime attempt **did not capture**: terrain warming began
  2026-10-06 19:30:50.905 and strict convergence timed out at 19:35:50.935.
  Last scheduled/published/nonempty counts 28419/28371/6559 do not identify the
  blocker. Missing HUD is a confirmed Forge lifecycle defect: vanilla Gui.render
  hook was bypassed by ForgeGui. Replacement uses a registered Forge overlay,
  wrapped diagnostics/abort status and five-second settling logs. Focused Java
  callback contract passes. Fix `f45aa7ceb8512ea3bfe90a8f1632cfe5d9a4c49f` is
  fully public-CI green in **#1003**, run `37564306133`, job `112608412286`:
  Forge HUD callback routing, Java/Forge compilation, startup, native validation
  animation/screenshot oracles, Create Chronicles and Crash Assistant all passed,
  with JAR/log upload success. Private pack fixtures skipped. No executable CI
  is pending for that HUD slice. The #1003 owner attempt confirms the visible HUD;
  convergence/O3/O4 capture remains the owner-machine gate.
- Owner #1003 attempt also **did not capture**: latest(7).log/debug(4).log
  show warming at 2026-10-07 01:52:12.465 and timeout at 02:07:12.476 (900 s).
  Non-empty count reached 6559 around 80 s and remained stable; recurrent builds
  reset zero-work quiet even after worker/queue drain. Initial-population ownership
  now distinguishes UNCOMPILED tasks from compiled-section maintenance. It spans
  task admission through publication/worker failure/cancelled-work retirement, rejects epoch changes,
  and aborts if initial population resumes during capture. Maintenance remains
  measured and world/mod semantics unchanged. Local ownership/race/teardown,
  maintenance regression, HUD and attribution contracts pass. Latest executable
  `50a92891b45121d73a9271138387e48effba12dc` is fully public-CI green in **#1005**,
  run `37600141289`, job `112722310635`. New ownership/HUD contracts, Java/Forge
  compilation, packaging, both startup paths, indirect/post/depth, validation-enabled
  animation/screenshot oracles, Create Chronicles, Crash Assistant and artifact
  uploads passed; private packs skipped. No executable CI is pending. Owner-world
  initial-population convergence and actual O3/O4 measurement remain uncollected.
  Return to max settle 300 s; do not repeat #1003 or keep
  increasing its timeout. Evidence: docs/BENCHMARK_TERRAIN_CONVERGENCE_2026-10-07.md.
- Next user-machine action is `docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`. Keep
  RD32 and the existing packs/settings; request only O3/O4 with profiling and
  convergence. No unrelated terrain/hybrid/indirect/Flywheel activation or
  deferred reload/re-entry test. Return the new UUID benchmark log + latest.log
  and any animation artifacts. No new hardware performance/adoption evidence
  has been collected, and both O3/O4 defaults remain off.
- Main-graphics timestamps still exclude helper uploads. New `texture_upload_gpu`
  is a whole-batch graphics-queue span including dependencies; never sum it with
  main graphics as a total GPU frame. CPU route denominators exclude hidden/no-op
  updates; custom/disabled CPU routes are coverage limits, not necessarily GPU
  failures. Memory fields include warmup/pending retirement and count payload,
  not allocator overhead. See `docs/PERFORMANCE_PROFILING.md`.
- Once the controlled capture exists, choose measured particle adapter(s), bulk
  texture job/copy preparation, remaining texture CPU work or another measured
  subsystem. Preserve CPU-visible clocks and arbitrary Forge/mod callbacks.
  Do not preselect a particle family, replace tick semantics or reopen the
  Immersive Portals-conflicting per-particle render redirect.

## Prior qualified executable / CI milestones

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

The owner requested investigation and implementation of GPU texture animation, terrain visibility/indirect commands, hybrid meshing and qualified particle work. `docs/GPU_OFFLOAD_INVESTIGATION_PLAN.md` defines O1–O8, eligibility/ownership/oracles, GPU timing scope, bounded memory, fallback and adoption gates. O3/O4 and O8 attribution now qualify as described above; O5 RD32 hardware measurement is immediate. No accelerated default or Phase 7 gate changed.

## Other qualified features and open adoption gates

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

**Flywheel-only next stage (not the immediate offload priority):** qualify actual ClientLevel/event rendering of the
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
The historical matched #946 benchmark plan is retained as comparison context;
the immediate user-machine gate is now the O5 RD32 capture above:

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
