# Bounded multi-sprite GPU interpolation dispatch — October 19, 2026


## Fixed-table reuse amendment — full #1055 correctness-qualified

Implementation `8134a113cdaaa9fc3d5c3903752b420ab7a287cf`;
isolated attribution-stub correction `629de44061e404e291dae647c8e026b23d841395`.
Full #1055 / [run 39227110919](https://github.com/Vorith03/VulkanMod/actions/runs/39227110919)
PASSED with Forge JAR artifact **12173844688** and smoke logs
**12175645031**. Native screenshot marker
`VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK` confirms the 320-row
reuse stress and exact atlas pixels; shared scratch marker and
synchronization validation passed, as did Forge/terrain/particle,
public Create Chronicles/Immersive Portals and Crash Assistant.
Private pack fixtures skipped. #1054 (39226851915) failed **before**
native/Forge testing: the extracted attribution stub omitted the new
`MultiStats.tableRecycles` field. Its failure is not a GPU pixel/sync
result.

Multi-dispatch pages reserve a fixed high-end 32 × 48-byte slab on
creation; future pixel scratch reservations exclude the whole slab.
Row addresses descend and shader job Z ordering is unchanged.
The cursor advances only after a successful 48-byte staging upload.
On 32 rows, `queueMultiJob` forces all outstanding atlas-copy reads
and their pending compute to be recorded. If any page-local pending
jobs remain, recycling rejects and preserves the normal fallbacks.
Only then can the cursor wrap. The first reuse emits a page-slab
buffer barrier **after** the staging-buffer reset decision and before
the new `vkCmdCopyBuffer`: source stage
`COMPUTE_SHADER | TRANSFER`, source access
`SHADER_READ | TRANSFER_WRITE`, destination stage/access
`TRANSFER / TRANSFER_WRITE`. Work is ordered on the **same
graphics VkQueue**, including earlier command buffers and submissions;
host queue-drain state alone does not establish GPU completion.
Retirements still use the existing pending-aware upload-safe path.
The 4 MiB page/128 MiB physical bounds and exact Java-double RGB,
alpha, mip and atlas ordering contracts remain.

Native oracle adds 160 rounds × two sprites = **320** accepted rows,
forced epoch boundaries every 16 rounds, and distinct VkQueue
submissions; it checks ≥9 wraps, zero extra GPU multi-fallback,
exact final pixels and resident-source overlapping overwrite.
The preexisting native mixed CPU/resident texture transfer and
shader synchronization gates continue to run. Long real-world
modpack adoption, reload/portal behavior and RX profitability remain
unknown. Lifetime `gpu_multi_table_recycles_total` allows measuring
table pressure along with `eligible/accepted/fallback/dispatch/
dispatchedJobs/metadataBytes/flushes`. The metadata staging byte
counter is **cumulative traffic**, not 1,536 bytes of live allocation.

## Scope and ownership

Implementation `cf3351f726ed143e1006b0504b92bb157020e322`;
fully qualified executable `c06f1cbc9c57a09b0df819edc639022bef049096`
(validation fixes `4718529021079515bba58fcdef55ee004cbee3f4`,
`c3b4a43d1f50db011b9e51156951a6fe231918b5`, `c06f1cbc`).
**Full #1053 / [run 39216055025](https://github.com/Vorith03/VulkanMod/actions/runs/39216055025) PASSED**;
Forge JAR artifact **12169313491**, smoke logs **12169019219**.
Native marker `VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK` validates
two independent accepted jobs executing in **one real dispatch** with
exact native pixels and atlas order. `VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK`
was also observed. Public offline/native, Forge package and startups,
terrain/particle pixels and synchronization, screenshot,
Create Chronicles/Immersive Portals and Crash Assistant passed;
private resource packs skipped.

Negative full runs: #1050 (`39214646118`) failed the isolated
attribution extractor's missing compute stub before native tests;
#1051 (`39215041966`) reached screenshot but misclassified
48-byte GPU metadata staging as redundant CPU pixel staging;
#1052 (`39215774197`) failed a /proc child-exit race in the
independent CI planner contract. They were fixture failures corrected
by the commits above; native multi-job qualification first completed
in full #1053.

This is a genuinely grouped compute-recording path, not merely shared descriptors:
compatible interpolations reserve *one append-only job row per accepted sprite* and
defer compute until the unified atlas copy queue is about to consume scratch.
One `vkCmdDispatch` uses workgroup Z as a sprite-job index and Y as a source mip,
with X spanning the largest accepted frame. Disjoint per-sprite scratch output
intervals and page-local contiguous job tables allow a group to use the same
source/scratch descriptor; there is no separate descriptor or VkBuffer per job.

The separate default-off gate is:

```text
-Dvulkanmod.experimentalGpuAnimationMultiDispatch=true
```

It requires enabled GPU animation interpolation plus all three previous pilots:
`experimentalGpuAnimationBatch`, `experimentalGpuTextureSourcePool` and
`experimentalGpuAnimationSharedScratch`, and the unified copy queue
`experimentalUnifiedTextureUploads`. The legacy fused per-sprite dispatch,
single-mip dispatch, standalone bindings, and CPU fallback remain.

## ABI and admission

- Per-page job metadata is exactly 12 u32 words / 48 bytes. For each job it
  contains the lease-relative source-page base, current and next frame XY,
  frame width/height, scratch output base, subframe/duration, immutable mip
  table offset and mip count. Each entry occupies a unique range allocated
  from the **high end** of a shared 4 MiB scratch page; pixel output occupies
  the low end. The two reservations are mutually bounded and append-only.
- The existing fused mip rows remain six u32 words, with existing per-mip
  dimensions, source/scratch offsets and frame shifts. The shader uses
  `reserved == 0xffffffff` only for multi-job dispatch, preserving the
  `reserved == 0` legacy and nonzero single-sprite fused ABIs.
- A group contains at most **32** jobs, and is limited to the current page
  and pending atlas-copy epoch. Tables descend in memory; Z indices traverse
  jobs in reverse acceptance order, which is safe because scratch slots are
  disjoint and atlas copies retain sequential destination ordering.
- Production still validates all source, mip, clock, scratch and atlas
  extents before selecting the new path. Invalid/missing resources, unsupported
  combinations, full page metadata budget or rejected metadata staging
  retain the proven per-sprite dispatch/CPU fallback. Accepted results
  return before the equivalent Java pixel interpolation runs.
- Every job row is staged before CPU work is skipped. An upload staging reset
  first flushes the prior atlas-copy epoch and its accepted pending compute
  before submitting or replacing command buffers. No unfallible deferred
  descriptor allocation remains after acceptance.

## Ordering, lifetime, resource and diagnostic contract

- When a binding reuses its scratch output slot, overlapping queued readers
  force a drain *before* accepting the replacement. Other live scratch slots
  can still share a descriptor/page/dispatch.
- The atlas queue calls `flushPendingBatchedJobs()` before emitting its
  recorded `VkBufferImageCopy` reads. Source transfer writes and scratch
  metadata transfer writes are made visible to compute; compute scratch
  writes are then made visible to transfer reads. Atlas overlap, source
  switches, region capacity, source admission and outer scope end retain
  their existing ordering boundaries. Binding retirement drains pending
  compute and continues using upload-safe/fence retirement.
- The previous **4 MiB per page / 128 MiB pending-aware physical** scratch
  reservation ceiling remains. Job rows consume existing page bytes;
  metadata is accounted as actual admitted bytes and no new descriptor sets
  or dedicated GPU job buffers are created.
- `GpuTextureInterpolationCompute.multiStats()` reports lifetime
  `eligible`, `accepted`, `fallbacks`, `dispatches`,
  `dispatchedJobs`, `metadataBytes` and `flushes`. The benchmark
  texture tick emits their lifetime totals for deltas across comparable
  samples; accepted interpolations continue counting CPU pixel work
  displaced. The legacy interpolation dispatch total includes the delayed
  grouped dispatch count. **A group of one job is allowed** at a forced
  boundary; a reduced dispatch count requires measured multi-job adoption.

## Correctness and unresolved gates

The opt-in screenshot/native oracle stages two independently leased two-frame
sources with immutable mip rows, requires **two accepted jobs and exactly one
native dispatch** before the atlas queue drains, then checks exact
Java-double RGB truncation and unchanged current-frame alpha. Repeated
interpolations and an overlapping later resident-source overwrite verify
copy order. The required native marker is
`VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK`; shader compiler,
synchronization-validation and Forge compatibility evidence must come
from the **completed** full workflow, not merely this contract.

**Not measured:** representative RX 6900 XT eligible/accepted fractions,
multi-job group-size distribution on Create Chronicles, added metadata
staging CPU/GPU time, saved dispatch overhead, total CPU graphics time,
FPS, p95/p99 or net profitability. This does not transfer Java animation
clock, unsupported sprite eligibility or terrain graph traversal to the
GPU. Keep default-off and do not request an isolated RX benchmark.

Preserve prior full #1049 shared-scratch qualification, negative
#1047/#1048 offline extractor evidence, #1041/#1042 particle descriptor
negative evidence and analyzed #1028 UUID `e05f2b31`.
Open parallel gates: real particle portal/mod-light/translucency/reload/
frame-slot verification, generated terrain REPLACE/atomic APPEND ABI,
integrated ownership adoption and measured net benefit.

## Historical #1053 append-only limitation (addressed in #1055 candidate)

Each update appends an immutable 48-byte row to the high end of its
shared scratch page, and the interval is not reused before GPU-safe
page retirement. The existing **4 MiB per page / 128 MiB physical
pending-aware** limit bounds this memory pressure, but sustained
high-churn updates can exhaust metadata headroom and shift work to
the original per-sprite GPU or CPU fallback. It is not proven to be a
performance improvement. A future fixed per-page reusable job slab
requires explicit prior compute-read -> next transfer-write ordering,
including across command-buffer submissions; host queue drainage alone
is not a GPU-completion token. Measure group sizes and fallback under
extended actual-world tick/reload before enabling by default.
