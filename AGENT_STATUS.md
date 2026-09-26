# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state

- Current executable HEAD is `c299a49d64d486ae6f0c8a42357b3318c35b6f8d` (`compat: skip Immersive Portals vanilla remote upload`). Public CI **#753** / run `36276670342` is fully green. Build/distributable, startup with and without early splash, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Phase 4 remains **5/8** and is the active priority. Open gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and reload/re-entry. Reload and world re-entry remain explicitly deferred for this pass.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while representative full-pack visual correctness is repaired.
- Phase 5 procedure-definition work remains **4/7**. `docs/TERRAIN_PERFORMANCE_BASELINE.md` fixes seed `2026092601`, a 2560x1440 profile, stationary camera, and a 1024-block eastbound spectator route. Numeric OpenGL/Vulkan/frame-time baselines remain open and no performance win is claimed.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.

## Current RX 6900 XT icon/model blocker — 2026-09-26

The user has prioritized missing Creative block/item imagery and player/entity models. The latest RX run was build **#752** (`b081ad64...`) with both real PureBDcraft packs and all four GPU-terrain flags.

Hardware progression:

- #742 reached the world and Creative menu: world terrain and GUI chrome/text/tooltips rendered, but Creative block/item imagery and rendered player/entity models were absent.
- #743 prepared framebuffer-attachment samplers before ordinary draws; the broad defect remained. Iceberg/Advancement Plaques also exposed an off-screen `MainTarget` ownership bug.
- #744 fixed auxiliary `MainTarget` ownership: only the primary window target is swapchain-backed; later targets receive normal Vulkan off-screen backing.
- #745 restored vanilla-style fixed Sampler0/1/2 reconciliation immediately before ordinary draws. The user's RX test showed **no visual change**, so sampler reconciliation is a valid contract repair but hardware-disproven as the broad root cause.
- #746 / `7cb7119aa6f4a7ea5c602a52a598453a43157923` added bounded `NEW_ENTITY` diagnostics for shader/pipeline, projection hash, depth/cull/color state, descriptor-facing Sampler0/1/2 images, buffer size, and a representative vertex.
- #747 / `5d164d2773b01fc89bef173facd82dbe6cc3b462` restored the vanilla `ShaderInstance.apply() -> draw -> clear()` lifecycle around ordinary Vulkan draws. Test-only follow-ups through `b081ad64` added a compiled-bytecode ordering/cleanup guard; #752 was fully green.
- The user actually ran **#752**. The run became visually inconclusive because Immersive Portals created a remote Nether client world and then crashed in `MyRenderHelper.earlyRemoteUpload()` before a useful Creative/player inspection could be completed.
- The #752 diagnostics materially narrow the remaining visual defect: ordinary `NEW_ENTITY` draws reached Vulkan with complete vertex buffers, sensible entity/item shaders, and real Sampler0 images. GUI-like item draws referenced the real **16384x16384** atlas. Therefore “no model draw was submitted,” “empty geometry,” and “Sampler0 was absent” are not plausible primary explanations for the broad missing imagery.
- No Vulkan validation `VUID`/`VK_ERROR` signal was found in the supplied #752 debug log.

Do not claim #747 fixed or failed the visual blocker: the #752 hardware run did not remain alive long enough for a meaningful visual verdict.

## Immersive Portals remote-upload blocker — repaired in #753

The #752 crash was:

`MyRenderHelper.earlyRemoteUpload()` -> portal-world `LevelRenderer.getChunkRenderDispatcher()` -> null -> vanilla dispatcher upload dereference.

This is expected at VulkanMod's renderer boundary: portal-world `LevelRenderer`s do not own the vanilla `ChunkRenderDispatcher`, because VulkanMod owns terrain upload/publication.

`c299a49d` now cancels Immersive Portals' vanilla `earlyRemoteUpload()` prepass at method HEAD under the existing IP compatibility mixin. It does **not** alter Vulkan terrain ownership or ordinary render state. `ImmersivePortalsRemoteUploadContractTest` guards the compiled mixin target, HEAD injection, cancellability, and actual `CallbackInfo.cancel()` call.

CI #753 proves both the bytecode contract and the actual Immersive Portals 3.0.7 compatibility smoke accept this boundary. Treat the prior null-dispatcher crash as repaired pending RX confirmation, not as a deferred blocker.

## Next RX gate

Use build **#753** / `c299a49d64d486ae6f0c8a42357b3318c35b6f8d` with both real PureBDcraft packs and the established four experimental terrain flags. Do not add the private-CI memory-reserve override.

Keep the first pass narrow:

1. Enter the existing world.
2. Confirm the prior Immersive Portals `earlyRemoteUpload()` crash no longer occurs.
3. Open Creative and check ordinary block/item imagery.
4. Switch to third person and check the player model; one nearby entity is useful if convenient.
5. If imagery/models are still absent, stop and retain one screenshot plus `latest.log`. The existing `NEW_ENTITY draw trace` data should guide the next change rather than another blind state patch.
6. Only if those visuals pass, continue with a moving Create contraption, Create GUI/overlay, representative particles/liquids/translucency/entities, and an actual portal view. Reload and world re-entry remain deferred.

Do not request separate #746/#747/#752 retests; #753 contains their production fixes/diagnostics plus the IP crash repair.

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

Closed by direct fixes/current evidence: shader parser issues, Create stencil startup, Twilight Forest/Alex's Caves/Moonlight/Quark shader aliases, real two-pack retention, RX Vulkan activation, real GPU-terrain execution, Distant Horizons AFTER_LEVEL, auxiliary MainTarget ownership, converted-shader lifecycle, and the IP vanilla remote-upload crash path.

Still requiring current-user-machine evidence:

- **first:** #753 Creative block/item imagery and player/entity rendering, plus absence of the prior IP early-upload crash;
- Iceberg/Advancement Plaques auxiliary item rendering after #744 when naturally encountered;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- representative particles/translucency/entities and a real Immersive Portals portal view;
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
