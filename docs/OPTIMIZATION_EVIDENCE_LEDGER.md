# Optimization effectiveness ledger — October 10, 2026 reality check

**#1026 diagnostic-only capacity discriminator:** Existing #1024/#1025 JFR repeats the same inlined `ensureCapacity` leaf at the `storeRenderedBuffer → Tesselator.end` path, yet does not identify native resize events. A scoped opt-in counter reports exact particle-finalization calls, corresponding ensureCapacity checks, actual `MemoryTracker.resize` events, requested/current/new native capacities, and resize wall duration at the already-owned redirect site. **Not a claimed speedup**. Report scope excludes other threads and non-particle renderer work; count and sums must be interpreted within those limits. Full #1026 Forge/native CI [run 39093829259](https://github.com/Vorith03/VulkanMod/actions/runs/39093829259) passed on executable `8da6c404` (artifact `12123293770`). **Qualified but unmeasured**: one new RX capture must supply resize/check counts; no duplicated control run. Its capacity-check injection may affect hot-path timing, so do not treat #1026 FPS as an optimization effectiveness comparison.

**#1025 RX hardware verdict (2026-10-17):** Owner #1025 UUID `7b8d70be` completed (8,585 frames / 180.039 s, ~47.68 FPS) against previously supplied #1024 UUID `1851e3c8` (9,167 / 180.022 s, ~50.92 FPS), **-6.4% observed**. Particle render CPU 3.776→3.892 ms/frame; texture CPU 14.095→14.491 ms/tick. Direct raw JFR particle-render `BufferBuilder.ensureCapacity` sampled leaves **953/3,138 → 909/3,051**, mostly still through `Tesselator` finalization. `onHasChunk` leaves 423→534. Main graphics GPU 2.890→3.545 ms/frame is a major cross-run confounder, not causally explained by CPU-only buffer changes. **Disposition: ineffective / unfavorable screening, not a proven isolated regression**; no default promotion or repeat baseline. Full details [#1025 RX analysis](RX_RD32_CAPTURE_1025_BUFFER_2026-10-17.md).

## #1025 candidate: production BufferBuilder growth-pointer and vertex-element work — 2026-10-17

- **Evidence:** #1024 raw 180-second Render-thread JFR has 953 execution leaves in `BufferBuilder.ensureCapacity` during particle render, 937 through `storeRenderedBuffer` finalization; another 385 leaves in the element-advance method. Sampling is not allocation telemetry. The original `BufferBuilderM` pointer update was injected at every `ensureCapacity` return; the existing `nextElement` used `% list.size()` per element.
- **Work elimination:** native pointer address is fetched only when Minecraft calls `MemoryTracker.resize`, not on capacity checks that do not resize. Element wrap uses comparison rather than integer remainder. Original Forge/vanilla buffer capacity policy, growth request/bytes, index generation, fallback vertex layouts, and upload lifetime remain unchanged.
- **Correctness/activation:** normal path, not a new GPU/particle experiment. Existing vertex consumer production smoke expanded with forced growth, exact pointer, positions/UVs, release and reuse. Requires full CI and RX gameplay/visual validation; do not infer net FPS or actual growth frequency from removed code.
- **Validation:** Full #1025 public/native/Forge CI passed at executable `7bff3d7c` ([run 39091850361](https://github.com/Vorith03/VulkanMod/actions/runs/39091850361)); production growth/reuse smoke, Forge packaging, Vulkan native/pixel/sync, Create Chronicles public and Crash Assistant passed. Private resource packs skipped. Qualified artifact 12123197441. **Disposition:** **qualified but unmeasured** pending one owner RX #1025 capture compared against the existing #1024 baseline. If capacity growth remains dominant despite eliminating redundant address work, instrument *real resize events and bytes* before expanding the optimization.


**#1024 RX evidence received (2026-10-17):** Build `3f77cab2`, opt-in Forge Frustum fast path applied in debug and exercised by raw JFR. Current particle-render `AABB.equals` sampled leaves **0/3,138** vs #1023 **764/2,910**, particle-render CPU **4.196→3.776 ms/frame**, capture **7,825→9,167 frames/~180s** (~43.47→50.92 FPS), texture tick **14.704→14.095 ms/tick**. Distinct workloads/executables confound the +17.15% aggregate: **activation and favorable screening, not formally proven isolated net FPS benefit**. Opt-in unchanged, visual check still unconfirmed. Next measured residual is particle lighting/chunk and `BufferBuilder` stacks plus 14.095-ms texture tick; investigate correct ownership before a bounded fix, without asking for repeat #1024 or #1023 capture. See [#1024 analysis](RX_RD32_CAPTURE_1024_FRUSTUM_2026-10-17.md). 

**New owner-directed next-FPS candidate (October 17, #1024):** The
RX #1023 JFR identifies the Forge per-particle frustum infinite-AABB
`equals` check as 764 of 2,910 sampled Java particle-render leaves and a
separate ~601 leaves in modded particle lighting/chunk checks. Bounded
`FrustumInfiniteBoundsEquality` fast rejection is opt-in and passed
full public/native/Forge CI [#1024](https://github.com/Vorith03/VulkanMod/actions/runs/39078815371) at `3f77cab2`.
The source helper matches the original AABB equality contract in 100,000
random tests and special/subclass cases; ordinary/default execution is
unchanged with the property absent. **No RX production adoption or net FPS
benefit measured**; isolated sample shares are not time savings. Owner correction: **only one new #1024 flag-ON RX capture**; use
the already received #1023 bulk-ON UUID `252df7a2` as the before
baseline, with otherwise unchanged settings and assumed restored world.
No duplicate baseline, fresh #1024 OFF, or repeated before/after.
This remains cross-build, historical-control screening and **not** a
same-JAR formally matched speedup claim.
The previous #1023 bulk CPU pair remains conditionally accepted 1/3 and its
formal default gate open, but the owner specifically selected a new hotspot
instead of immediate additional #1023 pairs. See
[fast-path evidence](FRUSTUM_INFINITE_BOUNDS_FAST_PATH_2026-10-17.md).

**Latest evidence, October 17:** owner #1020 capture is received; see
[analysis and terrain repair](RX_RD32_CAPTURE_1020_AND_TERRAIN_REPAIR_2026-10-17.md).
Source-switch elimination and fewer copy commands are exercised, while texture
CPU time is essentially unchanged in historical single captures. Net benefit is
still unmeasured. Terrain cache is rejected by its 512-entry cutoff against a
stable 1,604-entry backlog; the bounded membership/attempt repair is implemented
and passes production-method CPU regressions (baseline failure and cap-only spin
preserved). **Qualified but unmeasured**: full CI #1021 passes at `8ff928dd`
(run `39070465187`, job `117507019602`), including production packaging and all
public renderer/compatibility/pixel/sync gates. Private packs skipped. RX adoption
and net benefit remain unmeasured; the next gate is the changed-executable terrain
capture, not another unchanged #1020 run.
This supersedes the first-capture request below; do not repeat an unchanged run.


**Current implementation, October 17:** [source-switch queue repair](TEXTURE_SOURCE_GROUPING_REPAIR_2026-10-17.md)
replaces the unified pilot's single-source pending group with per-source emission
inside a 128-region disjoint epoch. **Qualified but unmeasured**: full public/native CI #1020 is green at
`e0807c626bfa27416ebadc53eddff519f9ac29ef`, run `39038204099`, job
`117412382928`. All 22 contracts, Forge packaging, mixed-source native pixels/
synchronization, renderer, Create Chronicles and Crash Assistant passed; private
packs skipped. **Net hardware benefit remains unmeasured**. The production regression fails on `504617c` and
passes after repair (128 alternating writes: 128->2 copy commands, 128->1
barriers; 100,000 randomized writes match sequential output). This changes real
command recording/dependency work, not only counters. Both pilots stay opt-in;
residency budget/admission is unchanged. Next: first #1020 RD32 adoption capture, then matched #1019-versus-candidate
RX runs with both pilots enabled in both. Existing
capture proves fragmentation/activation, not a measured speedup.

**Latest evidence, October 17:** the owner returned the #1019 pilot-on RD32
capture and raw JFR. [Full analysis](RX_RD32_CAPTURE_2026-10-17.md) supersedes
older "representative coverage unmeasured" statements for this workload only;
**net benefit is still unmeasured** without a matched control. Historical
#1014 results below remain historical, not a same-executable baseline.

| #1019 candidate | Measured adoption / residual | Net benefit / disposition | Next decision |
| --- | --- | --- | --- |
| Common CPU/GPU copy queue + source pool | Exercised: all 1,097,842 resident-copy requests queued. 1,435,220 mixed flushes, 88.23% caused by changed source VkBuffer, 8.154 ms/tick inclusive flush CPU. Source pool 31 pages / 124 MiB; no pool-capacity rejections. | **Qualified but unmeasured**; one pilot-on run cannot establish improvement or regression. | Source grouping is implemented and native-qualified in #1020; next compare emitted commands, texture CPU and upload GPU time on RX. |
| O3 original-source copies | 1,097,842 GPU / 621,869 CPU source requests (~63.84% GPU); 2,588,241 other/temporary CPU uploads are a separate denominator. Zero lifetime invalidation. | **Qualified but unmeasured**; source identity repair is consistent with zero invalidation here, not a matched speedup. | Preserve original/temporary identity and fallback; compare same executable. |
| O4 interpolation / residency | 108,800 GPU / 2,761,399 CPU (3.79% GPU); 2,755,020 disabled-residency fallback, 6,379 no-batch, other reasons zero. Logical budget nearly 128 MiB, 1,257 lifetime capacity rejections, zero source/staging rejections. | **Low adoption**, net benefit unmeasured. | Review fixed-budget first-admission/permanent-disable policy; counters do not link each disabled sprite to its rejection. No automatic budget increase or new compute scheduler. |
| Terrain graph reuse | 4,765 traversals for 4,765 frames after first window baseline, zero cached schedules; 1,604 pending dirty exceeds 512 preflight cap. | **Low adoption** of cached path; net benefit unmeasured. | Preserve neighbor/ring/visibility correctness; investigate recurring pending work before lifting limits. |
| Particle offload candidates | Particle tick 4.646 ms/tick, render 4.453 ms/frame; campfire smoke 97.82% of particle tick calls, dominant class includes multiple source/provider variants. | **Intentionally limited** to measured candidates; no GPU adapter benefit established. | Preserve collision/light/Forge semantics; use full JFR chains, including particle buffer finalization. |

This is the compact authoritative index for **performance outcomes**, not a commit log. Read `AGENTS.md` §6A for dispositions and the required evidence chain. Runtime numbers below are from the owner's October 10 #1014 RX 6900 XT/RADV RD32 Create Chronicles capture as summarized in the preceding hardware-analysis session; the raw uploaded benchmark is not embedded in Git. The previously unavailable raw capture, `vulkanmod-performance-benchmark-2a2bd027-e1e3-4629-8112-af77a3e7e0a3.log`, was recovered from the owner's Project Library and read directly on October 11. The figures below have now been reconciled against its terminal `benchmark` and `capture_complete` lines and the exact #1014 source implementation; the original upload remains outside Git. No matched A/B speedup claim is supported.

**Verified #1014 terminal counters (capture-specific where indicated):** successful `capture_complete`, 3,600 texture ticks over 180.028 s, 7,647 frames, initial-population settling 71.252 s, `shaderFloat64_enabled=true`, O3/O4 flags true. Texture mean **16.113 ms/tick** (sprite uploads 8.763, residual 7.349); CPU sprite-copy flushes **1,009,923** / 17,000,981 regions, **5.727 ms/tick** inclusive in texture time, 16.834 regions/flush. O3 materialized uploads: 1,313,703 GPU and 3,401,636 CPU, the CPU category mixing original source uploads and temporary interpolation output. O4 standard attempts: 1,067 GPU / 2,886,248 CPU. Residency: 1,184 admissions, 541 capacity rejections, 0 source/staging rejections, 704 invalidations, peak charged 131,071.629 KiB against 131,072 KiB cap. Main graphics GPU mean 2.424 ms/frame; **separate** graphics upload-batch span 2.141 ms/batch (not additive). Particle tick 3.520 ms/tick and render 3.480 ms/frame; campfire smoke dominates measured particle tick attribution. Terrain final **cumulative** graph traversals 9,698, cached admissions zero and pending dirty 1,604; these traversal totals include pre-capture history and are not an in-capture delta. Existing `MAX_CACHED_DIRTY_SECTIONS=512` makes the 1,604-deep dirty set ineligible for cached graph preflight when that preflight runs; other invalidations may also matter.

**Baseline provenance:** 180 s after initial terrain convergence; build #1014 executable `e9f8591e2df44486cc504dfbb06bdf759ceed377`, full public/native CI green ([run](https://github.com/Vorith03/VulkanMod/actions/runs/38044340567)). Latest subsequent commit before this audit: `fb7fbde` (checkpoint documentation only). Existing fixed-settle #988/#935-style FPS is not a matched control for this converged capture. Three-run paired comparisons remain the Phase 5 acceptance contract.

| Candidate | Code / activation | Observed adoption or residual | Comparable net benefit | Disposition / next decision |
| --- | --- | --- | --- | --- |
| O3 resident discrete texture copies | `GpuAnimatedTextureResidency`, `SpriteAnimationState`, `MSpriteContents`; opt-in `-Dvulkanmod.gpuAnimatedTextureCopies=true`; qualified native #998 and #1014 | Raw #1014: 1,313,703 GPU vs 3,401,636 CPU materialized requests (27.9% GPU **of mixed requests**, not source-only adoption), 1,184 admissions, 541 capacity rejections, 704 invalidations, **zero** source/staging rejections. Charged residency peaked at 131,071.629 KiB / 131,072 KiB. Temporary interpolation images can dominate CPU request counts; the exact source-only fraction was not captured. | **Unmeasured**: no same-convergence O3-off comparison | **Low adoption** in raw route mix (not a claim that every CPU route was eligible). Check source-vs-interpolation upload identity, disable reasons, capacity/fallback and route CPU time; then paired A/B. |
| O4 float64 GPU interpolation | `GpuTextureInterpolationCompute`, `SpriteTickerMixin`; opt-in `-Dvulkanmod.gpuAnimatedTextureInterpolation=true`, requires `shaderFloat64` and graphics compute; exact pixels qualified #998 and #1014 | Raw #1014: **1,067 GPU / 2,886,248 CPU** standard interpolation attempts (~0.037% GPU). This excludes hidden/repeated-index no-ops and custom ticker routes; the capture did not distinguish `no_residency`, disabled-after-rejection, unavailable, off-thread or dispatch failures. | **Unmeasured**: no O4-off matched comparison | **Low adoption** in raw route mix. Inspect source identity/fallback lifetime and whether sprites ever have residency; only expand diagnostic reasons needed to explain the gap. |
| CPU texture submission/batching | `VTextureSelector`, texture tick breakdown, `TextureTickAttribution`; normally active | Raw #1014: texture processing 16.113 ms/client tick; 8.763 inside uploads, 7.349 residual. **1,009,923** actual CPU sprite flushes (17,000,981 regions) took **5.727 ms/tick**, nested in the upload/texture cost. `texture_upload_gpu` was 2.141 ms *whole graphics upload batch*, not shader time or additive to main graphics. | **Unmeasured** | **Qualified but unmeasured**; distinguish actual CPU preparation, flushing, tick iteration, and overlap before further mechanism. |
| Terrain graph reuse, dirty admissions, backlog | Changes `db99a21` through `865d10b`, opt-in flags not required for CPU scheduler; #1014 qualified | Raw #1014 terminal: **9,698 total graph traversals** since renderer counter start (includes warmup), **0 cached_graph_schedules**, **1,604 pending dirty** (also stable across valid 5 s windows). The 512-entry cached-dirty preflight cap will refuse a set of 1,604 when evaluated; separate causes of full invalidation are still unknown. Do not mistake the cumulative traversal total for a capture-only delta. | **Unmeasured** | **Low adoption** of cached admission. Correlate `terrain_window.delta_valid`, camera/frustum/topology, dirty queue, pending/worker backpressure before a change. |
| Animated terrain sprite-scan coalescing | `TerrainSpriteUsageGate`, `fda45f5`; normal renderer pass, #1014 qualified | Production branch reachability and once-per-setup oracle; no per-pass workload/time attribution from #1014 established | **Unmeasured** | **Qualified but unmeasured**; assess cost if still significant, avoid restaging the same pass-scan optimization. |
| Particle attribution-hook overhead | `ParticleEngineAttributionMixin` gate and related profiler contracts, `db99a21`; production normal path avoids per-particle profiling hooks | Raw #1014 particle tick 3.520 ms/tick, particle rendering 3.480 ms/frame; CampfireSmokeParticle accounted for 29,290,919 tick calls and estimated 3.244 ms/tick, mostly in the `minecraft:campfire_cosy_smoke` source; no matched before/after. No general GPU particle simulation has been implemented as a qualified production replacement. | **Unmeasured** | **Qualified but unmeasured**; investigate the measured particle class/cost profile after the texture/terrain diagnosis, preserve Immersive Portals hook exclusions. |
| Benchmark convergence, profiler and CI | `f45aa7c`, `f424293`, `9d33348`, `1080620`, `084dc81`, CI policy | Previous #1002/#1003 could not deliver representative converged captures; #1014 completed one after proper initial-population settling. Native/Forge/compatibility gates remain intact. | Measurement reliability/availability improved; **not an FPS improvement** | **Infrastructure validated for #1014**, broader adoption/performance gates open. |

## Retrospective: October 5–10

**What genuinely advanced:** The O2 independent Java-double interpolation oracle and #998 native GPU pixel/synchronization qualification; O3 discrete resident copies and O4 compute interpolation; upload-route/peak-lifetime/GPU-batch telemetry; particle cost attribution with a less invasive gameplay default; cached coarse terrain/frustum state, direct dirty scheduling, backlog retention and ring ownership; per-setup animated sprite scan; fixes to Forge HUD and benchmark initial-population convergence; narrower, qualified CI execution. Correctness repair (including texture write-after-write ordering and ring identity checks), measurement enablement, CPU optimization, and CI efficiency are different categories and were not wasted just because none establishes an FPS gain.

**What failed:** Optimization effectiveness did not consistently control next-work recommendations. Existing O3/O4 capability was susceptible to being described as work to implement rather than a candidate requiring adoption diagnosis. A new round of nearby optimizations progressed before current offload paths had representative coverage or matched-speed evidence. The original inability to benchmark was a legitimate infrastructure blocker; the subsequent #1014 capture removed that blocker and must now drive decisions.

**Verified technical mechanism, impact not yet measured:** `SpriteContents.upload` is shared by vanilla discrete mip source uploads and interpolation-generated temporary mip images. The existing HEAD attempted `SpriteAnimationState.tryResidentUpload` without distinguishing them. Once an O4 interpolation attempt takes the CPU fallback, feeding its temporary images to a resident candidate can fail `GpuAnimatedTextureResidency.matches`, retire still-valid source residency, and permanently set `residentDisabled` for that sprite. A sprite with no resident source can also attempt to admit the temporary frame as though it were the original sheet. This is a *code-proven unsafe route classification*, not proof of what proportion of #1014 fallbacks it caused. The bounded correction is to admit only the owner's original `byMipLevel` source identity at the injection boundary; leave temporary interpolation uploads on normal CPU/Vulkan staging and retain genuine source mutation invalidation. Exercise O3 resident survival across an intentional O4 CPU fallback in native validation. Do not increase the 128 MiB budget or discard Forge fallbacks based on this hypothesis.

**Why process permitted it:** Green correctness oracles principally exercised successful O3/O4 paths, not the cross-path *successful residency -> CPU interpolation fallback -> continued discrete GPU residency* transition. Broad route counters disclosed under-adoption but not its reason. Roadmap statements warned against unmeasured claims, yet no compact implementation/activation/coverage ledger was mandatory before recommending the next optimization.

**Preventive/detective controls:** `AGENTS.md` §6A requires the evidence chain, branch-existing-path check and discrete dispositions; ROADMAP distinguishes performance gates from implementation. This table makes missing hardware and route evidence discoverable. The native fallback regression acts as a detector for the source-identity lifetime hazard. A future session verifies controls by checking the ledger, source code/test gate and paired captures, not by counting commits.

## Next disposition-changing actions

1. **Qualified #1016**: original-mip-only O3 residency boundary and native O3/O4-to-CPU-fallback preservation; do **not** claim a performance win without hardware measurement.
2. For existing #1014 evidence, inspect every available rejection and call-count counter; avoid treating memory allocation count as a per-update fallback denominator.
3. A later controlled capture should quantify standard interpolation GPU/CPU routes and discrete upload routes after the fix, without asking the owner to rerun any unchanged candidate. If desired for a true speedup claim, use matched CPU-path vs O3/O4 A/B with the **same** convergence rules and profiler flags.
4. If cached terrain admissions remain zero, distinguish bypass reasons within current traversal/dirty counters before revising scheduler logic.

## Corrective implementation after #1014

An original-mip-array identity guard now prevents interpolation-generated
temporary CPU NativeImages from entering O3 source admission/validation. A
CI-only interpolation fallback runs while O3 remains enabled, and the native
screenshot smoke requires CPU staging, unchanged original-source invalidation
count, and a subsequent GPU discrete upload. This targets the confirmed
cross-path hazard, not every observed CPU route. Native #1015 verified CPU temporary staging and later GPU resident survival, but
failed sync validation because the regression forced two overlapping atlas writes
into a single command buffer without a barrier. The test now isolates the later
GPU frame copy into a subsequent submitted command buffer. **Full native #1016
passed** ([run](https://github.com/Vorith03/VulkanMod/actions/runs/38118180872)),
including the forced-fallback marker, synchronization-clean screenshot tests,
Forge, Create Chronicles and Crash Assistant gates; private real-pack fixtures
were skipped. Source performance remains unmeasured. The exact terrain
traversal count is disputed between secondary summaries and must not become
an engineering input without the raw UUID report.

## Post-#1016 source-only diagnostic candidate — 2026-10-11

At `f3a72701003fd285c3f37d8c96200f16a61325d1`, production attribution now
splits materialized CPU/GPU uploads by **owned source mip array identity** versus
other temporary mip arrays, and partitions standard interpolation CPU fallbacks
by first observed gate (missing or disabled residency, GPU unavailable,
off-render-thread, no active batch/atlas, mutation or dispatch failure).
A separate copy-flush reason is recorded at each existing emission boundary
(resident copy, compute, region limit, batch end, texture/staging changes,
direct upload and staging reset). These counters are benchmark-only, have
fixed arrays, and do not alter Vulkan command recording, budget, pixels,
source mutation, or fallback outcomes.

The existing offline attribution capture contract has been extended for
warmup exclusion, per-route counters, fallback reason, flush reason, and
capture reset. **Source inspection passed; Java/Forge/native CI is intentionally
deferred** under the owner’s hold on building new JARs until a hardware test.
No adoption or speedup claim applies to this source-only candidate, and #1014
cannot contain these new fields. Next: review the focused contract and native
gate when a coherent physical-test candidate is ready; then compare exact O3/O4
fallback causes and flush boundaries under the same RD32 settings.


## Unified GPU texture-update architecture — user reprioritization, 2026-10-11

The target is a **common texture-update engine** with three semantic inputs:
immutable frame sheets + frame-clock metadata (GPU copy/compute), already
computed CPU/dynamic pixels (one-time/frequently staged Vulkan transfers),
and texture lifetime/reload/invalidation. No per-sprite feature allowlist is
the desired steady-state design; unsupported or non-reproducible custom
CPU semantics must retain an exact fallback at the generic input boundary.
GPU texture *storage and transfer* does not mean arbitrary Forge shader/mod
pixel logic can be automatically executed on the GPU.

The first source-only whole-texture-tick pilot extends the existing shared
VTextureSelector GPU transfer batching from 32 to 128 regions under
`-Dvulkanmod.experimentalUnifiedTextureUploads=true`, using a half-open
destination overlap test and explicit write dependency to preserve correct
per-texel writes. A Java 17 geometry harness passed locally and is
registered in the 20-contract offline suite. The native screenshot smoke
now opts in and verifies 48 disjoint writes plus one overlapping same-command
write by exact pixel readback and synchronization validation; this native
gate is **source-added but unexecuted** until the next qualified build. The original limit remains the default. It is not a new
animation source/interpolation implementation. Existing #1014 measurements
(1,009,923 small flushes, 5.727 ms/tick inclusive in texture CPU time)
motivate the experiment but **do not prove** how many calls the wider grouping
will save; non-capacity flush boundaries continue to exist. The original
copy-flush-reason diagnostic candidate remains relevant to this decision.

The broader design requires a **bounded shared immutable GPU source pool**
(atlas/sheet lifetime, safe eviction/retirement) and a **per-atlas GPU
animation job queue** that aggregates discrete and interpolation jobs with
correct mip, overlap, mutation, ordering and Forge fallback semantics.
O3/O4's existing exact interpolation shader and native pixel oracle should
be reused, not reimplemented. This is a future implementation gate, not
claimed as finished. Qualification needs: Java/Forge compile, native Vulkan
synchronization/pixel oracle for sequential overlapping writes, modpack
compatibility gate, and matched RD32 CPU/GPU capture with exact fallback and
copy-flush cause denominators.


## Source-only shared GPU residency — October 11 continuation

**Implemented but unqualified:** Opt-in
`vulkanmod.experimentalGpuTextureSourcePool` provisions immutable animation
source sheets into 4 MiB shared GPU buffer pages (larger dedicated page for
large sheets) with device-aligned leases. The same resident-source copy path
adds the base offset to its validated VkBufferImageCopy addresses; the
existing float64 compute shader continues to index mip bytes relative to
the slice through an offset/range-limited Vulkan descriptor. O3/O4 source
identity, mutation invalidation, the old 128 MiB active-source/scratch
budget, CPU fallbacks, and texture atlas clocks are preserved. GPU pages
have a separate 128 MiB physical cap.

The page allocator is append-only, with the entire GPU buffer retired only
when all slice leases close and its upload-safe retirement completes. This
prevents reuse-before-GPU-completion but may exacerbate fragmentation or
delay budget release. The CPU Java 17 allocator contract passes; the
expanded actual Vulkan native oracle now checks distinct source offsets
for O3 copies **and** O4 RGBA interpolation (RGB blended, current alpha
retained) and is included in the screenshot smoke under validation.
**It has not run**; no Forge compilation, native validation or matched RD32
performance evidence exists. Source pool memory/lease/capacity counters are
included in the benchmark summary; these are renderer-lifetime counters,
including warmup and pending retirement.

Next implementation phase, **not yet claimed**: aggregate GPU animation
commands by shared source page and atlas while preserving chronological
ordering with CPU-generated uploads, barriers, Vulkan format/mip bounds,
and source lifetimes. This first requires the present source-pool native
pixel/sync oracle to pass; enlargement alone is not proof of CPU time
reduction.


## Unified GPU-resident and CPU-staged atlas-copy engine — October 12

**Source-only, not correctness qualified or a proven improvement.**
The experimental whole-tick queue now accepts both CPU staging regions
(tightly packed) and O3 resident sheet regions (source-sheet stride and
per-slice base offset) via the same immutable fixed-array `VkBufferImageCopy`
emitter. Adjacent O3 source leases residing in one buffer page and
targeting one atlas can coalesce, reducing the number of source-copy
commands even though clocks/frame selection remain on the CPU. O4
compute remains one dispatch per eligible interpolated sprite; it drains
pending atlas copies before dispatch. The target is generic transfer
mechanics, not an ever-growing sprite-specific allowlist.

**Correctness intervention:** pending-rectangle overlap logic alone was
insufficient, as earlier flushes and the initial direct texture upload
lost their geometry history. Conservative transfer WAW barriers were
introduced at those opt-in flush/direct boundaries; the native oracle
tests 160 regions, an earlier flushed-overwrite and direct-overwrite,
plus shared source-page O3-to-O4 ordering and exact pixel parity. It
has not run. Barriers may reduce GPU concurrency, so matched GPU batch
timing is mandatory to judge the result. Real hardware benefit is
**unknown**.

Source-only benchmark metrics `resident_queued_copy_calls/regions`
and `resident_direct_copy_calls/regions` distinguish acceptance into
the queue from fallback to direct O3 commands. In this opt-in mode,
`copy_flushes/regions` refer to **mixed CPU-staged/GPU-resident
Vulkan transfers**, unlike #1014's CPU-staged-only data; these
denominators are deliberately not treated as a paired comparison.

**Next gate:** Java/Forge compile and all preexisting native screenshot,
sync-validation, animation oracle, Create/Flywheel and packaging gates.
Then RX 6900 XT matched captures if the pilot is kept; do not turn
on the experimental flags by default before those results.

**October 16 correctness follow-up, native qualification pending:** source
review found that O4's pre-copy atlas barrier did not cover a later O3/CPU
overwrite of the O4 result. The unified pilot now completes external O4
and direct resident copies with the same conservative WAW dependency as
flushed queue groups. The shared-source native oracle exercises the reverse
overlapping O4-to-O3 boundary and repeated O4 scratch use as well as the
existing O3-to-O4 boundary. All 21 offline contracts pass locally, including
the corrected reason-aware upload cleanup fixture. Native/Forge/full-pack
qualification and net hardware benefit remain pending; no adoption claim
or increased memory budget follows from this repair.


## Full native qualification outcome — October 16

**Disposition: qualified but unmeasured.** Full CI **#1018**, executable
`db77ff5c5232e18eb6a696a691c5ae01ed660117`, run `38899462146`, validation
job `116968655497`, passed all 21 offline contracts, Forge production
packaging, all existing native/synchronization oracles, combined Create
Chronicles and Crash Assistant, with log/JAR uploads. Private real-pack
fixtures skipped. #1017 was superseded/cancelled. This supersedes the
source-only/unqualified status of the unified queue, source pool and
October 16 ordering repair above; it does not establish a hardware gain.

The shared-source oracle proved nonzero descriptor-offset O4 pixels,
coalesced O3 copies, O3-to-O4 flushing, overlapping O4-to-O3 ordering and
repeated scratch dispatch. The wider queue oracle passed capacity-flushed
and initial-direct-write overwrites under synchronization validation.
Two opt-in flags activate the complete pilot:
`vulkanmod.experimentalUnifiedTextureUploads=true` and
`vulkanmod.experimentalGpuTextureSourcePool=true`, alongside O3/O4.

Next disposition-changing evidence is the RX RD32 candidate capture and
same-JAR control with only these pilots disabled, as documented in
`GPU_TEXTURE_RD32_HARDWARE_TEST.md`. Quantify source/temporary routes,
interpolation fallbacks, queued/direct copies and actual flush causes,
page-capacity/retirement effects, CPU tick/frame and separate GPU upload/
main-frame times. Three matched pairs remain the performance-claim gate.
Do not implement another scheduler, raise budgets or enable defaults from
native qualification alone. Full GPU compute dispatch batching remains open.

## Owner-requested broad CPU graphics census — October 16

**Diagnostic implementation, full qualification pending.** The exact existing
stage/route counters do not name arbitrary third-party/worker CPU call sites.
The benchmark now adds JFR execution and native-boundary stacks, categorized
with separate render/graphics-worker roles and retained unknown render-thread
samples. Bounded ranked paths are printed in the benchmark log; the rolling
raw JFR preserves retained background and overflow stacks for reclassification.
Missing/zero/overflow/unavailable states are explicit. Native API samples do
not establish driver-internal compute or GPU time; sampling is not exhaustive.
Use the existing measured stage/route/fallback counters to test a causal
offload hypothesis after locating a hotspot. No renderer speedup or new GPU
capability is claimed. All 22 offline contracts pass locally, including real
Java 17 JFR and stripped-runtime fallback. Full Forge/native startup activation
and raw-dump assertions are the next qualification gate; representative RX
coverage and observer cost remain unmeasured. See `PERFORMANCE_PROFILING.md`.


**CPU census qualification outcome:** full public/native **#1019** at
`ce9d66e1219e13139e3b0b6b7de531082f7a04c7`, run `38972838076`, job
`117215415389`, passed all 22 offline contracts, Forge packaging, real-Forge
JFR activation and orderly-exit raw dump, all retained native pixel/sync gates,
combined Create Chronicles, Crash Assistant and artifact uploads. Private-pack
fixtures skipped. This supersedes the pending qualification above. Disposition
is **qualified diagnostic, representative coverage/observer cost unmeasured**;
no renderer speedup follows. Use #1019 for the existing RX gate, return its raw
JFR as well as the UUID log, and keep sampler configuration equal in A/B.


## #1023 first same-JAR OFF/ON bulk CPU comparison — October 17

OFF owner UUID `5c61d553` vs ON `252df7a2`: both qualified
#1023/`bd2cc13a`, unchanged RD32/framebuffer/GPU pilots; zero
bulk CPU updates OFF versus 2,768,429 ON. Texture CPU
**17.984→14.704 ms/tick**; non-upload texture
**10.684→7.282 ms/tick**; complete client tick
**30.264→26.809 ms/tick**; throughput **37.03→43.47 FPS**
(+17.4%) favors ON. CPU JFR pixel/transfer category 2,348→1,249
relevant Java samples; GPU main graphics 3.191→3.173 ms/frame
and separate texture-upload 3.218→3.207 ms/batch are flat.
**Disposition: production-adopted, positive conditionally matched
OFF/ON pair 1/3; default-promotion benefit still unqualified.**
The owner explicitly instructed us to assume restored benchmark-world
snapshots; this is not independent log evidence. Require **two further
alternating restored-world pairs** before default adoption.
No new code/JAR/budget change now. See
[`RX_RD32_CAPTURE_1023_BULK_CPU_AB_2026-10-17.md`](RX_RD32_CAPTURE_1023_BULK_CPU_AB_2026-10-17.md).

## #1023 CPU bulk-ON RX adoption — 2026-10-17

Owner UUID `252df7a2`, #1023/`bd2cc13a`: valid 180.020 s /
7,825 frames, **100% bulk on the 2,768,429 recorded standard
CPU fallback updates**; GPU interpolation 111,838/2,880,267
standard attempts (3.88%, remaining largely residency-disabled).
Texture CPU 14.704 ms/tick, upload GPU 3.207 ms/batch and
main GPU 3.173 ms/frame. Compared with unpaired #1021
17.950 ms/tick and ~35.92 FPS, ~43.47 FPS current is only
favorable screening. **Disposition: correctness-qualified and
hardware-adopted, matched net benefit unmeasured.** No default
promotion. Next: same-JAR OFF control, three alternating
restored-world pairs. [Detailed evidence](RX_RD32_CAPTURE_1023_BULK_CPU_ON_2026-10-17.md).

## #1021 RX terrain adoption and bulk CPU fallback candidate — October 17

UUID `f4741fc5` on #1021/`8ff928dd` satisfies the requested single adoption
capture. 439 full traversals / 6,457 baseline-valid frames, 785 cached rebuild
admissions and 1,604 retained dirty notices establish scheduler adoption.
Terrain setup 0.907 ms/frame vs historical 4.221 ms is favorable **unpaired**
evidence. Disposition remains **qualified but unmeasured** for formal net benefit.
No repeat unchanged capture is needed.

Textures still cost 17.950 ms/tick. GPU interpolation covers 83,951/2,875,168
updates (2.92%); disabled residency is the main fallback. Actual vanilla pixel
stacks account for 2,213/9,203 raw render-thread execution samples inclusively.
New opt-in `experimentalCpuSpriteInterpolation` uses validated native rows only
after GPU refusal, retaining exact arithmetic/source/upload/clock semantics and
custom fallback. CPU production-kernel oracle passes; full Forge/native pixel/
synchronization qualification is pending, so disposition is **blocked** on that
gate and hardware benefit is **unmeasured**. No GPU cap/admission/default change.
See `RX_RD32_CAPTURE_1021_AND_CPU_INTERPOLATION_2026-10-17.md`. Next discriminator:
native qualification, then same-JAR flag off/on with bulk-update coverage and
matched whole-texture/tick/frame results. Particle and block-entity work is not
combined into this candidate.


Remote qualification update: implementation committed locally as `909497a`;
automatic approval review rejected remote push for lack of explicit remote-write
authorization. Full/native CI has not run. Next action is explicit push
authorization, then normal full qualification; no hardware test requested yet.

The owner subsequently authorized publication and CI. The initial approval block
is superseded; the connected GitHub API publishes the implementation because the
shell Git transport lacks credentials. Full/native qualification remains pending.


### Bulk fallback qualification completed — full #1023

Executable `bd2cc13afb74b6468acd586e24729e4795d1924a`, run `39073019680`, job
`117514597700`, passed all 23 contracts, Forge packaging and all public/native
pixel/synchronization/compatibility gates; private packs skipped. The real
transformed ticker executed 56 bulk frames with exact oracle pixels/clocks/
cadence and zero bulk adoption in the custom subclass. First attempt #1022
failed a demonstrated invoker/injection refmap conflict, fixed by isolating the
invoker; that negative result is retained. Publication was explicitly authorized;
the earlier approval and native-qualification blocks are superseded.
Disposition: **qualified but unmeasured**. Artifact `12116211650`, #1023.
Next: same-JAR flag false/true with bulk numerator, CPU denominator and complete
texture/tick/frame evidence. No hardware benefit, broader GPU adoption, default
or deferred lifecycle qualification is claimed.
