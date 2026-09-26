# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state

- The current pre-checkpoint code/test HEAD is `a663e861a767e13cb3d4d490c81fe417faba80b4` (`test: tighten shader lifecycle cleanup contract`). It adds no production behavior beyond build **#747** / `5d164d2773b01fc89bef173facd82dbe6cc3b462` (`render: apply converted shaders before ordinary draws`), which remains the executable RX candidate.
- Public CI **#749** / run `36275100742` is fully green at `a663e861a767e13cb3d4d490c81fe417faba80b4`. Its compiled-bytecode contract now guards the ordinary converted-shader lifecycle: `ShaderInstance.apply()` must precede fixed-sampler reconciliation and the Vulkan draw, normal cleanup must call `clear()` after the draw, and exceptional cleanup must call `clear()` before rethrow. The full distributable/Vulkan/compatibility matrix passed, including screenshot readback, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Flywheel 0.6, and Create 0.5.1.j.
- Phase 4 remains **5/8** and is the active priority. Open gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and reload/re-entry. Reload and world re-entry remain explicitly deferred for this pass. Phase 7 GPU-terrain/hybrid work remains paused at 6/11 while representative full-pack visual correctness is repaired.
- Phase 5 procedure-definition work remains **4/7**. `docs/TERRAIN_PERFORMANCE_BASELINE.md` fixes seed `2026092601`, a 2560x1440 profile, stationary camera, and a 1024-block eastbound spectator route. Numeric OpenGL/Vulkan/frame-time baselines remain open and no performance win is claimed.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.

## Current RX 6900 XT icon/model blocker — 2026-09-26

The user has explicitly prioritized the missing Creative item/block imagery and player/entity models. Do not divert the active work to reload/re-entry or the separate Immersive Portals remote-upload crash unless the user changes priority or new evidence makes it block the icon/model test.

Hardware progression:

- #742 reached the world and Creative menu: world terrain and GUI chrome/text/tooltips rendered, but Creative block/item imagery and the rendered player/entity models were absent.
- #743 prepared framebuffer attachment samplers before ordinary GUI/VBO pipeline binding. The same broad visual defect remained. Advancement Plaques/Iceberg also exposed `UnsupportedOperationException: Post effect cannot sample its own output attachment` because Iceberg's auxiliary `new MainTarget(96, 96)` had been mistaken for Minecraft's swapchain target.
- #744 repairs that auxiliary-`MainTarget` ownership boundary: only the primary window target is swapchain-backed; later `MainTarget`s receive normal Vulkan off-screen backing. This is CI-covered but has not yet been separately RX-confirmed.
- #745 restored vanilla-style fixed Sampler0/1/2 reconciliation immediately before ordinary draws. **The user's RX #745 test showed no change at all in the missing Creative imagery or player/entity models.** Treat fixed sampler reconciliation as a valid contract repair but as hardware-disproven for the broad visual defect.
- #746 / `7cb7119aa6f4a7ea5c602a52a598453a43157923` added bounded `NEW_ENTITY` diagnostics without intentionally changing rendering. Up to 48 distinct states log shader/pipeline, projection hash, depth/cull/color state, actual descriptor-facing Sampler0/1/2 image identities, buffer byte count, and representative first-vertex data.
- #747 / `5d164d2773b01fc89bef173facd82dbe6cc3b462` restores the missing vanilla `ShaderInstance.apply() -> draw -> clear()` lifecycle around ordinary Vulkan draws and keeps the #746 diagnostics. Full CI is green; RX confirmation is the next gate.
- #748 was superseded by the tightened test follow-up and cancelled by normal workflow concurrency. #749 is the authoritative post-#747 regression-guard run and is fully green.

Why #747 is a substantially stronger candidate than #745:

- Immersive Portals transforms vanilla model-view item/entity programs including the `rendertype_entity_*` family and `rendertype_item_entity_translucent_cull`.
- `ShaderInstanceM` deliberately routes those transformed programs through VulkanMod's converted legacy-shader path rather than the packaged preconverted core path.
- Converted shader UBO fields are bound directly to Minecraft `Uniform` storage. `ShaderInstance.apply()` copies the current `ModelViewMat`, `ProjMat`, `ColorModulator`, active IP clipping equation, and named sampler state into/alongside that storage.
- VulkanMod's overwritten ordinary `BufferUploader.drawWithShader()` had been binding/uploading/drawing the Vulkan pipeline without calling `ShaderInstance.apply()` or `clear()`.
- Therefore a valid item/entity Vulkan pipeline could execute with stale/default converted-shader matrices and state. Stale/identity projection or model-view data can clip both GUI item models and world entities while terrain and non-model GUI chrome continue to render.
- #747 restores the lifecycle before texture preparation and UBO upload, then clears it in `finally`. Packaged core shaders remain on their direct `VRenderSystem` bindings; converted shaders now receive the state contract they already depended on.
- Static follow-up review found no separate concrete bug in converted named-sampler resolution, fixed-sampler fallback, attachment preparation, or the active converted-shader state boundary. Do not make another speculative state patch before RX evidence.

The #747 Immersive Portals smoke passed after this change, as did the entire remaining compatibility matrix. #749 additionally locks the lifecycle ordering and cleanup into CI. Do not claim the visual blocker closed until the RX machine confirms it.

## Next RX gate

Use build **#747** / `5d164d2773b01fc89bef173facd82dbe6cc3b462` with the established four experimental terrain flags and both real PureBDcraft packs. Do not add the private-CI memory-reserve override.

Keep the test narrow:

1. Enter the existing world.
2. Open Creative and check ordinary block/item imagery.
3. Switch to third person and check the player model; one nearby entity is useful if convenient.
4. If either still fails, stop and retain one screenshot plus `latest.log`. The retained `NEW_ENTITY draw trace` lines should distinguish remaining geometry/state/descriptor problems without another blind patch.
5. Only if both pass, continue with a moving Create contraption, Create GUI/overlay, representative particles/liquids/translucency/entities, and a real portal view. Reload and world re-entry remain deferred.

Do not ask for separate #746 testing; #747 contains its diagnostics plus the lifecycle repair.

## Separate deferred runtime issue

The #745 session also exposed an Immersive Portals `earlyRemoteUpload()` crash where VulkanMod's compatibility handling supplied a null chunk dispatcher and IP dereferenced it. This is a real separate compatibility issue, but the user explicitly asked to focus on the missing icons/models. Preserve the finding; do not let it displace the current #747 visual gate unless it prevents reaching that gate.

## Real resource-pack gate — automated and RX-confirmed

The two user-supplied PureBDcraft ZIPs live only in private `Vorith03/storage` release assets and are exercised by `.github/workflows/real-resource-packs.yml`; do not copy their bytes or logs containing private payload data into the public repository.

- Storage CI verifies immutable SHA-256 values before use, checks out current `forge-1.20.1`, and runs the Immersive Portals smoke with both real packs selected together.
- Public VulkanMod CI's optional private-pack steps are normally skipped because repository variable/secret configuration is absent; the private storage workflow is the authoritative automated real-pack gate.
- Storage run #8 passed the real 16384x8192 atlas workload with both packs retained and Vulkan validation clean. A later scheduled 8 GiB runner attempt crossed VulkanMod's intentional host-memory guard; its rerun passed without weakening the threshold.
- The CI runner's `-Dvulkanmod.systemAvailableReserveMinMiB=768` is test infrastructure only. Do not carry it onto the user's heavy-pack machine.
- The user's RX runs confirm the packs remain selected, Vulkan activates as `AMD Radeon RX 6900 XT (RADV NAVI21)`, and real GPU-terrain work executes.

## Distant Horizons boundary — fixed/fail-closed

Distant Horizons 3.2.0-b previously called raw OpenGL `GL11.glGetInteger(GL_FRAMEBUFFER_BINDING)` from Forge AFTER_LEVEL even though its native OpenGL LOD renderer is deliberately suppressed under Vulkan. `DistantHorizonsForgeClientProxyMixin` now cancels only that callback. #742 and later RX runs progressed beyond the former abort.

DH remains **fail-closed** under Vulkan: OpenGL LOD draw/fade, DH lightmap upload, and the Forge framebuffer probe are suppressed. Do not describe DH LOD rendering itself as supported.

## Texture upload/runtime blocker sequence — settled

The prior staging-buffer replacement/atlas upload issues are closed by public CI, private real-pack CI, and RX evidence. The real 16K atlas uploads and world terrain uses it successfully. Do not reopen texture-pack upload/storage or reinterpret old post-exception validation cascades unless a current build reproduces them.

## Current Create Chronicles compatibility boundary

Closed by direct fixes/current evidence: shader parser issues, Create stencil startup, Twilight Forest/Alex's Caves/Moonlight/Quark shader aliases, real two-pack retention, RX Vulkan activation, real GPU-terrain execution, Distant Horizons AFTER_LEVEL, compatibility-matrix documentation, and renderer-replacement minimization.

Still requiring current-user-machine evidence:

- **first:** #747 Creative block/item imagery and player/entity rendering;
- Iceberg/Advancement Plaques auxiliary item rendering after #744 when naturally encountered;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- representative particles/translucency/entities and a real Immersive Portals portal view;
- dirty mixed-section hybrid terrain rebuild, resource reload, and world re-entry remain open roadmap gates but are deferred for the current pass.

Focused compatibility evidence and the current short-form retest sheet live in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`, `docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`, `docs/CREATE_CHRONICLES_RENDERER_REPLACEMENTS_2026-09-26.md`, and `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`.

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
- Distant Horizons 3.2.0-b remains fail-closed as described above.
- Arbitrary Forge callbacks, block entities, fluids, unsupported/translucent terrain, stale generations, missing residency, invalid ranges, overflow, face-predicate disagreement, and failed GPU work remain CPU/recovery territory.
- A historical shutdown/native-lifetime `double free or corruption (!prev)` signal remains unresolved. Do not make speculative ownership changes without a native backtrace or current-head reproduction.
