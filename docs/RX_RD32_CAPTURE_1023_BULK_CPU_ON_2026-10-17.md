# RX #1023 bulk CPU interpolation ON — owner hardware result, October 17 2026

## Capture identity and integrity

UUID `252df7a2-f803-4d4c-b174-f11f83b60a07`; binary
`0.3.2-forge.2-build.1023-gbd2cc13a` (qualified executable
`bd2cc13afb74b6468acd586e24729e4795d1924a`, full #1023 CI).
Received benchmark, matching raw JFR, latest and debug.
**ON candidate is positively identified:** all
`2,768,429 / 2,768,429` standard CPU interpolation updates used
the new bulk path; this is not merely a requested JVM property.

Benchmark completed normally (`capture_complete reason=benchmark_complete`):
180.020 s, 7,825 frames (~43.47 FPS full-capture throughput),
initial-population settle 73.943 s; RX 6900 XT/RADV, Java 17,
fixed 2552x1374 framebuffer, RD32, simulation 12, spectator position
(0,192,0), yaw -90, pitch 30, VSync false, both O3/O4 GPU texture
pilots active. Initial capture catch-up is substantial (first
11 frames average 461.890 ms); comparisons must use identically
defined windows. FPS-cap field says 260 but measured `frame_limit=0`;
do not infer an enforced owner cap. All 7,825 GPU pass samples were
reconstructed with zero invalid frames, missing markers, or terrain
marker failures. No Vulkan VUID/synchronization error found in the
logs; pack/mod configuration and resource errors exist. Owner did
not provide a separate visual-animation observation: no visual
claim is inferred.

## Production performance and route evidence

- Complete client tick 26.809 ms/tick mean, p95 34.885;
  texture leaf 14.704 ms/tick, p95 20.351.
  Sprite-upload scope 7.422 ms/tick, other texture work 7.282.
  Inclusive copy-flush CPU 8.260 ms/tick *overlaps* these scopes:
  never sum them.
- 3,589 texture ticks, 4,422,566 sprite-upload requests;
  1,123,613 GPU and 3,298,953 CPU, including
  2,660,551 non-source/temporary CPU uploads.
- 2,880,267 standard interpolation updates: 111,838 GPU (3.88%),
  2,768,429 CPU (96.12%); **every recorded CPU one was bulk**.
  GPU fallback was primarily residency disabled (2,764,592);
  3,837 lacked an upload batch.
- Shared source pool: 31 pages / 126,976 KiB allocated,
  463 leases; charged residency 131,069.816 KiB, near
  128 MiB cap, 463 admissions, 1,257 capacity rejects,
  no source/staging rejection or invalidation. Bulk CPU does
  not change residency or GPU admission.
- Unified copy path: 244,148 flushes / 21,816,716 regions,
  639,937 copy commands, zero source-switch flushes,
  120,178 region-limit flushes. GPU upload command-buffer
  time **3.207 ms/batch** (3,467 batches); separately
  main graphics **3.173 ms/frame** (p95 4.771).
  Neither GPU span represents full frame time or additive work.
- 35 terrain windows with valid deltas: 7,814 frames,
  251 traversals, 527 cached admissions, weighted terrain
  setup 0.693 ms/frame. Separate remaining costs include
  block-entity rendering ~2.225 ms/frame, particles
  4.482 ms/client tick and ~5,248 KiB/tick allocation.
- Raw JFR independently readable by `jfr summary`:
  23,140 execution and 17,541 native boundary events before
  capture filtering, plus 180 server ticks. Classifier reports
  23,139 admitted Java and 17,539 native-boundary samples,
  9,601 relevant Java samples, zero missing stacks,
  604 truncated and 61 stack-overflow samples. Frequent
  representative stacks include WorldRenderer block-entity
  scan (1,069) and interpolation/pixel Unsafe.putInt (732).
  Samples are not exclusive method milliseconds.

## Historical comparison and next required measurement

| Measure | #1021 historical | #1023 bulk ON |
| --- | ---: | ---: |
| Captured frames (~180 seconds) | 6,466 | 7,825 |
| Texture CPU ms/tick | 17.950 | 14.704 |
| GPU interpolation fraction | 2.92% | 3.88% |
| Main graphics GPU ms/frame | 3.164 | 3.173 |
| Terrain setup ms/frame, valid windows | 0.907 | 0.693 |

The apparent ~18.1% lower texture CPU time and higher throughput
are **favorable screening, not a proved gain**: the prior run
uses another executable and evolving world state. No same-JAR
OFF control is included in these attachments. Do not promote
a default or enlarge budgets from this candidate-only capture.

**Next:** reuse the *same #1023 executable*, toggle **only**
`-Dvulkanmod.experimentalCpuSpriteInterpolation=false` for
the OFF control; preserve the benchmark-world snapshot, GPU pilots,
graphics settings, 10-ms JFR sampler and all other flags. Return
the next benchmark UUID log, raw JFR, latest/debug and any animation
observations. One restored-world OFF/ON pair screens; **three
alternating identically restored-world pairs** are needed for a
formal performance claim. Do not ask the owner to rerun this
unchanged ON candidate. No additional code/JAR is needed yet.

## Owner evidence SHA-256 (keep raw attachments outside public Git)

- benchmark: `408ac485f7ae478a19de756f764b1624dfac58cd188364decc4d4508883cf7d6`
- JFR: `0cdd31542522ca6b7b9b5a723ffe4c6a7dba33dd485fd9975b4a6e75c103a148`
- latest: `bae8ec97aa5f25ff3cdb66f6975b4de3191bf5394cc0f8da8878cf0911faa2a1`
- debug: `c39eb55c95d8fa4a7670ac7ef87505893f2e331c8678f88dafde18d06e872b47`
