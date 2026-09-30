# CPU allocation and terrain-admission follow-up

## Evidence and scope

The build #825 RX stationary diagnostic is still the latest user-machine
performance evidence. See `PERFORMANCE_CAPTURE_BUILD_825_2026-09-29.md`.
It shows about 316.6 MiB/s of render-thread allocation and a settling-period
scheduling/build disparity. It does not identify the ~20.66 ms unnamed tick
remainder, nor prove which allocations dominate. Build #830's tick leaf probes
remain necessary before changing vanilla/Forge/mod tick semantics.

This slice removes concrete unnecessary work found in the relevant code paths.
It does not claim a measured RX frame-time or FPS improvement.

## Changes

1. `VRenderSystem.calculateMVP()` formerly constructed two Matrix4f instances and
   two FloatBuffer views per call. Reusable per-thread MatrixProduct scratch reads
   the byte buffers directly and writes the same projection * model-view result.
   Every call reloads both inputs; there is no matrix-value cache that could hide
   a mod's direct buffer update. Buffer positions and inputs are preserved, and
   output/input aliasing is supported. This does not change shared render-state
   threading or add native allocations/lifetime ownership.
2. Pipeline-state hashes formerly used nested Objects.hash varargs arrays and
   primitive boxing on graphics-pipeline lookup. Explicit 31-fold hashes preserve
   the exact old numeric values, render-pass compatibility semantics and disabled
   blend equivalence. Hashes are recomputed because LogicOpState is mutable.
3. `WorldRenderer.scheduleUpdate()` now applies the worker's existing
   `RenderSection.hasXYNeighbours()` readiness rule before region capture and
   task allocation. Previously such tasks could be rejected immediately by the
   worker, call setDirty() (including generation invalidation), and be recreated
   on the next traversal. Admission refusal leaves the section dirty, requests
   another traversal, consumes no scheduling capacity and increments no scheduled
   counter. The worker recheck remains mandatory for unloads after admission.
   The existing <=24-block readiness exemption is preserved. Repeated graph
   traversal while neighbours are absent is still possible; this patch does not
   claim to eliminate every settling cost or explain all #825 counter growth.

## Validation

Focused Java 17 compilation, resource processing, terrain region layout and
section voxel snapshot tests pass. New tests run as part of Gradle check/build:

- MatrixProductTest compares 1,000 arbitrary/perspective/affine cases bit-for-bit
  with the old implementation, exercises consecutive scratch reuse, nonzero
  buffer positions and aliased outputs, and measures warmed thread allocation.
  100,000 warmed new MVP updates allocate zero bytes on this JVM, versus
  27,200,000 bytes for the old path (272 bytes per call).
- PipelineStateHashTest compares 10,000 randomized component states against the
  original Objects.hash values, including mutable logic ops and disabled blend
  equivalence.

The first local setup attempt failed while ForgeGradle's generated injected JAR
was incomplete (ZipException: zip END header not found). The bounded retry passed
compilation and all focused tests; no project dependency change was made.
The complete local production `build` also passed (39 tasks), including all
regression tasks and distributable/module verification. CI #831 / run
`36704070880` is fully green for executable commit
`bbc8dd7aa244db1f138a7329b13b8e5f7ecf5622`: both startup modes, persistent GPU
indirect, post-chain/depth-post-chain, screenshot readback, FTB Library, Pick Up
Notifier, Immersive Portals, Distant Horizons, Crash Assistant, Chat Heads,
Flywheel and Create all passed. Three private-pack steps were skipped as
configured. Artifact: VulkanMod-Forge-build-831, ID `11091298348`, ZIP SHA-256
`cc3efb35ed782b08bc537045a5fd6137f2d556295b9daeb03350b8715a488db0`.
User-machine correctness/performance validation remains pending; CI startup
smokes do not exercise in-world neighbor arrival/unload scheduling.

## Next measurement

Use one new automated stationary capture containing both these fixes and #830's
tick leaf attribution, with the same #825 world/view/framebuffer/settings and 260
FPS cap. Inspect leaf wall time/allocation first; separately compare terrain
scheduling growth and tick-local versus outside-tick allocation. Preserve the
formal Phase 5 matched-baseline gates and do not infer pooled percentiles from
window summaries. Entity behavior, callback skipping, texture animation rates,
and GPU submission/fences have not been altered without supporting attribution.
