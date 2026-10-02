# VulkanMod Forge 1.20.1 — Agent Status

This is the living continuation checkpoint. Live `forge-1.20.1` Git/CI/runtime evidence always wins if this file is stale. Historical detail belongs in Git and focused evidence documents.

## Current build / executable state

- Latest CI-validated executable commit: `48b0f06b4c36b01ead161b05c18da8688d8a637f` (`fix: preserve staged sprite copies across buffer growth`).
- CI **#935** / run `36968050321` is fully green.
- #935 direct distributable: `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.935-g48b0f06b-all.jar`, artifact ID `11211050003`, SHA-256 `768a1e09ee5a979d71ed033de6aa4da5d67a8faf3ef2e49808f3adb134ffeedb`.
- #935 smoke logs: `VulkanMod-Forge-smoke-log-935`, artifact ID `11210498528`, SHA-256 `b581e9b1a836c8a20835b7d07aec458921497b1f36154e3e7dca58715a7008d1`.
- #935 passed production build/distribution, both Forge Vulkan startup modes, persistent GPU indirect shadow commands, vanilla post-chain/depth-post-chain, screenshot readback, packaged Immersive Portals anchors, Create Chronicles compatibility, and Crash Assistant 1.9.7. Private resource-pack fixture steps were skipped because the private fixture configuration was unavailable, as expected.
- Latest RX 6900 XT / RADV hardware-validated executable behavior remains build **#932**, commit `cd3dfe14bdfcfe93400b05c8811f5e15c73f3f0f`. Build #935 now requires the same fixed RX benchmark to quantify the texture-upload optimization.
- The adversarial audit remains complete: **0/5 repair clusters remaining**. Do not restart it without contradictory live evidence.

## #932 RX 6900 XT benchmark — current hardware evidence

Build **#932** completed the fixed automated stationary benchmark on the user's RX 6900 XT / RADV system with Create Chronicles.

Benchmark contract:

- fixed world/camera: `0,192,0`, yaw `-90`, pitch `30`
- framebuffer: `2552x1374`
- render distance 16 / simulation distance 12
- FPS cap 260, vsync off
- Distant Horizons rendering disabled
- System OpenAL off for continuity
- 60 s settle / 180 s measured capture
- GPU-terrain voxel staging / mesher / CPU bypass / draw handoff / hybrid enabled

The run completed cleanly for **180.003 s** with the camera unchanged and framebuffer stable.

### Coarse Vulkan GPU profiler is RX-validated

All **20,405** measured main-graphics frames produced accepted pass breakdowns, with zero invalid/unaccounted frames, missing markers, reconstruction/containment failures, timestamp read failures, broad sample drops, or terrain segment drops.

Average main-graphics GPU execution was **1.260 ms** (p95 **1.380 ms**):

- pre-world: **0.010 ms**
- world total: **1.195 ms**
  - terrain: **0.932 ms**
  - world-other: **0.263 ms**
- between world/HUD: **0.002 ms**
- HUD: **0.051 ms**
- tail: **0.002 ms**

Do **not** interpret terrain's share as evidence that terrain is the current performance bottleneck. The entire main-graphics command buffer is far below the recurring CPU tick cost. Finer GPU profiling is not justified by this capture unless later evidence changes that conclusion.

### CPU / texture work is the dominant performance evidence

#932 measured:

- client tick average: **26.085 ms**, p95 **31.597 ms**;
- texture tick average: **18.542 ms**, p95 **23.507 ms**;
- complete `SpriteContents.upload()` body average: **11.232 ms**, p95 **14.763 ms**;
- same-tick texture work outside those upload bodies: **7.310 ms** average, p95 **9.717 ms**;
- sprite upload calls: **1309.824/tick**;
- mip/sub-upload calls: **6465.681/tick**.

This reproduces #931 closely. The sustained hitch is primarily client-tick texture work, not main-graphics GPU execution.

Texture work remains split materially between upload-body work and the non-upload texture remainder. Keep those buckets separately measurable rather than assuming one optimization solves the other.

## Build #935 texture-upload optimization candidate

Focused evidence document: `docs/PERFORMANCE_TEXTURE_UPLOAD_BATCHING_2026-10-01.md`.

Executable implementation commits:

1. `d301b1d93874647afd3d0e17a66664f1460c9172` — `perf: batch animated sprite mip copies`;
2. `48b0f06b4c36b01ead161b05c18da8688d8a637f` — `fix: preserve staged sprite copies across buffer growth`.

### What changed

The texture tick already owned one shared graphics upload command buffer, but each mip in every `SpriteContents.upload()` independently built a `VkBufferImageCopy` and called `vkCmdCopyBufferToImage`. The #932 rates imply about **4.94 sub-uploads per sprite**.

Build #935 preserves every existing mip upload and animation update but, inside the already-owned upload batch, stages each mip normally and emits the retained mip regions together with one `vkCmdCopyBufferToImage` call per sprite in the common case.

The implementation deliberately preserves:

- animation cadence and frame/interpolation behavior;
- sprite/sub-upload semantic counters;
- staged bytes and offsets;
- existing image-layout transition ownership;
- the 128 MiB staging flush policy;
- fallback to the old per-mip path outside an active shared graphics upload batch;
- the `SpriteContents.upload()` profiler boundary, so any improvement remains attributable to `sprite_upload_ms`.

The correction in `48b0f06b...` handles `StagingBuffer` geometric growth safely. Deferred copy regions retain the exact old staging-buffer handle, split when the handle changes, and are recorded while that retired buffer is still kept alive by the active upload batch.

### Expected mechanism, not a measured claim

With #932-equivalent animation activity, the implementation should reduce Java/LWJGL `vkCmdCopyBufferToImage` recording calls from roughly **6466/tick toward ~1310/tick**, plus a small number of transition copies — approximately an **80% reduction in that specific command-recording operation**.

This is not yet a measured RX frame-time/FPS improvement. If `sprite_calls_per_tick` or `sub_upload_calls_per_tick` materially fall in the next benchmark, treat that as suspicious semantic drift rather than success.

## Settled profiler context

#931's broad Vulkan timestamps were valid, but the first coarse `gpu_passes` aggregation silently produced zero accepted/rejected frames because Forge's `ForgeGui.render(...)` override bypassed HUD hooks placed in vanilla `Gui.render()`. `cd3dfe14...` repaired the semantic boundary, explicit invalid accounting, and terrain containment. #932 hardware evidence fully validated that repair. Do not reopen this investigation unless new evidence contradicts it.

## Active sequencing

- Phase 4: **7/8**. World enter/leave/re-enter plus resource reload is the only mandatory open gate and remains deferred at the user's request.
- Phase 5: **4/7**, current useful priority. Formal comparable OpenGL/Vulkan baseline and hitch/frame-time evidence remain open under the fixed benchmark contract.
- Phase 7 GPU-terrain/hybrid: **6/11** and paused for Phase 5 measurement priority. Do not reopen unrelated Phase 7 optimization work while resolving benchmark evidence.
- Immersive Portals current compatibility behavior is RX-confirmed working. Preserve its documented semantic boundary unless new evidence contradicts it.
- The independent shutdown/native-lifetime abort observed around build #801 remains unresolved and separate from this performance work. Do not make speculative native ownership changes without focused evidence or a native backtrace.

## Next useful action

Run **build #935** with the exact same automated stationary benchmark contract as #932 and return `logs/vulkanmod-performance-benchmark-*.log`.

Evaluate in this order:

1. `client_tick_texture_detail.sprite_upload_ms_avg/p95` — primary target of #935;
2. total `textures` and `client_tick` average/p95 — end-user effect;
3. `non_upload_ms_avg/p95` — keep independently attributable;
4. `sprite_calls_per_tick` and `sub_upload_calls_per_tick` — should remain close to #932 (~1309.824 / ~6465.681), proving texture semantics were not throttled;
5. main-graphics GPU time — informational only because texture helper upload submissions are outside that timestamp scope.

If upload-body time falls materially while the ~7.31 ms non-upload remainder stays high, investigate that remainder next (ticker/interpolation/other texture-tick work) with bounded attribution. Do not guess or change animation semantics before that evidence exists.
