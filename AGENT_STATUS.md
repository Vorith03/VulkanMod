# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Repository state

- Current executable code/test candidate is `8c4fc1a9c054516d1b59530f1291a63de9268c72` (`test: require Immersive Portals matrix restore smoke`). Any later descendant may be documentation-only; executable evidence is anchored here.
- Public CI **#796** / run `36394198325` is fully green at `8c4fc1a9`. Build/distributable, both startup modes, persistent GPU indirect, post-chain/depth post-chain, screenshot readback, FTB Library, Pick Up Notifier, exact Immersive Portals 3.0.7 including explicit portal-matrix restore, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and Create 0.5.1.j all passed. Public private-pack steps were skipped as expected.
- Testable artifact is `VulkanMod-Forge-build-796` (artifact `10957517965`, SHA-256 `9b09485424cc6a1ba5b406e500e0524f743ae92c15d908d56ce1a3883c5d21c4`). This is the next RX 6900 XT/Create Chronicles portal-visual candidate.
- Phase 4 remains **5/8** and is the active priority. Open mandatory gates are current Create/Flywheel visuals, representative particles/translucency/entities/GUI, and world enter/leave/re-enter + resource reload. Reload and re-entry remain explicitly deferred for the current pass.
- Phase 7 GPU-terrain/hybrid work remains paused at **6/11** while representative full-pack visual correctness is repaired. Phase 5 measurement work remains **4/7**; no performance win is claimed without comparable RX A/B evidence.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**.
- `ROADMAP.md` Phase 4 gate count/order remains authoritative, but its prose-only “Current focus” still names an older build; this checkpoint supersedes that stale build reference without changing roadmap sequencing.

## Current RX 6900 XT evidence — 2026-09-28

The latest user-machine evidence is build **#791** (`795a4815...`) with both real PureBDcraft packs and all four experimental terrain flags.

- The previous nested-Nether `ChunkRenderDispatcher.setCamera(Vec3)` crash is hardware-confirmed closed. The run entered the world, Immersive Portals selected `RendererUsingFrameBuffer`, initialized its secondary framebuffer, created the Nether client world, and the user could traverse the portal in both directions without that crash recurring.
- The game shut down normally after the test. No `VUID-` or `VK_ERROR` signal was found in the supplied launcher log.
- Real portal content now renders, but the portal image is visibly **spatially detached from the portal opening**. Screenshots show the remote framebuffer composite displaced from the obsidian portal surface rather than remaining rigidly clipped/anchored to it as the camera moves around the structure.
- This is a visual transform/composition failure, not a remote-world creation or secondary-framebuffer failure. Do not regress to null-suppression or reopen the already-closed vanilla dispatcher path.
- Creative inventory imagery, current third-person player/entity rendering, main-menu lighting quality, and Create/Flywheel visual correctness were not conclusively re-checked in this #791 evidence and remain open.

The first RX question for #796 is therefore narrowly whether the remote view is now attached to the actual portal polygon from multiple viewing angles.

## Immersive Portals 3.0.7 compatibility boundary

### Vanilla terrain dispatcher ownership

- `c299a49d` cancels IP's obsolete `MyRenderHelper.earlyRemoteUpload()` vanilla chunk-upload prepass; earlier RX evidence confirms that crash is gone.
- `97b50739` fixes the later merged `LevelRenderer` terrain-camera path structurally. The post-apply rewrite selects the dispatcher call in the method containing IP's merged `ip_allowOverrideTerrainSetup()Z` helper, removes only that obsolete call, and leaves the unrelated direct dispatcher call intact.
- Exact published IP 3.0.7 CI composition has been green since #788, and #791 RX evidence now confirms nested secondary-world rendering gets beyond the former dispatcher failure.
- The exploratory query-callback skip remains reverted. `ImmersivePortalsQueryManagerMixin` bypasses unsupported OpenGL query results but still executes supplied geometry callbacks before conservatively reporting visible.

### Framebuffer renderer and depth clamp

Exact Forge IP 3.0.7 compatibility mode renders a remote world into a depth-enabled secondary `TextureTarget`, restores the original target, enables depth clamp, and composites the sampled secondary color image over the portal polygon.

VulkanMod's generic `RenderTarget` path already supplies the required semantic framebuffer contract: real sampled Vulkan color/depth images, synthetic texture IDs, LOAD-preserving target switches, shader-read transitions, and ordinary sampler binding. #791 RX evidence confirms the remote framebuffer actually contains/rendered world content. Do not duplicate this in IP-specific framebuffer code.

The earlier semantic gap was IP's `GL_DEPTH_CLAMP` request. Before #791 VulkanMod merely canceled `CHelper.enableDepthClamp()/disableDepthClamp()` and every graphics pipeline hardcoded `depthClampEnable(false)`.

Retained repair:

- `632136e6` enables Vulkan's optional `depthClamp` device feature when supported, tracks compatibility depth-clamp state, includes it in `GraphicsPipeline`'s cache key, and uses it for `VkPipelineRasterizationStateCreateInfo.depthClampEnable(...)`;
- IP's own `enableClippingMechanism` guard remains authoritative; only its raw GL enable/disable is translated into Vulkan state;
- unsupported devices never request an unavailable Vulkan feature and leave clamp disabled;
- #790's first strengthened probe failed earlier in unrelated composed BufferBuilder lifecycle (`BufferBuilder was empty`); that was a test-probe failure rather than production depth-clamp evidence;
- `795a4815` replaced that probe with a direct bind of IP's already-built `portal_area` `GraphicsPipeline` while depth clamp is enabled;
- CI #791 and #796 are fully green for the depth-clamped Vulkan pipeline path.

### Portal-area matrix ownership

#791's first successful real portal exposed a separate visual bug: the rendered remote view was displaced/detached from the portal surface.

Exact IP 3.0.7 behavior explains the failure:

- `ViewAreaRenderer.renderPortalArea(...)` explicitly writes its camera-relative `modelViewMatrix` and supplied projection matrix to `portalAreaShader`, then calls `ShaderInstance.apply()` before building the portal polygon;
- `MyRenderHelper.drawPortalAreaWithFramebuffer(...)` does the same for `drawFbInAreaShader` before compositing the secondary framebuffer over the portal polygon;
- VulkanMod's converted-legacy `ShaderInstance.apply()` mirrors global `RenderSystem` model-view/projection matrices for ordinary legacy draws. That overwrote IP's explicit portal matrices immediately before these two portal-area draws. The projection is normally already global; the model-view replacement is the important spatial mismatch for IP's camera-relative portal vertices.

Retained repair for #796:

- `d41ce91d` adds a narrow compatibility bridge that restores IP's explicit portal `ModelViewMat`/`ProjMat` values after converted legacy `apply()` and before draw-time UBO upload;
- `2a7c2eef` applies that boundary to the final framebuffer portal composite after IP's viewport setup and before portal geometry submission;
- `b5d9565e` applies the same boundary to `ViewAreaRenderer.renderPortalArea(...)` immediately after `ShaderInstance.apply()`;
- the generic legacy shader behavior is intentionally unchanged, preserving the existing item/entity/light-state repairs rather than globally weakening normal ShaderInstance state publication;
- `b28ee69c` extends the exact-IP smoke to exercise both real IP shaders with deliberately non-global matrices. It first proves converted `apply()` overwrites them, then proves the compatibility bridge restores the exact values in the live `Uniform` storage used by Vulkan UBO fields;
- `8c4fc1a9` makes that matrix-restore marker mandatory in `scripts/ci/immersive-portals-smoke.sh`;
- **CI #796 is fully green**, so both new mixin injection points apply to published Forge IP 3.0.7 and the matrix repair passes under the same full compatibility matrix.

RX confirmation is still required before declaring real portal visual placement correct.

### Custom shader reload ownership

VulkanMod replaces `GameRenderer.reloadShaders()`, while IP stores custom `ShaderInstance`s in static fields. The retained repair appends IP's registered custom shaders to every Vulkan-managed reload set once IP is ready, preventing static references from pointing at closed shader instances. CI #796 retains and exercises the repair; resource reload itself remains deferred for the current RX pass.

## Next RX gate

Use build **#796** / `8c4fc1a9c054516d1b59530f1291a63de9268c72` with both real PureBDcraft packs and the established four experimental terrain flags. Do not add the private-CI memory-reserve override. Do not enable `vulkanmod.traceNewEntityDraws` or `vulkanmod.profileTerrainUploadCopies` for the normal first pass.

Keep this pass narrow and reuse the same real portal if practical:

1. Approach the portal from the same side/area that showed the detached slab in #791.
2. Move left/right and closer/farther while looking through it. The remote image must remain exactly attached to and clipped by the portal opening rather than appearing as a displaced rectangle/slab.
3. Cross the portal once and inspect the reverse side as well. Ordinary portal traversal already worked on #791; this is about spatial composition from both sides, not a reload/re-entry stress test.
4. If the portal is now correct, open Creative and check ordinary block/item imagery, then check the player in third person and one nearby entity.
5. Only after those pass, continue with a moving Create/Flywheel contraption, Create GUI/overlay, and representative particles/liquids/translucency/entities.
6. If the portal remains detached, retain screenshots from two different viewing angles plus the launcher/latest log. Do not repeat #791/#790 or older crash tests.

Reload and world leave/re-enter remain explicitly deferred.

## Create Chronicles compatibility boundary

Closed by direct fixes/current evidence: shader parser issues, Create stencil startup, Twilight Forest/Alex's Caves/Moonlight/Quark shader aliases, real two-pack retention, RX Vulkan activation, real GPU-terrain execution, Distant Horizons AFTER_LEVEL suppression, auxiliary `MainTarget` ownership, converted-shader lifecycle/state/light uploads, fixed lightmap/overlay sampler bookkeeping, IP vanilla early-upload ownership, IP merged terrain-dispatcher ownership, real secondary-world/framebuffer execution, and CI-level IP framebuffer/depth-clamp/matrix semantics.

Still requiring current user-machine evidence:

- **first:** #796 portal polygon anchoring/clipping from multiple viewing angles;
- Creative block/item imagery and player/entity rendering on the current post-portal-fix candidate;
- main-menu player lighting quality if convenient on a fresh launch;
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
