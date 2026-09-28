# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Repository state

- Current executable code/test candidate is `795a4815facbc3faf928a084357b7a291f070fcf` (`test: bind Immersive Portals depth clamp pipeline directly`). Any later descendant may be documentation-only; executable evidence is anchored here.
- Public CI **#791** / run `36372663388` is fully green at `795a4815`. Build/distributable, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, exact Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-791` (artifact `10949877815`, SHA-256 `3af6ce949a661c1724ba07e27993c5af0038d57bae4105e78cb15c51e096df03`). This is the next RX 6900 XT/Create Chronicles candidate.
- Phase 4 remains **5/8** and is the active priority. Open mandatory gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and world enter/leave/re-enter + resource reload. Reload and re-entry remain explicitly deferred for the current pass.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while representative full-pack visual correctness is repaired. Phase 5 measurement work remains **4/7**; no performance win is claimed without comparable RX A/B evidence.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- `ROADMAP.md` Phase 4 gate count/order remains authoritative, but its prose-only “Current focus” still names older build #772; this checkpoint supersedes that stale build reference without changing roadmap sequencing.

## Current RX 6900 XT evidence — 2026-09-27

The latest user-machine evidence remains build **#784** (`d8a3f390...`) with both real PureBDcraft packs and all four experimental terrain flags.

- The main-menu player became visible after the retained legacy shader-state/light-direction and fixed lightmap/overlay sampler repairs. Lighting/color quality was not explicitly confirmed, so the former black/dark symptom is not yet closed.
- The run reached materially farther into world startup. Vulkan activated on RX 6900 XT/RADV, the integrated server had an overworld player, and the older Immersive Portals `earlyRemoteUpload()` null-dispatcher crash did not recur.
- It then exposed a distinct nested-Nether Immersive Portals crash: merged `LevelRenderer` terrain setup called vanilla `ChunkRenderDispatcher.setCamera(Vec3)` even though VulkanMod intentionally owns terrain and has no vanilla dispatcher.
- No `VUID-` or `VK_ERROR` signal was found in the supplied #784 debug log.
- Creative block/item imagery and ordinary player/entity rendering in-world remain unconfirmed on a candidate that can pass the nested portal-world startup boundary.

Do **not** claim the Creative/entity/portal visual blocker closed until the RX machine reaches the world on #791 and confirms the relevant visuals.

## Immersive Portals 3.0.7 compatibility boundary

### Vanilla terrain dispatcher ownership

- `c299a49d` cancels IP's obsolete `MyRenderHelper.earlyRemoteUpload()` vanilla chunk-upload prepass; #784 hardware evidence confirms that earlier crash is gone.
- `97b50739` fixes the later merged `LevelRenderer` terrain-camera path structurally. The post-apply rewrite selects the dispatcher call in the method containing IP's merged `ip_allowOverrideTerrainSetup()Z` helper, removes only that obsolete call, and leaves the unrelated direct dispatcher call intact.
- Exact published IP 3.0.7 CI composition has been green since #788. RX confirmation of nested-world rendering is still required.
- The exploratory query-callback skip remains reverted. `ImmersivePortalsQueryManagerMixin` bypasses unsupported OpenGL query results but still executes supplied geometry callbacks before conservatively reporting visible.

### Framebuffer renderer and depth clamp

Exact Forge IP 3.0.7 compatibility mode renders a remote world into a depth-enabled secondary `TextureTarget`, restores the original target, enables depth clamp, and composites the sampled secondary color image over the portal polygon.

VulkanMod's generic `RenderTarget` path already supplies the required semantic framebuffer contract: real sampled Vulkan color/depth images, synthetic texture IDs, LOAD-preserving target switches, shader-read transitions, and ordinary sampler binding. Do not duplicate this in IP-specific code.

The remaining semantic gap was IP's `GL_DEPTH_CLAMP` request. Before #791 VulkanMod merely canceled `CHelper.enableDepthClamp()/disableDepthClamp()` and every graphics pipeline hardcoded `depthClampEnable(false)`.

Retained repair:

- `632136e6` enables Vulkan's optional `depthClamp` device feature when supported, tracks compatibility depth-clamp state, includes it in `GraphicsPipeline`'s cache key, and uses it for `VkPipelineRasterizationStateCreateInfo.depthClampEnable(...)`;
- IP's own `enableClippingMechanism` guard remains authoritative; only its raw GL enable/disable is translated into Vulkan state;
- unsupported devices never request an unavailable Vulkan feature and leave clamp disabled;
- #790's strengthened smoke reached the enabled clamp state but its first probe used IP's `testOneTriangle()`, which failed earlier in unrelated composed BufferBuilder lifecycle (`BufferBuilder was empty`). Treat that as a test-probe failure, not production depth-clamp evidence;
- `795a4815` replaces that probe with a direct bind of IP's already-built `portal_area` `GraphicsPipeline` while depth clamp is enabled;
- **CI #791 is fully green**, so Lavapipe validation successfully created/bound the depth-clamped Vulkan pipeline and the rest of the compatibility matrix still passed.

This closes the known CI-level semantic gap but does **not** prove a real portal looks correct on RX hardware.

### Custom shader reload ownership

VulkanMod replaces `GameRenderer.reloadShaders()`, while IP stores custom `ShaderInstance`s in static fields. The retained repair appends IP's registered custom shaders to every Vulkan-managed reload set once IP is ready, preventing static references from pointing at closed shader instances. CI #791 retains and exercises the repair; RX portal rendering is still required before calling it hardware-confirmed.

## Next RX gate

Use build **#791** / `795a4815facbc3faf928a084357b7a291f070fcf` with both real PureBDcraft packs and the established four experimental terrain flags. Do not add the private-CI memory-reserve override. Do not enable `vulkanmod.traceNewEntityDraws` or `vulkanmod.profileTerrainUploadCopies` for the normal first pass.

Keep the first pass narrow:

1. On the main menu, note whether the visible player model is normally lit/colored or still black/dark.
2. Enter the existing world. First pass/fail question: does it get beyond the prior nested-Nether IP `ChunkRenderDispatcher.setCamera(Vec3)` crash?
3. If the world loads, open Creative and check ordinary block/item imagery.
4. Switch to third person and check the player model; one nearby entity is useful if convenient.
5. Look through an actual portal long enough to force a secondary-world render. Check whether the remote world is visible, correctly clipped to the portal polygon, and free of obvious near/far-plane slicing or magenta/blank framebuffer output.
6. If any step fails, retain one screenshot plus `latest.log` and any crash report, then stop. Add draw-specific diagnostics only if the failure actually needs them.
7. Only if those checks pass, continue with a moving Create contraption, Create GUI/overlay, and representative particles/liquids/translucency/entities. Reload and world re-entry remain deferred.

Do not request separate retests of #788/#790 or older intermediates; #791 contains the retained dispatcher repair plus the depth-clamp semantic fix and its exact IP validation.

## Create Chronicles compatibility boundary

Closed by direct fixes/current evidence: shader parser issues, Create stencil startup, Twilight Forest/Alex's Caves/Moonlight/Quark shader aliases, real two-pack retention, RX Vulkan activation, real GPU-terrain execution, Distant Horizons AFTER_LEVEL suppression, auxiliary `MainTarget` ownership, converted-shader lifecycle/state/light uploads, fixed lightmap/overlay sampler bookkeeping, IP vanilla early-upload ownership, IP merged terrain-dispatcher ownership, and CI-level IP framebuffer/depth-clamp semantics.

Still requiring current user-machine evidence:

- **first:** #791 world entry past the nested terrain-camera crash, main-menu player lighting quality, Creative block/item imagery, player/entity rendering, and a usable real portal secondary-world render;
- Iceberg/Advancement Plaques auxiliary item rendering after #744 when naturally encountered;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- representative particles/translucency/entities;
- dirty mixed-section hybrid terrain rebuild, resource reload, and world re-entry remain open roadmap gates but are deferred for the current pass.

Focused evidence: `docs/CREATE_CHRONICLES_COMPATIBILITY.md`, `docs/CREATE_CHRONICLES_COMPAT_MATRIX.md`, `docs/CREATE_CHRONICLES_RENDERER_REPLACEMENTS_2026-09-26.md`, and `docs/CREATE_CHRONICLES_RETEST_2026-09-25.md`.

## Real resource-pack gate

The two user-supplied PureBDcraft ZIPs live only in private `Vorith03/storage` release assets and are exercised by the private storage workflow; do not copy their bytes or private-payload logs into this public repository.

- Storage CI verifies immutable SHA-256 values and runs the compatibility workload with both packs selected together.
- Public VulkanMod CI private-pack steps are normally skipped because repository variable/secret configuration is absent.
- Storage run #8 passed the real 16384x8192 atlas workload with both packs retained and Vulkan validation clean. RX runs also confirm both packs remain selected and Vulkan activates on RX 6900 XT/RADV.
- The CI-only `-Dvulkanmod.systemAvailableReserveMinMiB=768` override is test infrastructure. Do not carry it onto the user's heavy-pack machine.

## Distant Horizons boundary

Distant Horizons 3.2.0-b OpenGL LOD draw/fade, DH lightmap upload, and the Forge AFTER_LEVEL framebuffer probe remain deliberately suppressed under Vulkan. This avoids raw-OpenGL failure while retaining DH data/maintenance. Do not describe native DH LOD rendering itself as supported.

## Performance and GPU-terrain durable contract

`docs/TERRAIN_PERFORMANCE_BASELINE.md` fixes seed `2026092601`, a 2560x1440 profile, stationary camera, and a 1024-block eastbound spectator route. Comparable OpenGL/Vulkan/frame-time measurements remain open; static cleanup is not a numerical performance claim.

The bounded GPU-terrain path classifies qualified ordinary cubes, reconstructs complete terrain vertices, and writes exact-generation output into persistent `ChunkArea` storage. Unsupported Forge content remains CPU-owned.

- REPLACE may GPU-own a fully qualified section. APPEND may combine CPU exception geometry with GPU ordinary-cube geometry only behind the additional hybrid flag.
- Fresh GPU-first sections and dirty rebuilds retain the last complete visible handoff until a complete replacement exists. Incomplete CPU geometry must never become visible without its matching GPU half.
- APPEND rebuilds use generation-scoped non-visible CPU/GPU staging and atomically switch both halves only after both are ready. Failure/stale/overflow paths retain complete geometry or use ordinary CPU recovery.
- Authoritative `Block.shouldRenderFace(...)` disagreement demotes GPU ownership; device-to-host mesher readback retains the required transfer-write -> host-read dependency.
- Production completion is non-blocking; synchronous helper-fence waiting is smoke/validation only. `MAX_IN_FLIGHT = 32` remains bounded and must not be enlarged without evidence.

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

## Safety constraints

- Do not weaken correctness or production memory safety merely to make CI/user testing pass.
- Preserve adaptive host-memory protection on the user's heavy-pack machine unless new machine-specific evidence justifies a deliberate diagnostic override.
- Arbitrary Forge callbacks, block entities, fluids, unsupported/translucent terrain, stale generations, missing residency, invalid ranges, overflow, face-predicate disagreement, and failed GPU work remain CPU/recovery territory.
- A historical shutdown/native-lifetime `double free or corruption (!prev)` signal remains unresolved. Do not make speculative ownership changes without a native backtrace or current-head reproduction.
