# GPU-first renderer ownership — October 18, 2026

## October 19 particle callback-state correctness candidate — #1056 pending

Executable `ba2f574e8db79ea26b7694d9cb37f31c8d5b2710`,
full [#1056 / run 39232013086](https://github.com/Vorith03/VulkanMod/actions/runs/39232013086)
**pending**. After exact-class and pre-callback admission, particle
getQuadSize/UV/light callbacks may alter the builder (such as enabling
sorting); a new recheck stops stale GPU packing. An exceptional CPU
emission reuses captured values without invoking callbacks twice. A native
synthetic callback mutation oracle checks the prior GPU record is CPU
materialized, the current quad is restored, later quads remain CPU and
no pending packed slots survive. This protects existing production GPU
ownership, not a new transfer of CPU work. Actual Immersive Portals and
Forge mod injections, reload, pixel translucency, RX adoption and
profitability remain unverified. All experiments stay default-off.



## Fixed GPU animation metadata slab — full #1055 correctness-qualified

**Qualified executable:** `629de44061e404e291dae647c8e026b23d841395`;
implementation `8134a113cdaaa9fc3d5c3903752b420ab7a287cf`,
isolated attribution-contract fix `629de440`.
Full [#1055 / run 39227110919](https://github.com/Vorith03/VulkanMod/actions/runs/39227110919)
**PASSED** with Forge JAR artifact **12173844688** and smoke
logs artifact **12175645031**. Native screenshot logs emitted
`VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK` and
`VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK`; offline contracts,
Forge package and both client startups, native texture/particle/
terrain pixel and synchronization, screenshots, public Create
Chronicles/Immersive Portals and Crash Assistant passed. Private
resource-pack fixtures skipped. Earlier #1054 / run 39226851915
failed only `attribution-capture-contract.py` because its isolated
`MultiStats` stub still had seven fields; production statistics now
have eight. That negative evidence is retained.

The opt-in multi-sprite path now reserves exactly **32 × 48 = 1,536
bytes** at the tail of each 4 MiB shared scratch page while active
pixel output slots remain disjoint. Each accepted sprite stages one
metadata row in this fixed slab; its cursor advances only after staging
succeeds. At wrap, the atlas queue first records prior pending compute
and copies. Before the next transfer overwrite, the graphics queue
records a buffer dependency from previous compute shader reads and
transfer writes to new transfer writes, covering the entire slab,
even if the earlier dispatch belonged to a prior submission.
Host-side queue drainage alone is not treated as completion. Failed
eligibility or staging still leaves the old GPU/CPU fallbacks intact;
the 128 MiB pending-aware page cap is unchanged, and a scratch request
too large for the table tail tries independent binding.

A native oracle extends the former two-job exact-pixel fixture by
**320** further accepted jobs on the same live page across forced copy
epochs and separate same-queue command buffers, requires at least nine
table cycles and no extra multi-job fallback, and finishes with exact
alpha/RGB and overlapping O3 overwrite readback. Prior native mixed
CPU/GPU upload and retirement tests also remain; real texture-pack
reload and representative RX use are not yet proven.
`gpu_multi_table_recycles_total` augments existing lifetime
eligibility, acceptance, fallback, grouped-dispatch, job, metadata-byte
and page-resource counters. Group size, metadata barrier cost, full
production adoption, actual CPU displacement, GPU submission overhead,
FPS and p95/p99 are **unmeasured**. All flags stay default-off.

**Next:** with #1055 fully qualified, advance actual-world particle portal/light/reload/frame-slot gates or
generated terrain REPLACE/atomic APPEND indirect ABI. Only request a
representative RX benchmark after meaningful multi-subsystem
integration. The integrated GPU-first release is not complete.
Retain #1050-#1052, #1047/#1048, #1041/#1042 and analyzed #1028
UUID `e05f2b31` negative/baseline evidence.

## Genuine GPU multi-sprite texture dispatch — full #1053 correctness-qualified

**Executable:** `c06f1cbc9c57a09b0df819edc639022bef049096`.
Production implementation `cf3351f726ed143e1006b0504b92bb157020e322`;
validation fixes `4718529021079515bba58fcdef55ee004cbee3f4`
(attribution extractor), `c3b4a43d1f50db011b9e51156951a6fe231918b5`
(native staging-accounting assertion) and `c06f1cbc9c57a09b0df819edc639022bef049096`
(CI planner child-exit race). **Full #1053 / [run 39216055025](https://github.com/Vorith03/VulkanMod/actions/runs/39216055025) PASSED**.
Forge JAR artifact **12169313491**, smoke logs **12169019219**.
The native screenshot test emitted
`VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK` and
`VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK`; public offline/Forge
packaging and both startups, native texture/particle/terrain pixel
and synchronization, screenshots, Create Chronicles/Immersive Portals
and Crash Assistant compatibility gates passed. Private resource packs
were skipped.

The default-off `experimentalGpuAnimationMultiDispatch` gate additionally
requires GPU interpolation, unified texture uploads, GPU animation batch,
source pool and shared scratch. Immutable **48-byte GPU job rows** are
staged into unused high-end space in each shared 4 MiB scratch page,
up to 32 compatible sprites per dispatch. The atlas copy queue drains
pending jobs into a single Z-indexed compute dispatch per shared page
before recording scratch-to-atlas reads. Disjoint source leases/output
slots, original per-mip tables, exact double RGB truncation/current alpha,
atlas copy/overlap ordering, barriers, GPU-safe retirement, and
fail-closed per-sprite GPU/CPU fallback are retained. Accepted GPU work
still bypasses the equivalent CPU pixel interpolation. A native oracle
proved **two accepted distinct-source jobs -> one real dispatch**, exact
pixels, repeated updates and a later overlapping resident copy; existing
transformed sprite-clock, odd-mip and CPU-fallback tests remain green.

**Limits and next action:** Metadata rows are append-only for the lifetime
of each shared scratch page; this is physically bounded by the existing
128 MiB pending-aware budget, but sustained animation churn can exhaust
job-table headroom and increase fallback. Groups forcibly drained by
atlas ordering can contain only one job, so nominal eligibility does
not prove substantial aggregation. Verify actual batch-size distribution,
long-session adoption/retirement, and net metadata staging/barrier cost
before treating this as efficient. Lifetime eligible/accepted/fallback,
dispatch, jobs-dispatched and resource counters are available for this
decision. No representative RX 6900 XT CPU-time saving, extra GPU cost,
FPS, p95/p99 or net profitability is measured. Keep experiments opt-in
and do **not** request a per-experiment owner benchmark.

Preserve negative **#1050** (isolated attribution extractor failure),
**#1051** (native test misclassified 48-byte GPU job staging as CPU pixels),
**#1052** (independent CI planner /proc child-exit race), together with
previous **#1047/#1048** offline extractor and **#1041/#1042** descriptor
negative evidence. Do not repeat analyzed #1028 UUID `e05f2b31`.
Other open release gates: actual-world particle portal/mod lighting,
translucency/reload/frame-slot behavior, generated-terrain REPLACE/atomic
APPEND candidate ABI integrated with indirect selection, and integrated RX
adoption/CPU displacement/profitability. This is a correctness-qualified
subsystem, **not a completed integrated GPU-first release**.
Contract: `docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`.


## Shared GPU texture interpolation resources — full #1049 correctness-qualified

**Qualified executable:** `6faa82e5f05d800dc0fa26fd51f2a775d921d708`
(implementation `8352108e56d6ffec566979cc35795b1499010049`,
harness corrections `a74f41ce` and `6faa82e5`).
Full **#1049 / [run 39208913742](https://github.com/Vorith03/VulkanMod/actions/runs/39208913742)** **PASSED**, Forge JAR artifact **12167505101** and smoke logs **12166663996**. The native `VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK` marker and full offline/Forge/terrain/particle/texture pixel/synchronization, screenshot, public Create Chronicles/Immersive Portals and Crash Assistant gates passed; private resource packs skipped.
Previous qualified executable `29a94f73c72ff30443ff12d45eb77c12805e4e17`
passed full #1046 / run 39205054428, JAR 12165621719,
smoke logs 12165785846.

New opt-in `experimentalGpuAnimationSharedScratch` (requires
`experimentalGpuAnimationBatch` and `experimentalGpuTextureSourcePool`)
lets different immutable source leases on the same source-page buffer share
one compute descriptor and append-only, disjoint slices of a 4 MiB scratch
VkBuffer. The source lease offset and output slot base are applied in
both legacy and all-mip shader ABI, exact double/current-alpha arithmetic
unchanged. The shared page is charged until GPU-safe retirement with a
128 MiB physical reservation cap; over-limit/unsupported work tries the
existing independent binding and retains the CPU fallback.
The atlas queue now drains pending source reads by *overlapping scratch
range*, retaining atlas image overlap and source/copy epoch ordering. Native
source-pool smoke adds two lease/slot output tests and marker
`VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK`.
The production ticker still skips CPU interpolation on accepted GPU dispatches.

**Limit:** this shares allocation/descriptor ownership but still issues
one all-mip dispatch per sprite. Genuine multi-sprite *dispatch aggregation*
remains open; no RX adoption, host/GPU cost, CPU saving, FPS/p95/p99 is measured.
#1047 and #1048 failed only the extracted `texture-transfer-contract.py`
harness (overload signature selection), before native/Forge gates; preserve
this negative CI evidence. #1049 is the first candidate with the full
corrected overload extractor. Keep all pilots off by default, do not request
a user benchmark for this isolated experiment. Contract:
`docs/GPU_TEXTURE_SHARED_SCRATCH_2026-10-18.md`.

Next: design/implement bounded genuine multi-sprite dispatch with
source-job metadata and atlas boundary/fallback protocol. Also retain
particle portal/mod/light/translucency/reload/frame-slot gates and generated
REPLACE/atomic APPEND candidate ABI. Do not repeat analyzed #1028 UUID
`e05f2b31` or erase #1041/#1042 descriptor negative evidence.
Integrated GPU-first correctness/adoption/CPU displacement/net profitability
remain open. Refresh the rolling `docs/NEXT_SESSION_PROMPT.md` every session.


## Producer-owned candidate geometry and readiness — full #1046 qualified

**Executable:** `29a94f73c72ff30443ff12d45eb77c12805e4e17`, full **#1046**, [run 39205054428](https://github.com/Vorith03/VulkanMod/actions/runs/39205054428). **PASSED**. Forge JAR artifact **12165621719**, smoke logs **12165785846**. Public offline/build/package/both startups/native terrain/indirect/texture/particle pixel/synchronization, Create Chronicles/Immersive Portals and Crash Assistant gates passed; private packs skipped.

The terrain input producer now owns immutable per-section/layer numeric geometry and vertex readiness snapshots. Upload/reset/deferred parameter publication/staged commit update them; Segment transitions invalidate the affected owned layer, with old listeners detached at replacement. Candidate publication consumes the snapshots, eliminating changed-traversal Segment polling and pending-readiness retry-every-draw rebuilds. Graph traversal, active-row serialization/hash and GPU command generation checks remain. Full contract and native lifecycle coverage are in `GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`; 24 offline contracts and extracted lifecycle checks passed locally.

This advances CPU preparation elimination for existing GPU indirect ownership; it is not a new mesher or a measured RX speedup. Producer allocations/callbacks add unmeasured costs outside candidate prep timing. Next prioritize genuine multi-sprite texture compute/shared descriptors/scratch; actual-world particle portal/mod/pixel/reload and generated REPLACE/atomic APPEND ABI integration stay open. All pilots opt-in; integrated release incomplete.

## Producer-owned GPU candidate queue containment — full #1045 qualified

**Executable:** `cb248683db075033f0bb28ad42e8882ebba3f6d4`, full **#1045** / [run 39201976864](https://github.com/Vorith03/VulkanMod/actions/runs/39201976864) **PASSED**; distributable Forge JAR artifact **12164426976**, smoke logs **12164093569**. Public offline contracts, Forge build/package/both startups, native GPU terrain/indirect and texture/particle pixels/synchronization, Create Chronicles/Immersive Portals and Crash Assistant gates passed; private pack fixtures skipped.

Production change: for opt-in GPU candidate provenance, `ChunkArea.addSection` now audits queued identity/owned-slot membership, recorded pre-frustum graph or direct-seed membership, and duplicate slot rejection as the traversal producer writes the CPU queue. An optionally tracked `ResettableQueue.contentRevision` detects bypassing `addSection/resetQueue`; registration replacing a queued owned identity revokes the producer proof. `RegionDrawBatch.publishLiveCandidates` checks this O(1) state **before cached GPU command reuse**, removing the previous O(CPU queued sections) per-changed-traversal containment scan. Its remaining producer-active slot walk still validates current owned identity, mesh parameters, upload readiness, graph/direct flags, and post-enqueue direct seed changes. Any incomplete/mutated proof invalidates GPU candidate reuse and lazily falls back to CPU commands. Native regression exercises direct seeds, off-frustum graph stamps, stale replacement, duplicate slot rejection, independent queue mutation and successful fresh traversal recovery. Default CPU-only queues retain revision tracking disabled.

**Disposition: correctness-qualified but performance unmeasured.** This eliminates CPU draw-time containment rework on the experimental GPU candidate path but does **not** eliminate CPU graph traversal, producer enqueue bookkeeping, active candidate slot parameter reads, or all CPU command fallback. It is not an RX FPS/p95/p99 result, proof of portal pixels, or authorization to enable by default. Particle actual portal callbacks/pixels/reload and frame pressure; texture multi-sprite descriptor/scratch compute; producer per-slot metadata/readiness; generated REPLACE/atomic APPEND candidate ABI and integrated RX adoption/net profitability are still open.


## GPU particle descriptor bound qualified — full #1044

**Qualified executable:** `71f1bec858a9c2dbb589d218b172eec624c0dd29`, full **#1044**, [run 39195687691](https://github.com/Vorith03/VulkanMod/actions/runs/39195687691) **PASSED**. Forge JAR artifact **12161603960**, smoke logs **12161404876**. Offline contracts, Forge package and both startups, native Vulkan terrain/indirect plus texture/particle pixel/sync, screenshot, public Create Chronicles with Immersive Portals and Crash Assistant all passed; private packs skipped.

**Production correction:** `6817c071` enforces the 64-descriptor pool reservation on the host, including allocated sets pending frame-fence retirement; it refuses excess GPU work before native allocation and preserves CPU quad materialization. `71f1bec8` isolates an 80-buffer native descriptor-pressure oracle into its own recording frame after three existing exact-geometry passes; also exercises owner teardown/reinit after explicit device idle. The preceding negative evidence is important: #1041 (39194476949) failed the fixture's assumption of native pool rejection; #1042 (39194915798) demonstrated Lavapipe admitted **82 live sets against maxSets=64**. #1043 was superseded by the isolated-frame test before full qualification. The final assertion and all public native/Forge gates are now green.

**Scope:** CPU simulation/light/callbacks and exact campfire eligibility are unchanged; this is a fail-closed resource-safety correction, not a new GPU work-ownership transfer or an observed RX speedup. The native oracle is NOT an actual resource-pack reload or production portal-translucency pixel comparison. Integrated GPU-first correctness/adoption/CPU displacement/added GPU time/FPS/p95/p99, generated REPLACE/APPEND ABI and multi-sprite texture compute remain open. All pilots remain opt-in; do not request a new per-milestone RX benchmark.

## Live recovery and current evidence

Live branch `forge-1.20.1` matched `3edd41b78605fe3a28264792cdf583d5e5c435e2`;
no successor delta. Latest executable `50f39eb5` passed full CI #1028,
run 39143814563. Open PR #12 is historical profiler work, not a competing
GPU-first implementation. Owner reprioritized the next release in this session.

The already supplied #1028 benchmark UUID
`e05f2b31-8ec9-4056-b16f-2ed2c25ccdde` was read directly from the owner file
on October 18. Relevant terminal data, not the older 3.79% estimate:

- Standard interpolation 2,841,737 updates; GPU 106,483 (**3.747%**),
  CPU 2,735,254, all recorded as CPU bulk. Disabled residency fallback
  2,719,880; missing active upload batch 15,374. No dispatch failures/mutations.
- Resident logical charge 131,071.148 KiB of 131,072 KiB; source
  125,817.191 KiB, actual allocated scratch 454.223 KiB. 474 admissions,
  1,246 capacity rejections; pool 31 pages/126,976 KiB, zero pool rejection.
  These are renderer-lifetime figures including warmup/retirement.
- Texture 16.687 ms/client tick (3,529 ticks), upload inclusive 5.587,
  non-upload 11.100. Mixed copy flush inclusive 8.899 ms/tick overlaps
  other scopes; do not add it. Separate upload GPU 2.913 ms/batch;
  main graphics GPU 2.865 ms/frame (3,911 measured GPU frames).
- CPU bulk pixel kernel is the highest ranked Java execution hotspot
  (766 sampled leaves). Samples are not exact milliseconds saved.
- GPU terrain mesher, CPU bypass, draw handoff and hybrid all false.

This does not establish a #1028 speedup or causal regression. It establishes
continued low interpolation adoption and real CPU pixels in the production path.
No unchanged owner rerun is necessary.

## Ownership boundary

| Work | Ownership / real bypass requirement |
| --- | --- |
| Standard animation RGB interpolation/current alpha | GPU, existing float64 O4 shader; true return cancels InterpolationData before vanilla/bulk pixels |
| Original source frame/mip copies | GPU device-local O3, CPU lifecycle and immutable-source snapshot at admission |
| Custom Java texture pixels | CPU generation, shared Vulkan staged transfer; no assumed shader equivalence |
| Animation clocks/resolved Forge metadata/visibility refresh | CPU, preserve mod-visible state and off-thread deferral |
| Particle billboard expansion | Transferable numeric rendering; Java light/callback/collision/portal selection remains CPU until qualified |
| Particle simulation | Hybrid only for an exact class adapter; campfire class name alone does not authorize all provider variants |
| Qualified terrain cubes | Existing GPU mesher, workers capture numeric light/tint/model/halo; bypass CPU renderBatched only on qualified ownership |
| Unsupported terrain/fluids/block entities/Forge callbacks | CPU exceptions, atomic APPEND and generation-safe fallback |
| Graph visibility and lifecycle/submission | CPU until separately equivalent connectivity policy; frustum-only GPU selection does not replace it |

## Implemented first milestone: residency admission

Opt-in `-Dvulkanmod.experimentalGpuAnimationAdmission=true` alongside existing
GPU texture copy/interpolation flags. No additional memory budget.

Previously the first materialized original-source upload reserved an entire
sheet plus possible scratch. Every later allocation failure permanently disabled
that sprite; discrete work could exhaust residency before interpolation-heavy
sprites received first refusal. GPU interpolation could not create residency
itself, even when the first desired update was interpolation.

The new inventory is registered only for the already-qualified standard ticker,
from actual source mip sizes and the resolved frame schedule. Expected avoided
pixels per tick counts interior ticks only for distinct current/next indices.
At the outer texture tick, sort unallocated candidates by expected pixels per
charged byte. Zero-benefit discrete sources use remaining room. Live sources
remain pinned; charged pending retirement reduces available room. Deferred
candidates do not attempt allocation or become permanently disabled. New
inventory, a failed allocation, close, or GPU-safe retirement invalidates the
plan, without per-tick sorting or eviction. Allocation failures still fail closed.

Interpolation can initialize a selected source before entering CPU pixel code.
Successful O4 dispatch returns through the existing cancelling mixin; no CPU
shadow output is generated. Initial source snapshots and clocks remain CPU.
This is a deterministic heuristic, not a measured optimal knapsack or promise
that every interpolated source fits. GPU dispatch batching remains essential:
existing O4 uses one binding/scratch per sprite and one dispatch per positive mip.

Counters reuse standard GPU/CPU update and fallback denominators; add explicit
admission-deferred fallbacks and inventory/plan snapshots labelled current,
at-plan or lifetime. Actual GPU pixels/dispatches and source/scratch costs remain
in existing telemetry. No new profiler subsystem.

## Validation and next implementation boundary

Production Java policy contract covers adversarial first-registration order,
fixed charge, density, pinned live work, allocation failure, pending retirement,
close/reload, oversize and invalid score. Existing native transformed ticker
oracle is extended with a competing 128 MiB discrete ticket plus exact odd/mip/
alpha/clock/hidden refresh and custom CPU fallback. It must run under Vulkan
synchronization validation and full Forge/compatibility CI before qualification.

Local limitation: JRE 17 exists but javac is absent, Gradle distribution fetch
fails with Network is unreachable. No local compilation/native pass is claimed.
Full CI result is pending until the published milestone is validated.

Next: inspect/implement reusable compute job aggregation using the current O4
shader/queue contract, then particle and terrain ownership integration. Do not
promote admission by itself to the complete release or request another hardware
capture for each isolated slice. Default paths stay unchanged while qualification
is incomplete. No new release candidate is qualified yet; CPU time saved,
new GPU costs and end-to-end FPS benefit are **unmeasured**.

## Second implemented texture slice: reusable all-mip dispatch and copy ownership

Opt-in `experimentalGpuAnimationBatch` appends a charged immutable six-word row
per source mip to the existing resident source lease. The same O4 shader reads
these rows with a workgroup-Y mip plane, issuing one dispatch and push-constant
record per sprite instead of one per positive mip. Original source bytes and
Java-double RGB/current-alpha kernel are retained. The shader retains its legacy
one-mip ABI for bindings without the table. Workgroup limits fail before bypass.
All-mip dispatch has inactive lanes for smaller mips; GPU cost remains unmeasured.

Compute output now enters the existing unified copy engine, preserving atlas,
capacity and overlapping-write epochs. O4 no longer drains unrelated copies on
every sprite or emits separate per-sprite atlas write barriers/copy commands.
Before a binding reuses scratch, pending readers of that exact buffer are drained;
the existing transfer-read/compute-write barrier then protects reuse. Compute-to-
transfer dependency remains explicit. This batches copy dependencies/host recording
across O3, O4 and CPU output; it does **not** yet share scratch/descriptor sets
across sprites or aggregate multiple sprites into one compute dispatch. Each
scratch VkBuffer still forms a separate native copy-source group.

The native source-offset/overlap oracle now checks pending O3+O4 queue geometry,
O4-to-O3 overwrites and repeated scratch reuse before its queued read. The real
Forge ticker oracle asserts one dispatch per production interpolation and no
CPU staging on standard GPU first-use refresh, plus exact odd/all-mip pixels.
Screenshot CI exercises the batch pilot with synchronization validation and
requires explicit admission/batch success markers. Existing production-method
queue regression now exercises computed output bounds and scratch-source drains.
Counters separately report queued/direct O4 output updates; dispatch count now
means actual dispatches, not mip regions.

Negative CI evidence: #1029 (39148852755, d340720c) passed the new pure admission
contract but failed an extracted tick harness missing its new dependency. #1030
(39149030428, ea3fb373) then exposed the same missing dependency in the attribution
harness. Both fixtures are corrected by preserving the real policy in the latter
and modelling the tick boundary in the former. Neither failed run reached Forge
compilation or native validation; do not describe them as qualified.

## Third implemented slice: GPU indirect commands without a CPU command shadow

`experimentalGpuIndirectCpuBypass` requires both existing
`experimentalGpuIndirectCommands` and `experimentalGpuIndirectDraw`. This is
separate from terrain meshing. Production `RegionDrawBatch.draw` first publishes
its candidate input and attempts the exact-generation GPU result, then lazily
builds the CPU FrameBatch only if ownership is rejected. On acceptance it does
not walk the CPU visible queue to serialize commands, allocate a CPU command
buffer, or upload those duplicate commands. The GPU storage+indirect output is
the consumed result. Existing frame-slot CPU buffers remain owned for later
fallback, and are rebuilt from current revisions when needed.

Eligibility additionally proves every CPU-visible section belongs to the region's
full owned candidate set, checks bounded candidate slots and current frozen
culling volume, and rejects any configuration using GPU terrain draw handoff.
Candidate v1 cannot encode atomic APPEND pairs, so the existing hybrid command
planner remains authoritative there. Camera/projection/portal-volume changes now
invalidate a candidate generation even when mesh/visibility fingerprints match;
stale-volume or incomplete-superset output is also rejected by the legacy GPU
consumer. CPU graph visibility and candidate numeric metadata remain inputs; this
does not replace graph traversal, remove every CPU frustum test, or establish
representative portal correctness.

Existing renderer-update statistics report GPU-owner region attempts/acceptance,
issued candidate **slots** (including zero-tail no-ops, not actual selected draw
count), and disabled/hybrid/incomplete-superset/stale-volume/missing-output/capacity
fallback counts. `sections` remains the existing CPU-observed count when using
CPU fallback; it is not fabricated from GPU slots. Normal operation adds no GPU
readback or synchronization wait. Existing exact-command/zero-tail Vulkan smoke
remains the GPU oracle. Expanded native region-cache tests exercise no CPU
allocation/upload on GPU ownership, dirty-mesh suppression, current fallback,
re-adoption and safety rejection. This slice still needs its full CI result.

## Qualification update

Full CI **#1031**, run **39149394473**, passed at texture executable
`1e32e359a7b64784b8d530b25ddc5ef98e76970c`. All offline contracts, Forge build/package,
both startup modes, persistent indirect, post/depth, screenshot exact animation
pixels and synchronization, public Create Chronicles and Crash Assistant passed.
The new admission and batch success markers are mandatory in the screenshot gate.
Private packs were skipped. Artifact **12144887049** is the CI-qualified texture
milestone, not the integrated GPU-first release candidate. RX coverage, net CPU
savings, GPU cost and frame-time/tail benefit remain **unmeasured**.

Particles remain the next substantial implementation boundary. The measured class
is not a transferable generic simulation: collision, light/chunk reads, randomness
and Java/mod callbacks remain CPU-owned. A rendering adapter must preserve inherited
billboard semantics, sorting/interleaving, custom shaders and the IP particle
render loop before bypassing SingleQuadParticle CPU expansion. Existing instancing
buffers/pipelines should be reused; no particle acceleration has been implemented
or claimed by the texture/terrain-command slices. Broad terrain generated-geometry
activation and the deferred REPLACE/APPEND portal/dirty rebuild gates remain open.

### Composition correction: candidate v1 versus pending APPEND

Adversarial review of the existing legacy GPU indirect consumer found that
`batch.gpuDrawCount == 0` is insufficient to establish safe candidate semantics:
a pending APPEND pair can already expose ready CPU exception parameters while the
atomic FrameBatch deliberately omits that section until GPU publication. Candidate
ABI v1 could then select the CPU half independently if another ordinary section
made the batch nonempty. Both GPU indirect ownership routes now reject any terrain
handoff configuration, retaining the authoritative atomic planner until a future
candidate ABI represents both halves. The native fallback contract covers this
combination explicitly. This preserves mesher REPLACE/APPEND support; it restricts
combining that support with the older independent indirect selector.

Full terrain-command milestone CI **#1032**, run **39150002619**, passed at
`03f730a596be87d691b01d1ea5c494cab4a2d1f8`, including the mandatory native
`VULKANMOD_GPU_COMMAND_OWNERSHIP_OK` marker, existing GPU exact-command/zero-tail
oracle, texture pixels/sync and all public Forge/compatibility gates. This does
not prove live RX command-bypass adoption, hybrid GPU commands, particle offload,
or FPS improvement. The subsequent APPEND-composition guard needs separate full
qualification before it becomes the final executable checkpoint.

## Precise remaining release gates

1. Texture adoption/profitability: actual standard-update GPU/CPU/fallback coverage
   after ranked admission, CPU texture/preparation cost, separate GPU upload/main
   costs and render-only/tick-bearing p95/p99 on representative RX. Schedule ranking
   is a heuristic, not runtime-visibility weighting or optimal packing; live sources
   stay pinned. Multi-sprite dispatch/shared scratch is still open.
2. Terrain input preparation: full #1038 qualifies a producer/traversal revision
   cache that skips the owned-section scan/hash/Builder on unchanged production
   draws. Changed traversal or mesh still rebuilds the candidate table, pending
   readiness still retries, and the resident indirect selection still needs current
   GPU generation/output and culling volume. All traversal resets invalidate,
   including off-frustum graph stamps outside the CPU queue; ownership/ring reuse
   also invalidate. Incremental updates on actual mesh/graph producer deltas beyond
   the safe unchanged-draw fast path, cache-hit/adoption and CPU/GPU cost metrics
   remain open. Queue membership alone is insufficient: `WorldRenderer.addNode`
   marks a node's last traversal frame before frustum rejection. Preserve
   ring movement, portals, current-volume checks and recovery. Cached CPU command
   lists were already cheap in some stationary intervals; measure profitability.
3. Particle render ownership: qualify a bounded exact render adapter through the
   existing Forge/portal particle loop and mixed/custom draw ordering. Java light,
   world/collision and callbacks remain until a separately proven simulation
   adapter can reproduce them without CPU shadow state or synchronous readback.
4. Meshing/command composition: current candidate v1 does not represent generated
   REPLACE/atomic APPEND pairs. Extend the existing input/output ABI and oracles
   before combining GPU-generated geometry with GPU indirect command ownership.
   Existing qualified worker bypass, stale-result recovery and atomic pair tests
   must remain. Representative portal/dirty mixed-section/lifecycle gates stay open.
5. Integrated release: no default promotion or 144-FPS claim before correctness,
   production adoption, net CPU savings and tail frame-time evidence. No new owner
   benchmark is requested for each isolated slice; retain existing #1028 reference.

The two texture pilots are `vulkanmod.experimentalGpuAnimationAdmission` and
`vulkanmod.experimentalGpuAnimationBatch`; they use the existing copy/interpolation,
unified-upload and source-pool flags. The command pilot is
`vulkanmod.experimentalGpuIndirectCpuBypass`, together with existing indirect
commands/draw flags. All remain opt-in. Java clocks, original mip images, custom
Forge behavior, world-state lookups and CPU recovery remain. There is no fully
integrated GPU-first release candidate yet.

## Final executable qualification for this session

Executable **0e6f3657de3c8193df529954df0bca019a058045** passed full
**#1033**, [run 39150537659](https://github.com/Vorith03/VulkanMod/actions/runs/39150537659).
This supersedes pending qualification of the APPEND composition correction.
Offline contracts, Forge distributable, both startups, native lazy command cache /
current fallback / APPEND safety, existing exact 172-command/512-candidate GPU
selection with 15 off-frustum direct seeds, post/depth, exact texture pixels and
synchronization, public Create Chronicles and Crash Assistant all passed. Private
packs skipped. CI artifact **12145046866**:
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1033-g0e6f3657-all.jar`.
Later documentation-only HEAD reuses this executable evidence.

The full release objective remains **incomplete**: no particle offload, no combined
mesher/indirect ownership, no eliminated per-draw candidate host scan, no RX
adoption/coverage/net CPU-time/GPU-cost/p95/p99/FPS result for these new pilots.
Do not present the CI JAR as an integrated GPU-first release candidate or request
an unchanged baseline. Current implementation and remaining gates are durable in
this document, the roadmap and living status.

## Subsequent particle continuation

A production exact-campfire billboard compute adapter is now implemented behind
`experimentalGpuParticleBillboards`. It cancels inherited CPU vertex expansion,
retains simulation/light/Java callbacks, expands original ordered quad slots, and
keeps the existing graphics shader and mixed/custom ordering. This replaces the
previous "no particle offload implemented" checkpoint above, without qualifying
portal/mod/pixel adoption or net benefit. See
`GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`, living status and next-session prompt for
current executable/CI evidence. The particle milestone passed full #1036 at
`761947c1` (39178618231), including mandatory geometry/sync/uploader/fallback tests;
JAR 12155143862. Final unsigned/bounded storage-range guard needs follow-up full
qualification. Broad particle portal/mod/pixel/adoption and RX benefit remain open.
The wider release remains incomplete.


## Subsequent terrain candidate reuse qualification (supersedes per-draw scan pending state above)

Commit `246ff243fc59e8e9667b299ceb3e846a04b57497` passed full **#1038**,
[run 39180288680](https://github.com/Vorith03/VulkanMod/actions/runs/39180288680),
JAR artifact **12155952145**. Unchanged candidate metadata now returns before
any 512-slot scan, CPU Builder, direct-seed boolean array or hash. A cheap
per-ChunkArea revision advances at every full graph traversal, even when its
visible queue is the same but off-frustum lastFrame/graph provenance changes,
and on owned-section registration, stale reference detection and ring clear.
Layer mesh/visibility revisions, pending segment readiness and exact volume
also invalidate. Full scan+fingerprint remains authoritative after a change;
rejected uploads still retry. CI native RegionBatch oracle exercises stable
reuse and mesh/visibility/volume/ownership/traversal/readiness rejection. This
does not remove the CPU graph traversal, changed-input scans or all GPU dispatch
preparation. No measured RX cache hits or net CPU/GPU/FPS/tail benefit.

The prior particle range safety correction `6a78cfb8` passed full **#1037**
(run 39179150695) on its second attempt, JAR **12156240273**. Initial attempt
failed an unrelated Forge FMLConfig null Boolean during the indirect smoke startup;
identical executable passed all gates on retry. Do not call #1037 first attempt
a particle-compute failure. Particle portal/mod/pixel/reload/pressure and net RX
benefit remain open. This is not an integrated GPU-first release qualification.

## Producer-side equivalent traversal input reuse — full #1039

Executable `e78a7641dffc86a62efaad35066e4733e1d9f702` passed full #1039
([run 39184804795](https://github.com/Vorith03/VulkanMod/actions/runs/39184804795));
Forge JAR artifact 12157573613, smoke logs 12157802321. Native indirect
region tests, Forge startup, texture/particle pixels/synchronization and public
compatibility checks all passed. This supersedes the earlier #1038-only
unchanged-draw candidate reuse checkpoint, but does not imply a hardware speedup.

On GPU candidate gates only, each ChunkArea maintains two 512-bit producer masks:
all owned graph visits at RenderSection.setLastFrame **before** frustum rejection,
and queued sections that entered as direct seeds. Masks reset at traversal rewrite
and area ring clearing; an independent ownership revision changes on registration,
stale-slot discovery and reuse. RegionDrawBatch retains per-layer last-published
masks and owner revision. A new traversal with unchanged graph and seed masks,
owner identity, mesh/queue revisions, ready output and exact frozen culling
volume can adopt the existing GPU table without scanning 512 owned slots,
re-hashing candidate records, allocating a Builder or publishing another
candidate generation. This does not replace CPU graph traversal. It also does
not bypass a required CPU fallback when GPU output is missing/stale.

A changed graph/seed bit, ownership, mesh, visibility, pending readiness or
camera/portal volume retains the existing full scan and atomic publication;
GPU indirect still rejects pending APPEND/handoff v1 and incomplete supersets.
Native smoke specifically asserts off-frustum graph stamps invalidate the
candidate comparison and tests changed direct-seed and ownership metadata.
The masks are per producer and no GPU readback is added. Resource reload and
portal/mod-injection pixel equivalence remain separate gates.

No cache hit/attempt denominator or net CPU accounting has yet been measured
on representative RX hardware. Recording per-visited-section mask writes may
itself cost CPU; do not presume profitability. Next meaningful step is a
bounded existing-profiler coverage/cost discriminator before an integrated
hardware run, alongside further GPU-owned compute work (multi-sprite texture
batching, generated terrain REPLACE/atomic APPEND command ABI, particle portal
qualifications). Keep opt-in gates until equivalent and profitable.

## Sparse active candidate production — full #1040

Qualified executable `1b182d53a18c0b83c5b4c1c52f94c08987ad5a1c`, full **#1040**, [run 39192146854](https://github.com/Vorith03/VulkanMod/actions/runs/39192146854) **PASSED**, JAR **12160493015**, smoke logs **12160477999**. Full offline, native terrain/indirect and texture/particle pixel/synchronization, Forge build/startups, public Create Chronicles and Crash Assistant gates passed; private packs skipped.

Changed traversal now builds candidate v1 from pre-frustum graph visits plus direct seeds in compact producer bitsets; never infer graph visibility from the CPU frustum queue. A membership/owner audit fails closed for missing/stale queued slots. Iteration over the active union replaces the full 512-slot owned-section CPU metadata scan on sparse changed traversals and reduces GPU input rows. Builder starts with 32 records, doubles to the existing 512 cap. Mesh/visibility/revision, pending active-slot readiness, generation, portal culling-volume and GPU output/fallback contracts remain; generated REPLACE/atomic APPEND indirect combination still excluded. Earlier #1039 stable-provenance reuse remains.

`gpuCandidateLifetime` adds lifetime draw/provenance reuse vs sparse rebuild/attempt, prepared/published slot counts, successful publications, producer mask mark attempts (graph plus direct attempts; not unique sections) and publisher preparation ms when `vulkanmod.performanceProfiler=true`. Take differences between benchmark summaries. Prep timer excludes producer per-visit writes and GPU dispatch/submission. This is a correctness-qualified CPU preparation reduction, NOT elimination of CPU graph traversal, and RX cache coverage/net CPU/GPU/FPS/p95/p99 benefit is **unmeasured**. Do not request the already analyzed #1028 UUID `e05f2b31` again.

Remaining: particle portal/mod/pixel/reload/pressure, multi-sprite GPU texture dispatch and shared descriptor/scratch, incremental producer slot data and REPLACE/atomic APPEND candidate ABI, integrated RX evidence. All opt-in, no release completion.

