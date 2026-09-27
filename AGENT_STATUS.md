# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state

- Current executable code/test candidate is `97b50739a875537eab84f10a52be509012422684` (`fix: select Immersive Portals terrain dispatcher structurally`). Any later descendant may be documentation-only; executable evidence is anchored here.
- Public CI **#788** / run `36359033122` is fully green at `97b50739`. Build/distributable, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-788` from CI #788. This is the next RX 6900 XT/Create Chronicles candidate.
- Phase 4 remains **5/8** and is the active priority. Open gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and reload/re-entry. Reload and world re-entry remain explicitly deferred for this pass.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while representative full-pack visual correctness is repaired.
- Phase 5 procedure-definition work remains **4/7**. `docs/TERRAIN_PERFORMANCE_BASELINE.md` fixes seed `2026092601`, a 2560x1440 profile, stationary camera, and a 1024-block eastbound spectator route. Numeric OpenGL/Vulkan/frame-time baselines remain open and no performance win is claimed.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.

## Post-#284 performance regression cleanup — 2026-09-27

A hostile/static review compared public CI #284 (`ef0c0fc...`) with current code and removed objectively unnecessary default-path work without rolling back compatibility repairs. Durable detail: `docs/PERFORMANCE_REGRESSION_CLEANUP_2026-09-27.md`.

Validated in public CI and retained through #788:

- disabled GPU-terrain draw handoff takes the direct CPU `RegionDrawBatch` path instead of querying synchronized GPU state and constructing handoff-plan records;
- terrain per-copy `System.nanoTime()` profiling is opt-in via `-Dvulkanmod.profileTerrainUploadCopies=true`, and disabled GPU-mesher completion polling is skipped;
- bounded `NEW_ENTITY` tracing is opt-in via `-Dvulkanmod.traceNewEntityDraws=true`; normal gameplay does not allocate its trace-state set or execute the probe;
- fixed Sampler0/1/2 reconciliation is cached using authoritative texture ids plus a selector-mutation version, preserving temporary-bind/image-replacement invalidation while avoiding repeated GL-id-to-Vulkan-image lookups on unchanged draws;
- packaged Vulkan core shaders skip redundant `ShaderInstance.apply()/clear()` calls, while converted Forge/Immersive-Portals shaders retain the full lifecycle and state publication;
- converted legacy/effect sampler preparation no longer stream-builds a temporary `VulkanImage[]` every manual draw; the existing pipeline-aware attachment helper allocates only when a transition is actually needed;
- converted-shader lifecycle/fixed-sampler bytecode contracts are executed by normal Gradle `check`.

Do **not** turn this static cleanup into a numerical performance claim. Comparable RX 6900 XT A/B data is still required. Ordinary framebuffer-attachment scanning, Forge-safe per-vertex wrapper capability checks, graphics-queue terrain upload ordering, and Forge render-stage callbacks remain intentionally unchanged until measurement or a stronger safe invalidation design justifies further work.

## Current RX 6900 XT visual blocker — 2026-09-27

The user has prioritized missing Creative block/item imagery and player/entity rendering. The latest user-machine evidence remains build **#784** (`d8a3f390...`) with both real PureBDcraft packs and all four GPU-terrain flags.

- #742 reached the world and Creative menu: world terrain and GUI chrome/text/tooltips rendered, but Creative block/item imagery and rendered player/entity models appeared absent.
- #743 prepared framebuffer-attachment samplers before ordinary draws; the broad defect remained. Iceberg/Advancement Plaques also exposed an off-screen `MainTarget` ownership bug.
- #744 fixed auxiliary `MainTarget` ownership: only the primary window target is swapchain-backed; later targets receive normal Vulkan off-screen backing.
- #745 restored vanilla-style fixed Sampler0/1/2 reconciliation. RX showed no visual change, so plain fixed-sampler reconciliation is hardware-disproven as the broad root cause by itself.
- #746 diagnostics proved ordinary `NEW_ENTITY` draws reached Vulkan with complete vertex buffers and real Sampler0 images, including the real 16384x16384 GUI atlas. The probe remains opt-in only through `-Dvulkanmod.traceNewEntityDraws=true`.
- `0fc73d43...` restored the complete converted legacy `ShaderInstance.apply()` state family; `b586d066...` restored `RenderSystem.setupShaderLights(shader)`; `b9f00692...` and `8a4f915a...` restored authoritative RenderSystem bookkeeping for the lightmap/overlay Sampler2/Sampler1 slots.
- #784 is the first RX confirmation after those retained state/light fixes: the main-menu player is visible. Lighting/color quality was not explicitly confirmed, so the prior pitch-black symptom is not yet closed.
- #784 also reached materially farther into world startup. Vulkan activated on RX 6900 XT/RADV, the integrated server had an overworld player, and the old IP `earlyRemoteUpload()` NPE did not recur. The run then exposed a later nested-Nether render crash in IP's merged terrain-begin callback when it called `ChunkRenderDispatcher.setCamera(Vec3)` on VulkanMod's intentionally absent vanilla dispatcher.
- No `VUID-` or `VK_ERROR` signal was found in the supplied #784 debug log.

Do **not** claim the Creative/entity visual blocker closed until the RX machine reaches the world on #788 and confirms Creative/player/entity rendering.

## Immersive Portals blockers — current state

### Vanilla terrain dispatcher paths

- The #752 `MyRenderHelper.earlyRemoteUpload()` null-dispatcher crash is hardware-confirmed cleared by the #784 run. `c299a49d` cancels that obsolete vanilla upload prepass at method HEAD.
- The #784 nested-world crash is a distinct direct `ChunkRenderDispatcher.setCamera(Vec3)` call in IP's merged `LevelRenderer` terrain callback. VulkanMod owns terrain camera/setup and forces IP's terrain-override helper false, so that vanilla dispatcher update must be removed without disturbing unrelated dispatcher work.
- #785 was green but its IP smoke did not yet prove the final composed `LevelRenderer` bytecode selector. Follow-up CI made that missing proof explicit.
- #787 failed closed on the real IP 3.0.7 composition because final `LevelRenderer` contains **two** direct `ChunkRenderDispatcher(Vec3)` calls; a global exact-one selector was therefore invalid.
- `97b50739` / #788 fixes this structurally. When multiple global calls exist, the post-apply rewrite selects only the call in a method invoking IP's merged `ip_allowOverrideTerrainSetup()Z` helper. The pinned 3.0.7 smoke now verifies zero such dispatcher calls remain in IP terrain-helper contexts **and exactly one unrelated direct dispatcher call survives**.
- Public CI #788 is fully green, including the actual Immersive Portals 3.0.7 smoke. RX confirmation is now the next meaningful gate.
- The exploratory variant that skipped IP query-callback geometry remains reverted. `ImmersivePortalsQueryManagerMixin` bypasses unsupported OpenGL query results but still executes supplied rendering callbacks before returning the conservative result.

### Custom shader reload ownership

VulkanMod replaces `GameRenderer.reloadShaders()`, while IP stores custom `ShaderInstance`s in static fields. The retained repair appends IP's registered custom shaders to every Vulkan-managed reload set once IP is ready, preventing static references from pointing at closed shader instances. `078d4c4c...`, `19493e9b...`, `bcdfd5e8...`, and `af70db75...` establish and guard this ownership. CI #788 retains the repair; RX portal rendering is still required before calling it hardware-confirmed.

## Next RX gate

Use build **#788** / `97b50739a875537eab84f10a52be509012422684` with both real PureBDcraft packs and the established four experimental terrain flags. Do not add the private-CI memory-reserve override. Do not enable `vulkanmod.traceNewEntityDraws` or `vulkanmod.profileTerrainUploadCopies` for the normal first pass.

Keep the first pass narrow:

1. On the main menu, note whether the visible player model is normally lit/colored or still black/dark.
2. Enter the existing world. The primary pass/fail question is whether it gets beyond the prior Immersive Portals nested-Nether `ChunkRenderDispatcher.setCamera(Vec3)` crash.
3. If the world loads, open Creative and check ordinary block/item imagery.
4. Switch to third person and check the player model; one nearby entity is useful if convenient.
5. If those work, look through an actual portal long enough to force a secondary-world render and note whether portal geometry/world rendering is usable.
6. If any step fails, retain one screenshot plus `latest.log` and any crash report, then stop. Only use `-Dvulkanmod.traceNewEntityDraws=true` later if a draw-specific failure actually needs it.
7. Only if the above passes, continue with a moving Create contraption, Create GUI/overlay, and representative particles/liquids/translucency/entities. Reload and world re-entry remain deferred.

Do not request separate retests of older intermediate builds; #788 contains the relevant retained rendering/state fixes plus the structurally verified IP terrain-dispatcher repair.

## Real resource-pack gate — automated and RX-confirmed

The two user-supplied PureBDcraft ZIPs live only in private `Vorith03/storage` release assets and are exercised by `.github/workflows/real-resource-packs.yml`; do not copy their bytes or logs containing private payload data into the public repository.

- Storage CI verifies immutable SHA-256 values before use and runs the Immersive Portals smoke with both packs selected together.
- Public VulkanMod CI's optional private-pack steps are normally skipped because repository variable/secret configuration is absent; private storage CI is the authoritative automated real-pack gate.
- Storage run #8 passed the real 16384x8192 atlas workload with both packs retained and Vulkan validation clean. A later 8 GiB runner attempt crossed VulkanMod's intentional host-memory guard; its rerun passed without weakening the threshold.
- The CI runner's `-Dvulkanmod.systemAvailableReserveMinMiB=768` is test infrastructure only. Do not carry it onto the user's heavy-pack machine.
- RX runs confirm both packs remain selected, Vulkan activates on RX 6900 XT/RADV, and real GPU-terrain work executes.

## Current Create Chronicles compatibility boundary

Closed by direct fixes/current evidence: shader parser issues, Create stencil startup, Twilight Forest/Alex's Caves/Moonlight/Quark shader aliases, real two-pack retention, RX Vulkan activation, real GPU-terrain execution, Distant Horizons AFTER_LEVEL handling, auxiliary `MainTarget` ownership, converted-shader lifecycle/state/light uploads, fixed lightmap/overlay sampler bookkeeping, the IP vanilla early-upload crash path, and CI-level IP shader-reload/terrain-dispatcher ownership.

Still requiring current user-machine evidence:

- **first:** #788 world entry past the IP nested terrain-camera crash, main-menu player lighting quality, Creative block/item imagery, player/entity rendering, and a usable portal secondary-world render;
- Iceberg/Advancement Plaques auxiliary item rendering after #744 when naturally encountered;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- representative particles/translucency/entities;
- dirty mixed-section hybrid terrain rebuild, resource reload, and world re-entry remain open roadmap gates but are deferred for the current pass.

Focused evidence: `docs/CREATE_CHRONICLES_COMPATIBILITY.md`, `docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`, `docs/CREATE_CHRONICLES_RENDERER_REPLACEMENTS_2026-09-26.md`, and `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`.

## Distant Horizons boundary — fixed/fail-closed

Distant Horizons 3.2.0-b OpenGL LOD draw/fade, DH lightmap upload, and the Forge AFTER_LEVEL framebuffer probe remain deliberately suppressed under Vulkan. This avoids raw-OpenGL failure while retaining DH data/maintenance. Do not describe native DH LOD rendering itself as supported.

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
