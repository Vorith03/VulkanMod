# #1028 opt-in 256-region texture-copy queue — 2026-10-18

## Measured cause and exact mechanism

Owner #1026 benchmark UUID `9b7f3496-f876-4ca7-ba6a-482519c5373a`, 3,577 texture ticks, records **21,373,252 queued copy regions**, **244,701 nonempty copy flush epochs**, **114,177 `REGION_LIMIT` flushes**, **628,843 Vulkan copy commands** and **8.289 ms/tick inclusive texture copy-flush CPU** inside ~14.690 ms/tick complete texture processing. This is active GPU-resident and CPU-staged shared upload pilot work. Nested flush timing is **not additive** to texture time and is not pure per-flush overhead. A similar 128-region pressure appears in #1025 (116,465 region-limit flushes) and #1024 (136,881); no causal claim of lost FPS comes from counts alone.

Source: `VTextureSelector.queueSpriteUploadCopy` unconditionally drains when the fixed unified queue reaches `MAX_SPRITE_COPY_BATCH_REGIONS=128`. The queue is correct, but the numeric boundary itself forces frequent native `vkCmdCopyBufferToImage` recordings, scratch build and conservative WAW barrier even when the following atlas destinations are pairwise disjoint.

## Bounded pilot-only change

Raise **only** the already-opt-in unified-region limit **128 → 256**. The legacy path remains 32; the same primitive-array scratch and source grouping, per-mip half-open overlap check, WAW ordering/barriers, source retirement, staging reset and outer/compute boundaries remain untouched. No new pilot flag, additional texture budget, GPU dispatch, persistent allocation or default-on adoption. The extra scratch is a few KiB and `VkBufferImageCopy` stack count remains explicitly bounded.

Correctness tests:
- The existing production-method offline contract now holds 256 alternating-source writes with exact source handles, offsets/mip/strides, forces a 257th write and a capacity drain, and verifies 256 unique source group commands and the unchanged 100,000 randomized sequential last-writer oracle.
- The actual native mixed-source Vulkan test now writes 288 pixel columns into a 320×1 atlas: tests pending count 254 before the capacity boundary and 31 after, crosses an overwrite of pixel 286 while it is pending, checks prior capacity-flushed pixel 1 and direct pixel 0 after subsequent overwrites, retires resident sources with pending writes and reads all 288 RGBA results. Eight-bit pixel expectations wrap correctly above 255. Existing Vulkan validation catches barrier/lifetime regressions.

## Disposition and test threshold

**Implemented as a bounded experiment, not a measured FPS improvement.** It might reduce region-limit drains but the larger region queue also increases O(N) overlap scanning and source-group setup, so the real CPU/frame impact is unknown. Validate with all offline/native/Forge correctness gates and CI; **only then** ask for a single new RX restored-snapshot 180-second benchmark with the already-used GPU texture and bulk/frustum flags. Compare against #1025's established performance capture where no extra broken #1026 check-hook was active, without requesting a repeat control. Observe `copy_flush_region_limit`, `copy_commands`, `copy_flush_cpu_ms_avg`, texture/client tick CPU, separate upload/main GPU timestamps and full FPS. If unfavorable, revert the numerical limit, not the entire shared texture queue.

