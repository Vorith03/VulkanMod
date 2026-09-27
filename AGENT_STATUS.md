# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state

- Current executable code/test HEAD is `896221b08c62f22be40a7a72c43af0549a28e58f` (`test: guard converted shader light directions`). Production behavior added since #753 is in `0fc73d436219b08525b8bdf88feae19be5dee1c1` (`render: restore complete legacy shader state`) and `b586d06689952f43b4f47f4ff0904679ca6c808b` (`render: restore legacy shader light directions`).
- Public CI **#758** / run `36281304200` is fully green at `896221b0`. Build/distributable, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Phase 4 remains **5/8** and is the active priority. Open gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and reload/re-entry. Reload and world re-entry remain explicitly deferred for this pass.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while representative full-pack visual correctness is repaired.
- Phase 5 procedure-definition work remains **4/7**. `docs/TERRAIN_PERFORMANCE_BASELINE.md` fixes seed `2026092601`, a 2560x1440 profile, stationary camera, and a 1024-block eastbound spectator route. Numeric OpenGL/Vulkan/frame-time baselines remain open and no performance win is claimed.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.

## Current RX 6900 XT icon/model blocker — 2026-09-26

The user has prioritized missing Creative block/item imagery and player/entity rendering. The latest RX run was build **#752** (`b081ad64...`) with both real PureBDcraft packs and all four GPU-terrain flags.

Hardware progression and current diagnosis:

- #742 reached the world and Creative menu: world terrain and GUI chrome/text/tooltips rendered, but Creative block/item imagery and rendered player/entity models appeared absent.
- #743 prepared framebuffer-attachment samplers before ordinary draws; the broad defect remained. Iceberg/Advancement Plaques also exposed an off-screen `MainTarget` ownership bug.
- #744 fixed auxiliary `MainTarget` ownership: only the primary window target is swapchain-backed; later targets receive normal Vulkan off-screen backing.
- #745 restored vanilla-style fixed Sampler0/1/2 reconciliation immediately before ordinary draws. The user's RX test showed **no visual change**, so sampler reconciliation is a valid contract repair but hardware-disproven as the broad root cause.
- #746 / `7cb7119aa6f4a7ea5c602a52a598453a43157923` added bounded `NEW_ENTITY` diagnostics for shader/pipeline, projection hash, depth/cull/color state, descriptor-facing Sampler0/1/2 images, buffer size, and a representative vertex.
- #747 / `5d164d2773b01fc89bef173facd82dbe6cc3b462` restored the `ShaderInstance.apply() -> draw -> clear()` lifecycle around ordinary Vulkan draws. Test-only follow-ups through `b081ad64` added a compiled-bytecode ordering/cleanup guard.
- The user ran #752. It became visually inconclusive in-world because Immersive Portals created a remote Nether client world and then crashed in `MyRenderHelper.earlyRemoteUpload()` before a useful Creative/player inspection could be completed.
- The #752 diagnostics showed ordinary `NEW_ENTITY` draws reaching Vulkan with complete vertex buffers and real Sampler0 images. GUI-like draws referenced the real **16384x16384** atlas. “No model draw,” “empty geometry,” and “Sampler0 absent” are therefore not plausible primary explanations.
- **New hardware detail supplied after that run:** the user *could see the player model in the main menu, but it was pitch black*. This proves at least that menu player geometry/projection/base-texture submission can survive far enough to produce a visible model; the prior description of player rendering as simply “absent” was incomplete.
- That observation exposed a concrete converted-shader state regression. The Forge branch's overwritten legacy `ShaderInstance.apply()` had retained only ModelView, Projection, and ColorModulator updates. Upstream 1.20.x VulkanMod also updates inverse-view rotation, glint alpha, fog start/end/color/shape, texture matrix, game time, screen size, and line width. `0fc73d43` restores that complete state family while preserving Immersive Portals clipping and named-sampler activation.
- More importantly, Minecraft 1.20.1 `ShaderInstance` owns `LIGHT0_DIRECTION` and `LIGHT1_DIRECTION`, and the item/entity shader JSON initializes both to zero. Entity/item vertex shaders consume those directions for diffuse lighting. The converted legacy path had not called `RenderSystem.setupShaderLights(shader)`, leaving those live directions at defaults. `b586d066` restores that upload. This is a concrete mechanism capable of producing a present-but-black menu player and extremely dark item/entity imagery.
- `ShaderInstanceLegacyApplyContractTest` now guards the complete standard live-state getter family, ScreenSize update, named-sampler activation, and `RenderSystem.setupShaderLights(ShaderInstance)`. It is invoked by the existing shader lifecycle contract. CI #758 proves the compiled contract and full compatibility matrix accept the repaired path.
- No Vulkan validation `VUID`/`VK_ERROR` signal was found in the supplied #752 debug log.

Do **not** claim the visual blocker closed until the RX machine confirms #758. The lighting/state diagnosis is materially stronger than the prior sampler hypothesis because it directly matches the user's black-player observation and the logged successful model/texture submission.

## Immersive Portals remote-upload blocker — repaired in #753 and retained

The #752 crash was `MyRenderHelper.earlyRemoteUpload()` dereferencing a null vanilla `ChunkRenderDispatcher` from a portal-world `LevelRenderer`. VulkanMod intentionally owns terrain upload/publication and does not provide that vanilla dispatcher.

`c299a49d` cancels Immersive Portals' vanilla `earlyRemoteUpload()` prepass at method HEAD under the existing IP compatibility mixin. `ImmersivePortalsRemoteUploadContractTest` guards the compiled injection/cancellation. CI #753 and #758 both pass the actual Immersive Portals 3.0.7 smoke. Treat the crash path as repaired pending RX confirmation.

## Next RX gate

Use build **#758** / `896221b08c62f22be40a7a72c43af0549a28e58f` with both real PureBDcraft packs and the established four experimental terrain flags. Do not add the private-CI memory-reserve override.

Keep the first pass narrow and use the newly discovered black-model signal:

1. On the main menu, inspect the player model first. It should no longer be pitch black if the restored light/state contract is effective.
2. Enter the existing world and confirm the prior Immersive Portals `earlyRemoteUpload()` crash no longer occurs.
3. Open Creative and check ordinary block/item imagery.
4. Switch to third person and check the player model; one nearby entity is useful if convenient.
5. If any of those still fail, stop and retain one screenshot plus `latest.log`. Do not broaden the test; use the retained diagnostics to choose the next change.
6. Only if those visuals pass, continue with a moving Create contraption, Create GUI/overlay, representative particles/liquids/translucency/entities, and an actual portal view. Reload and world re-entry remain deferred.

Do not request separate #746/#747/#752/#753 retests; #758 contains their relevant production fixes/diagnostics plus the complete converted-shader state and light-direction repair.

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

- **first:** #758 main-menu player lighting, Creative block/item imagery, player/entity rendering, and absence of the prior IP early-upload crash;
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
