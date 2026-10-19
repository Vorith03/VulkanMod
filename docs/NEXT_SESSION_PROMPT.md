# VulkanMod — Continue GPU-First Renderer Ownership

Continue implementation and verification in https://github.com/Vorith03/VulkanMod on `forge-1.20.1`. **Mission:** transfer eligible CPU graphics calculations to GPU-owned production results and actually skip redundant CPU calculations. Do not revert to queue-size/BufferBuilder micro-tuning, stop at investigation, or declare a multi-subsystem GPU-first release complete after an isolated milestone.

## Recover live source of truth

Follow `AGENTS.md` Section 3A and live branch/CI before editing. Read `ROADMAP.md`, `AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, `docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`, `docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`, `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md` and this prompt. Inspect only intervening commits, CI and task-relevant contracts; resolve relevant red/pending validation before editing affected subsystems. Live evidence overrides this handoff.

## Last correctness-qualified executable

- **`cb248683db075033f0bb28ad42e8882ebba3f6d4`**, full **#1045** [run 39201976864](https://github.com/Vorith03/VulkanMod/actions/runs/39201976864) **PASSED**; Forge JAR artifact **12164426976**, smoke log artifact **12164093569**. Offline, Forge package/startups, native terrain/indirect, exact particle/texture pixel and synchronization, public Create Chronicles/Immersive Portals and Crash Assistant gates passed. Private packs skipped. Subsequent documentation-only HEAD is not independently executable-qualified.
- This slice eliminates an O(CPU visible queue) containment audit from **changed-traversal opt-in GPU candidate publication**: queue producer records valid unique owned graph/direct slots as they are enqueued, checks mutation revision and identity changes, and GPU consumer refuses stale/duplicate/foreign/untracked sets BEFORE cache reuse, preserving CPU fallback. Active producer bitset walk, mesh/readiness and immutable frustum/portal-volume/generation checks remain. The extra producer bookkeeping is opt-in; net RX cost/profitability **unmeasured**. CPU graph traversal is **not** eliminated. Do not confuse graph visits before frustum rejection with the CPU draw queue.
- Preceding #1044 executable `71f1bec858a9c2dbb589d218b172eec624c0dd29` / run 39195687691 (JAR 12161603960) qualified real particle descriptor reservation bound including sets awaiting fence retirement. #1041/#1042 negative evidence: Lavapipe admitted 82 descriptors against a pool configured for 64; host cap was required. No broad live portal/reload or RX gain is established.
- Opt-in pilots remain: interpolation-benefit ranked texture residency, per-sprite all-mip GPU compute/shared atlas copy queue, exact-campfire billboard GPU vertex expansion, lazy GPU indirect command ownership without CPU command shadow, producer-provenance reuse and sparse producer candidates. Candidate ABI v1 cannot encode generated REPLACE plus atomic APPEND command pairs. CPU exception geometry and fallback remain.

## Next implementation and correctness gates

1. **Particle production integration:** qualify exact-campfire inherited adapter under actual Immersive Portals/mod injection, callback/light ordering, mixed translucent pixels, true reload and frame-slot pressure. Do not mistake existing no-world constructor CI or geometry oracle for a real portal gameplay test. Preserve CPU simulation and fallback.
2. **Terrain producer per-slot metadata:** move candidate geometry metadata and upload-ready transitions to authoritative mesh/area ownership events, reducing remaining per-changed-traversal active-slot CPU reads without sacrificing pre-frustum graph stamps, direct seeds, ownership identity, pending upload retries, generation/ring recovery, and exact portal culling volumes. Verify stale/mutated cases with native oracles.
3. **Textures:** implement genuine multi-sprite GPU compute batching and shared descriptor/scratch management. Preserve original exact mip arithmetic, immutable source leases, atlas epochs/overlap and Vulkan barriers. Avoid per-sprite duplicate CPU work.
4. **Generated terrain:** version GPU candidate ABI for REPLACE and atomic APPEND commands before combining hybrid GPU meshing and indirect selection. CPU exception meshes and stale-generation recovery are mandatory.
5. **Integrated target hardware:** once meaningful changes are combined, request ONE narrow RX 6900 XT A/B with enabled-path adoption/fallback denominators, CPU computations actually displaced, GPU/host submit cost, FPS and p95/p99. Do not request the already analyzed #1028 UUID `e05f2b31` or a new JAR/user benchmark per experiment. Experimental gates stay default-off until correctness and profitability demonstrated.

Implement coherent production code, focused/native/Forge verification, publish logical commits, retain negative evidence and update `ROADMAP.md`, `AGENT_STATUS.md`, optimization evidence and affected contracts with exact executable SHA, CI/run/artifact IDs or explicit pending/red status. No claim of overall release completion until integrated correctness, adoption, CPU displacement and net frame-time gates pass.

## Mandatory rolling handoff

**Before ending the NEXT session**, reach a coherent published recoverable milestone, update `docs/NEXT_SESSION_PROMPT.md` again, and include a NEW copyable continuation prompt in the final reply. **That continuation prompt must explicitly instruct ITS successor session to end with another freshly updated rolling handoff prompt.** Preserve these instructions for every subsequent session.
