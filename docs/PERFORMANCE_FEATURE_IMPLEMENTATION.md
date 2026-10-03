# Performance feature implementation

The owner authorized implementation of the complete research shortlist on 2026-10-02. Track actual functionality separately from prerequisites and hardware adoption. Follow AGENTS.md section 3A; do not restart settled audits. Research: PERFORMANCE_FEATURE_OPPORTUNITIES_2026-10-02.md. GPU work retains GPU_OFFLOAD_INVESTIGATION_PLAN.md ownership and correctness contracts.

| Feature | Implementation state | Validation/adoption |
| --- | --- | --- |
| Persistent compilation caches | Implemented SPIR-V and Vulkan driver cache persistence | Local contract and full CI #947 pass, including native persistence/reload; hardware hitch measurement pending |
| Usage-driven animated textures | Implemented opt-in vanilla ticker gating and first-use refresh | Full CI #948 green on bounded retry; native clock/all-mip pixel oracle passed both attempts; GPU-only usage fallback added; custom raw-UV consumers and hardware adoption open |
| Create/Flywheel Vulkan instancing | Pending legacy API/model/shader adapter | Keep existing working fallback until qualified |
| Entity/block-entity occlusion | Optional EntityCulling bridge preserves original cancellation and uncertain-view visibility | Pinned Forge 1.7.2 hook/runtime oracle pending; installed user version and hardware effectiveness unknown |
| Adaptive chunk scheduling | Implemented opt-in publication budget, configurable workers and frame-pressure permits | Local Java contract and full CI #950 passed, including real queue/worker smoke; hardware tuning open |
| World pregeneration tooling | Pending exact Forge 1.20.1 deployment tooling | User-world operation requires world/deployment details |
| Separate-server tooling | Pending mod/config parity and deployment tooling | No access to desktop/server or user world in this workspace |
| Render scaling/upscaling | Pending world-target/GUI/depth/post-chain contract | Quality profile separate from canonical benchmark |
| Far-terrain LOD | Pending DH numeric-data/Vulkan rendering adapter | DH currently does not work; suppression is not LOD support |

## Compilation caches

`persistentCompilationCache` defaults true in `config/vulkanmod_settings.json`. Set false to bypass application caches. Files live under `cache/vulkanmod`; deleting that directory is safe while the game is closed.

SPIR-V cache identity includes the SHA-256 of the actual loaded shaderc library, complete effective source, filename (debug/source identity), stage, entry point, options and a versioned compiler-options schema. If native compiler identity is unavailable, compilation proceeds uncached. Source transformation happens before the existing compile API; its output is authoritative. Changed source/stage/options naturally misses. Only successful compiler outputs are persisted; bytecode ownership remains exactly one shaderc result or native allocation.

Vulkan pipeline persistence includes vendor/device, driver/API version and pipeline UUID in its key, verifies the little-endian Vulkan v1 header before initializing a driver cache, and falls back to an empty cache if saved data is rejected. It exports bounded data before cache destruction while device properties remain alive. Existing in-memory pipeline/state caching remains intact.

Cache entries carry a version, embedded key, byte length and SHA-256 checksum; writes use temporary files and atomic replacement when supported. Per-process admission caps: SPIR-V 4 MiB/entry, 64 MiB/2048 entries; driver cache 32 MiB/entry, 64 MiB/16 entries. A full cache refuses new entries rather than evicting while rendering. Separate game processes can race admission; these are application admission limits, not a global filesystem quota. Cache I/O failure warns once and uses ordinary compilation. No quality or simulation changes occur.

This slice does not yet prewarm graphics pipeline variants: persistence alone does not reconstruct render-pass/descriptor/state identities. That requires observed-variant ownership and reload-safe replay. It does not claim measured startup/hitch/FPS gains.

## Usage-driven animated textures

`animateOnlyUsedTextures` defaults false. `animationVisibilityGraceMs` defaults 500 and is clamped to 0–5000. `animationAlwaysActiveSprites` accepts resource IDs to exclude untracked/custom consumers from gating. Exact vanilla SpriteContents/Ticker without Forge custom loader metadata qualify; custom subclasses/loaders retain original behavior.

Vanilla metadata advances on every ticker invocation. Hidden interpolation pixel work and uploads are suppressed; materializing an eligible sprite uses vanilla discrete-frame/interpolation routines at the current frame/subframe without advancing the clock. Worker builds capture animated sprite references, generation-atomic publication retains them with their compiled section, and drawing a visible section marks usage. Standard UV access and Forge bulk-quad emission mark item, GUI, entity and particle consumers; usage aggregates across portal views rather than one global camera. First use refreshes dirty atlas rectangles through the existing bounded same-queue upload path, restoring texture binding and upload flags. Closing source contents retires state.

Native CI oracle compares hidden versus ungated clock progression, zero hidden staging, no clock advance on refresh, reordered/repeated frames, interpolation, alpha and each mip across 24 ticks under Vulkan synchronization validation. The oracle passed CI #948 on both attempts. GPU-only terrain sections now retain an unknown-usage bit with their compiled generation and conservatively mark the entire block atlas used when visible; this preserves animation at the cost of reduced savings until model-table usage is available. Nullable exclusion entries are accepted. This is a qualified opt-in path, not proof of coverage for mods that cache raw UVs or bypass normal vertex consumers. Those consumers require explicit marking/conservative fallback before adoption; do not enable the feature by default from synthetic coverage alone.

## Adaptive chunk scheduling

`adaptiveChunkScheduling` defaults false. `chunkWorkerThreads` defaults 0 (existing automatic half-of-spare-cores policy); a positive value is clamped to the available processors and takes effect when the worker pool is recreated. With adaptive mode enabled, render-thread publication is admitted between complete result closures with both a time allowance (`chunkPublicationBudgetMs`, default 2 ms, clamp 0.1–20) and count limit (`chunkPublicationsPerFrame`, default 8, clamp 1–64). A dispatcher admits at least one result per frame to prevent starvation. One expensive result can exceed the allowance: this is a soft budget, never a partially published generation.

Budget identity comes from Minecraft.runTick, so repeated upload calls and nested portal views cannot reset it. Existing FIFO result order, cancellation cleanup, generation checks, hybrid publication transactions, area-upload submission, high/low task priority and bounded completed-native-result backlog remain authoritative. Shutdown still discards deferred results and releases their uploads.

Smoothed runTick duration (1/8 EMA) supplies frame pressure against `chunkTargetFrameMs` (default 16.6667, clamp 4–100). Pressure quarters the publication time allowance and worker permits at most; at least one worker remains eligible. Permits are reserved under the task-poll monitor before leaving the queue, released in finally, and wake waiting builders. This signal includes frame waits, simulation and present pacing; it is not an isolated CPU budget and can reduce throughput unnecessarily on paced clients. The worker threads remain allocated; only concurrent task admission changes. Debug output exposes frame duration, permits and deferred drains. No speculative motion prefetch or chunk quality reduction is included.

The production Java budget contract exercises count/deadline boundaries, same-frame repeated calls, next-frame resumption, pressure, progress and nanoTime wrap. Native startup smoke exercises the real result queue, cancellation, shutdown discard and concurrent worker permit release. Leave adaptive scheduling off until representative moving-camera chunk churn demonstrates better frame tails without unacceptable visible chunk latency.

## Entity/block-entity occlusion compatibility

This integration uses an optional external EntityCulling installation rather than duplicating its asynchronous occlusion engine. It adds no packaged dependency. Exact fixture: Forge `entityculling-forge-1.7.2-mc1.20.1.jar`, CurseForge file 5968677; upstream source tag `1.7.2` commit `cc2ae69b93cefcae1dfb0145db880793274c6b58`. That source injects cancellable HEAD hooks into LevelRenderer.renderEntity and BlockEntityRenderDispatcher.render; VulkanMod still routes those calls through the transformed targets, despite batching entities and replacing terrain/block-entity traversal.

`conservativeOcclusionCompatibility` defaults true. Before the external cancellation hooks, the bridge applies EntityCulling's one-second forced-visible timeout to secondary Immersive Portals views and configured uncertain bounds. `occlusionAlwaysVisibleEntities` defaults to Create's four contraption types; `occlusionAlwaysVisibleBlockEntities` defaults to beacons, rope pulleys and hose pulleys. Resource-ID arrays accept null entries; add custom renderers with geometry outside their declared bounds. Unknown/missing external interface leaves the bridge inactive; legacy and versionless interface names are detected independently. No culling result is cached by VulkanMod or shared between cameras. Secondary-view visibility may also retain that object on the main view for one second, trading savings for correctness.

The external mod retains ownership of nametag behavior, renderer shouldRenderOffScreen exclusions and its own whitelist/configuration. This feature does not install EntityCulling into a user instance or prove their installed version. Existing `entityCulling` is VulkanMod's entity batching switch and is distinct from external occlusion. For render-only experiments set EntityCulling's `tickCulling` false; the CI fixture restores any pre-existing config and disables tick culling. No client simulation change is part of this bridge.

The native contract sets real optional-mod culled flags on fresh entity/block-entity instances and invokes the actual transformed dispatch methods, checks cancellation counters and queue isolation, then confirms uncertain-view timeouts admit those same draws. A transformed-bytecode assertion checks guard-before-cancellation ordering for renderEntity. Full portal geometry, moving factories, custom bounds and hardware benefit remain user-world gates.
