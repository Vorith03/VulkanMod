# VulkanMod Forge 1.20.1 — Agent Status

This file is the **living checkpoint**, not the source of truth for live repository state. Always inspect the current `forge-1.20.1` HEAD and latest CI first. If this file disagrees with Git or CI, Git/CI wins.

## Required planning documents

Use these together:

- `AGENTS.md` — development protocol and evidence rules;
- `ROADMAP.md` — canonical phase order, exit gates, and progress-report format;
- `AGENT_STATUS.md` — current verified checkpoint and immediate work item.

Future agents should not silently invent a new major workstream. Start from the active roadmap gate unless live evidence or the user requires a temporary detour.

## Current continuation checkpoint — 2026-09-12

- Live branch HEAD inspected: `18e9247e5c85fe2151424f7f1b29fd4fbe6fc0b7`; CI **#306 green**. Build job 102786917792 and its logs confirm distributable verification and all configured startup/post-chain/depth/screenshot/Crash Assistant/Flywheel smoke gates passed.
- Highest demonstrated milestone: **6 — playable world**. Phase 3 is complete, including the RX 6900 XT Creeper/Enderman visual result below. Active phase: **4**, **3/8 gates**.
- Active work: current Create/Flywheel visual coverage and the prerequisite full-pack resource-reload/world-reentry retest. The #304 memory guard failure remains the latest runtime evidence; #306 changes no runtime code from #304.
- This continuation reconciles stale retest instructions and adds the confirmed PickupNotifier limitation to `docs/CREATE_CHRONICLES_COMPATIBILITY.md`. No renderer/configuration changes or new runtime success are claimed.
- Next actions: run the documented full-pack sequence with PickupNotifier absent and adequate headroom; inspect latest/debug logs plus visual results; fix any demonstrated defect before advancing gates. No new performance measurements or roadmap reorder.
- User-machine action is required because CI cannot establish visual correctness in the actual RX 6900 XT modpack. See the compatibility document for the exact sequence and evidence to retain.

## Historical checkpoints

The sections below preserve earlier evidence; their phase labels and suggested artifacts describe those dates, not the current continuation checkpoint above.

## Last verified green checkpoint

- Branch: `forge-1.20.1`
- Verified source commit: `9917cacf69848f492c72ac06f2eb591633937a58`
- Verified GitHub Actions run: **#289**
- Highest demonstrated milestone: **Milestone 6 — playable world**
- User RX 6900 XT result: Vulkan gameplay is playable; water rendering fix visually confirmed. Post-effect pixel correctness has not yet been visually confirmed on AMD.

### CI coverage at this checkpoint

[Run #289](https://github.com/Vorith03/VulkanMod/actions/runs/34323274115) passed:

- distributable Forge build / packaging verification;
- Vulkan startup under Lavapipe;
- Vulkan startup with Forge early splash disabled;
- liquid alpha/UV regression smoke;
- terrain region-cache/batching smoke;
- real vanilla `shaders/post/creeper.json` `PostChain.process(...)`, submission and presentation, including the red-input/green-dominant pixel oracle;
- vanilla `shaders/post/transparency.json` depth PostChain: four processes in two submitted frames, initialized auxiliary inputs, MainTarget/offscreen depth copies, and no fallback samplers;
- depth smoke with Khronos validation and synchronization validation enabled: no validation errors or synchronization hazards;
- Vulkan screenshot pixel/readback smoke with synchronization validation: main/offscreen targets, resize, next frame, opaque alpha, orientation, and Forge redirect/cancel/custom feedback;
- Crash Assistant 1.9.7 compatibility;
- Flywheel 0.6 compatibility.

## Roadmap position

- Active phase: **Phase 3 — Core rendering correctness hardening**
- Phase progress at the last verified checkpoint: **10/11 mandatory gates**
- P3.8, P3.10 and P3.11 remain green, now with a real Creeper pixel oracle in addition to execution/submission coverage.
- Next gate: **P3.9 — RX 6900 XT visual post-effect check**

Do not begin the major mesh-shader terrain backend while Phase 3 remains open. The roadmap intentionally places persistent region batching and performance measurement before the optional `VK_EXT_mesh_shader` backend so the project does not debug a new geometry pipeline and a new residency model simultaneously.

## Recent renderer/audit work

Important fixes already landed before this checkpoint include:

- deferred offscreen framebuffer destruction;
- deferred offscreen render-pass destruction;
- deferred post-effect pipeline destruction;
- graphics-pipeline reuse across compatible render passes;
- fullscreen blit MVP/state restoration;
- Vulkan depth target copies for `RenderTarget.copyDepthFrom`;
- render-target clear/filter semantics;
- MainTarget/swapchain sampled-color mapping for post effects;
- depth/stencil layout and synchronization corrections;
- std140-correct 8-byte `vec2` uniform alignment for converted shaders;
- validation-clean upright fullscreen PostPass viewport/scissor state.

Do not redo these investigations without new evidence.

## Bounded depth-post-processing handoff — 2026-09-08

**Completed.** Incoming live HEAD was `fe34ba27df85b86d7c91c235bef4f4983cfe2c72`; CI #281 passed ordinary PostChain execution but failed depth execution. The fix is commit `f9b2b3916da4b719a993b5e721eaf29ad5c22bdc`; full CI #282 is green. A following documentation-only checkpoint records that result. No Phase 4, terrain, mesh-shader, benchmark, or unrelated cleanup work was undertaken.

### Exact failure and related defects

- The observed fatal error was `EffectInstance.clear -> GlStateManager._activeTexture -> GL13C.glActiveTexture`: no current OpenGL context, JVM exit 134. `apply` already used VulkanMod's intercepted `RenderSystem.activeTexture`; `clear` bypassed it. Creeper's single sampler never changed GL's cached unit, hiding the defect; transparency uses twelve samplers. `EffectInstanceM.vulkanmod$clearSamplerUnit` now redirects only that call and preserves vanilla clear bookkeeping.
- CI also reported `DiffuseDepthSampler ... white fallback`. `MainTarget.getDepthTextureId` had no synthetic texture mapping. `MainTargetMixin.getDepthTextureId` now refreshes a stable name against the live swapchain depth attachment.
- `Pipeline.updateDescriptorSet` called `VulkanImage.readOnlyLayout`, which submitted a helper buffer **before** the primary frame containing the attachment writes. `EffectRenderState.prepareTextures` / `RenderTargetManager.prepareSampledImages`, called before binding the effect pipeline in `BufferUploaderM`, now prepare all declared color/depth inputs on the primary command buffer outside the output pass. Resuming preserves its LOAD contents. Descriptor layout remains `SHADER_READ_ONLY_OPTIMAL`, matching the actual image layout; no special depth-only layout feature is needed. Sampling the active output attachment is explicitly unsupported.
- `RenderPass.createRenderPass` now supplies the external attachment dependency needed for repeated LOAD passes when no layout change occurs. The color attachment transition also includes read access for LOAD.
- Generic framebuffer depth images lacked transfer source/destination usage and incorrectly inherited color filtering instead of `depthLinearFiltering`. Depth copies now have the required usage; depth filtering defaults independently to nearest, with clamp-to-edge. Single-level samplers now use nearest mip filtering: LOD zero does not exempt depth formats from [VUID 04770](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdDraw.html).
- `RenderTargetManager.transitionDepth` now transitions both aspects for combined depth/stencil formats while `VkImageCopy` still copies only depth, as required by [VUID 03320](https://docs.vulkan.org/refpages/latest/refpages/source/VkImageMemoryBarrier.html). The shared barriers already covered both early and late fragment tests; those scopes were preserved.
- The old constructor-return harness never initialized auxiliary scene inputs. The smoke now clears them and copies depth through MainTarget -> offscreen and offscreen -> offscreen paths before each process, verifies MainTarget mapping, effect cleanup and submission counts, and repeats within/across frames. The shell gate rejects validation errors, synchronization hazards and white fallback samplers.

### Validation and limits

- Exactly one source-fix CI run: **#282, all build/startup/PostChain/depth/Crash Assistant/Flywheel steps passed**.
- `git diff --check` and shell syntax validation passed. Local `agent-check.sh` was attempted but Gradle 8.1.1 could not download (`Network is unreachable`); CI compiled, reobfuscated and verified the distributable.
- The validation layer was active; its only reported validation messages were unused vertex attribute performance warnings at locations 1/2. These were not suppressed or cleaned up.
- This proves execution, valid bindings/layouts/copies and clean submission under Lavapipe, not pixel correctness on AMD. No pixel oracle, RX 6900 XT visual result, combined depth/stencil device run, or new performance measurement is claimed.

### Next recommended action

The bounded CI defect was resolved at #282. The separately requested screenshot task below subsequently closed P3.10. **P3.9 remains useful:** use the latest green build for an RX 6900 XT visual post-effect check, including depth-sensitive transparency ordering.

## Bounded screenshot/readback handoff — 2026-09-08

Incoming HEAD was `725189cae8358cd2d1297f7eafb31d86a378bdf3` (documentation following green depth CI #282). Source change: `0275668687d0664cd0d9e9c668b81b5af3e9906e`; isolated-harness correction: `ef0c0fc0426a9e058312773bfd529aca9ebb1a12`. A following documentation-only commit records the result.

### Root cause and fix

- F2 is polled after `Renderer.endFrame` presents the swapchain image. The old `MNativeImage.downloadTexture` read that image after ownership had passed to presentation, ignored the requested RenderTarget, assumed `TRANSFER_SRC_OPTIMAL` without a transition, and waited on the null fence returned by the now-unfenced same-queue helper submit. Swapchain/color targets also lacked transfer-source usage.
- `ScreenshotReadback.request` now records on the active primary frame, or queues post-present requests for the next frame. It resolves the target/image/dimensions at recording time. `RenderTargetManager.copyColorToBuffer` ends the active pass, transitions to transfer source, records a tight image-to-buffer copy and transfer-write/host-read barrier, restores layout, and resumes the exact LOAD pass. Main and offscreen color images opt into transfer-source usage; unsupported swapchain surfaces fail explicitly.
- `Renderer.endFrame` completes requested readbacks using the real submitted frame fence. Host-visible/coherent staging is mapped only after completion and then freed. BGRA/RGBA conversion, top-down rows and opaque alpha are explicit. Ordinary frames perform no screenshot copy or screenshot fence wait; the queue is bounded to eight captures.
- `ScreenshotRecorderM` defers `Screenshot._grab`, then replays vanilla saving with the completed image, preserving Forge's event, cancellation, redirected destination and custom message. Cancellation closes the image. `GameRendererScreenshotMixin` similarly preserves vanilla world-icon readiness/crop/write logic while recording the image before later GUI rendering. Direct synchronous `Screenshot.takeScreenshot`, `NativeImage.downloadTexture`, and legacy `VulkanImage.downloadTexture` calls outside the completed-capture scope are explicitly unsupported; consumers should use `Screenshot.grab` or `ScreenshotReadback.request`.

### Evidence and limits

- **Full CI #284 is green**, including the new screenshot gate, both PostChain gates, packaging/startup and Crash Assistant/Flywheel compatibility. Exactly two source/test CI runs were used for this task; the checkpoint-only documentation update skips CI.
- CI #283 built and passed existing startup/color/depth gates, but the new smoke failed its Forge event count. This was a harness lifecycle defect: `MinecraftForge.EVENT_BUS` starts shut down and normally starts in `ClientModLoader.completeModLoading`, after the constructor-return probe. The isolated test now starts/shuts down that bus explicitly and reports callback details. Production readback code did not need a second change.
- The screenshot smoke validates every PNG pixel and dimensions for main BGRA/offscreen RGBA targets, row orientation, opaque alpha, an offscreen resize after recording its copy, and a second frame's fresh contents. It exercises the production `Screenshot.grab` API, Forge redirect/cancel/custom feedback, and explicit rejection of unsafe synchronous entry points. The shell gate enables Khronos synchronization validation and rejects validation errors/hazards; PNGs are uploaded with smoke artifacts.
- Local diff and shell checks passed. Local `agent-check.sh` remains blocked by the unavailable Gradle distribution download; CI provides compilation, reobfuscation and runtime validation.
- No AMD visual result, actual F2 input simulation, world-icon crop runtime result, unsupported-surface run, or new performance measurement is claimed. World-icon mixin application is checked at startup. No Phase 4, terrain, mesh-shader or benchmark work was performed.

### Next recommended action

Stop this repository batch. Phase 3's remaining gate is **P3.9, the RX 6900 XT visual check**. Next three actions: (1) launch the latest green artifact with Minecraft 1.20.1 / Forge 47.3.0 and `earlyWindowControl = false`; (2) inspect ordinary/depth-sensitive post effects, press F2 before/after window resizing, and check the saved PNG plus a new world's icon; (3) return `logs/latest.log` and screenshots, or report the visual result before marking P3.9 complete. Highest milestone remains 6, playable world; no new benchmark evidence or roadmap sequencing change. Start a fresh chat for subsequent work, using the live HEAD/CI and these handoffs.

## Bounded PostChain pixel-correctness handoff — 2026-09-09

**Completed.** Incoming live HEAD was `babbc4ad030252cd26efbd22f8dfa5a62ff76891`. The uploaded CI #287 log showed that the new Creeper pixel oracle reached and submitted the vanilla PostChain successfully but read back opaque black (`rgba=0,0,0,255`) instead of the expected green-dominant result.

### Root cause and fix

- The converted effect shader UBO used VulkanMod's `AlignedStruct`/`Field` layout. `vec2` members were incorrectly assigned four-scalar / 16-byte base alignment. Under std140 a `vec2` has two-scalar / 8-byte base alignment. Creeper's post shaders place `ProjMat`, `InSize`, `OutSize` and then color-convolution fields in that block, so the bad `vec2` alignment displaced `OutSize` and every following uniform. The draw remained Vulkan-valid but the shader consumed the wrong values and produced black.
- Commit `a370b6407cbb3fff8a0deb7af753875da628e221` changes both `vec2` field-construction paths to 8-byte alignment. CI #288 immediately made the previously failing Creeper red-input/green-dominant pixel oracle pass.
- CI #288 then exposed a directly related validation defect in the newly added upright PostPass viewport workaround. `Renderer.setViewport(..., -height)` turned the viewport upright as intended, but the same negative height was passed to `VkRect2D.extent`, wrapping to a huge unsigned value and triggering `VUID-vkCmdSetScissor-offset-00597`. The depth PostChain itself completed and printed its pass marker; the shell gate correctly rejected the validation errors.
- Commit `9917cacf69848f492c72ac06f2eb591633937a58` makes `PostPassM` set only its positive-height Vulkan viewport directly, then restores the output target's valid scissor. This preserves the required screen orientation without feeding a negative extent to `vkCmdSetScissor`.

### Validation and limits

- **Full CI #289 is green.** Packaging, startup/no-splash, Creeper pixel PostChain, depth PostChain with synchronization validation, screenshot readback, Crash Assistant and Flywheel all passed.
- The Creeper gate now checks actual fragment output rather than only construction/submission/presentation. The depth gate confirms the follow-up viewport change is validation-clean under its enabled Khronos synchronization-validation configuration.
- No new RX 6900 XT visual result or comparable performance measurement is claimed. Lavapipe pixel correctness is stronger evidence than the old execution-only smoke, but P3.9 remains the required user-machine visual gate.
- No Phase 4, terrain, mesh-shader, benchmark, or unrelated cleanup work was undertaken.

### Next recommended action

Return to **P3.9**. Use the latest green artifact for the RX 6900 XT visual post-effect check, with particular attention to Creeper-style fullscreen effects and depth-sensitive transparency ordering. Do not mark Phase 3 complete until that visual result is observed.

## Agent iteration rules

Use these together with `AGENTS.md` and `ROADMAP.md`:

- Inspect live branch HEAD and latest CI before editing.
- If an executable checkout is available, run `bash scripts/ci/agent-check.sh` before pushing logically complete source/test-harness edits.
- **Batch mutually dependent edits before pushing.** Do not use GitHub Actions as a substitute for catching obvious compile/mapping mistakes when local/pre-CI validation is available.
- When working through a remote GitHub connector where each Contents API write creates a commit, prefer Git-data blobs/trees plus one ref update so a logical multi-file change creates **one commit and one CI run**.
- Do not push known-broken intermediate states merely to obtain compiler feedback.
- Branch CI uses `cancel-in-progress`; a newer push should supersede an obsolete in-progress run rather than consuming the whole smoke suite twice.
- Keep commits logically scoped, but distinguish logical scope from file count: one feature/test-harness change may correctly touch several files in one commit.
- Update this file at meaningful verified milestones, not after every tiny commit. Record the **last verified green checkpoint** so the file never pretends an unverified HEAD is green.
- Update roadmap checkboxes only after the associated CI/runtime/benchmark evidence is actually observed.
- If a blocker forces a roadmap detour, state the detour explicitly and return to the active gate afterward.

## Useful commands for an executable checkout

Fast source/resource regression pass:

```bash
bash scripts/ci/agent-check.sh
```

Individual Lavapipe gates after installing `xvfb`, `xauth`, Mesa Vulkan drivers, and `vulkan-validationlayers`:

```bash
bash scripts/ci/vulkan-smoke.sh startup
bash scripts/ci/vulkan-smoke.sh no-splash
bash scripts/ci/vulkan-smoke.sh post-chain
bash scripts/ci/vulkan-smoke.sh depth-post-chain
bash scripts/ci/vulkan-smoke.sh screenshot
bash scripts/ci/vulkan-smoke.sh crash-assistant
bash scripts/ci/vulkan-smoke.sh flywheel
```

## Handoff / roadmap-report rule

At the end of a substantial work batch, or when the user asks for progress, use the report format in `ROADMAP.md` and include at minimum:

- current live HEAD;
- commits created;
- latest completed CI result;
- highest completed milestone;
- active roadmap phase and completed/total gate count;
- exact active gate / unresolved issue;
- next three actions;
- whether RX 6900 XT testing is now useful;
- any new comparable performance evidence;
- any roadmap sequencing change (normally `none`).


## Bounded resource-reload native-memory milestone — 2026-09-09

**Implementation verified.** The live source checkpoint for this milestone was
`c7d7f355f6fcfc273298ec0e804b18f0aef902e2`; CI #302 passed after the change. This batch stayed bounded to
the resource-reload native-memory issue.

### Forge TextureAtlas ownership evidence

- The live starting HEAD was the temporary probe commit
  `ac9fb0e5256d8dcb7c6f51bd9854389865e8c559`. Build #301 was green.
- The #301 probe ran `javap -c -p` against the actual Forge 47.3.0
  `forge-1.20.1-47.3.0_mapped_official_1.20.1.jar`, not only a vanilla source
  copy. Its compiled `TextureAtlas.upload(Preparations)` calls
  `clearTextureData()` before copying the replacement regions and before
  installing the new `sprites` and `animatedTextures` lists.
- The compiled `clearTextureData()` walks both currently owned collections
  (the old sprite contents and old animation tickers), then replaces the
  sprite list, ticker list, and name map with empty collections. The Forge
  class also contains `ForgeHooksClient.onTextureStitchedPost`, confirming the
  probe observed the patched class used by this port.
- Therefore the approximately 195 MiB of animated block-atlas CPU images seen
  after a successful F3+T cannot be superseded animated data retained through
  the completed `TextureAtlas` ownership lists. It is current-generation
  animation/source data, or data held by an owner outside those completed atlas
  lists. Current animated `NativeImage` instances remain untouched.

### Implementation and validation

- `ResourceReloadMemoryManager` now creates a generation token only when
  early terrain/resource retirement actually succeeds.
- The allocator purge was removed from the pre-decode path. On a matching
  successful reload completion (`failure == null), diagnostics are logged
  before and after the existing `NativeAllocatorPurger` call, and the purge
  runs before terrain recovery/reconstruction.
- Failed reloads and stale/non-generation completions skip the purge while
  preserving the existing terrain recovery callback. The 4096 MiB system-memory
  floor and all existing RSS gates were unchanged.
- `ResourceReloadGenerationTest` covers non-reload gating, overlap, failed
  completion, success ordering, and stale completion. CI #302 reported
  `Resource reload generation tests passed` and verified
  `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.302-gc7d7f355-all.jar`.
- CI #296, #297, #298, #300, #301, and #302 were green; #299 was the known
  compile-only failure fixed by #300's NativeImage mixin bridge.

### Roadmap report

- **Source HEAD:** `c7d7f355f6fcfc273298ec0e804b18f0aef902e2`
- **Commits created:** `c7d7f355f6fcfc273298ec0e804b18f0aef902e2`
  (implementation); this documentation checkpoint follows it.
- **Latest completed CI:** #302 — green, full build/startup/PostChain/depth/screenshot/
  Crash Assistant/Flywheel suite.
- **Highest completed legacy milestone:** Milestone 6 — playable world.
- **Active phase:** Phase 4 — Create Chronicles compatibility baseline.
- **Phase progress:** 3/8 mandatory gates; no runtime gate was checked off here.
- **Active gate:** full-pack world enter/leave/re-entry and resource-reload survival,
  including F3+T beyond the previous animated-upload failure window.
- **Next three actions:** (1) install the #302 distributable in the target instance
  with `earlyWindowControl = false`; (2) run full-pack F3+T and observe RSS,
  system available memory, NativeImage totals, and animated sprites; (3) continue
  ordinary gameplay for several minutes beyond 82 seconds and return the log/result.
- **RX 6900 XT testing:** useful now; CI is green and the artifact is intended for
  the user's full Create Chronicles test.
- **Performance evidence:** no new comparable benchmark; #296's atlas upload
  batching and #298's allocator-reclaim measurements remain the latest evidence.
- **Roadmap changes:** none.


### RX 6900 XT runtime follow-up — 2026-09-10

- The Build #303 distributable loaded the target full-pack instance with Vulkan active, completed F3+T, and reached the success-only allocator purge. The observed purge reduced process RSS from 12239 MiB to 11704 MiB while NativeImage live bytes remained 1503 MiB, as expected because the purge returns allocator pages rather than closing live images.
- The launcher then reported a native abort at 22:09:57 with `Process crashed with exitcode 6`. The fatal stack is `GL11C.glGetInteger` -> `GlStateManager.getBoundFramebuffer` -> `fuzs.pickupnotifier.client.util.TransparencyBuffer.prepareExtraFramebuffer` from PickupNotifier 8.0.0 during GUI rendering.
- Startup also records a Vulkan `GLFW_NO_API` window. PickupNotifier is therefore making an OpenGL framebuffer query where no current OpenGL context exists. The client remained alive after the reload/purge and entered a second world before this abort, so the attached evidence does not implicate the resource-reload allocator ordering. No current animated `NativeImage` instances were closed. An `hs_err` file is not expected for this LWJGL fail-fast abort.
- The resource-reload runtime gate remains pending. The next pass should disable PickupNotifier 8.0.0 (or its OpenGL framebuffer/transparency path), repeat full-pack F3+T, and continue ordinary gameplay beyond 82 seconds while recording RSS, system available memory, NativeImage totals, and animated texture behavior. The 4096 MiB safety floor and existing RSS gates remain unchanged.


### RX 6900 XT Build #304 low-headroom retest — 2026-09-10

- Build #304 was tested with PickupNotifier absent; the launcher explicitly reported `pickupnotifier (version 8.0.0 -> MISSING)`. The client reached the target full pack with Vulkan active and began F3+T.
- Before terrain retirement, the reload snapshot was process RSS 11829 MiB, NativeImage live 1220 MiB, and system MemAvailable 8464 MiB. Atlas retirement produced the same ownership result as Build #303: NativeImage live fell to 297 MiB, the approximately 195 MiB of animated block-atlas CPU images was preserved, and no current animated images were closed.
- During replacement decode/upload, the existing guard tripped at process RSS 12294 MiB against the unchanged 12288 MiB soft limit while MemAvailable was 8144 MiB, below the unchanged 8192 MiB RSS-pressure threshold but above the unchanged 4096 MiB hard floor. The reload therefore failed before apply; there is no success marker or allocator-purge marker, as required by the success-only ordering.
- This is not evidence of a Build #304 runtime-code regression. The live comparison from the Build #303 source checkpoint `9c16a1ab8463a6872412c5a333ac5a6831e06249` to Build #304 `864b934182ada63ca7d246dab044f0ab76897d32` contains only documentation changes, and `MemoryDiagnostics` is unchanged. The #304 launcher started with 21810 MiB available versus 26316 MiB in the successful #303 run, leaving roughly 5 GiB less reload-time headroom.
- The resource-reload runtime gate remains pending. Repeat with PickupNotifier absent after closing memory-heavy applications, aiming for at least the prior approximately 13 GiB MemAvailable during reload; do not use the diagnostic safety overrides for the normal validation. Then continue ordinary gameplay beyond the previous 82-second window while watching RSS, system availability, NativeImage totals, and animated textures.