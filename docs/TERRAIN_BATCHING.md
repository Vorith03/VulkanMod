# Terrain region batching — Forge 1.20.1

## Goal and scope

On 2026-09-06 the user confirmed the water fix on the RX 6900 XT and requested
Nvidium-like terrain rendering as the next priority. That explicitly advances
terrain work before the remaining full-modpack compatibility gate.

This first implementation adds **cached region multi-draw submission**. It is
not a complete Nvidium port or a mesh-shader/compute-culling renderer. Minecraft
world generation, networking, and CPU chunk meshing remain unchanged. Expected
benefit is less render-thread work during terrain rendering and chunk arrival;
actual FPS, frame-time, and visible-chunk loading gains require local measurement.

Nvidium describes a near GPU-driven mesh-shader terrain pipeline:
[official project description](https://modrinth.com/mod/nvidium).
The portable Vulkan building block used here is
[indexed indirect drawing](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdDrawIndexedIndirect.html).

## Implementation

- Reuse the existing 8×8×8-section region geometry buffers and CPU visibility selection.
- Cache Vulkan's 20-byte indexed draw commands in coherent mapped buffers, one
  per region, opaque layer, and frame in flight. Buffers are GPU-accessible and
  host-visible; this does not introduce an unbounded distant-terrain VRAM cache.
- Rebuild only on visibility/mesh revisions or incomplete vertex uploads.
  Each active buffer is bounded to 512 commands (10 KiB).
- Encode region-local section coordinates in `firstInstance`; the vertex shader
  decodes them through `gl_InstanceIndex`. Pass one camera-relative region offset
  as 12-byte push constants. No per-section position UBO is uploaded.
- Gate on `multiDrawIndirect` and `drawIndirectFirstInstance`, enable the features
  at device creation, and respect the unsigned `maxDrawIndirectCount` limit.
- Preserve the existing translucent/tripwire path and its sorting. Vertex uploads
  must be complete before their geometry enters the new cached draw stream.
- Frame copies are rewritten only after that frame's existing fence wait. Region
  recycling, world teardown, and frame-count changes retire caches through the
  renderer's existing deferred buffer release.
- F3 reports region sections/draw calls and command updates/bytes per frame.

`Region Batching` is enabled by default, including for old config files lacking
the new field. Unsupported devices fall back. Toggle it in VulkanMod's video
options, or set `"regionBatching": false` in its JSON config while the game is
closed. Leave the older `Indirect Draw` option off for a baseline comparison.

The old indirect path was already present but disabled by default. This change
adds a separate cached path; it does not claim to invent multi-draw or GPU culling.

## Validation

- Build checks cover all 512 coordinate encodings, invalid coordinates, signed
  vertex offsets, command layout, negative/distant origins, and unsigned limits.
- Startup smoke tests compile the new GLSL and create its shader modules/layout.
- The real cache is exercised against mapped Vulkan buffers: upload readiness,
  cache reuse, mesh edits, visibility removal, section reset, separate frame
  storage, and preservation of translucent/tripwire selection.
- Startup probes do **not** rasterize a world or benchmark GPU performance.

## Local comparison

Install only `VulkanMod_Forge_1.20.1-0.3.2-forge.2-all.jar` in the same minimal
Forge 47.3.0 instance. Preserve `earlyWindowControl=false`.

1. Use the same world, render distance, resolution, and resource pack for both runs.
2. With Region Batching enabled, stand still and inspect the F3 region counters.
   Once uploads/visibility settle and all frame copies are populated, command
   update bytes should fall to zero while sections still render.
3. Fly through already generated terrain, turn rapidly, cross region boundaries,
   and place/break blocks. Check leaves, water, underground terrain, and negative
   coordinates. Try resource reload, resize, and world exit/re-entry.
4. Toggle Region Batching off, keeping legacy Indirect Draw off, and repeat the
   same route. Record FPS/frame-time behavior and visible terrain catch-up time.
5. Return `logs/latest.log`, F3 screenshots, and any visual defect. Avoid treating
   fresh world-generation timing as a renderer-only benchmark.

Next phase: use the measurements to choose between chunk build/upload throughput
work and GPU-generated visibility/commands. Full mesh-shader culling would require
feature probing, meshlet data, additional pipeline stages, synchronization, and
GPU validation; it is not delivered by this first region-batching patch.
