# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state

- Current executable code/test HEAD is `b749a297c820e2734eda1229635e338ef858efbb` (`compat: skip Immersive Portals vanilla terrain camera update`). The branch may have documentation-only descendants; executable state is anchored to this commit.
- Public CI **#785** / run `36344612320` is fully green at `b749a297`. Build/distributable, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Phase 4 remains **5/8** and is the active priority. Open gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and reload/re-entry. Reload and world re-entry remain explicitly deferred for this pass.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while representative full-pack visual correctness is repaired.
- Phase 5 procedure-definition work remains **4/7**. `docs/TERRAIN_PERFORMANCE_BASELINE.md` fixes seed `2026092601`, a 2560x1440 profile, stationary camera, and a 1024-block eastbound spectator route. Numeric OpenGL/Vulkan/frame-time baselines remain open and no performance win is claimed.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.

## Post-#284 performance regression cleanup — 2026-09-27

A hostile/static review compared public CI #284 (`ef0c0fc...`) with current code and removed objectively unnecessary default-path work without rolling back compatibility repairs. Durable detail: `docs/PERFORMANCE_REGRESSION_CLEANUP_2026-09-27.md`.

Validated in public CI #784 and retained through #785:

- disabled GPU-terrain draw handoff now takes the direct CPU `RegionDrawBatch` path instead of querying synchronized GPU state and constructing handoff-plan records;
- terrain per-copy `System.nanoTime()` profiling is opt-in via `-Dvulkanmod.profileTerrainUploadCopies=true`, and disabled GPU-mesher completion polling is skipped;
- bounded `NEW_ENTITY` tracing is opt-in via `-Dvulkanmod.traceNewEntityDraws=true`; normal gameplay does not allocate its trace-state set or execute the probe;
- fixed Sampler0/1/2 reconciliation is cached using authoritative texture ids plus a selector-mutation version, preserving temporary-bind/image-replacement invalidation while avoiding repeated GL-id-to-Vulkan-image lookups on unchanged draws;
- packaged Vulkan core shaders skip redundant `ShaderInstance.apply()/clear()` calls, while converted Forge/Immersive-Portals shaders retain the full lifecycle and state publication;
- converted legacy/effect sampler preparation no longer stream-builds a temporary `VulkanImage[]` every manual draw; the existing pipeline-aware attachment helper allocates only when a transition is actually needed;
- the converted-shader lifecycle/fixed-sampler bytecode contracts are now executed by normal Gradle `check`.

Do **not** turn this static cleanup into a numerical performance claim. Comparable RX 6900 XT A/B data is still required. Ordinary framebuffer-attachment scanning, Forge-safe per-vertex wrapper capability checks, graphics-queue terrain upload ordering, and Forge render-stage callbacks remain intentionally unchanged until measurement or a stronger safe invalidation design justifies further work.

## Current RX 6900 XT visual blocker — 2026-09-27

The user has prioritized missing Creative block/item imagery and player/entity rendering. The latest user-machine evidence is build **#784** (`d8a3f390...`) with both real PureBDcraft packs and all four GPU-terrain flags.

Hardware progression and current diagnosis:

- #742 reached the world and Creative menu: world terrain and GUI chrome/text/tooltips rendered, but Creative block/item imagery and rendered player/entity models appeared absent.
- #743 prepared framebuffer-attachment samplers before ordinary draws; the broad defect remained. Iceberg/Advancement Plaques also exposed an off-screen `MainTarget` ownership bug.
- #744 fixed auxiliary `MainTarget` ownership: only the primary window target is swapchain-backed; later targets receive normal Vulkan off-screen backing.
- #745 restored vanilla-style fixed Sampler0/1/2 reconciliation immediately before ordinary draws. The user's RX test showed **no visual change**, so sampler reconciliation is a valid contract repair but hardware-disproven as the broad root cause by itself. Current code preserves that repair but caches unchanged reconciliations through selector-mutation invalidation.
- #746 added bounded `NEW_ENTITY` diagnostics for shader/pipeline, projection hash, depth/cull/color state, descriptor-facing Sampler0/1/2 images, buffer size, and a representative vertex. Current code retains the probe only behind `-Dvulkanmod.traceNewEntityDraws=true`; do not enable it for normal gameplay/performance runs.
- #747 restored the `ShaderInstance.apply() -> draw -> clear()` lifecycle needed by converted legacy/mod shaders; later tests guard ordering and exceptional cleanup. Current packaged Vulkan core draws skip that redundant lifecycle, while converted Forge/Immersive-Portals draws retain it unchanged.
- The #752 run became visually inconclusive in-world because Immersive Portals created a remote Nether client world and then crashed in `MyRenderHelper.earlyRemoteUpload()` before a useful Creative/player inspection could be completed. The diagnostics nevertheless proved ordinary `NEW_ENTITY` draws reached Vulkan with complete vertex buffers and real Sampler0 images, including the real 16384x16384 GUI atlas.
- The user also reported that the main-menu player model was visible but **pitch black**. That materially narrowed the fault: geometry/projection/base texture submission could survive, while shader lighting/state remained suspect.
- `0fc73d436219b08525b8bdf88feae19be5dee1c1` restored the complete legacy `ShaderInstance.apply()` state family retained by upstream 1.20.x VulkanMod, including inverse view rotation, glint alpha, fog values, texture matrix, game time, screen size, and line width.
- `b586d06689952f43b4f47f4ff0904679ca6c808b` restored `RenderSystem.setupShaderLights(shader)`. Minecraft's item/entity shaders initialize `LIGHT0_DIRECTION`/`LIGHT1_DIRECTION` to zero and consume them for diffuse lighting, providing a concrete mechanism for a present-but-black model. The compiled lifecycle/state contract guards this path.
- `b9f00692ce4c46da83ce8f93b11c2ffaf0a660f2` and `8a4f915a61fc61a69acef32993ba96840e1877f1` repaired fixed sampler bookkeeping for the lightmap and overlay layers: their Vulkan images were already installed, but the overwritten paths had left Minecraft's authoritative RenderSystem Sampler2/Sampler1 slots unset. Because `ShaderTextureState.syncFixedSamplers()` reconciles from those slots immediately before ordinary draws, it could overwrite valid Vulkan lightmap/overlay descriptors with fallback images. `FixedSamplerBookkeepingContractTest` guards both calls.
- #784 is the first RX confirmation after those retained state/light fixes: the user reports that the main-menu player is visible. This confirms the model is no longer wholly absent, but the user did **not** explicitly state whether lighting/colors are now normal, so the previous pitch-black symptom is not yet considered closed.
- #784 also reached materially farther into world startup. Vulkan activated on the RX 6900 XT/RADV stack, the integrated server had an overworld player, and the old `earlyRemoteUpload()` NPE did not recur in the supplied log. Instead, Immersive Portals reached a later nested Nether render and crashed in its merged `onSetupTerrainBegin` callback while calling `ChunkRenderDispatcher.setCamera(Vec3)` on VulkanMod's intentionally absent vanilla dispatcher.
- `b749a297` repairs that newly exposed boundary by suppressing only the exact vanilla dispatcher camera update after Immersive Portals has merged its LevelRenderer handler. The existing Vulkan terrain-setup override remains in force; other IP render hooks are untouched. `ImmersivePortalsRemoteUploadContractTest` now guards both the old early-upload cancellation and the new dispatcher-camera redirect. Public CI #785, including the actual Immersive Portals 3.0.7 smoke, is green.
- An exploratory Immersive Portals change that skipped the query callback geometry was reverted. Current `ImmersivePortalsQueryManagerMixin` still bypasses the unsupported OpenGL query result itself but **runs the supplied rendering callback** before returning the conservative visible/sample result. Do not resurrect the skipped-callback variant without new evidence.
- No `VUID-` or `VK_ERROR` signal was found in the supplied #784 debug log.

Do **not** claim the Creative/entity visual blocker closed until the RX machine reaches the world on the current build and confirms Creative/player/entity rendering. The strongest current mechanisms remain the restored shader light/state path plus preserved lightmap/overlay sampler bookkeeping; #745 already showed that plain fixed-sampler reconciliation alone was insufficient.

## Immersive Portals blockers — current state

### Vanilla terrain dispatcher paths

The #752 crash was `MyRenderHelper.earlyRemoteUpload()` dereferencing a null vanilla `ChunkRenderDispatcher` from a portal-world `LevelRenderer`. `c299a49d` cancels that obsolete vanilla upload prepass at method HEAD. The #784 RX run progressed past that failure, so this specific early-upload crash path is hardware-confirmed cleared.

The #784 run then exposed a second vanilla-dispatcher dependency: IP's nested remote-world `onSetupTerrainBegin` handler calls `ChunkRenderDispatcher.setCamera(Vec3)` before asking whether it may override terrain setup. VulkanMod already forces IP's terrain-setup override helper false and owns terrain camera/setup through `WorldRenderer`, so the vanilla camera update is both unnecessary and unsafe. `b749a297` redirects only that exact merged call to a no-op. Public CI #785 and the actual IP 3.0.7 smoke are green; RX confirmation is the next gate.

### Custom shader reload ownership

A later static review found a separate concrete lifecycle defect: VulkanMod replaces `GameRenderer.reloadShaders()`, while Immersive Portals stores custom `ShaderInstance`s such as `portalAreaShader` in static fields. The previous startup bridge emitted those custom shaders only during IP initialization. A later Vulkan shader reload could close the old shader map while IP's static fields still referenced those closed instances, leaving null/closed Vulkan pipelines.

- `078d4c4c348bbee7d3e7db73092f82c51f4cd2d2` adds `appendPortalShadersIfReady(...)` and changes the IP startup rebuild to use the single Vulkan-managed reload path instead of emitting a second untracked shader set.
- `19493e9b039af4b3c09c74d19b6888463341d0b7` appends IP's registered custom shaders to **every** Vulkan `GameRenderer.reloadShaders()` replacement set once IP is ready.
- `bcdfd5e8b0e3fc2dc19e314237817c3e7b333db7` adds a compiled-bytecode ownership contract; `af70db75` wires it into the existing compatibility contract run.
- Public CI #785 is fully green, including the actual Immersive Portals 3.0.7 smoke and the converted-shader lifecycle/fixed-sampler contracts.

Treat this lifecycle repair as CI-validated but not yet RX-confirmed. It is the current implementation boundary; do not add another speculative IP shader path before hardware evidence.

## Next RX gate

Use build **#785** / `b749a297c820e2734eda1229635e338ef858efbb` with both real PureBDcraft packs and the established four experimental terrain flags. Do not add the private-CI memory-reserve override. Do not enable `vulkanmod.traceNewEntityDraws` or `vulkanmod.profileTerrainUploadCopies` for the normal first pass.

Keep the first pass narrow:

1. On the main menu, note whether the now-visible player model is normally lit/colored or still black/dark.
2. Enter the existing world and confirm it gets past the new Immersive Portals nested-Nether terrain-setup crash.
3. If the world loads, open Creative and check ordinary block/item imagery.
4. Switch to third person and check the player model; one nearby entity is useful if convenient.
5. If any of those fail, retain one screenshot plus `latest.log`/crash report and stop. Only if the failure needs the old draw probe, rerun narrowly with `-Dvulkanmod.traceNewEntityDraws=true`; otherwise keep diagnostics disabled.
6. Only if those visuals pass, continue with a moving Create contraption, Create GUI/overlay, representative particles/liquids/translucency/entities, and an actual portal view. Reload and world re-entry remain deferred.

Do not request separate #746/#747/#752/#753/#758/#768/#772/#784 retests; #785 contains their relevant retained fixes plus the performance cleanup, sampler bookkeeping, Immersive Portals shader-reload repair, and the newly exposed nested terrain-camera repair.

## Real resource-pack gate — automated and RX-confirmed

The two user-supplied PureBDcraft ZIPs live only in private `Vorith03/storage` release assets and are exercised by `.github/workflows/real-resource-packs.yml`; do not copy their bytes or logs containing private payload data into the public repository.

- Storage CI verifies immutable SHA-256 values before use and runs the Immersive Portals smoke with both packs selected together.
- Public VulkanMod CI's optional private-pack steps are normally skipped because repository variable/secret configuration is absent; private storage CI is the authoritative automated real-pack gate.
- Storage run #8 passed the real 16384x8192 atlas workload with both packs retained and Vulkan validation clean. A later 8 GiB runner attempt crossed VulkanMod's intentional host-memory guard; its rerun passed without weakening the threshold.
- The CI runner's `-Dvulkanmod.systemAvailableReserveMinMiB=768` is test infrastructure only. Do not carry it onto the user's heavy-pack machine.
- RX runs confirm both packs remain selected, Vulkan activates on the RX 6900 XT/RADV stack, and real GPU-terrain work executes.

## Distant Horizons boundary — fixed/fail-closed

Distant Horizons 3.2.0-b OpenGL LOD draw/fade, DH lightmap upload, and the Forge AFTER_LEVEL framebuffer probe remain deliberately suppressed under Vulkan. This avoids raw-OpenGL failure while retaining DH data/maintenance. Do not describe native DH LOD rendering itself as supported.

## Texture upload/runtime blocker sequence — settled

The prior staging-buffer replacement/atlas upload issues are closed by public CI, private real-pack CI, and RX evidence. The real large atlas uploads and world terrain use it successfully. Do not reopen texture-pack upload/storage or reinterpret old post-exception validation cascades unless a current build reproduces them.

## Current Create Chronicles compatibility boundary

Closed by direct fixes/current evidence: shader parser issues, Create stencil startup, Twilight Forest/Alex's Caves/Moonlight/Quark shader aliases, real two-pack retention, RX Vulkan activation, real GPU-terrain execution, Distant Horizons AFTER_LEVEL, auxiliary MainTarget ownership, converted-shader lifecycle/state/light uploads, fixed lightmap/overlay sampler bookkeeping, the IP vanilla early-upload crash path, and CI-level IP custom-shader reload ownership.

Still requiring current-user-machine evidence:

- **first:** #785 world entry past the newly exposed IP nested terrain-camera path, main-menu player lighting quality, Creative block/item imagery, player/entity rendering, and a usable portal render after the custom-shader reload repair;
- Iceberg/Advancement Plaques auxiliary item rendering after #744 when naturally encountered;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- representative particles/translucency/entities;
- dirty mixed-section hybrid terrain rebuild, resource reload, and world re-entry remain open roadmap gates but are deferred for the current pass.

Focused evidence: `docs/CREATE_CHRONICLES_COMPATIBILITY.md`, `docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`, `docs/CREATE_CHRONICLES_RENDERER_REPLACEMENTS_2026-09-26.md`, and `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`.

## GPU-terrain durable contract

The bounded compute path classifies qualified ordinary cubes, reconstructs complete terrain vertices, and writes exact-generation output into persistent `ChunkArea` storage. Unsupported Forge content remains CPU-owned.

- REPLACE may GPU-own a fully qualified section. APPEND may combine CPU exception geometry with GPU ordinary-cube geometry only behind the additional hybrid flag.
- Fresh GPU-first sections and dirty rebuilds retain the last complete visible handoff until a complete replacement exists. Incomplete CPU geometry must never become visible without its matching GPU half.
- APPEND rebuilds use generation-scoped, non-visible CPU/GPU staging and atomically switch both halves only after both are ready. Failure/stale/overflow paths remain fail-closed to retained complete geometry or ordinary CPU recovery.
- Authoritative `Block.shouldRenderFace(...)` disagreement demotes GPU ownership; device-to-host mesher readback has the required transfer-write -> host-read dependency.
- Production completion is non-blocking; the synchronous helper-fence wait is smoke/validation only. `MAX_IN_FLIGHT = 32` remains bounded and should not be enlarged without evidence.

Whole-section CPU bypass requires:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
```

Mixed-section APPEND additionally requires:

```text
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Keep accelerated consumption default-off until representative RX correctness and comparable frame-time evidence are complete. Primary contracts: `docs/GPU_TERRAIN_BOUNDARY.md`, `docs/GPU_TERRAIN_OUTPUT_OWNERSHIP_2026-09-16.md`, `docs/GPU_TERRAIN_BOUNDED_OUTPUT_2026-09-14.md`, and `docs/GPU_TERRAIN_MODEL_INSTANCE_CONTRACT_2026-09-14.md`.

## Safety constraints that remain authoritative

- Do not weaken correctness or production memory safety merely to make CI/user testing pass.
- Preserve adaptive host-memory protection on the user's heavy-pack machine unless new machine-specific evidence justifies a deliberate diagnostic override.
- Arbitrary Forge callbacks, block entities, fluids, unsupported/translucent terrain, stale generations, missing residency, invalid ranges, overflow, face-predicate disagreement, and failed GPU work remain CPU/recovery territory.
- A historical shutdown/native-lifetime `double free or corruption (!prev)` signal remains unresolved. Do not make speculative ownership changes without a native backtrace or current-head reproduction.
