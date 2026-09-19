# Full codebase audit checkpoint — 2026-09-18

This is a **continuation checkpoint**, not the final audit report.

The full adversarial audit was intentionally started before the next RX 6900 XT/Create Chronicles functional test. Its purpose is to review the current Forge 1.20.1 production tree without assuming that green CI, inherited VulkanMod behavior, or recently added smoke coverage proves code quality or lifetime correctness.

## Audited snapshot

- Repository: `Vorith03/VulkanMod`
- Branch: `forge-1.20.1`
- Audited HEAD: `5e17f013783a08bccf6ffa12024bd2f79ddb1f2d` (`docs: advance hardware test checkpoint to CI 690 [skip ci]`)
- Executable parent: `8e553bb6991c16f86098227aa35639189d4e4aaf` (`compat: preserve portal reload guards`)
- The HEAD commit changes documentation only, so executable code under audit is identical to `8e553bb6`.
- CI #690 / run `35385931592` is the last fully green executable validation checkpoint described by `AGENT_STATUS.md`.

If live HEAD changes after this checkpoint, follow `AGENTS.md` Section 3A and inspect the delta before continuing. Do not silently apply these conclusions to changed code.

## Audit standard

The user explicitly requested an extremely thorough review with no assumptions and no defense of bad code.

Accordingly:

- Treat smoke success as evidence, not proof of lifetime/correctness.
- Distinguish confirmed defects from suspicious code and unresolved questions.
- Trace ownership, synchronization, generation, and exception paths across classes.
- Do not promote a concern merely because code is unusual.
- Explicitly remove hypotheses from the findings list when the relevant contract proves them safe.
- Preserve experimental/fail-closed boundaries while reviewing them; an intentionally gated incomplete feature is not automatically a defect.
- Prefer underlying ownership/lifetime fixes over symptom patches.

## Repository surface established

The recursive production tree contains approximately:

- 279 production Java files;
- 288 Java files including the small `src/test` surface;
- ~180 shader/resource files.

A significant amount of validation code also lives under `src/main` behind CI/system-property gates, so test adequacy must be evaluated by behavior and gating rather than source-set location alone.

## Audit surfaces substantially reviewed

The following areas received a deep or substantial first pass:

- Vulkan device and renderer initialization/cleanup;
- frame acquire/submit/present synchronization;
- command pools and queue helpers;
- `Synchronization`;
- VMA buffer/image allocation and deferred retirement;
- staging, vertex, uniform, indirect and automatic index buffers;
- swapchain and off-screen framebuffer ownership;
- render passes / RenderTarget plumbing;
- screenshot readback;
- Vulkan texture wrappers and Minecraft texture mixins;
- pipeline/descriptor infrastructure and GLSL conversion paths;
- Forge/Mixin startup/window integration;
- legacy OpenGL compatibility shims;
- post/effect shader paths;
- generic VBO / BufferBuilder paths;
- terrain area-buffer allocation and graphics-queue upload ordering;
- section/region reuse and generation invalidation;
- worker -> render-thread terrain publication;
- a large first pass through GPU-terrain capture and fail-closed ownership transitions.

The audit is **not complete**. See "Remaining audit work."

# Confirmed findings

These are defects supported by the current audited tree. Severity is relative to runtime correctness and recoverability, not a claim that each is currently observed on the user's hardware.

## A1 — High: off-screen framebuffer teardown destroys attachment views before their framebuffer

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/framebuffer/Framebuffer.java`
- `src/main/java/net/vulkanmod/vulkan/memory/MemoryManager.java`
- `src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java`

`Framebuffer.cleanUp()` schedules its color/depth images for deferred retirement and separately queues native `VkFramebuffer` destruction through a frame operation.

`MemoryManager.initFrame()` currently performs:

1. `freeBuffers(frame)`;
2. then `doFrameOps(frame)`.

Therefore, at the safe frame boundary, attachment `VulkanImage` retirement can destroy the image view before the queued `vkDestroyFramebuffer(...)` runs.

The frame fence proves that the GPU has finished using the objects, but it does **not** make the Vulkan object-lifetime ordering valid: a `VkFramebuffer` must not remain alive while an attachment view it references has already been destroyed.

Repair direction: make dependent-object retirement explicitly ordered so framebuffers die before their attachment views/images, rather than relying on two independent same-frame queues.

## A2 — High: `VulkanImage.createImage()` swallows Vulkan creation failures and continues with invalid state

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java`
- `src/main/java/net/vulkanmod/vulkan/memory/MemoryManager.java`

The legacy private `VulkanImage.createImage(...)` catches `Exception`, prints the stack trace, and returns normally.

Callers then continue with view and sampler creation. For non-`Error` Vulkan/VMA failures this converts a clean allocation/creation failure into construction around zero or invalid handles, producing a secondary failure or invalid Vulkan state.

The recent explicit OOM propagation in `MemoryManager.createImage(...)` does not fix the broader catch-and-continue behavior.

The higher-level texture/depth factories are also not failure-atomic if view or sampler creation fails after the image allocation succeeds.

Repair direction: fail fast, propagate the original failure, and clean up partially-created image/view/sampler state transactionally.

## A3 — Rendering correctness: sampler identity does not include clamp/wrap state

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java`
- `src/main/java/net/vulkanmod/mixin/texture/MAbstractTexture.java`
- `src/main/java/net/vulkanmod/mixin/texture/MSimpleTexture.java`
- `src/main/java/net/vulkanmod/mixin/render/RenderTargetMixin.java`

`VulkanImage` caches sampler handles using a key derived from blur + mipmap only. Clamp/repeat mode is omitted even though it is part of the native sampler state.

Additionally, `MAbstractTexture.setFilter(...)` calls:

`updateTextureSampler(blur, false, mipmap)`

and therefore hard-codes repeat wrapping while changing filtering, instead of preserving the texture's clamp state.

This is reachable because texture creation can request clamp while later filter changes use the same image wrapper.

Repair direction: make wrap/clamp part of persistent texture/sampler state and sampler-cache identity; changing filter must not silently mutate wrapping.

## A4 — High recoverability/data-safety risk: Minecraft emergency save is explicitly suppressed

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/MinecraftMixin.java`

The mixin redirects the `Minecraft.run()` invocation of `Minecraft.emergencySave()` to an empty method.

No compensating emergency-save implementation has been found in the audited tree.

This can turn some otherwise recoverable client/render failures into avoidable loss of unsaved integrated-world state. The inherited behavior must not be retained merely because older VulkanMod code did it.

Repair direction: restore the vanilla/Forge emergency-save contract unless concrete current evidence proves it is unsafe under this renderer, in which case provide the smallest safe equivalent and document why.

## A5 — Rendering correctness: automatic sequential indices overflow 16-bit index space

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/memory/AutoIndexBuffer.java`
- `src/main/java/net/vulkanmod/vulkan/Drawer.java`
- `src/main/java/net/vulkanmod/render/VBO.java`
- terrain paths that call `Renderer.getDrawer().getQuadsIndexBuffer().checkCapacity(...)`

`AutoIndexBuffer` always emits Java `short` indices and its consumers bind the result as `VK_INDEX_TYPE_UINT16`.

The shared quad buffer is initially created for 100,000 vertices and can grow further. Once a generated vertex index exceeds 65,535, the cast to `short` wraps and the draw references the wrong vertex.

This is a latent geometry-corruption bug for sufficiently large single sequential draws.

Repair direction: enforce a proven <=65,535-vertex limit or select/generate 32-bit indices when the draw exceeds the UINT16 range. Do not merely enlarge the existing UINT16 buffer.

## A6 — Robustness: mapped-memory paths ignore VMA map failure and one path is not exception-safe

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/memory/MemoryManager.java`

The mapping helpers call `vmaMapMemory(...)` without checking the returned `VkResult`.

`MapAndCopy(...)` also performs the consumer callback before `vmaUnmapMemory(...)` without a `finally`, so a callback exception skips the unmap.

The ordinary `Map(...)` path can expose an invalid/uninitialized mapped pointer if mapping fails.

Repair direction: check every mapping result before dereference and guarantee unmap/temporary native cleanup with structured `finally` ownership.

## A7 — Low: process-lifetime native allocations are not consistently freed

Examples observed:

- `Synchronization` native `LongBuffer`;
- static native buffers in `Drawer`;
- `MappedBuffer` allocations;
- Vulkan physical-device/property structures and feature structures with no obvious final free.

These are small compared with terrain allocations and are not the current priority, but they reveal inconsistent explicit-native ownership.

Repair direction: centralize or document process-lifetime ownership and free resources during Vulkan teardown where practical.

# Findings still being verified

Do **not** turn these into committed fixes without completing the trace.

## P1 — probable packed-color corruption in `BufferBuilderM.fastColor`

`src/main/java/net/vulkanmod/mixin/render/vertex/BufferBuilderM.java` contains a fast color method that passes a packed integer color to `MemoryUtil.memPutFloat(...)`.

The expected representation appears to be the raw 32-bit packed color bits, which would require `memPutInt(...)`; converting the integer numerically to float would write unrelated bytes.

Reachability through the non-`NEW_ENTITY` fast path still needs to be proven before promotion to a confirmed production finding.

## P2 — frame-slot vs swapchain-image semaphore indexing

`Renderer` indexes acquire fences/command buffers with `currentFrame`, while render-finished semaphores are selected with swapchain `imageIndex`.

This is unusual but not yet proven incorrect. Binary-semaphore reuse and presentation/acquisition guarantees need to be checked against the exact Vulkan contract before calling it a bug.

## P3 — non-transactional queue/command-pool error paths

Several queue and command-pool helpers can strand command-buffer bookkeeping or partially-created sync objects when Vulkan calls fail mid-operation.

These are credible robustness problems, but severity/reachability under recoverable (non-device-lost) failures still needs to be separated from device-loss-only behavior.

## P4 — legacy GL compatibility semantic holes

Some compatibility entry points remain no-ops or placeholders, including portions of texture parameters, active texture, pixel-store, blend equation, framebuffer/renderbuffer emulation, and raw GL11 wrappers.

Do not blanket-fix all TODOs. Determine which contracts are exercised by Forge/Create Chronicles callers and implement only the semantically required Vulkan behavior. Existing focused compatibility fixes demonstrate the preferred pattern.

# Investigated concerns that should NOT be reopened without new evidence

## C1 — duplicate descriptor-pool type entries are legal

The pipeline descriptor-pool builder can create multiple `VkDescriptorPoolSize` entries with the same descriptor type.

Vulkan permits this; counts for matching types are additive. This looked suspicious but is not itself a defect.

## C2 — terrain upload "ready after submission" is intentional

`AreaUploadManager` marks terrain uploads CPU-visible after helper submission rather than waiting for device completion.

The relevant copy and consuming GPU work are kept on the same graphics queue. Queue order supplies copy -> consume ordering, while frame fences own staging/command-buffer retirement.

Do not add a render-thread fence wait merely because "ready" does not mean device-idle.

## C3 — immediate CPU segment reuse is not automatically a GPU use-after-free

`DrawParameters.reset()` can return a suballocation to the CPU-side area allocator before older submitted draws finish.

The underlying Vulkan buffer remains alive, and replacement uploads/draws use the same graphics queue after the old draws. Queue order prevents overwrite-before-old-read for the paths inspected.

A finding would require a path that reuses those bytes without the established queue ordering or destroys the backing buffer too early.

## C4 — old performance-audit transfer-family finding is stale

Older documentation warned that exclusive device-local buffers were copied on a dedicated transfer queue without queue-family ownership transfer.

Current `MemoryManager.createBuffer(...)` uses concurrent family sharing for transfer-capable buffers when graphics and transfer families differ, and hot persistent terrain overwrites have additionally moved onto the graphics queue for ordering.

Do not copy the old finding into the current audit without re-establishing a violating resource path.

# Important supporting observations

- `createSyncObjects()` and several other native creation paths remain only partially transactional if later object creation fails.
- `Synchronization.getWaitSemaphores(...)` consumes bookkeeping before the main submit succeeds; failure atomicity should be reviewed.
- `Vulkan.cleanUp()` contains broad cleanup exception handling that can hide partial teardown failure.
- The legacy compatibility surface intentionally contains unsupported/no-op GL behavior; Create Chronicles compatibility has been fixed incrementally from concrete call-site evidence.
- The current build/distribution checks are comparatively strong: the audit has not found a packaging defect equivalent to the historical LWJGL module/JarJar failures.
- Recent GPU-terrain code generally contains substantially stronger generation/ownership checks than older renderer utilities. No Phase 7 architectural showstopper has been confirmed yet.

# Remaining audit work

A new audit session should continue from here rather than restarting.

Priority order:

1. **Finish GPU-terrain completion/output ownership**
   - `GpuTerrainSectionMesherBridge`
   - `GpuTerrainOutputStore`
   - `GpuTerrainAppendRebuildTransaction`
   - `GpuTerrainDrawHandoff`
   - `RegionDrawBatch`
   - candidate/model/voxel GPU stores
   - helper completion tokens, descriptor-slot return, overflow/failure cleanup
   - generation mismatch and retained-visible-generation behavior.

2. **Finish terrain worker/publication lifecycle**
   - remaining `ChunkTask` and `TaskDispatcher` paths;
   - transparency-sort publication;
   - cancellation after native `UploadBuffer` ownership transfer;
   - world unload, render-distance reconstruction, resource reload and stale callbacks;
   - lock-order review across `RenderSection`, `ChunkArea`, `AreaUploadManager` and GPU stores.

3. **Shader / Java-GPU ABI audit**
   - compute shaders for meshing, selection, sparse lighting and model tables;
   - Java strides, offsets, counts and GLSL layouts;
   - indirect command ABI;
   - barrier stage/access pairs;
   - integer range/overflow bounds;
   - output capacity and readback header/payload sizes.

4. **Mixin / Forge compatibility audit**
   - complete `vulkanmod.mixins.json` surface;
   - `@Overwrite` semantics relative to Forge-patched 1.20.1;
   - optional-mod mixin applicability and raw-GL interception;
   - state save/restore around Immersive Portals recursion and post-processing;
   - distinguish deliberate substitutions from silent semantic holes.

5. **Test-quality audit**
   - ensure smokes assert contracts rather than internal implementation;
   - identify important exception/teardown paths with no oracle;
   - verify property-gated `src/main` smoke code is inert in ordinary production;
   - assess whether validation layers can expose A1 and similar lifetime violations in CI.

6. **General legacy pass**
   - configuration persistence/threading;
   - profiler/debug code;
   - remaining native-memory ownership;
   - integer arithmetic and bounds on native copies/allocations;
   - dead/superseded code and production TODOs.

# Continuation instruction

The next session should:

1. follow `AGENTS.md` Section 3A;
2. verify live `forge-1.20.1` HEAD and inspect only the delta from `5e17f013`;
3. read this checkpoint;
4. continue the full adversarial audit from "Remaining audit work";
5. do not re-audit cleared concerns unless the live delta changes their contracts;
6. surface newly confirmed defects as they are proved;
7. when the audit is complete, convert this checkpoint into/follow it with a final audit report;
8. repair significant confirmed findings in small logically grouped changes with focused validation and CI before asking for the pending RX 6900 XT functional test.

The previously documented RX/Create Chronicles test remains valuable, but the user intentionally inserted this full audit before performing it. Finish the audit and resolve significant findings first unless a new live blocker makes hardware evidence necessary to distinguish hypotheses.
