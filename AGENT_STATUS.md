# VulkanMod Forge 1.20.1 — Agent Status

This file is the **living checkpoint**, not the source of truth for live repository state. Always inspect the current `forge-1.20.1` HEAD and latest CI first. If this file disagrees with Git or CI, Git/CI wins.

## Required planning documents

Use these together:

- `AGENTS.md` — development protocol and evidence rules;
- `ROADMAP.md` — canonical phase order, exit gates, and progress-report format;
- `AGENT_STATUS.md` — current verified checkpoint and immediate work item.

Future agents should not silently invent a new major workstream. Start from the active roadmap gate unless live evidence or the user requires a temporary detour.

## Last verified green checkpoint

- Branch: `forge-1.20.1`
- Verified commit: `f9b2b3916da4b719a993b5e721eaf29ad5c22bdc`
- Verified GitHub Actions run: **#282**
- Highest demonstrated milestone: **Milestone 6 — playable world**
- User RX 6900 XT result: Vulkan gameplay is playable; water rendering fix visually confirmed.

### CI coverage at this checkpoint

[Run #282](https://github.com/Vorith03/VulkanMod/actions/runs/34200598111) passed:

- distributable Forge build / packaging verification;
- Vulkan startup under Lavapipe;
- Vulkan startup with Forge early splash disabled;
- liquid alpha/UV regression smoke;
- terrain region-cache/batching smoke;
- real vanilla `shaders/post/creeper.json` `PostChain.process(...)`, submission and presentation;
- vanilla `shaders/post/transparency.json` depth PostChain: four processes in two submitted frames, initialized auxiliary inputs, MainTarget/offscreen depth copies, and no fallback samplers;
- depth smoke with Khronos validation 1.3.204.1 and synchronization validation enabled: no validation errors or synchronization hazards;
- Crash Assistant 1.9.7 compatibility;
- Flywheel 0.6 compatibility.

## Roadmap position

- Active phase: **Phase 3 — Core rendering correctness hardening**
- Phase progress at the last verified checkpoint: **9/11 mandatory gates**
- Newly demonstrated gates: **P3.8 — real PostChain execution**, **P3.11 — supported vanilla depth-aux / nearest-clamp sampler path**
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
- depth/stencil layout and synchronization corrections.

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

The bounded CI defect is resolved; stop repository work here. **P3.9 is now useful:** use the green build for an RX 6900 XT visual post-effect check, including depth-sensitive transparency ordering. Keep P3.10 readback safety open; this task did not change the production screenshot path. Resume any further roadmap work only in a separately requested task.

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
