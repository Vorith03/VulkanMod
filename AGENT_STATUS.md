# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Repository state

- Current executable/test candidate is `f86146bf12b21ad3612f67a601747e18db981ca3` (`test: guard IP framebuffer composite anchor`). The production fix immediately underneath is `9833410f549f59910a9fdcd5f795bb94e3576902` (`fix: restore IP framebuffer matrices at draw boundary`).
- Public CI **#801** / run `36407726305` is fully green. Build/distributable, packaged Immersive Portals injection-anchor validation, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, exact Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-801` (artifact `10962799916`, SHA-256 `b806bf26218fb09d0ccf93d6b6aff009d9a3a8c30f9082d407b62da01dc7806f`).
- Phase 4 remains **5/8** and is the active priority. Open mandatory gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and world enter/leave/re-enter + resource reload. Reload and re-entry remain explicitly deferred for the current pass.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while representative full-pack visual correctness is repaired. Phase 5 measurement work remains **4/7**; no performance win is claimed without comparable RX A/B evidence.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence.
- `ROADMAP.md` Phase 4 gate count/order remains authoritative; this checkpoint supersedes stale prose-only build references without changing roadmap sequencing.

## Current RX 6900 XT evidence — 2026-09-28

Latest user-machine visual evidence is build **#799** with both real PureBDcraft packs and the established four experimental terrain flags.

- The previous nested-Nether vanilla `ChunkRenderDispatcher` crashes remain closed. Earlier #791/#796 runs established that the overworld loads, IP selects `RendererUsingFrameBuffer`, the secondary framebuffer is created, and the Nether client world can be created.
- #791 rendered real remote-world portal content and allowed portal traversal, but the visible remote framebuffer composite was spatially detached from the portal opening.
- #796 did not reach visual evaluation because its new `ViewAreaRenderer` matrix injector used a development-only Mojmap `ShaderInstance.apply()` anchor with `remap=false`; the packaged Forge runtime matched 0/1 injection points and crashed. That packaging defect was repaired and guarded in #799.
- #799 no longer shows the detached remote slab. Instead, the portal opening is a correctly positioned **solid black portal polygon**. This is strong evidence that the visibility/depth portal-area pass is now spatially correct while the final sampled framebuffer composite is still not overwriting it.
- In exact IP 3.0.7 the visibility query callback calls `ViewAreaRenderer.renderPortalArea(..., Vec3.ZERO, ..., true, true, true, true)`, so that first pass deliberately writes a black portal polygon and depth. The final `drawPortalAreaWithFramebuffer(...)` pass should then draw the sampled secondary framebuffer over the same geometry.
- The #799 black-portal result therefore narrows the failure to the final framebuffer composite transform/submission, not remote-world creation, secondary framebuffer allocation, or the old terrain dispatcher path.
- Creative inventory imagery, current third-person player/entity rendering, main-menu lighting quality, and Create/Flywheel visual correctness remain open.

## Immersive Portals 3.0.7 compatibility boundary

### Vanilla terrain dispatcher ownership

- `c299a49d` cancels IP's obsolete `MyRenderHelper.earlyRemoteUpload()` vanilla chunk-upload prepass; RX evidence confirms that failure is gone.
- `97b50739` structurally removes only the stale merged IP terrain-camera `ChunkRenderDispatcher.setCamera(Vec3)` call while leaving the unrelated direct dispatcher call intact.
- The exploratory query-callback skip remains reverted. `ImmersivePortalsQueryManagerMixin` bypasses unsupported OpenGL query-result handling but still executes the supplied geometry callback before conservatively reporting visible.

### Framebuffer renderer and depth clamp

Exact Forge IP 3.0.7 compatibility mode renders a remote world into a depth-enabled secondary `TextureTarget`, restores the original target, enables depth clamp, and composites the sampled secondary color image over the portal polygon.

VulkanMod's generic `RenderTarget` path supplies sampled Vulkan color/depth images, synthetic texture IDs, render-target switching, shader-read transitions, and named sampler resolution. #791 RX evidence demonstrated that real remote-world content can make it through this path; do not duplicate it with a portal-specific framebuffer implementation absent new contrary evidence.

Retained depth-clamp repair:

- `632136e6` enables Vulkan's optional `depthClamp` feature when supported, tracks the IP compatibility request, includes depth clamp in the graphics-pipeline cache key, and sets `VkPipelineRasterizationStateCreateInfo.depthClampEnable(...)` accordingly;
- IP's own `enableClippingMechanism` guard remains authoritative;
- unsupported devices never request the unavailable feature;
- `795a4815` strengthened CI by directly creating/binding IP's clamped portal pipeline under Vulkan validation.

### Portal-area matrix ownership

Exact IP writes explicit portal matrices before both portal-area draws:

1. `ViewAreaRenderer.renderPortalArea(...)` — visibility/depth pass;
2. `MyRenderHelper.drawPortalAreaWithFramebuffer(...)` — final sampled framebuffer composite.

VulkanMod's converted-legacy `ShaderInstance.apply()` mirrors global `RenderSystem` matrices for ordinary legacy shaders, so IP's explicit matrices must be restored **after `apply()` and before BufferUploader submits the portal mesh**.

Retained/current repair:

- `d41ce91d` adds the narrow compatibility bridge that restores explicit IP `ModelViewMat`/`ProjMat` values without weakening generic legacy shader behavior.
- `934d4e57` fixes the visibility/depth pass by restoring immediately before IP's own `ViewAreaRenderer.buildPortalViewAreaTrianglesBuffer(...)` call. This is after `shader.apply()` and is stable in packaged Forge because it does not depend on a remapped Minecraft method name.
- #799 RX evidence strongly confirms this first pass is now spatially correct: the black prepass is attached to the actual portal opening.
- The earlier final-composite injection (`2a7c2eef`) was incorrectly anchored **after IP's `_viewport(...)` call**. Exact IP source shows `_viewport(...)` occurs before the framebuffer shader receives its explicit matrices and before `shader.apply()`. The compatibility bridge therefore ran too early and its restored matrices were immediately overwritten again by `apply()`.
- `9833410f` moves the final framebuffer-composite restore to immediately **before** IP's own `ViewAreaRenderer.buildPortalViewAreaTrianglesBuffer(...)` call. This is the real post-`apply()`, pre-draw boundary and uses the same production-stable IP-owned anchor as the visibility pass.
- `f86146bf` strengthens `scripts/ci/immersive-portals-package-contract.py`: the actual distributable JAR must contain the stable IP-owned portal-buffer anchor in **both** matrix mixins; the view-area mixin must not contain the production-unsafe Mojmap `ShaderInstance.apply()` anchor; the framebuffer mixin must not contain the too-early `_viewport(...)` anchor.
- The exact #799 distributable was independently inspected and contains `_viewport` but not the stable final-composite buffer-build anchor, so the strengthened contract correctly identifies the bug exposed by the #799 screenshot.
- **CI #801 is fully green** with the corrected final-composite anchor and the strengthened packaged-JAR contract, while exact published IP 3.0.7 and the downstream compatibility matrix remain green.

RX confirmation is still required before declaring portal visual composition correct.

### Custom shader reload ownership

VulkanMod replaces `GameRenderer.reloadShaders()`, while IP stores custom `ShaderInstance`s in static fields. The retained repair appends IP's registered custom shaders to Vulkan-managed reload sets once IP is ready, preventing static references from pointing at closed shader instances. Resource reload itself remains deferred for this RX pass.

## Next RX gate

Use build **#801** / `f86146bf12b21ad3612f67a601747e18db981ca3` with both real PureBDcraft packs and the established four experimental terrain flags. Do not add the private-CI memory-reserve override. Do not enable tracing/profiling for the normal first pass.

Keep the test narrow and reuse the same portal:

1. Look at the portal from approximately the same position as the #799 black-portal screenshot.
2. The opening should now contain the remote world rather than the black prepass, and the image should remain rigidly attached/clipped to the portal while moving left/right and closer/farther.
3. Cross once and inspect the reverse side. This is a visual-composition check, not a reload/re-entry stress test.
4. If the portal is correct, open Creative and check ordinary block/item imagery, then check the player in third person and one nearby entity.
5. Only after those pass, continue with a moving Create/Flywheel contraption, Create GUI/overlay, and representative particles/liquids/translucency/entities.
6. If the portal is still black or visually wrong, retain a screenshot plus the launcher/latest log from #801. Do not repeat older #791/#796 crash tests.

Reload and world leave/re-enter remain explicitly deferred.

## Create Chronicles open evidence

Still requiring current user-machine evidence:

- **first:** #801 actual portal framebuffer composite on RX 6900 XT;
- Creative block/item imagery and player/entity rendering on the current candidate;
- main-menu player lighting quality if convenient on a fresh launch;
- Iceberg/Advancement Plaques auxiliary item rendering when naturally encountered;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- representative particles/translucency/entities;
- dirty mixed-section hybrid terrain rebuild, resource reload, and world re-entry remain open roadmap gates but are deferred for the current pass.

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

Keep accelerated consumption default-off until representative RX correctness and comparable frame-time evidence are complete. Do not weaken production memory safety or ownership rules merely to make tests pass. A historical shutdown/native-lifetime `double free or corruption (!prev)` signal remains unresolved; do not make speculative ownership changes without a current reproduction/native backtrace.
