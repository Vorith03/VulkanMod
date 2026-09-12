# GPU-driven terrain: section input checkpoint

## Evidence and scope — 2026-09-12

Inspected live `forge-1.20.1` at `d41ff7cca9007666d765dc6bc7581c961d084b7c`,
CI #351 (run 34687677511, job 103537424600): build/distribution and all startup,
post-chain, readback and compatibility gates passed. AGENT_STATUS still named #342;
ROADMAP's older snapshot named #308. Neither was used as the live code baseline.

The user's latest useful RX 6900 XT / Create Chronicles / RD32 capture reports
approximately 7 active workers, 49 queued builds, 194 ms queue wait, 30 ms build,
30 ms publication latency, 0.1–0.2 ms terrain upload, effectively zero synchronization
wait, and small builder-to-handoff copies. This is a diagnostic sample, not an A/B
benchmark. It motivates targeting Java terrain construction rather than removing
its bounded native handoff or optimizing the already-small upload stage.

The first source slice is **a versioned, paletted section input snapshot, captured
alongside CPU compilation and published into bounded region-owned CPU staging**.
There is no compute dispatch, GPU voxel residency, GPU meshing or performance claim
in this checkpoint. This is the input record and ownership path the next SSBO upload
will consume; no generic Vulkan abstraction rewrite is involved.

## Attachment points in the live renderer

- `WorldRenderer.setupRenderer` traverses visibility and schedules dirty sections
  using a traversal-local `RenderRegionCache`. `compileSections` drains completed
  results before terrain drawing. Existing CPU traversal remains authoritative.
- `RenderSection.createCompileTask` captures the region and cancels prior rebuild /
  sort tasks. The new voxel generation advances on dirty, reset and release; a
  result captured before these events cannot publish voxel state afterward.
- `ChunkTask.BuildTask.compile` visits 4096 positions in x-fastest/y/z order. It
  already resolves solidity, block-entity presence and fluid state. Capture reuses
  those answers and adds a registry-ID lookup and palette packing only when enabled.
  No additional model calls or world traversal are introduced.
- `TaskDispatcher.scheduleSectionUpdate` already checks task cancellation on the
  render thread before updating meshes and invoking the publication callback.
  That same callback publishes the immutable voxel snapshot. A null result clears
  old input; transparency-only updates never replace section inputs.
- `ChunkArea` owns the new staging store, using the same 512-slot region addressing
  as `RegionBatchLayout` and `terrain_region.vsh`. Its revision is independent of
  `DrawBuffers`' per-layer mesh revisions. Fine-section removal, coarse-region wrap,
  world teardown and render-distance reconstruction clear this ownership.
- `DrawBuffers` / `AreaBuffer` already provide persistent geometry suballocation.
  The new store deliberately does not call `getDrawBuffers` or allocate vertex/index
  storage just to retain block data. Existing vertex format is 20 bytes: short4
  position, RGBA8 color, ushort2 UV, short2 light. Region indirect commands are the
  Vulkan 20-byte indexed-command format, with firstInstance encoding section x/y/z.
- `AreaUploadManager` submits terrain copies on the graphics queue with frame-based
  retirement. Shaderc accepts compute shaders and queue discovery finds compute
  capability, but there is no complete compute pipeline / storage-descriptor /
  terrain-dispatch backend. Adding that plus residency and a mesher in one change
  would couple too many new lifetime and synchronization rules.

## Concrete CPU/GPU boundary

| Current responsibility | Intended destination | Required boundary / limitation |
| --- | --- | --- |
| World/mod state lookup, Forge model data and callbacks | CPU | GPU sees IDs and resolved numeric streams, never Java objects/code |
| `isSolidRender`, custom face-occlusion rules | CPU initially | Captured solidity is for VisGraph, **not** proof that a face may be culled |
| VisGraph section connectivity | CPU initially | Later compute visibility must preserve cave traversal semantics or conservatively overdraw |
| Block-entity discovery, renderer lookup, global BE updates | CPU | Remains outside static terrain compute |
| Baked-model / render-layer / random-variant selection | CPU or precompiled finite tables | Only move instantiation after eligibility and variant semantics are proven |
| Face rejection for qualified ordinary cubes | GPU | Six neighbors, explicit face coverage, render layer and boundary halo required |
| Ordinary cube vertex/index emission | GPU | Qualified template, lighting/tint inputs, bounded output and overflow fallback |
| Simple baked-model instantiation | GPU eventually | Reusable immutable quad templates, per-instance variant, offsets, light and tint |
| `renderLiquid` | CPU, later a separate qualified GPU path | Neighbor heights, corner normals, waterlogging, sprites/tint and layer semantics |
| AO / light and tint evaluation | CPU initially; numeric portions may move later | Neighbor sampling, biome blending, emissive behavior and Forge hooks cannot be inferred from state ID |
| Translucent sorting and index generation | Existing CPU path first | Separate measured compute experiment; preserve water/tripwire behavior |
| CPU mesh builders, native handoff, exceptions | CPU fallback | Keep until every bypassed output is explicitly covered by a proven GPU path |
| Frustum selection / indirect command generation | GPU later | Residency/validity table, device limits and compute-to-indirect barriers |

The eventual section input consists of state/template IDs, resolved instance data,
light/tint streams, neighbor/halo validity and an exception mask/list. This checkpoint
implements **state IDs and CPU-resolved flags only**. It cannot yet independently
mesh a section. All voxels carry CPU_REQUIRED; there is no implicit vanilla/block-ID
allowlist and no claim that a BakedModel is static because its class looks familiar.

### Reusable baked-model templates

Build a resource-generation-scoped template registry on the CPU. A qualified entry
contains immutable quad positions, UVs/sprite/material references, cull direction,
coverage, tint index, shade/AO policy and render layer. Finite variants need explicit
selection rules preserving Minecraft's position seed and Forge model-data behavior.
A section refers to a resolved template/variant, rather than calling renderBatched
for every ordinary block on every rebuild.

Qualification must be conservative: world-dependent quads, arbitrary model-data
callbacks, custom render layers and unsupported geometry remain exceptions unless
an explicit adapter resolves their inputs. Templates may share geometry while
keeping per-position color/light/offset streams separate. Resource reload invalidates
the template generation (even if state IDs are unchanged), and world changes discard
section residency. Dynamic callbacks never run on the GPU. Snapshot v1 has no template
references, so it introduces no new resource-reload GPU ownership.

## Snapshot v1 ABI

Little-endian uint32 stream; all record-relative offsets are in **words**, byte sizes
are in bytes. A caller writes into its own aligned staging slice with `writeTo`.
No 8/16-bit Vulkan storage feature is needed: two palette indices are packed into
each uint32. The immutable object owns only a private int array.

| Header word | Meaning |
| --- | --- |
| 0 / 1 | Magic 0x56584c31 / version 1 |
| 2 / 3 | Total byte size / 4096 voxels |
| 4–6 | Signed world block origin x/y/z, aligned to 16 |
| 7 / 8 | Palette count / palette word offset (16) |
| 9 / 10 | Packed-index word offset / flag-plane word offset |
| 11 | Four flag planes |
| 12–15 | Reserved zero; no halo/light/tint/template streams |

Palette entries are full 32-bit runtime Minecraft state IDs, not persistent save IDs
or model IDs. Following the palette are 2048 words of packed unsigned 16-bit local
palette indices. Index `i = x | y<<4 | z<<8`; palette slot is
`(indices[i>>1] >> ((i&1)*16)) & 65535`. The four 128-word bit planes are, in order:
CPU `isSolidRender`, nonempty fluid, block-entity presence, CPU_REQUIRED.

The fallback mask is a compact exception representation: bit i remains set until a
future template compiler explicitly qualifies that voxel. Version 1 rejects attempts
to clear it. A different capability/version contract must precede GPU eligibility.
Snapshot size is `10304 + 4*paletteCount` bytes (10308 minimum, 26688 maximum).

## Bounds, invalidation and failure behavior

Enable only with `-Dvulkanmod.experimentalSectionVoxels=true` (restart required).
Default-off rendering runs no palette builder or registry-ID lookups and retains no
snapshots. Rendering remains fully CPU-generated with either setting. The opt-in
path adds CPU work and heap retention and may be slower.

Region stores share a 32 MiB payload cap and 2048-entry cap, separate from existing
process/system safety floors, which are unchanged. Pending snapshots are plain heap
objects and share the existing bounded task/result queues; per-worker scratch is
also bounded by 4096 cells. The cap is retained payload, **not total JVM overhead**.
At capacity, a new snapshot is discarded. A rejected replacement first removes its
old entry, so consumers cannot mistake stale input for the new build. No eviction,
blocking wait, native allocation or GPU allocation is added. F3 reports retained
sections/KiB and budget rejections only when enabled.

Consumers must treat absent records as CPU-only, validate origin/generation and
future template/neighbor validity, and never retain raw region addresses across
reuse. Region revisions are invalidation tokens, not GPU-completion fences. This
checkpoint stores interior cells only: a future mesher needs a consistent halo or
validated adjacent snapshots, including unknown-neighbor behavior. Runtime registry
IDs alone are insufficient to reconstruct Forge model data or lighting.

## Next bounded milestones

1. Upload this exact record into lazily allocated, bounded, region-scoped SSBO pages,
   with a section offset/length/generation/validity table and real GPU readback tests.
   Reuse region lifetime/suballocation logic, but do not pass STORAGE_BUFFER usage
   to today's AreaBuffer: its allocation switch otherwise creates an IndexBuffer.
   Avoid growth stalls by using capped pages or deferred replacement; release CPU
   staging after ownership transfers if retained residency is unnecessary.
2. Add a narrowly scoped compute consumer that verifies/decode-reduces these records
   against the CPU reference. Explicit transfer-write -> compute-read barriers,
   descriptor ranges, queue capability checks, frame retirement and unavailable-path
   fallback are prerequisites. Same-queue submission order alone is not a substitute
   for memory barriers. Never introduce vkDeviceWaitIdle into terrain updates.
3. Qualify a tiny simple-cube template subset; add resolved light/tint and halo input,
   bounded face counts/output, overflow fallback, then A/B test against CPU geometry.
   Only after correctness move selected instantiation/face work out of renderBatched.
4. Extend visibility/indirect generation using persistent section metadata, then
   simple baked models/fluids. Mesh shaders remain optional and later.

The user's desired long-term ordering remains persistent regions -> GPU visibility /
selection -> indirect commands -> GPU terrain representation -> hybrid meshing ->
optional mesh shaders. Defining the input ABI now does not require enabling meshing
before visibility or closing unmeasured Phase 6 performance gates.

## Validation contract

`testSectionVoxelSnapshot` (part of Gradle check/build) independently decodes the GPU
ABI, including 4096 unique/high state IDs, word boundaries, negative coordinates,
caller buffer offsets/order, invalid records, memory/entry limits and failed replacement.
Startup smoke is added to verify both gate settings, real registry IDs and Minecraft traversal
order, the actual publication/cancellation queue, generation rejection, region wrap,
release and mesh-revision independence. It does not execute a world build or GPU mesher.
The full existing Vulkan/compatibility CI suite remains required.

RX 6900 XT testing: compare the same baseline route/settings with capture off and on.
Record F3 voxel residency/rejections plus queue/build/handoff times and heap use, then
leave/reenter the world and change render distance to check residency resets. This is
an overhead/lifecycle experiment, not a speedup test. Use the baseline contract;
no F3+T reload test is requested for this checkpoint.

### Local validation / publication status

The pure-Java snapshot/store suite passed locally using the installed Java 17
compiler module (`java -m jdk.compiler/com.sun.tools.javac.Main`); the `javac`
launcher itself is absent. Shell syntax and `git diff --check` also passed.
Full local Gradle could not start: its uncached 8.1.1 distribution download failed
with `java.net.SocketException: Network is unreachable`.

Source commit `41ed707` and follow-up checkpoint work are local only. Automatic
approval review rejected the remote push, citing lack of explicit authorization
to publish the payload to GitHub. Remote HEAD was rechecked as `d41ff7c`. New CI and
startup results are therefore **pending**, not green. Do not distribute this as a
verified runtime JAR until push approval and the full build/smoke suite complete.
