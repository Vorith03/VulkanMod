# GPU terrain live section-selection probe — 2026-09-14

## Scope

Phase 7 remains intentionally hybrid and fail-closed. CPU graph traversal and the
existing CPU-built region indirect batches still determine production rendering, but
the section-selection compute path has advanced from a synthetic oracle to a live,
region-owned diagnostic path over current terrain metadata.

The progression is now:

- `7f1ea528442a48e07be9e268be172387817c3ff7` — bounded section-selection/indirect
  compaction oracle;
- `7cbcc4f348cad721a1491a2b81b1e84af2fe16b3` — versioned region-candidate ABI with
  readiness, graph-visibility, terrain-layer and camera-frustum predicates;
- `10c6c46d55dfeca5cfd7d503d1ad57dcc3997e55` — generation-owned device-local
  candidate residency with asynchronous publication and a 16 MiB global cap;
- `be7b206189d5da174bf7af2877b5c72fddbf6417` — per-layer live candidate publication
  from current region draw metadata;
- `59e6c75cd10d840023c06c93409265b7abc8a9d1` — exact production-frustum extraction
  and a rate-limited live GPU-vs-CPU diagnostic comparator;
- `f7330d9fa0f44c8f13e3bb366105613d13583f44` — candidate production from the full
  fine-grid region ownership set, making GPU compute own the frustum decision over a
  true live superset instead of replaying an already-frustum-filtered CPU queue.

None of these commits replaces the commands consumed by production terrain draws.
The existing `RegionDrawBatch.FrameBatch` remains authoritative, so allocation,
upload, compute or comparison failure cannot make terrain disappear.

## Candidate ABI and residency

The table header contains magic, version, a 64-bit generation, candidate count and
the exact region origin. Region origins are required to be **section aligned** (16
blocks), not globally 128-block aligned. This matters because an 8-section-tall
coarse region can legitimately start at an Overworld origin such as Y=-64 even
though its local packed section coordinates still span exactly 0..511.

Each eight-word candidate record contains:

1. indexed draw index count;
2. instance count;
3. first index;
4. signed vertex offset;
5. packed region section / `firstInstance` word;
6. readiness, graph-visible and terrain-layer flags;
7. reserved word;
8. reserved word.

The first five words are the existing 20-byte Vulkan indexed-indirect command ABI.
Candidate residency uses fixed maximum 16,416-byte device-local buffers under a
16 MiB global cap. Copies are staged through `AreaUploadManager`; a replacement is
published only after its matching graphics-queue copy submission. New generations
immediately revoke stale discoverable residency while old physical storage remains
fence-retired. Budget or allocation failure leaves the CPU renderer authoritative.

Residency is now independent per `TerrainRenderType`, preventing one terrain layer
from replacing another layer's live diagnostic input.

## Live producer

`ChunkArea` now retains a validated 512-slot view of the fine `RenderSection`s that
currently belong to that 8x8x8 coarse region. Ring-grid movement does not require an
expensive removal scan: a stored reference is used only when its current
`RenderSection.getChunkArea()` and local packed slot still match the region; stale
entries are discarded lazily.

The live candidate producer iterates that owned-section superset, not the CPU visible
`sectionQueue`. For each layer it records the current draw metadata and:

- `READY` only when the section has nonzero geometry and its vertex allocation is
  ready for drawing;
- `GRAPH_VISIBLE` from the current traversal stamp;
- the exact terrain-layer ordinal;
- the section's packed 0..511 region slot.

A content fingerprint includes region/mesh/visibility revisions plus every emitted
command/flag field. Identical frame-local batch copies therefore do not continually
republish the same table, while upload-readiness or graph-visible changes are still
noticed even when a mesh revision alone would not reveal them.

## Graph/frustum ownership split

The CPU traversal already provides a useful hybrid boundary. `WorldRenderer.addNode`
stamps `RenderSection.lastFrame` after graph/smart-culling eligibility is reached but
**before** the section-level frustum rejection. Seeds are also stamped before they
enter the traversal queue. `ChunkArea` records the traversal frame associated with
its authoritative visible queue and treats matching section stamps as the live
`GRAPH_VISIBLE` predicate.

The GPU table can therefore include graph-reachable sections that the CPU frustum
later rejects. The compute shader owns that frustum decision in the diagnostic path,
which is materially different from re-filtering the already-visible CPU queue.

The production `VFrustum` exposes the same six normalized inward-facing plane
equations used to initialize its CPU `FrustumIntersection`, together with the exact
camera-relative region origin after `offsetToFullyIncludeCameraCube(8)`. GPU and CPU
therefore evaluate the same coordinate space and adjusted culling volume.

One rare semantic edge is deliberately not declared proven yet: when the camera is
outside the build-height range, `initializeQueueForFullUpdate` can seed a horizontal
set directly. Real-hardware diagnostics should expose any difference between that
special seed behavior and the reconstructed graph+frustum predicate before the gate
is closed.

## Live diagnostic comparator

The live diagnostic is restart-only and opt-in:

```text
-Dvulkanmod.experimentalGpuSectionSelection=true
-Dvulkanmod.debugGpuSectionSelection=true
-Dvulkanmod.debugGpuSectionSelectionSamples=8
```

The sample count defaults to eight when omitted. The comparator globally allows one
claimed live table at a time, waits until that exact generation becomes resident,
and spaces claims by at least 0.5 seconds. This intentionally bounds the cost because
each diagnostic sample submits compute and performs a synchronous readback.

For a claimed generation the CPU snapshots the authoritative production
`sectionQueue` set using the same nonzero-geometry and upload-readiness rules as
`RegionDrawBatch`. The diagnostic then performs three independent checks:

1. reconstruct graph+frustum+layer+readiness selection from the candidate table and
   exact CPU `VFrustum`, and compare it with the production CPU queue set;
2. dispatch `section_select_probe.comp` against the **resident device-local live
   table**, with a full 512-command output capacity;
3. compare the GPU result with the CPU queue set, including requested/written counts,
   overflow state, exact section membership, uniqueness and all five indirect-command
   words for every selected section.

Useful log markers are:

```text
VULKANMOD_GPU_LIVE_SECTION_SELECTION_OK
VULKANMOD_GPU_LIVE_SECTION_SELECTION_MISMATCH
VULKANMOD_GPU_LIVE_SECTION_SELECTION_ERROR
```

A mismatch is reported but does not alter or abort production terrain rendering.

## CI evidence

Synthetic/bounded groundwork remains green:

- CI #418 (`34808397281`) — initial bounded compaction proof;
- CI #419 (`34810466530`) — exact 39/512 eligibility + frustum match, bounded overflow
  and stale-generation rejection;
- CI #420 (`34813758435`) — asynchronous residency bytes, generation replacement,
  invalidation and `ChunkArea` lifecycle cleanup.

Live-integration source is also clean across the complete workflow:

- CI #421 (`34818047289`) — `be7b206`, live per-layer candidate publication;
- CI #422 (`34818691743`) — `59e6c75`, production-frustum extraction and live
  comparator plumbing;
- CI #423 (`34819161719`) — `f7330d9`, full region superset ownership and direct
  GPU-vs-authoritative-CPU set comparison.

All three passed compilation/distribution, both Lavapipe Vulkan startups, post-chain,
depth post-chain, screenshot readback, Crash Assistant, Chat Heads, Flywheel, log
upload and artifact upload.

CI proves the code and synthetic Vulkan contracts, not correctness or performance on
the user's RX 6900 XT / Create Chronicles workload. No performance improvement is
claimed from this diagnostic path.

## Phase 7 status and next boundary

Phase 7 remains **4/11 gates complete**. The GPU visibility/section-selection gate is
not closed until the live comparator produces representative real-hardware/modpack
evidence with no unresolved mismatches.

The next evidence step is to run the current source with the three JVM properties
above, move through representative normal terrain, rotate the camera across region
boundaries, and retain the `VULKANMOD_GPU_LIVE_SECTION_SELECTION_*` lines. Also test
an unusual camera/build-height situation if practical so the direct seed edge is not
silently assumed equivalent.

After exact live selection is proven, the next source boundary is **not** more
selection predicates. It is a frame-safe production output contract:

- persistent per-frame/per-layer GPU-selected indirect output;
- explicit transfer/compute/indirect-draw synchronization;
- bounded output capacity and overflow fallback to the current CPU batch;
- generation/lifecycle invalidation across area reuse and mesh replacement;
- a feature-gated consumer that can compare or shadow the CPU commands before any
  production ownership switch;
- eventual removal of CPU region/graph ownership only after the hybrid path is proven.

Lighting-demand telemetry remains an independent prerequisite for hybrid GPU meshing;
it is not a blocker for this section-selection/indirect-command track.
