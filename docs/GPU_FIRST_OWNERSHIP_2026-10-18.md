# GPU-first renderer ownership — October 18, 2026


## Integrated implementation milestone — October 20 completion checklist

Owner scope: internally qualified, default-off integrated build **ready for one
real-world qualification package**, not release completion or proven RX speedup.
This checklist supersedes historical next-step paragraphs below. Qualified
executable: `9a6de848`, full #1082 / run 39551989165 SUCCESS (attempt 1).
**Internal implementation-readiness milestone complete; real-world qualification open.**

| Path | Production entry / consumed result / CPU displacement | Remaining CPU / disposition | Internal completion evidence |
| --- | --- | --- | --- |
| Textures | SpriteTickerMixin -> GpuTextureInterpolationCompute -> unified atlas copy queue; accepted scratch pixels replace Java interpolation, immutable pooled source copies replace pixel staging. Shared pages and 32-row reusable tables aggregate compute. | Clocks, exact source snapshots, custom ticker semantics, mutation invalidation, bounded scheduling and submission are required orchestration; rejected/capacity/unsupported work retains CPU. No new generic animation engine. | Existing transformed ticker, odd/all-mip pixels, overlapping CPU/GPU copies, 320-row wrap and retirement oracles; [x] combined configuration CI. |
| Particles | ParticleBillboardMixin inherited exact CampfireSmokeParticle render -> packed slots -> original BufferUploader -> ParticleBillboardCompute -> original graphics draw; accepted four-vertex expansion bypasses CPU transforms/emissions. | Simulation, world/light queries, callbacks, camera/roll and sorting are required semantic inputs. Arbitrary subclass/mod simulations unsupported; CPU materialization is rejection/sorting recovery only. Original vertex upload size retained, not called eliminated. | Native mixed 524-vertex/callback-once, late-state recovery, capacity and descriptor-fence pressure; [x] combined configuration CI. |
| Terrain meshing / lighting | ChunkTask capture/preflight -> GpuTerrainSectionMesherBridge -> production mesher -> generation-owned persistent output -> generated handoff. Qualified cubes omit CPU renderBatched; GPU produces complete 20-byte position/UV/AO/color/light vertices. | ModelData/custom models, tint, fluids, block entities, unsupported layers and experimental Forge lighting remain CPU semantics; sparse world/light/halo capture, qualification, VisGraph and publication are required inputs. No approximate simulation or GPU world API. | Existing real model, sparse light and packed-vertex/native async completion, overflow/stale/recovery fixtures; [x] combined enabled startup/modpack CI. |
| Culling / selection | WorldRenderer producer graph/direct stamps -> exact frozen VFrustum candidate input -> native selector -> persistent storage+indirect output consumed by RegionDrawBatch.draw. GPU does final candidate frustum/compaction; graph traversal remains. | CPU connectivity/mod traversal and legacy visibility work still needed for world/chunk consumers; no claim that GPU frustum testing replaces graph policy. Unsupported sorted layers retain CPU. | Native exact six-plane/direct-seed/zero-tail oracle; [x] combined production consumer pixels. Genuine portal and representative graph-set correctness remain external qualification. |
| Candidate / command preparation | Producer geometry/readiness, ownership/provenance and generated revisions cache unchanged proof; accepted GPU owner skips FrameBatch construction/serialization/upload. | Changed-input metadata validation/serialization and bounded arithmetic express GPU inputs; cached unchanged draws bypass these. Fallback-only CPU command construction remains. No synchronous count readback or speculative new cache. | Existing mode/invalidation/revocation/readiness/identity/stale queued publication fixtures; [x] actual draw acceptance plus no commandUploads increment. |
| Generated REPLACE | Exact-generation ready output substitutes CPU command/geometry; original region vertex buffer and auto indices consumed by GPU-selected draw. | CPU complete fallback only on missing/stale/rejected output; input capture/ownership and producer changes required. | Native publisher and resident fingerprint tests; [x] production draw pixels with nonzero resident offset and CPU commands bypassed. |
| Atomic APPEND | Generation transaction preserves CPU exceptions then GPU ordinary geometry; paired candidate rows reserve adjacent selector slots atomically. | Java exception geometry intentionally retained; missing half withholds pair and recovers complete CPU mesh. 512 sections / 1,024 commands unchanged. | Existing real transaction/publication queues and 509-pair native capacity/order oracle; [x] production draw red CPU + green GPU overlapping pixel order and frame-slot reuse. |

No unfinished eligible algorithm is relabelled retained: the bounded supported
algorithms already have production consumers; newly identified gap is **combined
configuration and the final generated indirect raster edge**. Actual loaded-world
mesher/portal adoption, private packs, long reload/re-entry churn and RX profitability
remain distinct external observations, not implementation acceptance by fixture.
If combined validation reveals a defect, repair it before owner testing.

## October 21 — bridge-to-raster qualification in progress

The owner explicitly requested continued work toward the full GPU effort after
internal readiness. That broader effort is **not complete**. A remaining internally
feasible integration edge is now under qualification: actual production mesher
output -> bridge async completion/publication/handoff -> native selector ->
RegionDrawBatch indirect raster, with accepted CPU command-upload bypass.

Reuse decision: existing numerical mesher tests and seeded indirect pixels prove
separate halves but do not join them. Reuse the existing screenshot draw oracle,
real ChunkArea voxel/light upload, baked model qualification, bridge dispatcher,
completion poll, residency and original draw. Add only deterministic isolated-cube
input in the existing mesher smoke and a second mode of the existing pixel test;
no new runtime hook, allocator, scheduler, dependency or replacement oracle.
The fixture-only idle wait exposes signaled helper completion; production remains
non-blocking. No GPU vertex payload is read back, patched or reuploaded. The
original seeded test and independent numerical/async oracles remain unchanged.

Validation pending on the new executable. Synthetic inputs and an unlit projection
remain distinct from loaded ClientLevel terrain, atlas/lightmap rendering, real
portals/private packs and hardware profitability. Those gates remain open.

### Reproduced traversal-stamp defect and bounded correction

Full #1080 / run 39514286089 on `eb6989a3` passed compilation/package,
startup, combined no-splash, native indirect, post/depth, then FAILED the new
production consumer pixel oracle: expected green `ff00ff00`, got black `ff000000`
while GPU command ownership was accepted. No qualified #1080 JAR exists.
#1079 was cancelled/superseded, not qualified.

Root cause: WorldRenderer increments a `short` traversal stamp through all
65,536 values. ChunkArea used `-1` as absent-proof sentinel in graph visibility,
provenance copying/comparison and sparse active masks. A legitimate `-1`
traversal suppressed GRAPH_VISIBLE (and graph-only candidates), while direct
candidate metadata could still admit an empty GPU draw. The fixture's initial
`-1` stamp exposed this real production-domain collision.

Correction: one explicit `traversalFrameValid` bit, established by authoritative
addSection and cleared by resetQueue/clearVoxels. All four reader paths use the
bit; the short's equality/domain and CPU traversal are unchanged. Reuse decision:
existing stamps/bitsets/revisions already express ownership. Reserving `-1` in
WorldRenderer would alter shared CPU semantics; widening all stamps or adding a
new epoch/cache is unnecessary. One validity bit is the smallest missing state.
No queue/barrier/fence, capacity, portal volume or fallback policy changes.

Focused Java 17 extraction of the four actual production reader methods passed
all 65,536 valid values plus absent-proof rejection; restoring the old sentinel
failed specifically at valid stamp `-1`. This is local policy evidence, not
native qualification. The real RegionBatchSmokeTest now checks -2/-1/0/MAX/MIN,
off-frustum graph slots, direct seed/provenance copy/compare, reset and area clear.
The production pixel oracle deliberately stays at `-1` and still requires exact
nonzero REPLACE/APPEND pixels, zero-exception APPEND, complete CPU fallback and
re-adoption. The corrected executable passed full #1082 as recorded below.

Reuse decision: the missing raster edge cannot be proven by current candidate
metadata/selector readback fixtures. Extend existing ScreenshotReadbackSmokeTest,
RegionDrawBatch.draw, AreaBuffer/output residency, publication queues, Pipeline
builder and native screenshot readback. Add only a CI-only consumer pixel fixture,
with independently seeded resident vertices and an unlit shader; it does **not**
claim real mesher output or a loaded world. One plain JVM-flag file is shared by
existing no-splash/screenshot/public compatibility scripts so configuration cannot
drift across those clients; no new harness, dependency, scheduler or allocator.

### Final integrated qualification

Exact executable `9a6de8480c7ab46ccae7cdc06a652c108daa8078`, full
[#1082 / 39551989165](https://github.com/Vorith03/VulkanMod/actions/runs/39551989165),
SUCCESS attempt 1. JAR artifact **12324685470**, logs **12323799769**.
Forge build/distribution and nested LWJGL linkage verified; all 24 offline
contracts, baseline startup, combined GPU no-splash startup, native mesher/model/
lighting/async publication, indirect/509-pair APPEND/REPLACE, post/depth, combined
screenshot texture/particle/generated-indirect pixels and synchronization,
combined public Create Chronicles/IP and Crash Assistant passed. Private packs
skipped. Native `VULKANMOD_GPU_TRAVERSAL_STAMP_DOMAIN_OK` and
`VULKANMOD_GPU_GENERATED_INDIRECT_PIXELS_OK` were observed in actual job logs.
The latter proves nonzero resident-offset REPLACE, ordered overlapping CPU/GPU
APPEND, zero-exception APPEND, frame-slot reuse, rejected-proof complete CPU
recovery/re-adoption and no duplicate accepted CPU command uploads. Seeded
vertices remain distinct from actual loaded-world mesher/portal pixels.

Downloaded artifact bytes were inspected: 11,074,747-byte JAR, manifest and
mods.toml version `0.3.2-forge.2-build.1082-g9a6de848`, new consumer fixture class,
SHA-256 `c97997f24572fce48abbe739659b3b69808bd5f24d5e61d9ffbf28dc30f1e64a`.
No renderer run is needed for the final documentation-only checkpoint.

Qualification sequence remains explicit: #1080 failed real pixels on eb6989a3;
#1081 failed Java test access on cfbd0437 before native checks (logs 12323488387).
9a6de848 uses public releaseBuffers for the same teardown assertion and qualified
both the validity-bit fix and all integrated fixtures. #1079 was cancelled.
No assertions were weakened. Experiments/defaults, ownership limits and recovery
contracts remain. No known independently repairable in-scope defect/prerequisite
remains for this test package; do not infer exhaustive world/mod correctness.

The current [test package](GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md) supplies
exact artifact/setup, shared activation flags, explicit CPU-off control, scenes,
visual/adoption/performance criteria, failures and log paths. Proceed to that
focused real-world qualification when its inputs are available; do not reopen
settled maintenance or invent another optimization solely to continue coding.
RX speedup/default adoption, real portals/private packs, actual particle callbacks/
lighting/translucency and sustained gameplay/reload evidence remain open.

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


## Generated-world indirect recording observation (October 19, executable #1067 pending)

`2f8a45397639d8a616db3b0e08adc915a08a50b8` adds **diagnostic-only** `vulkanmod.debugGpuGeneratedWorldDraw`: after actual live-world `vkCmdDrawIndexedIndirect` with accepted GPU command ownership, emit a rate-limited per-mode/per-portal-context `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` record. Inventories are computed when actual producer section plans publish an accepted candidate table, not from the ordinary mesh CPU selection diagnostic; accepted fingerprint reuse retains inventory, rejected/retired tables reset it. A separate gate asserts loaded world, GPU command owner, real recorded draw, and generated REPLACE/paired-APPEND/zero-exception APPEND. The portal classification uses the optional live Immersive Portals render-context API and is never inferred from frustum or camera coordinates. No generic startup test can produce this witness without a live world. A witness still cannot establish that a particular candidate survived GPU frustum culling, nor that vertex/pixel output matches CPU; production pixels and portal comparison are still missing.

An equivalent nonresident GPU plan shortcut avoids redundant CPU `DrawPlan` allocation, retaining the exact existing completeness fallback. Command ABI, paired reservation, owner/generation/readiness, graph/direct provenance, volume/queue checks and GPU-safe resource retirement are unchanged. CI [#1067](https://github.com/Vorith03/VulkanMod/actions/runs/39286363394) pending; #1066 remains last qualified. Reproduction/test boundary in `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`.


## Generated APPEND atomic GPU indirect ownership — full #1066 correctness-qualified

Executable `360434e664c13050e2eab1174673ed19525ed813`, full **#1066 / [run 39273496566](https://github.com/Vorith03/VulkanMod/actions/runs/39273496566)** **PASSED** (attempt 1), Forge JAR artifact **12196457613**, smoke logs **12196891834**. The workflow checked out the exact executable SHA; all public offline/build/package, both Forge startups, native indirect/terrain/particle/texture/screenshot/synchronization, post/depth, public Create Chronicles with Immersive Portals and Crash Assistant gates passed. The private-pack token was absent, so private resource packs were **not** exercised. Previous fully qualified executable `f9d581ba6a3a9136bd47166e04d5f26e4fda2995`, full #1059, JAR **12181441097**, smoke logs **12181540962**, remains retained as baseline.

New default-off `experimentalGpuGeneratedIndirectAppend` requires `experimentalGpuGeneratedIndirectReplace` plus the existing terrain handoff, GPU indirect commands/draw and CPU-bypass ownership switches. The producer-owned candidate-v1 record format (8 words) is retained with additive paired-row flags: APPEND CPU exceptions and the exact-generation GPU command appear as two adjacent candidate rows with identical section/provenance/ready/layer identity. The existing compute selector reserves two adjacent indexed-indirect slots in **one atomic operation**, emitting CPU exceptions first, GPU-generated quads second; ordinary and generated REPLACE single rows coexist. Candidate and output capacity is **1,024 commands** (512 region sections, at most two each), with bounded zero tail and no new renderer or readback. Individual section pairs are atomic; unrelated sections remain subject to existing GPU compaction order.

Active producer sections are re-evaluated each draw (ordinary producer revisions do not fully cover generated state). Fingerprints include generated ownership, generation and CPU completeness even when numeric fields coincide. Missing/stale/retired GPU residency, unready CPU exceptions, malformed pairing, incomplete provenance/queue/owner, exact frustum-volume changes, unsupported translucent/tripwire layers and capacity/resource failures reject GPU ownership. Rejected generated plans force a **fresh** lazy FrameBatch re-plan rather than trusting ordinary mesh revisions, preserving incomplete-pair withholding and complete CPU recovery. Compute-to-indirect barriers, same-graphics-queue ordering and frame-fence resource retirement are unchanged. No changes to flags' default-off settings.

Mandatory native `VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK` passed in #1066. Real Vulkan readback checked **509 ordered atomic CPU/GPU pairs plus ordinary and REPLACE singles** at 1,024-row input capacity, rejected malformed/unready pairs, exact five-word commands and a zero output tail. The production publisher smoke also covers pending/ready APPEND, mixed REPLACE, zero-exception collapse, candidate capacity, retirement, revocation/recovery and bypass of duplicate FrameBatch upload. Earlier negative CI: **#1060** failed a stale 512-limit layout-test assertion after expanding command capacity; **#1064** caught a shader regression that rejected valid bounded-overflow selection when input count exceeded output capacity. Both were corrected before #1066. Intermediate #1061–#1063 and #1065 were coalesced/cancelled, not qualified. Preserve earlier negative #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31`.

**Disposition: qualified but unmeasured.** This establishes bounded native metadata/command selection and Forge compatibility, **not generated-world rendering, actual portal raster/pixels, modpack adoption or measurable RX frame-time benefit**. Successful GPU owner skips duplicate FrameBatch serialization/upload only; CPU graph traversal, producer queue audit and per-candidate numeric planning/record allocation/hash still run. Rejected generated plans now force CPU re-planning, an unmeasured potential cost. Producer-side invalidation completeness, real generated-world/Immersive Portals culling, sustained churn and integrated GPU/CPU/submit/FPS/p95/p99 profitability remain open. Actual-world particle portal/mod callbacks, lighting/translucency/camera pixels, reload/re-entry and sustained descriptor/frame-slot pressure remain unresolved; no new independent particle defect was found. Do not request an isolated RX owner benchmark or repeat #1028. Integrated GPU-first release remains incomplete.

**Next:** after this full native qualification, establish real generated-world APPEND/REPLACE/ordinary *production-consumption and visual/portal correctness* with narrow, evidence-backed tests (not candidate/planner fixtures masquerading as pixels); then identify and eliminate genuinely redundant per-candidate CPU numeric planning in accepted GPU-owned work without changing graph, ownership or fallback contracts. Retain unresolved real-world particle gates and integrated adoption/cost work. Every session must update `docs/NEXT_SESSION_PROMPT.md` and end with a NEW copyable rolling handoff prompt which itself mandates the same behavior for every later session.


## Generated REPLACE indirect composition — full #1059 correctness-qualified

Executable `f9d581ba6a3a9136bd47166e04d5f26e4fda2995`, [run 39239916771](https://github.com/Vorith03/VulkanMod/actions/runs/39239916771), **PASSED**, JAR **12181441097**, logs **12181540962**. All public gates passed; private packs skipped. Adds default-off `experimentalGpuGeneratedIndirectReplace` with existing handoff/indirect/CPU-bypass gates. Candidate v1 can encode single-command REPLACE; all APPEND combinations retain the atomic FrameBatch planner. Exact ownership/readiness, provenance, queue, volume, capacity and retirement requirements remain. Generated plans are re-evaluated per draw until their full producer invalidation contract exists. Duplicate FrameBatch serialization/upload is skipped on acceptance; per-section numeric planning remains in candidate preparation, so CPU displacement is incomplete and net cost unmeasured.

Local 24 offline contracts/extracted planner and publisher passed; native indirect smoke passed the mandatory new REPLACE candidate marker. This qualifies fixture metadata/control flow, not a generated-world draw. See the terrain handoff contract for precise tests and boundaries. Previous full #1058 qualification is preserved. Particle real portal/mod pixels/reload/sustained-pressure evidence remains unavailable; no new actionable defect justified delaying terrain. Next: atomic APPEND-aware candidate/selector extension and mixed-consumption qualification. Keep every pilot default-off, preserve negative evidence, and do not request isolated RX benchmarks. Integrated correctness/adoption/profitability and release completion remain open. Refresh the rolling handoff every session.


## October 19 particle post-callback admission safety — full #1056 qualified

Full [#1056 / run 39232013086](https://github.com/Vorith03/VulkanMod/actions/runs/39232013086) **PASSED** for executable `ba2f574e8db79ea26b7694d9cb37f31c8d5b2710`, Forge JAR artifact **12178027221** and smoke logs **12177772301**. Public offline contracts, Forge distributable/both startups, native texture/particle/terrain pixels and synchronization, screenshot and Create Chronicles/Immersive Portals/Crash Assistant checks passed; private packs were skipped.

Exact-campfire inherited rendering rechecks the GPU particle writer after
size/UV/light callbacks. Late callback-triggered sorting or other builder
mutation now recovers the current quad on CPU from captured numeric inputs
without duplicated callback execution, instead of throwing on stale GPU
admission. The native synthetic mutation oracle proves prior packed-quad
CPU recovery, current captured fallback, later CPU quads and matching
vertex/light/color. `callback_state_cpu_recoveries=3` is test-generated,
not a workload adoption measure. Production Immersive Portals injection,
translucent pixels, real reload and representative RX cost remain unverified.
No new CPU ownership transferred; keep all experiments default-off.


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

