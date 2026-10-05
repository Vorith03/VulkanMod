# Texture animation offload reference — O2

Target: Minecraft 1.20.1, Forge 47.3.0, Java 17. This is a numeric reference and
ownership contract, not an enabled resident-frame or compute implementation.
Follow `GPU_OFFLOAD_INVESTIGATION_PLAN.md`; the matched #946 RX result remains
O1 and is required before selecting a production offload experiment.

## Reference and qualification

`SpriteAnimationOracle` is diagnostic-only. It consumes resolved frame entries
and snapshots of the actual generated mip images. It does not regenerate mips,
select upload paths, advance a game ticker, or authorize a custom source.
`animation-oracle-contract.py` checks explicit alpha/rounding/clock/mip vectors.
`SpriteAnimationSmokeTest` runs the actual transformed Forge ticker and compares
both ordinary uploads and hidden/first-use refresh against independent numeric
pixels, clocks and staging cadence. Qualification is pending the next CI run.

Cases cover reordered/repeated indices and mixed durations; discrete and
interpolated tickers; a rectangular sheet with odd tile width and multiple rows;
implicit metadata; invalid-index/zero-duration filtering; every positive-size
mip; Forge zero-extent mip guards; and a subclass that must keep CPU uploads.
Source mip pixels are authoritative inputs, so passing this oracle does not
qualify replacing Forge's mip-generation algorithm.

Expected pinned behavior to verify in generated source and runtime:

- Frame position is the ordinal in the resolved metadata list, not the sheet
  index. Each call increments subFrame once. At its duration boundary, advance
  one entry, wrap, reset subFrame, and upload only if the sheet index changes.
- Empty metadata enumerates the sheet in row-major order with the default
  duration. Invalid explicit entries are removed before ticker creation. Static
  and unsupported metadata are outside this animated reference.
- Between boundaries, interpolation runs only when enabled and current/next
  sheet indices differ. RGB uses Java double arithmetic with current weight
  `1.0 - subFrame / duration`, then truncates each channel. Alpha is copied from
  the current frame. ABGR integer order and RGBA transfer bytes remain distinct.
  An integer lerp or round-to-nearest replacement requires exact equivalence;
  one-third weights can expose floating-point truncation differences.
- Each mip reads its existing source image. Base sheet tile offsets are shifted
  by mip level; this differs from multiplying a separately truncated tile width
  for odd dimensions. Destination coordinates and tile extents also shift.
- Forge stops upload when either sprite extent becomes zero and guards
  interpolation allocation/work at those levels. Such mips are not synthesized
  from clamped one-pixel sprite dimensions.
- Visibility suppression keeps CPU metadata progression. First-use refresh
  materializes the current frame/subframe without advancing that clock.
  Custom subclasses and non-null Forge texture metadata stay on the CPU path.

CI records available Forge-generated SpriteContents source with a SHA256 and
archives transformed SpriteContents/Ticker/InterpolationData bytecode. The
source hash identifies evidence, not an optimization eligibility certificate.

## Source eligibility and lifetime

Exact vanilla class identity and absent Forge metadata qualify the existing
usage-gating adapter only. They do **not** establish immutable pixel storage.
Forge exposes the original NativeImage; mods may mutate it, supply custom mip
images or change behavior through mixins. A future offload path needs an explicit
source ownership/invalidation contract before skipping CPU work.

An admitted source must have validated immutable RGBA bytes for every supported
mip, a resolved numeric frame schedule, and an owned generation. Dynamic/custom
sources and unproven mutations retain the existing path. Preserve the original
mod-visible CPU images. Never free them merely because a GPU snapshot exists.

A source/atlas generation lease must invalidate on source replacement, restitch,
reload, cancellation and close. Submit only jobs whose renderer, source and atlas
generations still match. Stale jobs fall back before suppressing CPU work. Retire
resident allocations after the last consuming frame/submission fence. CPU-side
close alone is not permission to release in-flight device memory.

## Proposed numeric job boundary

The following are required fields for a future versioned ABI, not a shipped
binary layout. Validate integer arithmetic and source/destination bounds before
recording commands; never serialize Java/NativeImage/native image handles as
persistent identities.

| Field | Meaning |
| --- | --- |
| ABI version / operation | Discrete copy versus qualified interpolation |
| Renderer / atlas / source generation | Exact resource ownership and stale-job rejection |
| Current / next frame ID; subFrame / duration | CPU-owned resolved animation clock |
| Mip and format | Existing source mip and exact RGBA format |
| Source allocation generation / byte offsets / row stride | Bounded numeric source ranges for both frames |
| Destination x/y/width/height | Positive-size atlas rectangle after mip shifts |
| Output range, when computing | Bounded scratch bytes and consumption lifetime |

Estimate residency from actual source bytes across mips, excluding repeated
metadata entries. Charge any copied/deduplicated frame storage explicitly;
deduplication needs identical bytes, format and mip semantics. Use checked long
arithmetic, a renderer-wide cap (initial plan: 128 MiB), peak reload accounting
and admission failure before bypassing uploads. Do not preload all pack sources
or synchronously evict per tick.

## Remaining gates

A passing synthetic oracle closes bounded numeric behavior only. Production
source admission, custom mixin ownership, resource-pressure/stale-generation
fallback, resident copies, compute dispatch and helper-queue timestamps remain
unimplemented. Pack/portal/lifecycle and paired RX adoption gates remain open.
The user's deferred reload/re-entry check remains deferred. No hardware speedup
or default promotion follows from this work.
