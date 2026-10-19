# GPU terrain indirect-draw handoff — 2026-09-15

## Generated REPLACE indirect ownership — executable published, full #1059 pending

Executable `f9d581ba6a3a9136bd47166e04d5f26e4fda2995`, [full #1059 / run 39239916771](https://github.com/Vorith03/VulkanMod/actions/runs/39239916771): **pending** at this checkpoint; no new qualified JAR/log artifacts yet. Previous qualified executable remains `29e95e5c31221276dae83d65363407f0c43f740c`, full #1058, JAR **12178863678**, logs **12178788575**. All 24 local offline contracts and a temporary extracted production REPLACE-helper/existing-handoff-planner Java contract passed. Local `agent-check.sh` could not download Gradle (`Network is unreachable`); this is not local Forge compilation.

New default-off `experimentalGpuGeneratedIndirectReplace`, together with terrain draw handoff, GPU indirect commands/draw and indirect CPU bypass, allows the existing candidate-v1 path to represent exact-generation **single-command REPLACE** alongside ordinary complete meshes. It reuses the established handoff planner and GPU selector, keeps graph/direct provenance, queue containment, exact culling volume and region ownership checks, and prepares auto-quad indices on GPU command ownership. No new wire ABI/shader or parallel renderer. Any active APPEND (even zero CPU exceptions), missing GPU half with incomplete CPU geometry, stale/retired residency, incomplete provenance, unavailable output or capacity failure retains the existing lazy FrameBatch/hybrid fallback. No partial APPEND is exposed.

Generated ownership/completeness does not yet have the ordinary candidate producer's full invalidation contract, so that opt-in combined path **re-evaluates active plans every draw** before fingerprint reuse. This adds CPU planning/record allocation/hash cost; ordinary cached paths retain their existing behavior. Accepted indirect ownership bypasses duplicate `FrameBatch.update/rebuild` and command upload, **not** Java graph traversal, per-candidate handoff planning, serialization or all CPU graphics calculations. Per-section numeric command planning now runs in candidate preparation; its computation was not eliminated. Classify CPU displacement as incomplete, not as all command construction removed. Ordinary-mesh live selection diagnostics are not used as a generated-ownership oracle.

The mandatory native probe `VULKANMOD_GPU_GENERATED_REPLACE_CANDIDATE_OK` checks the production candidate resolver/publisher, exact GPU numeric fields/section ID, pending/incomplete rejection, APPEND revocation without ordinary mesh invalidation, restoration, retirement and lazy FrameBatch bypass. Until #1059 completes it is **implemented, not native-qualified**. Even a pass is bounded fixture/metadata/control-flow evidence, not a real generated-world draw, portal pixel result, normal workload adoption, GPU/CPU cost or RX FPS/p95/p99 improvement.

Particle inspection found no new independently actionable defect in the available source/evidence. Actual Immersive Portals particle scenes, real mod callbacks/pixels, live reload/re-entry and sustained gameplay pressure remain unresolved; no artificial substitute was claimed. Work therefore advanced terrain under the owner's mandatory Priority B rule.

**Next:** inspect #1059 and repair any real failure; then extend the existing candidate/selector ABI to atomically represent CPU-exception + generated APPEND pairs with bounded capacity, generation/lifetime/queue/portal checks and lazy fallback. Add mixed ordinary/REPLACE/APPEND native consumption evidence and real-world qualification before promotion. Preserve #1054, #1050–#1052, #1047/#1048, #1041/#1042, and analyzed #1028 UUID `e05f2b31`. No per-slice owner benchmark; all pilots remain opt-in and the integrated release is incomplete. Every session must refresh the rolling handoff and repeat that requirement in its successor prompt.

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
