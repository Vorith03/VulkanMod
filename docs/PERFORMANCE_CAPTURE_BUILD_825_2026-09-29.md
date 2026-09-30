# Build #825 automated RX stationary diagnostic (2026-09-29)

## Identity and scope

User files: `vulkanmod-performance-stationary(5).log`, `latest(5).log`, `debug(2).log`. Capture UUID: `6da1a30e-ef2d-49f6-b76a-547bff8ca0d1`. Loaded executable: `0.3.2-forge.2-build.825-g3a0e6713` (`3a0e671388a97857b12e87b7aa786630d9178c64`). Live branch at analysis: `c2dd7aaf70c18d4cfa991b24993617938bf66c89`; its only delta from that executable is documentation. CI #825 / run `36666321378` was independently checked: build/distributable and public smokes passed, three private-pack steps skipped.

RX 6900 XT / RADV NAVI21, Linux, Java 17.0.15, 16 logical CPUs. GPU mesher, CPU bypass, draw handoff, hybrid and voxel staging were enabled. All 36 windows have render distance 16, simulation distance 12, VSync false, reported FPS cap 260, framebuffer **2552×1374**, player present, no screen, and unchanged `0,192,0 / -90,30` pose. Accounting overlap/unbalanced counters are zero; all 3,601 tick-bearing frames have CPU/allocation samples and one tick call.

This is one stationary diagnostic, not a completed matched Phase 5 baseline. The reported cap differs from the unlimited benchmark contract, and exact seed, pristine-copy provenance, full graphics/resource-pack profile and JVM memory arguments are not all proved by the capture. Frame cost is CPU wall time, not GPU execution or presentation time. Do not average window percentiles to invent a pooled p95/1% low.

Source SHA-256 values:

| File | SHA-256 |
| --- | --- |
| `vulkanmod-performance-stationary(5).log` | `f767c252486464aa1f07eea587d4c90aaf4e55a118137c67277adc0faa8465d2` |
| `latest(5).log` | `637d5b8a332f87bb1ed8e02456ec13ee4f82e71a4813f71d8cd7fde3125bb5ae` |
| `debug(2).log` | `76bbbd4020ab8a9804263dd000a7e36754c706ef61855578eed391c56adf6d4d` |

Raw logs remain user evidence; do not copy their private paths/account/configuration into the public repository.

## Automation result

At 21:37:33.206 local log time, terrain was visible and the 60-second settle began. Measured capture began at 21:38:33.208. `benchmark complete` reports measured_s=180.020, staging_cap_seen=true and after_cap_s=225.532. `capture_complete reason=benchmark_complete` records 31,466 frames. Thus teleport/pose readiness, measured capture, cap-duration gate and successful output completion are observed on the RX machine.

Normal teardown saved all dimensions at 21:41:36.873 and reached `Minecraft: Stopping!` at 21:41:37.410. No native double-free appears in these supplied files. They do not include the launcher's process exit status or native stderr, so this does not close the separate #801 native-shutdown issue or prove exit code zero.

## Performance findings

Numbers below weight per-window means by the appropriate frame class. FPS uses summed frames divided by summed measured window durations (179.956 seconds overall); whole capture duration includes approximately 63 ms of excluded summary output. Actual whole-capture throughput is similarly about 174.8 FPS.

| Metric | All 36 windows | Final 12 windows (~60 s) |
| --- | ---: | ---: |
| Measured throughput | 174.9 FPS | 208.9 FPS |
| Tick-bearing frame mean | 28.49 ms | 27.30 ms |
| Render-only frame mean | 2.77 ms | 2.40 ms |
| Client tick wall time / tick-bearing frame | 24.17 ms | 23.63 ms |
| Client tick CPU / tick-bearing frame | 23.96 ms | 23.46 ms (approximately) |
| Unnamed tick remainder / tick-bearing frame | 20.66 ms | 20.31 ms |
| Entity tick / tick-bearing frame | 3.46 ms | 3.30 ms |
| Game render / all frames | 2.805 ms | 2.386 ms |
| Terrain setup / all frames | 0.240 ms | 0.002 ms |
| Render-thread allocation rate | 316.6 MiB/s | 312.5 MiB/s |

**2,669 of the 2,670 frames above the 25 ms threshold contain a tick**; only one slow frame is render-only. There are 3,601 tick-bearing frames in 180 seconds, consistent with the 20 Hz client cadence. Roughly 85.5% of tick wall time is `client_tick_other`. Level tick (~0.004 ms), renderer tick (~0.034 ms) and connection tick (~0.002 ms) are small; entity tick is the main named child. The unnamed portion includes uninstrumented vanilla/Forge/mod work; this capture cannot name the responsible callback or mod.

Tick CPU almost matches wall time, and the render thread uses approximately 98.9% of one CPU core over the measured windows. This supports a sustained CPU-work bottleneck. GC totals 209 ms over the whole run, which cannot explain thousands of recurring ~25–35 ms tick-bearing frames. Individual isolated pauses still lack exact GC attribution.

Allocation is an additional investigation target: tick-local allocation averages 3,860 KiB (~3.77 MiB) per tick-bearing frame, about 75 MiB/s at this cadence. Subtracting those samples from window-wide render-thread allocation leaves approximately 241 MiB/s outside the timed tick (~1.38 MiB per rendered frame). This remainder includes rendering, frame/loop work and profiler summaries; it is not a renderer-only allocation probe and identifies no allocating class.

Submission/fence/upload CPU costs remain small: frame fence ~0.002 ms/frame, image acquire ~0.023 ms, queue submit ~0.032 ms, present ~0.010 ms, terrain uploads ~0.0004 ms. These are not GPU timestamps and do not establish zero GPU cost. The recurring slow frames are explained by the measured tick CPU work before any GPU-pass instrumentation is necessary.

Per-window frame p95 ranges 23.716–35.706 ms; the maximum frame is 66.922 ms. The first 12 windows average 132.0 FPS, while the final 12 average 208.9 FPS. The view therefore still evolves after the fixed settle: scheduling counters rise until around window 17, and terrain_setup falls from 1.075 ms in window 1 to ~0.001–0.003 ms from window 18 onward. This association is not proof of the underlying cause. Do not pool this transient with the final slice to imply a single steady-state renderer cost or compare it as a speedup against #806, whose in-world resolution is unknown.

Summary overhead totals 63.051 ms: first summary 10.021 ms, most late summaries roughly 1.2 ms. It is excluded from frame/loop timing but can affect real presentation cadence. Zero overlap counters and small unaccounted (~0.029 ms/frame overall) establish consistent broad accounting, not complete function-level coverage.

## Terrain saturation result

Staging is already pinned at **2048/2048 entries**, approximately 22,151/32,768 KiB, throughout capture. The entry limit is full; the byte budget is not.

From window 1 to window 36, accepted builds rise 6,178→6,210 (+32), staging rejections 944→968 (+24), worker preflight-full 939→957 (+18), input-publication rejections stay **2**, and CPU recoveries stay **11**. Workers/queues/publication are idle at the sampled boundaries. The large #806 publication-rejection/recovery cycle is absent in this run despite the same full entry cap. This supports the intended preflight containment; different coverage/workload prevents an FPS A/B claim.

Scheduling totals rise 292,614→354,053, mainly in the first ~85 seconds. Do not equate these counters with completed builds or treat lifetime latency averages as per-window worker timings.

## Separate runtime failure and next action

At 21:36:26.366, before capture, SoundEngine reports `java.lang.IllegalStateException: Failed to open OpenAL device` from `com.mojang.blaze3d.audio.Library` and disables sound/music. Both logs contain one occurrence and the complete 41-line stack. The failure persists; these files do not identify its device/native cause. No warnings/errors recur during the measured capture. A repaired-audio run would represent a changed workload and should be labeled accordingly.

Next useful engineering slice: inspect the exact Forge-patched `Minecraft.tick()` contract and existing probes, then use bounded sampling or targeted callback instrumentation to attribute the ~20.7 ms unnamed tick remainder and high allocation rate. Do not blanket-instrument every mod, tune GC first, or optimize Vulkan submit/fences on this evidence. No repeat of unchanged #825 automation is required. Keep OpenGL/Vulkan matched comparison gates open until the fixed contract and counterpart run exist.
