# Initial terrain dirty coalescing — October 23, 2026

## Scope, evidence, and causality

The owner's October 23 initial-world capture on the internally qualified build
1104 reported first nonempty terrain command at ~7.240 s from the observed
main-world entry, command fence confirmation at ~7.888 s, a worst early frame
around 1,539 ms, and approximately 5.76 million `dirtyNotices` by ~5.7 s.
The same early capture included 64 scheduled builds, ~5.67 ms mean worker service,
~50.30 ms mean publication queue wait and no recorded GPU *input-page*
capacity rejection. These are observations of a **single owner diagnostic
capture**, not matched before/after timings, reliable first-presented-pixel
measurements, or proof of the cause of each stall. Command/fence arrival is not
on-screen usable terrain.

`WorldRenderer.setSectionDirty` increments `performanceDirtyNotices` before
`SectionGrid.setDirty` rejects stale/out-of-height/ring-unowned coordinates.
Therefore the 5.76 million notifications do **not** mean 5.76 million effective
`RenderSection.setDirty` invalidations. Counting them as saved invalidations,
GPU upload attempts, or proven CPU savings is incorrect. A sustained bulk
invalidator, a world-input delay, publication pressure, and GPU output allocation
pressure have not been causally separated by this capture.

## Bounded repair and preserved invariants

The code change began with `31e3876d` and was narrowed after live native
and source-contract feedback; qualified final executable source is
`b26385b4c1216c7c1191132507415417a2d2f12c`.

- `RenderSection` may skip repeated invalidation only when already dirty,
  still UNCOMPILED, with no rebuild task and with the virgin-only
  `initialDirtyCoalescible` permission intact. Preserve player-change intent
  and the original rebuild request.
- `createCompileTask` revokes that permission before region capture.
  `TaskDispatcher.scheduleSectionUpdate` revokes it before enqueueing *any*
  publication, including legacy/unversioned results.
- Publication admission and the shortcut use the same section monitor.
  Ordinary dirtying continues to invalidate generations and discard stale GPU/CPU
  output; the callback to the world renderer remains outside the lock.
- Ring `reset` **never reopens** the permission after first producer admission.
  Initial camera positioning before any producer is still permitted to coalesce;
  reusing a previously admitted ring owner is not.
- All disabled paths, APPEND/REPLACE atomic handoff, complete CPU recovery,
  normal generation rejection and experimental-default-off behavior remain
  unchanged. No allocator, GPU output budget, scheduler or culler was replaced.

## Exact integrated qualification — build 1110

[Workflow #1110](https://github.com/Vorith03/VulkanMod/actions/runs/39995855963)
is SUCCESS, including the dependent published-byte audit.

- Executable commit: `b26385b4c1216c7c1191132507415417a2d2f12c`.
- [Published JAR artifact](https://github.com/Vorith03/VulkanMod/actions/runs/39995855963/artifacts/12520408712):
  `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1110-gb26385b4-all.jar`;
  11,135,757 bytes, audited SHA256
  `c0a076889e7cda16dd50a1f653cff0e6731fe955b7eafaa58599b0f49a6e2e74`.
- [Published audit](https://github.com/Vorith03/VulkanMod/actions/runs/39995855963/artifacts/12521317020)
  checks Java 17, MC1.20.1, Forge47.3.0, packaged mixins/native modules,
  CRC, original 18 exact public main/portal pixel pairs and zero
  validation/synchronization errors. One-page GPU input retirement passed.
  Native terrain publication drain, stale result rejection, loaded-world
  CPU/GPU ownership/displacement, and portal/lifecycle tests passed.
- Three private resource-pack steps SKIPPED, not passed. The observed CI
  global input charge peaks at 2 MiB and returns to zero; these tiny software
  captures do not represent D32 memory, output geometry or driver heap.
- Earlier #1105–1108 failed on standalone test initialization, the original
  stale-publication smoke invariant, or a source-contract signature; #1109
  was cancelled/superseded. Do not call any of those qualified.
  #1110 qualifies the corrected code and unchanged owner safety boundary.

## Remaining evidence / next action

**Implementation/correctness qualification complete. Performance mission OPEN.**
Neither 2–3-second nearby appearance nor 5-second intended-view coverage,
first usable *presented* pixels, a frozen converged section/layer denominator,
all-required world-input arrival, nor net hitch/CPU/memory benefit is proved.

Use [the owner package](TERRAIN_INITIAL_OWNER_TEST.md) with the immutable
`VulkanMod_Benchmark` save, identical D32/S12, 2552×1374 and fixed view.
The new evidence is a same-build-1110 CPU reference plus GPU run; the old
build-1104 diagnostic capture need not be repeated merely to obtain a second
unchanged trace. To isolate this particular repair, compare build 1104 vs
build 1110 with *identical trace-OFF* flags/save and captured visuals; to test
net GPU-first progress against the texture/particle-qualified reference,
compare build 1092 vs 1110 with the same trace-OFF profile. Do not mix the
trace-ON correctness runs with a causal speed comparison. Consider cache
order and repeated/reversed trials where variance matters.

No more speculative worker/buffer-budget changes or release promotion are
authorized by this evidence alone. User hardware/save capture is the
external dependency; carry forward exact SHA, CI and provenance.
