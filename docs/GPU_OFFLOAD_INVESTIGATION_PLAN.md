# GPU offload investigation and implementation plan

Requested 2026-10-02. This is an engineering plan, not implemented functionality or a speedup claim. Follow AGENTS.md section 3A and the live Phase 5 checkpoint before each executable slice.

## Objective and current evidence

Reduce CPU critical-path work while preserving Forge 1.20.1 rendering, animation cadence, exact source data, and bounded memory/lifetime ownership. Judge success by end-to-end frame/tick latency, not the amount of code moved to compute.

The hardware-validated #935 stationary capture measured 25.208 ms/client tick, including 17.474 ms texture work and 1.883 ms particle ticking. Texture work includes 9.834 ms inside sprite uploads and 7.640 ms outside them; the latter is not proof of interpolation cost. Main graphics execution was 1.277 ms, but its timestamp scope excludes helper texture upload command buffers. GPU headroom is therefore a hypothesis to verify, not a whole-device measurement. #946 has passed CI and awaits matched RX measurement after CPU batching/allocation improvements.

Implementation priority:

1. Measure #946 and identify remaining transferable texture work.
2. Qualify immutable animated-frame residency and GPU copies.
3. Qualify compute interpolation if its remaining CPU cost justifies it.
4. Resume existing terrain visibility/indirect and hybrid meshing tracks when traversal evidence supports them.
5. Investigate particle owners, then consider a narrow GPU render/simulation adapter only if measured benefit warrants one.

These are decision gates, not five unconditional rewrites. Research, contract work and synthetic oracles may proceed while hardware evidence is pending; production bypasses and default promotion may not skip their gates. Keep one executable experiment active at a time so results remain attributable.

## Common experiment and acceptance contract

- Keep Java 17, Minecraft 1.20.1, Forge 47.3.0 and RX 6900 XT/RADV as fixed targets. Keep Embeddium/Oculus excluded from the comparable renderer configuration. Distant Horizons is unresolved and must not be used as a working LOD comparison.
- Use the canonical stationary camera/settings and eastbound traversal in TERRAIN_PERFORMANCE_BASELINE.md. Record pack/mod hashes, JVM/driver/settings, graphics gates, framebuffer, run identity and source commit. Separate first-load cost from warm operation.
- Establish a corrected CPU reference before comparing an offload path. Collect at least three paired captures with alternating A/B order; expand only when variability prevents a decision. Compare identical semantic workloads and the same profiler configuration, then confirm the winning path without expensive shadow validation enabled.
- Report client tick and frame mean/p95/p99, slow-frame counts, loop gaps, render-only versus tick frames, worker queue/build/publication latency where relevant, allocations/GC, peak native/host/device memory, staging bytes, commands/dispatches and fallback rates. Use per-sample residuals; do not subtract independently calculated percentiles.
- Add GPU timestamps around each experimental copy/compute submission, sampling availability asynchronously. Identify queue and timestamp scope; do not add overlapping queue durations into a fictitious GPU frame total. Also measure CPU submission/fence waits and the time until outputs become usable. No per-frame host readback or forced wait in the normal accelerated path.
- Proposed adoption threshold: repeatable improvement beyond observed run variability, targeting at least 10% of the affected subsystem and 3% of end-to-end frame or tick time in its intended workload. These are initial decision targets, not promised gains. A p99 regression greater than 5% needs explanation and reruns; any correctness failure blocks promotion regardless of speed.
- Each experiment ends with a durable result: adopt, revise, or reject, with evidence and the next smallest action. A compute microbenchmark alone cannot satisfy the performance gate.

## T0 — Establish texture costs and transferable workload

Start from #946; first compare it with #935 to evaluate the already-shipped CPU changes. Read texture_outer_batch_attribution alongside the client-tick detail. Reconcile measured tick counts and scopes before subtracting averages.

If the remainder is material, add bounded sampled attribution around actual SpriteContents ticker and interpolation paths: metadata iteration, interpolation pixel work, staging/copies, layout transitions and submission. Distinguish interpolated sprites from discrete-frame sprites; collect dimensions, mip count, unique frame count, update rate, bytes written, custom ticker class and source namespace when reliably available. Namespace/class attribution is a diagnostic hint, not proof of mod ownership. Bound owner tables and sampling cost, expose omitted samples, and exclude warmup.

Inspect the actual Forge-patched ticker/interpolation implementation and third-party mixin call order. Write down exact frame indices, durations, repeated/reordered metadata entries, channel order, alpha behavior, interpolation arithmetic/rounding, mip treatment and catch-up/skipped-upload semantics. Do not infer these from the upload timer or reproduce them from memory.

Deliverables: cost split, eligibility inventory, measured memory-size estimate and a decision between remaining CPU optimization, resident copies and compute interpolation. If no substantial transferable work remains, stop the texture offload track and choose the next measured bottleneck.

## T1 — Immutable frame residency and exact GPU copy pilot

Attachment points to investigate: MSpriteContents, MSpriteAtlasTexture, MTextureManager, SpriteUtil, VTextureSelector and VulkanImage. Keep atlas stitching, animation metadata progression and Forge/mod callbacks on CPU. Reuse existing queue/compute infrastructure where its ownership contract applies; do not redesign the renderer.

Initial pilot supports known immutable RGBA frame/mip data and ordinary discrete-frame tickers only. Custom/dynamic NativeImages, custom tickers, unsupported formats, invalid metadata and unproven mutations stay on the CPU path. Qualification must establish immutability/invalidation, not merely recognize a familiar class name.

Upload eligible source frames and their existing mip data once into a bounded device-local buffer. Preserve atlas dimensions, UVs, samplers and all downstream shaders. First try batched buffer-to-atlas-image copies from resident source bytes when frame selection changes, using the existing transfer-destination atlas path. Compare this against the optimized CPU staging path: fewer host copies may still leave costly region recording. Only investigate alternate source images or descriptors if the pilot exposes a measured limitation.

Define a versioned numeric job record: resource generation, atlas identity/generation, source allocation generation, sprite destination rectangle, mip, frame source offsets and bounds. Validate bounds before recording commands. Deduplicate immutable source bytes only with exact identity/content and matching mip/format semantics.

Provisionally cap pilot frame residency at 128 MiB per renderer, separate from existing staging budgets; log actual residency, peak during reload, rejections and selected coverage. Choose useful sprites from measured update cost per resident byte. Adjust the cap only from measured coverage/memory evidence. No steady-state cache thrashing, unbounded preload, or synchronous eviction. Allocation failure falls back before bypassing the CPU upload. Retain original CPU sources for fallback and reload; CPU source retirement is a separate future decision.

Start on the current graphics queue, outside render passes, so cross-queue ownership is not a prerequisite. Record copy-to-sampled-read visibility and all prior atlas-read-to-write dependencies using the renderer's actual submission order. Reuse frame-completion retirement, never free resources still referenced by queued commands.

Deliverables: opt-in pilot, counters, exact GPU readback oracle, resource-pressure fallback, and paired RX result. Default remains CPU until correctness and adoption gates pass.

## T2 — Compute interpolation pilot

Proceed only if T0/#946 shows material CPU interpolation and T1's ownership/oracles are sound. CPU supplies frame IDs and interpolation progress; compute writes selected/interpolated mip pixels. Preserve exact vanilla/Forge channel arithmetic, alpha handling and rounding; if floating-point shader arithmetic differs, test an exact packed-byte/integer formulation before admitting it. Precomputed source mips remain authoritative; interpolating mip frames and generating mips afterward are not presumed equivalent.

First investigate a storage-buffer output followed by the existing buffer-to-image transfer: it avoids making every atlas storage-image capable. A direct storage-image write is a separately measured alternative requiring queried format/usage support and compatible views/layouts, not an assumed universal fast path. Preserve the current R8G8B8A8_UNORM contract; do not introduce color-space changes.

Group jobs by atlas/generation/mip into bounded dispatches; avoid one dispatch or barrier per sprite. Define exact output bounds and descriptor/allocation lifetime. Test compute-write to transfer-read and transfer-write to shader-sampled-read dependencies, plus previous frame atlas readers. Do not assume asynchronous compute is faster: evaluate a dedicated queue only after the same-queue path wins and overlap can be measured without extra waits or ownership overhead.

CPU fallback must be selected before skipping interpolation/uploads. A pending GPU result must never display a stale frame or force a tick to wait for host readback. If a GPU job cannot be safely submitted, execute the exact CPU path for that update and retain/invalidate resources under the defined generation contract. Device loss uses established failure handling, not guessed recovery after partial execution.

## Texture correctness and lifecycle gates

Synthetic CPU-reference/readback cases: non-square frame sheets, repeated/reordered frame IDs, mixed durations, interpolation endpoints and intermediate values, alpha edge cases, small/odd dimensions, every available mip, atlas-edge rectangles, multiple atlases, simultaneous CPU/GPU sprites, catch-up ticks, hidden/skipped rendering and source mutation rejection. Compare exact output bytes where semantics are byte-defined. Check animation update counts/timing, not just screenshots.

Run Vulkan validation plus build/package/startup/readback and combined compatibility fixtures. Add a resource-pressure and stale-generation oracle. Verify reload/restitch, replacement/cancellation, world close/re-entry, multiple portal worlds, resize, repeated startup/shutdown and in-flight retirement before any accelerated default. The user has deferred the existing manual reload/re-entry gate: automated lifecycle work can proceed, but keep the hardware gate open and the feature opt-in until that test is authorized and completed. Use available real resource-pack fixtures when configured; skipped private-pack CI is not coverage.

## G1 — Resume GPU terrain visibility and indirect commands

Existing Phase 7 implementation already includes candidate residency, diagnostic selection/command comparisons and default-off indirect consumption. Read the current indirect handoff and generation/lifetime contracts; do not rebuild those systems from this plan.

Use traversal/churn captures to identify CPU graph traversal, frustum selection, candidate serialization, command construction and submission costs. Count CPU work retained for Forge events and scheduling. A GPU pass that still rebuilds equivalent CPU output every frame can lose; keep shadow comparison confined to validation or bounded samples.

First compare live GPU selections and exact indirect commands against CPU output across camera motion, region wrap, portal cameras, dirty/unloaded sections, empty layers, capacity overflow and generation changes. Preserve CPU graph visibility until an alternative connectivity/occlusion policy has its own oracle; a frustum test alone cannot replace cave traversal. Do not derive occlusion from unavailable/stale depth. Consider hierarchical depth only as a separate evidence-backed experiment.

Then measure candidate-table upload/compute/synchronization/indirect consumption end to end. Retain CPU fallback for stale or unsupported input. Promotion requires the existing Phase 7 correctness, lifecycle and RX gates; main stationary GPU timing is not enough to resume this ahead of a dominant texture bottleneck.

## G2 — Qualify and extend existing hybrid chunk meshing

Build on GpuTerrainSectionMesher/Bridge, model tables, sparse lighting, output ownership and atomic REPLACE/APPEND publication. Read current model-instance/output contracts. First perform the already-documented RX functional REPLACE+APPEND test, including a portal view and dirty mixed-section rebuild; avoid repeating settled model qualification or density collection.

Measure eligible coverage, worker CPU saved, numeric input/halo/light/tint preparation, device residency churn, queue delay, GPU completion time, output capacity/fallback and total publication latency. Maintain separate stationary, rebuild-heavy and traversal results. Only expand qualification for the most expensive measured unsupported geometry with an explicit numeric adapter.

Preserve arbitrary Forge/model-data callbacks, block entities, fluids, custom geometry, unsupported offsets/random behavior and translucent/tripwire sorting on the existing CPU path. Never bypass callbacks solely because a block looks cubic. Preserve exact-generation bounds, nonblocking completion, overflow fallback and atomic CPU/GPU mixed-section replacement. Investigate translucent GPU sorting only if separately profiled and accompanied by ordering/visual oracles. Mesh shaders are optional later work and must not become an RX prerequisite.

## P0/P1 — Particle attribution, then bounded adapters

Begin with bounded sampled tick/render attribution by particle class and provider/source identity when available, live counts, spawn/removal churn and allocation hot paths. Separate simulation from render preparation and vertex uploads. Particle ticking is only 1.883 ms in #935, so its realistic savings ceiling is smaller than the original texture target.

Prefer removing redundant render preparation or GPU billboard expansion while retaining CPU simulation if that is the measured cost. For a compute-simulation experiment, admit only a specifically qualified adapter whose motion, lifetime, collisions, random stream, lighting and callbacks can be preserved. GPU outputs must not feed required Java callbacks through a new per-tick synchronous readback. Arbitrary mod particle classes remain CPU-owned.

Implement a versioned bounded numeric state buffer, spawn/removal protocol and indirect/billboard draw path for that adapter; preserve sorting/blending, portal/camera scope and CPU fallback. Test seed-controlled trajectories, collisions, lifetimes, transitions, output capacity and rendered order against the CPU oracle. Adopt only if the adapter covers meaningful measured work and improves full-frame timing. Otherwise record rejection and retain CPU behavior.

## Reviewable work packages

| Package | Output | Dependency / decision |
| --- | --- | --- |
| O1 | #946 comparison and capture-scope GPU/cost evidence | Next matched RX capture; no offload performance claim beforehand |
| O2 | Animation eligibility/ABI/memory/lifetime contract and synthetic CPU oracle | Inspect exact Forge/mod ticker semantics; may start while O1 is pending |
| O3 | Opt-in resident-frame GPU copy pilot and fallback | O1 selects meaningful target; O2 correctness contract |
| O4 | Opt-in compute interpolation and numeric equality oracle | Measured interpolation cost; O3 ownership; queried device capabilities |
| O5 | Lifecycle/pack/portal validation and paired RX adoption report | O3/O4; hardware lifecycle gate remains open while deferred |
| O6 | Existing terrain selection/indirect RX correctness and A/B report | Traversal evidence; current Phase 7 handoff |
| O7 | Existing hybrid meshing RX qualification, then measured adapters | O6 where dependent; model/output ownership contracts |
| O8 | Particle owner/cost report, then one qualified rendering/simulation pilot | Particle evidence selects target; no generic simulation replacement |

O2 bounded numeric-reference and ownership-contract work is qualified in full public CI **#988** (executable `3d01d50a6e73fc84f0daec067d248000f65d3d16`). `TEXTURE_ANIMATION_OFFLOAD_CONTRACT.md` records exact transformed Forge behavior, seven native cases and inspected bytecode. Source immutability/admission and the production copy/compute ABI remain unimplemented; no renderer default or hardware adoption gate changed.

O1 is the next hardware action; O2 production source admission remains open. Texture O3/O4 may be accepted independently; O4 is not required if resident copies resolve the bottleneck. Terrain and particles are conditional tracks. No calendar or numerical speedup promise is attached to this plan.

## Current execution status — 2026-10-06

The owner's current priority is to move safely transferable renderer work off the
CPU wherever exact semantics and fallback can be preserved. This is an explicit
detour within the existing roadmap, not permission to replace arbitrary Forge
callbacks or gameplay logic with approximate GPU behavior.

- **O2:** exact transformed ticker/interpolation contract remains qualified.
- **O3:** implemented and native-qualified. Exact-class standard animations can
  use bounded immutable device-local source residency and GPU frame copies.
- **O4:** implemented and native-qualified in full public CI **#998** at
  `f1ddc72397ed132dab16b5e953db369ba76a6ed3`. Same-graphics-queue compute
  interpolation requires `shaderFloat64`, preserves CPU schedule/clock
  ownership, uses device-local scratch and falls back before skipping CPU work.
  #997 established exact pixel equivalence but exposed a Vulkan transfer-write
  hazard; #998 adds explicit atlas transfer-write ordering and is validation-clean.
- **O5:** now the immediate texture gate. Collect matched RX performance,
  residency/compute coverage, fallback/memory counters and real modpack behavior.
  Default promotion remains blocked on hardware/lifecycle evidence.
- **O8 attribution prerequisite:** implemented and CI-qualified through #995.
  Per-class tick timing/allocation/churn and source/provider/render-type identity
  are bounded; per-particle render redirection was deliberately rejected because
  it conflicts with Immersive Portals. Existing aggregate render attribution
  remains authoritative until a non-invasive GPU adapter boundary is proven.
- **O6/O7:** existing GPU selection/indirect/hybrid code remains the implementation
  base. Do not duplicate it. The next meaningful gates are RX movement/portal
  correctness and dirty mixed-section rebuild evidence.
- **Vulkan Flywheel engine:** transformed GPU instancing prerequisites and native
  state/event pixels are already qualified, but backend activation remains blocked
  on the existing loaded-world probe and complete fallback coverage.

A future standard-animation scheduler may move CPU ticker iteration/dispatch
bookkeeping to a bulk GPU job table only if O5 shows that work remains material
after O3/O4. Keep the CPU-visible clock or establish an explicit compatibility
boundary before such a scheduler suppresses Java ticker progression.

## Primary Vulkan references checked 2026-10-02

- [Storage images and texel buffers](https://docs.vulkan.org/guide/latest/storage_image_and_texel_buffers.html): query format support and image usage/view requirements before selecting a storage-image design.
- [Synchronization examples](https://docs.vulkan.org/guide/latest/synchronization_examples.html): derive actual compute/transfer/sampled and indirect dependencies; adapt examples to the project's queue and lifetime model.
- [GPU rendering and multi-draw indirect](https://docs.vulkan.org/samples/latest/samples/performance/multi_draw_indirect/README.html): reference for GPU selection and indirect consumption, not evidence of speedup in this modpack.

Repository contracts and runtime evidence remain authoritative for this renderer. Later upstream or generic GPU examples are reference material, not permission to replace its Forge boundary.
