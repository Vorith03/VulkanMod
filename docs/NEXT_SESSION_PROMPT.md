# VulkanMod — GPU-first renderer continuation (rolling handoff)

Continue implementation and verification on `Vorith03/VulkanMod`, branch
`forge-1.20.1`. GPU-owned production results must replace eligible CPU
graphics work and bypass its CPU execution. Do not regress to micro-tuning
or declare the integrated release complete after a local resource milestone.

## Recover live state

Follow `AGENTS.md` §3A. Read `ROADMAP.md`, `AGENT_STATUS.md`,
`docs/OPTIMIZATION_EVIDENCE_LEDGER.md`,
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`,
`docs/GPU_TEXTURE_SHARED_SCRATCH_2026-10-18.md`,
`docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`,
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`, and this prompt.
Compare live Git/CI to the documented checkpoint, resolving relevant
red/pending qualification before further work. Live evidence wins.

## Current recovery point

Latest **qualified executable**:
`6faa82e5f05d800dc0fa26fd51f2a775d921d708`
(full **#1049**, [run 39208913742](https://github.com/Vorith03/VulkanMod/actions/runs/39208913742),
**PASSED**). Forge JAR artifact **12167505101**, smoke logs **12166663996**.
Native marker `VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK` passed, alongside
the public offline/native/Forge package/dual-startup/texture/terrain/particle
pixel and synchronization, screenshot, Create Chronicles/Immersive Portals
and Crash Assistant gates; private resource packs skipped. RX measured
performance still open.

Implementation commit `8352108e56d6ffec566979cc35795b1499010049`,
harness fixes `a74f41ce5b2ca4e11ae8b5f42dbab6f84d0b68a4` and
`6faa82e5f05d800dc0fa26fd51f2a775d921d708`. Negative #1047/#1048
failed only the offline Java extractor selection before native validation.
The older qualified terrain milestone was executable `29a94f73`,
full #1046, run 39205054428, JAR 12165621719, logs 12165785846.

The new `experimentalGpuAnimationSharedScratch` requires the
all-mip batch and source-pool pilots. Sprite leases on the same source
page share a compute descriptor and separate append-only scratch slots,
with source/table/output offsets translated in push constants/shader.
A range-aware queued scratch read drain replaces whole-buffer flush for
shared pages. Cap 4 MiB per scratch page and 128 MiB physical pending-aware.
Existing standalone binding and CPU fallback remain. The native
source-pool oracle covers two leases/slots and atlas pixels. No RX
effectiveness data exists. This **does not** yet batch several sprites
into a single GPU dispatch; next work must make a safe bounded job
aggregation with explicit queue/overlap/lifetime/fallback ordering.

## Open qualification and performance gates

Preserve exact double/current-alpha/mip arithmetic, source leases,
mutable source invalidation, overlap order, copy and compute barriers,
CPU interpolation cancellation and CPU fallback. Do not enable flags
by default. Preserve terrain immutable producer geometry/readiness,
stale owner/listener removal, prefrustum graph visits/direct seeds,
queue containment, portal volumes, generations, lazy CPU fallback,
and REPLACE/atomic APPEND exclusion. Graph traversal and row
serialization/hash remain. Particle campfire portal/mod injection,
lighting, translucency, actual reload and frame pressure are open;
#1041/#1042 native descriptor negative evidence remains. Generated
terrain REPLACE/atomic APPEND candidate ABI remains open.

Do not rerun analyzed #1028 UUID `e05f2b31` or request user RX
benchmarks per isolated pilot. Once integration is qualified, measure
adoption/fallback, actual CPU work/time displaced, added GPU and submission
cost, FPS and p95/p99 on matched RX 6900 XT workload. Qualification is
not measured speedup or a completed GPU-first release.

## Mandatory next-session handoff

Finish the next session with a coherent published milestone, exact
executable and CI/run/artifact IDs (or explicitly pending/red state),
updated `AGENT_STATUS.md`, `ROADMAP.md`, evidence ledger, affected
contracts and this `docs/NEXT_SESSION_PROMPT.md`. Include a NEW
copyable continuation prompt in that next session's final response.
That NEW prompt must **explicitly require its successor to finish
with another freshly updated rolling handoff prompt**. Carry this
requirement forward indefinitely.
