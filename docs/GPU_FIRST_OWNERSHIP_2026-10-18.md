# GPU-first renderer ownership — October 18, 2026

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
