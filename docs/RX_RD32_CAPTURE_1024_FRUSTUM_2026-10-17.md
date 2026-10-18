# RX RD32 #1024 opt-in particle-frustum fast-path — 2026-10-17

## Received artifacts and run validity
- Owner runtime UUID: `1851e3c8-ccef-42ed-9191-126d1d45ae77`.
- Distributable: `0.3.2-forge.2-build.1024-g3f77cab2`; qualified executable `3f77cab2941f60a0a935b2f609f28317072b59f9`, full public/native/Forge CI [run 39078815371](https://github.com/Vorith03/VulkanMod/actions/runs/39078815371).
- `capture_complete reason=benchmark_complete`, 180.022 seconds, 9,167 frames. Automation measured world=`VulkanMod_Benchmark` spectator xyz=0,192,0 yaw=-90 pitch=30; RD32, simulation 12; fixed framebuffer 2552x1374; 0 pose/framebuffer changes, no screen frames; VSync false; `frame_limit=0`. Settling 71.881 seconds, initial population stable.
- AMD RX 6900 XT / RADV; Java 17.0.15; main GPU timing scope is `main_graphics_command_buffer`, *not* total GPU frame/present.
- GPU timestamps: all 9,167 frames measured, 0 drops/failures; coarse GPU pass reconstruction valid for all. Texture upload GPU: 3,480/3,480 batches, 0 read failures.
- JFR: 180 seconds; `jfr summary` validates 22,945 execution and 17,565 native-boundary events. Classification admitted 9,347 relevant execution samples, 0 missing stacks (some truncation); raw file independently decoded through JFR RecordingFile. No found VUID/device-loss/synchronization validation errors in supplied latest/debug. Some mod/POI/server overload warnings occur; do not confuse absence of GPU errors with general log cleanliness.
- **Activation evidence:** `debug(20261018-001334).log` contains `Mixing render.FrustumInfiniteBoundsFastPathMixin from vulkanmod.mixins.json into net.minecraft.client.renderer.culling.Frustum`. Actual #1024 JFR has zero `net.minecraft.world.phys.AABB.equals` frames in Render-thread particle rendering; 3 samples contain the opt-in `FrustumInfiniteBounds` helper. `standard_interpolation_cpu_bulk_updates=2,810,771` of `2,810,771` CPU updates confirms the existing bulk texture setting stayed active; O3/O4 flags enabled in environment.

## Historical before/after and interpretation

The owner selected the already supplied #1023 **bulk CPU ON** UUID `252df7a2-f803-4d4c-b174-f11f83b60a07` as the before capture and authorized assuming restored benchmark-world snapshots. Both captures used the same nominal modpack, framebuffer/camera, RD32/simulation 12, GPU flags, bulk texture path and 10-ms JFR. The executable changed; restoring actual snapshot bytes cannot be independently verified.

| Metric | #1023 bulk ON | #1024 frustum fast path ON | Observed |
| --- | ---: | ---: | ---: |
| Full capture frames / duration | 7,825 / 180.020 s | 9,167 / 180.022 s | ~43.47 to ~50.92 FPS (+17.15%) |
| Particle rendering CPU ms/frame | 4.196 | 3.776 | -0.420 (-10.0%) |
| Particle tick CPU ms/tick | 4.482 | 4.456 | -0.026 |
| Complete client tick CPU ms/tick | 26.809 | 26.066 | -0.743 |
| Texture CPU ms/tick | 14.704 | 14.095 | -0.609 |
| Texture sprite-upload CPU ms/tick | 7.422 | 7.812 | +0.390 |
| Texture non-upload CPU ms/tick | 7.282 | 6.283 | -0.999 |
| Main-graphics GPU ms/frame (limited scope) | 3.173 | 2.890 | -0.283 |
| Texture upload GPU ms/batch (separate scope) | 3.207 | 2.914 | -0.293 |
| JFR particle render samples (all Render-thread stacks) | 2,910 | 3,138 | Sampling cohorts, **not time** |
| Particle-render `AABB.equals` sampled leaves | 764 | 0 | Hot equality path disappears |

The complete old (#1023) JFR/statistics were previously analyzed in `docs/RX_RD32_CAPTURE_1023_BULK_CPU_ON_2026-10-17.md` and `docs/FRUSTUM_INFINITE_BOUNDS_FAST_PATH_2026-10-17.md`. New JFR sample counts were independently obtained from raw `jdk.ExecutionSample` events rather than relying only on the top-40 call-stack list.

**Interpretation:** The target mechanism is active, the high-count equality sampling hotspot disappears, and independently timed particle render work is lower. Since only about 0.420 ms/frame of directly observed particle-render CPU is removed and other CPU/GPU costs also change, **do not attribute the whole 17.15% throughput difference to this equality change**. Different world/JIT/server scheduling, workload and startup transients are confounders. This is favorable cross-executable screening, not a same-JAR OFF/ON or formal multi-pair benefit. No separate owner visual confirmation was supplied; do not claim smoke/particle correctness from timing or automated CI alone. Keep opt-in absent by default.

## Current residual profile and next engineering target
- Texture `TextureManager.tick` is the largest measured tick leaf: 14.095 ms/tick, comprising sprite uploads 7.812 and non-upload 6.283 ms/tick. Inclusive `copy_flush_cpu_ms_avg=8.084` overlaps texture/upload measures, never add it as another separate cost. 22,158,433 regions, 220,055 flushes, 800,795 commands. Most GPU interpolation attempts still use the CPU path (2,810,771 vs 70,132 GPU; near-capacity residency). Do not automatically raise the GPU residency budget.
- Particles: tick 4.456 ms/tick, ~4,880 KiB/tick allocation. CampfireSmokeParticle: 29,471,618 of 30,127,224 tick calls (97.82%); 277,291 smoke additions; 316,781 particle additions total.
- Particle-render raw JFR cohort 3,138: 1,459 stacks contain `BufferBuilder`; 811 contain `LightEngine` or `ClientLevel.onHasChunk`, including 423 leaves specifically in the modded hasChunk handler; 82 leaves in `Frustum.m_113029_`. AABB.equals has 0 leaves. These stack categories can overlap; they are **samples, not milliseconds or CPU-offload feasibility**.
- World render CPU averages 8.257 ms/frame; particle rendering 3.776, block entity stage about 2.1 ms/frame. GPU main-graphics 2.890 ms average has no GPU backlog indication by itself, and excludes texture upload GPU and present. Steady-state last ~35 s reports 1,948 frames / ~34.7 s (56.1 FPS) versus full-capture 50.92, so use aligned intervals before claims.
- Terrain: 6,559 visible sections, no ongoing worker queue, 1,604 retained dirty notices, 2,048/2,048 voxel staging entries with CPU/GPU terrain mesher disabled. Distant Horizons OpenGL LOD draw remains explicitly suppressed under Vulkan; this is not a newly fixed compatibility issue.
- Next work should first inspect exact patch/mixin ownership, correctness and repeated-work mechanism for particle light/chunk lookups or geometry emission (or the remaining texture upload/remainder) before selecting a bounded fix. Preserve Forge/Immersive Portals and particle visual semantics; do not replace `hasChunk` or suppress lighting by assumption. Do not request redundant #1024 OFF or historical #1023 baseline. No new hardware test is required until an executable change needs qualification.

## Raw owner evidence fingerprints (logs/JFR are not committed)
- Benchmark: SHA256 `6ebefa0cec72931f5a7a25a702114f97986573306cb8572be8d5c3c11bd5fd83`.
- Raw JFR: SHA256 `cc9753eeec42e43840af8c57e6af0ae6e819fd37137e5a8a52655c4933bb41e7`.
- Latest: SHA256 `8bb92849f68356d003825ccd0e99fd32979db71b8cc1ade143b27acd9eb9d69d`.
- Debug: SHA256 `cd413501787de1236979c7eadfb33f5e8c79a7f3887b8d89d49e22a5cb8d01c5`.
