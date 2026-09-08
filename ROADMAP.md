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

**Status: ACTIVE**

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
- [ ] P3.9 — perform one RX 6900 XT visual post-effect check after P3.8 is green;
- [ ] P3.10 — replace the unsafe post-present screenshot/readback path with a same-frame Vulkan transfer path, or explicitly gate it until safe;
- [x] P3.11 — characterize and fix MainTarget depth-aux sampling / sampler-filter semantics for vanilla transparency (nearest/clamp depth sampling, valid copies/barriers; CI #282). Broader mod-specific filtering remains unproven.

**Progress: 9/11**

**Next work item: P3.9.** The bounded depth CI task is complete; see `AGENT_STATUS.md` for the root cause and evidence. P3.10 remains open.

### Phase 3 exit rule

Do not begin a major terrain renderer rewrite while a known command-buffer/layout/readback correctness defect remains uncharacterized. Small preparatory performance research is fine; invasive implementation waits for this phase to close.

---

# Phase 4 — Create Chronicles compatibility baseline

**Status: READY AFTER PHASE 3**

Goal: prove the renderer works in the user's actual target environment and identify the minimum incompatible renderer-replacement set.

Mandatory gates:

- [x] Flywheel 0.6 has a positive CI startup gate;
- [x] Crash Assistant 1.9.7 has a positive CI startup gate;
- [ ] current distributable launches the target Create Chronicles instance with Vulkan active;
- [ ] Create/Flywheel contraptions render correctly in ordinary gameplay;
- [ ] world enter/leave/re-enter and resource reload paths survive in the modpack;
- [ ] representative particles/translucency/entities/GUI paths are checked for visible regressions;
- [ ] incompatible renderer replacements (for example Embeddium/Rubidium/Oculus if applicable) are evidence-backed and minimized;
- [ ] a concise compatibility/known-limitations matrix is committed.

**Progress: 2/8**

### Shaderpack scope

Vanilla Minecraft post effects are core correctness and belong in Phase 3. Full Iris/Oculus-style shaderpack compatibility is **not** an initial release blocker unless the user explicitly promotes it; renderer replacements may be fundamentally incompatible and should be characterized rather than forced together.

---

# Phase 5 — Performance baseline and measurement discipline

**Status: WAITING ON PHASE 4 BASELINE**

Goal: create apples-to-apples measurements so optimization claims have evidence.

Mandatory gates:

- [ ] define a fixed test world/route and graphics settings for repeatable measurements;
- [ ] record an OpenGL comparison baseline on the same machine/settings/modpack;
- [ ] record the Vulkan baseline on the same machine/settings/modpack;
- [ ] record frame-time behavior in addition to average FPS (at minimum low-percentile or hitch-sensitive evidence);
- [ ] define a terrain traversal/chunk-visibility stress case;
- [ ] define a Create-heavy/modded rendering stress case;
- [ ] commit the benchmark procedure and acceptance rule: no performance claim without comparable before/after evidence.

**Progress: 0/7**

### Default comparison target

The comparison should answer practical user questions, not win a synthetic benchmark. Use the user's actual 2560×1440 gameplay configuration unless the benchmark procedure explicitly calls for an additional resolution.

---

# Phase 6 — Terrain renderer v1: persistent region batching

**Status: FOUNDATION PRESENT; MAJOR IMPLEMENTATION WAITS ON PHASE 5**

Goal: outperform the traditional Minecraft submission model using a stable Vulkan terrain backend before adding mesh shaders.

Mandatory gates:

- [x] region batch-layout regression coverage exists;
- [x] basic terrain region-cache/batching smoke coverage exists and batching can be enabled;
- [ ] complete RenderRegionCache lifecycle audit for rebuild/unload/world transitions with no stale GPU ownership;
- [ ] move terrain geometry toward persistent region-scoped GPU allocations;
- [ ] implement stable suballocation/reuse rather than churn-heavy per-rebuild allocation where practical;
- [ ] reduce CPU draw submission count with region/layer batching;
- [ ] add indirect/multi-draw-style submission where profiling shows it is beneficial;
- [ ] preserve a known-good fallback path while the new backend matures;
- [ ] demonstrate visual correctness under high chunk churn / camera movement;
- [ ] measure a reproducible improvement (or reject/rework the design if it does not improve the Phase 5 baseline).

**Progress: 2/10**

### Design target

This phase should make terrain data **persistent, compact, and batch-friendly**. It is intentionally useful even on hardware/drivers without mesh-shader support and becomes the data/residency foundation for Phase 7.

---

# Phase 7 — GPU-driven / mesh-shader terrain backend

**Status: PLANNED**

Goal: pursue the Nvidium-like endgame using Vulkan's cross-vendor GPU-driven capabilities, with `VK_EXT_mesh_shader` as an optional accelerated backend rather than a hard dependency.

Mandatory gates:

- [ ] detect `VK_EXT_mesh_shader` support and required feature/property limits at runtime;
- [ ] enable mesh-shader device features only when supported, without breaking the classic Vulkan fallback;
- [ ] define a terrain meshlet/cluster representation compatible with Phase 6 residency;
- [ ] render an opaque-terrain prototype using `vkCmdDrawMeshTasksEXT` (or the appropriate EXT path);
- [ ] move frustum/cluster rejection to GPU-driven work where profiling supports it;
- [ ] evaluate occlusion/hierarchical culling and keep it only if it produces measurable wins;
- [ ] integrate chunk rebuild/unload/residency updates without full-buffer rebuilds;
- [ ] define a safe strategy for cutout and translucent terrain (which may remain on a different path initially);
- [ ] retain and continuously test a non-mesh-shader Vulkan backend;
- [ ] benchmark the mesh backend on the RX 6900 XT against both Phase 6 Vulkan and the Phase 5 OpenGL baseline; enable by default only if it is faster and stable.

**Progress: 0/10**

### Architectural rule

Do **not** jump directly to mesh shaders before Phase 6 provides stable terrain residency and Phase 5 provides a benchmark. Otherwise the project would be debugging a new geometry pipeline and a new memory/lifecycle model simultaneously.

The goal is Nvidium-inspired architecture, not an NVIDIA/OpenGL-specific transplant.

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

Verified checkpoint after the bounded depth-post-processing fix:

- highest demonstrated legacy milestone: **Milestone 6 — playable world**;
- last verified green code checkpoint: **`f9b2b3916da4b719a993b5e721eaf29ad5c22bdc`, CI #282**;
- active phase: **Phase 3 — Core rendering correctness hardening**;
- next gate: **P3.9 — RX 6900 XT visual post-effect check**;
- RX 6900 XT visual post-effect testing is **useful now**; CI depth execution and validation passed. No new visual or performance result is claimed.

Future agents must verify live HEAD/CI first rather than assuming this snapshot is still current.
