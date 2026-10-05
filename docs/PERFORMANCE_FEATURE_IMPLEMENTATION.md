# Performance feature implementation

The owner authorized implementation of the complete research shortlist on 2026-10-02. Track actual functionality separately from prerequisites and hardware adoption. Follow AGENTS.md section 3A; do not restart settled audits. Research: PERFORMANCE_FEATURE_OPPORTUNITIES_2026-10-02.md. GPU work retains GPU_OFFLOAD_INVESTIGATION_PLAN.md ownership and correctness contracts.

| Feature | Implementation state | Validation/adoption |
| --- | --- | --- |
| Persistent compilation caches | Implemented SPIR-V and Vulkan driver cache persistence | Local contract and full CI #947 pass, including native persistence/reload; hardware hitch measurement pending |
| Observed graphics pipeline prewarming | Implemented opt-in bounded history and compatible native replay; immutable retirement-safe state keys | Baseline full public CI #984 green; cold-recording/resize fixes and required native replay/pixel gate pending #987; hardware hitch measurement open |
| Usage-driven animated textures | Implemented opt-in vanilla ticker gating and first-use refresh | Full CI #948 green on bounded retry; native clock/all-mip pixel oracle passed both attempts; GPU-only usage fallback added; custom raw-UV consumers and hardware adoption open |
| Create/Flywheel Vulkan instancing | Callable CPU Engine and experimental transformed material/state/event dispatch implemented; global adoption pending | Full public CI #974 passed actual material-to-native pixels and state/scene/lifetime checks; existing fallback remains active |
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

Persistent compilation caching alone does not reconstruct graphics pipeline variants.
The separate observed-state implementation below supplies opt-in replay. Neither
feature has measured hardware startup/hitch/FPS gains.

## Observed graphics pipeline prewarming

The implementation through `e56837a6c14fd9219f4834470c3c5124513c0b5e` passed full
public CI #984, run `37247832350`, job `111569282360`. Private real-pack gates were
skipped. It defaults off; enable with `-Dvulkanmod.pipelineVariantPrewarm=true`.
`persistentCompilationCache=false` also disables history. Disabled production draws
do not record variants or sample the prewarming clock.

Each history is identified by both effective SPIR-V byte streams and the complete
vertex/instance layouts. It records at most 32 used pipeline states, including
topology, depth clamp, cull, blend, depth, logic, color mask, stencil and attachment
formats. The versioned/checksummed disk cache has 8 KiB entry and 4 MiB/512 entry
admission caps. No native handle survives serialization. Replay uses the live
compatible render pass and requires matching cull/depth-clamp boundaries. The lazy
requested pipeline remains authoritative; optional replay failure warns once.

`pipelineVariantPrewarmMaxVariants` defaults 4 (clamped 1–16);
`pipelineVariantPrewarmBudgetMs` defaults 2 (clamped 0.1–20). This is an admission
budget checked between synchronous driver calls; one pipeline compilation can
exceed it. It is not a guaranteed frame-time deadline. Only the newest matching
bounded subset is attempted per compatibility boundary. History is persisted at
pipeline cleanup, outside the per-draw recording path.

Continuation review exposed a cold-history bug: `indexOf` returned -1 and the last
index of an empty history was also -1, causing the first observation to be skipped.
The fix requires an existing index before taking the already-newest early return.
The Java 17 contract now passes cold disk persistence, exact state/hash replay,
shader/layout identity and cursor isolation, live-pass replacement, cull/depth-clamp/
format boundaries, 32-record/4-replay bounds, malformed codec records, disk corruption
and disabled admission. It runs actual prewarmer/state/cache code with only external
loader/device surroundings stubbed. Local Gradle remains unavailable because the
distribution download is network-blocked; no local packaged/native pass is claimed.

The screenshot CI launch explicitly enables prewarming and a 20 ms admission
budget. Its new required fixture seeds red-mask/line/ordinary variants, persists
them, recreates the pipeline, and checks that native handles exist before those
alternative states are requested. Exact subsequent keys must reuse those handles;
red/white attachment pixels and compatible target resize must remain correct.
Codec/identity/bounds are also checked against actual loaded types. #985 passed
build/public startup but exposed a test assumption: new observations are not part
of a session's immutable loaded replay history. `45e03f4` reconstructs the codec
replay session from decoded records without polluting the native oracle's disk key.
#986 passed those checks, persisted native replay, exact alternative-key handle
reuse and rendered pixels, then failed its resize/reuse assertion. Framebuffer
cleanup clears color/depth attachment references; PipelineState previously read
those mutable references for equality/hash, invalidating keys already in the
graphics-pipeline map. `cd88b978098d2613387f41b000a0262f49eed1b2` snapshots immutable
attachment format compatibility at state creation and still refreshes the live
RenderPass representative for subsequent native creation. It fixes ordinary lazy
pipeline caching as well as experimental replay. Local Java checks pass stable
hash/equality and HashMap handle reuse after retirement; the same regression oracle
fails against the former PipelineState. Full public CI **#987**, run `37356098042`,
is pending for the corrected slice. General public startup success alone does not prove replay.
Representative RX hitch measurements, loaded-world/reload adoption and pipeline
compile-time scheduling remain open. The feature stays off by default.

## Usage-driven animated textures

`animateOnlyUsedTextures` defaults false. `animationVisibilityGraceMs` defaults 500 and is clamped to 0–5000. `animationAlwaysActiveSprites` accepts resource IDs to exclude untracked/custom consumers from gating. Exact vanilla SpriteContents/Ticker without Forge custom loader metadata qualify; custom subclasses/loaders retain original behavior.

Vanilla metadata advances on every ticker invocation. Hidden interpolation pixel work and uploads are suppressed; materializing an eligible sprite uses vanilla discrete-frame/interpolation routines at the current frame/subframe without advancing the clock. Worker builds capture animated sprite references, generation-atomic publication retains them with their compiled section, and drawing a visible section marks usage. Standard UV access and Forge bulk-quad emission mark item, GUI, entity and particle consumers; usage aggregates across portal views rather than one global camera. First use refreshes dirty atlas rectangles through the existing bounded same-queue upload path, restoring texture binding and upload flags. Closing source contents retires state.

Native CI oracle compares hidden versus ungated clock progression, zero hidden staging, no clock advance on refresh, reordered/repeated frames, interpolation, alpha and each mip across 24 ticks under Vulkan synchronization validation. The oracle passed CI #948 on both attempts. GPU-only terrain sections now retain an unknown-usage bit with their compiled generation and conservatively mark the entire block atlas used when visible; this preserves animation at the cost of reduced savings until model-table usage is available. Nullable exclusion entries are accepted. This is a qualified opt-in path, not proof of coverage for mods that cache raw UVs or bypass normal vertex consumers. Those consumers require explicit marking/conservative fallback before adoption; do not enable the feature by default from synthetic coverage alone.

## Legacy Create/Flywheel adapter: next implementation boundary

The inspected upstream is [Flywheel's legacy 1.20.1/0.6 source](https://github.com/Engine-Room/Flywheel/tree/7ec4a460fb8416c32abe04da6efd8f0965835201), not its current 1.x API. `Engine` implements `MaterialManager` and `RenderDispatcher`; its original GPU path allocates GL model pools, VAOs and mapped instance buffers. A Vulkan engine must replace that ownership rather than call it after enabling Backend.isOn.

`Model.getReader()`/vertex type expose the CPU geometry path; custom `Model.createEBO()` can perform GL work, so it cannot serve as the Vulkan index adapter. Start with qualified built-in quad models and transformed ModelData (model/normal matrices, color and light). Preserve dirty notifications, removal, owner changes during stealInstance, compaction, and camera-origin shifts. Origin rebasing currently clears groups and notifies listeners. Unsupported model/index/program types need an actual fallback path before backend availability changes; Backend.canUseInstancing is a world-level decision, not a per-material capability.

The first implementation prerequisite now supplies an immutable `InstanceVertexFormat` on binding 1 alongside the existing model binding 0, including explicit matrix-column locations and typed packed color/light fetches. GraphicsPipeline checks location collisions, stride/offset/attribute limits and actual device format support before native allocation. `Drawer.drawIndexedInstanced` binds both uploaded slices, supports 16/32-bit indices and bounded nonzero firstInstance, and requires an active render pass. Model index values must be validated at import; callers retain fence-safe buffer lifetime/update ownership. No production wait or persistent instance owner is added at this stage.

The pinned legacy `BasicWriterUnsafe` writes block/sky light as two bytes (`light << 4`), then four RGBA bytes. `ModelWriterUnsafe` writes mat4 at offset 6 and mat3 at offset 70; `BufferLayout` sums sizes without padding, giving a 106-byte stride. Those float offsets and alternating record alignment cannot directly serve this Vulkan input. The adapter must reencode numeric fields into an aligned record and preserve the legacy normalized-light/lightmap shader semantics. The native prerequisite oracle uses an independent aligned 108-byte record with integer ushort light, not an unchanged Flywheel buffer or a qualified Create lightmap shader. Do not instantiate its GL VecBuffer/writer merely to obtain serialized bytes.

The Java contract checks layout admission, immutable ownership, alignment/overlap, device limits and overflow-safe fetch bounds. The native oracle draws a shared quad with independent model/normal matrices, packed color/light and prefix offsets; it checks nonzero firstInstance, zero count, resized targets and an ordinary draw after instancing. That original oracle uses 16-bit indices; the later model/ownership slice qualifies UINT32 indices above 65535 in #967. Implementation `ffc6422adbb8a937666199fbaadb35e8e01ccf49` passed these pixels in CI #962, which correctly failed validation because descriptor-free shaders created empty descriptor pools. `56e62c2222c58c0f6fe23b8753b92229faa91417` skips descriptor pool creation/allocation/binding/reset for descriptor-free pipelines; full public CI **#963** (run `37113220066`, job `111174996134`) passed the same native pixels without validation errors and all existing public gates. Private resource-pack fixtures were skipped. Shared-model import/index validation and dirty/removal/transfer ownership were later qualified in #967 as detailed below. Flywheel engine/material integration, origin listener/recreation, light/material, crumbling, translucency, reload and portal-world gates remain open. No enabled Flywheel backend or performance gain is claimed.

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

The model/instance adapter slice implements owned `ModelGeometry`, optional `LegacyFlywheelModel`
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
firstInstance, zero count, resize and ordinary draw isolation. Full public CI #967
(run `37135821753`, job `111240032366`) passed all of these gates and every existing
public gate at `437fff77ef38722de85edd5dd9ec36e802c26935`. JAR and smoke artifacts
uploaded successfully; private resource-pack fixtures were skipped. Hardware gates
remain open. Engine/material routing, unsupported-material fallback, shade/lightmap,
crumbling, translucency, world/reload/portal ownership and origin listeners are the next
integration boundary; do not enable Backend.isOn from these prerequisites.


The #966 executable (`5b791947...`, run `37135355524`, job `111238774408`)
passed compilation/distributable, Java ownership, native shared-mesh/normalized-light/
32-bit/retirement pixels and preceding public renderer gates, then crashed in the
actual Flywheel CPU fixture with SIGSEGV in `sun.misc.Unsafe.putLong`. JOML 1.10.5
`MemUtilUnsafe.put(Matrix4f, offset, ByteBuffer)` reads a native destination address
and checks directness only under Options.DEBUG. The adapter had supplied its heap
snapshot. `437fff77ef38722de85edd5dd9ec36e802c26935` encodes each numeric matrix
component with Java ByteBuffer.putFloat instead; this removes that unsafe address
path without changing record layout. Full public CI #967 / run `37135821753`, job `111240032366`, passed for the
corrected executable, including both actual pinned Flywheel CPU and native pixel oracles. Crash-report files are now included in smoke artifacts.

Next material routing must preserve the pinned `ModelType`/`Programs.TRANSFORMED`
contract: model.vert replaces vertex color/light with instance color/light and applies
model/normal matrices. `context/world.glsl` samples the lightmap through `shiftLight`
(`light * 255/256 + 1/32`). UNORM fetch qualification alone does not qualify these
samplers, diffuse/fog/alpha behavior or the retained per-vertex shade membership.
Material.model(key, supplier) promises the same instancer for repeated keys within
its owning material. Engine/material/generation cache ownership must satisfy that
promise, close GPU buffers once, and retire the original CPU model without using its
GL-bearing delete path. Preserve RenderLayer/RenderType identity and origin-listener
notification; unknown material/program/model cases need a real fallback rather than
raising an exception after global Backend.canUseInstancing has suppressed Create's
ordinary renderers. Keep backend availability disabled until that routing is proven.

## Legacy material ownership integration (2026-10-03, CI #968)

`LegacyFlywheelMaterials` now exposes the actual optional MaterialManager,
MaterialGroup and Material interfaces through proxies, scoped to one world identity
and reload generation. RenderLayer, RenderType and struct identities partition the
cache; model keys retain the legacy equality-based lookup within their material.
Repeated requests return the same Instancer without invoking the supplier again.
The exact pinned ModelType and transformed program qualify; unknown specs are not
probed or passed to a GL writer. Total groups/materials/models are bounded to 4096
each and retained copied geometry to 256 MiB (plus per-model/per-instance limits).

Newly supplied qualified CPU-only BlockModels are imported and their exact owned
vertex/custom-index allocations freed in finally, without Model.delete or GL calls.
Unqualified ownership stays untouched and is handed to a required fallback owner.
Fallback receives the original layer/state/spec/key; for an already-created unknown
model it receives that exact object through a single-use supplier. Its lifecycle is
explicit: clear membership before origin listeners, close on generation retirement.
This is a delegation interface, **not a qualified world-rendering fallback**. The
engine must supply and test a renderer before enabling global backend availability.

Qualified shared Vulkan meshes allocate lazily on draw and retire once with their
material generation. Origin changes preserve mesh/instancer identity, clear all local
and delegated membership, publish the new coordinate and notify weak recreation
listeners afterward. Failed recreation remains retryable at the same coordinate.
Retired manager/group/material/instancer handles cannot create fresh local instances.
The caller must finish Flywheel tasks and stay on the render thread. A replacement
world/reload creates a fresh manager; production reload/portal engine ownership is
still open.

The actual pinned fixture now exercises API default methods, supplier-once/equal-key
caching, material/layer/state/world-generation isolation, custom-index invalid-import
retry, unknown-spec/model delegation, origin clear/recreation ordering and stale
handle rejection. Existing standalone CPU ownership/input contracts pass locally.
Full local Gradle validation remains unavailable: its distribution download is
network-blocked. Full public CI **#968**, run `37142141537`, job `111258615844`,
passed compilation/distributable packaging, actual optional material fixture, native
renderer gates and all existing public compatibility checks. JAR and log uploads
succeeded; private real-pack fixtures were skipped.
This candidate does not install an Engine, translate Flywheel's material shaders,
qualify lightmap/diffuse/fog/alpha/crumbling/translucency or change Backend.isOn.

## CPU engine dispatcher and batching fallback

`LegacyFlywheelEngine` now exposes the pinned Engine/RenderDispatcher interfaces and
routes MaterialManager defaults through the generation owner. Its currently callable
route deliberately sends all materials through CPU batching until transformed GPU
material shaders qualify. It is not registered in InstanceWorld and Backend.isOn
remains false. The engine synchronizes its exact task owner before origin clearing,
rendering and retirement, rejects foreign task/world events, rejects null-layer
crumbling events and retains camera-distance rebasing with optional fixed-origin
mode. CPU output copies event.stack and adds the integer origin: InstanceWorld
already supplies the negative camera translation. The engine owns its BufferSource,
so finishing a state cannot drain unrelated Minecraft batches. Its private
`OwnedBufferSource` allocates lazily. Failed emissions retire the partial source and
free its current native allocation (including a grown allocation), then recreate
it on the next use. Engine retirement frees it even if material cleanup throws.
`BufferBuilderMemory` is used only for these privately owned builders after their
synchronous consumers finish; vanilla/shared builders retain their existing lifetime.
Release is idempotent, and beginning a batch on a retired builder is rejected.

`LegacyFlywheelCpuFallback` invokes the actual Batched transform into the pinned
ModelTransformer.Params numeric object. It emits ordinary BLOCK/NEW_ENTITY quads
through VertexConsumer with model/normal transforms, instance-or-source color and
packed light, overlay, UV/sprite shift and original shade membership. BLOCK output
uses Forge's directional/constant-ambient diffuse rule; entity formats retain their
shader lighting. The default legacy CPU normal behavior omits stack normals.
Ownership/transfer/removal uses InstanceGroup's new live-iteration boundary without
consuming publication dirty flags or allocating packed GPU snapshots.

Only qualified owned built-in CPU geometry and sequential quad topology use this
fallback. Nonsequential/custom ownership, unsupported render formats and non-Batched
structs delegate with their original source still live. A supplied external fallback
must actually render those cases in the event/world/origin context. No universal
custom-model or shader-program renderer is claimed. Topology/size admission occurs
before consuming source allocations. Registration, actual custom fallback rendering,
real world/portal/reload/crumbling/translucency, and GPU material lightmap/fog/alpha
pixels remain open. This slice supplies callable dispatch and actual CPU emission;
it does not by itself permit global Create renderer suppression.

The actual optional fixture adds emitted BufferBuilder byte checks for ModelType and
OrientedType pose/origin coordinates, color/alpha, light, UV, normals, ordinary and
constant-ambient shading; owner transfer/back, removal and wrong-type rejection;
origin clearing; nonsequential source handoff; and actual Engine default/debug/delete,
pre-frame task synchronization and foreign-owner rejection. The lifetime fixture
also grows an actual transformed builder, aborts a partial batch, verifies release,
recreates a fresh batch and checks repeated retirement and stale-builder rejection.
Standalone ownership
contract passes locally, including live-iteration dirty-bit preservation. Full local
Gradle remains blocked by the distribution download. The CPU Engine/transform slice
`9e7bca9f6d199b60c0d421e5446c31a7d27753c5` passed full public CI **#969**, run
`37155602660`, job `111298239954`, including the actual Flywheel fixture and
JAR/log uploads. Private packs were skipped. The native batch-lifetime follow-up
`5fb3d7024b2f4d0d0f55018e18648debfa319496` passed full public CI **#970**, run
`37156114140`, attempt 2, job `111300814405`, including native batch growth/abort/
recreation/idempotent retirement in the actual Flywheel fixture. Attempt 1 failed
in Forge early-display union-filesystem class loading before these checks; one
bounded retry passed unchanged code. JAR/log uploads succeeded; private packs
were skipped.

## Qualified transformed material shader (engine still CPU-first)

`LegacyFlywheelPipeline` translates the exact pinned 0.6 GPU shader composition:
`model.vert`, `InstancingTemplateData.generateFooter`, `core/diffuse.glsl`,
`block.frag`, `context/world.glsl` and `context/fog.glsl` at upstream commit
`7ec4a460fb8416c32abe04da6efd8f0965835201`. It uses the existing BLOCK/shared-mesh
and aligned transformed-instance layouts. Instance RGBA/light replace model
RGBA/light; normalized transformed normals supply the legacy GPU diffuse formula.
The fragment shader samples the atlas and both lightmap channels with the legacy
`255/256 + 1/32` adjustment, multiplies RGB by illumination/diffuse, preserves
atlas-times-instance alpha independently of lightmap/fog alpha, then applies
linear cylindrical fog and alpha discard. This is the pinned GPU diffuse contract;
it does not add the CPU fallback's separate unshaded/constant-ambient rules.

A private 112-byte std140 world block contains ViewProjection, origin-relative
camera, fog RGB/range and alpha threshold. It validates finite inputs and a positive
finite fog interval. Each draw copies those bytes through the existing dynamic UBO
arena, so later updates cannot alter earlier commands. Retiring the owner prevents
further use, frees the CPU uniform allocation and schedules pipeline/descriptor
cleanup through MemoryManager's frame-fence operations. Production adds no idle
wait or host readback. The caller still owns exact atlas/lightmap sampler bindings,
RenderType blend/depth/cull state, targets, world/origin/event ownership and supported
material admission; this owner does not install or enable a backend.

The native screenshot fixture uses the actual packaged shader, immutable shared
quad and transformed instance layout. It checks model/world matrix translations,
normal normalization/directional diffuse, instance replacement of black/unlit source
vertices, distinguishable block/sky lightmap channels, legacy light coordinates,
atlas/light/fog alpha separation, alpha discard, per-draw uniform isolation, mixed-axis
cylindrical fog, resize/zero count and retirement while commands still reference the
pipeline. Its waits/readback are test-only. #971 compiled the shader and produced
expected first RGB pixels but the alpha oracle used the production screenshot API,
which intentionally forces opacity. The follow-up uses a test-only raw attachment
transfer; production screenshot behavior is unchanged. Corrected executable
`f6400db90f81a4e33dd681e81ad141816c45090e` passed full public CI **#972**, run
`37157165558`, job `111302811813`. All nine raw RGBA pixel cases, synchronization
validation, actual optional Flywheel CPU/lifetime fixtures, distributable packaging
and every existing public gate passed. JAR/log uploads succeeded; private packs
were skipped. No shader/lifetime CI remains pending. This qualifies the bounded
material shader and owner, not an installed engine or performance gain. Real world
and portal rendering, resource reload, custom programs, crumbling, translucent
state/ordering and engine adoption remain open.

## Qualified experimental transformed state/event dispatcher (adoption pending)

The callable Engine has an explicit transformed-rendering constructor option; its
existing constructor remains CPU-first. Exact ModelType/TRANSFORMED, owned BlockModel
geometry, and matching SOLID/solid or CUTOUT/cutout/cutoutMipped identities are the
only native states admitted. Admission precedes supplier consumption, and declined
states/types/models retain the real Batched CPU fallback or explicit unsupported
owner. No Backend or InstanceWorld registration changes.

LegacyFlywheelRenderer applies the exact vanilla RenderType, explicitly establishes
positive cull/depth/write defaults, reconciles fixed atlas/lightmap samplers after
lightmap setup, and restores caller shader/sampler/active-unit/blend/depth/cull/write
state in finally, including failed drawing. Applied post/mod shader owners are
rejected rather than allowing globally overridden descriptors. Stencil/scissor and
the active target remain caller-owned. CUTOUT uses the pinned GPU alpha threshold
0.1. CPU shading remains distinct from the pinned GPU diffuse contract.

Scene composition copies RenderLayerEvent.viewProjection (captured before
InstanceWorld translates its stack), subtracts integer origin from double camera
coordinates before float conversion, then multiplies the event matrix by that
relative camera translation. Ignore-origin contexts preserve the event matrix.
The Engine checks exact task/world ownership and synchronizes before drawing;
material entries lazily own shared meshes and serialize their actual ModelData into
the existing append-only instance arena. Retirement uses existing frame fences.

Focused instance/ownership/fixture/shell contracts pass locally. New native fixtures
exercise six raw pixel frames for distant coordinates, nonzero event translation,
ignore-origin, all three admitted states, alpha, depth, backface cull, sampler repair
and caller/failure restoration; the actual optional Flywheel fixture additionally
draws MaterialManager -> Material -> ModelData -> lazy shared mesh -> textured pixel,
retiring both owners while commands reference them. #973 compiled/packaged and passed existing startup/indirect/post gates, but its
new state fixture stopped before drawing because startup lacks the world atlas/lightmap.
The follow-up validates those textures at the actual descriptor-consuming draw;
missing production textures still reject, while the fixture supplies explicit
synthetic images after real RenderType setup. Edge/gap probes additionally reject
loss of distant fractional camera coordinates. Corrected executable
`2a7fc3391ae6e11f5c5598d1f4127d78b64cd12f` passed full public **#974**, run
`37183077787`, job `111379372847`: all six new native state/event frames, the actual
pinned MaterialManager -> ModelData -> lazy shared mesh -> raw RGBA pixel and
recorded-owner retirement fixture, synchronization validation, existing CPU fallback
checks and every public gate passed. JAR/log uploads succeeded; private packs were
skipped. There is no CI result pending for this bounded dispatcher slice. Real ClientLevel Engine event dispatch, portal-world ownership, reload, crumbling,
translucent ordering and universal unsupported rendering remain open.

## Qualified pre-camera CPU event precision

The previous CPU Engine copied InstanceWorld's already camera-translated float
stack and then added the integer origin. At distant coordinates this cannot recover
fractional camera components lost by the first conversion, unlike the GPU's
double-before-float relative camera path. The optional RenderLayerEvent constructor
now snapshots the original pose and normal only while a callable Engine owns that
exact world. Reference-counted capture leases retire with the last Engine; normal
backend-disabled events allocate no matrix snapshots. The event stack stays intact.

The CPU path composes copied pre-camera matrices with origin-minus-camera computed
in double precision, validates finite inputs and rejects events predating capture
ownership. Its actual pinned event fixture uses a null world strictly for numeric
tests: a rotated pose at 30 million blocks exposes the old float precision loss and
checks fractional translation, normal/caller/copy isolation, missing/nonfinite
inputs, exact world isolation, two-owner retirement and disabled capture. Focused
local contracts pass. Executable `3e71a7eda3d3edd3dc40a9afdc425dcf2a6eda38`
passed full public CI #975, run `37220910466`, job `111490885523`, including the
actual optional constructor/mixin and numeric fixture, all existing native pixels,
combined compatibility and distributable/log uploads. Private packs were skipped.
This closes no loaded ClientLevel or portal gate by itself.

## Opt-in loaded-world Engine probe (runtime evidence pending)

`-Dvulkanmod.flywheelWorldProbe=true` enables one ClientTick END probe after a real
ClientLevel/player and solid shader are ready, between renderer frames. It creates
an explicitly callable transformed + CPU Engine scoped to the actual level, loads
exact ModelType and OrientedType through its MaterialManager defaults and draws two
isolated offscreen frames. A synthetic camera at 30 million blocks and a second
origin 201 blocks away test fractional GPU/CPU alignment and recreation listeners.
The real pinned RenderLayerEvent is constructed before InstanceWorld's camera-stack
translation is reproduced. Foreign task/world and crumbling events reject before
drawing; caller pose/view-projection and routing counters are checked.

The probe uses synthetic white atlas/lightmap images under the actual loaded
texture identities, retaining/restoring the original images and caller shader,
fixed sampler, blend/depth/cull/write, stencil enable, projection/model-view, fog,
shader color and chunk offset state. Its expected raw RGBA pixels distinguish GPU
ModelData from real CPU Batched OrientedData, including constant-ambient shading.
The test alone waits for GPU completion and performs raw readback. Normal gameplay
has a constant false property guard and never enters this path. It does not change
player position, world contents, Backend availability or InstanceWorld registration.
A pass establishes this bounded Engine/event/mixed-routing case; real atlas model
parity, portals, reload, crumbling, translucency and custom programs remain separate.

To collect the new runtime evidence, add `-Dvulkanmod.flywheelWorldProbe=true
-Dvulkanmod.validation=true` to JVM arguments and open an existing world with
Flywheel 0.6 installed. After about one second of world ticks, two test frames run
and ordinary play resumes. Search latest.log for `Flywheel loaded-world probe
passed` or `Flywheel loaded-world probe failed`, and retain both latest.log and
debug.log. This probe is not run by startup-only CI; compilation/default startup
checks do not constitute a loaded-world pass. Compilation and public default
startup for the Camera method-name correction and this addition passed #984;
loaded-world probe evidence remains pending.
