# VulkanMod — Continue GPU-First Renderer Ownership

Continue implementation and verification in https://github.com/Vorith03/VulkanMod on `forge-1.20.1`. Transfer eligible graphics calculations to GPU-owned production results and eliminate their duplicate CPU execution. Do not return to queue-size/BufferBuilder micro-tuning, stop at investigation, or declare the integrated release complete after one milestone.

## Recover authoritative state

Follow `AGENTS.md` Section 3A against live Git and CI. Read `ROADMAP.md`, `AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, `docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`, `docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`, `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md` and this prompt. Inspect intervening changes and resolve relevant pending/red validation first. Live evidence supersedes this prompt.

## Latest implementation and qualification

Qualified executable `29a94f73c72ff30443ff12d45eb77c12805e4e17` passed full **#1046 / run `39205054428`**. Forge JAR artifact **`12165621719`**, smoke logs **`12165785846`**. Public offline/build/package/both Forge startups/native terrain/indirect/texture/particle pixel/synchronization, Create Chronicles/Immersive Portals and Crash Assistant gates passed; private packs skipped. Subsequent documentation-only HEAD reuses this executable evidence. No RX CPU-time/FPS/tail gain is measured.

The new terrain slice publishes immutable candidate numeric geometry at upload, staged commit, reset and deferred parameter publication. Opt-in Segment readiness events invalidate only the current owned area/layer; replacement detaches the retired listener. Changed-traversal candidate assembly consumes the snapshots and pending snapshots can remain cached until completion, removing active Segment polling and retry-every-draw preparation. The mandatory native marker is `VULKANMOD_GPU_CANDIDATE_GEOMETRY_PRODUCER_OK`. Preserve producer mutation publication, segment replacement/detachment, stale-owner isolation, pre-frustum graph visits, direct seeds, queue mutation/duplicate rejection, owned-slot checks, exact portal volume, GPU generation safety and lazy CPU fallback. Active-slot serialization/hash and Java graph traversal still run. Snapshot allocations and callbacks add unmeasured producer cost; no RX speedup is established.

Prior #1044 particle descriptor reservations include sets waiting for fence retirement. Preserve #1041/#1042 negative evidence: Lavapipe admitted 82 sets against maxSets=64. Native constructor geometry/teardown/pressure tests are not actual portal gameplay or resource reload qualification.

## Next substantive implementation

Prioritize genuine multi-sprite GPU texture compute batching and shared descriptor/scratch ownership in `GpuTextureInterpolationCompute`, `GpuAnimatedTextureResidency`, source leases and the existing atlas-copy queue. The current batch pilot still dispatches once per sprite and retains separate descriptors/scratch. Preserve exact original mip/double/current-alpha arithmetic, source leases, overlap epochs, atlas ordering, capacity fallback and synchronization. CPU interpolation must actually be skipped for accepted jobs. Inspect current code before designing the change.

Also open: exact-campfire particles with actual Immersive Portals/mod callbacks, lighting, mixed translucent pixels and true reload/frame pressure; versioned candidate ABI for generated REPLACE and atomic APPEND pairs before composing GPU meshing and indirect selection. Preserve CPU exception geometry and stale-generation recovery. Keep all experimental features opt-in.

Validate coherent changes with focused and full native/Forge gates, publish logical commits and retain negative evidence. No benchmark per isolated experiment, no repeat of analyzed #1028 UUID `e05f2b31`. Once meaningfully integrated, seek narrow RX evidence of adoption/fallback, CPU work/time eliminated, added GPU/submission cost, FPS and p95/p99. Integrated correctness, adoption, CPU displacement and net performance remain release gates.

## Mandatory rolling handoff

Before ending YOUR session, reach a coherent published recoverable milestone; record exact executable, CI/run/artifact IDs or explicitly pending/red evidence; update status, roadmap, evidence ledger and affected contracts; update this file; and include a NEW copyable continuation prompt in your final response. That new prompt must explicitly instruct ITS successor to finish with another freshly updated rolling handoff prompt. Preserve this requirement across every subsequent session.
