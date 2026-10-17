# RX #1023 bulk CPU interpolation ON/OFF first control — October 17, 2026

## Disposition

**Positive first same-executable screening pair. Do not promote the opt-in
CPU path to default yet.** Both captures identify qualified Forge/native #1023
executable `bd2cc13afb74b6468acd586e24729e4795d1924a`, Minecraft 1.20.1 /
Forge 47.3.0, Java 17.0.15, RX 6900 XT/RADV, fixed 2552×1374, RD32 / simulation 12,
VSync off, same world/dimension/pose, texture source pool/unified queue and
GPU copy/interpolation enabled. GPU float64 was available in both.
The only *intended* setting difference was
`-Dvulkanmod.experimentalCpuSpriteInterpolation=false/true`.
Benchmark outputs prove the behavioral switch: OFF records **0** bulk
CPU updates; ON records **2,768,429 / 2,768,429** standard CPU interpolation
updates using bulk. The files alone do not prove that the owner restored an
identical pre-run world snapshot or that every other launcher JVM argument
matched. Treat this as one strong screening pair, not three restored-world
replicates or a closed Phase 5 matched-baseline gate.

Both runs have `capture_complete reason=benchmark_complete`; world,
screen, pose and framebuffer stayed stable, with initial-population settling
before capture. They cover roughly 180 seconds each; both begin with large
catch-up hitches. Both JFR files are readable using `jfr summary`;
no Vulkan VUID/validation error appears in the adjacent supplied logs.
No owner visual statement about animated texture flicker or staleness
was provided; do not infer visual qualification from log success.

| Scope | CPU bulk OFF control | CPU bulk ON candidate | ON versus OFF |
| --- | ---: | ---: | ---: |
| Run UUID | `5c61d553` | `252df7a2` | same #1023 executable |
| Capture duration (s) | 180.009 | 180.020 | near-equal |
| Total captured frames | 6,666 | 7,825 | +1,159 |
| Whole-capture throughput (FPS) | ~37.03 | ~43.47 | **+17.4%** |
| Texture leaf mean (ms/client tick) | 17.984 | 14.704 | **−3.280 (−18.2%)** |
| Texture leaf p95 (ms/tick) | 24.163 | 20.351 | **−3.812** |
| Non-sprite-upload texture remainder (ms/tick) | 10.684 | 7.282 | **−3.402 (−31.8%)** |
| Sprite upload timed scope (ms/tick) | 7.300 | 7.422 | +0.122 |
| Complete client tick mean (ms/tick) | 30.264 | 26.809 | **−3.455 (−11.4%)** |
| Complete client tick p95 (ms/tick) | 38.205 | 34.885 | −3.320 |
| Inclusive copy-flush CPU mean (ms/tick) | 8.255 | 8.260 | ~unchanged |
| Separate GPU texture-upload batch mean (ms/batch) | 3.218 | 3.207 | ~unchanged |
| Main graphics command-buffer GPU mean (ms/frame) | 3.191 | 3.173 | ~unchanged |
| Texture pixel allocation (KiB/tick) | 244.916 | 238.791 | small difference |
| Standard CPU interpolation updates | 2,753,048 | 2,768,429 | near-equal |
| CPU bulk updates (subset of preceding row) | **0** | **2,768,429** | route toggles correctly |
| GPU interpolation updates | 118,831 | 111,838 | small adoption difference |

The complete client-tick improvement closely tracks the texture-leaf change
(3.455 vs 3.280 ms), and nearly all texture reduction occurs outside the
sprite-upload body (3.402 ms). Upload timing actually rose slightly with bulk
ON, so an upload-route regression does not explain the improvement.
The exact amount should not be treated as a per-frame saved duration;
Minecraft runs multiple client ticks on some frames. Texture copy-flush is an
**inclusive overlapping** scope and must not be added to texture/upload times.
The separate upload GPU timestamp includes copies/compute/dependencies in
its own graphics batches, not CPU staging or the main graphics command buffer;
GPU timings are not frame totals and should not be summed.

## Workload and late-run checks

Both runs sampled ~3,600 client ticks (OFF 3,584, ON 3,589), about 2.87–2.88
million standard interpolation updates and ~4.4 million materialized sprite
uploads. OFF: 4,400,707 sprite requests / 21,715,511 mip copies; ON:
4,422,566 / 21,823,650. Particle engine work is similar:
OFF 29,912,571 particle ticks; ON 29,968,385. Particle tick mean differs
slightly (OFF 4.748, ON 4.482 ms/tick), but this is smaller than the texture
delta. The resident GPU path is still capped near 128 MiB in both:
OFF 474 admissions/1,246 capacity rejects; ON 463/1,257.
GPU interpolation covers only 4.14% OFF versus 3.88% ON,
with residency-disabled fallback dominating. The CPU bulk path does
**not** repair low GPU residency coverage.

Late capture behavior rules out the explanation that only early catch-up
changed throughput. Window-weighted frame throughput after ~60 seconds:
OFF ~40.98 FPS, ON ~47.66 FPS. After ~120 seconds:
OFF ~41.01 FPS, ON ~48.40 FPS. These are grouped-window rates, not
an exact global p95/p99 reconstruction or matched-frame distribution.
Startup catch-up is still material: first 5-second window averages
417.075 ms/frame OFF vs 461.890 ms/frame ON, with maximum frame
1,423.944 ms OFF and 1,221.384 ms ON. Both runs have GPU marker
reconstruction fully valid: OFF 6,666/6,666; ON 7,825/7,825;
no missing, invalid or dropped terrain-marker frames.

## JFR corroboration, not causal time attribution

The OFF raw JFR has 23,384 `jdk.ExecutionSample`, 17,550
`jdk.NativeMethodSample`, and 180 server-tick events. The ON raw JFR
has 23,140, 17,541, and 180 respectively; raw counts differ from
capture-classified counts because retained samples may straddle the
admitted window. Profiler-classified relevant Java samples:
OFF 9,489 / ON 9,601. Within those, exclusive
`render/texture_pixels_transfers` owner categories are **2,348 OFF
versus 1,249 ON**. Top individual OFF method sample:
`sun.misc.Unsafe.putInt` **1,074**, compared with **732** ON;
`sun.misc.Unsafe.getInt` top methods have **336 OFF** vs **172 ON**
samples. The OFF result additionally surfaces native pixel-access
checks and the vanilla `InterpolationData` loop. Sampling
frequency corroborates significantly greater CPU pixel-loop activity
OFF, but individual stacks and owner categories are *not method
milliseconds*, and may contain inclusive nesting or sampling noise.
Both captures have 0 missing-stack samples; OFF 546 truncated,
ON 604. OFF recorded no stack-cap overflow; ON recorded 61.

## Decision and remaining gate

This pair provides **measured production activation, substantial favorable
CPU reduction in the predicted scope, corroborating JFR, near-identical GPU
timing, and higher whole-capture FPS**. The original causal hypothesis is
strongly supported; no new code fix is indicated by the control result.
The experimental path remains off by default; the formal multi-pair,
identically-restored-world benefit criterion is open, as are private packs,
deferred reload/re-entry and independent visual observations.

Next, **do not rebuild a JAR or repeat this unchanged ON/OFF screening
pair without restoring the benchmark world**. For formal default adoption,
use the same qualified #1023 JAR, snapshot-restored worlds, identical
modpack/launcher settings, a single changed CPU-bulk flag and the same
10-ms JFR sampler; obtain three alternating OFF/ON pairs in total.
If the owner confirms that this first pair used an identically restored
snapshot it can count as pair one; otherwise it is screening only.
Comparisons must cover full tick/texture/non-upload, GPU upload and
graphics scopes, late-run frame distributions, and any animation errors.
No other texture/GPU budget change should be bundled into that comparison.

## Original owner upload provenance (SHA-256)

OFF, UUID `5c61d553`:
- benchmark: `23b8efba36e8f2b52caff72706a26315d2fe6d91375eaa4153e69f18349bda76`
- raw JFR: `ae15aeb9db95c97732ce2674247fe6156bcef95de89560ffd0b8060457a30b06`
- latest: `2f9430289b038e8eaf0e30b369d85f12d5e8ec39d670922e3c051d7cdda0bb82`
- debug: `3c49327fbb6a48e9e572d7ada123ffef54eff9acb64456d6c4ea945ec0b0c9ce`

ON, UUID `252df7a2` (previously received):
- benchmark: `408ac485f7ae478a19de756f764b1624dfac58cd188364decc4d4508883cf7d6`
- raw JFR: `0cdd31542522ca6b7b9b5a723ffe4c6a7dba33dd485fd9975b4a6e75c103a148`
- latest: `bae8ec97aa5f25ff3cdb66f6975b4de3191bf5394cc0f8da8878cf0911faa2a1`
- debug: `c39eb55c95d8fa4a7670ac7ef87505893f2e331c8678f88dafde18d06e872b47`

Raw logs and JFR remain attached to the user's ChatGPT sessions, not
committed to public Git.
