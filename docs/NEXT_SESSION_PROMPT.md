# VulkanMod — Finish unsigned-limit GPU adoption qualification

Continue autonomous implementation/qualification in Vorith03/VulkanMod,
forge-1.20.1, Minecraft1.20.1/Forge47.3.0/Java17; preserve compatibility, complete
CPU fallback and default-OFF experiments. Follow current AGENTS.md §3A, live Git/CI
delta, ROADMAP, AGENT_STATUS, optimization ledger and
GPU_ADOPTION_REPAIRS_2026-10-21.md. Do not restart investigations or wait for another
continue while meaningful authorized work remains. Inspect actual worktrees and
uncommitted changes before editing; preserve overlapping work. PR#12 remains untouched.

## Exact checkpoint and immediate action

Code HEAD at this checkpoint:272b7f81f09a05e4796e008d64a33c73a5270505;
a documentation checkpoint may follow (resolve actual HEAD via live Git).
Full #1092 /39716738464 on that executable was IN PROGRESS. #1091
/39716188014 (c9b6ef70) was superseded/cancelled. FIRST inspect those runs and failure logs if red.
#1090 /39715651273 failed compilation because LWJGL3.3.1 has getters without
setters; c9b6ef70 corrected fixture writes using MemoryUtil/IntBuffer views.
Never claim a pending, cancelled, failed or older run qualifies the final executable.

Last qualified executable550a510c6f2ed833eb80e8e03a881ab708faf270,
full #1089 /39713898920 SUCCESS, JAR12398816155/logs12398058418.
Its artifact ZIP digest fc457485eef17f1f4c296c253e1d8c5c44ed5b72decc66ec98a18c6e77774adc
is not the extracted JAR SHA256 and is not a qualification for later source.

## Work already preserved and implemented

550a510c particle window patch survived interruption and passed full1089. It uses
aligned descriptor windows in existing frame vertex buffers, relative shader
indices, absolute upload/draw/barrier offsets, original CPU recovery,64-set
pending-aware quota, resize invalidation and frame retirement.

b767223c fixes the shared unsigned uint32 host-limit defect: RADV source sets X
workgroup count0xffffffff (Java -1), so old signed comparisons reject even tiny
dispatches; signed storage-range comparison also disabled texture shared scratch.
Both texture guards and particle guard now widen unsigned; standalone texture
ranges are preflighted. Bounded lifetime interpolation rejection counters and
actual unsigned limits are emitted in existing profiler output. This supersedes
the incorrect earlier assumption that <=1024 groups excludes a workgroup guard.
Source link/mechanism/safety are in the focused report. Owner installed limits
are not read back yet. No fallback, shader, allocator, terrain or default changes.

c9b6ef70 native texture/particle tests model high-bit limits with tiny dispatches
below Vulkan-required minimum capabilities and restore real values in finally.
272b7f81 additionally forces a nonzero32KiB window in the ACTUAL production
BufferUploader-owned frame buffer, requires GPU/window delta+CPU bypass, then
compares the actual drawn slice against independent geometry/UV/color/light.
Expected marker VULKANMOD_GPU_PARTICLE_WINDOW_PRODUCTION_OK. Original direct
compute, pool pressure, callbacks, CPU sorting, fallback, fences and retirement
oracles remain. Native texture tests use transformed clocks/tickers/cancellation,
source mutation, exact mip atlas pixels, shared descriptors and322+ accepted
multi jobs including bounded table reuse. Require both real execution and consumption.

Local all24 Java17/offline contracts passed. Independent pinned-LWJGL struct
probe passed and reproduced old signed failures at0x80000000/0xffffffff.
Focused Java17 compilation of all5 affected renderer/profiler classes passed
against mapped Forge/pinned LWJGL (missing Dist annotations only on reduced
classpath). Local complete Gradle dependency bootstrap timed out240s; do not
call it a full build or native pass. Native/software-runtime qualification is CI.

## Finish integrated qualification, do not stop at intermediate success

Review #1092 exact SHA and every applicable full gate. If failure, classify and
fix smallest cause, use focused checks, commit coherent slice and run full final
qualification. Do not weaken tests, remove fallback or bypass real Vulkan limits.
No further unrelated refactor or terrain architecture work is authorized/needed.
Once full final run passes, download final JAR and native logs, CRC/manifest/Java17
packaging check, extracted JAR SHA256, exact output/CPU displacement and production
window marker, synchronization error scan, terrain/indirect/world/portal lifecycle
and public compatibility evidence. Distinguish skipped private fixtures.
Record final SHA/run/artifacts/counts/remaining owner gates in focused report,
status/roadmap/ledger/contracts and rolling prompt. Do not stop at quick CI,
compilation, isolated fixtures or another plan while final qualification is feasible.

## Existing owner evidence and targeted handoff

Read OWNER_RX_6900XT_GPU_BENCHMARK_2026-10-21.md as needed. Owner2552x1374,
5800X/RX6900XT/RADV,Create Chronicles~302mods,D32/S12,VSyncOFF,cap260,fixed
VulkanMod_Benchmark xyz0/192/0,yaw-90,pitch30. #1088 partial UUID20c49c53
58.60fps versus integrated19 UUID221fd7a2 71.34fps are one-trial descriptive
comparisons only; no allfalse matched control or owner visual pairs. Integrated
GPU interpolation0/2879091,particles0/126249312; terrain727 GPU publishes with
CPU bypass/live REPLACE/APPEND and19 recoveries. Do not request old logs again.

After internally qualified final artifact: deliver report and exact ON/OFF.jvmargs
files with all19 current feature flags true/false and identical profiler/controller
properties. Functional launch autoBenchmark=false: texture/smoke appearance,
F3+T reload/worldreentry, new adoption counts/unsigned limits/rejections/CPU bypass.
Then autoBenchmark=true for matched same-JAR ON/OFF captures60s minimum+10s
quiet+180s stationary. Preserve framebuffer2552x1374 and save/scene/settings.
Compare FPS/frame tails,CPU world/texture/particle costs,separate GPU/upload scopes,
adoption/recoveries and visuals; dispatch counts alone are not measured CPU savings.
No release-ready, owner-verified or causal net benefit claim from software CI.

Before ending, update this complete rolling handoff with actual HEAD,commits,
final CI/artifact SHA256,evidence,blockers and exact next action. Successors must
maintain this same continuity discipline. If an external gate is unavailable,
finish independent work, preserve evidence and name that exact pending gate;
do not claim it passed or promise background work after the session ends.
