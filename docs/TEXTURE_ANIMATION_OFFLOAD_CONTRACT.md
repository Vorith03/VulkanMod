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
pixels, clocks and staging cadence. Full public CI #988 passed on its first attempt (run `37372339264`, executable
`3d01d50a6e73fc84f0daec067d248000f65d3d16`). Every existing public gate and
JAR/log upload passed; private real-pack fixtures were skipped. Local boundary
vectors also reject deliberately interpolated alpha, integer lerp and late frame
transition implementations.

Cases cover reordered/repeated indices and mixed durations; discrete and
interpolated tickers; a rectangular sheet with odd tile width and multiple rows;
implicit metadata; invalid-index/zero-duration filtering; every positive-size
mip; Forge zero-extent mip guards; and a subclass that must keep CPU uploads.
Source mip pixels are authoritative inputs, so passing this oracle does not
qualify replacing Forge's mip-generation algorithm.

Pinned behavior confirmed by the actual runtime oracle and inspected transformed
SpriteContents/Ticker/InterpolationData bytecode:

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

CI attempts to record available Forge-generated SpriteContents source with a
SHA256 and archives transformed SpriteContents/Ticker/InterpolationData bytecode.
The generated source archive was unavailable in #988. Its downloaded smoke-log
artifact includes all three bytecode dumps, which were inspected directly:
metadata filtering/default enumeration; tick increment/duration comparison and
wrapping; shifted base-frame mip offsets/positive-size guards; current alpha;
double multiply/add followed by d2i truncation; and the exact-class/Forge metadata
exclusion. The InterpolationData javap SHA256 is
`340af8fd13b1238abb298f23e6f55d29539acd1dd70aaca2958445913054b1e9`.

Qualification is confined to the render thread. The pinned ticker queues a
RenderSystem render call when interpolation is invoked off-thread; that call
uses the ticker at execution time. An offload adapter must establish equivalent
thread/clock ownership before claiming to replace that route. The numeric
reference does not qualify off-thread timing or third-party ticker mixins.

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

## O3/O4 implementation qualification

O3/O4 are now implemented as opt-in production candidates rather than only a
numeric reference. Full public CI **#998** (commit
`f1ddc72397ed132dab16b5e953db369ba76a6ed3`, run `37447468935`, job
`112215647015`) qualifies the following bounded path:

- exact vanilla `SpriteContents` identity with absent Forge metadata is the
  admission boundary; custom/dynamic behavior retains the CPU path;
- every source mip is snapshotted once into bounded device-local RGBA storage,
  with original CPU images retained for fallback;
- source `NativeImage` mutation generation invalidates residency and forces CPU
  staging before stale GPU bytes can replace the atlas;
- discrete changes use device-local buffer-to-image copies;
- interpolation requires an enabled device `shaderFloat64` feature and uses
  same-graphics-queue compute into bounded device-local scratch followed by the
  existing transfer-destination atlas path;
- CPU retains the resolved frame schedule, `frame/subFrame` clock, visibility
  gating and off-render-thread deferral semantics;
- residency plus interpolation scratch are charged against the 128 MiB pilot
  cap, allocation/admission failures fall back, and retirement is delayed through
  the renderer's upload-safe queue;
- mixed CPU/GPU sprite ordering, transfer-write ordering and final sampled-read
  transitions are explicit. #997 passed exact pixel comparisons but Vulkan sync
  validation found missing atlas write-after-write ordering; #998 added the
  transfer-write dependency and passes validation cleanly.

The validation-enabled native oracle compares the GPU candidate directly against
the transformed CPU ticker for reordered/repeated/implicit/filtered schedules,
odd rectangular frames, zero-extent mips, hidden/first-use refresh, exact alpha
and Java-double truncation across every positive mip. Existing post-chain,
screenshot, indirect, Create Chronicles and Crash Assistant gates also pass.

## Remaining gates

O3/O4 still need matched RX 6900 XT / RADV performance and real-pack/lifecycle
adoption evidence before either path can become a default. The user's deferred
manual reload/re-entry gate remains open. The accelerated path also deliberately
does not move arbitrary mod ticker callbacks or the CPU-visible animation clock.

Future texture work should first measure #998-class residency/compute coverage,
CPU time removed, GPU copy/compute cost, fallback rate and memory peak. If
standard ticker iteration remains material after pixel work is removed, a
separate bulk GPU animation-job scheduler may be investigated with its own
CPU-visible clock/compatibility contract; it is not implicitly authorized by
O3/O4 correctness.
