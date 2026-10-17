# Repair source-switch fragmentation in the unified texture queue

## Cause and discriminating evidence

Base: `504617cd560739b0eaf6215cbd442f086f708731` on `forge-1.20.1`.
Its only delta above executable #1019 (`ce9d66e1`) is documentation.
The live branch had no subsequent implementation; open PR #12 is the separate
historical flight-recorder branch, not a texture repair. Baseline full/native
run `38972838076`, job `117215415389`, is green (private packs skipped).

The supplied RD32 UUID `6c29da16-40b9-4f37-8e78-aa1180197560` records
1,266,289 source-handle-switch flushes out of 1,435,220 mixed flushes (88.23%),
with 8.154 ms/tick inclusive flush CPU and 17.787 ms/tick texture time. These
nested times cannot be added, and flush time is not partitioned by reason.
The original report and hashes are in `RX_RD32_CAPTURE_2026-10-17.md`.

Tracing `SpriteContents.upload -> SpriteAnimationState.tryResidentUpload ->
GpuAnimatedTextureResidency.copyToAtlas -> VTextureSelector.copyResidentSpriteFrame`
and the CPU `NativeImage -> uploadSubTexture` path finds a shared queue with
**one source handle for all pending regions**. Every CPU-staging/resident-page
switch drained it. Each drain allocated/filled native copy structs, entered
Vulkan command recording and emitted a conservative transfer-write barrier.
This made interleaved independent writes expensive despite shared source pages.
The benchmark's retained native-boundary stacks independently exercise this exact
call chain: the top rank (125 samples) is `vkCmdCopyBufferToImage <-
flushSpriteUploadCopies <- queueSpriteUploadCopy:594 <- copyResidentSpriteFrame`;
the next rank (43 samples) has the same source-switch line 594 from CPU
interpolation's `uploadSubTexture`. Line 597's region-limit route is separately
ranked (42 samples). These are statistical boundary counts, not exclusive CPU
milliseconds; they substantiate both input routes reaching the expensive source
switch, rather than attributing all flush cost to it.
The handle-switch rule predates the mixed queue: one VkBuffer per Vulkan copy
command was incorrectly inherited as one VkBuffer per pending ordering epoch.

Falsifiable hypothesis: disjoint CPU/resident writes can remain in one bounded
epoch, regardless of source. Grouping at drain should eliminate source-only
drains and combine repeated sources, without changing final pixels or crossing
any actual dependency. The exact production enqueue/drain methods now run in
`texture-transfer-contract.py` against a native-call recorder. The test fails
on the original `504617c` selector at `source alternation must not drain` and
passes on the repaired selector. A separate run of the original production
methods also confirms the original 128/128 command/barrier counts. A controlled
128-write/two-source alternation
changes **128 copy commands / 128 barriers to 2 commands / 1 barrier**. This is
an operation-count reproducer, not a time/FPS measurement or a replay of the
benchmark (the original capture did not record every handle/region).

## Execution and ownership change

Only the existing `experimentalUnifiedTextureUploads=true` path changes its
scheduling. No new flag, memory-budget increase, default adoption, shader, upload
submission, device-idle wait or per-update Java allocation is added.

- The same 128-region queue retains each region's VkBuffer handle along with
  offset, mip, destination rectangle and source row/image strides.
- Overlap checking covers **all pending sources**. Any overlap drains all prior
  writes before appending the later one, including a return to an earlier source.
- At drain, reusable primitive scratch arrays stably group indices by source;
  each source emits one copy command containing its own original regions.
  Every destination within an epoch is pairwise disjoint at its mip, so source
  groups can be emitted without inter-group dependencies. One existing
  transfer-write barrier after the entire epoch orders it against future writes.
- Region-capacity, texture/layout/direct-operation, O4 compute, source-seed,
  explicit-flush, staging-reset and outer-scope boundaries still drain. Overwrites
  of earlier capacity-flushed or direct-written pixels retain their dependencies.
- The disabled-pilot path retains source-change draining, insertion order and
  its existing barrier policy; it bypasses source-group planning.
- Group planning and intersection are bounded by 128 regions (worst-case
  quadratic scans); scratch storage is fixed, a few KiB. Increased geometry/
  grouping CPU work is a real remaining performance risk to measure.

`StagingBuffer.resizeBuffer` retires the old BufferInfo rather than overwriting
or freeing it. `MemoryManager.addToFreeable` puts staging and resident buffers
in the upload-safe retirement queue. `freeBuffers` refuses retirement while an
upload batch is active and waits both queues before release. Staging usage
reset drains/submits/waits **before** reusing bytes. Resident sources are
immutable and shared page slices append-only until page retirement. Thus
retaining multiple handles until drain changes no source ownership or reuse
permission. The native fixture also retires sources while their final copies
are pending. Arbitrary source mutation and CPU fallback semantics are unchanged.

## Validation

Local Java 17:

- All **22** existing offline contracts passed. After final production edits,
  the focused transfer and attribution contracts passed again.
- Production-method recorder: 128 alternating sources remain queued, two source
  commands/one dependency; different mips; cross-source overlaps; capacity then
  overwrite; all 128 sources distinct; empty drain; legacy behavior. Offsets and
  row/image strides are verified after grouping. **100,000 randomized writes**
  across seven source handles/four mips match a sequential last-writer oracle.
- The new regression fails against the unmodified base selector, proving the
  test distinguishes the original failure from its correction.
- `copy_commands` admission/reset checks pass across warmup and two captures.
- `git diff --check` passes.
- Local `scripts/ci/agent-check.sh` cannot configure: Gradle 8.1.1 distribution
  download fails with `Network is unreachable`. No local Forge/native pass is
  claimed; no alternate Gradle version was substituted.

Native CI fixture extends the existing screenshot/sync gate with **160
alternating CPU/two-device-buffer writes**, nonzero source offsets/strides,
asserted pending queue counts (126 before capacity and 31 after), cross-source
pending overwrites, capacity/direct-first overwrites and early source retirement.
All 160 final pixels are checked. Existing transformed ticker/mip/mutation/
fallback tests and both O3/O4 ordering directions remain in the suite.

Full public/native CI **#1020** passed at
`e0807c626bfa27416ebadc53eddff519f9ac29ef`: run `39038204099`, validation job
`117412382928`. All 22 offline contracts, Forge/packaging, animation source and
portal anchors, both startup configurations, indirect/post/depth/screenshot
pixel+sync gates, combined Create Chronicles and Crash Assistant passed. The
extended mixed-source native oracle was required by the screenshot gate and
passed. Logs and qualified JAR uploads passed; private real-pack fixtures were
skipped. The JAR is `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1020-ge0807c62-all.jar`,
artifact `12103455469`. Native/Forge correctness is qualified; net RX speedup,
real-pack visual correctness and deferred reload/re-entry remain unverified. The owner
JAR hold is respected until this coherent candidate reaches the point where
native qualification and RX comparison are the remaining validation gates.

## Measurement and next action

`copy_flushes` now counts **nonempty ordering epochs**, not necessarily Vulkan
copy commands. `copy_commands` counts actual queued `vkCmdCopyBufferToImage`
commands, excluding direct CPU/resident/O4 commands. `copy_regions` remains
all queued regions; copy-flush CPU timing covers grouping and all emitted source
commands/dependency in that epoch. Flush reasons still reconcile with epochs,
not command counts. On #1019 each nonempty flush emitted one command, so its
1,435,220 flushes are also its queued command count. Compare both counters,
region totals and full texture/client tick and GPU upload times.

After full native/Forge qualification, use the new candidate versus #1019 with
**both pilots enabled in both**. Keep the existing RD32 world, selected packs,
2552x1374 framebuffer, simulation distance 12, camera, 60s minimum settle/10s
quiet/300s max settle, 180s capture, GPU timestamps and 10ms JFR sampler. Use
identically restored world copies because the integrated server/particles can
change over time. Return UUID log, neighboring JFR and latest.log; report any
flickering/stale textures. One pair is a screening result; three alternating
matched pairs are required for a formal net-benefit claim. The historical
single capture is evidence of the mechanism, not a matched time control.
Do not use pilot-off as the repair-only control: that changes source storage,
GPU copy coverage and other scheduling as well.

Expected: no source-switch drains in unified mode, fewer queued copy commands
and dependencies with comparable region/animation counts. Net texture/tick/frame
and upload-GPU time must improve without rendering errors or synchronization
hazards before calling this a measured performance improvement. Fewer epochs
alone are insufficient; grouping scans or GPU scheduling could offset gains.

Remaining independent costs: O4's 3.79% coverage is mainly disabled residency;
source review confirms fixed-budget first-admission and permanent state disable,
not observed mutation churn (zero invalidations). The capture does not establish
which alternative admission policy wins at the same memory cap, so that policy
is unchanged here. Full per-frame terrain traversals with the 1,604 dirty backlog,
campfire particle tick/render and integrated-server load remain. Do not combine
those changes with the first controlled texture repair comparison. Reload/re-entry
and private-pack qualification remain open.
