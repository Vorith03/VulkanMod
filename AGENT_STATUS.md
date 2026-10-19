# VulkanMod Forge 1.20.1 — Agent Status

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
