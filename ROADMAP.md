# VulkanMod Forge 1.20.1 — Canonical Roadmap

## October 20 — integrated implementation ready for real-world qualification

**Internal readiness complete:** exact executable
`9a6de8480c7ab46ccae7cdc06a652c108daa8078`, full #1082 / run 39551989165
SUCCESS attempt 1. JAR 12324685470, logs 12323799769; private packs skipped.
Forge/package, baseline and combined GPU startups, native mesher/lighting/
publication/selector, 509-pair APPEND, generated consumer/texture/particle pixels,
synchronization/post/depth, combined public Create Chronicles/IP and Crash
Assistant all passed. Downloaded 11,074,747-byte JAR manifest/version inspected;
SHA-256 c97997f24572fce48abbe739659b3b69808bd5f24d5e61d9ffbf28dc30f1e64a.

A new actual RegionDrawBatch.draw pixel fixture exposed a production collision:
valid wrapping short traversal -1 was mistaken for absent graph proof. Explicit
validity now preserves the full stamp domain without changing CPU traversal,
barriers/fences or fallback. Native domain/reset/clear and actual -1 REPLACE/
APPEND/zero-exception/fallback/re-adoption pixels pass. #1080 failed the pixel
oracle on eb6989a3; #1081 failed new-test private access before native validation;
#1079 was cancelled. These are not qualified runs.

The single current completion checklist and precise retained CPU semantics are at
the top of docs/GPU_FIRST_OWNERSHIP_2026-10-18.md (repository-root-relative).
Accepted textures/particles/terrain/indirect output feeds production and bypasses
its documented CPU counterpart; clocks/callbacks/world/light/graph input,
changed metadata, unsupported semantics and complete recovery remain CPU-owned.
This session's production correction is correctness, not extra measured offload.

**Next:** the integrated package in docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md.
Actual loaded-world/portal/private-pack/particle gameplay behavior and RX adoption,
net CPU/GPU costs/FPS/p95/p99 remain open. Phase 7 actual-world/default-promotion
and release gates are not closed by internal readiness. Disposition: **qualified
but unmeasured**. No further independently evidenced in-scope prerequisite remains;
advance to owner evidence without unrelated churn. Historical entries below retain
their original boundaries.

## October 20 maintenance implementation detour

The owner selected implementation of both reuse/maintainability reviews. Current
scope, dispositions and qualification are in [the tracker](docs/MAINTENANCE_TRACKER_2026-10-20.md)
and [AGENT_STATUS.md](AGENT_STATUS.md). Milestones `a15ce968`, `0b50f449`, `27a14fca`
remove obsolete infrastructure and clarify native/cache/ABI/admission ownership.
Full #1077 / run 39508596394 PASSED on correction `c303cf26`;
JAR 12305230721, logs 12305300783; private packs skipped. This qualified the earlier
maintenance milestones, replacing `682b0d6b` / #1074. #1076 remains a
compile failure; #1075 was cancelled. Follow-up `6c65e00f` fixes stale mat3
partial writes and adds behavioral compute failure/cache transition coverage;
full #1078 / run 39509869276 PASSED on that exact executable (JAR 12304984467,
logs 12306121182; private packs skipped). `6c65e00f` is the current qualified
maintenance executable. The bounded assignment is complete; retained/deferred/
blocked migrations stand. Resume the available Phase 7 gate; see the tracker.
No FPS result or closure of actual-world/portal, private-pack, particle or RX
profitability gates is claimed.
Historical checkpoints below retain their original evidence boundaries.


## October 19 — reject stale queued REPLACE publication; full #1073 PASSED

**Latest qualified executable:** `85037cfdb2779294676f33ba3b951791d66dec10`; [full #1073 / run 39304235826](https://github.com/Vorith03/VulkanMod/actions/runs/39304235826) **COMPLETED / SUCCESS, attempt 1** on that executable. Forge distributable JAR artifact **12213616043**; smoke logs **12213091579**. Offline contracts, Forge compile/package, both client startup modes (including the production-queue publication smoke), native indirect/terrain/texture/particle/screenshot/synchronization/post/depth, public Create Chronicles/Immersive Portals and Crash Assistant gates passed. Private resource packs were not exercised.

**Production correctness fix:** `TaskDispatcher.scheduleSectionUpdate` previously rejected stale section generations only for staged APPEND (`stagePreservedLayer`). A completed ordinary REPLACE build could remain in the publication queue while `RenderSection.setDirty` advanced its voxel generation, without its already-queued result yet being cancelled. The accepted old upload could overwrite CPU draw parameters and compiled/visibility state ahead of the next build. All **nonnegative generation-bearing** queued section updates now reject a mismatched live generation before touching geometry or invoking the completion callback; legacy unversioned (negative-generation) consumers remain unchanged. The existing publication drain smoke now exercises the actual completed-result queue: stale REPLACE rejection, current-generation REPLACE acceptance and unversioned compatibility. Both client startup smoke modes passed on the exact executable.

**Audit boundary:** This is a bounded async-publication correctness guard, **not** elimination of CPU planning or a measured FPS improvement. The separate GPU mesher dispatch/completion paths were inspected for section/area/generation/preflight validation; no new defect was established there and their locks, queue ordering, GPU residency/retirement and atomic APPEND transaction were not modified. It is not exhaustive proof of all producer or async transitions. The real loaded-world REPLACE/APPEND consumption marker, nonzero post-cull selection, matched main-world and genuine Immersive Portals GPU/CPU pixels, RX 6900 XT adoption, displaced CPU milliseconds, added GPU costs and FPS/p95/p99 remain **unmeasured/unavailable**. Preserve all pilots default-off, 512-section/1,024-command limits, exact generation/queue/provenance/frustum, APPEND CPU exceptions, same-queue barriers, fence-safe retirement and complete CPU recovery.

**Earlier evidence stays distinct:** #1072 passed (executable `47f00dff1c8349133948dc0dd257d5771ac8217a`, JAR **12207901585**, logs **12207722559**); #1071 was cancelled; #1070 passed only on attempt 2 after attempt-1 FMLConfig null-Boolean startup failure (successful JAR **12206524656**, logs **12208005267**, failed logs **12206393088**). Preserve #1067, #1060, #1064 and older negatives including analyzed #1028 UUID `e05f2b31`. Full #1073 does not establish private-pack or user-world gates.

**Next gate:** Obtain a real generated-world witness and matched CPU/GPU main-world and genuine portal screenshots per `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`. A recorded command is not proof of selected nonzero GPU commands or correct pixels. If private worlds/RX client remain unavailable, retain that blocker and continue independently safe ownership/invalidation/readiness/fallback work only when a specific evidenced hole is found; avoid repeating settled audits or requesting isolated benchmarks. Update this repository handoff and finish each session with a **NEW complete copyable rolling handoff prompt instructing its own successor to do the same perpetually**.


## October 19 — cached producer proof revocation; full #1072 PASSED

**Qualified executable:** `47f00dff1c8349133948dc0dd257d5771ac8217a` (implementation `5308522c67c4310c4bd20b3cb0a590c4aff2834b`), [full #1072 / run 39295126051](https://github.com/Vorith03/VulkanMod/actions/runs/39295126051) **COMPLETED / SUCCESS, attempt 1**. Forge JAR artifact **12207901585**, smoke-log artifact **12207722559**. Offline contracts, Forge packaging/both startups, native indirect/cache/terrain/texture/particle/screenshot/synchronization, post/depth, public Create Chronicles/Immersive Portals and Crash Assistant passed. Private-pack steps were skipped. #1071 / run 39295004950 was **cancelled**, superseded by strengthening the smoke to require a populated cache; it is not qualified.

**Verified behavior:** changing a queued section's direct-seed status in either direction now revokes its area's producer queue proof; transferring a section to another area notifies the departing owner and advances its ownership/input revisions. The next candidate publication clears the cached table and denies GPU ownership until a fresh traversal proves the queue again. Stable direction status and same-area assignment preserve the proof. The native smoke starts with a nonempty initialized generated-mode table, verifies both direction revocations, recovery and departing/destination independence. This closes bypasses of per-slot direction/identity checks by cache fast paths; it adds no new CPU-displacement mechanism or measured speedup. Local 24 offline contracts and an extracted-production-method direction/owner check passed. Local `agent-check.sh` could not download Gradle (`Network is unreachable`); full CI supplies Forge compilation/runtime qualification.

**Preserved intervening evidence:** previous `a93d5472fdebd3576b80b8e21ef62bddccb65500`, full #1070 / run 39293185968 **PASSED ON ATTEMPT 2**; JAR **12206524656**, successful logs **12208005267**. Attempt 1 failed screenshot-client startup in `FMLConfig.getBoolConfigValue -> ImmediateWindowHandler.setupMinecraftWindow` with a null Boolean before pixel assertions (failed logs **12206393088**); same executable passed on one bounded retry. Its residency fingerprint fix additionally keys representability/GPU vertex bound so identical numeric CPU geometry cannot retain retired generated command state. Keep the failed attempt, cancelled #1071, #1067/#1060/#1064 and older documented negatives distinct from successful qualification.

**Next gate / limits:** no actual-world generated APPEND/REPLACE witness, matched main-world/genuine-portal CPU/GPU screenshots, representative RX adoption, eliminated CPU milliseconds, added GPU/dispatch/submission costs or FPS/p95/p99 benefit. Private packs and outstanding real particle portal/callback/lighting/translucency/camera/reload/frame-pressure gates remain unavailable/unqualified. All experiments remain default-off; bounds, atomic APPEND, exact provenance/frustum, queue synchronization, fence retirement and complete CPU fallback are unchanged. Follow the actual-world witness contract when evidence is available; otherwise explicitly retain the blocker and audit newly evidenced producer transitions before extending reuse. Do not repeat settled #1028 UUID `e05f2b31` tests. Every successor must update the repository handoff and finish with its own NEW copyable rolling handoff perpetuating that requirement.


## October 19 — producer-invalidation generated candidate reuse; full #1069 PASSED

**Qualified executable:** `41ba122c03509fbbe7d19b67d60dda48735a6723`; [full #1069 / run 39291766470](https://github.com/Vorith03/VulkanMod/actions/runs/39291766470) **COMPLETED / SUCCESS** on that exact executable. Forge distributable JAR artifact **12205981816**, smoke logs **12206581647**. Public offline/Forge package, native generated APPEND/REPLACE and indirect, terrain/particle/texture/screenshot/synchronization, both startup modes, post/depth, Create Chronicles with public Immersive Portals fixtures, and Crash Assistant gates completed successfully. Private resource packs were not exercised. Prior full #1068 executable `0f76caf7e5377ce27edebbfc23235c5daaa0168d` passed (JAR 12203627510; smoke 12202694551).

**Implemented:** `ChunkArea` now exposes a producer-maintained generated-state revision. Exact owned `RenderSection` transitions (CPU-complete flag, staged preflight/ownership, visible handoff, atomic APPEND commit, invalidation and complete CPU recovery) advance it. Existing `DrawBuffers` mesh and immutable candidate-geometry revisions cover GPU residency publication/retirement, CPU mesh parameters and readiness. `RegionDrawBatch.publishLiveCandidates` can reuse a same-mode generated candidate table when producer revision, owned-slot queue audit, mesh/geometry, graph/direct provenance, visibility and **exact culling volume** match; unchanged draws/traversals no longer construct/hash every active generated candidate. Changed ownership/readiness requires fresh per-candidate planning, and mid-build generated-revision changes fail closed. A mode discriminator blocks cross-mode cache reuse. `RegionBatchSmokeTest` verifies unchanged REPLACE bypasses the per-slot loop and APPEND-preflight transition rescans and revokes complete ownership. Existing native APPEND capacity/atomicity and CPU fallback remain unchanged.

**Evidence boundary:** The qualified suite establishes compilation/fixtures and public compatibility, **not** main-world generated command selection, raster pixels, a real Immersive Portals view, long churn correctness on the user's world, actual eliminated CPU milliseconds, added GPU/submission work or RX 6900 XT FPS/p95/p99 profitability. No actual-world witness marker or matched CPU/GPU screenshots were captured. Graph/mod traversal, producer queue audit, changed-snapshot numeric planning, legitimate CPU exceptions and incomplete-output recovery stay CPU-owned. All experiments remain default-off; 512-section/1,024-command limits, APPEND atomicity, generation/provenance, portal frusta, same-queue ordering and fence-safe retirement are unchanged.

**Preserved negatives:** #1067 failed `compileJava` before native testing (name collision; corrected before passed #1068); #1060 stale 512 limit and #1064 bounded-overflow shader regression were corrected before #1066. Earlier #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31` remain negative or limited as previously documented. Do not convert cancelled or failed runs to success.

**Next gate:** Use `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md` for *actual loaded-world* REPLACE/APPEND command-recording witness and matched CPU/GPU main-world plus genuine Immersive Portals screenshots. A log marker does not prove post-culling nonzero commands or correct pixels. If this external user-world/AMD evidence is unavailable, explicitly leave it pending and continue only demonstrably safe implementation. Audit producer revision completeness on newly discovered asynchronous transitions rather than assuming this native fixture proves all worlds. Preserve outstanding particle portal/mod callback, lighting/translucency/camera, resource reload/frame pressure, private packs and integrated profitability. No isolated owner benchmark before meaningful integration. **Every later session must update `docs/NEXT_SESSION_PROMPT.md` and finish with a NEW copyable handoff that commands its successor to do the same, perpetually.**


## October 19 — generated-world draw witness, full #1068 PASSED

**Current fully qualified executable:** `0f76caf7e5377ce27edebbfc23235c5daaa0168d`; full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) **COMPLETED / SUCCESS** on that exact executable (not the later documentation HEAD). Distributable Forge JAR artifact **12203627510**, smoke-log artifact **12202694551**. Successful full-validation steps included offline contracts, Forge compilation/package, both client startup modes, native GPU indirect/terrain (including `VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK` 509-pair selector proof and `VULKANMOD_GPU_GENERATED_REPLACE_CANDIDATE_OK`), texture multi-dispatch, particle, screenshot, synchronization/post/depth chain, public Create Chronicles with Immersive Portals, and Crash Assistant. Private resource-pack fixture coverage remains unavailable (token absent); no actual-world generated-terrain/portal pixel validation was performed by this run. Previous qualification #1066 `360434e664c13050e2eab1174673ed19525ed813` remains the regression baseline (JAR 12196457613, smoke logs 12196891834).

**Actual-world observability introduced, NOT an observed world result:** opt-in `-Dvulkanmod.debugGpuGeneratedWorldDraw=true` adds `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` only after a live loaded-world GPU-owned `vkCmdDrawIndexedIndirect` recording from current generated candidates; REPLACE, atomic APPEND pair and APPEND with no CPU exceptions are separately inventoried, including optional real Immersive Portals portal-view context. Native fixtures do not produce this marker; it does not prove the GPU selected a nonzero command after culling, actual geometry/pixels, or CPU/GPU visual equivalence. **No real-world marker/screenshot exists in this session.** The same executable skips temporary CPU `DrawCommand`/`DrawPlan` construction for missing/stale/wrong-generation GPU output during generated candidate preparation, retaining unchanged CPU completeness and fallback. Active CPU graph traversal, hashing, admitted per-candidate command planning, CPU recovery and queue/fence-safe retirement remain. Net CPU/GPU costs, adoption and RX FPS/p95/p99 are **unmeasured**; all pilots stay default-off.

**Preserved negative evidence:** full #1067 / run 39286363394 failed in `compileJava` due a newly introduced local `generatedReplace` count colliding with the method's boolean parameter; no #1067 JAR, smoke-log artifact 12204160640, offline contracts passed but all native/Forge runtime tests were skipped. Exact rename in `0f76caf7e5377ce27edebbfc23235c5daaa0168d` restored compilation; #1068 subsequently passed. Keep #1060 (obsolete 512 assertion), #1064 (bounded-overflow shader regression), older #1054/#1050–#1052/#1047/#1048/#1041/#1042 and analyzed #1028 UUID `e05f2b31`. They must not be rebranded as passed.

**Next action:** obtain actual generated-world REPLACE and APPEND production-consumption witness and controlled main-world/Immersive Portals CPU-vs-GPU scene screenshots using `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`, or explicitly retain the external world/AMD hardware gap. Do not demand isolated hardware owner benchmark before integrated verification. After real-world geometry and portal proof, safely eliminate more repeated numeric CPU planning using a complete producer invalidation contract; preserve atomic APPEND fallback and actual-world particle portal/mod callbacks/translucency/lighting/camera/reload/frame-slot gates. Integrated release remains **incomplete**. Every following work session must refresh `docs/NEXT_SESSION_PROMPT.md` and finish with its own new copyable rolling handoff prompt perpetuating that requirement.


## Updated validation checkpoint: #1067 failed; #1068 pending (October 19)

Full [#1067 / run 39286363394](https://github.com/Vorith03/VulkanMod/actions/runs/39286363394) **FAILED before native/Forge runtime**: public offline contracts passed, but `compileJava` rejected the newly added local `int generatedReplace` because that publisher method already has a boolean `generatedReplace` parameter. This is a **Java source naming error, not a negative GPU shader/pixel/synchronization result**. Artifact **12204160640** is failed-run smoke logs; **no #1067 JAR exists**. The targeted, behavior-preserving rename landed in corrected executable `0f76caf7e5377ce27edebbfc23235c5daaa0168d`. Full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) was **queued/in progress** at this documentation checkpoint, with no passed result or verified JAR/smoke artifacts yet. Until full #1068 succeeds, the last qualified executable remains `360434e664c13050e2eab1174673ed19525ed813` in full #1066 (JAR 12196457613; logs 12196891834). **Next: check #1068 conclusion and exact artifacts before claiming qualification; fix any subsequent actual failure step.** All runtime generated-world/portal pixels and integrated profitability remain open.


## ACTIVE Phase 7 — actual-world generated indirect consumption witness (full #1067 pending)

Executable `2f8a45397639d8a616db3b0e08adc915a08a50b8` extends the qualified #1066 APPEND/REPLACE candidate pipeline without changing selector ABI, GPU resources, output ordering, producer identity, portals' exact frustum comparisons, CPU exception pairing, queue or fence lifetime. Pending full [#1067 / run 39286363394](https://github.com/Vorith03/VulkanMod/actions/runs/39286363394) must qualify before executable promotion; #1066 remains the latest passing baseline until then.

The optional `vulkanmod.debugGpuGeneratedWorldDraw` witness is emitted **after real-world Vulkan indirect recording**, separate from fixture-only producer/native tests, with distinct REPLACE/APPEND-pair/APPEND-zero-exception and portal-render context. No emitted live-world witness exists yet; it reports **recorded candidate-backed indirect batches, not GPU-selected-command readback or pixels**. Missing/stale GPU residency now skips allocating a redundant CPU `DrawPlan` during candidate preparation; valid admissions still perform graph traversal, per-candidate numeric planning, record allocation and fingerprints. CPU-time and profitability are unmeasured.

**Gate remains open:** full #1067 check; real generated world mode adoption; screenshots/portal-pixel and fallback equivalence; actual-world particle gates and integrated RX CPU/GPU/dispatch/FPS/p95/p99. Next practical test is documented in `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`. Do not count opt-in witness availability as actual-world qualification. Preserve all old failures and rolling handoff requirements.


## Phase 7 generated APPEND indirect composition — full #1066 correctness-qualified

Executable `360434e664c13050e2eab1174673ed19525ed813`, full **#1066 / [run 39273496566](https://github.com/Vorith03/VulkanMod/actions/runs/39273496566)** **PASSED** (attempt 1), Forge JAR artifact **12196457613**, smoke logs **12196891834**. The workflow checked out the exact executable SHA; all public offline/build/package, both Forge startups, native indirect/terrain/particle/texture/screenshot/synchronization, post/depth, public Create Chronicles with Immersive Portals and Crash Assistant gates passed. The private-pack token was absent, so private resource packs were **not** exercised. Previous fully qualified executable `f9d581ba6a3a9136bd47166e04d5f26e4fda2995`, full #1059, JAR **12181441097**, smoke logs **12181540962**, remains retained as baseline.

New default-off `experimentalGpuGeneratedIndirectAppend` requires `experimentalGpuGeneratedIndirectReplace` plus the existing terrain handoff, GPU indirect commands/draw and CPU-bypass ownership switches. The producer-owned candidate-v1 record format (8 words) is retained with additive paired-row flags: APPEND CPU exceptions and the exact-generation GPU command appear as two adjacent candidate rows with identical section/provenance/ready/layer identity. The existing compute selector reserves two adjacent indexed-indirect slots in **one atomic operation**, emitting CPU exceptions first, GPU-generated quads second; ordinary and generated REPLACE single rows coexist. Candidate and output capacity is **1,024 commands** (512 region sections, at most two each), with bounded zero tail and no new renderer or readback. Individual section pairs are atomic; unrelated sections remain subject to existing GPU compaction order.

Active producer sections are re-evaluated each draw (ordinary producer revisions do not fully cover generated state). Fingerprints include generated ownership, generation and CPU completeness even when numeric fields coincide. Missing/stale/retired GPU residency, unready CPU exceptions, malformed pairing, incomplete provenance/queue/owner, exact frustum-volume changes, unsupported translucent/tripwire layers and capacity/resource failures reject GPU ownership. Rejected generated plans force a **fresh** lazy FrameBatch re-plan rather than trusting ordinary mesh revisions, preserving incomplete-pair withholding and complete CPU recovery. Compute-to-indirect barriers, same-graphics-queue ordering and frame-fence resource retirement are unchanged. No changes to flags' default-off settings.

Mandatory native `VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK` passed in #1066. Real Vulkan readback checked **509 ordered atomic CPU/GPU pairs plus ordinary and REPLACE singles** at 1,024-row input capacity, rejected malformed/unready pairs, exact five-word commands and a zero output tail. The production publisher smoke also covers pending/ready APPEND, mixed REPLACE, zero-exception collapse, candidate capacity, retirement, revocation/recovery and bypass of duplicate FrameBatch upload. Earlier negative CI: **#1060** failed a stale 512-limit layout-test assertion after expanding command capacity; **#1064** caught a shader regression that rejected valid bounded-overflow selection when input count exceeded output capacity. Both were corrected before #1066. Intermediate #1061–#1063 and #1065 were coalesced/cancelled, not qualified. Preserve earlier negative #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31`.

**Disposition: qualified but unmeasured.** This establishes bounded native metadata/command selection and Forge compatibility, **not generated-world rendering, actual portal raster/pixels, modpack adoption or measurable RX frame-time benefit**. Successful GPU owner skips duplicate FrameBatch serialization/upload only; CPU graph traversal, producer queue audit and per-candidate numeric planning/record allocation/hash still run. Rejected generated plans now force CPU re-planning, an unmeasured potential cost. Producer-side invalidation completeness, real generated-world/Immersive Portals culling, sustained churn and integrated GPU/CPU/submit/FPS/p95/p99 profitability remain open. Actual-world particle portal/mod callbacks, lighting/translucency/camera pixels, reload/re-entry and sustained descriptor/frame-slot pressure remain unresolved; no new independent particle defect was found. Do not request an isolated RX owner benchmark or repeat #1028. Integrated GPU-first release remains incomplete.

**Gate:** native candidate/selector mixed APPEND acceptance **closed at fixture/control-flow level**; generated-world/portal pixels, real workload activation and integrated release **open**. **Next:** after this full native qualification, establish real generated-world APPEND/REPLACE/ordinary *production-consumption and visual/portal correctness* with narrow, evidence-backed tests (not candidate/planner fixtures masquerading as pixels); then identify and eliminate genuinely redundant per-candidate CPU numeric planning in accepted GPU-owned work without changing graph, ownership or fallback contracts. Retain unresolved real-world particle gates and integrated adoption/cost work. Every session must update `docs/NEXT_SESSION_PROMPT.md` and end with a NEW copyable rolling handoff prompt which itself mandates the same behavior for every later session.


## Generated REPLACE indirect composition — full #1059 correctness-qualified

Executable `f9d581ba6a3a9136bd47166e04d5f26e4fda2995`, [run 39239916771](https://github.com/Vorith03/VulkanMod/actions/runs/39239916771), **PASSED**, JAR **12181441097**, logs **12181540962**. All public gates passed; private packs skipped. Adds default-off `experimentalGpuGeneratedIndirectReplace` with existing handoff/indirect/CPU-bypass gates. Candidate v1 can encode single-command REPLACE; all APPEND combinations retain the atomic FrameBatch planner. Exact ownership/readiness, provenance, queue, volume, capacity and retirement requirements remain. Generated plans are re-evaluated per draw until their full producer invalidation contract exists. Duplicate FrameBatch serialization/upload is skipped on acceptance; per-section numeric planning remains in candidate preparation, so CPU displacement is incomplete and net cost unmeasured.

Local 24 offline contracts/extracted planner and publisher passed; native indirect smoke passed the mandatory new REPLACE candidate marker. This qualifies fixture metadata/control flow, not a generated-world draw. See the terrain handoff contract for precise tests and boundaries. Previous full #1058 qualification is preserved. Particle real portal/mod pixels/reload/sustained-pressure evidence remains unavailable; no new actionable defect justified delaying terrain. Next: atomic APPEND-aware candidate/selector extension and mixed-consumption qualification. Keep every pilot default-off, preserve negative evidence, and do not request isolated RX benchmarks. Integrated correctness/adoption/profitability and release completion remain open. Refresh the rolling handoff every session.


## Particle descriptor frame-slot retirement — full #1058 correctness-qualified

Executable `29e95e5c31221276dae83d65363407f0c43f740c` (instrumentation `ebb5d320591a31c46700be4bb340b7cdc5444cda`); [full #1058 / run 39236211314](https://github.com/Vorith03/VulkanMod/actions/runs/39236211314) **PASSED**, Forge JAR artifact **12178863678**, smoke logs **12178788575**. #1057 was coalesced/cancelled by the newer executable, not failed native validation. The preceding **qualified** executable was `ba2f574e8db79ea26b7694d9cb37f31c8d5b2710` / full #1056, JAR 12178027221, smoke logs 12177772301. Native Vulkan screenshot smoke confirmed real-pool pressure, fence-slot retirement and renewed admission; build/package, offline contracts, both Forge startups, terrain/texture/particle pixels and synchronization, screenshots, Create Chronicles/Immersive Portals and Crash Assistant all passed; private packs were skipped. The CI particle lifetime log ended with `allocated_descriptors=64 pending_retirement_descriptors=63` after the **second** deliberate saturation, not production churn or a leak.

The opt-in GPU particle statistics now distinguish cached `live_descriptors`, all `allocated_descriptors` (including frame-retired reservations), and `pending_retirement_descriptors`. The native smoke exhausts the real host-enforced 64-descriptor pool with up to 80 distinct vertex buffers, checks the exact reservation increment and CPU-recovery path, frees temporary buffers through ordinary frame ops, advances submitted frame slots through their fences, and requires the same pool to accept a second pressure test at least as effectively. Device-idle waits are fixture-only; no production wait, bigger pool, or new eligible class. This strengthens resource lifecycle verification and pressure attribution, not normal-path GPU/CPU work displacement.

**Open:** real Immersive Portals particle rendering (CI tests loading/shaders/clipping but no real portal-world particle pixels), mod-injected callbacks, translucent/lighting/camera equivalence, actual reload/re-entry and sustained frame pressure, and RX adoption, compute/submission cost, FPS/p95/p99. With #1058 qualified, continue the actual-portal particle gate then generated terrain REPLACE/atomic APPEND candidate ABI with GPU indirect consumption. Retain negative #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31`. All experiments opt-in; release incomplete.


## Particle late-callback admission recovery — full #1056 qualified

Full [#1056 / run 39232013086](https://github.com/Vorith03/VulkanMod/actions/runs/39232013086) **PASSED** for executable `ba2f574e8db79ea26b7694d9cb37f31c8d5b2710`, Forge JAR artifact **12178027221** and smoke logs **12177772301**. Public offline contracts, Forge distributable/both startups, native texture/particle/terrain pixels and synchronization, screenshot and Create Chronicles/Immersive Portals/Crash Assistant checks passed; private packs were skipped.

Default-off exact-campfire GPU rendering now rechecks `BufferBuilder`
admission after particle size/UV/lighting callbacks. If one mutates
sorting/alignment/format, a captured-input CPU quad is emitted without
calling the callbacks twice; previous compact GPU quads are materialized
by the sort hook. Native oracle verifies the callback mutation, exact
counts and color/light, geometry/UV tolerance and clean stream state.
This closes **synthetic callback mutation fail-closed correctness**, not
real-world IP callbacks/translucent pixels, a new GPU eligible class or
any CPU-time or RX FPS/p95/p99 benefit. Continue the ACTIVE GPU-first
sequence; do not promote defaults.


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

New terrain preparation slice: authoritative mesh and Segment lifecycle events publish candidate geometry/readiness; changed traversals reuse snapshots, and pending inputs wait for event invalidation without repeated CPU preparation. Graph traversal and active candidate serialization remain. Local 24 contracts and extracted lifecycle checks passed; full native/Forge qualification passed. This does not close integrated GPU-first release or RX profitability gates. Next implementation should prioritize multi-sprite texture dispatch/shared resources, retaining particle actual-world qualification and generated REPLACE/APPEND ABI gates. Detailed contract: `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`.

## Producer-owned GPU candidate queue containment — full #1045 qualified

**Executable:** `cb248683db075033f0bb28ad42e8882ebba3f6d4`, full **#1045** / [run 39201976864](https://github.com/Vorith03/VulkanMod/actions/runs/39201976864) **PASSED**; distributable Forge JAR artifact **12164426976**, smoke logs **12164093569**. Public offline contracts, Forge build/package/both startups, native GPU terrain/indirect and texture/particle pixels/synchronization, Create Chronicles/Immersive Portals and Crash Assistant gates passed; private pack fixtures skipped.

Production change: for opt-in GPU candidate provenance, `ChunkArea.addSection` now audits queued identity/owned-slot membership, recorded pre-frustum graph or direct-seed membership, and duplicate slot rejection as the traversal producer writes the CPU queue. An optionally tracked `ResettableQueue.contentRevision` detects bypassing `addSection/resetQueue`; registration replacing a queued owned identity revokes the producer proof. `RegionDrawBatch.publishLiveCandidates` checks this O(1) state **before cached GPU command reuse**, removing the previous O(CPU queued sections) per-changed-traversal containment scan. Its remaining producer-active slot walk still validates current owned identity, mesh parameters, upload readiness, graph/direct flags, and post-enqueue direct seed changes. Any incomplete/mutated proof invalidates GPU candidate reuse and lazily falls back to CPU commands. Native regression exercises direct seeds, off-frustum graph stamps, stale replacement, duplicate slot rejection, independent queue mutation and successful fresh traversal recovery. Default CPU-only queues retain revision tracking disabled.

**Disposition: correctness-qualified but performance unmeasured.** This eliminates CPU draw-time containment rework on the experimental GPU candidate path but does **not** eliminate CPU graph traversal, producer enqueue bookkeeping, active candidate slot parameter reads, or all CPU command fallback. It is not an RX FPS/p95/p99 result, proof of portal pixels, or authorization to enable by default. Particle actual portal callbacks/pixels/reload and frame pressure; texture multi-sprite descriptor/scratch compute; producer per-slot metadata/readiness; generated REPLACE/atomic APPEND candidate ABI and integrated RX adoption/net profitability are still open.


## GPU particle descriptor bound qualified — full #1044

**Qualified executable:** `71f1bec858a9c2dbb589d218b172eec624c0dd29`, full **#1044**, [run 39195687691](https://github.com/Vorith03/VulkanMod/actions/runs/39195687691) **PASSED**. Forge JAR artifact **12161603960**, smoke logs **12161404876**. Offline contracts, Forge package and both startups, native Vulkan terrain/indirect plus texture/particle pixel/sync, screenshot, public Create Chronicles with Immersive Portals and Crash Assistant all passed; private packs skipped.

**Production correction:** `6817c071` enforces the 64-descriptor pool reservation on the host, including allocated sets pending frame-fence retirement; it refuses excess GPU work before native allocation and preserves CPU quad materialization. `71f1bec8` isolates an 80-buffer native descriptor-pressure oracle into its own recording frame after three existing exact-geometry passes; also exercises owner teardown/reinit after explicit device idle. The preceding negative evidence is important: #1041 (39194476949) failed the fixture's assumption of native pool rejection; #1042 (39194915798) demonstrated Lavapipe admitted **82 live sets against maxSets=64**. #1043 was superseded by the isolated-frame test before full qualification. The final assertion and all public native/Forge gates are now green.

**Scope:** CPU simulation/light/callbacks and exact campfire eligibility are unchanged; this is a fail-closed resource-safety correction, not a new GPU work-ownership transfer or an observed RX speedup. The native oracle is NOT an actual resource-pack reload or production portal-translucency pixel comparison. Integrated GPU-first correctness/adoption/CPU displacement/added GPU time/FPS/p95/p99, generated REPLACE/APPEND ABI and multi-sprite texture compute remain open. All pilots remain opt-in; do not request a new per-milestone RX benchmark.

This file is the **canonical execution roadmap** for the Forge 1.20.1 port and performance project.

It answers three questions for every current and future agent:

1. What phase are we in?
2. What exact gate are we trying to close?
3. What must be true before we advance?

Live Git/CI/runtime evidence remains authoritative for facts. This roadmap is authoritative for **sequencing and completion criteria** unless the user explicitly changes priorities.

---

## Owner-directed GPU-first release — October 18, 2026 (ACTIVE)

The next substantive release must transfer qualified production graphics work to
GPU ownership and bypass equivalent CPU calculations. This supersedes the old
queue-size/particle-buffer optimization sequence. #1028 correctness evidence is
preserved; its returned RX capture is now read, so do not request it again.

Sequence and release gates:

1. **Textures first:** replace first-come residency admission with bounded,
   interpolation-benefit admission; integrate reusable/batched compute execution
   so broader coverage does not multiply Java descriptor/dispatch preparation.
   Preserve exact source mips, alpha/double arithmetic, clocks, mutations and
   retirement. No budget increase or default promotion without evidence.
2. **Particles:** qualified GPU billboard/render preparation first, simulation
   only for exact supported adapters; retain lighting, collisions, Forge/custom
   callbacks and portal behavior. No generic approximate particle replacement.
3. **Terrain:** activate/complete existing generation-safe REPLACE/APPEND and
   indirect infrastructure; omit GPU-owned CPU geometry, preserve Java exception
   geometry and graph visibility. Source evidence in the current capture says
   mesher/bypass/draw/hybrid flags were all false, not that compute failed.
4. **Integrated qualification:** native pixels/geometry/synchronization,
   Forge/portal/modpack/lifecycle correctness, then one representative changed
   candidate RX observation using the existing valid reference. Report CPU
   displacement, adoption/fallback denominators, CPU preparation, separate GPU
   costs, render-only/tick-bearing and tail frame times. 144 FPS is aspirational.

Implemented texture milestone (full native/Forge #1031, RX benefit unmeasured): `experimentalGpuAnimationAdmission` plans the standard
ticker inventory at the outer texture-tick boundary. Existing live sources stay
pinned, unallocated candidates are ranked by expected interpolation pixels per
charged source+scratch byte, with discrete copies after interpolation. It does
not preload, evict, grow the 128 MiB cap, transfer custom callbacks or claim GPU
speedup. See `docs/GPU_FIRST_OWNERSHIP_2026-10-18.md` for evidence and open gates.
A third opt-in slice removes the CPU indirect-command shadow when existing GPU
command output is current, complete and bounded, with lazy current CPU fallback.
Hybrid APPEND retains its atomic planner until the candidate ABI supports it.
An opt-in exact-campfire billboard compute adapter passed native/Forge full #1036;
its final storage-range correction passed full #1037 attempt 2. Original ordered
stream and shader are retained and CPU four-vertex expansion is bypassed. See
`docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`.
Terrain candidate input fast path passed full #1038 for unchanged draws.
New producer-side traversal provenance reuse passed full #1039 at `e78a7641`
(run 39184804795; JAR 12157573613): exact pre-frustum graph-visit and
direct-seed bitsets let equivalent traversal rewrites retain GPU candidate
generation without the 512-slot CPU scan/hash/Builder. Changed graph, owner,
mesh, readiness, queue or portal volume still forces authoritative scanning.
Per-visit CPU tracking cost, adoption and net RX benefit remain unmeasured.
Full #1040 [run 39192146854](https://github.com/Vorith03/VulkanMod/actions/runs/39192146854) **PASSED** at executable `1b182d53a18c0b83c5b4c1c52f94c08987ad5a1c`, Forge JAR artifact **12160493015**, smoke logs **12160477999**. Public offline/native Vulkan indirect/terrain, texture/particle pixel/sync, Forge package/startups, Create Chronicles and Crash Assistant succeeded; private pack fixtures skipped.
Opt-in candidate v1 now serializes only pre-frustum graph-visited and direct-seeded section slots on changed traversals, not every owned slot. The CPU queue is audited for coverage; stale/incomplete sets retain CPU fallback. Active slots retain readiness/mesh/graph flags, increasing packed-slot order, generation and exact camera/portal-volume checks; GPU hybrid REPLACE/atomic APPEND remains unsupported. The Builder allocates 32 initial records and grows to the 512 bound. Lifetime `gpuCandidateLifetime` profiler/F3 counters cover draw/traversal reuse, sparse rebuild/attempt, prepared/published slots, producer mark attempts and timed publisher preparation; marks include graph/direct attempts, and `prepMs` excludes producer stamps and GPU submission. CPU graph traversal and active-slot parameter reads remain. There is **no measured RX net CPU/GPU/FPS/p95/p99 gain**, and no default or GPU-first release promotion.
Particle portal/mod/pixel adoption and generated-terrain activation remain open.
The entire multi-subsystem mission is **incomplete** until these gates close.

---

## Roadmap governance

### Session continuation

Session startup, live-state recovery, and continuation are governed by the canonical procedure in `AGENTS.md` Section 3A. This file does not define a second startup checklist.

Once live state is established, start from the first unsatisfied mandatory gate in the **ACTIVE** phase unless a live regression, prerequisite blocker, correctness issue, or explicit user priority makes a documented detour necessary.

### No silent roadmap drift

Agents must not silently replace the active goal with a more interesting one.

A temporary detour is allowed when:

- CI or runtime has regressed;
- the active gate is blocked by a prerequisite defect;
- a newly discovered correctness issue makes continuing unsafe;
- the user explicitly reprioritizes the work.

When detouring, report the reason, fix/characterize the blocker, then return to the active roadmap gate.

A substantial roadmap reorder should be committed to this file and called out to the user.

### Evidence required to check a gate

A checkbox becomes complete only when there is corresponding evidence such as:

- green CI;
- Vulkan validation success;
- a reproducible automated test;
- a user RX 6900 XT runtime/visual result;
- a measured benchmark.

Code existing in the repository is not by itself proof that the behavior works.

### Optimization implementation versus realized benefit

For performance work, implementation and CI qualification are enabling milestones, not demonstrated speedup. Consult [optimization evidence ledger](docs/OPTIMIZATION_EVIDENCE_LEDGER.md) before selecting work or describing an existing GPU path as a new opportunity. Every substantive candidate needs recorded production activation and coverage, a fallback/cost explanation, and matched before/after hardware evidence before being marked a **proven improvement**. Record qualified-but-unmeasured and low-adoption candidates explicitly; do not close a performance gate based solely on a correctness oracle. Existing Phase 5 matched baseline and frame-time gates remain open.

### Progress percentages

Do not invent an overall project percentage from intuition.

For reports, use:

- highest completed legacy milestone from `AGENTS.md`;
- active phase;
- mandatory gates completed / total in that phase;
- active gate identifier.

If the user asks for a percentage, the default percentage is the **active phase gate percentage**. Explain that later phases are not equal in size.

---

# Phase 0 — Agent/CI infrastructure

**Status: DONE**

Goal: make autonomous repository work reproducible and inexpensive to iterate.

Mandatory gates:

- [x] Branch CI builds and verifies the distributable Forge JAR.
- [x] Lavapipe Vulkan startup smoke exists.
- [x] Forge no-early-splash Vulkan startup smoke exists.
- [x] Crash Assistant and Flywheel positive compatibility gates exist.
- [x] Fast pre-push `scripts/ci/agent-check.sh` exists for executable checkouts.
- [x] CI smoke logic is centralized and branch concurrency cancels superseded runs.

**Progress: 6/6**

---

# Phase 1 — Forge foundation and packaging

**Status: DONE**

Goal: produce a real Forge 47.3.0 / Minecraft 1.20.1 VulkanMod artifact rather than a development-only launch.

Mandatory gates:

- [x] ForgeGradle project is structurally valid.
- [x] Java 17 compilation succeeds.
- [x] Mixins/refmap are packaged and apply.
- [x] required LWJGL Vulkan/VMA/shaderc pieces are packaged correctly.
- [x] reobfuscated distributable JAR is produced and verified.
- [x] Forge can create the Vulkan `GLFW_NO_API` game window.
- [x] Vulkan renderer activation is demonstrated rather than silently falling back to OpenGL.

**Progress: 7/7**

---

# Phase 2 — Playable renderer baseline

**Status: DONE**

Goal: establish a known-working gameplay baseline before invasive optimization.

Mandatory gates:

- [x] title/menu rendering works;
- [x] a normal world loads through Vulkan;
- [x] terrain renders;
- [x] entities and normal GUI paths render;
- [x] textures render in gameplay;
- [x] liquid rendering regression is fixed and visually confirmed;
- [x] RX 6900 XT gameplay is playable with good baseline behavior;
- [x] terrain region batching/cache foundation can run without preventing gameplay.

**Progress: 8/8**

---

# Phase 3 — Core rendering correctness hardening

**Status: DONE**

Goal: close remaining architectural correctness holes before treating performance measurements as trustworthy.

Mandatory gates:

- [x] offscreen framebuffer/render-pass destruction is deferred safely;
- [x] post-effect pipeline destruction is deferred safely;
- [x] graphics pipelines are reused across compatible render passes without stale native handles;
- [x] fullscreen blit plus RenderTarget clear/filter/resize state is Vulkan-correct for supported paths;
- [x] `RenderTarget.copyDepthFrom` has a Vulkan depth-copy path with corrected depth/stencil barriers;
- [x] MainTarget/swapchain color can be sampled by supported post effects;
- [x] a real vanilla `shaders/post/creeper.json` PostChain constructs successfully in CI;
- [x] **P3.8 — execute one real `PostChain.process(...)` frame under Lavapipe, submit/present it, and exit cleanly;** (CI #282)
- [x] P3.9 — RX 6900 XT visual post-effect check: Creeper and Enderman spectator effects visually confirmed correct on 2026-09-09 after CI #289 fixed pixel output and viewport/scissor validation;
- [x] P3.10 — same-frame Vulkan screenshot transfers with frame-fence readback; post-present requests defer to the next frame and unsupported synchronous entry points are gated. Pixel/resize/Forge-event smoke and synchronization validation pass (CI #284).
- [x] P3.11 — characterize and fix MainTarget depth-aux sampling / sampler-filter semantics for vanilla transparency (nearest/clamp depth sampling, valid copies/barriers; CI #282). Broader mod-specific filtering remains unproven.

**Progress: 11/11**

Phase 3 is complete. Automated Lavapipe pixel/validation coverage and the RX 6900 XT Creeper/Enderman visual check now agree that the supported vanilla PostChain path is rendering correctly.

### Phase 3 exit rule

Do not begin a major terrain renderer rewrite while a known command-buffer/layout/readback correctness defect remains uncharacterized. Phase 3 has satisfied this rule. The earlier bounded GPU-terrain priority produced Phase 7 groundwork. Phase 4 now has 7/8 gates verified; its remaining reload/re-entry lifecycle gate is deferred by user direction. Phase 5 measurement work is the current useful priority and remains required for performance claims.

---

# Phase 4 — Create Chronicles compatibility baseline

**Status: 7/8 VERIFIED; RELOAD AND RE-ENTRY DEFERRED BY USER DIRECTION**

Goal: prove the renderer works in the user's actual target environment and identify the minimum incompatible renderer-replacement set.

Mandatory gates:

- [x] Flywheel 0.6 has a positive CI startup gate;
- [x] Crash Assistant 1.9.7 has a positive CI startup gate;
- [x] current distributable launches the target Create Chronicles instance with Vulkan active; build 289 reached the full ~300-mod instance/world with `Vulkan renderer active: AMD Radeon RX 6900 XT (RADV NAVI21)` on 2026-09-09;
- [x] Create/Flywheel contraptions render correctly in ordinary gameplay; build #801 RX 6900 XT verification covered moving contraptions and ordinary Create/Flywheel rendering;
- [ ] world enter/leave/re-enter and resource reload paths survive in the modpack;
- [x] representative particles/translucency/entities/GUI paths are checked for visible regressions; build #801 RX 6900 XT verification covered these paths along with Creative imagery, third-person player/entities, and Immersive Portals portal rendering/traversal;
- [x] incompatible renderer replacements are evidence-backed and minimized for the target scope (`docs/CREATE_CHRONICLES_RENDERER_REPLACEMENTS_2026-09-26.md`);
- [x] a concise compatibility/known-limitations matrix is committed (`docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`).

**Progress: 7/8**

**Current state:** build #801 established the current RX 6900 XT visual compatibility baseline. Immersive Portals portal rendering/traversal, Creative inventory imagery, third-person player and ordinary entities, Create/Flywheel contraptions, and representative particle/translucency/GUI paths are verified. Do not ask the user to repeat those checks unless a later executable change directly threatens them. The only remaining mandatory Phase 4 gate is world enter/leave/re-enter plus resource reload, and the user has explicitly deferred that lifecycle work. Keep its checkbox open until directly tested. Phase 5 measurement work is the current useful priority.

### Shaderpack scope

Vanilla Minecraft post effects are core correctness and belong in Phase 3. Full Iris/Oculus-style shaderpack compatibility is **not** an initial release blocker unless the user explicitly promotes it; renderer replacements may be fundamentally incompatible and should be characterized rather than forced together.

---

# Phase 5 — Performance baseline and measurement discipline

**Status: ACTIVE MEASUREMENT PRIORITY; CONTRACT PRESENT; COMPARABLE RUNS OPEN**

**Owner-directed architecture priority (2026-10-11):** consolidate animation and
texture transfers into a generic GPU update engine; stop multiplying per-sprite
heuristic exemptions. Preserve opaque CPU dynamic/mod pixel generation as an
input, not a falsely GPU-equivalent algorithm. Start with the common Vulkan
copy-batch path and its synchronization contract, then central shared
resident animation source/dispatch where qualification supports it.
The opt-in shared device-local GPU source pool and unified CPU-staged/
GPU-resident discrete-copy queue are **native/Forge correctness-qualified**
in full CI **#1018** at `db77ff5c5232e18eb6a696a691c5ae01ed660117`.
Exact pixels and synchronization passed across shared-source offsets,
capacity/direct overwrites and both O3/O4 ordering directions. Default
compatibility gates passed; private resource-pack fixtures were skipped.
Representative RX coverage and net frame-time benefit remain **unmeasured**;
use `docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md` for the next gate. Keep both
pilots off by default. **GPU compute animation dispatch coalescing** remains
a separate open step, pending the qualified pilot's coverage/cost evidence.

**October 17 next-framerate owner direction:** After the conditionally
matched first #1023 CPU bulk ON/OFF pair, the owner explicitly selected
the **next performance candidate** rather than immediately repeating two
#1023 pairs. The new opt-in Forge infinite-AABB frustum fast-rejection
candidate passed public/native #1024 at `3f77cab2`; see
`docs/FRUSTUM_INFINITE_BOUNDS_FAST_PATH_2026-10-17.md`.
The owner specifically declined a redundant new OFF/control run:
compare **one new #1024 fast-path-ON capture** against the existing
#1023 bulk-ON UUID `252df7a2` benchmark/JFR as its before baseline.
Retain the same restored-world assumption and otherwise fixed test
settings. This is cross-build screening, not a same-JAR three-pair
proof; frustum RX adoption, visual correctness and net benefit remain
open until the single new capture is analyzed. No framerate gain is
claimed. The separate CPU bulk pair 2/3 and 3/3, CPU-default
adoption, and Phase 5 OpenGL/Vulkan baseline gates remain open.
Do not conflate these separate qualification tracks.

**October 17 evidence update:** #1019's owner RD32 pilot-on capture and raw JFR
are received; see `docs/RX_RD32_CAPTURE_2026-10-17.md` and the ledger. The
common queue/source pool are exercised, but source-buffer switches cause
88.23% of mixed flushes and GPU interpolation covers only 3.79% of standard
attempts, mainly falling back through disabled residency. Representative
coverage is now measured for this capture; **net benefit remains unmeasured**.
The bounded source-grouping repair is implemented with production-method CPU
regressions and full native/Forge qualification in **#1020** at `e0807c62`; see
`docs/TEXTURE_SOURCE_GROUPING_REPAIR_2026-10-17.md`. Fixed-budget admission
stays unchanged. No default adoption or automatic memory-cap increase is selected.
The first #1020 adoption capture is now received: UUID `e12d7c14` (see
`docs/RX_RD32_CAPTURE_1020_AND_TERRAIN_REPAIR_2026-10-17.md`). Source switches
are eliminated, but CPU texture benefit remains unproven. The measured per-frame
terrain traversal/1,604-notice backlog now has a bounded production scheduler
repair with CPU regressions and full native/Forge qualification in #1021 at
`8ff928dd`. Representative RX adoption and net benefit remain open. The unchanged single capture need not be repeated. The #1021 capture is now
received: UUID `f4741fc5`, 439 traversals/6,457 valid frames, 785 cached admissions
and 1,604 retained notices; terrain setup averages 0.907 ms/frame (historical
#1020: 4.221). Adoption is measured; net benefit remains unpaired. The opt-in
bulk CPU interpolation candidate targets the still-measured vanilla pixel loop
without changing GPU budgets/admission. Full native/Forge #1023 qualification passed at `bd2cc13a` (run `39073019680`),
including 56 actual bulk frames and custom exclusion. The **same-JAR #1023 OFF control** UUID `5c61d553` is now received and correctly exercises zero bulk updates versus 2,768,429 ON. Texture CPU 17.984→14.704 ms/tick, complete client tick 30.264→26.809, and throughput 37.03→43.47 FPS favor ON. Upload and main graphics GPU means are nearly unchanged; raw JFR corroborates lower CPU pixel-loop sampling. See `docs/RX_RD32_CAPTURE_1023_BULK_CPU_AB_2026-10-17.md`. The owner directed us to **assume restored benchmark-world snapshots**. The positive #1023 OFF/ON comparison therefore counts **conditionally as matched pair 1/3** (snapshot restoration is assumed, not proven by logs). **Two further alternating snapshot-restored pairs** are needed for the separate CPU fallback default-adoption decision; Phase 5 OpenGL/Vulkan baseline and frame-time gates remain open. No new JAR or GPU budget change is selected. See
`docs/RX_RD32_CAPTURE_1021_AND_CPU_INTERPOLATION_2026-10-17.md`.
Matched control/pairs remain
open; no Phase 5 performance gate is closed by one capture.

**Owner-directed detour (2026-10-06):** prioritize safe GPU offload of renderer work while these measurement gates remain open. O3/O4 texture residency/compute, measured particle adapters, existing Phase 7 GPU terrain/hybrid work, and qualified Vulkan instancing may advance as opt-in experiments. Preserve exact CPU fallback/Forge semantics and do not close Phase 5 gates without comparable hardware evidence.

Goal: create apples-to-apples measurements so optimization claims have evidence.

Mandatory gates:

- [x] define a fixed test world/route and graphics settings for repeatable measurements (`docs/TERRAIN_PERFORMANCE_BASELINE.md`, seed `2026092601` and fixed 1024-block route);
- [ ] record an OpenGL comparison baseline on the same machine/settings/modpack;
- [ ] record the Vulkan baseline on the same machine/settings/modpack;
- [ ] record frame-time behavior in addition to average FPS (at minimum low-percentile or hitch-sensitive evidence);
- [x] define a terrain traversal/chunk-visibility stress case;
- [x] define a Create-heavy/modded rendering stress case;
- [x] commit the benchmark procedure and acceptance rule: no performance claim without comparable before/after evidence.

**Progress: 4/7**

Evidence: `docs/TERRAIN_PERFORMANCE_BASELINE.md` now fixes the benchmark seed, 2560x1440 graphics profile, stationary camera, eastbound `X=-512 -> +512` spectator route, result record, and A/B acceptance contract. Numeric OpenGL/Vulkan/frame-time results remain open.

GPU offload investigation and conditional implementation are sequenced in
`docs/GPU_OFFLOAD_INVESTIGATION_PLAN.md`: corrected baseline and texture eligibility,
resident-frame copies, measured compute interpolation, then existing terrain and
qualified particle tracks. This plan does not close measurement gates or enable
accelerated defaults.

Broader work-avoidance, caching, compatibility and workload options are researched
in `docs/PERFORMANCE_FEATURE_OPPORTUNITIES_2026-10-02.md`. This shortlist does not
change phase completion or the matched #946 hardware comparison priority.

### Default comparison target

The comparison should answer practical user questions, not win a synthetic benchmark. Use the fixed 2560×1440 benchmark profile for comparable A/B runs; additional gameplay resolutions may be reported separately but must not be mixed into that baseline series.

---

# Phase 6 — Terrain renderer v1: persistent region batching

**Status: PERSISTENT FOUNDATION VERIFIED; RX VISUAL/PERFORMANCE GATES OPEN**

Goal: outperform the traditional Minecraft submission model using a stable Vulkan terrain backend before adding mesh shaders.

Mandatory gates:

- [x] region batch-layout regression coverage exists;
- [x] basic terrain region-cache/batching smoke coverage exists and batching can be enabled;
- [x] complete RenderRegionCache lifecycle audit for rebuild/unload/world transitions with no stale GPU ownership;
- [x] move terrain geometry toward persistent region-scoped GPU allocations;
- [x] implement stable suballocation/reuse rather than churn-heavy per-rebuild allocation where practical;
- [x] reduce CPU draw submission count with region/layer batching;
- [ ] add indirect/multi-draw-style submission where profiling shows it is beneficial;
- [x] preserve a known-good fallback path while the new backend matures;
- [ ] demonstrate visual correctness under high chunk churn / camera movement;
- [ ] measure a reproducible improvement (or reject/rework the design if it does not improve the Phase 5 baseline).

**Progress: 7/10**

Evidence: lifecycle audit at #342 and live #351 region-cache/startup logs. No measured speedup is claimed.

### Design target

This phase should make terrain data **persistent, compact, and batch-friendly**. It is intentionally useful even on hardware/drivers without mesh-shader support and becomes the data/residency foundation for Phase 7.

---

# Phase 7 — GPU-driven terrain and hybrid meshing

**Status: BOUNDED GROUNDWORK VERIFIED; GPU-FIRST OWNERSHIP INTEGRATION ACTIVE AFTER TEXTURES/PARTICLES**

Target sequence: persistent regions -> GPU visibility/section selection -> GPU
indirect commands -> GPU terrain representation -> hybrid meshing -> optional mesh
shaders. Define input data now without enabling unqualified GPU rendering.

Mandatory gates:

- [x] define and validate compact CPU/GPU section input with conservative exception handling;
- [x] add bounded region-scoped voxel/state GPU residency and independent update/invalidation;
- [x] add feature-gated compute plumbing with explicit barriers, ownership and fallback;
- [ ] implement correct GPU visibility/section selection;
- [x] generate bounded GPU indirect commands while retaining direct/legacy fallbacks;
- [x] qualify reusable baked-model templates and resolve Java-dependent instance metadata;
- [x] implement hybrid ordinary-cube meshing with halo/light/tint inputs and output-overflow fallback;
- [ ] integrate rebuild/unload/world/resource-generation transitions without stale GPU data;
- [ ] preserve arbitrary Forge callbacks, block entities and unsupported models on CPU;
- [ ] preserve translucent/tripwire rendering until separately supported and validated;
- [ ] obtain RX 6900 XT A/B correctness/performance evidence before enabling an accelerated default.

**Progress: 6/11 verified gates.** Input infrastructure, bounded region residency,
diagnostic compute plumbing, fail-closed reusable model instances, bounded GPU
indirect-command generation/fallback, and hybrid ordinary-cube meshing are verified.
Live GPU selection correctness, broader lifecycle/Forge-preservation validation, and
RX 6900 XT correctness/performance evidence remain open; accelerated consumption stays
default-off pending representative hardware evidence.

Current lighting/output work has advanced beyond the earlier dense-lattice prototype.
Reusable canonical model templates, bounded sparse lighting capture/decode, exact
Minecraft-matched AO/color/light reconstruction, and complete packed 20-byte terrain
vertex generation are proven for the qualified subset. The recovered Create Chronicles
density sample still supports bounded sparse input; do not repeat that collection.
CPU terrain output remains authoritative in normal gameplay because all accelerated
terrain gates are default-off. With the explicit experimental gates enabled, fully
qualified REPLACE sections can become GPU-first and conservative APPEND sections can
split one generation between CPU exception geometry and GPU ordinary-cube geometry.

The bounded-output contract includes generation-owned persistent area-buffer publication.
`GpuTerrainSectionMesher` is now a reusable production-owned compute dispatcher rather
than living only inside the smoke test. `GpuTerrainSectionMesherBridge` can, when the
experimental property is enabled, consume exact-generation voxel + sparse-lighting
residency and the current qualified model table, reserve bounded output, dispatch outside
an active render pass, and publish only an exact, non-overflow result for a section whose
visible block-model geometry is fully qualified. Missing/stale input, unsupported visible
geometry, fluids, allocation/dispatch failure, generation turnover, or count mismatch
leave the CPU mesh authoritative. CI #503 is green for this bridge state.

The reusable model/template gate is complete for the supported subset as recorded in
`docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. The qualifier exercises
Forge's actual ModelData/render-type quad API, requires a singleton solid layer and
rejects seed-varying geometry. Unsupported Forge callbacks and per-position offsets
remain CPU-only rather than being approximated.

The section-selection/indirect track is now live rather than synthetic-only. The
versioned candidate table covers generation, region identity, mesh readiness, CPU
graph visibility, terrain layer, nonempty draws and six-plane frustum intersection.
A live producer builds each layer's table from the full region-owned fine-section
superset, freezes the production frustum for that generation, and publishes through
generation-safe device-local residency. The rate-limited diagnostic can compare live
GPU selection and exact five-word commands with the authoritative CPU queue.

Persistent GPU indirect output remains a bounded storage+indirect buffer with a
512-command capacity, zero-tail commands and explicit synchronization. The separate
default-off production indirect consumer retains the CPU batch whenever generation,
region, candidate-superset, capacity, or feature-gate checks fail. See
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`.

The default-off generated-geometry consumer remains fail-closed. `RegionDrawBatch.FrameBatch`
can consume exact-generation GPU output for REPLACE or emit CPU-then-GPU APPEND commands
for one section. Publication/invalidation advance mesh revision so cached frame batches
cannot retain stale offsets, APPEND suppresses both halves until its CPU exception upload
is ready, and stale/missing GPU residency falls back to complete CPU output when available.

Production section-mesher completion is non-blocking on the render thread. Qualified
fresh REPLACE sections can skip ordinary CPU `renderBatched(...)`; conservative APPEND
workers omit only the GPU-owned ordinary-cube subset while preserving unsupported
geometry, fluids, block entities, and protected neighbors on CPU. Dirty APPEND rebuilds
stage CPU exception geometry and GPU output independently, retain the previous complete
pair, and switch both halves atomically only when the replacement generation is ready.
CI #723 validates the current combined terrain/compatibility tree, including transition,
face-policy, readback, overflow/fallback, hybrid command-count/section-count oracles,
the GLSL declaration-parser regression, Immersive Portals framebuffer rendering under
VulkanMod's no-OpenGL-context window, per-portal-world `LevelRenderer` ownership,
recursive render-buffer use, terrain clip-plane propagation across all three Vulkan
terrain pipelines, IP reload-hook compatibility, an aliased `rendertype_cutout` terrain
shader, and an aliased `rendertype_entity_translucent` model-view shader that mirrors
IP's entity/projection/weather clipping state machine. Distant Horizons 3.2.0-b is explicitly fail-closed under
Vulkan by suppressing its OpenGL LOD draw/fade passes.

The hybrid implementation gate is therefore closed. The first attempted narrow
Create Chronicles/RX 6900 XT run stopped before terrain evidence because Immersive
Portals still issued a raw stencil GL call; that regression and the downstream portal
multi-world/clipping hazards are covered by CI #690. A later full-pack build #711 run
then exposed a separate Create 0.5.1.j startup requirement: its GUI helper creates an
off-screen stencil RenderTarget, so the audit-era fail-closed stencil rejection was not
sufficient. Off-screen Vulkan stencil support plus a call-site bridge for Create's raw
`GL_STENCIL_TEST` toggles are implemented and covered by the exact Create fixture. The
same #711 run also exposed parser noise and a Twilight Forest/Immersive Portals aliased-
program failure once Forge shader registration was correctly restored: `647c13e225b4`
fixes comment text being parsed as GLSL declarations, and `b9910cfb0675` binds the
correct pre-model-view IP clip plane when a namespaced Forge shader aliases one of IP's
terrain programs. The next RX run on build #720 confirmed those fixes but exposed the
same IP name/program mismatch in Alex's Caves: `rendertype_sepia` aliases transformed
`rendertype_entity_translucent`. `5964641c5428` adds the corresponding model-view alias
bridge using IP's own entity/projection/weather clipping semantics. `a12fb862873c` adds the
observed `particle` alias shape to the IP smoke and `860961c6b636` adds a selected-resource-
pack retention oracle; CI #723 is green for all of them plus the existing Create stencil
fixture. When Phase 7 resumes, its next RX evidence boundary is a repeat functional run
with REPLACE + APPEND enabled, including looking through a real portal and performing a
dirty mixed-section rebuild. This remains a correctness test, not a performance claim.
Live GPU visibility/section-selection correctness and broader lifecycle/Forge preservation
gates remain open until representative runtime evidence supports them.

Mesh-shader capability detection, optional meshlet formats and a mesh-shader draw
backend remain later work. They must not become a prerequisite for classic compute
or CPU fallback on the RX 6900 XT. Existing Phase 5/6 measurement gates remain open.

---

# Phase 8 — Stability, compatibility closure, and release candidate

**Status: PLANNED**

Goal: turn the optimized renderer into something the user can keep installed rather than a benchmark prototype.

Mandatory gates:

- [ ] repeated world load/unload/reload cycles do not show unbounded Vulkan resource growth;
- [ ] resize/window/fullscreen transitions are stable on the target setup;
- [ ] extended normal gameplay session completes without renderer crash/device loss;
- [ ] representative Create Chronicles gameplay is stable and major visual defects are closed or documented;
- [ ] Vulkan validation/debug runs are clean enough that remaining messages are understood and non-blocking;
- [ ] final compatibility matrix and required mod/configuration changes are documented;
- [ ] final OpenGL vs Vulkan benchmark report is recorded, including cases where Vulkan is not faster;
- [ ] installable release-candidate JAR is produced from green CI with concise test/install notes.

**Progress: 0/8**
