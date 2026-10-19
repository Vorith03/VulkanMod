# VulkanMod — Continue integrated GPU-first renderer (rolling handoff)

Resume implementation and verification in
[ Vorith03/VulkanMod ](https://github.com/Vorith03/VulkanMod)
on `forge-1.20.1`. GPU-owned production results must actually
replace eligible CPU graphics computation. Never return to
queue-size/BufferBuilder micro-optimizations, or declare the integrated
release complete after an isolated milestone.

## Recovery

Follow `AGENTS.md` Section 3A. Check current HEAD and latest full CI,
inspect delta from the documentation checkpoint, and resolve red or
pending validation before affected edits. Read `ROADMAP.md`,
`AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`,
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`,
`docs/GPU_TEXTURE_SHARED_SCRATCH_2026-10-18.md`,
`docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`,
`docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`,
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`,
and this handoff. Live Git/CI overrides stale SHA or status.

## Latest qualified executable

`ba2f574e8db79ea26b7694d9cb37f31c8d5b2710`; **full #1056**,
[run 39232013086](https://github.com/Vorith03/VulkanMod/actions/runs/39232013086) **PASSED**,
Forge JAR **12178027221**, smoke logs **12177772301**.
Public offline, Forge build/package/two startups, native
terrain/indirect/texture/particle pixels/synchronization, screenshot
and Create Chronicles with Immersive Portals/Crash Assistant all
passed; private packs skipped. The new native particle oracle
proves post-callback state recheck and CPU recovery when a synthetic
lighting callback turns on sorting after an earlier packed GPU quad.
It checks exact callbacks and color/light, geometry/UV tolerance and
no packed records left in the sorted stream. The lifetime
`callback_state_cpu_recoveries=3` is **synthetic fixture traffic**.

Prior qualified texture milestone `629de44061e404e291dae647c8e026b23d841395`,
full #1055 run 39227110919, Forge JAR 12173844688, logs
12175645031. Fixed reusable shared scratch GPU metadata job table
32 × 48 bytes, 320 additional accepted native stress jobs,
same-queue compute-read/transfer-write to transfer-write ordering,
exact pixels and atlas overwrite. No RX profitability proven.

## Next substantive work

First: actual-world particle Immersive Portals and mod-injected render
callbacks, exact light/translucency/camera/pixel behavior, real resource
reload/teardown/reinitialization, frame-slot and descriptor lifetime
pressure, CPU fallback for unsupported classes. Reuse native tests
and prior Project user observations; do not request redundant owner
tests. The synthetic callback regression does **not** close these gates.

Second: generated terrain REPLACE/atomic APPEND candidate ABI integrated
with real GPU indirect draw selection. Preserve graph/direct provenance,
producer-owned geometry/readiness, queue containment, exact portal
culling volumes, listener detachment, generation ownership and lazy CPU
fallback. Graph traversal and changed active-row serialization/hash
are **not** eliminated work.

After multi-subsystem qualification, measure eligible vs accepted GPU
jobs, actual aggregation, fallback reasons, displaced CPU work,
additional GPU execution/submissions/memory and representative RX
FPS/p95/p99. Keep pilots opt-in; do not enable by default until
net benefit. Preserve negative #1054, #1050–#1052, #1047/#1048,
#1041/#1042 and analyzed #1028 UUID `e05f2b31`.

## Mandatory rolling handoff

Before ending, publish coherent recoverable Git work and verify
exact executable SHA, full CI/run/JAR/log artifacts or identify
pending/red gates. Update `AGENT_STATUS.md`, `ROADMAP.md`,
`docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, affected contracts and
`docs/NEXT_SESSION_PROMPT.md`. In your final reply include a
**NEW copyable continuation prompt**. **That prompt must explicitly
instruct its successor to finish with another freshly updated rolling
handoff prompt. Every later session must preserve this requirement.**
