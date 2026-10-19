# GPU texture source-page descriptor and shared scratch pilot — October 18, 2026


## Shared scratch multi-job fixed tail (candidate #1055)

The separate multi-dispatch flag now reserves a reusable 1,536-byte,
32-row metadata tail in each 4 MiB shared scratch page rather than
append-only rows. Active sprite output slices remain disjoint, page
retirement and 128 MiB pending-aware reservations are unchanged.
Before a tail wrap, the atlas queue records all pending work; the
next metadata upload emits a compute-read/transfer-write to
transfer-write buffer barrier on the same graphics queue after any
staging reset, also ordering earlier submitted command buffers.
Large bindings fall back to standalone scratch/CPU as before.
See `docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`.
Candidate `629de440` / full #1055 pending; not yet native-qualified.

## Implementation boundary

Executable changes: `8352108e56d6ffec566979cc35795b1499010049`;
contract extraction/regression corrections: `a74f41ce5b2ca4e11ae8b5f42dbab6f84d0b68a4`
and `6faa82e5f05d800dc0fa26fd51f2a775d921d708`.
**Qualified executable:** `6faa82e5f05d800dc0fa26fd51f2a775d921d708`.
Full **#1049 / [run 39208913742](https://github.com/Vorith03/VulkanMod/actions/runs/39208913742) PASSED**.
Forge JAR artifact **12167505101**, smoke logs **12166663996**.
Native marker `VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK` was observed in
the screenshot client log. Public offline, Forge package and dual startup,
terrain indirect, GPU texture/particle pixel and synchronization, screenshot,
Create Chronicles/Immersive Portals and Crash Assistant gates passed;
private resource-pack gates skipped.

The opt-in `-Dvulkanmod.experimentalGpuAnimationSharedScratch=true` requires the
existing all-mip batch and pooled-source experiments
(`experimentalGpuAnimationBatch` and `experimentalGpuTextureSourcePool`),
plus enabled GPU interpolation. The default and standalone batch/lease routes
remain unchanged. This is a resource-ownership slice, not a change to animation
clock authority, mip generation, eligibility, or CPU fallback.

For source leases sharing one immutable `GpuAnimationSourcePool` page, O4
may allocate a **single descriptor set** binding that entire source page and
one 4 MiB device-local scratch page. Each admitted sprite binding reserves
its own 4-byte-aligned scratch interval by append-only bump allocation.
The source lease's offset is carried in shader push constants. The immutable
per-mip source table continues to contain lease-relative offsets; the shader
adds the source-page base. Its output offset adds the scratch-slot base.
Nonfused one-mip dispatch uses the same absolute-offset convention.
The RGBA/current-alpha, Java-double fraction/truncation and all-mip dispatch
formula are unchanged. Output VkBufferImageCopy and memory barrier ranges
cover the reserved interval, not another sprite's slot.

Each shared page's **descriptor is charged once**, including until safe
retirement. Physical scratch pages are capped at 128 MiB including
retirement-pending pages, independently of per-sprite logical residency
charges; oversized/unsupported allocations retain the existing standalone
binding attempt or CPU fallback. No scratch interval is reused while the
page is live. Last-binding retirement retires the scratch VkBuffer and
descriptor through the existing upload-safe/fence path; source leases retain
their independent ownership. The page-wide descriptor range is admitted
only within `maxStorageBufferRange`. Device reset/teardown must remain
serialized with GPU-safe retirement.

`VTextureSelector.flushPendingSpriteUploadRange` checks queued *packed*
transfer source reads for intersection with a scratch interval before rewrite.
Unknown row strides conservatively flush. Disjoint sprite slots must not force
an unrelated copy queue drain. Atlas destination overlap, source switching,
staging reset and normal copy queue capacity boundaries still retain original
flush/barrier ordering. Accepted GPU interpolation returns true to the
SpriteContents ticker, bypassing equivalent CPU interpolation; rejected
work retains the preexisting CPU path.

## Verification and limitations

The extended public texture-transfer contract extracts both production
`queueComputedSpriteFrame` overloads and the new pending-range method;
it asserts source-offset encoding, disjoint non-flush, overlapping flush,
and existing atlas/geometry semantics. The screenshot/native source-pool
oracle enables the new flag, allocates *distinct* source leases and binding
slots sharing a descriptor, dispatches both and compares atlas pixels, including
queued O3 overwrite and repeated-slot reuse. Expected marker:
`VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK`.

Full CI #1047 (run 39208412862) **failed before native compilation**
because `texture-transfer-contract.py` extracted only the old queue API
signature and not the new overload; this is harness evidence, not a native
pixel or synchronization verdict. The final unambiguous extractor correction is in `6faa82e5`; #1048 also failed its incomplete overload selector. Full #1049 passed on the corrected executable.

**This does not aggregate multiple sprites into one GPU dispatch.** It
reduces per-sprite descriptor and VkBuffer allocation but retains a
push-constant update and all-mip dispatch per sprite. A future genuine
multi-sprite kernel needs a bounded job table, queued compute/copy ordering
at upload boundaries, a defined per-job failure/CPU fallback policy,
and direct native multi-job pixel/retirement/overlap validation. Do not
claim that next gate from shared memory ownership alone.

No RX 6900 XT coverage, CPU wall time saved, newly incurred compute/queue
cost, frame FPS, p95 or p99 is established. Retain all pilots opt-in.
Open gates include actual-world particle portal/mod/light/translucency/
resource-reload pressure, generated-terrain REPLACE/atomic APPEND candidate
ABI, and integrated adoption/CPU displacement/profitability. Preserve
#1041/#1042 descriptor-pool negative evidence and the analyzed #1028
benchmark UUID `e05f2b31`; no isolated hardware benchmark request.

## October 19 verified multi-dispatch successor

This prior #1049 standalone/shared descriptor/resource ownership pilot
is **superseded for the separate opt-in aggregated path**, not invalidated.
Full **#1053 / run 39216055025** passed with executable
`c06f1cbc9c57a09b0df819edc639022bef049096`, Forge artifact
**12169313491**, smoke logs **12169019219** and native marker
`VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK`. The new
`experimentalGpuAnimationMultiDispatch` flag uses up to 32
page-local GPU job rows per Z-dimension compute dispatch and validated two
independent sprites in one dispatch with exact native atlas pixels,
repeated updates and overlap ordering. Its 48-byte rows use the scratch
page's unused high-end tail, remain append-only per page lifetime, and
can run out of room during extended high-churn animation sessions;
legacy one-sprite GPU/CPU fallback remains available. This is
correctness qualification only, not RX performance evidence.
See `docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`.
