# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state

- Current `forge-1.20.1` executable head is `185cf90672dea41abf97eab8c2d9dba8fa0f260e`. CI **#742** passed the complete distributable/Vulkan/compatibility smoke matrix. This default-off GPU candidate-selection follow-up retries rejected uploads when the CPU scene is unchanged; it does not change the four-flag GPU-terrain path or the pending RX full-pack gate.
- The September 24 local branch's three unpushed resource-pack commits are superseded: their CI scripts and standing instructions are already identical on the shared branch, and the shared release-asset download replaces the old private-LFS checkout. PR #11 preserved the one useful #723 historical observation in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`. CI #736/#737 failed only because their new shadow fixture put every direct seed inside the frustum. PR #11 moved some graph-visible seeds clearly outside and retained separate ordinary off-frustum candidates; #740 passed that oracle. Do not replay the older local commits.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. The strategic roadmap remains Phase 7 GPU-terrain/hybrid work under the existing priority override; the current full-pack work is a compatibility/correctness detour requested by the user.

## Real resource-pack gate — automated and RX-confirmed

The two user-supplied PureBDcraft ZIPs live only in private `Vorith03/storage` release assets and are exercised by `.github/workflows/real-resource-packs.yml`; do not copy their bytes or logs containing private payload data into the public repository.

- Storage CI verifies immutable SHA-256 values before use, checks out the current public `forge-1.20.1` branch, and runs `scripts/ci/immersive-portals-smoke.sh` with both real packs selected together.
- Public VulkanMod CI contains optional private-pack steps, but repository variable/secret configuration is absent, so those steps are skipped. **The private storage workflow is the authoritative automated real-pack gate.**
- Storage run #8 passed the real 16384x8192 atlas workload against `5e04d1e...` with both packs retained, Vulkan validation clean, and `Vulkan smoke test passed`. The first large upload completed in about 3.6 s; representative peaks were NativeImage ~797 MiB, VulkanImage estimate ~1430 MiB, and staging high-water ~169 MiB.
- Scheduled storage run #9 attempt 1 later failed only because the disposable 8 GiB runner crossed VulkanMod's intentional host-memory guard: MemAvailable fell to ~771 MiB while the unchanged adaptive reserve was ~793 MiB. The renderer still reached `Vulkan smoke test passed`; selected-pack retention could not remain valid after safety fallback. Do not classify this as a renderer regression.
- Storage #9 was rerun against the then-current public branch **without weakening the memory threshold** and passed. Its two-pack result predates the later direct-seed selection changes; do not claim that the exact `5984ed3` head has a separate private-pack pass merely because public CI is green.
- The CI runner's `-Dvulkanmod.systemAvailableReserveMinMiB=768` remains test infrastructure only; the normal 10% adaptive reserve still applies (~794 MiB on that runner). Do not carry this override into the user's Create Chronicles run or reduce production/user safety merely to make a test pass.

## RX 6900 XT full-pack evidence — 2026-09-25

The user tested build #728 / `5e04d1e12c14f24da9a816ddc5beebc27a789642` in the real Create Chronicles instance with both PureBDcraft packs and all four experimental GPU-terrain flags.

- Vulkan activated as `AMD Radeon RX 6900 XT (RADV NAVI21)`.
- The real base and Create Chronicles PureBDcraft packs remained in the active `Reloading ResourceManager:` list. Both observed client reloads completed; the old `Caught error loading resourcepacks, removing all selected resourcepacks` rollback did not recur.
- The reviewed log contained no `Validation Error`, `SYNC-HAZARD`, or `VK_ERROR_DEVICE_LOST` signature.
- The GPU-terrain path was genuinely active on RADV: sparse-lighting capture ran; fresh whole-section and APPEND/hybrid CPU-bypass samples published successfully; an unsupported visible Forbidden Arcanus model was correctly rejected to CPU fallback (`unsupported_visible_model`). This is representative hardware evidence for the intended fail-closed split, not merely CI coverage.
- World rendering progressed far enough to execute terrain work and Forge's AFTER_LEVEL render stage. The first meaningful blocker was then a native LWJGL abort from Distant Horizons 3.2.0-b calling `GL11.glGetInteger(GL_FRAMEBUFFER_BINDING)` in `ForgeClientProxy.afterLevelRenderEvent()` despite VulkanMod's existing LOD/fade suppression.

Do **not** ask the user to repeat the already-settled pack-retention/RADV/GPU-terrain activation checks as an investigative task. A new launch necessarily exercises them, but the next test question is whether the DH fix moves the runtime frontier forward.

## Distant Horizons Forge framebuffer blocker — fixed

DH 3.2.0-b's Forge `afterLevelRenderEvent(RenderLevelStageEvent)` callback only caches the currently bound OpenGL framebuffer ID for DH's native OpenGL renderer. Under VulkanMod the window is `GLFW_NO_API`, and DH LOD rendering is already deliberately suppressed, so this query has no valid Vulkan meaning and can hard-abort before Java can recover.

- `DistantHorizonsForgeClientProxyMixin` now cancels only that callback at HEAD (`require=1`). DH chunk/data/network/input lifecycle remains intact; this is still a fail-closed bridge, not Vulkan DH LOD rendering.
- CI #733 initially went red after the new regression smoke itself initialized `ForgeClientProxy` too early. That prematurely cached DH's dependency-injected static `PACKET_SENDER` as null, broke Forge mod setup, skipped model-baking events, and secondarily caused VulkanMod's GPU-terrain model-table validation to report only 1/1815 states resolved. This was a **test-induced lifecycle bug**, not a production terrain regression.
- `c7af550787fc207743706afdf5a62da73d927670` makes the smoke load/transform the Forge proxy without class initialization. `1c2040686d8da86a72e41fc8a011b70d9ed65037` updates the fixture assertion accordingly.
- In CI #735 the DH smoke logs `Distant Horizons Forge afterLevelRenderEvent compatibility target verified without early class initialization`, then `Distant Horizons 3.2.0-b compatibility smoke passed`; normal terrain model generation returns to 1730/24135 qualified templates and the overall Vulkan smoke passes. All downstream compatibility fixtures are green.

Distant Horizons remains **fail-closed** under Vulkan: OpenGL LOD draw/fade, DH lightmap upload, and this Forge framebuffer probe are suppressed. Do not describe DH LOD rendering itself as supported.

## Texture upload/runtime blocker sequence — settled

1. `414c645f0d535573dc13e5757efdb4dc6a870785` / CI #726 fixed staging-buffer replacement while the shared upload command buffer still referenced old storage.
2. The first private 16K-atlas attempt exposed exception cleanup: an upload safety exception could leave an atlas-owned shared upload batch/layout stranded.
3. `fd49c5f790447326d1a0d90f478c08b2a48f6579` adds exception-safe atlas batch cleanup while preserving the original resource exception.
4. `5e04d1e12c14f24da9a816ddc5beebc27a789642` preserves explicit atlas batch ownership for normal completion timing/bookkeeping.
5. Public CI, private real-pack CI, and the user's RX run now agree that the old real-pack upload/rollback blocker is closed. Do not reinterpret old post-exception validation cascades as current failures unless reproduced on a current head.

## Current Create Chronicles compatibility boundary

The parser issue, Create stencil startup abort, Twilight Forest `red_thread -> rendertype_cutout`, Alex's Caves `rendertype_sepia -> rendertype_entity_translucent`, Moonlight/Quark `particle` aliases, and the real two-pack reload/retention path are all closed by direct fixes plus current CI/hardware evidence. The #728 RX run also closes Vulkan activation and demonstrates real GPU-terrain execution on RADV.

Still requiring current-user-machine evidence after the DH fix:

- sustained world rendering past the former DH AFTER_LEVEL abort;
- visible Create/Flywheel contraption and Create UI/overlay correctness;
- real Immersive Portals portal visuals;
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

1. **Use CI build #742 / `185cf90672dea41abf97eab8c2d9dba8fa0f260e` for the next RX 6900 XT / RADV Create Chronicles run.** #741 and earlier artifacts are superseded.
2. Keep the same four experimental terrain flags. Do **not** add the private-CI memory-reserve override.
3. Launch normally with the two real PureBDcraft packs. The first new question is whether the target world now renders past the former Distant Horizons framebuffer-query abort; pack retention, RX/RADV Vulkan activation, and initial GPU-terrain execution are already established evidence unless they regress.
4. If world rendering survives, continue directly with the unresolved gates: visible Create/Flywheel contraption + Create UI, real portal visuals, dirty mixed-section rebuild, in-world `F3+T`, exit/re-entry, brief continued play, then normal exit.
5. Stop at the first new meaningful blocker. Retain `latest.log`, `debug.log` when useful, any crash report, and a screenshot only for a visible rendering defect. State which gate was reached.
6. If correctness is clean, return to comparable Phase 5/6 frame-time A/B evidence before making any performance/default-path claim.
