# GPU terrain indirect-draw handoff — 2026-09-15

## October22 — fence-empty input-page ownership correction (candidate)

Consumed input slice retirement now destroys a page ONLY when the final allocation
has crossed its existing main-frame fence. Pending/live replacement allocations
keep the page alive. Native BufferInfo destruction/accounting and global quota
return are one operation inside that already completed fence callback; no callback
is appended to the frame-op list currently being drained. Page indices of LIVE
residency never shift; retired holes are reused, trailing holes removed, and lookup
of an empty retired slot returns null. Close skips previously retired holes and
retains its original deferred resource release. The32MiB/512KiB/alignment budgets,
generation checks, output/APPEND/model ownership and CPU recovery are unchanged.

The old store reproduced cross-region starvation under a one-page shared budget
after all input slices retired. Updated production-store contract admits128 region
pairs under the same bound, with pending/live protection and exact native-resource
accounting. Full native different-region byte/compute and integrated raster/bypass /
lifecycle qualification is required before promotion; see the focused appearance
report for the actual final run. More frequent isolated input-page allocations are
an explicit owner pacing/memory gate, not a proven net performance improvement.


## October22 — staging + frame-fenced consumption observation qualified in full1097

Executable2577508c/full1097 /39817774598 SUCCESS; exact JAR/hash and full evidence
in TERRAIN_APPEARANCE_2026-10-22.md. One-shot CPU inputs release by identity; GPU
input slices revoke discovery then retire at existing frame fences. Input failures
return staging through existing deferred recovery. Output, APPEND pairing and CPU
recovery remain separate; no geometry quota/default changes.

Default-off debugTerrainDrawReadback copies ACTUAL recorded GPU/CPU indirect
sources after rendering before command finalization, then reads after the existing
frame fence. Diagnostic source revisions suppress unchanged copies only; changed
readiness/ownership re-observes. Source metadata and requests are bounded and
cancelled after device idle on failure/shutdown. CPU source barriers do not require
compute. World epoch/stage ownership prevents delayed callbacks being attributed
to a replacement world. Native exact raster/CPU bypass and both enabled same-save
world/portal controls passed, with all18 original phase pixel pairs exact and no
validation/synchronization errors. No independent selector or gameplay wait added.

This is command-consumption correctness evidence, NOT on-screen appearance time
or full-view percentage. Full input availability/reference/generation/path coverage
and representative RX performance gates remain open. Preserve these distinctions.


## October22 — single-consumer mesher input staging (candidate, qualification pending)

CPU voxel snapshots are no longer persistent inputs after the mesher dispatch
attempt. The bridge returns the exact captured snapshot by identity; async output
completion retains numeric plans, model/output owners and its helper result, not
the snapshot. Matching-generation GPU voxel/light inputs lose discoverability;
existing frame-fence slice retirement still owns physical reuse after graphics
queue upload -> compute -> main-frame submission. Rendered output/APPEND pairing
and CPU recovery are unchanged. Input failures return staging from the existing
deferred recovery callback, even when complete CPU geometry needs no rebuild.
A replaced CPU snapshot or newer pending/resident GPU generation is protected.

Byte/entry/page quotas, region page caching, selector ABI, visibility/provenance,
output retirement and default-off activation remain. The raster fixture stages its
worker plan before uploads and uses actual automatic post-submit dispatch; it
requires input revocation while the original production pixels/bypass/retirement
oracles run. Native sparse fixtures additionally read consumed bytes before
retirement and reject stale consumers. Final candidate e6b9b35b/full1094 pending;
see TERRAIN_APPEARANCE_2026-10-22.md. No initial-appearance or RX speedup claim.


## October21 — integrated production ownership qualified in full #1088

Executable `95221419486931c295b83b1efe1c98ef3df4eef7` passed full #1088 /39680381869,
including9 same-save main-world and9 actual public-portal exact pixel phases,
per-capture generated GPU ownership/CPU command bypass, recovery/reload/re-entry/
producer turnover, native capacity and retirement, zero validation errors/hazards.
Verified JAR12383727666/logs12385056493; see the single GPU_FIRST_OWNERSHIP checklist
and current GPU_GENERATED_WORLD_DRAW_WITNESS owner package. Older pending statements
below are historical, superseded by this qualification.

RegionDrawBatch publishes candidate input, submits at most once when exact residency
is pending, then revalidates original cache/provenance/readiness before dispatch.
Existing GpuSectionSelectionShadowStore root owns bounded borrowed view slots under
the same16MiB quota. Exact cached output is selected first; every accepted cached
read claims its actual frame, even without a new dispatch. A different still-recorded
view cannot overwrite its output/parameters/descriptors. Root close retires every
slot. Negative allocation proof expires only when actual native quota retirement
advances revision, never constructor rollback. No new allocator, raised capacity,
synchronous production wait or approximate portal volume.

BuildTask snapshots actual dispatch failure separately from incomplete-producer
invalidation. If non-dispatch turnover has no complete ready old view, the worker
first completes ordinary CPU recovery. Only that publication queues ONE existing
post-submit callback; current generation/area, non-dirty/non-failed state and ready
complete CPU fallback are required before setDirty requests a standard GPU-capable
rebuild. Stale/recycled/new-failure callbacks cannot readmit. Actual failed dispatch
is excluded at the task publication, so it remains complete CPU until subsequent
real input change. Native pending-upload/exactly-once/revocation and real-world
failed-dispatch versus producer-turnover phases independently qualify this policy.

Complete CPU fallback, staged APPEND CPU exceptions, generation/ownership/provenance,
exact frusta,512 sections/1024 commands, upload/barrier/fence retirement policy remain.
This is internal readiness of the bounded supported paths, not representative RX
performance, arbitrary mod support or release completion.

## October 21 — complete startup probe validation

A reliably captured complete local startup log passed the extended APPEND/native
assertions and both Gradle tests but exposed75 RAW hazards in independent voxel/
sparse-light/selector probes. Each shader uses atomicAdd/atomicOr on a header
initialized by vkCmdFillBuffer, while its existing barrier grants shader WRITE
only. Extend those existing buffer-specific output barriers to READ|WRITE,
matching the corrected production selector/result-header policy. Model-table and
vertex output buffers are writeonly and remain WRITE-only. The first rerun
reduced75 hazards to1: the persistent-vertex fixture also uses the sparse-light
shader atomic scratch header (not a writeonly vertex target), so that fifth
scratch barrier receives the same READ|WRITE correction. No oracle/result is
replaced, no validation error suppressed and no shader arithmetic changed. Rerun
all native startup/indirect/combined gates; prior exit0 is not qualification.

## October 21 — local synchronization validation follow-up

The newer local validation layer reports selector output binding 1 READ_AFTER_WRITE
from vkCmdFillBuffer to shader storage reads. section_select_probe.comp uses
atomicAdd/atomicOr on that cleared header; the existing output barrier admitted
only SHADER_WRITE. Add SHADER_READ to that same transfer-to-compute destination
access mask. Existing mesher result-header barriers already include both accesses
and need no change. This reuses the authoritative selector barrier, changes no
algorithm/allocator/queue or CPU fallback, and must pass real native validation.
The independently reproduced swapchain acquisition chain fix is recorded in the
current world-witness document. No validation warning is suppressed.


### Selector slot retirement correction (qualification pending)

Inspection found an eligible-adoption gap: lastDispatchFrame compared only a
numeric ring index. After unchanged cached draws span a full ring, a new generation
on that same retired slot is rejected despite no live descriptor use. CPU fallback
is safe but redundant and can repeat for intermittent views. Reuse alternatives:
main-submit counts distinguish epochs but do not themselves prove slot retirement;
renderer already owns exact fence-safe MemoryManager frame callbacks. Replace the
last-index sentinel with one fixed per-slot in-flight bit cleared by that existing
callback. Keep one dispatch per still-live slot, all barriers and lazy CPU recovery.
No new epoch/cache/scheduler or synchronization primitive is introduced.

The native raster test deliberately repeats unchanged output across one complete
slot ring before APPEND generation turnover, then requires same-slot GPU ownership
and no CPU command upload. The native selector also attempts a different genuinely
uploaded generation before frame retirement and requires rejection, unchanged-output
reuse and preservation of old exact output. Full native qualification is pending.


## October 20 — integrated consumer pixels and complete short traversal domain

Current qualified executable `9a6de848`, full #1082 / 39551989165 SUCCESS;
JAR 12324685470, logs 12323799769. ChunkArea traversal proof has explicit validity:
addSection establishes it; resetQueue and clearVoxels revoke it. The wrapping
WorldRenderer short stamp may equal -1; no short value means absent proof.
Graph visibility, provenance copy/compare and sparse active masks use the same
validity bit. This fixes accepted but empty GPU draws at the old sentinel value.
The native producer domain/reset/clear tests and production RegionDrawBatch.draw
pixels at -1 passed, including nonzero resident offsets, ordered atomic APPEND,
zero-exception APPEND, complete fallback/re-adoption and accepted CPU command
upload bypass. The fixture seeds resident vertices; actual world mesher/portal
pixels remain external qualification. Use the current GPU_FIRST_OWNERSHIP checklist
and GPU_GENERATED_WORLD_DRAW_WITNESS test package; historical checkpoints below
retain their original boundaries. Limits, barriers, fences and CPU recovery remain.


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

### Producer invalidation audit

| Transition | Production invalidation / admission boundary |
| --- | --- |
| Delayed CPU build result | TaskDispatcher queues publication; cancelled tasks and stale staged generations reject before publication. RenderSection preflight/completeness/recovery transitions advance generated revision. |
| Generated residency publish, commit, retirement | GpuTerrainOutputStore advances layer mesh revision; candidate fingerprints include representability and GPU vertex bound, even if CPU geometry has identical numbers. |
| CPU upload readiness | Immutable CandidateGeometry and per-layer geometry revisions change on pending/ready/reset. |
| Queued direct-seed status changes | RenderSection.setGraphInfo now revokes the area's queue proof in either direction; no cached table is accepted until fresh traversal. Same status does not revoke. |
| Area identity transfer | RenderSection.setChunkArea notifies the departing area, advancing ownership/input revisions and revoking its queue proof before reassignment. Same-area assignment does not revoke. |
| New traversal / recovery | resetQueue clears captured provenance and starts fresh producer audit; lazy CPU fallback remains authoritative on rejection. |

The new smoke begins with an initialized generated-mode candidate cache, checks revocation rather than relying on a rescan to discover mutation, and checks traversal recovery/destination independence. It exercises producer cache safety with complete CPU geometry; it is not an actual generated-world draw or portal pixel proof. Audit conclusions are limited to these paths, not proof that every asynchronous producer transition is covered. No new locks, cross-thread publication contract, selector ABI, GPU resource, queue wait or default-on feature was introduced.


## October 19 — producer-invalidation generated candidate reuse; full #1069 PASSED

**Qualified executable:** `41ba122c03509fbbe7d19b67d60dda48735a6723`; [full #1069 / run 39291766470](https://github.com/Vorith03/VulkanMod/actions/runs/39291766470) **COMPLETED / SUCCESS** on that exact executable. Forge distributable JAR artifact **12205981816**, smoke logs **12206581647**. Public offline/Forge package, native generated APPEND/REPLACE and indirect, terrain/particle/texture/screenshot/synchronization, both startup modes, post/depth, Create Chronicles with public Immersive Portals fixtures, and Crash Assistant gates completed successfully. Private resource packs were not exercised. Prior full #1068 executable `0f76caf7e5377ce27edebbfc23235c5daaa0168d` passed (JAR 12203627510; smoke 12202694551).

**Implemented:** `ChunkArea` now exposes a producer-maintained generated-state revision. Exact owned `RenderSection` transitions (CPU-complete flag, staged preflight/ownership, visible handoff, atomic APPEND commit, invalidation and complete CPU recovery) advance it. Existing `DrawBuffers` mesh and immutable candidate-geometry revisions cover GPU residency publication/retirement, CPU mesh parameters and readiness. `RegionDrawBatch.publishLiveCandidates` can reuse a same-mode generated candidate table when producer revision, owned-slot queue audit, mesh/geometry, graph/direct provenance, visibility and **exact culling volume** match; unchanged draws/traversals no longer construct/hash every active generated candidate. Changed ownership/readiness requires fresh per-candidate planning, and mid-build generated-revision changes fail closed. A mode discriminator blocks cross-mode cache reuse. `RegionBatchSmokeTest` verifies unchanged REPLACE bypasses the per-slot loop and APPEND-preflight transition rescans and revokes complete ownership. Existing native APPEND capacity/atomicity and CPU fallback remain unchanged.

**Evidence boundary:** The qualified suite establishes compilation/fixtures and public compatibility, **not** main-world generated command selection, raster pixels, a real Immersive Portals view, long churn correctness on the user's world, actual eliminated CPU milliseconds, added GPU/submission work or RX 6900 XT FPS/p95/p99 profitability. No actual-world witness marker or matched CPU/GPU screenshots were captured. Graph/mod traversal, producer queue audit, changed-snapshot numeric planning, legitimate CPU exceptions and incomplete-output recovery stay CPU-owned. All experiments remain default-off; 512-section/1,024-command limits, APPEND atomicity, generation/provenance, portal frusta, same-queue ordering and fence-safe retirement are unchanged.

**Preserved negatives:** #1067 failed `compileJava` before native testing (name collision; corrected before passed #1068); #1060 stale 512 limit and #1064 bounded-overflow shader regression were corrected before #1066. Earlier #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31` remain negative or limited as previously documented. Do not convert cancelled or failed runs to success.

**Next gate:** Use `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md` for *actual loaded-world* REPLACE/APPEND command-recording witness and matched CPU/GPU main-world plus genuine Immersive Portals screenshots. A log marker does not prove post-culling nonzero commands or correct pixels. If this external user-world/AMD evidence is unavailable, explicitly leave it pending and continue only demonstrably safe implementation. Audit producer revision completeness on newly discovered asynchronous transitions rather than assuming this native fixture proves all worlds. Preserve outstanding particle portal/mod callback, lighting/translucency/camera, resource reload/frame pressure, private packs and integrated profitability. No isolated owner benchmark before meaningful integration. **Every later session must update `docs/NEXT_SESSION_PROMPT.md` and finish with a NEW copyable handoff that commands its successor to do the same, perpetually.**


## Atomic APPEND indirect candidate/selector contract — full #1066

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

## Producer-owned candidate geometry and readiness — full #1046 qualified

**Executable:** `29a94f73c72ff30443ff12d45eb77c12805e4e17`, full **#1046**, [run 39205054428](https://github.com/Vorith03/VulkanMod/actions/runs/39205054428). **PASSED**. Forge JAR artifact **12165621719**, smoke logs **12165785846**. All public offline/build/package/both startups/native terrain/indirect/texture/particle pixel/synchronization, Create Chronicles/Immersive Portals and Crash Assistant gates passed; private packs skipped.

`DrawBuffers` upload, staged commit, reset and deferred parameter publication now publish immutable per-section/layer candidate geometry. Opt-in vertex-segment listeners publish pending/ready/reset transitions; staged replacement detaches the retired segment and binds the new segment. A current-owned-identity check advances only the affected area's layer revision. Changed-traversal candidate assembly consumes these snapshots instead of rereading mutable DrawParameters and testing every active vertex Segment. Pending snapshots may remain cached until an authoritative readiness event changes the revision; the old retry-every-draw Builder/hash/readiness scan is removed. Duplicate unchanged producer notifications reuse the snapshot. Numeric records are allocated only on actual geometry/readiness changes; empty geometry shares one record. All current production writers were audited; future parameter mutations must publish through this contract.

Graph traversal, pre-frustum graph stamps, direct seeds, owned-slot checks, queue-containment proof, active-slot serialization/fingerprinting, exact portal volumes, GPU generation/output validation, lazy CPU command fallback and candidate-v1 REPLACE/APPEND exclusion remain. This is less repeated CPU preparation for GPU-owned indirect commands, **not** a new GPU mesh algorithm or elimination of Java graph traversal. Default-off paths install no segment listeners or per-change snapshots. Snapshot allocation and producer callback costs are new and **unmeasured**; existing candidate preparation timing excludes those producer costs.

Local evidence: all 24 offline contracts passed with the available JDK 17; extracted production DrawParameters/Segment compiled and passed immutable numeric metadata, completion, repeated notification, reset, replacement and retired callback checks. Full CI passed the mandatory `VULKANMOD_GPU_CANDIDATE_GEOMETRY_PRODUCER_OK` in the native indirect run. Its real producer fixture covers pending/ready/reupload/reset, layer/stale-owner isolation and traversal snapshot reuse; the existing native staged transaction checks publication/detachment. No local Forge/native runtime or RX result is claimed.

**Disposition: qualified but unmeasured.** No RX adoption, CPU-time reduction, added GPU/submission cost, FPS or p95/p99 result is established. All pilots remain opt-in and the integrated GPU-first release is incomplete. Next substantial slices: genuine multi-sprite texture compute/shared resources; actual-world particle portal/mod/light/translucency/reload qualification; generated REPLACE/atomic APPEND-aware candidate ABI. Preserve #1041/#1042 negative descriptor evidence and do not request #1028 UUID `e05f2b31` again. `docs/NEXT_SESSION_PROMPT.md` retains the mandatory rolling handoff.

## Producer-owned GPU candidate queue containment — full #1045 qualified

**Executable:** `cb248683db075033f0bb28ad42e8882ebba3f6d4`, full **#1045** / [run 39201976864](https://github.com/Vorith03/VulkanMod/actions/runs/39201976864) **PASSED**; distributable Forge JAR artifact **12164426976**, smoke logs **12164093569**. Public offline contracts, Forge build/package/both startups, native GPU terrain/indirect and texture/particle pixels/synchronization, Create Chronicles/Immersive Portals and Crash Assistant gates passed; private pack fixtures skipped.

Production change: for opt-in GPU candidate provenance, `ChunkArea.addSection` now audits queued identity/owned-slot membership, recorded pre-frustum graph or direct-seed membership, and duplicate slot rejection as the traversal producer writes the CPU queue. An optionally tracked `ResettableQueue.contentRevision` detects bypassing `addSection/resetQueue`; registration replacing a queued owned identity revokes the producer proof. `RegionDrawBatch.publishLiveCandidates` checks this O(1) state **before cached GPU command reuse**, removing the previous O(CPU queued sections) per-changed-traversal containment scan. Its remaining producer-active slot walk still validates current owned identity, mesh parameters, upload readiness, graph/direct flags, and post-enqueue direct seed changes. Any incomplete/mutated proof invalidates GPU candidate reuse and lazily falls back to CPU commands. Native regression exercises direct seeds, off-frustum graph stamps, stale replacement, duplicate slot rejection, independent queue mutation and successful fresh traversal recovery. Default CPU-only queues retain revision tracking disabled.

**Disposition: correctness-qualified but performance unmeasured.** This eliminates CPU draw-time containment rework on the experimental GPU candidate path but does **not** eliminate CPU graph traversal, producer enqueue bookkeeping, active candidate slot parameter reads, or all CPU command fallback. It is not an RX FPS/p95/p99 result, proof of portal pixels, or authorization to enable by default. Particle actual portal callbacks/pixels/reload and frame pressure; texture multi-sprite descriptor/scratch compute; producer per-slot metadata/readiness; generated REPLACE/atomic APPEND candidate ABI and integrated RX adoption/net profitability are still open.


## Scope

This checkpoint advances the Phase 7 section-selection track from persistent GPU
shadow output to a default-off production consumer. It does **not** bypass Minecraft
terrain meshing: geometry and draw metadata are still produced by the established CPU
terrain path. The new GPU work selects among that already-built region geometry and
can supply the indexed-indirect command buffer for a region/layer draw.

Source commits:

- `d91de023159f7a22a2b49f00392ca6e133cfd095` — `gpu terrain: gate GPU-selected indirect draws`;
- `67dafa92c5a9f568616e10a54c504d9285f8f514` — `gpu terrain: verify indirect draw fallback contract`.

## Production ownership gate

GPU-selected indirect commands are consumed only when **both** restart-time system
properties are enabled:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
```

The first property retains the existing persistent compute/shadow path. The second is
a separate ownership switch; normal builds remain CPU-driven when it is absent.

`RegionDrawBatch` continues to build the CPU `FrameBatch` before considering the GPU
buffer. A GPU draw is allowed only when all of the following are true:

- the production draw gate is enabled;
- a persistent shadow store exists;
- the current candidate table exists;
- the store has successfully dispatched or already contains that exact table generation;
- region X/Y/Z and generation match the current candidate table;
- the candidate count is not smaller than the authoritative CPU draw count;
- the candidate count does not exceed the persistent output capacity;
- the authoritative CPU draw count is nonzero.

Any failed condition uses the already-built CPU indirect buffer for that draw.

## Bounded command consumption

The compute output remains a fixed 512-command-capacity `StorageIndirectBuffer` with
a four-word diagnostic header. Before each new dispatch the full output is zeroed,
then selected commands are compacted from slot zero. The production draw begins after
the header and issues at most the candidate-table count, never more than the store's
capacity.

The CPU does not synchronously read the GPU-written count. Unused compact slots are
zero commands and therefore no-op draws. This preserves asynchronous queueing while
remaining bounded. The existing Vulkan smoke proves the zero tail, exact five-word
indexed-indirect metadata, full-capacity behavior and no overflow for the production
storage layout.

## Synchronization and lifetime

The persistent selector dispatch runs in a helper command buffer on the graphics
queue. It retains the existing barriers:

1. prior indirect reads -> transfer writes before reusing the output;
2. transfer writes -> compute reads/writes for candidate/parameter/output data;
3. compute writes -> indirect-command reads before the later main-frame draw.

The helper submit occurs before the main-frame submit on the same `VkQueue`; normal
queue order plus the main frame fence owns retirement. A frame-slot parameter buffer
is never rewritten after a helper submission from that same frame slot. If a newer
generation cannot be dispatched safely in the slot, that draw falls back to CPU.

## Regression contract

`RegionBatchSmokeTest` now exercises the same pure host decision predicate used by the
production consumer. It requires CPU fallback for:

- a disabled production gate;
- invalid/stale GPU output;
- a candidate set smaller than the CPU draw set;
- a candidate set larger than output capacity;
- an empty authoritative CPU draw set.

It also proves that an exact bounded set and a bounded superset are eligible for GPU
consumption. The Vulkan shadow smoke remains the independent proof that the buffer
itself contains exact commands and a zero tail.

## CI evidence

- CI #443, run `34925846288`, passed the complete workflow at `d91de023`: build and
  distributable verification, both Vulkan startups, persistent GPU indirect shadow,
  post-chain, depth post-chain, screenshot, FTB Library, Crash Assistant, Chat Heads,
  Flywheel, logs and artifacts.
- Its persistent shadow smoke emitted
  `VULKANMOD_GPU_INDIRECT_SHADOW_SMOKE_OK` after producing **99 exact commands from
  512 candidates**, with compute-to-indirect synchronization and zero-tail output.
- CI #444, run `34927562475`, validates the host fallback-contract commit
  `67dafa92`. Treat the gate as verified only if the live workflow conclusion remains
  green; live Actions evidence supersedes this document if it differs.

CI does not exercise representative Create Chronicles world rendering with the
production ownership gate enabled. It therefore does not establish live section-set
correctness or performance on the RX 6900 XT.

## Phase 7 interpretation

The **bounded GPU indirect-command + fallback** mechanism is now implementation- and
CI-verifiable, so that roadmap gate may be closed after #444 is green. The separate
GPU visibility/section-selection gate remains open until representative live RX 6900
XT diagnostics show no unresolved CPU/GPU selection mismatch.

This checkpoint does not claim:

- reduced CPU chunk-meshing work;
- correct GPU selection for every Forge/modpack camera edge case;
- a frame-time or FPS improvement;
- readiness for default enablement.

The known unusual case where traversal is seeded while the camera is outside normal
build height should be included in real-hardware validation before closing selection.

## RX 6900 XT correctness test

Use the CI artifact containing `67dafa92` and add these JVM properties:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
-Dvulkanmod.debugGpuSectionSelection=true
-Dvulkanmod.debugGpuSectionSelectionSamples=8
```

Then, in the normal Create Chronicles test world:

1. load ordinary terrain at normal build height;
2. rotate the camera through full turns while moving across several region boundaries;
3. perform a short fast spectator-flight/chunk-churn pass;
4. if practical, move the spectator camera above normal build height, rotate/move,
   then return to ordinary terrain;
5. watch for missing terrain, popping/holes, stale chunks, flicker, device loss or a
   visible difference from the CPU-driven build.

Return the relevant `latest.log` lines with:

```bash
grep -E 'VULKANMOD_GPU_(INDIRECT_DRAW_ACTIVE|LIVE_SECTION_SELECTION_(OK|MISMATCH|ERROR)|INDIRECT_SHADOW)' latest.log
```

Success requires the `VULKANMOD_GPU_INDIRECT_DRAW_ACTIVE` marker, representative
`VULKANMOD_GPU_LIVE_SECTION_SELECTION_OK` samples, no unresolved mismatch/error, and
no visual terrain regression. If any mismatch or visual defect appears, disable only
`vulkanmod.experimentalGpuIndirectDraw` to restore CPU production ownership while
retaining the shadow/diagnostic path for investigation.

## Next boundary

If RX selection/draw correctness is clean, record that evidence and close the live
selection gate. Performance should then be measured A/B before considering this path
for default use; issuing bounded zero-tail commands may have different costs across
drivers even though it avoids a synchronous count readback.

The larger project priority remains hybrid GPU terrain construction. Lighting-demand
telemetry still needs representative Create Chronicles section evidence before a
production lighting input encoding is chosen, and `CPU_REQUIRED` remains authoritative
for GPU-meshing qualification/fallback.


## Sparse producer input amendment — October 18 full #1040

Executable `1b182d53a18c0b83c5b4c1c52f94c08987ad5a1c`, full [#1040 / run 39192146854](https://github.com/Vorith03/VulkanMod/actions/runs/39192146854) passed public offline/native/Forge/compatibility gates (JAR artifact 12160493015).

On changed traversals candidate v1 input now contains **only** producer-stamped graph visits before frustum rejection and separately marked direct seeds. This is a superset of the eligible CPU draw queue, **not** the CPU frustum-approved queue and **not** the entire owned 512-section region. The shader predicate requires graph-visible or direct-seed eligibility; inactive owned slots cannot emit commands. Ordered packed-section IDs, flags, count header, max 512 capacity and selection shader ABI remain unchanged. A CPU queue-to-producer containment check, owned identity check, active-slot readiness, changed mesh/queue/graph/direct masks, ring generation and exact portal culling volume preserve fail-closed CPU fallback. A stale graph bit is not treated as ownership; untracked queued entries reject GPU commands. This still cannot atomically represent generated REPLACE/APPEND terrain command pairs, so combined mesher/indirect consumption remains disabled. Java graph traversal remains CPU-owned; RX profitability unmeasured.

## October 19, 2026 successor: generated-world recording witness

The historical 512-command and APPEND-exclusion paragraphs above describe the original single-command candidate contract; the current default-off generated REPLACE/atomic APPEND selector has 1,024 commands, as qualified in full #1066. Executable `2f8a45397639d8a616db3b0e08adc915a08a50b8` adds optional live-world `vulkanmod.debugGpuGeneratedWorldDraw` reporting after `vkCmdDrawIndexedIndirect` records the current generated candidate-backed output. It separates REPLACE, paired APPEND and zero-exception APPEND and labels the actual Immersive Portals render context where available; it is not GPU-command readback or pixel comparison. CI #1067 initially queued. The nonresident fast reject avoids constructing `DrawPlan` in candidate publication while preserving fail-closed CPU fallback. See `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md` and the latest `AGENT_STATUS.md` for evidence gates.

## Updated validation checkpoint: #1067 failed; #1068 pending (October 19)

Full [#1067 / run 39286363394](https://github.com/Vorith03/VulkanMod/actions/runs/39286363394) **FAILED before native/Forge runtime**: public offline contracts passed, but `compileJava` rejected the newly added local `int generatedReplace` because that publisher method already has a boolean `generatedReplace` parameter. This is a **Java source naming error, not a negative GPU shader/pixel/synchronization result**. Artifact **12204160640** is failed-run smoke logs; **no #1067 JAR exists**. The targeted, behavior-preserving rename landed in corrected executable `0f76caf7e5377ce27edebbfc23235c5daaa0168d`. Full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) was **queued/in progress** at this documentation checkpoint, with no passed result or verified JAR/smoke artifacts yet. Until full #1068 succeeds, the last qualified executable remains `360434e664c13050e2eab1174673ed19525ed813` in full #1066 (JAR 12196457613; logs 12196891834). **Next: check #1068 conclusion and exact artifacts before claiming qualification; fix any subsequent actual failure step.** All runtime generated-world/portal pixels and integrated profitability remain open.


## October 19 — generated-world draw witness, full #1068 PASSED

**Current fully qualified executable:** `0f76caf7e5377ce27edebbfc23235c5daaa0168d`; full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) **COMPLETED / SUCCESS** on that exact executable (not the later documentation HEAD). Distributable Forge JAR artifact **12203627510**, smoke-log artifact **12202694551**. Successful full-validation steps included offline contracts, Forge compilation/package, both client startup modes, native GPU indirect/terrain (including `VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK` 509-pair selector proof and `VULKANMOD_GPU_GENERATED_REPLACE_CANDIDATE_OK`), texture multi-dispatch, particle, screenshot, synchronization/post/depth chain, public Create Chronicles with Immersive Portals, and Crash Assistant. Private resource-pack fixture coverage remains unavailable (token absent); no actual-world generated-terrain/portal pixel validation was performed by this run. Previous qualification #1066 `360434e664c13050e2eab1174673ed19525ed813` remains the regression baseline (JAR 12196457613, smoke logs 12196891834).

**Actual-world observability introduced, NOT an observed world result:** opt-in `-Dvulkanmod.debugGpuGeneratedWorldDraw=true` adds `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` only after a live loaded-world GPU-owned `vkCmdDrawIndexedIndirect` recording from current generated candidates; REPLACE, atomic APPEND pair and APPEND with no CPU exceptions are separately inventoried, including optional real Immersive Portals portal-view context. Native fixtures do not produce this marker; it does not prove the GPU selected a nonzero command after culling, actual geometry/pixels, or CPU/GPU visual equivalence. **No real-world marker/screenshot exists in this session.** The same executable skips temporary CPU `DrawCommand`/`DrawPlan` construction for missing/stale/wrong-generation GPU output during generated candidate preparation, retaining unchanged CPU completeness and fallback. Active CPU graph traversal, hashing, admitted per-candidate command planning, CPU recovery and queue/fence-safe retirement remain. Net CPU/GPU costs, adoption and RX FPS/p95/p99 are **unmeasured**; all pilots stay default-off.

**Preserved negative evidence:** full #1067 / run 39286363394 failed in `compileJava` due a newly introduced local `generatedReplace` count colliding with the method's boolean parameter; no #1067 JAR, smoke-log artifact 12204160640, offline contracts passed but all native/Forge runtime tests were skipped. Exact rename in `0f76caf7e5377ce27edebbfc23235c5daaa0168d` restored compilation; #1068 subsequently passed. Keep #1060 (obsolete 512 assertion), #1064 (bounded-overflow shader regression), older #1054/#1050–#1052/#1047/#1048/#1041/#1042 and analyzed #1028 UUID `e05f2b31`. They must not be rebranded as passed.

**Next action:** obtain actual generated-world REPLACE and APPEND production-consumption witness and controlled main-world/Immersive Portals CPU-vs-GPU scene screenshots using `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`, or explicitly retain the external world/AMD hardware gap. Do not demand isolated hardware owner benchmark before integrated verification. After real-world geometry and portal proof, safely eliminate more repeated numeric CPU planning using a complete producer invalidation contract; preserve atomic APPEND fallback and actual-world particle portal/mod callbacks/translucency/lighting/camera/reload/frame-slot gates. Integrated release remains **incomplete**. Every following work session must refresh `docs/NEXT_SESSION_PROMPT.md` and finish with its own new copyable rolling handoff prompt perpetuating that requirement.

