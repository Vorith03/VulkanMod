# VulkanMod Forge 1.20.1 — Agent Status

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

**Validation pending:** #1029 passed admission policy but an extracted tick
fixture lacked its new dependency; #1030 found the corresponding attribution
fixture gap. Both fixed, negative evidence preserved in the ownership doc.
Local JRE lacks javac, Gradle download unreachable; full
native/Forge CI required. No measured coverage/net benefit for the new pilot,
no default promotion or integrated release qualification. See
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md` and new active roadmap sequence.
Continue texture compute aggregation, then qualified particle preparation and
existing terrain execution ownership; do not declare the multi-subsystem mission
complete after this admission slice. No new owner test needed at this boundary.


## #1028 GPU texture copy queue bound — full CI qualified (2026-10-18)

After owner #1026 diagnostic disproved native particle resize growth in the measured render and invalidated its zero capacity-check counter, #1027 removed the probe (full Forge/native CI passed, run 39143353962 at executable `df07fc7b`). The independent #1026 texture profiler shows 114,177 flushes caused by the unified queue's 128-region cap and 8.289 ms/tick inclusive copy flush cost. A **pilot-only 128→256** capacity experiment changes only that integer bound and sizes its existing fixed scratch arrays. The legacy 32-region queue, source ordering/overlap safety, staging budget, barriers, GPU compute and custom pixel generation remain unchanged. The real production-method contract and native Vulkan pixel/synchronization smoke now exercise 256 capacity and 288 writes with overwrite/retirement. Full [#1028 Forge/native CI](https://github.com/Vorith03/VulkanMod/actions/runs/39143814563) **passed** on qualified executable `50f39eb50dda7e71ac0b726495183218e70b69f7` (JAR artifact `12142713171`). Offline production-method contracts, Forge package, two Vulkan startups, indirect, post/depth, screenshot pixel/sync with the 288-column native unified-copy test enabled, public Create Chronicles and Crash Assistant all passed; optional real resource packs skipped. **Disposition: correctness-qualified, RX speedup unmeasured.** A larger queue may increase per-epoch overlap scan CPU. Next: one new #1028 180-second RX capture on the previous world snapshot and flags, compare to existing #1025 without a new baseline/OFF run. Check region-limit drains, true copy commands, inclusive texture flush/tick/client tick, separately scoped GPU times, FPS and visual anomalies. See `docs/TEXTURE_COPY_REGION_BATCH_CAP_1028_2026-10-18.md`.

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
