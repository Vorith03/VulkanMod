# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Repository state

- Current executable/test candidate remains `f86146bf12b21ad3612f67a601747e18db981ca3` (`test: guard IP framebuffer composite anchor`). The production portal-composite fix immediately underneath is `9833410f549f59910a9fdcd5f795bb94e3576902` (`fix: restore IP framebuffer matrices at draw boundary`).
- Public CI **#801** / run `36407726305` is fully green. Build/distributable, packaged Immersive Portals injection-anchor validation, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, exact Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-801` (artifact `10962799916`, SHA-256 `b806bf26218fb09d0ccf93d6b6aff009d9a3a8c30f9082d407b62da01dc7806f`).
- **Live Phase 4 evidence is now 7/8.** The Create/Flywheel ordinary-gameplay visual gate and the representative particles/translucency/entities/GUI gate are RX-confirmed on #801. The only remaining mandatory Phase 4 gate is world enter/leave/re-enter plus resource reload, which the user explicitly deferred.
- `ROADMAP.md` still contains the older 5/8 Phase 4 checkbox text. Treat that count as stale factual prose until reconciled; its gate order/definitions remain authoritative.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while Phase 4 is active. Phase 5 measurement work remains **4/7**; no performance win is claimed without comparable RX A/B evidence.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence.

## Current RX 6900 XT evidence — 2026-09-28, build #801

The user reports the current #801 candidate is visually correct for the compatibility paths that were previously open:

- Immersive Portals portal rendering works correctly. The remote world is visible through the portal, the composite remains attached/clipped to the portal, and portal traversal works in both directions. This closes the #799 black-portal/final-composite failure.
- Creative inventory block/item imagery renders correctly.
- Third-person player rendering and ordinary entity rendering work correctly.
- Create/Flywheel content, including ordinary contraption rendering, works correctly.
- Representative visual/UI paths that were still open are working, closing the particles/translucency/entities/GUI Phase 4 gate.
- Do not ask the user to repeat these checks unless a later executable change directly threatens them.

The same #801 session still exposed the separate shutdown/native-lifetime problem after normal Minecraft shutdown (`double free or corruption` / abort 134). Treat that as independent from the now-working portal/visual compatibility paths. Do not make speculative native-ownership changes without a focused reproduction/backtrace or other ownership evidence.

## Immersive Portals 3.0.7 compatibility boundary

### Vanilla terrain dispatcher ownership

- `c299a49d` cancels IP's obsolete `MyRenderHelper.earlyRemoteUpload()` vanilla chunk-upload prepass; RX evidence confirms that failure is gone.
- `97b50739` structurally removes only the stale merged IP terrain-camera `ChunkRenderDispatcher.setCamera(Vec3)` call while leaving the unrelated direct dispatcher call intact.
- The exploratory query-callback skip remains reverted. `ImmersivePortalsQueryManagerMixin` bypasses unsupported OpenGL query-result handling but still executes the supplied geometry callback before conservatively reporting visible.

### Framebuffer renderer, depth clamp, and portal matrices

Exact Forge IP 3.0.7 compatibility mode renders a remote world into a depth-enabled secondary `TextureTarget`, restores the original target, enables depth clamp, and composites the sampled secondary color image over the portal polygon.

VulkanMod's generic `RenderTarget` path supplies sampled Vulkan color/depth images, synthetic texture IDs, render-target switching, shader-read transitions, and named sampler resolution. #801 RX evidence now confirms that this generic path is sufficient for real portal rendering; do not duplicate it with a portal-specific framebuffer implementation absent new contrary evidence.

Retained repair boundaries:

- `632136e6` enables Vulkan's optional `depthClamp` feature when supported, tracks the IP compatibility request, includes depth clamp in the graphics-pipeline cache key, and sets `VkPipelineRasterizationStateCreateInfo.depthClampEnable(...)` accordingly.
- `d41ce91d` adds the narrow compatibility bridge that restores explicit IP `ModelViewMat`/`ProjMat` values without weakening generic legacy shader behavior.
- `934d4e57` restores matrices at the stable post-`apply()`, pre-draw boundary for the visibility/depth pass.
- `9833410f` restores matrices at the same semantic boundary for the final sampled framebuffer composite.
- `f86146bf` guards the packaged distributable so both matrix mixins retain the stable IP-owned portal-buffer anchor and the unsafe/too-early anchors cannot regress silently.
- **#801 RX confirmation closes the portal visual-composition issue.**

### Custom shader reload ownership

VulkanMod replaces `GameRenderer.reloadShaders()`, while IP stores custom `ShaderInstance`s in static fields. The retained repair appends IP's registered custom shaders to Vulkan-managed reload sets once IP is ready, preventing static references from pointing at closed shader instances. Actual resource reload remains part of the one deferred Phase 4 lifecycle gate.

## Phase 4 remaining gate

Only this mandatory Phase 4 gate remains open:

- world enter/leave/re-enter and resource reload paths survive in the full Create Chronicles modpack.

The user explicitly deferred reload and re-entry for the current pass. Do not reinterpret the completed visual gates as needing another retest before moving on. When Phase 4 lifecycle testing resumes, keep it narrow: exercise a controlled resource reload and world leave/re-entry sequence and distinguish any renderer failure from the independent shutdown/native-lifetime abort.

Focused evidence: `docs/CREATE_CHRONICLES_COMPATIBILITY.md`, `docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`, `docs/CREATE_CHRONICLES_RENDERER_REPLACEMENTS_2026-09-26.md`, and `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`.

## Real resource-pack gate

The two user-supplied PureBDcraft ZIPs live only in private `Vorith03/storage` release assets. Do not copy their bytes or private-payload logs into this public repository.

Storage CI verifies immutable hashes and the real two-pack atlas workload. Public VulkanMod CI private-pack steps are normally skipped because the variable/secret pair is absent. The CI-only `-Dvulkanmod.systemAvailableReserveMinMiB=768` override is test infrastructure and must not be carried onto the user's heavy-pack machine.

## Distant Horizons boundary

Distant Horizons 3.2.0-b OpenGL LOD draw/fade, DH lightmap upload, and the Forge AFTER_LEVEL framebuffer probe remain deliberately suppressed under Vulkan. DH data/maintenance is retained. Do not describe native DH LOD rendering itself as supported.

## GPU-terrain flags / safety

Whole-section accelerated bypass requires:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Mixed-section APPEND additionally requires:

```text
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Keep accelerated consumption default-off until representative RX correctness and comparable frame-time evidence are complete. Do not weaken production memory safety or ownership rules merely to make tests pass.
