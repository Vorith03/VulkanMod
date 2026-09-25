# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail remains in Git and focused evidence documents; keep this file centered on facts that affect the next session.

## Status compaction policy

- Treat checkpoint material dated **today and the immediately preceding calendar day** as a protected recent window; rewrite it only to correct or reconcile facts.
- Once material ages out, collapse it into durable current-state sections. Preserve validated capabilities/gates, unresolved regressions, unique user-machine evidence, measurements that should not be recollected, safety/fallback constraints, and focused-document pointers.
- Prefer removing superseded chronology, stale next steps, obsolete run detail, and repeated implementation narrative already preserved by Git/CI.
- Normally review for compaction at most once per calendar day. Aim for roughly 100 lines or fewer when practical; correctness wins over size.

## Repository state — 2026-09-25

- Current `forge-1.20.1` executable head is `5e04d1e12c14f24da9a816ddc5beebc27a789642` (`Preserve atlas batch ownership in completion tracing`). CI **#728** is fully green at that exact SHA: distributable build, both Vulkan startup smokes, persistent GPU-indirect, post/depth chains, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b fail-closed fixture, Crash Assistant, Chat Heads, Flywheel, and exact Create 0.5.1.j stencil coverage all pass.
- The adversarial audit repair effort remains complete: **0 / 5 repair clusters remaining**. Do not reopen it without contradictory live evidence. Durable report: `docs/CODEBASE_AUDIT_2026-09-18.md`.
- Highest demonstrated `AGENTS.md` milestone remains **6 — playable world**. The strategic roadmap remains Phase 7 GPU-terrain/hybrid work under the existing priority override; the current resource-pack work is an explicit compatibility detour requested by the user.

## Real resource-pack gate — newly automated and green

The two user-supplied PureBDcraft ZIPs now live only in private `Vorith03/storage` release assets and are exercised by `.github/workflows/real-resource-packs.yml`; do not copy their bytes or logs containing private payload data into the public repository.

- Storage CI verifies immutable SHA-256 values before use, checks out the current public `forge-1.20.1` branch, and runs `scripts/ci/immersive-portals-smoke.sh` with both real packs selected together.
- The storage workflow runs on release/manual dispatch, hourly schedule, and immediately when its workflow/support code changes. Scheduled runs key a cache marker by public VulkanMod commit so unchanged public heads are not needlessly retested.
- Public VulkanMod CI contains optional private-pack steps, but repository variable/secret configuration is absent, so those steps are currently skipped. **The private storage workflow is the authoritative real-pack CI gate.**
- Storage run **#8** on 2026-09-25 passed against public head `5e04d1e12c14f24da9a816ddc5beebc27a789642` with Vulkan validation enabled. `Reloading ResourceManager:` contained both `file/vulkanmod-real-base-64x.zip` and `file/vulkanmod-real-overlay-64x.zip`; the 16384x8192 atlas completed batched upload; selected-pack retention verification passed; `Vulkan smoke test passed`; and the workflow's `Validation Error|SYNC-HAZARD` rejection gate stayed clean.
- In that run the first 16384x8192 upload completed in about **3.6 s**. Logged peak/late-state values included NativeImage peak ~797 MiB, VulkanImage estimated peak ~1430 MiB, staging high-water ~169 MiB, and MemAvailable still ~1560 MiB at the later large-atlas completion.
- The private disposable 8 GiB runner keeps VulkanMod's normal 10% adaptive reserve (~794 MiB there) but sets `-Dvulkanmod.systemAvailableReserveMinMiB=768` so the CI-specific minimum does not become 1–2 GiB. This is **test infrastructure only**. Do not carry that override into the user's Create Chronicles run or lower the user's normal safety floor to make a test pass.
- A rerun of the older storage #7 job using its 1 GiB CI reserve also passed against the current public head, reinforcing that #8 is not a one-off functional success.

## Texture upload/runtime blocker sequence — 2026-09-25

User-side Create Chronicles evidence after #723 moved the failure frontier into large texture upload/lifetime behavior. Subsequent fixes are now CI-validated:

1. `414c645f0d535573dc13e5757efdb4dc6a870785` / CI **#726** fixed a real staging-buffer lifetime hazard: growing/replacing texture staging storage must not destroy a buffer while the active shared upload command buffer can still reference it.
2. The original storage #7 real-pack attempt then reached the real 16K atlas but tripped its CI-only 1 GiB system-memory reserve at ~972 MiB available. After vanilla began fallback reload, Vulkan validation reported the atlas image expected as `SHADER_READ_ONLY_OPTIMAL` while still in `TRANSFER_DST_OPTIMAL`.
3. The stack proved the first resource-load exception arose inside `TextureAtlasSprite.uploadFirstFrame()`. VulkanMod had opened a shared atlas upload batch earlier in `TextureAtlas.upload()`, but cleanup existed only at normal `RETURN`; an exceptional upload could therefore strand the batch/layout.
4. `fd49c5f790447326d1a0d90f478c08b2a48f6579` adds exception-safe cleanup around that exact sprite-upload call. It closes/submits only a batch owned by the atlas mixin, records the read-only transition in the same command stream, preserves the original resource exception, and attaches any cleanup failure as suppressed rather than masking it.
5. `5e04d1e12c14f24da9a816ddc5beebc27a789642` preserves explicit ownership information for normal completion timing/bookkeeping. CI #728 and both current-head private real-pack runs are green.

Do not interpret the old post-exception validation cascade as an independent current renderer failure unless it reproduces on a current-head run. The current successful private runs contain no Vulkan validation errors.

## Current Create Chronicles compatibility boundary

The earlier build #720 blocker is superseded: the parser issue, Create stencil startup abort, Twilight Forest `red_thread -> rendertype_cutout`, Alex's Caves `rendertype_sepia -> rendertype_entity_translucent`, and Moonlight/Quark `particle` alias family now have direct fixes/regression coverage. The real two-pack Lavapipe workload is also automated and green.

What CI still cannot prove is the full ~300-mod RX 6900 XT/RADV environment, world entry, real Create/Flywheel gameplay, portal visuals, dirty hybrid terrain rebuilds, in-world `F3+T`, and world exit/re-entry. Those require the user's machine.

A separate historical shutdown/native-lifetime signal remains unresolved: build #720's failed-reload shutdown ended in glibc `double free or corruption (!prev)`, and a 2026-09-13 full-pack session had already ended in the same allocator-abort family. Current evidence does not identify VulkanMod as the allocator owner. Do not make speculative ownership changes without a native backtrace or a current-head reproduction.

Focused compatibility evidence and the full user test sequence live in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`.

## GPU-terrain durable contract

The bounded compute path classifies qualified ordinary cubes, reconstructs complete terrain vertices, and writes exact-generation output into persistent `ChunkArea` storage. Unsupported Forge content remains CPU-owned.

- REPLACE may GPU-own a fully qualified section. APPEND may combine CPU exception geometry with GPU ordinary-cube geometry only behind the additional hybrid flag.
- Fresh GPU-first sections and dirty rebuilds retain the last complete visible handoff until a complete replacement exists. Incomplete CPU geometry must never become visible without its matching GPU half.
- APPEND rebuilds use generation-scoped, non-visible CPU/GPU staging and atomically switch both halves only after both are ready. Failure/stale/overflow paths remain fail-closed to retained complete geometry or ordinary CPU recovery.
- Authoritative `Block.shouldRenderFace(...)` disagreement demotes GPU ownership; device-to-host mesher readback has the required transfer-write -> host-read dependency. These 2026-09-17 validation blockers are fixed and regression-covered.
- Production completion is non-blocking; the synchronous helper-fence wait is smoke/validation only. `MAX_IN_FLIGHT = 32` remains bounded and should not be enlarged without evidence.

Whole-section CPU bypass requires all three flags:

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

- Do not weaken correctness or production memory safety merely to make CI/user testing pass. The storage-run reserve override is isolated to the disposable CI runner.
- The user's prior heavy-pack machine has suffered system-wide OOM behavior and historically had no swap during relevant failures; preserve adaptive host-memory protection unless new machine-specific evidence justifies a deliberate diagnostic override.
- Distant Horizons 3.2.0-b remains **fail-closed** under Vulkan: its OpenGL LOD draw/fade/lightmap paths are suppressed; do not call that working DH LOD rendering.
- Arbitrary Forge callbacks, block entities, fluids, unsupported/translucent terrain, stale generations, missing residency, invalid ranges, overflow, face-predicate disagreement, and failed GPU work remain CPU/recovery territory.

## Next action

1. **Use CI build #728 / `5e04d1e12c14f24da9a816ddc5beebc27a789642` for the next RX 6900 XT / RADV Create Chronicles run.** Build #723 and earlier test artifacts are superseded.
2. Use the same four experimental terrain flags above. Do **not** add the storage CI memory-reserve override.
3. Launch with both real PureBDcraft packs selected. First gate: initial reload completes, both packs remain selected, Vulkan reports the RX 6900 XT/RADV renderer, and the log contains neither resource-pack rollback nor Vulkan validation/device-loss failure.
4. If that succeeds, enter the target world and exercise a visible Create/Flywheel contraption, a real Immersive Portals portal, and at least one dirty mixed-section rebuild. Then `F3+T`, wait for completion, exit to title, re-enter the same world, and play briefly again.
5. On the first new blocker, retain `latest.log`, `debug.log` when useful, any crash report, and a screenshot only for a visible rendering defect. Do not repeat already-settled telemetry without a new question it can answer.
6. If correctness is clean, return to comparable Phase 5/6 frame-time A/B evidence before making any performance/default-path claim.
