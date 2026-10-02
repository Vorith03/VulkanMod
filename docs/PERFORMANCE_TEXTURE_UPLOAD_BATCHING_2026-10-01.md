# Animated texture mip-copy batching — 2026-10-01

## Evidence and scope

The RX 6900 XT / RADV automated stationary benchmark from build #932 (`cd3dfe14bdfcfe93400b05c8811f5e15c73f3f0f`) completed the fixed 180-second capture and made the next performance target unambiguous:

- client tick: **26.085 ms average**, 31.597 ms p95;
- texture tick: **18.542 ms average**;
- complete `SpriteContents.upload()` bodies: **11.232 ms average**, 14.763 ms p95;
- same texture tick outside those upload bodies: **7.310 ms average**;
- `SpriteContents.upload()` calls: **1309.824/tick**;
- mip/sub-upload calls: **6465.681/tick**;
- main graphics GPU execution: **1.260 ms average**, 1.380 ms p95.

This is a CPU-side texture-upload optimization. It does not change animation cadence, frame selection, interpolation, staged pixel bytes, texture visibility policy, terrain rendering, or the main graphics profiler.

## Concrete repeated work

The texture path already owned one shared `GraphicsQueue` upload command buffer while ticking/uploading animated textures. However, every mip still flowed independently through `NativeImage` -> `VTextureSelector.uploadSubTexture()` -> `VulkanImage.uploadSubTextureAsync()`, and each mip built one `VkBufferImageCopy` and issued one `vkCmdCopyBufferToImage` recording call.

The #932 call rates imply about **4.94 mip/sub-uploads per sprite upload** (`6465.681 / 1309.824`). The pixel copies themselves are required, but recording almost five Vulkan copy commands per sprite is redundant when those regions share the same destination image, layout and owned command buffer.

## Implementation

Executable commits:

- `d301b1d93874647afd3d0e17a66664f1460c9172` — batch animated sprite mip copies;
- `48b0f06b4c36b01ead161b05c18da8688d8a637f` — preserve deferred copy regions across staging-buffer growth.

`MSpriteContents` now brackets each real sprite upload with a narrow `VTextureSelector` sprite-copy scope. `VTextureSelector` batches only when the graphics queue already has an active upload batch. Otherwise it uses the previous per-mip path unchanged.

Inside an owned upload batch:

1. The first copy that still needs an image-layout transition continues through `VulkanImage.uploadSubTextureAsync()`, preserving existing transition ownership.
2. Once the image is already in `VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL`, every mip is staged exactly as before, but its copy-region metadata is retained in fixed primitive arrays.
3. At the end of the `SpriteContents.upload()` body, all retained mip regions are emitted by one `vkCmdCopyBufferToImage` call.
4. The existing 128 MiB texture-staging budget flush records pending regions before command-buffer submit/wait/reset.
5. `StagingBuffer` geometric growth is also safe: growth retires the previous `VkBuffer` but keeps it alive while the graphics upload batch is active. The batch records the exact staging-buffer handle that owns each region set and splits/flushes when that handle changes, so no deferred region is rebound to a replacement buffer.
6. The fixed region capacity is 32; overflow simply flushes early. This is well above practical Minecraft mip counts and does not drop work.

The profiler boundary is intentionally preserved: the batched copy recording is flushed before the `SpriteContents.upload()` timing scope ends, so the next benchmark can attribute any gain to `sprite_upload_ms` rather than moving work into the non-upload remainder.

## Expected effect, not yet a hardware claim

With unchanged #932 animation activity, the mechanism should reduce Java/LWJGL `vkCmdCopyBufferToImage` recording calls from roughly **6466/tick toward about 1310/tick**, plus a small number of first-image transition copies. That is approximately an **80% reduction in this specific command-recording operation**.

The number of sprite calls, sub-upload calls, staged bytes and animation updates should remain essentially unchanged. A reduction in those semantic counters would be suspicious rather than evidence of success.

## Validation

CI **#935** / run `36968050321` is fully green for executable commit `48b0f06b4c36b01ead161b05c18da8688d8a637f`.

It passed the production build/distribution checks, both Forge Vulkan startup modes, persistent GPU indirect shadow commands, vanilla post-chain and depth-post-chain execution, screenshot readback, packaged Immersive Portals anchors, Create Chronicles compatibility, and Crash Assistant 1.9.7. The private resource-pack fixture steps were skipped because that fixture configuration was unavailable, as expected.

Artifacts:

- direct JAR `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.935-g48b0f06b-all.jar`, artifact ID `11211050003`, SHA-256 `768a1e09ee5a979d71ed033de6aa4da5d67a8faf3ef2e49808f3adb134ffeedb`;
- smoke logs `VulkanMod-Forge-smoke-log-935`, artifact ID `11210498528`, SHA-256 `b581e9b1a836c8a20835b7d07aec458921497b1f36154e3e7dca58715a7008d1`.

CI proves build/startup/resource-loading compatibility, not the RX performance effect.

## Next measurement

Run build #935 with the same fixed RX benchmark contract used for #932.

Compare, in order:

1. `client_tick_texture_detail.sprite_upload_ms_avg/p95` — primary target;
2. total `textures` and `client_tick` average/p95 — end-user effect;
3. `non_upload_ms_avg/p95` — should remain separately attributable;
4. `sprite_calls_per_tick` and `sub_upload_calls_per_tick` — should stay near #932, proving animation/update semantics were not throttled;
5. main-graphics GPU time — informational only, because these helper upload commands are outside that timestamp scope.

Do not claim a measured performance improvement until that matched RX capture exists.
