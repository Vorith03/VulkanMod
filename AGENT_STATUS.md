# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state

- Current executable head is build **#745** / `acb69d632a124b02d54f5ce6f42df84c33dc3d39` (`render: restore core sampler bindings before draw`). Public CI #745 / run `36234407912` passed the complete distributable/Vulkan/compatibility smoke matrix on the first attempt. Artifact `VulkanMod-Forge-build-745` was published for that exact SHA.
- #744 / `0577fafaa7791093cecae54d8005d25248007f10` fixes an ownership error exposed by the user's #743 RX run: VulkanMod had treated every `MainTarget` instance as Minecraft's swapchain target, but Forge mods may construct auxiliary `MainTarget`s for off-screen rendering. Iceberg 1.1.25 does exactly that for its 96x96 item-icon framebuffer. The primary window target remains swapchain-backed; later auxiliary `MainTarget`s now receive normal Vulkan off-screen color/depth backing and normal RenderTarget semantics.
- #745 fixes a second ordinary item/entity draw-state gap found while following the still-missing #743 Creative/player visuals. Vanilla RenderType setup records authoritative core Sampler0/1/2 ids in `RenderSystem.shaderTextures`, but setup helpers such as `TextureManager.bindForSetup()` can temporarily disturb the emulated active texture binding. OpenGL's `ShaderInstance.apply()` repairs those sampler bindings immediately before drawing; VulkanMod's preconverted core draw path bypassed that GL apply step. `ShaderTextureState.syncFixedSamplers()` now performs the equivalent reconciliation before ordinary `BufferUploader` and VBO descriptor preparation, and `AbstractTexture.bind()` no longer unconditionally overwrites fixed Sampler0 in addition to its active-unit bind.
- The #745 runtime smoke deliberately poisons the descriptor-facing Sampler0/light selectors while keeping different authoritative `RenderSystem` slot 0/2 ids, then verifies the pre-draw reconciliation restores both images. Both Vulkan startup variants passed that oracle, followed by post-chain, screenshot, FTB, Pick Up Notifier, Immersive Portals, Distant Horizons, Crash Assistant, Chat Heads, Flywheel, and exact Create stencil fixtures.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. The user reprioritized Phase 4 on 2026-09-26. Phase 7 GPU-terrain/hybrid work is paused at 6/11 while representative full-pack visual correctness is repaired. Reload and world re-entry remain open gates but are explicitly deferred for this pass.

## Real resource-pack gate — automated and RX-confirmed

The two user-supplied PureBDcraft ZIPs live only in private `Vorith03/storage` release assets and are exercised by `.github/workflows/real-resource-packs.yml`; do not copy their bytes or logs containing private payload data into the public repository.

- Storage CI verifies immutable SHA-256 values before use, checks out the current public `forge-1.20.1` branch, and runs `scripts/ci/immersive-portals-smoke.sh` with both real packs selected together.
- Public VulkanMod CI contains optional private-pack steps, but repository variable/secret configuration is absent, so those steps are skipped. **The private storage workflow is the authoritative automated real-pack gate.**
- Storage run #8 passed the real 16384x8192 atlas workload against `5e04d1e...` with both packs retained, Vulkan validation clean, and `Vulkan smoke test passed`. The first large upload completed in about 3.6 s; representative peaks were NativeImage ~797 MiB, VulkanImage estimate ~1430 MiB, and staging high-water ~169 MiB.
- Scheduled storage run #9 attempt 1 later failed only because the disposable 8 GiB runner crossed VulkanMod's intentional host-memory guard; its rerun passed without weakening the memory threshold. Do not classify the first attempt as a renderer regression.
- The CI runner's `-Dvulkanmod.systemAvailableReserveMinMiB=768` remains test infrastructure only. Do not carry this override into the user's Create Chronicles run or reduce production/user safety merely to make a test pass.

## RX 6900 XT full-pack evidence — 2026-09-25 to 2026-09-26

Build #728 / `5e04d1e12c14f24da9a816ddc5beebc27a789642` established the heavy-pack baseline with both real PureBDcraft packs and all four experimental GPU-terrain flags:

- Vulkan activated as `AMD Radeon RX 6900 XT (RADV NAVI21)`.
- Both real packs remained selected through observed client reloads; the old resource-pack rollback did not recur.
- The reviewed log contained no `Validation Error`, `SYNC-HAZARD`, or `VK_ERROR_DEVICE_LOST` signature.
- The GPU-terrain path was genuinely active on RADV: sparse-lighting capture ran; fresh whole-section and APPEND/hybrid CPU-bypass samples published successfully; unsupported visible content correctly fell back to CPU terrain.
- The first blocker was Distant Horizons 3.2.0-b calling raw OpenGL `GL11.glGetInteger(GL_FRAMEBUFFER_BINDING)` at Forge AFTER_LEVEL. The scoped DH callback guard fixed that without claiming DH LOD rendering support.

Build #742 then reached the world and Creative menu past the former DH abort. World blocks rendered, but Creative item/block icons and the player's rendered model were invisible. Its later Advancement Plaques/Iceberg path exposed an attachment-layout sampling failure. Build #743 prepared framebuffer attachment samplers before ordinary GUI/VBO pipeline binding and passed CI.

The user's **#743** RX retest is still the current hardware frontier:

- The same broad visible defect remained: GUI chrome/text/tooltips rendered, while Creative item/block imagery and the player/entity rendering were absent.
- The world continued to render and the GPU-terrain path continued publishing; Distant Horizons remained correctly fail-closed and Immersive Portals selected its framebuffer compatibility renderer.
- When Advancement Plaques 1.6.9 used Iceberg 1.1.25's `CustomItemRenderer`, the new sampler-preparation path failed explicitly with `UnsupportedOperationException: Post effect cannot sample its own output attachment` in `RenderTargetManager.prepareSampledImages`.
- Iceberg constructs a separate `new MainTarget(96, 96)` for icon rendering. VulkanMod's old broad `instanceof MainTarget` handling mapped that auxiliary target to the live swapchain. Therefore Iceberg's subsequent blit appeared to sample the current output attachment itself. #744 repairs that ownership boundary rather than weakening the self-sampling safety check.
- Independent static tracing of vanilla `RenderType.end()` and VulkanMod's preconverted core draw path then found the sampler-state reconciliation gap addressed by #745. This path is directly shared by batched item/entity rendering and is a stronger candidate for the broad missing Creative/player imagery than the Iceberg-only framebuffer alias.

Do **not** claim #745 has restored Creative/player visuals until it is tested on the RX machine. #744 directly addresses the demonstrated Iceberg auxiliary-framebuffer alias and its #743 crash; #745 restores the authoritative fixed core sampler state before ordinary item/entity draws. Both are CI-covered, but the visible hardware gate remains open.

## Distant Horizons Forge framebuffer blocker — fixed

DH 3.2.0-b's Forge `afterLevelRenderEvent(RenderLevelStageEvent)` callback only caches the currently bound OpenGL framebuffer ID for DH's native OpenGL renderer. Under VulkanMod the window is `GLFW_NO_API`, and DH LOD rendering is already deliberately suppressed, so this query has no valid Vulkan meaning.

- `DistantHorizonsForgeClientProxyMixin` cancels only that callback at HEAD (`require=1`). DH chunk/data/network/input lifecycle remains intact.
- CI #733's first smoke accidentally initialized DH's proxy too early; `c7af550787fc207743706afdf5a62da73d927670` and `1c2040686d8da86a72e41fc8a011b70d9ed65037` corrected the test lifecycle. CI #735 and later fixtures are green.
- The #742/#743 RX runs progressed beyond the former AFTER_LEVEL abort, so the guard is hardware-confirmed.

Distant Horizons remains **fail-closed** under Vulkan: OpenGL LOD draw/fade, DH lightmap upload, and the Forge framebuffer probe are suppressed. Do not describe DH LOD rendering itself as supported.

## Texture upload/runtime blocker sequence — settled

1. `414c645f0d535573dc13e5757efdb4dc6a870785` / CI #726 fixed staging-buffer replacement while the shared upload command buffer still referenced old storage.
2. `fd49c5f790447326d1a0d90f478c08b2a48f6579` made atlas-batch cleanup exception-safe.
3. `5e04d1e12c14f24da9a816ddc5beebc27a789642` preserved explicit atlas batch ownership for normal completion timing/bookkeeping.
4. Public CI, private real-pack CI, and the user's RX run agree that the old real-pack upload/rollback blocker is closed. Do not reinterpret old post-exception validation cascades as current failures unless reproduced on a current head.

## Current Create Chronicles compatibility boundary

The parser issue, Create stencil startup abort, Twilight Forest `red_thread -> rendertype_cutout`, Alex's Caves `rendertype_sepia -> rendertype_entity_translucent`, Moonlight/Quark `particle` aliases, real two-pack retention, RX Vulkan activation, real GPU-terrain execution, and the Distant Horizons raw-GL AFTER_LEVEL abort are closed by direct fixes plus current CI/hardware evidence.

Still requiring current-user-machine evidence (the last two items remain deferred by the user's 2026-09-26 Phase 4 priority):

- **first:** #745 Creative item/block icons, player/entity rendering, and Iceberg/Advancement Plaques off-screen icon rendering;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- representative particles/translucency/entities and a real Immersive Portals portal view;
- a dirty mixed-section hybrid terrain rebuild without incomplete-geometry blink/disappearance;
- in-world `F3+T` after gameplay, followed by continued correct rendering;
- exit to title, re-entry, brief continued play, and normal final exit.

A separate historical shutdown/native-lifetime signal remains unresolved: build #720's failed-reload shutdown ended in glibc `double free or corruption (!prev)`, and a 2026-09-13 full-pack session had already ended in the same allocator-abort family. Current evidence does not identify VulkanMod as the allocator owner. Do not make speculative ownership changes without a native backtrace or a current-head reproduction.

Focused compatibility evidence and the current short-form retest sheet live in `docs/CREATE_CHRONICLES_COMPATIBILITY.md` and `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`.

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

## Next action

1. **Use build #745 / `acb69d632a124b02d54f5ce6f42df84c33dc3d39` for the next RX 6900 XT/Create Chronicles run.** Public CI is fully green and artifact `VulkanMod-Forge-build-745` exists for that exact SHA.
2. Keep the same four experimental terrain flags and the two real PureBDcraft packs. Do **not** add the private-CI memory-reserve override.
3. Make the test narrow: enter the existing world, open Creative, confirm whether item/block icons are visible, and check third-person/player or another representative entity. If an Advancement Plaques/Iceberg item icon appears, confirm the #743 `Post effect cannot sample its own output attachment` crash does not recur.
4. If those visuals are still absent, stop there and retain `latest.log` plus one screenshot; the next investigation should target the remaining ordinary `NEW_ENTITY`/entity pipeline state beyond fixed sampler reconciliation.
5. Only if icons/player are correct, continue with a moving Create contraption, Create GUI/overlay, representative particles/translucency/entities, and a real portal. Reload and world re-entry remain deferred.
