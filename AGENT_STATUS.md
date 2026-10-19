# VulkanMod Forge 1.20.1 — Agent Status

## October 19 — generated-world draw witness, full #1068 PASSED

**Current fully qualified executable:** `0f76caf7e5377ce27edebbfc23235c5daaa0168d`; full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) **COMPLETED / SUCCESS** on that exact executable (not the later documentation HEAD). Distributable Forge JAR artifact **12203627510**, smoke-log artifact **12202694551**. Successful full-validation steps included offline contracts, Forge compilation/package, both client startup modes, native GPU indirect/terrain (including `VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK` 509-pair selector proof and `VULKANMOD_GPU_GENERATED_REPLACE_CANDIDATE_OK`), texture multi-dispatch, particle, screenshot, synchronization/post/depth chain, public Create Chronicles with Immersive Portals, and Crash Assistant. Private resource-pack fixture coverage remains unavailable (token absent); no actual-world generated-terrain/portal pixel validation was performed by this run. Previous qualification #1066 `360434e664c13050e2eab1174673ed19525ed813` remains the regression baseline (JAR 12196457613, smoke logs 12196891834).

**Actual-world observability introduced, NOT an observed world result:** opt-in `-Dvulkanmod.debugGpuGeneratedWorldDraw=true` adds `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` only after a live loaded-world GPU-owned `vkCmdDrawIndexedIndirect` recording from current generated candidates; REPLACE, atomic APPEND pair and APPEND with no CPU exceptions are separately inventoried, including optional real Immersive Portals portal-view context. Native fixtures do not produce this marker; it does not prove the GPU selected a nonzero command after culling, actual geometry/pixels, or CPU/GPU visual equivalence. **No real-world marker/screenshot exists in this session.** The same executable skips temporary CPU `DrawCommand`/`DrawPlan` construction for missing/stale/wrong-generation GPU output during generated candidate preparation, retaining unchanged CPU completeness and fallback. Active CPU graph traversal, hashing, admitted per-candidate command planning, CPU recovery and queue/fence-safe retirement remain. Net CPU/GPU costs, adoption and RX FPS/p95/p99 are **unmeasured**; all pilots stay default-off.

**Preserved negative evidence:** full #1067 / run 39286363394 failed in `compileJava` due a newly introduced local `generatedReplace` count colliding with the method's boolean parameter; no #1067 JAR, smoke-log artifact 12204160640, offline contracts passed but all native/Forge runtime tests were skipped. Exact rename in `0f76caf7e5377ce27edebbfc23235c5daaa0168d` restored compilation; #1068 subsequently passed. Keep #1060 (obsolete 512 assertion), #1064 (bounded-overflow shader regression), older #1054/#1050–#1052/#1047/#1048/#1041/#1042 and analyzed #1028 UUID `e05f2b31`. They must not be rebranded as passed.

**Next action:** obtain actual generated-world REPLACE and APPEND production-consumption witness and controlled main-world/Immersive Portals CPU-vs-GPU scene screenshots using `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`, or explicitly retain the external world/AMD hardware gap. Do not demand isolated hardware owner benchmark before integrated verification. After real-world geometry and portal proof, safely eliminate more repeated numeric CPU planning using a complete producer invalidation contract; preserve atomic APPEND fallback and actual-world particle portal/mod callbacks/translucency/lighting/camera/reload/frame-slot gates. Integrated release remains **incomplete**. Every following work session must refresh `docs/NEXT_SESSION_PROMPT.md` and finish with its own new copyable rolling handoff prompt perpetuating that requirement.


## Updated validation checkpoint: #1067 failed; #1068 pending (October 19)

Full [#1067 / run 39286363394](https://github.com/Vorith03/VulkanMod/actions/runs/39286363394) **FAILED before native/Forge runtime**: public offline contracts passed, but `compileJava` rejected the newly added local `int generatedReplace` because that publisher method already has a boolean `generatedReplace` parameter. This is a **Java source naming error, not a negative GPU shader/pixel/synchronization result**. Artifact **12204160640** is failed-run smoke logs; **no #1067 JAR exists**. The targeted, behavior-preserving rename landed in corrected executable `0f76caf7e5377ce27edebbfc23235c5daaa0168d`. Full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) was **queued/in progress** at this documentation checkpoint, with no passed result or verified JAR/smoke artifacts yet. Until full #1068 succeeds, the last qualified executable remains `360434e664c13050e2eab1174673ed19525ed813` in full #1066 (JAR 12196457613; logs 12196891834). **Next: check #1068 conclusion and exact artifacts before claiming qualification; fix any subsequent actual failure step.** All runtime generated-world/portal pixels and integrated profitability remain open.


## Actual-world generated indirect recording witness — full #1067 pending (October 19)

Executable `2f8a45397639d8a616db3b0e08adc915a08a50b8` was pushed after qualified #1066 (the earlier exact executable remains `360434e664c13050e2eab1174673ed19525ed813`; #1066 JAR 12196457613, logs 12196891834). Full #1067 / [run 39286363394](https://github.com/Vorith03/VulkanMod/actions/runs/39286363394) was **queued** when this note was written; do not treat it as passed until the live workflow and artifact identifiers are checked. The local runtime could not access GitHub/ForgeGradle dependencies, so no local Forge compilation was claimed.

Opt-in `-Dvulkanmod.debugGpuGeneratedWorldDraw=true` now emits `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` *only after* a live-client-world GPU-owned `vkCmdDrawIndexedIndirect` is recorded. It identifies producer-backed REPLACE, APPEND atomic CPU+GPU pairs and zero-exception APPEND separately, includes exact region/candidate generation and opt-in Immersive Portals world-render context, and rate-limits by mode/context. Startup/fixture metadata, CPU fallback and publication without a draw are rejected by a production-shared witness predicate exercised by RegionBatchSmokeTest. This is a **real-world diagnostic capability**, not an observed world run, GPU-selected-command readback or portal/terrain pixel equivalence. No real-world marker has yet been captured.

The same commit avoids constructing redundant CPU `DrawCommand`/`DrawPlan` objects for absent, invalid or wrong-generation GPU residency in mixed candidate preparation. It preserves the exact existing CPU completeness and fallback result; valid generated plans, active-slot traversal, hashes, serialized candidate records and synchronous shader/queue/lifetime checks are unchanged. This is a narrow CPU allocation reduction, **not** a measured CPU-time benefit or complete elimination of numeric planning.

**Next gate:** qualify #1067 fully; inspect artifact/logs, correct actual failures if any. Then obtain *actual-world* generated REPLACE + APPEND mode witness markers and controlled main-view/Immersive Portals reference screenshots under identical scene/camera settings. Compare visible geometry, partial/stale/recovery behavior and exact portal frustum context; do not infer pixel correctness from witness logs alone. If no such world evidence is available, leave the gate open and next test precisely stated. Keep all experiments default-off and preserve #1060/#1064 and older negative evidence. Follow `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`. Integrated FPS/p95/p99, eliminated CPU time and GPU dispatch/submission costs remain unmeasured.


## Generated atomic APPEND GPU indirect composition — full #1066 correctness-qualified

Executable `360434e664c13050e2eab1174673ed19525ed813`, full **#1066 / [run 39273496566](https://github.com/Vorith03/VulkanMod/actions/runs/39273496566)** **PASSED** (attempt 1), Forge JAR artifact **12196457613**, smoke logs **12196891834**. The workflow checked out the exact executable SHA; all public offline/build/package, both Forge startups, native indirect/terrain/particle/texture/screenshot/synchronization, post/depth, public Create Chronicles with Immersive Portals and Crash Assistant gates passed. The private-pack token was absent, so private resource packs were **not** exercised. Previous fully qualified executable `f9d581ba6a3a9136bd47166e04d5f26e4fda2995`, full #1059, JAR **12181441097**, smoke logs **12181540962**, remains retained as baseline.

New default-off `experimentalGpuGeneratedIndirectAppend` requires `experimentalGpuGeneratedIndirectReplace` plus the existing terrain handoff, GPU indirect commands/draw and CPU-bypass ownership switches. The producer-owned candidate-v1 record format (8 words) is retained with additive paired-row flags: APPEND CPU exceptions and the exact-generation GPU command appear as two adjacent candidate rows with identical section/provenance/ready/layer identity. The existing compute selector reserves two adjacent indexed-indirect slots in **one atomic operation**, emitting CPU exceptions first, GPU-generated quads second; ordinary and generated REPLACE single rows coexist. Candidate and output capacity is **1,024 commands** (512 region sections, at most two each), with bounded zero tail and no new renderer or readback. Individual section pairs are atomic; unrelated sections remain subject to existing GPU compaction order.

Active producer sections are re-evaluated each draw (ordinary producer revisions do not fully cover generated state). Fingerprints include generated ownership, generation and CPU completeness even when numeric fields coincide. Missing/stale/retired GPU residency, unready CPU exceptions, malformed pairing, incomplete provenance/queue/owner, exact frustum-volume changes, unsupported translucent/tripwire layers and capacity/resource failures reject GPU ownership. Rejected generated plans force a **fresh** lazy FrameBatch re-plan rather than trusting ordinary mesh revisions, preserving incomplete-pair withholding and complete CPU recovery. Compute-to-indirect barriers, same-graphics-queue ordering and frame-fence resource retirement are unchanged. No changes to flags' default-off settings.

Mandatory native `VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK` passed in #1066. Real Vulkan readback checked **509 ordered atomic CPU/GPU pairs plus ordinary and REPLACE singles** at 1,024-row input capacity, rejected malformed/unready pairs, exact five-word commands and a zero output tail. The production publisher smoke also covers pending/ready APPEND, mixed REPLACE, zero-exception collapse, candidate capacity, retirement, revocation/recovery and bypass of duplicate FrameBatch upload. Earlier negative CI: **#1060** failed a stale 512-limit layout-test assertion after expanding command capacity; **#1064** caught a shader regression that rejected valid bounded-overflow selection when input count exceeded output capacity. Both were corrected before #1066. Intermediate #1061–#1063 and #1065 were coalesced/cancelled, not qualified. Preserve earlier negative #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31`.

**Disposition: qualified but unmeasured.** This establishes bounded native metadata/command selection and Forge compatibility, **not generated-world rendering, actual portal raster/pixels, modpack adoption or measurable RX frame-time benefit**. Successful GPU owner skips duplicate FrameBatch serialization/upload only; CPU graph traversal, producer queue audit and per-candidate numeric planning/record allocation/hash still run. Rejected generated plans now force CPU re-planning, an unmeasured potential cost. Producer-side invalidation completeness, real generated-world/Immersive Portals culling, sustained churn and integrated GPU/CPU/submit/FPS/p95/p99 profitability remain open. Actual-world particle portal/mod callbacks, lighting/translucency/camera pixels, reload/re-entry and sustained descriptor/frame-slot pressure remain unresolved; no new independent particle defect was found. Do not request an isolated RX owner benchmark or repeat #1028. Integrated GPU-first release remains incomplete.

**Next:** after this full native qualification, establish real generated-world APPEND/REPLACE/ordinary *production-consumption and visual/portal correctness* with narrow, evidence-backed tests (not candidate/planner fixtures masquerading as pixels); then identify and eliminate genuinely redundant per-candidate CPU numeric planning in accepted GPU-owned work without changing graph, ownership or fallback contracts. Retain unresolved real-world particle gates and integrated adoption/cost work. Every session must update `docs/NEXT_SESSION_PROMPT.md` and end with a NEW copyable rolling handoff prompt which itself mandates the same behavior for every later session.


## Generated REPLACE indirect composition — full #1059 correctness-qualified

Executable `f9d581ba6a3a9136bd47166e04d5f26e4fda2995`, [full #1059 / run 39239916771](https://github.com/Vorith03/VulkanMod/actions/runs/39239916771): **PASSED**. Forge JAR artifact **12181441097**, smoke logs **12181540962**. All public offline/build/package/both Forge startups/native indirect, texture/particle/screenshot/synchronization, public Create Chronicles/Immersive Portals and Crash Assistant gates passed; private packs skipped. The indirect native log contains `VULKANMOD_GPU_GENERATED_REPLACE_CANDIDATE_OK`; checkout SHA was confirmed in the job log. Previous qualified executable was `29e95e5c31221276dae83d65363407f0c43f740c`, full #1058, JAR **12178863678**, logs **12178788575**. All 24 local offline contracts and temporary extracted production REPLACE-helper/existing-handoff-planner and publisher Java contracts passed. The publisher contract used modeled host dependencies and covered mixed ordinary/REPLACE wire rows, same-revision APPEND revocation/recovery, stale generation, queue and missing-owner rejection; it is not native rendering evidence. Local `agent-check.sh` could not download Gradle (`Network is unreachable`); this is not local Forge compilation.

New default-off `experimentalGpuGeneratedIndirectReplace`, together with terrain draw handoff, GPU indirect commands/draw and indirect CPU bypass, allows the existing candidate-v1 path to represent exact-generation **single-command REPLACE** alongside ordinary complete meshes. It reuses the established handoff planner and GPU selector, keeps graph/direct provenance, queue containment, exact culling volume and region ownership checks, and prepares auto-quad indices on GPU command ownership. No new wire ABI/shader or parallel renderer. Any active APPEND (even zero CPU exceptions), missing GPU half with incomplete CPU geometry, stale/retired residency, incomplete provenance, unavailable output or capacity failure retains the existing lazy FrameBatch/hybrid fallback. No partial APPEND is exposed.

Generated ownership/completeness does not yet have the ordinary candidate producer's full invalidation contract, so that opt-in combined path **re-evaluates active plans every draw** before fingerprint reuse. This adds CPU planning/record allocation/hash cost; ordinary cached paths retain their existing behavior. Accepted indirect ownership bypasses duplicate `FrameBatch.update/rebuild` and command upload, **not** Java graph traversal, per-candidate handoff planning, serialization or all CPU graphics calculations. Per-section numeric command planning now runs in candidate preparation; its computation was not eliminated. Classify CPU displacement as incomplete, not as all command construction removed. Ordinary-mesh live selection diagnostics are not used as a generated-ownership oracle.

The mandatory native probe `VULKANMOD_GPU_GENERATED_REPLACE_CANDIDATE_OK` checks the production candidate resolver/publisher, exact GPU numeric fields/section ID, pending/incomplete rejection, APPEND revocation without ordinary mesh invalidation, restoration, retirement and lazy FrameBatch bypass. Full #1059 passed this mandatory marker. It is bounded native-process fixture/metadata/control-flow evidence, not a real generated-world draw, portal pixel result, normal workload adoption, GPU/CPU cost or RX FPS/p95/p99 improvement.

Particle inspection found no new independently actionable defect in the available source/evidence. Actual Immersive Portals particle scenes, real mod callbacks/pixels, live reload/re-entry and sustained gameplay pressure remain unresolved; no artificial substitute was claimed. Work therefore advanced terrain under the owner's mandatory Priority B rule.

**Next:** with the bounded candidate/planner milestone qualified, extend the existing candidate/selector ABI to atomically represent CPU-exception + generated APPEND pairs with bounded capacity, generation/lifetime/queue/portal checks and lazy fallback. Add mixed ordinary/REPLACE/APPEND native consumption evidence and real-world qualification before promotion. Preserve #1054, #1050–#1052, #1047/#1048, #1041/#1042, and analyzed #1028 UUID `e05f2b31`. No per-slice owner benchmark; all pilots remain opt-in and the integrated release is incomplete. Every session must refresh the rolling handoff and repeat that requirement in its successor prompt.

## Particle descriptor frame-slot retirement — full #1058 correctness-qualified

Executable `29e95e5c31221276dae83d65363407f0c43f740c` (instrumentation `ebb5d320591a31c46700be4bb340b7cdc5444cda`); [full #1058 / run 39236211314](https://github.com/Vorith03/VulkanMod/actions/runs/39236211314) **PASSED**, Forge JAR artifact **12178863678**, smoke logs **12178788575**. #1057 was coalesced/cancelled by the newer executable, not failed native validation. The preceding **qualified** executable was `ba2f574e8db79ea26b7694d9cb37f31c8d5b2710` / full #1056, JAR 12178027221, smoke logs 12177772301. Native Vulkan screenshot smoke confirmed real-pool pressure, fence-slot retirement and renewed admission; build/package, offline contracts, both Forge startups, terrain/texture/particle pixels and synchronization, screenshots, Create Chronicles/Immersive Portals and Crash Assistant all passed; private packs were skipped. The CI particle lifetime log ended with `allocated_descriptors=64 pending_retirement_descriptors=63` after the **second** deliberate saturation, not production churn or a leak.

The opt-in GPU particle statistics now distinguish cached `live_descriptors`, all `allocated_descriptors` (including frame-retired reservations), and `pending_retirement_descriptors`. The native smoke exhausts the real host-enforced 64-descriptor pool with up to 80 distinct vertex buffers, checks the exact reservation increment and CPU-recovery path, frees temporary buffers through ordinary frame ops, advances submitted frame slots through their fences, and requires the same pool to accept a second pressure test at least as effectively. Device-idle waits are fixture-only; no production wait, bigger pool, or new eligible class. This strengthens resource lifecycle verification and pressure attribution, not normal-path GPU/CPU work displacement.

**Open:** real Immersive Portals particle rendering (CI tests loading/shaders/clipping but no real portal-world particle pixels), mod-injected callbacks, translucent/lighting/camera equivalence, actual reload/re-entry and sustained frame pressure, and RX adoption, compute/submission cost, FPS/p95/p99. With #1058 qualified, continue the actual-portal particle gate then generated terrain REPLACE/atomic APPEND candidate ABI with GPU indirect consumption. Retain negative #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31`. All experiments opt-in; release incomplete.


## Particle post-callback CPU recovery — full #1056 correctness-qualified

Full [#1056 / run 39232013086](https://github.com/Vorith03/VulkanMod/actions/runs/39232013086) **PASSED** for executable `ba2f574e8db79ea26b7694d9cb37f31c8d5b2710`, Forge JAR artifact **12178027221** and smoke logs **12177772301**. Public offline contracts, Forge distributable/both startups, native texture/particle/terrain pixels and synchronization, screenshot and Create Chronicles/Immersive Portals/Crash Assistant checks passed; private packs were skipped.

The opt-in exact-campfire GPU billboard adapter now rechecks the builder contract **after** `getQuadSize`, UV and `getLightColor` callbacks. A mod callback can change sorting/format/consumer admission after the first check. Instead of throwing when packing into invalidated state, the adapter emits the original four CPU vertices from already evaluated camera/quaternion/size/UV/light inputs, cancels inherited rendering and does **not** invoke callbacks again. This is exceptional fail-closed CPU work; normal accepted GPU quads still omit CPU vertex expansion. A new lifetime `callback_state_cpu_recoveries` counter is separate from `original_cpu_eligible_calls`.

The native smoke used the transformed inherited method and real builder; a probe enabled sorting inside `getLightColor` after an earlier compact GPU quad. It verified prior packed-quad materialization, captured CPU recovery for the current quad, later original CPU quads, no pending compact records, callback-once, exact color/light and geometry/UV within 2e-6. The CI particle smoke marker logged `callback_state_cpu_recoveries=3` across three scripted passes; this is **fixture traffic, not production adoption**. All public checks passed, but the oracle is not a real Immersive Portals/mod-injected callback or resource-reload/translucency-world pixel comparison. Frame-slot profitability and representative RX CPU/GPU/FPS/p95/p99 remain **unmeasured**.

**Next:** advance actual-world particle portal, lighting, translucency, resource reload and frame-slot/descriptor gates, then generated REPLACE/atomic APPEND candidate ABI for GPU indirect. Keep all pilots opt-in; preserve negative #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31`. Integrated GPU-first release remains incomplete.


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

Mesh uploads, staged commits, resets and deferred parameter updates now publish immutable candidate numeric geometry. Opt-in vertex Segment readiness notifications advance only the current owned area/layer revision; segment replacement detaches retired listeners. Changed graph traversals consume the snapshots, and pending candidates remain cached until a producer event instead of polling/rebuilding every draw. Pre-frustum graph/direct provenance, queue proof, ownership, exact portal volumes, GPU generations, CPU fallback and REPLACE/APPEND exclusion remain. Active-slot serialization/hash and Java graph traversal still occur.

All 24 offline contracts and an extracted production DrawParameters/Segment lifecycle contract passed locally. Full native CI passed the mandatory new `VULKANMOD_GPU_CANDIDATE_GEOMETRY_PRODUCER_OK` marker, including staged listener detachment coverage. See the terrain handoff contract for producer mutation requirements. No local Forge/native runtime is claimed; remote CI supplies that qualification. Snapshot allocation/producer callbacks and net RX CPU/GPU/FPS/p95/p99 effects are unmeasured; all pilots remain opt-in.

Next: genuine multi-sprite texture dispatch and shared descriptor/scratch ownership, then actual-world particle portal/mod/pixel/reload gates and generated REPLACE/atomic APPEND candidate ABI. Integrated release remains incomplete. Preserve #1041/#1042 descriptor negative evidence and analyzed #1028 UUID `e05f2b31`; no isolated owner benchmark. Rolling prompt updated in `docs/NEXT_SESSION_PROMPT.md` and must be refreshed again by the next session.

## Producer-owned GPU candidate queue containment — full #1045 qualified

**Executable:** `cb248683db075033f0bb28ad42e8882ebba3f6d4`, full **#1045** / [run 39201976864](https://github.com/Vorith03/VulkanMod/actions/runs/39201976864) **PASSED**; distributable Forge JAR artifact **12164426976**, smoke logs **12164093569**. Public offline contracts, Forge build/package/both startups, native GPU terrain/indirect and texture/particle pixels/synchronization, Create Chronicles/Immersive Portals and Crash Assistant gates passed; private pack fixtures skipped.

Production change: for opt-in GPU candidate provenance, `ChunkArea.addSection` now audits queued identity/owned-slot membership, recorded pre-frustum graph or direct-seed membership, and duplicate slot rejection as the traversal producer writes the CPU queue. An optionally tracked `ResettableQueue.contentRevision` detects bypassing `addSection/resetQueue`; registration replacing a queued owned identity revokes the producer proof. `RegionDrawBatch.publishLiveCandidates` checks this O(1) state **before cached GPU command reuse**, removing the previous O(CPU queued sections) per-changed-traversal containment scan. Its remaining producer-active slot walk still validates current owned identity, mesh parameters, upload readiness, graph/direct flags, and post-enqueue direct seed changes. Any incomplete/mutated proof invalidates GPU candidate reuse and lazily falls back to CPU commands. Native regression exercises direct seeds, off-frustum graph stamps, stale replacement, duplicate slot rejection, independent queue mutation and successful fresh traversal recovery. Default CPU-only queues retain revision tracking disabled.

**Disposition: correctness-qualified but performance unmeasured.** This eliminates CPU draw-time containment rework on the experimental GPU candidate path but does **not** eliminate CPU graph traversal, producer enqueue bookkeeping, active candidate slot parameter reads, or all CPU command fallback. It is not an RX FPS/p95/p99 result, proof of portal pixels, or authorization to enable by default. Particle actual portal callbacks/pixels/reload and frame pressure; texture multi-sprite descriptor/scratch compute; producer per-slot metadata/readiness; generated REPLACE/atomic APPEND candidate ABI and integrated RX adoption/net profitability are still open.


## GPU particle descriptor bound qualified — full #1044

**Qualified executable:** `71f1bec858a9c2dbb589d218b172eec624c0dd29`, full **#1044**, [run 39195687691](https://github.com/Vorith03/VulkanMod/actions/runs/39195687691) **PASSED**. Forge JAR artifact **12161603960**, smoke logs **12161404876**. Offline contracts, Forge package and both startups, native Vulkan terrain/indirect plus texture/particle pixel/sync, screenshot, public Create Chronicles with Immersive Portals and Crash Assistant all passed; private packs skipped.

**Production correction:** `6817c071` enforces the 64-descriptor pool reservation on the host, including allocated sets pending frame-fence retirement; it refuses excess GPU work before native allocation and preserves CPU quad materialization. `71f1bec8` isolates an 80-buffer native descriptor-pressure oracle into its own recording frame after three existing exact-geometry passes; also exercises owner teardown/reinit after explicit device idle. The preceding negative evidence is important: #1041 (39194476949) failed the fixture's assumption of native pool rejection; #1042 (39194915798) demonstrated Lavapipe admitted **82 live sets against maxSets=64**. #1043 was superseded by the isolated-frame test before full qualification. The final assertion and all public native/Forge gates are now green.

**Scope:** CPU simulation/light/callbacks and exact campfire eligibility are unchanged; this is a fail-closed resource-safety correction, not a new GPU work-ownership transfer or an observed RX speedup. The native oracle is NOT an actual resource-pack reload or production portal-translucency pixel comparison. Integrated GPU-first correctness/adoption/CPU displacement/added GPU time/FPS/p95/p99, generated REPLACE/APPEND ABI and multi-sprite texture compute remain open. All pilots remain opt-in; do not request a new per-milestone RX benchmark.

## Sparse producer GPU candidate input — #1040 qualified

Full #1040 [run 39192146854](https://github.com/Vorith03/VulkanMod/actions/runs/39192146854) **PASSED** at executable `1b182d53a18c0b83c5b4c1c52f94c08987ad5a1c`, Forge JAR artifact **12160493015**, smoke logs **12160477999**. Public offline/native Vulkan indirect/terrain, texture/particle pixel/sync, Forge package/startups, Create Chronicles and Crash Assistant succeeded; private pack fixtures skipped.

Opt-in candidate v1 now serializes only pre-frustum graph-visited and direct-seeded section slots on changed traversals, not every owned slot. The CPU queue is audited for coverage; stale/incomplete sets retain CPU fallback. Active slots retain readiness/mesh/graph flags, increasing packed-slot order, generation and exact camera/portal-volume checks; GPU hybrid REPLACE/atomic APPEND remains unsupported. The Builder allocates 32 initial records and grows to the 512 bound. Lifetime `gpuCandidateLifetime` profiler/F3 counters cover draw/traversal reuse, sparse rebuild/attempt, prepared/published slots, producer mark attempts and timed publisher preparation; marks include graph/direct attempts, and `prepMs` excludes producer stamps and GPU submission. CPU graph traversal and active-slot parameter reads remain. There is **no measured RX net CPU/GPU/FPS/p95/p99 gain**, and no default or GPU-first release promotion.

Next: qualify particle portal/mod/pixel/reload/frame pressure; advance shared multi-sprite texture compute and per-slot producer metadata; version the candidate ABI for REPLACE/atomic APPEND. Keep experiments opt-in. `docs/NEXT_SESSION_PROMPT.md` carries the mandatory rolling handoff.


## Producer-side traversal provenance reuse — full #1039 qualified

Latest qualified executable **e78a7641dffc86a62efaad35066e4733e1d9f702**,
full **#1039**, [run 39184804795](https://github.com/Vorith03/VulkanMod/actions/runs/39184804795)
**passed**. Distributable JAR artifact **12157573613**, smoke logs **12157802321**.
Build/package, both Forge starts, native persistent indirect/region smoke, GPU
terrain, exact texture/particle/pixel/synchronization and public Create Chronicles
and Crash Assistant gates passed. Private packs skipped.

The production GPU region candidate path now records **producer** graph-visitation
bits at RenderSection.setLastFrame (including nodes culled *after* the graph
stamp) and direct-seed provenance when ChunkArea enqueues a section. Two bounded
512-bit sets per area are enabled only for the existing GPU candidate experiment.
An independent ownership revision and per-layer provenance snapshots allow an
equivalent traversal rewrite to reuse the already published GPU candidate input
**without the 512-slot scan, hash or Builder**, even though the input revision
changed. Mesh, queue visibility, owner identity, pending readiness and exact
camera/portal culling volume must still match. Changed provenance takes the
existing authoritative scan/fingerprint/upload/generation/fallback path.
Native #1039 oracle covers equivalent rewrites, off-frustum graph stamps,
direct-seed changes, ownership, readiness and volume rejection.

This is a narrower producer-side incremental fast path, **not** elimination of
Java graph traversal or changed-input candidate scanning. Actual cache adoption,
extra per-visited-node bitset cost, net CPU time, GPU cost, FPS and p95/p99 remain
**unmeasured**. Existing GPU and texture/particle opt-in flags are unchanged;
no default promotion or integrated GPU-first release completion is authorized.

Next: add *decision-useful* producer fast-path coverage/cost counts within the
existing instrumentation when preparing an integrated RX test; qualify portal/
mod-injection particle pixels and reload/frame-slot pressure; progress multi-sprite
texture compute and REPLACE/atomic APPEND-aware candidate ABI. Keep the old #1028
capture, do not request isolated owner re-runs. Rolling prompt updated at
`docs/NEXT_SESSION_PROMPT.md`; the following session must update it again.

## Terrain candidate unchanged-draw reuse — full #1038 qualified

Latest qualified executable **246ff243fc59e8e9667b299ceb3e846a04b57497**,
full **#1038** / [run 39180288680](https://github.com/Vorith03/VulkanMod/actions/runs/39180288680),
**passed**: offline/Forge JAR and both startups, native region command ownership
(including new candidate-cache invalidation assertions), GPU terrain selection,
texture/particle/pixel/sync, public Create Chronicles and Crash Assistant gates.
Private resource packs skipped. JAR artifact **12155952145**.
No new RX adoption/net CPU saving/GPU cost/FPS/p95/p99 result is measured.

Implementation `246ff243` skips the 512-slot builder/owned-section scan/hash
on unchanged production draws after a published candidate snapshot. Dedicated
`ChunkArea` input revision advances on each graph traversal (including marked
off-frustum nodes not represented by queue membership), owned-section changes,
stale slot discovery and ring resets. Per-layer mesh/visibility revisions,
pending buffer readiness and exact current-volume checks invalidate the fast path.
First visit/changed traversal still uses authoritative full scan/fingerprint,
failed upload still retries, and CPU fallback/generation/APPEND exclusion remain.
This removes repeated host metadata preparation on steady draws, not traversal
or all CPU graphics work. Cache-hit adoption and profitability are unmeasured.

Particle final storage-range correction `6a78cfb8` passed full **#1037**
[run 39179150695](https://github.com/Vorith03/VulkanMod/actions/runs/39179150695)
on attempt 2, JAR **12156240273**. Attempt 1 failed Forge startup
`FMLConfig.ConfigValue` null Boolean in indirect smoke before particle assertions;
rerun of identical executable completed all public native/Forge gates. The
failed attempt is negative CI evidence, not a proven particle defect.
#1036 earlier passed the particle geometry/uploader/synchronization pilot.

Next: portal/mod/pixel particle semantics and reload/frame-slot pressure;
incremental region candidate production beyond the safe unchanged-draw cache;
multi-sprite texture execution; REPLACE/atomic APPEND-aware candidate ABI and
integrated adoption/cost evidence. Keep gates opt-in; do not request unchanged
#1028 baseline or isolated user hardware tests. Updated rolling handoff is
`docs/NEXT_SESSION_PROMPT.md` and explicitly requires another prompt.

## Particle billboard continuation — native/Forge milestone qualified

Owner requested a coherent handoff and a next-session prompt which itself requires
another handoff prompt. `docs/NEXT_SESSION_PROMPT.md` carries that rolling requirement.

Implemented `c225399f`, corrected counters/mapping `0b6ba02e`, shader fixture
initialization `761947c10e5a4f2f5c1f0ce13f497bacb5f3a10b`. **Full #1036 passed**
(run 39178618231, JAR artifact 12155143862, logs 12155024238): offline, Forge
package, both startups, terrain indirect, post/depth, screenshot geometry/sync,
public Create Chronicles and Crash Assistant. Private packs skipped.

Opt-in `experimentalGpuParticleBillboards`, exact campfire class only, normal
unsorted Tesselator particle buffer. Inherited CPU four-vector/quad expansion is
canceled; one compute job per original draw writes original ordered slots before
the existing graphics shader. Simulation, camera/roll, light/UV/size callbacks stay
Java. Other classes/custom overrides retain CPU rendering; no ParticleEngine
redirect. CPU sorting and unavailable post-pack compute materialize current CPU
vertices. Frame buffer generations cache descriptors and retire with existing fences.
See `docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md` for bounds and remaining qualification.

Native oracle compared 524 vertices (87 GPU / 44 CPU quads) per frame over three
frames: positions/UV within 2e-6, packed color/light exact, callbacks once, nonunit
rotation/roll, real uploader, sorting/recovery, growth/retirement. Validation probes
also dispatch directly; their lifetime totals are NOT workload adoption ratios.
CI host preparation/submission was 18.330 ms for six compute batches, not RX or
steady-state performance. Real GPU costs / CPU savings / FPS / tails unmeasured.

#1034 was superseded. #1035 (39165513611) passed build and early native gates but
failed fixture shader initialization before geometry comparison; #1036 fixed it.
Final review corrected unsigned uint32 storage-limit admission and bounds descriptor
views despite oversized unused buffer-growth tails; identical executable passed
full #1037 attempt 2 (attempt 1 Forge FMLConfig startup failure). Do not promote
defaults or request unchanged RX tests.

All 24 local offline contracts passed. The javac launcher is absent but jdk.compiler
is installed: a temporary wrapper can invoke `java com.sun.tools.javac.Main "$@"`.
Gradle distribution network access remains unavailable. Temporary wrappers may be
lost across interruptions; recreate them instead of repeating Gradle fetches.

Next: finish final guard qualification; then particle portal/mod-injection/pixel and
reload/pressure checks, incremental terrain candidate input and remaining texture/
mesher composition gates. The multi-subsystem release remains incomplete.

## GPU-first ownership release ACTIVE — 2026-10-18

Owner explicitly superseded the previous queue-size/particle-buffer sequence.
Recovery matched `3edd41b7` / executable `50f39eb5`, full #1028 green (39143814563).
The supplied #1028 UUID `e05f2b31` was read: GPU interpolation **106,483/2,841,737
(3.747%)**, CPU bulk 2,735,254, disabled residency 2,719,880, lifetime capacity
rejections 1,246 at the fixed 128 MiB cap. Texture 16.687 ms/tick; separate
main graphics GPU 2.865 ms/frame, upload GPU 2.913 ms/batch. Historical
queue/cap results stay preserved below; **do not ask for another #1028 capture**.

Implemented opt-in `experimentalGpuAnimationAdmission`: register actual standard
animation source/schedule inventory, rank expected avoided interpolation pixels
per source+scratch byte before the outer tick; keep live GPU sources pinned and
retirement charged. Deferred candidates neither allocate nor permanently disable.
Selected interpolation can initialize residency on first interior tick, then
existing O4 cancels CPU pixels. Custom/clock/mutation/lifetime fallback retained.
CPU policy and native transformed ticker pressure/oracle regressions added.

Second texture slice implemented: `experimentalGpuAnimationBatch` uploads a
charged immutable mip table into the existing source lease, dispatches all mips
once per sprite, and queues compute output alongside O3/CPU transfers. Only a
pending read of the exact scratch buffer forces a reuse drain. Source/atlas
ordering, scratch barriers and exact pixel kernel remain; per-sprite descriptors
and source groups are still retained (multi-sprite compute batching remains open).
Native/production-method regressions cover overlap and pending scratch reuse.

**Texture milestone qualified:** full #1031 passed at `1e32e359` (39149394473),
artifact 12144887049. Offline, Forge package/startup, native exact animation pixels/
sync including admission and batch, public Create Chronicles and Crash Assistant
all green; private packs skipped. RX adoption/net benefit unmeasured.

Third slice native/Forge-qualified in full #1032 at `03f730a5` (39150002619): `experimentalGpuIndirectCpuBypass`
requires existing GPU indirect commands/draw flags and no terrain mesh handoff.
Consumes current complete bounded GPU commands without constructing/uploading CPU
FrameBatch; lazily restores current CPU commands on rejection. Candidate ownership
and exact culling volume checks preserve missing-section/portal/projection safety;
APPEND stays on the existing atomic planner. Native cache tests cover dirty GPU
intervals and fallback. Renderer-update slot/fallback counters, no readbacks.

Historical #1029 passed admission policy but an extracted tick
fixture lacked its new dependency; #1030 found the corresponding attribution
fixture gap. Both fixed, negative evidence preserved in the ownership doc.
**Final executable checkpoint:** `0e6f3657de3c8193df529954df0bca019a058045`,
full **#1033** / run **39150537659**, passed; JAR artifact **12145046866**. Both native startup/command cache
oracles (including pending APPEND rejection), exact GPU selection/zero-tail,
texture pixels/synchronization, Forge packaging, public Create Chronicles and
Crash Assistant passed; real private packs skipped. This includes the composition
guard rejecting v1 indirect selection during terrain handoff. Local JRE lacks
javac and Gradle download is unreachable; remote CI supplies qualification.

No measured coverage/net benefit for the new pilot,
no default promotion or integrated release qualification. See
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md` and new active roadmap sequence.
Next: particle billboard preparation with exact ordering/Forge/IP fallback,
further multi-sprite texture execution, and generation-safe incremental terrain
candidate input. The current candidate fingerprint still scans/allocates owned
region metadata per draw; this host cost is not displaced by command shadow
bypass. CPU graph traversal, readiness and ownership must invalidate retained GPU
inputs correctly. Extend candidate ABI before combining mesher REPLACE/APPEND with
GPU indirect ownership. See the precise remaining gates in the ownership doc; do not declare the multi-subsystem mission
complete after these milestones. No new owner test needed at this boundary.


## #1028 GPU texture copy queue bound — full CI qualified (2026-10-18)

After owner #1026 diagnostic disproved native particle resize growth in the measured render and invalidated its zero capacity-check counter, #1027 removed the probe (full Forge/native CI passed, run 39143353962 at executable `df07fc7b`). The independent #1026 texture profiler shows 114,177 flushes caused by the unified queue's 128-region cap and 8.289 ms/tick inclusive copy flush cost. A **pilot-only 128→256** capacity experiment changes only that integer bound and sizes its existing fixed scratch arrays. The legacy 32-region queue, source ordering/overlap safety, staging budget, barriers, GPU compute and custom pixel generation remain unchanged. The real production-method contract and native Vulkan pixel/synchronization smoke now exercise 256 capacity and 288 writes with overwrite/retirement. Full [#1028 Forge/native CI](https://github.com/Vorith03/VulkanMod/actions/runs/39143814563) **passed** on qualified executable `50f39eb50dda7e71ac0b726495183218e70b69f7` (JAR artifact `12142713171`). Offline production-method contracts, Forge package, two Vulkan startups, indirect, post/depth, screenshot pixel/sync with the 288-column native unified-copy test enabled, public Create Chronicles and Crash Assistant all passed; optional real resource packs skipped. **Disposition: correctness-qualified, RX speedup unmeasured.** A larger queue may increase per-epoch overlap scan CPU. The #1028 owner capture has now been received/read in the active checkpoint above; do not repeat it. See `docs/TEXTURE_COPY_REGION_BATCH_CAP_1028_2026-10-18.md`.

## Preserved evidence and outstanding hardware gates

Historical checkpoint detail remains in Git at `3edd41b7` and the focused RX docs/
optimization ledger. #1026 true particle native growth count was zero; its
capacity-check counter was invalid and was removed in qualified #1027. #1025
buffer screening was unfavorable, not causal proof. #1024 frustum was activated
with favorable unpaired evidence; default remains gated. #1023 CPU bulk has one
conditionally matched OFF/ON pair under the owner's restored-world assumption;
two further pairs and visual/lifecycle/default adoption remain open, but owner
selected GPU-first development rather than repeating them now. No unchanged
baselines are requested. Phase 5 OpenGL/Vulkan matched performance gates remain
open. GPU texture reload/re-entry and terrain REPLACE+APPEND portal/dirty mixed-
section owner observations remain deferred. Private resource-pack CI skipping
is not real-pack coverage. Existing Forge compatibility boundaries remain.
