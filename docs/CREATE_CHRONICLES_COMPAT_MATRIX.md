# Create Chronicles compatibility matrix

This is the concise Phase 4 compatibility/known-limitations matrix for the Forge 1.20.1 port. The detailed evidence ledger remains in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`; the current user retest procedure remains in `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`. Live Git/CI/runtime evidence wins if this summary becomes stale.

## Current evidence boundary

- Current executable candidate: CI build **#745** / `acb69d632a124b02d54f5ce6f42df84c33dc3d39`.
- Public CI #745 is fully green across the current distributable/Vulkan/compatibility smoke matrix.
- Latest RX 6900 XT hardware evidence is still **#743**: terrain/world and GUI chrome rendered, but Creative block/item imagery and the player/entity model were absent.
- #744 repairs auxiliary `MainTarget` ownership for Iceberg-style off-screen targets.
- #745 restores vanilla-style fixed Sampler0/1/2 reconciliation immediately before ordinary item/entity draws.
- #744/#745 are CI-covered but not yet RX-confirmed.
- Reload and world re-entry are explicitly deferred for the current Phase 4 pass.

## Matrix

| Component / path | Status | Evidence / limitation |
| --- | --- | --- |
| VulkanMod on Forge 47.3.0 | **Supported baseline** | Full Create Chronicles instance has launched with Vulkan active on RX 6900 XT/RADV; build #289 established the Phase 4 launch gate and later runs progressed further. |
| World/terrain rendering | **RX-confirmed on #743** | World blocks remained visible in the latest hardware run. Experimental GPU-terrain publication also remained active/fail-closed as designed. |
| Creative block/item imagery | **Pending #745 RX retest** | Missing on #743. #745 repairs fixed core sampler reconciliation on the ordinary item/entity draw path; CI proves the contract, not the visible hardware result. |
| Player / ordinary entity model rendering | **Pending #745 RX retest** | Missing on #743. Shares the ordinary preconverted draw path targeted by #745, but no RX confirmation exists yet. |
| Auxiliary/off-screen `MainTarget` | **CI-fixed; RX confirmation pending** | #743 exposed Iceberg 1.1.25 aliasing its 96x96 target to the swapchain and triggering self-sampling rejection. #744 gives auxiliary MainTargets normal independent Vulkan backing. |
| Create 0.5.1.j startup / stencil target | **Supported baseline** | Exact Create fixture is green; the full pack has progressed beyond the former `RenderTarget.enableStencil()` abort. In-world Create GUI appearance still needs representative visual exercise. |
| Flywheel 0.6.x startup | **Supported baseline** | Positive CI gate keeps its OpenGL backend off and preserves Create fallback rendering. Current moving-contraption appearance remains a user visual gate. |
| Create/Flywheel contraptions | **Pending current visual gate** | Historical build #226 rendered a spinning water wheel, but substantial renderer changes since then require a current-artifact check. |
| Distant Horizons 3.2.0-b | **Fail-closed by design** | Its OpenGL LOD draw/fade/lightmap paths remain suppressed under Vulkan. The Forge AFTER_LEVEL raw-GL framebuffer probe is guarded and #742/#743 progressed beyond that abort. DH LOD rendering is not claimed as supported. |
| Immersive Portals 3.0.7 | **Automated compatibility covered; portal visual pending** | CI covers framebuffer ownership, recursive buffers, shader aliases, clip planes and reload hook compatibility. A real portal view remains an RX visual gate. |
| PureBDcraft base + Create Chronicles packs | **RX-confirmed retention** | #728 retained both real packs through observed reloads; private storage CI also exercises the real 16K-atlas workload. Do not copy private pack bytes into this public repository. |
| FTB Library | **Supported baseline** | Dedicated compatibility coverage is green. |
| Pick Up Notifier | **Supported baseline** | Dedicated compatibility smoke is green; do not disable it because of superseded historical failures. |
| Crash Assistant 1.9.7 | **Supported baseline** | Positive CI startup gate is green. |
| Chat Heads | **Supported baseline** | Current compatibility fixture is green. |
| Representative particles / translucency / ordinary GUI | **Pending current visual gate** | CI covers important mechanics, but representative full-pack appearance has not yet closed the Phase 4 visual gate. |
| Resource reload (`F3+T`) | **Deferred / open** | Explicitly deferred for the current pass; historical evidence remains in the detailed ledger. |
| World exit / re-entry | **Deferred / open** | Explicitly deferred for the current pass. |
| Embeddium 0.3.31 | **Intentionally disabled; minimization gate open** | Known-good Vulkan baseline disables it. Embeddium replaces terrain rendering and optimizes immediate-mode entity/GUI paths, directly overlapping VulkanMod's renderer; individual 1.20.1 coexistence has not been validated and should not be assumed. |
| Oculus 1.8.0 | **Intentionally disabled; out of initial shaderpack scope** | Known-good baseline disables it. Full Iris/Oculus-style shaderpack support is not an initial Phase 4 blocker; no compatibility claim is made. |
| Rubidium Extra 0.5.4.4 | **Intentionally disabled pending dependency/minimization proof** | It is an Embeddium/Rubidium companion rather than an independent renderer target. Do not re-enable it in bulk with renderer replacements. |
| Oculus-Flywheel-Compat 2.0.3 | **Intentionally disabled with Oculus** | It has no useful baseline role while Oculus is absent; no independent incompatibility claim is made. |

## Current required configuration / safety boundaries

- Keep `config/fml.toml` -> `earlyWindowControl = false` so Forge's early OpenGL splash does not conflict with VulkanMod's `GLFW_NO_API` game window.
- Keep the established renderer-replacement baseline disabled for the current #745 retest; do not bulk-enable Embeddium/Oculus-family components while diagnosing VulkanMod visuals.
- Keep the four established experimental GPU-terrain flags together for the current hardware pass when reproducing the established baseline.
- Do not add the private-CI memory-reserve override to the user's machine and do not weaken production host-memory safety to make a test pass.
- Preserve CPU/fail-closed handling for unsupported Forge terrain callbacks/content and all documented GPU-terrain ownership/lifecycle boundaries.

## Phase 4 interpretation

This matrix closes the roadmap's documentation gate only: a concise, committed compatibility/known-limitations summary now exists. It does **not** close the separate gates for current Create/Flywheel visuals, representative particles/translucency/entities/GUI, renderer-replacement minimization, or the deferred reload/re-entry lifecycle.
