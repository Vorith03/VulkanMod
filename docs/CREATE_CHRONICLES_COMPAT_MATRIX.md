# Create Chronicles compatibility matrix

This is the concise Phase 4 compatibility/known-limitations matrix for the Forge 1.20.1 port. The detailed evidence ledger remains in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`; the current user retest procedure remains in `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`. Live Git/CI/runtime evidence wins if this summary becomes stale.

## Current evidence boundary

- Current executable candidate: CI build **#801** / `f86146bf12b21ad3612f67a601747e18db981ca3`.
- Public CI #801 is fully green across the current distributable/Vulkan/compatibility smoke matrix.
- RX 6900 XT hardware evidence on **2026-09-28** confirms the previously open visual compatibility paths on #801: Immersive Portals portal composition/traversal, Creative block/item imagery, player/ordinary entity rendering, Create/Flywheel ordinary gameplay rendering, and representative visual/UI paths all work.
- Reload and world re-entry remain explicitly deferred for the current Phase 4 pass.
- The same #801 session still reproduces a separate shutdown/native-lifetime abort after normal Minecraft shutdown; that is not evidence of a portal or ordinary gameplay rendering failure.

## Matrix

| Component / path | Status | Evidence / limitation |
| --- | --- | --- |
| VulkanMod on Forge 47.3.0 | **Supported baseline** | Full Create Chronicles instance runs with Vulkan active on RX 6900 XT/RADV; build #289 established the launch gate and #801 closes the current visual compatibility retest. |
| World/terrain rendering | **RX-confirmed on #801** | Ordinary world terrain renders correctly in the current full-pack hardware run. Experimental GPU-terrain publication remains separately gated/fail-closed as designed. |
| Creative block/item imagery | **RX-confirmed on #801** | The prior #743 missing-item regression is closed on current hardware. |
| Player / ordinary entity model rendering | **RX-confirmed on #801** | Third-person player and ordinary entity rendering are working on current hardware. |
| Auxiliary/off-screen `MainTarget` | **CI-fixed; opportunistic RX coverage only** | #744 gives auxiliary MainTargets independent Vulkan backing after the earlier Iceberg self-sampling failure. No new dedicated Iceberg-only retest is required unless that path regresses naturally. |
| Create 0.5.1.j startup / stencil target | **Supported baseline** | Exact Create fixture is green; the full pack progresses through startup and current Create UI/gameplay visuals work. |
| Flywheel 0.6.x startup | **Supported baseline** | Positive CI gate keeps its OpenGL backend off and preserves Create fallback rendering. Current ordinary gameplay appearance is RX-confirmed. |
| Create/Flywheel contraptions | **RX-confirmed on #801** | Current moving/ordinary Create-Flywheel rendering is reported working, superseding the stale historical-only visual evidence. |
| Distant Horizons 3.2.0-b | **Fail-closed by design** | Its OpenGL LOD draw/fade/lightmap paths remain suppressed under Vulkan. The Forge AFTER_LEVEL raw-GL framebuffer probe is guarded. DH LOD rendering is not claimed as supported. |
| Immersive Portals 3.0.7 | **RX-confirmed on #801** | Real remote-world portal content renders correctly, remains attached/clipped to the portal, and traversal works in both directions. The prior detached/black portal-composite failures are closed. |
| PureBDcraft base + Create Chronicles packs | **RX-confirmed retention** | Real packs have remained selected through the established workload; private storage CI also exercises the real 16K-atlas workload. Do not copy private pack bytes into this public repository. |
| FTB Library | **Supported baseline** | Dedicated compatibility coverage is green. |
| Pick Up Notifier | **Supported baseline** | Dedicated compatibility smoke is green; do not disable it because of superseded historical failures. |
| Crash Assistant 1.9.7 | **Supported baseline** | Positive CI startup gate is green. |
| Chat Heads | **Supported baseline** | Current compatibility fixture is green. |
| Representative particles / translucency / entities / ordinary GUI | **RX-confirmed on #801** | The user reports the remaining representative visual/UI paths are working, closing this Phase 4 visual gate. |
| Resource reload (`F3+T`) | **Deferred / open** | Explicitly deferred for the current pass; this remains part of the only open mandatory Phase 4 lifecycle gate. |
| World exit / re-entry | **Deferred / open** | Explicitly deferred for the current pass; this remains part of the only open mandatory Phase 4 lifecycle gate. |
| Embeddium 0.3.31 | **Required disabled baseline** | Exact 1.20.1/Forge 47.3.0 source uses an OpenGL `GLRenderDevice` and raw LWJGL OpenGL calls. VulkanMod intentionally owns a `GLFW_NO_API` window, so coexistence would require a new compatibility architecture rather than a configuration tweak. |
| Oculus 1.8.0 | **Required disabled baseline** | Exact 1.20.1 metadata requires Embeddium. Full Iris/Oculus-style shaderpack support is outside the initial Phase 4 blocker scope. |
| Rubidium Extra 0.5.4.4 | **Required disabled with Embeddium** | Exact 1.20.1 metadata requires Embeddium; it has no independent role after the OpenGL renderer backend is removed. |
| Oculus-Flywheel-Compat 2.0.3 | **Required disabled with Oculus** | Exact Forge 1.20.1 metadata requires Oculus. Flywheel itself remains enabled and separately covered. |

Detailed renderer-stack evidence: `docs/CREATE_CHRONICLES_RENDERER_REPLACEMENTS_2026-09-26.md`.

## Current required configuration / safety boundaries

- Keep `config/fml.toml` -> `earlyWindowControl = false` so Forge's early OpenGL splash does not conflict with VulkanMod's `GLFW_NO_API` game window.
- Keep Embeddium, Oculus, Rubidium Extra, and Oculus-Flywheel-Compat disabled for the current Vulkan baseline. They form one OpenGL renderer/dependency stack; do not disable Flywheel itself.
- Keep the four established experimental GPU-terrain flags together for the current hardware pass when reproducing the established baseline.
- Do not add the private-CI memory-reserve override to the user's machine and do not weaken production host-memory safety to make a test pass.
- Preserve CPU/fail-closed handling for unsupported Forge terrain callbacks/content and all documented GPU-terrain ownership/lifecycle boundaries.

## Phase 4 interpretation

Live evidence now closes the current Create/Flywheel visual gate and the representative particles/translucency/entities/GUI gate in addition to the previously completed launch/configuration/matrix gates. **Phase 4 is therefore 7/8 by current evidence.** The only remaining mandatory gate is the explicitly deferred world enter/leave/re-enter plus resource reload lifecycle check.
