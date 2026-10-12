# Optimization effectiveness ledger — October 10, 2026 reality check

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
