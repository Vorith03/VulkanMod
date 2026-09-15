# GPU terrain indirect-draw handoff — 2026-09-15

## Scope

This checkpoint advances the Phase 7 section-selection track from persistent GPU
shadow output to a default-off production consumer. It does **not** bypass Minecraft
terrain meshing: geometry and draw metadata are still produced by the established CPU
terrain path. The new GPU work selects among that already-built region geometry and
can supply the indexed-indirect command buffer for a region/layer draw.

Source commits:

- `d91de023159f7a22a2b49f00392ca6e133cfd095` — `gpu terrain: gate GPU-selected indirect draws`;
- `67dafa92c5a9f568616e10a54c504d9285f8f514` — `gpu terrain: verify indirect draw fallback contract`.

## Production ownership gate

GPU-selected indirect commands are consumed only when **both** restart-time system
properties are enabled:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
```

The first property retains the existing persistent compute/shadow path. The second is
a separate ownership switch; normal builds remain CPU-driven when it is absent.

`RegionDrawBatch` continues to build the CPU `FrameBatch` before considering the GPU
buffer. A GPU draw is allowed only when all of the following are true:

- the production draw gate is enabled;
- a persistent shadow store exists;
- the current candidate table exists;
- the store has successfully dispatched or already contains that exact table generation;
- region X/Y/Z and generation match the current candidate table;
- the candidate count is not smaller than the authoritative CPU draw count;
- the candidate count does not exceed the persistent output capacity;
- the authoritative CPU draw count is nonzero.

Any failed condition uses the already-built CPU indirect buffer for that draw.

## Bounded command consumption

The compute output remains a fixed 512-command-capacity `StorageIndirectBuffer` with
a four-word diagnostic header. Before each new dispatch the full output is zeroed,
then selected commands are compacted from slot zero. The production draw begins after
the header and issues at most the candidate-table count, never more than the store's
capacity.

The CPU does not synchronously read the GPU-written count. Unused compact slots are
zero commands and therefore no-op draws. This preserves asynchronous queueing while
remaining bounded. The existing Vulkan smoke proves the zero tail, exact five-word
indexed-indirect metadata, full-capacity behavior and no overflow for the production
storage layout.

## Synchronization and lifetime

The persistent selector dispatch runs in a helper command buffer on the graphics
queue. It retains the existing barriers:

1. prior indirect reads -> transfer writes before reusing the output;
2. transfer writes -> compute reads/writes for candidate/parameter/output data;
3. compute writes -> indirect-command reads before the later main-frame draw.

The helper submit occurs before the main-frame submit on the same `VkQueue`; normal
queue order plus the main frame fence owns retirement. A frame-slot parameter buffer
is never rewritten after a helper submission from that same frame slot. If a newer
generation cannot be dispatched safely in the slot, that draw falls back to CPU.

## Regression contract

`RegionBatchSmokeTest` now exercises the same pure host decision predicate used by the
production consumer. It requires CPU fallback for:

- a disabled production gate;
- invalid/stale GPU output;
- a candidate set smaller than the CPU draw set;
- a candidate set larger than output capacity;
- an empty authoritative CPU draw set.

It also proves that an exact bounded set and a bounded superset are eligible for GPU
consumption. The Vulkan shadow smoke remains the independent proof that the buffer
itself contains exact commands and a zero tail.

## CI evidence

- CI #443, run `34925846288`, passed the complete workflow at `d91de023`: build and
  distributable verification, both Vulkan startups, persistent GPU indirect shadow,
  post-chain, depth post-chain, screenshot, FTB Library, Crash Assistant, Chat Heads,
  Flywheel, logs and artifacts.
- Its persistent shadow smoke emitted
  `VULKANMOD_GPU_INDIRECT_SHADOW_SMOKE_OK` after producing **99 exact commands from
  512 candidates**, with compute-to-indirect synchronization and zero-tail output.
- CI #444, run `34927562475`, validates the host fallback-contract commit
  `67dafa92`. Treat the gate as verified only if the live workflow conclusion remains
  green; live Actions evidence supersedes this document if it differs.

CI does not exercise representative Create Chronicles world rendering with the
production ownership gate enabled. It therefore does not establish live section-set
correctness or performance on the RX 6900 XT.

## Phase 7 interpretation

The **bounded GPU indirect-command + fallback** mechanism is now implementation- and
CI-verifiable, so that roadmap gate may be closed after #444 is green. The separate
GPU visibility/section-selection gate remains open until representative live RX 6900
XT diagnostics show no unresolved CPU/GPU selection mismatch.

This checkpoint does not claim:

- reduced CPU chunk-meshing work;
- correct GPU selection for every Forge/modpack camera edge case;
- a frame-time or FPS improvement;
- readiness for default enablement.

The known unusual case where traversal is seeded while the camera is outside normal
build height should be included in real-hardware validation before closing selection.

## RX 6900 XT correctness test

Use the CI artifact containing `67dafa92` and add these JVM properties:

```text
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
-Dvulkanmod.debugGpuSectionSelection=true
-Dvulkanmod.debugGpuSectionSelectionSamples=8
```

Then, in the normal Create Chronicles test world:

1. load ordinary terrain at normal build height;
2. rotate the camera through full turns while moving across several region boundaries;
3. perform a short fast spectator-flight/chunk-churn pass;
4. if practical, move the spectator camera above normal build height, rotate/move,
   then return to ordinary terrain;
5. watch for missing terrain, popping/holes, stale chunks, flicker, device loss or a
   visible difference from the CPU-driven build.

Return the relevant `latest.log` lines with:

```bash
grep -E 'VULKANMOD_GPU_(INDIRECT_DRAW_ACTIVE|LIVE_SECTION_SELECTION_(OK|MISMATCH|ERROR)|INDIRECT_SHADOW)' latest.log
```

Success requires the `VULKANMOD_GPU_INDIRECT_DRAW_ACTIVE` marker, representative
`VULKANMOD_GPU_LIVE_SECTION_SELECTION_OK` samples, no unresolved mismatch/error, and
no visual terrain regression. If any mismatch or visual defect appears, disable only
`vulkanmod.experimentalGpuIndirectDraw` to restore CPU production ownership while
retaining the shadow/diagnostic path for investigation.

## Next boundary

If RX selection/draw correctness is clean, record that evidence and close the live
selection gate. Performance should then be measured A/B before considering this path
for default use; issuing bounded zero-tail commands may have different costs across
drivers even though it avoids a synchronous count readback.

The larger project priority remains hybrid GPU terrain construction. Lighting-demand
telemetry still needs representative Create Chronicles section evidence before a
production lighting input encoding is chosen, and `CPU_REQUIRED` remains authoritative
for GPU-meshing qualification/fallback.
