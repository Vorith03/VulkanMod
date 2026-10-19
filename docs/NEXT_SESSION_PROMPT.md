# VulkanMod — GPU-first integrated renderer rolling handoff

Continue implementation and verification in `Vorith03/VulkanMod` on
`forge-1.20.1`. Production GPU results must replace eligible CPU graphics
calculation, and redundant CPU execution must stop. This is not a
queue-size/BufferBuilder optimization effort or isolated milestone release.

## Recover live state

Follow `AGENTS.md` Section 3A. Inspect live HEAD, changes from this
checkpoint, current CI and logs when red. Read `ROADMAP.md`,
`AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`,
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`,
`docs/GPU_TEXTURE_SHARED_SCRATCH_2026-10-18.md`,
`docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`,
`docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`,
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`
and this prompt. Live repository evidence supersedes this checkpoint.

## Latest milestones

Latest **fully qualified executable** remains
`629de44061e404e291dae647c8e026b23d841395`, full
[#1055 / run 39227110919](https://github.com/Vorith03/VulkanMod/actions/runs/39227110919)
PASSED, Forge JAR artifact **12173844688**, smoke logs
**12175645031**, native `VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK`
and `VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK`. The shared 32 × 48-byte
texture job-table reuse stress (320 accepted jobs) and all public
offline/Forge/native screenshot/synchronization/terrain/particle and
Create Chronicles/Immersive Portals/Crash Assistant gates passed; private
resource packs skipped. This is correctness, not RX profitability.

**Current particle executable candidate:**
`ba2f574e8db79ea26b7694d9cb37f31c8d5b2710`,
full [#1056 / run 39232013086](https://github.com/Vorith03/VulkanMod/actions/runs/39232013086)
**PENDING**. If newly red, inspect the actual failing step and fix
before changing other relevant production code. It adds a post-callback
builder-state recheck for the exact-campfire GPU particle adapter.
If a callback mutates sorting/eligibility, it emits CPU vertices
from already evaluated numeric values **without re-executing callbacks**.
A native smoke oracle exercises callback-initiated sorting after a prior
packed quad, CPU recovery and exact callback/color/light with tolerant
geometry/UV equivalence. This is a synthetic callback safety gate, NOT
real-world IP mod-injection/reload/translucency qualification.

## Active release gates

First: actual Immersive Portals and mod-injected particle callbacks,
lighting, translucency/camera pixels, resource reload/teardown/reinit,
frame-slot/descriptor retirement; unsupported variants stay CPU.

Second: generated GPU terrain REPLACE/atomic APPEND candidate ABI
integrated with real indirect selection. Preserve producer-owned
geometry/readiness, graph/direct/queue proof, exact portal volumes,
generation/retirement and lazy CPU fallback. Do not claim graph traversal
or active serialization/hash displaced.

Then: representative combined RX integration once meaningful multi-subsystem
correctness is established. Measure real accepted/eligible jobs, grouped
dispatch, CPU time displaced, added GPU/submit/memory costs, FPS/p95/p99
and pressure. All flags stay default-off. Preserve failed #1054,
#1050–#1052, #1047/#1048, #1041/#1042 and already analyzed #1028
benchmark UUID `e05f2b31`. Do not request another isolated RX test.

## Mandatory rolling handoff for every successor

Before ending, publish a coherent recoverable Git milestone, record the
exact qualified executable SHA/full CI run/JAR/log artifacts or explicit
pending/red state. Update `AGENT_STATUS.md`, `ROADMAP.md`,
`docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, affected contracts and this
`docs/NEXT_SESSION_PROMPT.md`. Include a **NEW copyable continuation
prompt** in the final reply. **That new prompt must explicitly require
the next session to end with another freshly updated rolling handoff
prompt, preserving this instruction indefinitely.**
