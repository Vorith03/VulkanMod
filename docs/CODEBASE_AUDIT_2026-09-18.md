# Full adversarial codebase audit — 2026-09-18

This is the final report for the full adversarial audit inserted before the next RX 6900 XT / Create Chronicles functional test.

The review deliberately treated green CI, inherited VulkanMod behavior, and existing smoke coverage as evidence rather than proof. Unusual code was not promoted to a finding unless ownership, lifetime, API, or data-layout tracing established a violated contract.

## Audited snapshot

- Repository: `Vorith03/VulkanMod`
- Branch: `forge-1.20.1`
- Live documentation HEAD at audit completion: `6f152a2312caa3b9c81c02aba8dcf513ee3ee486` (`docs: checkpoint full codebase audit [skip ci]`)
- Executable code under audit: `8e553bb6991c16f86098227aa35639189d4e4aaf` (`compat: preserve portal reload guards`)
- The commits after `8e553bb6` through `6f152a23` are documentation-only; no production code changed while the audit was running.
- Last fully green executable validation checkpoint: CI #690 / run `35385931592`.

If live executable code changes after this report, inspect the delta before applying these conclusions mechanically.

## Executive summary

The audit found no reason to abandon the current Forge renderer architecture and no Phase 7 GPU-terrain architectural showstopper. The recent GPU-terrain generation, staging, and fail-closed ownership code is generally stronger than older renderer utilities.

However, the tree is **not ready to go directly to the planned hardware correctness test**. Significant defects remain in four areas:

1. Vulkan/native lifetime and failure handling;
2. terrain worker snapshot consistency;
3. generic/Forge rendering compatibility contracts;
4. CI fixture isolation and missing contract oracles.

The most important lesson from the compatibility pass is that successful vanilla rendering and targeted mod smokes do not prove Forge compatibility. Several whole-method replacements erase Forge-added behavior inside the overwritten method while still looking correct under vanilla-oriented tests.

The findings below are ordered by identifier, not repair priority.

# Confirmed findings

## A1 — High: framebuffer teardown violates attachment-view lifetime ordering

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/framebuffer/Framebuffer.java`
- `src/main/java/net/vulkanmod/vulkan/memory/MemoryManager.java`
- `src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java`

`Framebuffer.cleanUp()` schedules attachment images/views for deferred retirement and separately queues `VkFramebuffer` destruction as a frame operation.

`MemoryManager.initFrame()` frees deferred buffers/images before running frame operations. At the safe frame boundary this can therefore destroy an attachment view while a framebuffer referencing that view is still alive.

The fence makes prior GPU use complete; it does not waive Vulkan object-parent/dependency lifetime rules.

**Repair direction:** retire the framebuffer before its attachment views/images through one explicitly ordered retirement unit.

## A2 — High: `VulkanImage` creation can swallow the primary failure and continue with invalid handles

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java`
- `src/main/java/net/vulkanmod/vulkan/memory/MemoryManager.java`

The legacy private image-creation path catches `Exception`, prints it, and returns normally. Higher-level construction can then proceed into image-view/sampler creation around zero or invalid image state.

Texture/depth factory construction is also not transactional if a later view or sampler step fails after image allocation succeeds.

**Repair direction:** fail fast on the original Vulkan/VMA result and clean up partial image/view/sampler construction transactionally.

## A3 — Medium: sampler identity and filter updates lose wrap/clamp state

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java`
- `src/main/java/net/vulkanmod/mixin/texture/MAbstractTexture.java`
- `src/main/java/net/vulkanmod/mixin/texture/MSimpleTexture.java`

Sampler cache identity is derived from blur + mipmap but omits clamp/repeat state. `MAbstractTexture.setFilter(...)` additionally calls `updateTextureSampler(blur, false, mipmap)`, silently forcing repeat wrapping while changing filter state.

**Repair direction:** retain wrap state as part of the texture wrapper and include it in sampler identity.

## A4 — High data-safety risk: Minecraft emergency save is suppressed

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/MinecraftMixin.java`

The `Minecraft.run()` call to `emergencySave()` is redirected to an empty method. No compensating emergency-save contract was found.

A renderer/client failure that vanilla/Forge could otherwise save around can therefore lose unsaved integrated-world state.

**Repair direction:** restore the vanilla/Forge emergency-save behavior unless current evidence proves a specific Vulkan teardown conflict, in which case replace it with the smallest safe equivalent.

## A5 — High rendering correctness: automatic sequential indices can exceed UINT16

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/memory/AutoIndexBuffer.java`
- `src/main/java/net/vulkanmod/vulkan/Drawer.java`
- `src/main/java/net/vulkanmod/render/VBO.java`

Automatic indices are always generated as Java `short` and bound as `VK_INDEX_TYPE_UINT16`, while the shared quad buffer can represent/grow beyond 65,535 vertices.

Past that range generated indices wrap and reference incorrect vertices.

**Repair direction:** enforce a proven UINT16 vertex ceiling or select/generate a UINT32 index path for larger draws.

## A6 — Medium robustness: mapped-memory paths ignore VMA mapping failure and one path can skip unmap

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/memory/MemoryManager.java`

Mapping helpers do not check the `vmaMapMemory(...)` result before dereferencing the returned pointer. `MapAndCopy(...)` also invokes user/copy work before unmapping without a `finally`, so an exception can strand a mapping.

**Repair direction:** check every VMA result and make unmap/temporary ownership structured and exception-safe.

## A7 — Low: small process-lifetime native allocations remain unowned

Examples include:

- `Synchronization` native `LongBuffer`;
- static buffers in `Drawer`;
- `MappedBuffer(int)` allocations;
- some physical-device/property/feature structures.

These are small relative to terrain and reload allocations, but explicit native ownership is inconsistent.

**Repair direction:** centralize/document true process-lifetime ownership and release it during Vulkan teardown where practical.

## A8 — High: production GPU-terrain mesher has no complete lifecycle owner and construction is not failure-atomic

Relevant code:

- `src/main/java/net/vulkanmod/render/chunk/voxel/GpuTerrainSectionMesher.java`
- `src/main/java/net/vulkanmod/render/chunk/GpuTerrainSectionMesherBridge.java`
- `src/main/java/net/vulkanmod/vulkan/Vulkan.java`

The mesher constructor creates descriptor resources, pipeline layout, and pipeline sequentially without transactional cleanup. A later construction failure leaks earlier native objects.

The bridge also records initialization as attempted, so a failed partial construction is neither retried nor recoverable through a later `close()`.

More importantly, no production teardown owner was found for a successfully created mesher before `Device.destroy()`. Its descriptor pool/layout and compute pipeline can therefore remain live into device destruction.

The section-selection shared pipeline/store use explicit failure cleanup and demonstrate the stronger pattern that should be copied here.

**Repair direction:** give the production mesher a single explicit owner, make initialization transactional, allow sensible retry/fail-closed behavior, and destroy it before device teardown.

## A9 — High concurrency correctness: terrain workers can mix a reassigned section origin with an older captured region

Relevant code:

- `src/main/java/net/vulkanmod/render/chunk/RenderSection.java`
- `src/main/java/net/vulkanmod/render/chunk/build/ChunkTask.java`
- `src/main/java/net/vulkanmod/render/chunk/SectionGrid.java`

A `BuildTask` captures a `RenderChunkRegion`, but compilation later derives its section origin from mutable `RenderSection.xOffset/yOffset/zOffset`.

`RenderSection.setOrigin(...)` cancels the previous task and then mutates those offsets. A worker that has already crossed its cancellation check can enter/continue compilation after reassignment and combine the **new** section origin with the **old** captured region snapshot.

That violates task snapshot consistency and can produce wrong region lookups or worker failure during rapid section-ring reuse.

**Repair direction:** capture immutable origin/position together with the region when constructing the task and compile exclusively from that snapshot. Cancellation should be a publication/relevance gate, not the mechanism that makes mutable inputs safe.

## A10 — High rendering/memory correctness: `BufferBuilderM` fast path corrupts non-`NEW_ENTITY` formats

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/vertex/BufferBuilderM.java`
- `src/main/java/net/vulkanmod/mixin/render/model/ModelPartM.java`
- `src/main/java/net/vulkanmod/mixin/vertex/VertexMultiConsumersM.java`

The extended packed-vertex method has one correct special case for `DefaultVertexFormat.NEW_ENTITY`. Its fallback then writes all attributes using hard-coded NEW_ENTITY offsets even though the current format is arbitrary.

It also writes packed color with `MemoryUtil.memPutFloat(ptr + 12, packedColor)` instead of preserving the packed integer bits.

This path is reachable through multi-consumer model rendering, where one model vertex can fan out to the ordinary entity consumer plus a differently formatted consumer such as a glint `POSITION_TEX` path. The fallback can therefore write incorrect fields and write beyond the intended vertex stride.

**Repair direction:** never use one fixed packed layout for arbitrary formats. Keep a validated specialized fast path only for exact known layouts and fall back to the ordinary `VertexConsumer` element API otherwise.

## A11 — High Forge compatibility: `VertexConsumer.putBulkData` overwrite drops Forge baked-light, baked-normal, and alpha semantics

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/vertex/VertexConsumerM.java`
- Forge 1.20.1 patch: `patches/minecraft/com/mojang/blaze3d/vertex/VertexConsumer.java.patch`
- Forge extension: `net.minecraftforge.client.extensions.IForgeVertexConsumer`

Forge patches this exact method to:

- merge baked light via `applyBakedLighting(...)`;
- apply per-quad baked normals via `applyBakedNormals(...)`;
- preserve the alpha parameter/per-vertex alpha behavior.

VulkanMod overwrites the whole method from vanilla-style logic and omits those Forge calls.

Custom/emissive/model-data-driven baked quads can therefore render with incorrect light, normals, or alpha.

**Repair direction:** preserve Forge's current patched contract in the Vulkan implementation rather than copying vanilla semantics.

## A12 — High mod compatibility: generic `VertexConsumer` paths assume VulkanMod's private interface

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/model/ModelPartM.java`
- `src/main/java/net/vulkanmod/mixin/vertex/SpriteCoordinateExpanderM.java`
- `src/main/java/net/vulkanmod/mixin/vertex/VertexMultiConsumersM.java`
- `src/main/java/net/vulkanmod/interfaces/ExtendedVertexBuilder.java`

The model compile path casts the supplied `VertexConsumer` to `ExtendedVertexBuilder`. Wrapper mixins similarly cast arbitrary delegates.

Neither vanilla nor Forge requires third-party `VertexConsumer` implementations to implement VulkanMod's private interface. A mod-provided consumer can therefore fail with `ClassCastException`.

**Repair direction:** use `instanceof ExtendedVertexBuilder` only as an optional fast path and preserve the ordinary `VertexConsumer.vertex(...)` contract as the universal fallback.

## A13 — High Forge compatibility: custom shader registration/reload lifecycle is not preserved

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/GameRendererMixin.java`
- `src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java`
- Forge 1.20.1 patches for `GameRenderer` and `ShaderInstance`
- Forge `RegisterShadersEvent`

`GameRendererMixin.reloadShaders(...)` cancels the Forge-patched method at HEAD and manually builds a hard-coded shader list. It does not post Forge's `RegisterShadersEvent`.

This removes the supported registration point for mod core shaders. The existing narrow `BufferUploader` fallback for missing NEW_ENTITY shaders masks one symptom but does not preserve the API.

The same replacement path schedules every old shader for close but deliberately leaves `shutdownShaders()/map clear` disabled. Any old key not overwritten by the hard-coded list can remain in `this.shaders` while pointing at a shader that has just been scheduled for close.

Forge also adds a public `ShaderInstance(ResourceProvider, ResourceLocation, VertexFormat)` constructor for namespaced shaders. VulkanMod's pipeline-creation injection has the String-constructor signature and its legacy resource loading assumes Minecraft-style paths. Direct namespaced constructor use is therefore not a supported Vulkan path.

**Repair direction:** preserve the Forge shader registration event and map lifecycle first. Then make Vulkan pipeline construction namespace-correct for both Forge constructors instead of adding more format-specific fallbacks.

## A14 — Medium Forge compatibility: `RenderTarget.enableStencil()` can report success without a stencil attachment

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/RenderTargetMixin.java`
- `src/main/java/net/vulkanmod/vulkan/framebuffer/Framebuffer.java`
- `src/main/java/net/vulkanmod/vulkan/Device.java`
- Forge 1.20.1 `RenderTarget` patch.

Forge's `enableStencil()` sets a private stencil flag and calls `resize()`, expecting the recreated depth target to use a depth+stencil format.

VulkanMod overwrites `resize()` but never consults Forge's stencil flag. It always creates the ordinary framebuffer depth attachment using `Device.findDepthFormat()`, which prefers `VK_FORMAT_D32_SFLOAT` before stencil-capable formats.

The inherited Forge `isStencilEnabled()` can therefore return true while no stencil aspect exists.

**Repair direction:** either implement the Forge stencil contract with an explicitly stencil-capable format and correct aspect handling, or fail the capability request explicitly rather than reporting a false success.

**Post-audit runtime update (2026-09-19):** the audit finding itself remains valid, but real Create Chronicles evidence resolved the implementation choice. Build #711 reached Create 0.5.1.j's `UIRenderHelper$CustomRenderTarget.create() -> RenderTarget.enableStencil()` startup path and failed because the then-current repair explicitly rejected stencil targets. Off-screen Vulkan depth/stencil support was implemented in `8a9a374bafb`, combined depth-sampling/attachment image views were corrected in `f2323d254473` (CI #713), and `4abc931918a2` redirects Create's direct `GL_STENCIL_TEST` toggles at the actual `StencilElement` call sites. CI #715 passes the exact Create 0.5.1.j fixture. This is contradictory post-audit runtime evidence refining the repair choice, not a reason to rerun the audit.

## A15 — Medium Forge compatibility: block-layer `RenderLevelStageEvent` callbacks are removed

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/LevelRendererMixin.java`
- Forge 1.20.1 `LevelRenderer` patch
- `net.minecraftforge.client.ForgeHooksClient.dispatchRenderStage(...)`

Forge dispatches the render-stage event for recognized terrain `RenderType` layers at the end of `LevelRenderer.renderChunkLayer(...)`.

VulkanMod overwrites that whole method with a call into `WorldRenderer.renderSectionLayer(...)` and never reproduces the Forge dispatch.

Mods listening for the solid/cutout/translucent/tripwire block-layer stages therefore lose a supported Forge extension point.

**Repair direction:** dispatch the Forge render stage after VulkanMod completes the equivalent layer draw, preserving the current camera/frustum/projection semantics.

## A16 — Medium/high native memory: shader compilation leaks shaderc/SPIR-V resources on every pipeline build/reload

Relevant code:

- `src/main/java/net/vulkanmod/vulkan/shader/SPIRVUtils.java`
- `src/main/java/net/vulkanmod/vulkan/shader/Pipeline.java`
- `src/main/java/net/vulkanmod/vulkan/shader/GraphicsPipeline.java`

`compileShader(...)` creates `shaderc_compile_options_t` for every compile and never calls `shaderc_compile_options_release(...)`.

The returned `SPIRV.free()` has `shaderc_result_release(handle)` commented out and only nulls its Java `ByteBuffer` reference. Pipeline construction creates Vulkan shader modules from those results but never frees the SPIR-V wrappers afterward.

The alternate native-buffer read path also allocates with `memAlloc` while the same `free()` does not release that allocation.

Unlike A7, this is reload-amplified: shader/resource reloads compile many shaders and leak native allocations each time.

**Repair direction:** give `SPIRV` explicit origin-aware ownership, release compile options in a `finally`, release shaderc results/native byte buffers after module creation, and release the long-lived compiler during teardown if retained globally.

## A17 — Medium compatibility debt: active texture state is not implemented coherently

Relevant code:

- `src/main/java/net/vulkanmod/mixin/render/RenderSystemMixin.java`
- `src/main/java/net/vulkanmod/mixin/render/GlStateManagerM.java`
- `src/main/java/net/vulkanmod/vulkan/texture/VTextureSelector.java`

`RenderSystem.activeTexture(int)` is overwritten as an empty method. `GlStateManager._bindTexture(int)` binds the generic Vulkan texture path, while `VTextureSelector.uploadSubTexture(...)` selects the destination from its own `activeTexture` field.

Specific vanilla paths such as overlay/lightmap install bespoke workarounds, but the public RenderSystem/GlStateManager active-unit contract itself remains inconsistent.

This confirms one concrete part of the checkpoint's broader legacy-GL concern. The remaining raw-GL TODOs should still be fixed only when a real Forge/mod call contract requires them.

**Repair direction:** centralize texture-unit translation and make active-unit + bind/upload semantics coherent; do not add isolated caller workarounds indefinitely.

## A18 — Medium test-infrastructure: compatibility smoke fixtures are not isolated

Relevant code:

- `.github/workflows/build.yml`
- `scripts/ci/vulkan-smoke.sh`

Two state leaks exist across workflow steps:

1. `no-splash` edits `run/config/fml.toml` to disable Forge's early window and does not restore it. Every later smoke in the same job therefore runs under the no-splash mode unless it explicitly changes the file.
2. Chat Heads and Flywheel modes append CI-only repositories/dependencies to `build.gradle` without a restore trap. The later Flywheel step inherits the Chat Heads dependency even after deleting a Chat Heads JAR from `run/mods`.

The dedicated FTB Library, Pick Up Notifier, Immersive Portals, and Distant Horizons scripts correctly back up/restore `build.gradle`; the generic script should follow that pattern.

**Repair direction:** make every fixture hermetic: back up/restore modified files and explicitly define the startup mode/mod set for each smoke.

## A19 — Low/medium configuration persistence and recovery gaps

Relevant code:

- `src/main/java/net/vulkanmod/config/Config.java`
- `src/main/java/net/vulkanmod/config/Options.java`
- `src/main/java/net/vulkanmod/config/OptionScreenV.java`

The custom options screen mutates vanilla Minecraft `OptionInstance` values but `Options.applyOptions(...)` only writes `vulkanmod_settings.json`; it does not call Minecraft `Options.save()`. Vanilla options changed through this screen can therefore fail to persist across restart.

The VulkanMod config file is also written directly rather than atomically, while `Config.load(...)` catches only `IOException`; malformed/truncated JSON can surface as an unchecked parse failure at startup.

**Repair direction:** persist Minecraft options through the vanilla save contract and make VulkanMod config writes atomic/recoverable with parse-error handling.

# Concerns investigated and cleared

These should not be reopened without new code or contradictory runtime evidence.

## C1 — duplicate descriptor-pool type entries are legal

Vulkan permits multiple pool-size entries of the same descriptor type; counts are additive.

## C2 — terrain upload readiness after queue submission is intentional

Terrain upload and consuming GPU work share graphics-queue ordering. CPU-visible "ready" after submission is not device completion, but the inspected paths do not require device completion.

## C3 — immediate area-segment reuse is not automatically a GPU UAF

The backing buffer stays alive and replacement uploads/draws are ordered on the same graphics queue. A finding would require a path outside that ordering contract.

## C4 — the old dedicated-transfer-family ownership warning is stale

Current transfer-capable buffers use concurrent sharing when graphics and transfer families differ, and hot terrain overwrites use the graphics queue.

## C5 — frame-slot acquire sync vs image-index present sync is correct

`imageAvailable` semaphores and in-flight fences are frame-slot indexed; `renderFinished` semaphores are acquired-image indexed. That image-indexed present semaphore lifetime is the safe/recommended binary-semaphore reuse pattern because reacquisition establishes that presentation has released the image.

The old checkpoint candidate P2 is closed.

## C6 — GPU-terrain completion-token and descriptor-slot ownership held up

The production completion token guards exactly-once retirement/publication. Helper-fence polling and frame-fence fallback do not double-publish or double-return a descriptor slot.

## C7 — worker shutdown does not race pending native upload buffers

`TaskDispatcher.stopThreads()` joins producers before clearing publication/native results.

## C8 — in-flight GPU output reservations protect region reuse

A GPU output reservation remains counted in `AreaBuffer.used`. Region reuse therefore sees live geometry and goes through safe backing-buffer retirement rather than treating an in-flight slice as empty.

## C9 — Java/GLSL GPU-terrain ABIs are internally consistent

The audit matched:

- voxel v4 headers/planes/halo;
- sparse-lighting v1 offsets/counts;
- model-table v1 layout;
- section-mesher result layout;
- region candidate records;
- five-word indexed indirect command ABI;
- packed section in `firstInstance` -> `gl_InstanceIndex`;
- readback sizes;
- face/output capacity bounds;
- transfer/compute/vertex/indirect/host barrier stage/access pairs.

No silent production stride/offset mismatch was found.

## C10 — stale CPU publication after a new dirty mark was not promoted

An older completed CPU result can wait in the publication queue while a section is dirty, but the inspected state machine keeps the section dirty and requires a subsequent rebuild. This is temporary stale-fallback behavior, not by itself an ownership or generation-safety violation.

## C11 — Immersive Portals cull-state shims do change Vulkan pipeline state

The initial concern that IP's cull redirects were no-ops was incorrect. `RenderSystem.enableCull/disableCull` updates `VRenderSystem.cull`, and `PipelineState` includes that value in pipeline selection.

# Remaining robustness debt not promoted to a top-level defect

These areas are still imperfect but did not justify a stronger finding under the current evidence:

- queue/command-pool error paths are not consistently transactional; most inspected failures immediately escalate out of rendering rather than attempting unsafe recovery;
- `Vulkan.cleanUp()` has broad exception handling that can hide partial teardown problems;
- several native constructors outside the mesher are only partially transactional;
- profiler/debug code contains stale/commented branches and weak stack-balance assertions, but no new concrete normal-path correctness failure was established;
- the legacy GL compatibility layer still contains TODO/no-op entry points. Fix call-site-proven contracts, not every historical OpenGL function by default;
- `GlTexture` has some int-sized allocation arithmetic that could fail poorly for absurd mod-supplied dimensions, but bounded ordinary texture paths use the safer `TextureUploadLayout` long arithmetic.

# Test-quality conclusions

Current CI is strong at several deliberately tested contracts:

- Vulkan startup with/without Forge early splash;
- GPU-indirect synchronization validation;
- post-processing including a real pixel oracle;
- depth-chain synchronization;
- screenshot readback;
- targeted compatibility regressions for FTB Library, Pick Up Notifier, Immersive Portals, Distant Horizons, Crash Assistant, Chat Heads, and Flywheel;
- GPU-terrain generation/ownership/readback barriers through focused smokes.

But green CI did not cover the public Forge contracts that failed in this audit. Add focused oracles for at least:

- a synthetic `RegisterShadersEvent` custom namespaced shader and reload;
- `RenderLevelStageEvent` block-layer delivery;
- a non-VulkanMod custom `VertexConsumer` plus Forge baked light/normal/alpha behavior;
- `RenderTarget.enableStencil()/isStencilEnabled()` matching a real stencil-capable attachment;
- exact vertex bytes for the multi-consumer glint/non-NEW_ENTITY path;
- failure-injection/transactional cleanup for image and compute-pipeline construction;
- framebuffer close followed by forced deferred retirement under validation layers;
- repeated shader reload native-memory accounting;
- hermetic smoke-fixture checks that detect leaked build/config mutations.

# Repair sequencing

Do not repair all findings in one giant change. Use focused commits and CI.

A practical sequence before the next user hardware run is:

1. **Normal-path rendering/Forge contracts**
   - A10, A11, A12;
   - A13;
   - A15;
   - A14.
   These can affect ordinary modded rendering without requiring exceptional Vulkan failures.

2. **Terrain/runtime correctness and native lifetime**
   - A9;
   - A8;
   - A1;
   - A2;
   - A16;
   - A6.

3. **General rendering correctness**
   - A5;
   - A3;
   - A4 should be restored no later than this group and may be fixed earlier because it is small and data-safety relevant.

4. **Validation infrastructure**
   - A18 plus the missing Forge/failure-path oracles above.

5. **Lower-priority cleanup**
   - A17;
   - A19;
   - A7 and remaining transactional/legacy debt.

Reorder within a group when one fix naturally supplies the regression oracle for another, but do not use grouping as a reason to create oversized commits.

# Hardware-test boundary

The previously planned RX 6900 XT / RADV Create Chronicles run remains valuable, but the audit intentionally precedes it.

Do **not** use that hardware run as a substitute for fixing defects that are already proven from code. Resolve the significant normal-path and lifetime findings with focused CI first. Request new user-machine evidence only when the repaired tree reaches a question that CI/software Vulkan cannot answer.

The existing user-side observations and previous failed Immersive Portals attempt remain valid evidence; do not repeat tests solely because this audit occurred.

# Continuation instruction

The audit is complete. A new session should:

1. follow `AGENTS.md` Section 3A and inspect the live delta from executable audit SHA `8e553bb6`;
2. read this report rather than restarting codebase archaeology;
3. begin the repair sequence above in small logical changes;
4. add the missing contract-level regression tests alongside the fixes;
5. update `AGENT_STATUS.md` when the repaired safety/compatibility boundary materially changes;
6. resume the pending RX/Create Chronicles test only after significant proven findings are resolved and CI is green.
