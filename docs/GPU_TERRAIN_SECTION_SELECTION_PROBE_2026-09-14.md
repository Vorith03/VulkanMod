# GPU terrain bounded section-selection probe — 2026-09-14

## Scope

Source commit `7f1ea528442a48e07be9e268be172387817c3ff7` adds an isolated Vulkan compute
oracle for the first section-selection and indirect-command building block. It does
not change production rendering. CPU graph traversal, frustum visibility, region
command construction and direct/legacy fallbacks remain authoritative.

## Proven ABI

The input and compact output use VulkanMod's existing 20-byte indexed indirect
command layout:

1. index count;
2. instance count;
3. first index;
4. signed vertex offset;
5. packed section/first-instance word.

A zero index or instance count is ineligible. Each live invocation atomically claims
a compact slot. The output begins with separate requested and written counts plus an
overflow flag. No invocation writes a command when its claimed slot is outside the
caller-provided capacity.

## Oracle coverage

The startup fixture supplies all 512 possible region slots with independently varied
index/instance eligibility and unique values in every command field. Exactly 310
candidates are live.

- Capacity 512 reports 310 requested and written, no overflow, and an exact set match
  with the CPU oracle.
- Capacity 17 reports 310 requested, 17 written and overflow, with 17 unique eligible
  commands and unchanged metadata.
- Capacity zero reports 310 requested, zero written and overflow while allocating no
  command payload.

CI #418 (run `34808397281`, job `103864694566`) passed compilation, shader creation,
both Lavapipe Vulkan startups, post/depth/screenshot checks, Crash Assistant, Chat
Heads and Flywheel. The log repeatedly emitted:

`VULKANMOD_GPU_SECTION_SELECTION_OK: 310 live of 512 candidates; bounded capacity/overflow and exact indirect metadata verified`

## Remaining boundary

This does not complete the Phase 7 GPU visibility/section-selection or bounded
indirect-command gate. Before either gate can close, the renderer still needs:

- a generation-owned region candidate record with mesh readiness and layer identity;
- exact camera/frustum inputs and comparison against the current CPU selection;
- graph/smart-culling ownership defined without hiding Forge-visible sections;
- frame-safe output lifetime and compute-to-indirect-draw barriers;
- production overflow fallback and lifecycle invalidation;
- RX 6900 XT visual and performance evidence.

The next safe source step is the region candidate-record ABI plus an exact
CPU-versus-GPU frustum/eligibility oracle. Lighting-demand telemetry remains an
independent prerequisite for hybrid meshing, not for this selection track.
