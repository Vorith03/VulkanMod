# VulkanMod — Continue GPU-First Renderer Ownership

Continue `Vorith03/VulkanMod`, branch `forge-1.20.1`. This is a code implementation and verification task to replace eligible CPU graphics work with GPU-owned production results and actually stop the redundant CPU path. Do not revert to queue-size/BufferBuilder work or declare the integrated release complete on an isolated milestone.

Follow `AGENTS.md` §3A using live Git/CI; read `ROADMAP.md`, `AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, `docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`, `docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`, `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md` and this prompt. Inspect delta, not settled history; resolve pending/red relevant CI before editing that subsystem.

## Checkpoint

Last correctness-qualified executable: `1b182d53a18c0b83c5b4c1c52f94c08987ad5a1c`, full #1040 / run 39192146854 PASSED, JAR 12160493015, smoke logs 12160477999, public native/Forge/compatibility gates passed; private packs skipped. Sparse candidate v1 retains pre-frustum graph visits and direct seeds, owned/queue/mesh/readiness/ring/generation/portal-volume fallback. CPU graph traversal remains. Generated REPLACE/atomic APPEND pairs cannot be represented in v1. No RX adoption or net cost benefit measured.

Next source implementation `7bdf80575e1cfd891ffbfeddbb9bc656cad8c880` introduces native particle descriptor-pool exhaustion / CPU compact recovery and post-device-idle teardown/reinitialization; full CI #1041, run 39194476949 was pending when recorded. Verify current run and JAR before calling it qualified. Docs-only HEAD is not a new executable. This is test-only; not portal pixels or resource reload.

## Priorities

1. Qualify campfire billboard under actual Immersive Portals/mod injection, callback/light, mixed translucent pixel, resource reload and frame-slot pressure. Preserve CPU simulation, exact-class admission and fail-closed fallback. CI #1041 test scope must not be misrepresented as live portal qualification.
2. Advance producer-side per-slot terrain metadata/readiness beyond sparse changed-traversal rebuild; retain graph visibility distinct from frustum queue, direct seeds, ring/generation/mesh and portal volume safety and CPU fallback.
3. Implement genuine multi-sprite GPU texture compute batching with shared scratch/descriptors while preserving all original mip math, source leases, atlas ordering and barriers.
4. Version the candidate ABI for generated terrain REPLACE and atomic APPEND draw command pairs before combining GPU mesher and indirect selection; retain CPU exceptions and stale-generation recovery.
5. Integrate before seeking one targeted RX 6900 XT comparison of adoption, truly eliminated CPU work, extra GPU cost, FPS and p95/p99. The analyzed #1028 UUID `e05f2b31` should not be requested again. Keep all experiments opt-in until correctness and profitability pass.

Make a coherent code milestone, run focused/native/Forge validation, publish logical commits, preserve red/negative evidence, and update roadmap, status, optimization ledger and affected contracts with exact executable, CI/run/artifact IDs or documented pending state.

**Mandatory rolling handoff:** Before this NEXT session ends, update `docs/NEXT_SESSION_PROMPT.md` again and include a NEW copyable continuation prompt in the final reply. The successor's prompt MUST itself explicitly instruct the following session to end with another updated rolling handoff prompt. Do not claim full release completion until integrated correctness, adoption, CPU displacement and measurable performance gates pass.
