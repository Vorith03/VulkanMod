# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## #1023 bulk CPU interpolation: first OFF/ON control favors ON — 2026-10-17

Owner #1023 OFF UUID `5c61d553` (benchmark/JFR/latest/debug) is received.
Same qualified executable `bd2cc13a` and nominal identical RD32/2552×1374
workload/GPU flags as ON UUID `252df7a2`: production bulk count
**0 OFF versus 2,768,429/2,768,429 ON**. Both captures complete
normally. Texture CPU **17.984 OFF vs 14.704 ON ms/tick**;
non-upload remainder **10.684 vs 7.282**; full client tick
**30.264 vs 26.809**. Full capture **6,666 vs 7,825 frames**
(~37.03 vs ~43.47 FPS, **17.4% favorable ON**).
GPU main graphics (3.191 vs 3.173 ms/frame) and texture upload
(3.218 vs 3.207 ms/batch) near-equal. OFF JFR has 2,348 relevant
texture pixel/transfer category samples vs ON 1,249, corroborating
reduced CPU loop work. See
`docs/RX_RD32_CAPTURE_1023_BULK_CPU_AB_2026-10-17.md` for
metrics, hashes, GPU/JFR validity, workload comparisons and cautions.

**Disposition:** **conditionally accepted matched OFF/ON pair 1/3**
on the owner's instruction to assume restored benchmark-world snapshots.
This is an explicit assumption, not a snapshot identity proven by logs.
Bulk CPU ON has substantial favorable texture/frametime evidence, but
the three-pair default-adoption gate remains open.
**Two further alternating restored-world OFF/ON pairs are needed**,
with unchanged #1023 JAR, GPU flags, modpack/settings and 10-ms JFR.
No repeat of pair one, no new JAR/GPU budgets, no default promotion.
Independent owner visual observations remain to be collected; the
separate Phase 5 OpenGL/Vulkan baseline gates remain open.

## Owner #1023 bulk CPU ON capture received; OFF control next — 2026-10-17

Owner UUID `252df7a2` benchmark/JFR/latest/debug is received and valid
for #1023 (`bd2cc13a`). Actual production bulk CPU adoption is
2,768,429/2,768,429 standard CPU interpolation updates.
Texture CPU = 14.704 ms/tick versus #1021 historical 17.950
(**unpaired, no speedup claim**); 7,825 frames/180.020 s,
GPU interpolation 3.88%, main graphics GPU ~3.17 ms/frame.
See `docs/RX_RD32_CAPTURE_1023_BULK_CPU_ON_2026-10-17.md`
for full metrics, caveats, independent JFR verification and hashes.

**Next:** same qualified #1023 JAR, CPU bulk flag **false** with
identical other settings, source world, GPU pilots and 10-ms JFR.
Three alternating restored-world OFF/ON pairs qualify formal
benefit. Do not repeat this unchanged ON run, change budgets,
enable a default, or build another JAR for the comparison.
Hardware adoption is measured; matched net benefit remains open.

## Bulk CPU interpolation qualified; RX comparison next — 2026-10-17

Executable `bd2cc13afb74b6468acd586e24729e4795d1924a` passed full CI **#1023**:
run `39073019680`, job `117514597700`. All 23 contracts, Forge production
packaging, pinned animation/portal anchors, both startup configurations,
indirect/post/depth, native screenshot pixels/synchronization, public Create
Chronicles and Crash Assistant passed. JAR/log uploads passed; private packs
skipped. Qualified artifact `12116211650` is
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1023-gbd2cc13a-all.jar`.

The opt-in `vulkanmod.experimentalCpuSpriteInterpolation=true` path replaces
the measured standard CPU pixel loop with validated native rows after GPU first
refusal. Exact RGB double/truncation/current-alpha pixels, source mips,
original uploads/clocks/visibility, source mutation and custom/invalid/off-thread
fallback remain. No GPU budget/admission or default change. The actual transformed
ticker/native oracle exercised 56 bulk frames across five standard cases and
zero on the custom subclass, with all-mip pixels, hidden/refresh cadence and
clocks matching the unchanged vanilla reference. Three corrupted local kernels
(alpha, arithmetic, odd mip offsets) fail the independent CPU oracle.
Initial #1022 failed compileJava because an invoker shared MSpriteContents'
injection refmap namespace; #1023 qualifies the separate registered invoker.
Source changes were explicitly owner-authorized and published through the
connected GitHub API because shell Git has no credentials. Local pre-API
commits `909497a`/`e1c332d` are historical; the qualified remote executable above
is authoritative.

Owner #1021 UUID `f4741fc5` + JFR/latest/debug are received: 439 traversals / 6,457
valid frames, 785 cached admissions, retained 1,604 pending dirty. Terrain setup
averages 0.907 ms vs historical 4.221 ms: adopted and favorable screening, **not
matched net-benefit proof**. Textures remain 17.950 ms/tick; GPU interpolation
covers 2.92%, with disabled residency dominating fallback. Do not repeat unchanged
#1021. See `docs/RX_RD32_CAPTURE_1021_AND_CPU_INTERPOLATION_2026-10-17.md`.

Next: same-JAR #1023 control/candidate with ONLY the bulk CPU flag false/true,
all prior flags/settings and 10 ms JFR sampler retained. Return UUID logs/JFR,
latest/debug and animation observations. Read bulk-update numerator/CPU-update
denominator, complete texture/client-tick time and frame distributions. A pair
screens; three alternating restored-world pairs qualify formal benefit.
Hardware adoption/net benefit remain **unmeasured**; do not enable the default.
Instructions: `docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`. Particle/block-entity/server
work stays separate; reload/re-entry and private-pack qualification remain open.

## Owner #1020 evidence and terrain repair fully qualified — 2026-10-17

The requested changed-executable capture is received: UUID
`e12d7c14-9f90-4c41-b213-96606e05a272`, #1020 / `e0807c62`, benchmark + raw JFR
+ latest(10)/debug(7). See
`docs/RX_RD32_CAPTURE_1020_AND_TERRAIN_REPAIR_2026-10-17.md` for hashes,
analysis and the implemented scheduler repair. Prior instructions to collect
the first #1020 capture are superseded. Source-switch drains are zero and queued
copy commands fall ~55.6% versus the historical #1019 capture, but textures still
cost 17.556 ms/tick and inclusive flush CPU 8.176 ms/tick. No paired CPU speedup
is established; O4 coverage remains low (4.17%).

The stationary capture has 4,997 graph traversals/4,997 baseline-valid frames,
zero cached schedules and 1,604 pending dirty. The 512-entry cutoff guarantees
cache rejection. The repair validates real graph membership regardless of backlog
size, retains unready notices in place and bounds attempted admissions. A cap-only
edit exposed a reproducible concurrent-set iterator spin on remove/reinsert; that
negative result is preserved in the evidence document. Exact membership, topology,
capacity, neighbor and dirty-retention rules remain. All 22 offline contracts pass;
final focused production scheduler/ring/population tests pass, including a
stale-notice/redirty race. Full CI **#1021** at executable
`8ff928dd02deb6ad2d9098210a82fa23bfdbf5a6` is **green**: run `39070465187`, job
`117507019602`. All 22 contracts, Forge production packaging, animation/portal
anchors, both startup modes, indirect, post/depth, screenshot pixel/sync,
Create Chronicles, Crash Assistant and artifact uploads passed. Private packs
were skipped. This supersedes local Forge download blockage for qualification.
Qualified JAR: `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1021-g8ff928dd-all.jar`,
artifact `12115062566` on that run. Large-backlog behavior is covered by the CPU
production-method fixture; representative RX adoption/net benefit is unmeasured.

Next: one changed-executable #1021 RD32 capture using the new first section of
`docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`. Test whether stationary traversal
and terrain-setup CPU cost fall while dirty maintenance is retained. Other graph
invalidations may coexist; no guaranteed traversal-elimination or FPS claim.
CPU texture interpolation remains a major unresolved cost. Do not request another
unchanged #1020 capture, raise memory budgets or enable pilots by default.

## Source-switch texture repair fully qualified — 2026-10-17

Implementation `e0807c626bfa27416ebadc53eddff519f9ac29ef` is qualified by full
public/native CI **#1020**, run `39038204099`, job `117412382928` (**all green**).
All 22 offline contracts, Forge compilation/production packaging, pinned
animation and portal anchors, both Vulkan startup configurations, indirect,
post/depth and screenshot pixel/sync gates, combined Create Chronicles and
Crash Assistant passed. Qualified JAR and logs uploaded; private-pack fixtures
were skipped. Source-only/native-pending descriptions in the implementation
commit are superseded by this result. No RX net benefit is claimed.

At base `504617c`, the unified queue required one source handle for all pending
regions, causing 88.23% of #1019's flushes. Both CPU interpolation and resident
copy stacks reached Vulkan command recording through that source-switch drain.
The repaired common CPU/O3 queue retains each region's source handle and emits
one command per source within a bounded pairwise-disjoint epoch. All overlap,
compute/direct/layout/reset/lifetime boundaries retain draining and dependencies.
One barrier orders the epoch instead of barriers between disjoint source groups.
No residency budget/policy, default, shader, submission or wait was added.

See `docs/TEXTURE_SOURCE_GROUPING_REPAIR_2026-10-17.md`. Local production-method
regressions fail on the original selector and pass after repair. Executed counts:
128 disjoint alternating two-source writes require **2 commands/1 barrier instead
of 128/128**; 100,000 randomized writes match sequential pixels. These are
operation counts, **not measured frame/tick time**. The native test passed 160
alternating CPU/two-resident-source pixels, asserted pending counts, cross-source/
capacity/direct overlaps and early source retirement. Existing O3/O4 boundaries
and mutation/fallback/ticker/mip oracles also passed. Local Forge configuration
remains unavailable due the network-blocked Gradle 8.1.1 download; CI provides
Forge/native qualification.

**Next action:** the first changed-executable RD32 capture with #1020 using
`docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`, returning UUID log, neighboring raw
JFR and latest.log plus animation observations. Keep both pilots enabled and
the #1019 settings (2552x1374, simulation distance 12, same camera/sampler).
Check `copy_commands`, regions, full texture/client tick CPU and upload GPU time;
`copy_flushes` now counts epochs, not commands. No net-benefit verdict from fewer
flushes alone. The exact test-ready artifact is
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1020-ge0807c62-all.jar`, artifact
`12103455469` on run `39038204099`. For a formal speedup claim use three matched
#1019/#1020 pairs from identically restored worlds with both pilots enabled in
both. Candidate-only capture establishes adoption, not paired time benefit.

Low O4 coverage under first-admission/permanent-disable residency, persistent
terrain dirty traversal, campfire particle costs and integrated-server load
remain independent work. Preserve the first comparison before combining those
changes. Reload/re-entry and private-pack qualification remain open. The owner
#1019 capture is already received; the source-review-only direction below is
historical and superseded by this implementation/qualification.

## Owner #1019 RX capture received and analyzed — 2026-10-17

The requested single RD32 capture is now available: UUID
`6c29da16-40b9-4f37-8e78-aa1180197560`, benchmark + raw JFR + latest/debug.
Analysis base is `d27f762abe061c90ee3bcff00ccd329b0bc03b8a`, documentation-only
above tested executable `ce9d66e1219e13139e3b0b6b7de531082f7a04c7` (#1019).
The live #1019 job steps were rechecked green; private-pack fixtures skipped.
See `docs/RX_RD32_CAPTURE_2026-10-17.md` for evidence, scopes, raw file hashes,
source review and next decisions. No executable changes or new build here.

Capture completed: 4,772 frames / 180.012 s (~26.51 FPS), large early hitches,
late-window mean ~34.75 ms. Textures cost 17.787 ms/client tick. O4 reaches
108,800 / 2,870,199 attempts (3.79%); 2,755,020 CPU fallbacks are residency
disabled. Charged residency is near 128 MiB with 1,257 lifetime capacity
rejections, zero source/staging rejection/invalidation. The common mixed queue
is active, but 1,266,289 / 1,435,220 flushes (88.23%) are **source VkBuffer
switches**, not demonstrated staging reallocations. Total inclusive copy-flush
CPU is 8.154 ms/tick, not additive to texture time and not reason-specific.
Terrain traverses once per frame in all baseline-valid windows; 1,604 pending
dirty exceeds the 512-entry cached preflight. Campfire smoke dominates particle
tick calls; particle rendering also costs 4.453 ms/frame. JFR raw event counts
reconcile with the log; category shares require full caller chains. Server lag
persists. No paired speedup, visual correctness or absence of observer effect
is claimed; a historical slower aggregate is not a proven regression.

**Next useful action:** source-review a bounded common copy queue grouped by
source, preserving destination ordering/overlap, O4/direct boundaries and
GPU-safe lifetime. Raising region capacity alone does not address the measured
source switches; removing barriers or blindly reordering is unsafe. Separately
review fixed-budget first-admission/permanent-disabled residency policy before
choosing a coverage change. Do not raise budgets, enable defaults or create a
new GPU scheduler by inference. This analysis is complete; implementation and
qualification of any successor remain open. No repeat of the unchanged single
capture is needed. The existing same-JAR pilot-off control/three matched pairs
remain necessary for a performance verdict, not for this source review.
Reload/re-entry remains deferred. Older "next required capture" statements
below are historical and superseded for receipt of this one capture.

## Broad CPU graphics discovery fully qualified — 2026-10-16

Full public/native CI **#1019**, run `38972838076`, validation job
`117215415389`, passed at executable
`ce9d66e1219e13139e3b0b6b7de531082f7a04c7`. All 22 offline contracts,
Forge compilation/production packaging, animation source and portal anchors,
both Vulkan startup paths, indirect/post/depth/screenshot pixel/sync gates,
combined Create Chronicles, Crash Assistant and log/JAR uploads passed.
Private real-resource-pack fixtures were skipped. This supersedes #1018 as the
next hardware-test candidate. No RX performance or hotspot coverage is claimed.

The owner requested broad CPU graphics logs for selecting the next pass.
Existing exact phase/texture/particle route counters remain intact. New
benchmark-scoped Java 17 JFR execution/native-boundary sampling adds ranked
render/mod/graphics-worker call chains, graphics categories and explicit
unclassified/missing/truncated/overflow/observer counts to the benchmark log.
The raw rolling 64 MiB `*.cpu-graphics-*.jfr` sits beside it. Java execution
and native-boundary counts use separate denominators; they are statistical
samples, not exact method milliseconds, driver internals, GPU time, an
exhaustive operation trace or proof of offload eligibility. Unknown custom
worker pipelines can remain in raw background samples; stacks and retention
limits are reported rather than hidden.

Sampling defaults on only for automated profiling captures; ordinary gameplay
adds no per-draw hooks. One-time initialization is excluded from the profiler
and automated-controller deadline. Decode/ranking runs after stopping capture.
Missing JFR or I/O failure produces unavailable/failed metadata. Local Java 17
contracts exercise real record/decode, orderly-exit dumps, classification and
caps, cancel/output failures, absent-JFR fallback and deadline alignment.
Native startup additionally verified active JFR inside Forge and a nonempty
raw recording after JVM exit. Local Forge compilation remains unavailable due
the blocked Gradle distribution download; CI establishes that qualification.

**Next required action:** existing RX 6900 XT/RADV RD32 capture using #1019 and
`docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`. Return the UUID benchmark log, its
neighboring raw JFR and latest.log. Read CPU stack coverage alongside measured
stage/texture fallback costs; retain matching sampler settings for the same-JAR
pilot on/off comparison. Three matched pairs remain required for a formal
performance claim. No additional scheduler, budget increase or renderer default
adoption is selected before this evidence. Reload/re-entry remains deferred.

## Unified GPU texture pilot fully qualified — 2026-10-16

Full public/native CI **#1018**, run `38899462146`, validation job
`116968655497`, passed at executable
`db77ff5c5232e18eb6a696a691c5ae01ed660117`. This qualifies the accumulated
unified CPU/GPU atlas-copy queue, shared immutable GPU source pages, original
source/fallback attribution, and reverse-boundary ordering repair. All 21
offline contracts, Forge compilation/production packaging, pinned animation
source and packaged portal anchors, both Vulkan startup paths, indirect,
post/depth/screenshot native gates, combined Create Chronicles, Crash Assistant,
and log/JAR uploads passed. Private real-pack fixtures were skipped. #1017
was superseded/cancelled, not a failed qualification. Local offline contracts
also pass on Java 17; local Forge compilation remains unavailable because the
Gradle distribution download is network-blocked.

The source review found that O4's pre-copy atlas barrier could not order its
own scratch-to-atlas result against a subsequent queued overwrite. The opt-in
unified path now completes external O4/direct resident copies with the same
conservative transfer-write dependency as flushed groups. The native oracle
passed both O3-to-O4 and overlapping O4-to-O3 boundaries, repeated scratch use,
source-offset isolation, and all four exact atlas pixels under sync validation.
The stale upload-cleanup fixture was updated for reason-aware flush calls;
failed/nested scope cleanup assertions remain intact.

**Next required action:** owner RX 6900 XT/RADV RD32 capture using the qualified
#1018 JAR and the two explicit pilot flags in
`docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`. Read separate source/temporary upload
routes, O4 fallback buckets, queued/direct resident copies, flush causes, page
memory and CPU/GPU times. For net benefit, use the same JAR/settings with only
the two pilots disabled as control; three matched pairs remain required for a
formal performance claim. #1014 already completed and is historical context,
not a same-executable mixed-queue control. No new compute scheduler or default
adoption is selected before this evidence. Reload/re-entry remains deferred;
private-pack coverage, representative adoption and RX speedup remain unverified.
The source-only notes below describe pre-qualification history, superseded by
this result for native/Forge qualification only.

## Unified CPU/GPU atlas-copy queue — 2026-10-12 source-only continuation

Commits `65ebac2bdd13c2d1c83f93814282faceee54c42b`,
`2fbe5a4da81e78428037d6885f12a1793149168a`,
`1cc93e35f699ca5608a327b3ada48a79629ea612`,
`69fd3cdb4141cbf0551211b96f8c41009ca5f9fa`, and
`d63502604669ec138d977334f3810a7fac419584`
extend the existing experimental unified transfer queue to O3 GPU-resident
discrete sprite copies, not just CPU-staged uploads. They share one
`VkBufferImageCopy` emitter: a pending group has exactly one Vulkan image
destination and one buffer source; each region retains its mip, offset,
extent, `bufferRowLength`, and `bufferImageHeight`. Shared resident
page slices can combine into fewer transfer calls. Source/texture
switch, overlap, 128-region cap, and texture-tick end flush the group,
as does the existing pre-O4 compute boundary.

**Newly identified ordering hazard and fix:** the original pending-region
overlap check did not know about writes already recorded directly or
flushed because of capacity. In the opt-in unified path, every emitted
group, every direct CPU upload in a texture-tick scope, and every unqueued
resident write now establishes a conservative transfer-write-to-write
dependency before later writes. The native smoke writes 160 disjoint
pixels across the 128-region boundary, then overwrites both an earlier
flushed pixel and the original direct-upload pixel. The shared source
native smoke now records two O3 copies in one batch, then validates
their automatic flush into O4 compute and exact source-isolated pixels.

**Cost/measurement:** this conservative barrier policy can add GPU
serialization; the performance benefit and even net direction remain
unmeasured. The benchmark now distinguishes
`resident_queued_copy_calls/regions` from
`resident_direct_copy_calls/regions`, because the generic
`copy_flushes`/regions/timing includes GPU-resident queued copies
when the pilot is enabled, rather than CPU-staged copies only. Do not
compare these mixed queue denominators directly against #1014.

**Validation state:** source/diff audit and existing standalone region
geometry coverage only. No Forge compile, native Vulkan synchronization
validation, CI job, or new JAR has run; #1016 remains the latest
fully qualified executable under the existing owner hold. The next
necessary action is *one* full native qualification of the accumulated
pilot, including both screenshot/pixel oracles and compatibility. If it
fails, fix the actual build/Vulkan evidence; do not stack a second
compute shader scheduler on an unqualified transfer path. After
qualification, capture matched GPU adoption, copy flush causes, and CPU
and GPU frame times before considering a batched O4 compute kernel.

## Shared GPU animation source pool — 2026-10-11 source-only implementation

At source commit `76fce5f64fb52cf6a626c3028ba4fe1ee9151d0d`,
the opt-in `-Dvulkanmod.experimentalGpuTextureSourcePool=true` path
moves the **immutable O3/O4 source backing storage** out of individual
`TextureResidentBuffer` objects into shared 4 MiB (or dedicated larger)
device-local pages with aligned per-sprite leases. The **device's**
`minStorageBufferOffsetAlignment` governs slice boundaries. O3
`VkBufferImageCopy.bufferOffset` and O4 storage descriptor
offset/range/source validation now use the lease correctly, while all
existing shader mip-relative addresses and exact CPU fallback remain
unchanged. One 128 MiB physical-page cap prevents unbounded allocation;
the earlier 128 MiB per-source/scratch accounting budget is retained.
This is real shared Vulkan GPU storage, but **not** a deferred GPU
animation-job merger.

Safety boundary: source slices are append-only while the page is live,
and cannot be immediately reused after a sprite closes. The page is
retired via the existing upload-safe `TextureResidentBuffer.retire`
queue **only after all its source leases close**; charge/counter callbacks
wait for page retirement. This avoids GPU use-after-free and reuse races,
but incurs page fragmentation and can retain retired-source budget until
a whole page empties. Do not default-enable or call it an adoption win
without native and RX measurements. Transparent execution of arbitrary
Forge CPU pixel-generation logic remains out of scope.

A pure Java 17 page-layout contract passed locally, including
device-offset-style 256-byte alignment, underflow and non-reuse. The
registered offline suite now contains **21 contracts**.
The Vulkan screenshot native test is source-added and opts into both
the existing unified-transfer pilot and the new shared-source pilot:
two leases share one VkBuffer; two O3 copies and O4 interpolation from
the **second nonzero descriptor offset** are checked by exact atlas
readback, including Minecraft's preserve-current-alpha rule. Both
original pixel/mutation/fallback tests and Vulkan sync validation remain
mandatory.

**Qualification:** no full Forge Java compilation, native run, CI or
new JAR has occurred under the existing owner-directed pre-hardware
build hold. This is a source-only candidate, and the last qualified
executable remains **#1016**. New profiler fields report pooled-source
page/lease/capacity counters. At the next warranted build, the native
smoke must pass (including no validation hazards) before any owner RX
test. Only then decide whether shared per-atlas job batching is safe
and effective; do not stack unqualified high-risk Vulkan dispatch changes
on this until those boundaries are established.

## Whole-tick GPU transfer unification pilot — 2026-10-11

The owner explicitly prioritized a generic GPU texture-update architecture
over more sprite-by-sprite coverage exceptions. Existing code **already**
transfers all uploaded CPU pixel data via Vulkan and batches the entire
TextureManager.tick scope, including custom Tickables; however, it records
many small transfer command groups and O3/O4 requires separately admitted
immutable native animation source sheets. Arbitrary Forge/custom CPU pixel
generation, resource decoding, and source mutation cannot transparently
be replaced with identical GPU computation.

Pilot `0840db29c3a422b44333991d707a85084ad98947` extends that **shared**
transfer boundary rather than adding a sprite-specific rule. With the opt-in
`-Dvulkanmod.experimentalUnifiedTextureUploads=true`, the common upload
region ceiling grows from 32 to 128. Each pending image/mip destination is
checked for exact rectangle overlap. Overlapping copies flush first and
record a transfer-write-to-write pipeline barrier before the next write,
preserving the Vulkan no-overlap and WAW rules. Existing staging ownership,
source mutation checks, non-tick upload behavior and O3/O4 CPU fallback
remain unchanged. A Java 17 offline contract covers rectangle geometry;
the contract is added to the bounded CPU suite (20 total). The isolated
geometry policy was compiled/executed with Java 17 and passed in this
session; this is **not** a Forge compile.

Native test source commit `22f7cfb215a17573993217a9e30307bedf4b11f7`
adds a real Vulkan pixel/readback oracle for 48 independent subuploads
followed by a same-batch overlapping overwrite. The native screenshot
CI configuration opts into this pilot and requires the oracle's marker;
synchronization validation must stay active. The native test was **not
yet executed**.

**Validation and effectiveness:** source-only apart from the isolated Java
geometry contract; deferred Java/Forge/native qualification and RX A/B due
the existing no-new-JAR build hold. There is
no demonstrated GPU-speedup or guarantee of full GPU-resident texture
coverage. The next coherent work should address central *GPU animation
update scheduling and shared source residency*, rather than expanding
per-sprite eligibility exceptions; it must keep mod-defined CPU pixel
generation as an opaque input and bound GPU memory. Do not present this
transfer pilot as a completed GPU-animation redesign.

## Raw #1014 reconciliation and source-only attribution — 2026-10-11

The original owner-uploaded UUID benchmark log was recovered from the Project
Library. It confirms 180.028 s, 3,600 texture ticks, 7,647 frames, O3/O4
requested with float64 enabled, 16.113 ms/tick texture work, **1,009,923**
CPU sprite-copy flushes taking **5.727 ms/tick** (nested in texture time),
1,313,703 GPU/3,401,636 CPU mixed sprite-upload requests, and only
1,067 GPU/2,886,248 CPU standard interpolation attempts. Residency had 1,184
admissions, 541 capacity rejections, **0** source/staging rejections, 704
invalidations and a nearly full 128 MiB charged budget. The final terrain
counter was **9,698 cumulative traversals** (not capture-only), zero cached
schedules, stable 1,604 pending dirty; the live fast-path preflight is capped
at 512 notices. See `docs/OPTIMIZATION_EVIDENCE_LEDGER.md` for exact scope
and the competing hypotheses.

Source-only commit `f3a72701003fd285c3f37d8c96200f16a61325d1`
instruments the existing paths without moving them: separate source vs
temporary CPU uploads, disjoint first-failure O4 reason buckets, and
nonempty sprite-copy flush counts/regions by actual calling boundary.
The `attribution-capture-contract.py` harness covers capture gating and
reset for the new counters. Source/diff consistency inspection completed.
**No Java compile, native CI or new JAR was run for this candidate** in
accordance with the owner’s test-build hold; qualification and measured
effectiveness are explicitly pending.

Next: do not infer that all 5.727 ms of flush CPU can be eliminated without
preserving texture/GPU write ordering. Reassess the #1016 source-identity
fix with the new distinct-route attribution at the next justified hardware
capture; only optimize the dominant *measured* flush reason or O4 fallback.
Do not raise the 128 MiB cap, loosen Forge/custom-source gating, or claim
RX speedup before paired results. Existing full #1016 is the last
**qualified** executable, not the latest source-only candidate.

## Post-#1014 optimization effectiveness audit — 2026-10-10

**Full public/native CI #1016 passed** at executable
`f6fa3f71962a529441ee7be64d468605eb3f2d0a`
([run](https://github.com/Vorith03/VulkanMod/actions/runs/38118180872)).
All 19 offline contracts, Forge compilation/packaging, animation provenance,
Immersive Portals anchors, both Vulkan startup paths, indirect/post/depth,
native screenshot and O3/O4 forced CPU interpolation fallback, Create Chronicles,
Crash Assistant, and distributable/log uploads passed. Private real-pack
fixtures were skipped. Source is correctness-qualified; **no new RX gameplay
performance, route-adoption or matched FPS evidence** is established.

Earlier #1015 exposed a regression-test-only same-command-buffer transfer
write-after-write hazard when it forced CPU interpolation then immediately
overwrote the same atlas rectangle using O3. Full Vulkan sync validation
correctly caught it. The final test exercises the next discrete GPU copy
in a subsequent completed upload batch. Validation was not weakened and no
extra hot-path transfer barrier was added.

Owner's completed RX 6900 XT RD32 capture now supersedes the pre-hardware
checkpoint below. Its preliminary route counters show substantial CPU fallback
in O3/O4; **no matched speedup is established**. Consult
`docs/OPTIMIZATION_EVIDENCE_LEDGER.md` for exact source-counter denominators,
the verified retrospective, dispositions and next gate. Audit enforcement is
in `AGENTS.md` §6A and `ROADMAP.md`.

A code-level O3/O4 fallback boundary error was identified: interpolation's
temporary CPU mip outputs were treated as candidate original-resident source
uploads, potentially invalidating residency and permanently disabling that
sprite after a single CPU interpolation fallback. This session restricts
residency to the exact SpriteContents original `byMipLevel` array and adds
a forced-interpolation-CPU-fallback native smoke with a required completion
marker. **This correction is now native qualified in #1016 but remains
hardware-performance-unmeasured.** Preserve source mutation checks, fallback
behavior, 128 MiB budget, defaults and Forge/portal compatibility.

Next: reconcile the previous #1014 benchmark against its original UUID log
before treating numerical counts as authoritative. Distinguish source uploads
from interpolation-generated staging, classify valid source rejection versus
capacity and lifetime fallback, then select the narrowest outcome-changing
matched test if residual uncertainty demands one. Do not rerun the unchanged
#1014 candidate or claim an FPS improvement based on #1016 CI.

## Final pre-hardware qualification — 2026-10-10

The owner requested continuation until the next physical test is needed. The
bounded CPU pilot, cached-graph scheduling/backpressure, ring dirty ownership
and terrain sprite-pass coalescing are now the completed candidate batch.
Further optimization selection needs the converged RD32 O3/O4 capture; the older
#988 run cannot establish gains for these changes. The source-only build hold
has reached its stated exception: perform one full qualification/build for the
physical test, then request only the existing controlled capture in
`docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`.

All 19 offline contracts passed locally with Java 17. The terrain contract now
executes production SectionGrid.setDirty indexing/admission, checking recycled
slots, height/X/Z aliases, notification flags and all 600 valid fixture owners.
This supplements the coordinate-helper and actual cached-scheduler contracts;
it does not prove loaded-world/AMD behavior. Full Forge/native/combined-pack
qualification for the complete candidate passed in **#1014**, run
`38044340567`, full-validation job `114190874016`, executable
`e9f8591e2df44486cc504dfbb06bdf759ceed377`. All offline contracts, Forge
compilation/production packaging, animation-source/portal anchors, both Vulkan
startup paths, indirect/post/depth/screenshot native gates, combined Create
Chronicles, Crash Assistant and JAR/log uploads passed. Private real-pack
fixtures were skipped. The next required evidence is the owner's converged
RD32 capture; no further executable work is selected before that evidence.
Preserve O3/O4 defaults off and all existing accelerated-default/reload/re-entry
boundaries. Install `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1014-ge9f8591e-all.jar`
using `docs/GPU_TEXTURE_RD32_HARDWARE_TEST.md`; return the new UUID benchmark
log and latest.log, plus any animation artifacts or abort reason. No RX
speedup, convergence or offload-adoption claim is established by CI.

## Animated terrain sprite usage pass coalescing — 2026-10-10

With visible-only animated textures enabled, WorldRenderer.renderSectionLayer
previously traversed every visible section's animated sprite list again for
each terrain pass (solid, cutout, translucent, tripwire, etc.), despite all
passes observing the same current chunk queue. A per-WorldRenderer lightweight
gate now claims one sprite-usage traversal after each setupRenderer call; the
gate also resets on allChanged. This preserves ordinary and portal view
coverage, conservative unknown-sprite fallback, and first-pass timing.
An isolated Java 17 contract checks once-per-setup and reset semantics.
This slice was held source-only until the physical hardware-test build.
Full Forge/native/compatibility qualification is now green in #1014 above.
Actual CPU reduction has not yet been measured.

## Section ring dirty-notice ownership — 2026-10-10

The user explicitly prohibited building more JARs until the next physical
hardware test. These optimization slices were kept source-only with [skip ci].
The completed candidate reached that test boundary and was built/qualified once
in #1014 above. Earlier build #1013 (run 38035000308) passed all
native/offline/combined-pack gates at 865d10b0.

A source inspection found SectionGrid.setDirty accepted an X/Z modulus alias
without checking the live world-section origin and also wrapped Y by height.
An off-grid or recycled notification could invalidate a different resident
section and waste mesh work. The fix rejects out-of-height Y coordinates and
checks exact world origin before dirtying an X/Z ring slot. Camera relocation
already marks newly assigned sections dirty. A standalone Java 17 contract
covers negative coordinates, ring recycling, aliased X/Z, unaligned origins
and overflow. This is not a measured FPS improvement; complete Forge/combined
qualification is now established by #1014, with RX validation still required.

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
