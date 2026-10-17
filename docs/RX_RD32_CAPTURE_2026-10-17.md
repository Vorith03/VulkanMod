# RX 6900 XT RD32 capture — October 17, 2026

## Disposition

The owner returned the requested #1019 capture and raw CPU-graphics JFR.
The benchmark completed, both texture pilots exercised production paths, and
CPU sampling retained the capture. **Coverage evidence is established; net
performance benefit remains unmeasured.** This is one pilot-on capture, not a
same-executable pilot-off control or a paired performance result. No renderer
default, memory-budget increase, shader scheduler or new JAR is selected here.
Visual correctness is not established by logs alone.

## Provenance and validation

- Branch inspected at `d27f762abe061c90ee3bcff00ccd329b0bc03b8a`; its only
  delta from executable `ce9d66e1219e13139e3b0b6b7de531082f7a04c7` is documentation.
- Runtime identity: `0.3.2-forge.2-build.1019-gce9d66e1`, run UUID
  `6c29da16-40b9-4f37-8e78-aa1180197560`.
- Full CI #1019 run `38972838076`, job `117215415389`: live step metadata
  rechecked; offline contracts, build/package, startup, screenshot/pixel/sync,
  combined Create Chronicles and Crash Assistant passed. Private-pack steps
  skipped. That remains qualification evidence, not RX performance evidence.
- Java 17.0.15, Linux, 16 logical CPUs, RX 6900 XT / RADV NAVI21;
  framebuffer **2552 x 1374**, RD32, simulation distance 12, vsync false.
  The log reports cap 260 but measured frame-limit time is zero; it does not
  explain these frame times. Preserve the actual framebuffer for comparisons.
- Fixed spectator camera `(0,192,0)`, yaw -90, pitch 30, no pose/framebuffer
  changes; initial-population settling **85.398 s**; successful 180.012 s
  profiler capture / 180.004 s controller measurement and orderly save/exit.
- O3/O4 requested, float64 enabled, source pool enabled, all resident copy
  requests queued rather than direct: both requested pilots are exercised.
- Files supplied: `latest(9).log`, `debug(6).log`,
  `vulkanmod-performance-benchmark-6c29da16-40b9-4f37-8e78-aa1180197560.log`
  and its `.cpu-graphics-15002216574870579369.jfr` neighbor. Raw files remain
  owner attachments, not public repository contents.

SHA-256 of benchmark log:
`1819357c21238172fc98237fe2027d2e6bb9ef985435236a198531b5a52d71ef`.
SHA-256 of JFR:
`34830c2c6178f5d980bbea03136e19c0564bef74cada450ce43f12961fe475e8`.

## Frame, tick and GPU observations

| Scope | Observation |
| --- | --- |
| Full capture | 4,772 frames / 180.012 s = **26.51 FPS**; frame-count-weighted frame mean **37.662 ms** excluding between-frame gaps/summary I/O |
| Tick-bearing frames | 3,380 frames, **47.631 ms** mean; 3,573 actual client tick calls |
| Render-only frames | 1,392 frames, **13.455 ms** mean |
| Opening hitch | First window only 7 frames, mean 750.121 ms, maximum **1,635.542 ms**; second window mean 126.746 ms |
| Late performance | Windows wholly after 60 s: 3,449 frames, 34.638 ms mean; wholly after 120 s: 1,706 frames, 34.755 ms mean, about 28.75 FPS over window durations |
| Client tick | **30.166 ms/tick**, p95 38.303 ms; textures **17.787 ms/tick**, particles **4.646 ms/tick** |
| Texture details | 8.869 ms/tick inside sprite uploads; 8.918 ms residual; outer batch only 0.165 ms/tick. These are nested scopes, not additive extra costs |
| World rendering | **13.545 ms/frame**; particle rendering **4.453 ms/frame**; terrain setup **4.662 ms/frame** and block-entity render scope **2.198 ms/frame** from weighted windows |
| Main graphics GPU | **3.123 ms/frame**, p95 4.504 ms; no query drops/read failures |
| Separate texture-upload GPU span | **4.219 ms/batch**, p95 6.176 ms, 3,380 measured batches; includes copy/compute/dependencies; no unresolved/dropped queries |

The two GPU scopes have different denominators and exclude different work;
do not add their means or describe 3.123 ms as all GPU time. CPU phase times,
negligible frame-fence wait (~0.008 ms/frame) and execution stacks identify
substantial CPU work. They do not establish that every operation can move to
GPU or exclude server/driver/observer effects. Do not aggregate per-window
percentiles into a purported capture percentile. The early hitches remain
part of this capture; the late-window view is descriptive, not a replacement
baseline. This recording does not identify their JIT/GC/initialization cause.

## Texture coverage and the measured queue boundary

- Source uploads: **1,097,842 GPU / 621,869 CPU** (~63.84% GPU of source
  requests). Another **2,588,241 CPU** requests upload temporary/other images;
  do not count those as rejected original-source residency attempts.
- Standard distinct-frame interpolation: **108,800 GPU / 2,761,399 CPU**,
  **3.7907% GPU** of 2,870,199 attempts. CPU fallback buckets:
  **2,755,020 residency-disabled**, **6,379 no-upload-batch**, all other
  buckets zero. Disabled is 99.769% of CPU interpolation fallback.
- Renderer-lifetime residency: **463 admissions, 1,257 capacity rejections**,
  zero source/staging rejection and zero invalidation; charged current/peak
  **131,069.816 KiB / 131,072 KiB cap**. Actual source current 125,850.492 KiB,
  scratch current 430.246 KiB. Source pages: 31 / 126,976 KiB allocated,
  463 leases, zero pool-capacity rejections or retired pages.
  Lifetime values include warmup and pending retirement; do not treat them
  as capture-only counters or infer a leak from a full budget.
- Mixed copy emitter: **1,435,220 flushes**, 21,250,172 regions,
  **401.685 flushes/tick**, 14.806 regions/flush; **8.154 ms/tick** inclusive
  flush CPU. All **1,097,842** resident copy requests / 5,228,762 regions
  used the common queue; zero direct resident copies.
- Flush reasons: **1,266,289 source-buffer switches (88.23%)**,
  107,119 GPU-interpolation boundaries, 48,292 region limits, 6,760 direct
  upload boundaries, 3,380 texture switches and 3,380 outer ends; zero
  overlapping-region flushes. Reasons sum exactly to the emitter total.
  `STAGING_BUFFER_SWITCH` names any changed source VkBuffer in the mixed
  emitter, including resident pages; it does **not** mean 1.27 million
  staging allocations/growth events. Per-reason CPU timing is not captured.
- GPU interpolation made **544,000 dispatches** for 108,800 updates
  (five dispatches/update); do not prioritize compute-job coalescing solely
  from that count while residency-disabled CPU interpolation dominates.

Source review at the tested executable confirms:

1. `VTextureSelector.queueSpriteUploadCopy` supports only one pending source
   buffer. Any source handle change immediately emits the pending group;
   unified mode also records a conservative write dependency after each group.
   This is a concrete batch-fragmentation mechanism. The log cannot tell which
   pairs of source handles dominate or assign all 8.154 ms to source switches.
2. `SpriteAnimationState.tryResidentUpload` marks a failed first `tryCreate`
   admission as permanently disabled for that state. `tryGpuInterpolation`
   returns the disabled bucket first. The near-full logical budget and
   capacity rejections strongly implicate admission/capacity in low coverage;
   counters do not join each disabled sprite to its original rejection.
   Zero source invalidations is consistent with the earlier source-identity
   repair being exercised, not a matched speedup claim.

## Terrain, particles and background CPU

Across windows with a valid preceding baseline (everything after the first
window), **4,765 graph traversals for 4,765 frames**, zero cached rebuild
schedules, and only 78 builds/publications. The pending dirty set remains
1,604 and visible sections remain 6,559. Source preflight refuses cached
reuse above 512 pending entries, forcing a full traversal in this state;
other invalidations may coexist. Do not solve this by blindly lifting the
cap or dropping dirty work. Raw stacks also expose the scan of visible
sections in `WorldRenderer.renderBlockEntities`, separate from actual
block-entity draw complexity.

Particles: 29,919,650 tick calls across 3,573 engine ticks. Campfire smoke
accounts for 29,268,483 calls (~97.82%) and an estimated **4.307 ms/engine
tick** from stride-8 sampling. Overall particle leaf allocation is
**5,246.209 KiB/tick**. The dominant class has multiple source/provider identities:
1,013 source/provider mismatches mean the first example provider is not an
exclusive identity. Particle rendering costs 4.453 ms/frame; raw stacks
include BufferBuilder finalization and light/chunk lookups. That establishes
a concrete workload, not eligibility to replace collision/lighting/mod logic.

Raw JFR also retains **12,882 server-thread Java samples**, versus 9,726
render-thread Java samples; these are not exact thread CPU milliseconds.
Repeated integrated-server `Can't keep up` warnings occur during capture.
The 180 periodic `minecraft.ServerTickTime` rolling-average observations have
median **55.743 ms**, maximum 368.209 ms (not individual tick percentiles).
Server simulation remains a separate workload to preserve in comparisons.
Window JVM counters sum to 55 collections / 699 ms reported GC and about
39,767 MiB render-thread allocation; GC totals alone do not explain all
frame hitches. No fatal renderer/device-loss event appears during capture;
normal exit does not certify visual correctness or Vulkan validation coverage.

## Raw recording cross-check

Independent Java 17 `RecordingFile` decoding found **40,734 events**:
23,039 execution samples, 17,515 native-method samples and 180 server-tick
events. Two native samples lie outside the admitted range; this reconciles
with the report's 17,513 admitted native samples. No missing sample stack;
raw JFR truncates 20 stacks. The report's **425 truncated stacks** also counts
stacks longer than its 48-frame display cap; these figures are different
scopes, not a decoder mismatch. First/last retained samples span the capture.
The report has no stack-cap overflow or decode-event-limit hit.

Native samples include 15,983 FileSystemWatchService samples; they are native
boundary observations, not graphics CPU time or evidence of a watcher spin.
Report categories use the first leaf-to-root graphics owner. Consequently
terrain category samples include block-entity section scanning, and vertex
category samples include particle buffer finalization. Read full caller
chains before interpreting category shares.

Independent render-thread Java stack containment (inclusive, potentially
overlapping; denominator 9,726): TextureManager 2,278; interpolation 2,119;
visibility traversal 2,091; ParticleEngine 1,978; block-entity render scan
987; client block-entity ticking 831. The same main workload groups persist
in later 30-second intervals; the opening hitch does not create them all.
These are statistical sample counts, not measured method milliseconds.
Observer overhead remains unmeasured without a matched configuration.

## Next useful work and comparison gate

The requested single capture is received; do not request its repetition.
First review a **bounded common transfer queue grouped by source buffer**,
with exact destination ordering/overlap epochs and flush boundaries for O4,
direct uploads, staging retirement and texture/tick lifetime. Source switches
dominate emitted groups, whereas raising the region ceiling alone cannot
address them. Any implementation must retain exact pixel/synchronization
oracles and conservative fallback; do not simply remove barriers or globally
reorder uploads. A benefit remains a hypothesis until measured.

Separately review logical residency admission at the fixed budget, especially
whether first-come permanent admission spends capacity on less useful sheets
while interpolation-heavy sheets fall back. Existing counters justify this
source review, not a budget increase or a new heuristic allowlist. Keep
terrain graph reuse and measured smoke paths as subsequent bounded workstreams.

For a pilot effectiveness verdict, the existing same-#1019 pilot-off control
in `GPU_TEXTURE_RD32_HARDWARE_TEST.md` remains necessary; three matched pairs
remain the formal performance gate. A slower historical aggregate (#1014
16.113 ms/tick textures vs #1019 17.787, 42.48 vs 26.51 full-run FPS) is a
warning signal, **not proof of regression**: executable, observer, early
hitches, route mix and server workload are not controlled. No new user-machine
test is required to perform the source review above.
