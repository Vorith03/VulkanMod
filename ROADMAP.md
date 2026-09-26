# VulkanMod Forge 1.20.1 — Canonical Roadmap

This file is the **canonical execution roadmap** for the Forge 1.20.1 port and performance project.

It answers three questions for every current and future agent:

1. What phase are we in?
2. What exact gate are we trying to close?
3. What must be true before we advance?

Live Git/CI/runtime evidence remains authoritative for facts. This roadmap is authoritative for **sequencing and completion criteria** unless the user explicitly changes priorities.

---

## Roadmap governance

### Session continuation

Session startup, live-state recovery, and continuation are governed by the canonical procedure in `AGENTS.md` Section 3A. This file does not define a second startup checklist.

Once live state is established, start from the first unsatisfied mandatory gate in the **ACTIVE** phase unless a live regression, prerequisite blocker, correctness issue, or explicit user priority makes a documented detour necessary.

### No silent roadmap drift

Agents must not silently replace the active goal with a more interesting one.

A temporary detour is allowed when:

- CI or runtime has regressed;
- the active gate is blocked by a prerequisite defect;
- a newly discovered correctness issue makes continuing unsafe;
- the user explicitly reprioritizes the work.

When detouring, report the reason, fix/characterize the blocker, then return to the active roadmap gate.

A substantial roadmap reorder should be committed to this file and called out to the user.

### Evidence required to check a gate

A checkbox becomes complete only when there is corresponding evidence such as:

- green CI;
- Vulkan validation success;
- a reproducible automated test;
- a user RX 6900 XT runtime/visual result;
- a measured benchmark.

Code existing in the repository is not by itself proof that the behavior works.

### Progress percentages

Do not invent an overall project percentage from intuition.

For reports, use:

- highest completed legacy milestone from `AGENTS.md`;
- active phase;
- mandatory gates completed / total in that phase;
- active gate identifier.

If the user asks for a percentage, the default percentage is the **active phase gate percentage**. Explain that later phases are not equal in size.

---

# Phase 0 — Agent/CI infrastructure

**Status: DONE**

Goal: make autonomous repository work reproducible and inexpensive to iterate.

Mandatory gates:

- [x] Branch CI builds and verifies the distributable Forge JAR.
- [x] Lavapipe Vulkan startup smoke exists.
- [x] Forge no-early-splash Vulkan startup smoke exists.
- [x] Crash Assistant and Flywheel positive compatibility gates exist.
- [x] Fast pre-push `scripts/ci/agent-check.sh` exists for executable checkouts.
- [x] CI smoke logic is centralized and branch concurrency cancels superseded runs.

**Progress: 6/6**

---

# Phase 1 — Forge foundation and packaging

**Status: DONE**

Goal: produce a real Forge 47.3.0 / Minecraft 1.20.1 VulkanMod artifact rather than a development-only launch.

Mandatory gates:

- [x] ForgeGradle project is structurally valid.
- [x] Java 17 compilation succeeds.
- [x] Mixins/refmap are packaged and apply.
- [x] required LWJGL Vulkan/VMA/shaderc pieces are packaged correctly.
- [x] reobfuscated distributable JAR is produced and verified.
- [x] Forge can create the Vulkan `GLFW_NO_API` game window.
- [x] Vulkan renderer activation is demonstrated rather than silently falling back to OpenGL.

**Progress: 7/7**

---

# Phase 2 — Playable renderer baseline

**Status: DONE**

Goal: establish a known-working gameplay baseline before invasive optimization.

Mandatory gates:

- [x] title/menu rendering works;
- [x] a normal world loads through Vulkan;
- [x] terrain renders;
- [x] entities and normal GUI paths render;
- [x] textures render in gameplay;
- [x] liquid rendering regression is fixed and visually confirmed;
- [x] RX 6900 XT gameplay is playable with good baseline behavior;
- [x] terrain region batching/cache foundation can run without preventing gameplay.

**Progress: 8/8**

---

# Phase 3 — Core rendering correctness hardening

**Status: DONE**

Goal: close remaining architectural correctness holes before treating performance measurements as trustworthy.

Mandatory gates:

- [x] offscreen framebuffer/render-pass destruction is deferred safely;
- [x] post-effect pipeline destruction is deferred safely;
- [x] graphics pipelines are reused across compatible render passes without stale native handles;
- [x] fullscreen blit plus RenderTarget clear/filter/resize state is Vulkan-correct for supported paths;
- [x] `RenderTarget.copyDepthFrom` has a Vulkan depth-copy path with corrected depth/stencil barriers;
- [x] MainTarget/swapchain color can be sampled by supported post effects;
- [x] a real vanilla `shaders/post/creeper.json` PostChain constructs successfully in CI;
- [x] **P3.8 — execute one real `PostChain.process(...)` frame under Lavapipe, submit/present it, and exit cleanly;** (CI #282)
- [x] P3.9 — RX 6900 XT visual post-effect check: Creeper and Enderman spectator effects visually confirmed correct on 2026-09-09 after CI #289 fixed pixel output and viewport/scissor validation;
- [x] P3.10 — same-frame Vulkan screenshot transfers with frame-fence readback; post-present requests defer to the next frame and unsupported synchronous entry points are gated. Pixel/resize/Forge-event smoke and synchronization validation pass (CI #284).
- [x] P3.11 — characterize and fix MainTarget depth-aux sampling / sampler-filter semantics for vanilla transparency (nearest/clamp depth sampling, valid copies/barriers; CI #282). Broader mod-specific filtering remains unproven.

**Progress: 11/11**

Phase 3 is complete. Automated Lavapipe pixel/validation coverage and the RX 6900 XT Creeper/Enderman visual check now agree that the supported vanilla PostChain path is rendering correctly.

### Phase 3 exit rule

Do not begin a major terrain renderer rewrite while a known command-buffer/layout/readback correctness defect remains uncharacterized. Phase 3 has satisfied this rule. The earlier bounded GPU-terrain priority produced Phase 7 groundwork; the user has now returned the active priority to Phase 4. Phase 5 measurements are still required for performance claims.

---

# Phase 4 — Create Chronicles compatibility baseline

**Status: ACTIVE BY USER DIRECTION (2026-09-26); RELOAD AND RE-ENTRY DEFERRED**

Goal: prove the renderer works in the user's actual target environment and identify the minimum incompatible renderer-replacement set.

Mandatory gates:

- [x] Flywheel 0.6 has a positive CI startup gate;
- [x] Crash Assistant 1.9.7 has a positive CI startup gate;
- [x] current distributable launches the target Create Chronicles instance with Vulkan active; build 289 reached the full ~300-mod instance/world with `Vulkan renderer active: AMD Radeon RX 6900 XT (RADV NAVI21)` on 2026-09-09;
- [ ] Create/Flywheel contraptions render correctly in ordinary gameplay;
- [ ] world enter/leave/re-enter and resource reload paths survive in the modpack;
- [ ] representative particles/translucency/entities/GUI paths are checked for visible regressions;
- [ ] incompatible renderer replacements (for example Embeddium/Rubidium/Oculus if applicable) are evidence-backed and minimized;
- [x] a concise compatibility/known-limitations matrix is committed (`docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`).

**Progress: 4/8**

**Current focus:** use #745 for the next narrow RX visual gate: Creative block/item imagery and third-person/player or another representative entity. #744 repairs auxiliary `MainTarget` ownership and #745 restores fixed core sampler reconciliation, but both remain unconfirmed on the RX machine. If that gate passes, continue with Create/Flywheel contraptions, Create UI, representative effects, and a real portal. The user explicitly deferred reload and world re-entry for this pass; its checkbox stays open. Keep production memory safety intact. The earlier terrain priority override is historical context rather than the current sequencing instruction.

### Shaderpack scope

Vanilla Minecraft post effects are core correctness and belong in Phase 3. Full Iris/Oculus-style shaderpack compatibility is **not** an initial release blocker unless the user explicitly promotes it; renderer replacements may be fundamentally incompatible and should be characterized rather than forced together.

---

# Phase 5 — Performance baseline and measurement discipline

**Status: MEASUREMENT CONTRACT PRESENT; COMPARABLE RUNS OPEN**

Goal: create apples-to-apples measurements so optimization claims have evidence.

Mandatory gates:

- [ ] define a fixed test world/route and graphics settings for repeatable measurements;
- [ ] record an OpenGL comparison baseline on the same machine/settings/modpack;
- [ ] record the Vulkan baseline on the same machine/settings/modpack;
- [ ] record frame-time behavior in addition to average FPS (at minimum low-percentile or hitch-sensitive evidence);
- [x] define a terrain traversal/chunk-visibility stress case;
- [x] define a Create-heavy/modded rendering stress case;
- [x] commit the benchmark procedure and acceptance rule: no performance claim without comparable before/after evidence.

**Progress: 3/7**

Evidence: `docs/TERRAIN_PERFORMANCE_BASELINE.md`; fixed coordinates and A/B results remain open.

### Default comparison target

The comparison should answer practical user questions, not win a synthetic benchmark. Use the user's actual 2560×1440 gameplay configuration unless the benchmark procedure explicitly calls for an additional resolution.

---

# Phase 6 — Terrain renderer v1: persistent region batching

**Status: PERSISTENT FOUNDATION VERIFIED; RX VISUAL/PERFORMANCE GATES OPEN**

Goal: outperform the traditional Minecraft submission model using a stable Vulkan terrain backend before adding mesh shaders.

Mandatory gates:

- [x] region batch-layout regression coverage exists;
- [x] basic terrain region-cache/batching smoke coverage exists and batching can be enabled;
- [x] complete RenderRegionCache lifecycle audit for rebuild/unload/world transitions with no stale GPU ownership;
- [x] move terrain geometry toward persistent region-scoped GPU allocations;
- [x] implement stable suballocation/reuse rather than churn-heavy per-rebuild allocation where practical;
- [x] reduce CPU draw submission count with region/layer batching;
- [ ] add indirect/multi-draw-style submission where profiling shows it is beneficial;
- [x] preserve a known-good fallback path while the new backend matures;
- [ ] demonstrate visual correctness under high chunk churn / camera movement;
- [ ] measure a reproducible improvement (or reject/rework the design if it does not improve the Phase 5 baseline).

**Progress: 7/10**

Evidence: lifecycle audit at #342 and live #351 region-cache/startup logs. No measured speedup is claimed.

### Design target

This phase should make terrain data **persistent, compact, and batch-friendly**. It is intentionally useful even on hardware/drivers without mesh-shader support and becomes the data/residency foundation for Phase 7.

---

# Phase 7 — GPU-driven terrain and hybrid meshing

**Status: BOUNDED GROUNDWORK VERIFIED; PAUSED FOR CURRENT PHASE 4 PRIORITY**

Target sequence: persistent regions -> GPU visibility/section selection -> GPU
indirect commands -> GPU terrain representation -> hybrid meshing -> optional mesh
shaders. Define input data now without enabling unqualified GPU rendering.

Mandatory gates:

- [x] define and validate compact CPU/GPU section input with conservative exception handling;
- [x] add bounded region-scoped voxel/state GPU residency and independent update/invalidation;
- [x] add feature-gated compute plumbing with explicit barriers, ownership and fallback;
- [ ] implement correct GPU visibility/section selection;
- [x] generate bounded GPU indirect commands while retaining direct/legacy fallbacks;
- [x] qualify reusable baked-model templates and resolve Java-dependent instance metadata;
- [x] implement hybrid ordinary-cube meshing with halo/light/tint inputs and output-overflow fallback;
- [ ] integrate rebuild/unload/world/resource-generation transitions without stale GPU data;
- [ ] preserve arbitrary Forge callbacks, block entities and unsupported models on CPU;
- [ ] preserve translucent/tripwire rendering until separately supported and validated;
- [ ] obtain RX 6900 XT A/B correctness/performance evidence before enabling an accelerated default.

**Progress: 6/11 verified gates.** Input infrastructure, bounded region residency,
diagnostic compute plumbing, fail-closed reusable model instances, bounded GPU
indirect-command generation/fallback, and hybrid ordinary-cube meshing are verified.
Live GPU selection correctness, broader lifecycle/Forge-preservation validation, and
RX 6900 XT correctness/performance evidence remain open; accelerated consumption stays
default-off pending representative hardware evidence.

Current lighting/output work has advanced beyond the earlier dense-lattice prototype.
Reusable canonical model templates, bounded sparse lighting capture/decode, exact
Minecraft-matched AO/color/light reconstruction, and complete packed 20-byte terrain
vertex generation are proven for the qualified subset. The recovered Create Chronicles
density sample still supports bounded sparse input; do not repeat that collection.
CPU terrain output remains authoritative in normal gameplay because all accelerated
terrain gates are default-off. With the explicit experimental gates enabled, fully
qualified REPLACE sections can become GPU-first and conservative APPEND sections can
split one generation between CPU exception geometry and GPU ordinary-cube geometry.

The bounded-output contract includes generation-owned persistent area-buffer publication.
`GpuTerrainSectionMesher` is now a reusable production-owned compute dispatcher rather
than living only inside the smoke test. `GpuTerrainSectionMesherBridge` can, when the
experimental property is enabled, consume exact-generation voxel + sparse-lighting
residency and the current qualified model table, reserve bounded output, dispatch outside
an active render pass, and publish only an exact, non-overflow result for a section whose
visible block-model geometry is fully qualified. Missing/stale input, unsupported visible
geometry, fluids, allocation/dispatch failure, generation turnover, or count mismatch
leave the CPU mesh authoritative. CI #503 is green for this bridge state.

The reusable model/template gate is complete for the supported subset as recorded in
`docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`. The qualifier exercises
Forge's actual ModelData/render-type quad API, requires a singleton solid layer and
rejects seed-varying geometry. Unsupported Forge callbacks and per-position offsets
remain CPU-only rather than being approximated.

The section-selection/indirect track is now live rather than synthetic-only. The
versioned candidate table covers generation, region identity, mesh readiness, CPU
graph visibility, terrain layer, nonempty draws and six-plane frustum intersection.
A live producer builds each layer's table from the full region-owned fine-section
superset, freezes the production frustum for that generation, and publishes through
generation-safe device-local residency. The rate-limited diagnostic can compare live
GPU selection and exact five-word commands with the authoritative CPU queue.

Persistent GPU indirect output remains a bounded storage+indirect buffer with a
512-command capacity, zero-tail commands and explicit synchronization. The separate
default-off production indirect consumer retains the CPU batch whenever generation,
region, candidate-superset, capacity, or feature-gate checks fail. See
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`.

The default-off generated-geometry consumer remains fail-closed. `RegionDrawBatch.FrameBatch`
can consume exact-generation GPU output for REPLACE or emit CPU-then-GPU APPEND commands
for one section. Publication/invalidation advance mesh revision so cached frame batches
cannot retain stale offsets, APPEND suppresses both halves until its CPU exception upload
is ready, and stale/missing GPU residency falls back to complete CPU output when available.

Production section-mesher completion is non-blocking on the render thread. Qualified
fresh REPLACE sections can skip ordinary CPU `renderBatched(...)`; conservative APPEND
workers omit only the GPU-owned ordinary-cube subset while preserving unsupported
geometry, fluids, block entities, and protected neighbors on CPU. Dirty APPEND rebuilds
stage CPU exception geometry and GPU output independently, retain the previous complete
pair, and switch both halves atomically only when the replacement generation is ready.
CI #723 validates the current combined terrain/compatibility tree, including transition,
face-policy, readback, overflow/fallback, hybrid command-count/section-count oracles,
the GLSL declaration-parser regression, Immersive Portals framebuffer rendering under
VulkanMod's no-OpenGL-context window, per-portal-world `LevelRenderer` ownership,
recursive render-buffer use, terrain clip-plane propagation across all three Vulkan
terrain pipelines, IP reload-hook compatibility, an aliased `rendertype_cutout` terrain
shader, and an aliased `rendertype_entity_translucent` model-view shader that mirrors
IP's entity/projection/weather clipping state machine. Distant Horizons 3.2.0-b is explicitly fail-closed under
Vulkan by suppressing its OpenGL LOD draw/fade passes.

The hybrid implementation gate is therefore closed. The first attempted narrow
Create Chronicles/RX 6900 XT run stopped before terrain evidence because Immersive
Portals still issued a raw stencil GL call; that regression and the downstream portal
multi-world/clipping hazards are covered by CI #690. A later full-pack build #711 run
then exposed a separate Create 0.5.1.j startup requirement: its GUI helper creates an
off-screen stencil RenderTarget, so the audit-era fail-closed stencil rejection was not
sufficient. Off-screen Vulkan stencil support plus a call-site bridge for Create's raw
`GL_STENCIL_TEST` toggles are implemented and covered by the exact Create fixture. The
same #711 run also exposed parser noise and a Twilight Forest/Immersive Portals aliased-
program failure once Forge shader registration was correctly restored: `647c13e225b4`
fixes comment text being parsed as GLSL declarations, and `b9910cfb0675` binds the
correct pre-model-view IP clip plane when a namespaced Forge shader aliases one of IP's
terrain programs. The next RX run on build #720 confirmed those fixes but exposed the
same IP name/program mismatch in Alex's Caves: `rendertype_sepia` aliases transformed
`rendertype_entity_translucent`. `5964641c5428` adds the corresponding model-view alias
bridge using IP's own entity/projection/weather clipping semantics. `a12fb862873c` adds the
observed `particle` alias shape to the IP smoke and `860961c6b636` adds a selected-resource-
pack retention oracle; CI #723 is green for all of them plus the existing Create stencil
fixture. The next evidence boundary
is a repeat functional run with REPLACE + APPEND enabled, including looking through a real
portal and performing a dirty mixed-section rebuild.
This remains a correctness test, not a performance claim. Live GPU visibility/section-
selection correctness and broader lifecycle/Forge preservation gates remain open until
representative runtime evidence supports them.

Mesh-shader capability detection, optional meshlet formats and a mesh-shader draw
backend remain later work. They must not become a prerequisite for classic compute
or CPU fallback on the RX 6900 XT. Existing Phase 5/6 measurement gates remain open.

---

# Phase 8 — Stability, compatibility closure, and release candidate

**Status: PLANNED**

Goal: turn the optimized renderer into something the user can keep installed rather than a benchmark prototype.

Mandatory gates:

- [ ] repeated world load/unload/reload cycles do not show unbounded Vulkan resource growth;
- [ ] resize/window/fullscreen transitions are stable on the target setup;
- [ ] extended normal gameplay session completes without renderer crash/device loss;
- [ ] representative Create Chronicles gameplay is stable and major visual defects are closed or documented;
- [ ] Vulkan validation/debug runs are clean enough that remaining messages are understood and non-blocking;
- [ ] final compatibility matrix and required mod/configuration changes are documented;
- [ ] final OpenGL vs Vulkan benchmark report is recorded, including cases where Vulkan is not faster;
- [ ] installable release-candidate JAR is produced from green CI with concise test/install notes.
