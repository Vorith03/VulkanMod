# Performance feature implementation

The owner authorized implementation of the complete research shortlist on 2026-10-02. Track actual functionality separately from prerequisites and hardware adoption. Follow AGENTS.md section 3A; do not restart settled audits. Research: PERFORMANCE_FEATURE_OPPORTUNITIES_2026-10-02.md. GPU work retains GPU_OFFLOAD_INVESTIGATION_PLAN.md ownership and correctness contracts.

| Feature | Implementation state | Validation/adoption |
| --- | --- | --- |
| Persistent compilation caches | Implemented SPIR-V and Vulkan driver cache persistence | Local contract and full CI #947 pass, including native persistence/reload; hardware hitch measurement pending |
| Usage-driven animated textures | Implemented opt-in vanilla ticker gating and first-use refresh | Full CI #948 green on bounded retry; native clock/all-mip pixel oracle passed both attempts; GPU-only usage fallback added; custom raw-UV consumers and hardware adoption open |
| Create/Flywheel Vulkan instancing | Vulkan instance-input/draw prerequisite implemented; legacy engine/model/material adapter pending | Full public CI #963 passed aligned matrix/color/light pixel oracle; keep existing working fallback until actual adapter qualification |
| Entity/block-entity occlusion | Optional EntityCulling bridge preserves original cancellation and uncertain-view visibility | Full CI #952 passed pinned Forge 1.7.2 native dispatch and corrected hook-order oracle; installed user version and hardware effectiveness unknown |
| Adaptive chunk scheduling | Implemented opt-in publication budget, configurable workers and frame-pressure permits | Local Java contract and full CI #950 passed, including real queue/worker smoke; hardware tuning open |
| World pregeneration tooling | Implemented offline dimension/region command generation and review plan | Local contracts and full CI #954 passed; qualified Chunky install and actual user-world execution pending |
| Separate-server tooling | Implemented strict mod/config parity audit and copied deployment staging | Local contracts and full CI #954 passed; matching real server distribution and user-world/host deployment pending |
| Render scaling/upscaling | Implemented opt-in bilinear world scaling with native GUI | Full CI #961 passed native pixel/depth/effect and transformed boundary checks, plus original IP redirect preservation; loaded-world/hardware adoption pending |
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

## Legacy Create/Flywheel adapter: next implementation boundary

The inspected upstream is [Flywheel's legacy 1.20.1/0.6 source](https://github.com/Engine-Room/Flywheel/tree/7ec4a460fb8416c32abe04da6efd8f0965835201), not its current 1.x API. `Engine` implements `MaterialManager` and `RenderDispatcher`; its original GPU path allocates GL model pools, VAOs and mapped instance buffers. A Vulkan engine must replace that ownership rather than call it after enabling Backend.isOn.

`Model.getReader()`/vertex type expose the CPU geometry path; custom `Model.createEBO()` can perform GL work, so it cannot serve as the Vulkan index adapter. Start with qualified built-in quad models and transformed ModelData (model/normal matrices, color and light). Preserve dirty notifications, removal, owner changes during stealInstance, compaction, and camera-origin shifts. Origin rebasing currently clears groups and notifies listeners. Unsupported model/index/program types need an actual fallback path before backend availability changes; Backend.canUseInstancing is a world-level decision, not a per-material capability.

The first implementation prerequisite now supplies an immutable `InstanceVertexFormat` on binding 1 alongside the existing model binding 0, including explicit matrix-column locations and typed packed color/light fetches. GraphicsPipeline checks location collisions, stride/offset/attribute limits and actual device format support before native allocation. `Drawer.drawIndexedInstanced` binds both uploaded slices, supports 16/32-bit indices and bounded nonzero firstInstance, and requires an active render pass. Model index values must be validated at import; callers retain fence-safe buffer lifetime/update ownership. No production wait or persistent instance owner is added at this stage.

The pinned legacy `BasicWriterUnsafe` writes block/sky light as two bytes (`light << 4`), then four RGBA bytes. `ModelWriterUnsafe` writes mat4 at offset 6 and mat3 at offset 70; `BufferLayout` sums sizes without padding, giving a 106-byte stride. Those float offsets and alternating record alignment cannot directly serve this Vulkan input. The adapter must reencode numeric fields into an aligned record and preserve the legacy normalized-light/lightmap shader semantics. The native prerequisite oracle uses an independent aligned 108-byte record with integer ushort light, not an unchanged Flywheel buffer or a qualified Create lightmap shader. Do not instantiate its GL VecBuffer/writer merely to obtain serialized bytes.

The Java contract checks layout admission, immutable ownership, alignment/overlap, device limits and overflow-safe fetch bounds. The native oracle draws a shared quad with independent model/normal matrices, packed color/light and prefix offsets; it checks nonzero firstInstance, zero count, resized targets and an ordinary draw after instancing. It uses 16-bit indices; the API's 32-bit branch still needs a native adapter oracle. Implementation `ffc6422adbb8a937666199fbaadb35e8e01ccf49` passed these pixels in CI #962, which correctly failed validation because descriptor-free shaders created empty descriptor pools. `56e62c2222c58c0f6fe23b8753b92229faa91417` skips descriptor pool creation/allocation/binding/reset for descriptor-free pipelines; full public CI **#963** (run `37113220066`, job `111174996134`) passed the same native pixels without validation errors and all existing public gates. Private resource-pack fixtures were skipped. Flywheel engine/material integration, shared-model import/index validation, dirty/removal/rebase ownership, light/material, crumbling, translucency, reload and portal-world gates remain open. No enabled Flywheel backend or performance gain is claimed.

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

## Deployment tools

`scripts/performance/deployment.py` implements the separate-server parity/staging and pregeneration command paths. Usage, inputs, exact version/side rules, source-world preservation and adoption checks are in [PERFORMANCE_DEPLOYMENT_TOOLING.md](PERFORMANCE_DEPLOYMENT_TOOLING.md). The tools generate reviewable copies/plans and never launch a server or execute generation. No actual desktop/server/world input is available here; neither deployment nor its performance benefit is claimed.

## World render scaling

`worldRenderScale` defaults `1.0` (native). Finite values clamp to 0.5–1.0; invalid non-finite values retain native resolution. The world uses ceil(native extent × scale), a sampled color/depth TextureTarget, and a bilinear fullscreen composition into the native main target before GuiGraphics construction. This is a quality tradeoff, not FSR or temporal reconstruction; no dynamic resolution controller is included. Keep quality profiles separate from the unchanged-workload benchmark.

Primary MainTarget identity is preserved while its attachment lookups, binds, clears, depth copies, viewport and ScreenSize uniforms route to the owned world target. Vanilla camera, outline and transparency chains resize by chain identity/extent and remain inside the world scope. The native target and render state restore before GUI; an outer Minecraft dispatch wrapper restores them if rendering fails. Deferred world-icon requests capture the native composition before GUI, while ordinary F2 requests retain end-of-frame timing. Resources retire through existing framebuffer frame operations; production scaling adds no host readback or device-idle wait.

Any Immersive Portals installation currently retains native resolution because its multi-view ownership has not been qualified for scaling. This includes the user's existing portal setup. MixinPlugin excludes the capture/cleanup wrappers when IP classes are present, preserving its original world redirect before transformation. Vanilla depth consumers inside the world scope receive scaled depth; native GUI receives the original native depth. Arbitrary third-party post effects and depth consumers outside that scope, loaded-world visual parity, resize/fullscreen, resource reload and hardware benefit remain adoption gates. Scaling is off by default.

The native oracle exercises two scales/resizing, primary attachment identity, red/blue orientation, a sharp one-pixel native overlay, pre-GUI world-icon timing, actual creeper/transparency chains with depth copies, external post-chain resize invalidation, and failed-capture restoration. A transformed-bytecode contract requires capture before camera post processing and composition before native GUI creation. A separate combined IP contract requires its original world redirect and absence of both scaling wrappers. Full CI **#961** (commit `9ee1f5fc3ffa25226d4c47c0d2e7ddfb6cf7c667`, run `37098078519`, job `111131992409`) passed these checks and every public gate; private real-resource-pack fixtures were skipped. The built-in blit shader selects `Framebuffer0`, matching DrawUtil's framebuffer texture binding; DrawUtil's fullscreen projection uses Vulkan zero-to-one depth (the previous GL projection clipped its z=0 quad at z=-1). IP presence detection uses ModLauncher's supported default bytecode retrieval without class initialization; requesting untransformed bytes is unsupported on this loader.

## Legacy CPU model and instance ownership slice (2026-10-03)

The next adapter slice implements owned `ModelGeometry`, optional `LegacyFlywheelModel`
import of the exact pinned built-in `BlockModel`, `SharedModelBuffer` Vulkan ownership,
`InstanceGroup` CPU lifecycle, and a real optional `Instancer`/`ModelData` proxy in
`LegacyFlywheelInstances`. Backend availability remains unchanged. This is a callable
model/instance path, not an enabled Create engine or a measured performance gain.

The importer never invokes `createEBO`, model pools, GL writers, VAOs or `model.delete`.
It reads qualified CPU vertex readers, validates their allocation span before unsafe
numeric reads, and preserves position/color/UV/light/normal plus shade membership.
Sequential quad suppliers get generated triangle indices; the exact built-in custom
supplier supplies its still-owned CPU SHORT/INT indices. A custom supplier whose CPU
indices were already released to GL, other model/reader types and inaccessible API
fields are explicitly unsupported. Every index is range checked. Indices above 65535
use UINT32. Model vertices and index input are each limited to 64 MiB; instance groups
are limited to 16 MiB. Inputs are copied and read-only views cannot modify ownership.
The caller must hold a live, undeleted Flywheel model during import. No model-name
cache is introduced; future engine caches must include model/material/reload identity.

The real transformed data is encoded directly from numeric fields, without creating
a GL VecBuffer or writer. The aligned record has legacy shifted light bytes at 0/1,
zero padding at 2/3, RGBA at 4, mat4 at 8 and mat3 at 72 (stride 108).
Light is fetched as UNORM8 on binding 1, preserving the original byte/255 semantics;
the unused z/w light channels are zero. BLOCK model locations 0–4 reserve transformed
locations 5–13. This does not qualify Create's actual lightmap/material shader.

Instances retain dirty data, drop deleted/transferred members, compact in stable order,
and support transfer back before either group compacts. Failed packing retries consumed
dirty records. Origin clear drops group membership; a future Engine must perform the
legacy listener/recreation notification after clearing all affected groups. Snapshots
are detached from older snapshots and future data writes. Calls require the render
thread after Flywheel task completion; this is not a concurrent update publication API.

Immutable mesh buffers upload once and close idempotently through existing deferred
frame retirement. The instance draw copies a direct snapshot into Drawer’s append-only
frame-slot vertex arena. It never overwrites earlier draws in the slot, uses existing
fence-qualified resets (including failed-acquire behavior), and retires resized buffers
through the existing owner. It adds no production wait or device-idle call.

Focused Java contracts pass locally for model/index admission, immutable ownership,
16/32-bit selection, dirty reuse, removal/compaction, transfer/back, failed packing,
origin clear and close. Full local compilation is unavailable because this workspace
has no downloaded Gradle distribution and the wrapper download is network-blocked.
CI additionally runs the actual pinned Forge Flywheel CPU fixture and a native shared
mesh with indices 65536–65539, normalized light channels, same-frame changing snapshots,
firstInstance, zero count, resize and ordinary draw isolation. These new gates are
pending until the published candidate passes CI. Existing private-pack/hardware gates
remain open. Engine/material routing, unsupported-material fallback, shade/lightmap,
crumbling, translucency, world/reload/portal ownership and origin listeners are the next
integration boundary; do not enable Backend.isOn from these prerequisites.
