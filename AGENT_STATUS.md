# VulkanMod Forge 1.20.1 — Agent Status

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
views despite oversized unused buffer-growth tails; that final guard needs its own
full CI before final-tip qualification. Do not promote defaults or request RX tests.

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
