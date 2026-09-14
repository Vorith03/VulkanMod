# GPU terrain bounded section-selection probe — 2026-09-14

## Scope

Source commits `7f1ea528442a48e07be9e268be172387817c3ff7` and
`7cbcc4f348cad721a1491a2b81b1e84af2fe16b3` add an isolated Vulkan compute
oracle and a versioned region-candidate ABI for the first section-selection and
indirect-command building block. They do
not change production rendering. CPU graph traversal, frustum visibility, region
command construction and direct/legacy fallbacks remain authoritative.

The table header includes magic, version, a 64-bit generation, candidate count and
the exact 128-block-aligned region origin. Each eight-word record contains the five
indirect-command words plus readiness, CPU graph-visibility and terrain-layer flags.
Two reserved words permit a fail-closed version bump when production needs more
metadata.

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

CI #418 (run `34808397281`, job `103864694566`) passed the initial compaction proof.
CI #419 (run `34810466530`, job `103870615231`) passed compilation, shader creation,
both Lavapipe Vulkan startups, post/depth/screenshot checks, Crash Assistant, Chat
Heads and Flywheel after extending the predicate. Its fixture independently varies
readiness, CPU graph visibility, layer, index/instance counts and section position
against six camera-relative planes. CPU and GPU select the same exact 39/512 set;
capacity nine overflows safely, while the wrong generation produces an all-zero
result. The log repeatedly emitted:

`VULKANMOD_GPU_SECTION_SELECTION_OK: 39 exact frustum/layer/ready/graph matches from 512 generation-owned candidates; bounded overflow and stale rejection verified`

## Remaining boundary

This does not complete the Phase 7 GPU visibility/section-selection or bounded
indirect-command gate. Before either gate can close, the renderer still needs:

- persistent region-owned candidate-table GPU residency and frame-safe publication;
- production camera/frustum input extraction and comparison against live selection;
- graph/smart-culling ownership defined without hiding Forge-visible sections;
- frame-safe output lifetime and compute-to-indirect-draw barriers;
- production overflow fallback and lifecycle invalidation;
- RX 6900 XT visual and performance evidence.

The next safe source step is a region-owned GPU store for the candidate table with
allocate-then-publish generation semantics and invalidation on mesh/visibility/region
lifecycle changes. It must remain diagnostic until live CPU/GPU selection agreement
is measured. Lighting-demand telemetry remains an independent prerequisite for
hybrid meshing, not for this selection track.
