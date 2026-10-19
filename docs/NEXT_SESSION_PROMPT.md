# VulkanMod — Continue GPU-first integrated renderer (rolling handoff)

Continue implementation/verification in https://github.com/Vorith03/VulkanMod on `forge-1.20.1`. GPU-owned results must replace eligible CPU graphics work in production, and corresponding CPU calculations must actually stop. Do not resume queue-size/BufferBuilder micro-optimizations, claim overall completion from isolated subsystems, or switch experiments on without measured benefit.

## Recovery and latest state

Follow `AGENTS.md` Section 3A against live Git/CI; inspect intervening commits from this documentation checkpoint. Read `ROADMAP.md`, `AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, `docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`, `docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`, `docs/GPU_TEXTURE_SHARED_SCRATCH_2026-10-18.md`, `docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`, `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md` and this handoff. Live Git and CI override this checkpoint.

Last qualified executable `ba2f574e8db79ea26b7694d9cb37f31c8d5b2710`: **full #1056 PASSED**, run 39232013086, Forge JAR artifact 12178027221, smoke logs 12177772301. Its synthetic post-callback sorting recovery validates callback-once, exact light/color, geometry/UV tolerance and prior packed recovery; not actual Immersive Portals pixels or a performance result. Texture metadata slab 320-job exact-pixel stress qualified in #1055, not RX-profitable by evidence.

New executable `29e95e5c31221276dae83d65363407f0c43f740c` after `ebb5d320591a31c46700be4bb340b7cdc5444cda`: **full #1058 / run 39236211314 was PENDING at handoff**. Resolve this first; if red inspect failed job/steps/logs and fix smallest cause, if green record full CI conclusion and exact JAR/log artifact IDs. #1057 was superseded/cancelled. New native smoke tests actual host-enforced particle descriptor exhaustion, frame-fence retirement and pressure recovery, and statistics expose `allocated_descriptors` and `pending_retirement_descriptors`. Do not claim this slice qualified until validated.

## Active work after validation

First: actual Immersive Portals/Forge-mod particle callbacks, precise lighting/translucency/camera pixels, resource reload/teardown/re-entry and descriptor/frame-slot pressure; exact-class admission and CPU fallback mandatory. Current public Immersive Portals smoke proves shader and mixin behavior, **not real-world portal particle rendering**. Make a bounded meaningful correctness/ownership improvement, reuse prior tests, do not demand repeat owner tests.

Second: generated terrain REPLACE/atomic APPEND producer candidate ABI integrated with GPU indirect draw selection. Preserve graph/direct provenance, queue containment, producer geometry/readiness, exact portal volumes, listener/generation ownership and lazy CPU fallback. Do not count CPU graph traversal or candidate serialization/hash as eliminated.

After multi-subsystem qualification measure true accepted/eligible jobs, aggregation, displaced CPU work, GPU costs and submissions, memory retirement, actual RX FPS and p95/p99; keep pilots opt-in until demonstrated net benefit. Preserve negative #1054, #1050–#1052, #1047/#1048, #1041/#1042 and analyzed #1028 UUID `e05f2b31`.

## Mandatory rolling handoff

Before ending publish recoverable Git progress, record the exact executable SHA and full CI/run/JAR/log artifacts or explicit red/pending status, update `AGENT_STATUS.md`, `ROADMAP.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, affected contracts and this `docs/NEXT_SESSION_PROMPT.md`. Include a **NEW copyable continuation prompt** in the final response. **The next prompt MUST explicitly instruct its successor to finish with another freshly updated rolling handoff prompt; all subsequent sessions must preserve this requirement.**
