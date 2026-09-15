# VulkanMod Forge 1.20.1 — Canonical Roadmap

This file is the **canonical execution roadmap** for the Forge 1.20.1 port and performance project.

It answers three questions for every current and future agent:

1. What phase are we in?
2. What exact gate are we trying to close?
3. What must be true before we advance?

Live Git/CI/runtime evidence remains authoritative for facts. This roadmap is authoritative for **sequencing and completion criteria** unless the user explicitly changes priorities.

---

## Roadmap governance

### Required session-start behavior

Before substantial work, an agent should inspect:

1. current `forge-1.20.1` HEAD;
2. latest CI result;
3. `AGENTS.md`;
4. `AGENT_STATUS.md`;
5. this `ROADMAP.md`.

Start from the first unsatisfied mandatory gate in the **ACTIVE** phase unless a live regression or blocker makes another task necessary.

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

Do not begin a major terrain renderer rewrite while a known command-buffer/layout/readback correctness defect remains uncharacterized. Phase 3 has satisfied this rule; the user has since explicitly prioritized bounded GPU-terrain groundwork while Phase 4 remains parked. Phase 5 measurements are still required for performance claims.

---

# Phase 4 — Create Chronicles compatibility baseline

**Status: PARKED BY USER PRIORITY OVERRIDE**

Goal: prove the renderer works in the user's actual target environment and identify the minimum incompatible renderer-replacement set.

Mandatory gates:

- [x] Flywheel 0.6 has a positive CI startup gate;
- [x] Crash Assistant 1.9.7 has a positive CI startup gate;
- [x] current distributable launches the target Create Chronicles instance with Vulkan active; build 289 reached the full ~300-mod instance/world with `Vulkan renderer active: AMD Radeon RX 6900 XT (RADV NAVI21)` on 2026-09-09;
- [ ] Create/Flywheel contraptions render correctly in ordinary gameplay;
- [ ] world enter/leave/re-enter and resource reload paths survive in the modpack;
- [ ] representative particles/translucency/entities/GUI paths are checked for visible regressions;
- [ ] incompatible renderer replacements (for example Embeddium/Rubidium/Oculus if applicable) are evidence-backed and minimized;
- [ ] a concise compatibility/known-limitations matrix is committed.

**Progress: 3/8**

**Parked work:** full-pack reload/compatibility closure remains open. See
`docs/TERRAIN_PRIORITY_OVERRIDE_2026-09-12.md`. Do not return to F3+T or lower memory
safety floors while pursuing the explicitly requested terrain/GPU track.

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

**Status: ACTIVE BOUNDED GROUNDWORK BY EXPLICIT USER DIRECTION**

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
- [ ] implement hybrid ordinary-cube meshing with halo/light/tint inputs and output-overflow fallback;
- [ ] integrate rebuild/unload/world/resource-generation transitions without stale GPU data;
- [ ] preserve arbitrary Forge callbacks, block entities and unsupported models on CPU;
- [ ] preserve translucent/tripwire rendering until separately supported and validated;
- [ ] obtain RX 6900 XT A/B correctness/performance evidence before enabling an accelerated default.

**Progress: 5/11 verified gates.** Input infrastructure, bounded region residency,
diagnostic compute plumbing, fail-closed reusable model instances, and bounded GPU
indirect-command generation/fallback are verified. Live GPU selection correctness and
hybrid terrain meshing remain open; GPU indirect production consumption is default-off
pending representative RX 6900 XT evidence.

Current lighting slice: the exact dense-lattice prototype in
`docs/GPU_TERRAIN_LIGHTING_LATTICE_PROTOTYPE_2026-09-13.md` rejects both a 20-cube
and an 18-cube plus sparse second shell as too large. Reusable canonical model
templates and partial position/UV vertex generation are proven in diagnostic compute,
but lighting metadata remains unresolved and CPU terrain output remains authoritative.
The opt-in estimator in `docs/GPU_TERRAIN_LIGHTING_DEMAND_TELEMETRY_2026-09-14.md`
now measures demand-driven point/brick density against actual CPU mesh bytes; real
Create Chronicles section evidence is the next gate for the hybrid-meshing track.

The diagnostic compact face/vertex stream is now explicitly capacity-bounded as
recorded in `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`: requested and written
counts plus overflow are verified without out-of-bounds writes. This establishes the
fallback contract needed by hybrid meshing, but does not complete that production
gate because allocation/publication, lighting/color and CPU fallback integration are
not implemented.

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

Persistent GPU output is a bounded storage+indirect buffer with a 512-command
capacity, zero-tail commands and explicit prior-indirect->transfer,
transfer->compute, and compute->indirect synchronization. Commit `d91de023` adds a
separate default-off production consumer; the CPU batch remains built and is used
whenever the shadow generation/region is invalid, candidate input is smaller than the
CPU draw set, capacity would be exceeded, or the experimental draw gate is disabled.
Commit `67dafa92` adds direct host-side regression assertions for those fail-closed
conditions and exact/bounded-superset success. CI #443 is fully green and CI #444 is
the corresponding verification run for the fallback-contract commit. See
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`.

This closes the bounded indirect-command/fallback mechanism, **not** the live
visibility-selection gate. Representative Create Chronicles/RX 6900 XT diagnostics
must still show no unresolved GPU-vs-CPU mismatch, including the unusual
camera-outside-build-height seed path. No performance improvement is claimed.

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

**Progress: 0/8**

---

# Optional/stretch work after the core roadmap

These are valuable but must not silently displace the active roadmap:

- broader Oculus/Iris-style shaderpack interoperability research;
- multithreaded Vulkan command generation;
- dedicated asynchronous transfer/upload queues if profiling justifies them;
- advanced GPU occlusion structures beyond the Phase 7 minimum;
- additional platform/GPU compatibility beyond the user's primary AMD/Linux target;
- modern resource/texture compression experiments that require Minecraft asset-pipeline changes.

---

# Required roadmap report format

On request, at a substantial checkpoint, or before a chat handoff, report using this shape:

## Roadmap report

- **HEAD:** `<commit>`
- **Latest CI:** `#<run> — green/failing/in progress`
- **Highest completed legacy milestone:** `<AGENTS.md milestone>`
- **Active phase:** `Phase N — name`
- **Phase progress:** `X/Y mandatory gates (Z%)`
- **Active gate:** `PN.N — exact gate`
- **Completed since last report:** concise evidence-backed items
- **Current blocker/risk:** exact issue, or `none`
- **Next three actions:** ordered concrete actions
- **RX 6900 XT testing:** `not useful yet` / `useful now`, with reason and exact test if useful
- **Performance evidence:** latest comparable measurement, or `no new benchmark evidence`
- **Roadmap changes:** `none`, or list any user-approved/evidence-driven sequencing changes

Do not report a phase gate as complete merely because a patch was pushed; report it complete after the corresponding evidence is green/observed.

---

# Current roadmap snapshot

- Verified runtime source: `67dafa92c5a9f568616e10a54c504d9285f8f514`,
  **CI #444** (run `34927562475`) is the verification run for the fallback-contract
  commit and must be green before relying on this snapshot. Previous source CI #443
  is fully green at `d91de023` across build/distribution, both Vulkan startups,
  persistent GPU indirect shadow, post/depth, screenshot and compatibility smokes.
- Highest demonstrated milestone: 6, playable world.
- Phase 3 complete; Phase 4 parked 3/8; Phase 5 3/7; Phase 6 7/10; Phase 7 5/11.
- P7 input ABI, capped persistent region SSBO residency, compute plumbing, reusable
  Forge model instances, compact output capacity/overflow safety, and bounded GPU
  indirect-command generation with CPU fallback are verified. A live metadata
  producer and default-off production indirect consumer now exist. Live GPU selection
  correctness still needs representative RX 6900 XT/Create Chronicles evidence.
- Hybrid GPU meshing still requires real lighting-demand density evidence before the
  snapshot ABI is extended or any CPU geometry ownership is removed.
- No new RX 6900 XT A/B performance measurement or performance claim.

See `AGENT_STATUS.md` for current test instructions and
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md` for the bounded indirect-draw
contract. Documentation-only follow-ups use `[skip ci]`. Recheck live source/CI before
the next implementation step.
