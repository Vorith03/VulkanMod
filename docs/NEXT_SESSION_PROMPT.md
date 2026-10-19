# VulkanMod — Continue GPU-First Renderer Ownership

Continue development of https://github.com/Vorith03/VulkanMod on branch `forge-1.20.1`.

**Mission:** Deliver a substantive GPU-first release by moving eligible graphics calculations to GPU-owned production results and actually skipping their CPU equivalents. This is an implementation-and-verification task. Do not return to queue-size/BufferBuilder micro-tuning, stop at investigation, or declare the whole release done after an isolated milestone.

## Recover live authoritative state

Follow `AGENTS.md` Section 3A. Read `ROADMAP.md`, `AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, `docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`, `docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`, `docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md` and this prompt. Check current HEAD and intervening commits/CI. Resolve relevant red or pending CI before touching affected subsystems, preserve negative evidence, avoid settled archaeology. Current Git, CI and runtime evidence override this handoff.

## Latest qualified milestone: native particle descriptor safety

- **Executable `71f1bec858a9c2dbb589d218b172eec624c0dd29`** passed full **#1044**, [run 39195687691](https://github.com/Vorith03/VulkanMod/actions/runs/39195687691).
- Distributable Forge JAR artifact **12161603960**, smoke log artifact **12161404876**. Public offline native terrain/indirect, pixel/texture/particle/synchronization, Forge packaging/startups, Create Chronicles/Immersive Portals and Crash Assistant passed; private pack fixtures skipped.
- Producer commit `6817c071` adds an explicit 64-set GPU particle descriptor reservation bound including pending fence-retired sets. On exhaustion, refuse native allocation and recover CPU vertices. `71f1bec8` qualifies an 80-VkBuffer real pressure test in an isolated recording frame, plus teardown/reinitialize after device idle and the existing three-frame mixed exact pixel/geometry oracle.
- **Negative evidence preserved:** #1041 failed an overconfident test assumption; #1042 observed Lavapipe allocate 82 live sets against maxSets=64. It did NOT establish a geometry defect. #1043 superseded before full validation. Final #1044 tests host cap and CPU recovery and is green.
- The preceding #1040 sparse terrain candidate milestone `1b182d53` remains. Producer graph visits are recorded BEFORE CPU frustum rejection; direct seeds are separate. Active sparse input, owned queue audit, mesh/readiness, region reuse/generation, exact portal-volume and GPU/CPU command fallback are preserved. CPU graph traversal is NOT eliminated. Candidate ABI v1 cannot represent generated REPLACE/atomic APPEND command pairs.
- Texture ranked residency and per-sprite all-mip compute/shared atlas copy, exact-campfire particle billboard compute, GPU indirect command ownership and sparse producer candidate reuse are still opt-in. **No RX 6900 XT net CPU savings, new GPU cost, FPS or p95/p99 performance qualification** from #1044. The owner #1028 capture UUID `e05f2b31` is already analyzed; do not request it again.

## Next implementation gates

1. **Particles:** qualify exact-campfire adapter with actual portal/mod injection and callbacks, mixed translucent pixels, real resource reload and frame-slot pressure. Distinguish CI-native stress from gameplay adoption; preserve original shader/order and CPU simulation, lighting and safe fallback.
2. **Terrain:** move per-slot candidate metadata/readiness maintenance closer to mesh/producer owners so changed traversals need less CPU gathering. Preserve graph visibility distinct from frustum queue, direct seeds, owned slots, mesh readiness, portal culling volume, generation and CPU recovery.
3. **Textures:** genuine multi-sprite compute aggregation with reusable scratch and descriptor management. Preserve original mip arithmetic, source leases, atlas epochs, overlapping writes and memory barriers.
4. **Generated terrain:** version the GPU candidate ABI for REPLACE and atomic APPEND command pairs before combined GPU mesher/indirect draw selection. CPU exception meshes and stale-generation recovery remain mandatory.
5. **Integrated RX evidence:** after an appropriately integrated executable, request ONE targeted RX 6900 XT A/B capable of reporting GPU adoption denominator, CPU computations actually eliminated, additional GPU/submit time, FPS and frame p95/p99. Do not request a new JAR or benchmark for each small change; no default promotion without proven net profitability.

Implement and validate coherent code, publish logical commits, preserve unsuccessful results, and update `AGENT_STATUS.md`, `ROADMAP.md`, optimization evidence and affected subsystem contracts with exact qualified executable SHA, CI run/artifact IDs or explicit pending/red status.

## Mandatory rolling handoff

**Before ending the NEXT session**, reach a recoverable published engineering milestone, update `docs/NEXT_SESSION_PROMPT.md`, and include a NEW copyable continuation prompt in the final response. **That prompt must explicitly instruct ITS successor session to end with another fresh rolling handoff prompt.** The overall GPU-first renderer release remains incomplete until integrated correctness, adoption, real CPU displacement and performance gates pass.
