# VulkanMod — Continue GPU-first renderer ownership (rolling handoff)

Resume implementation and verification in Vorith03/VulkanMod branch
`forge-1.20.1`. GPU-owned production results must replace eligible
CPU graphics calculations, and redundant CPU computation must stop.
Do not return to queue-size/BufferBuilder micro-optimizations or
declare the integrated release finished after isolated correctness.

## Mandatory recovery

Follow `AGENTS.md` Section 3A. Read `ROADMAP.md`,
`AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`,
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`,
`docs/GPU_TEXTURE_SHARED_SCRATCH_2026-10-18.md`,
`docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`,
`docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`,
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`
and this prompt. Inspect live Git/CI delta before editing.
Prior last FULL green executable `c06f1cbc9c57a09b0df819edc639022bef049096`,
#1053 run 39216055025, Forge artifact 12169313491 and
smoke logs 12169019219; native
`VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK`.
This is the prior qualified baseline, not the new candidate.

## Active implementation checkpoint and CI

Candidate executable `629de44061e404e291dae647c8e026b23d841395`,
metadata-reuse implementation `8134a113cdaaa9fc3d5c3903752b420ab7a287cf`;
full #1055 [run 39227110919](https://github.com/Vorith03/VulkanMod/actions/runs/39227110919)
is **pending** at this handoff. Check its final steps, native
screenshot markers and artifact IDs before declaring it qualified.
#1054 run 39226851915 failed only isolated
`attribution-capture-contract.py` due seven-field stub; fixed
by `629de440`. Preserve earlier #1050-#1052, #1047/#1048,
#1041/#1042 negative evidence. Do not repeat #1028 analyzed
benchmark UUID `e05f2b31`.

Multi-dispatch now uses a fixed 32 × 48-byte GPU job table in
each 4 MiB shared scratch page. On exhaustion, drain all prior
pending atlas-copy and compute work, then emit explicit same-graphics
queue compute-read/transfer-write -> transfer-write barrier before
reusing the table; this orders reads even across prior submissions.
Existing exact Java-double RGB/current alpha, mips, clocks, source
leases, overlapping atlas destinations, staging resets, GPU-safe
retirement and fallback must remain. Native stress cycles 320 extra
accepted jobs over submission/copy boundaries; `tableRecycles`
is added to production lifetime counters. All pilots default-off.

## Remaining release work

If #1055 is red, inspect the failing gate first, fix and qualify.
If green, document exact executable/full run/JAR/smoke evidence
then address meaningful outstanding integrated work: actual-world
Immersive Portals/mod-injected particle lighting, translucency,
reload, frame-slot/descriptor pressure; terrain generated REPLACE/
atomic APPEND candidate ABI with GPU indirect handoff. Preserve
terrain queue provenance, graph/direct producer proofs, readiness,
exact portal volumes, listener detach, generation and lazy fallback.
CPU graph traversal and active row serialization/hash remain.
Measure representative adoption/accepted jobs/group sizes,
metadata and submission costs, actual CPU time displaced,
GPU time, FPS/p95/p99 only after multi-subsystem correctness.
A green native oracle is NOT net-performance proof.

## Rolling handoff REQUIRED for every successor

Before ending, publish a recoverable coherent Git milestone;
record exact executable SHA and full CI/run/JAR/log artifacts
or pending/red blocker, update `AGENT_STATUS.md`, `ROADMAP.md`,
`docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, affected contracts,
and `docs/NEXT_SESSION_PROMPT.md`. Include a NEW copyable
continuation prompt in the final answer.

**The NEW prompt must explicitly require its successor to finish
with another freshly updated rolling handoff prompt. Preserve that
successor-handoff requirement in every session.**
