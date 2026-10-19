# VulkanMod — Continue GPU-first renderer ownership (rolling handoff)

Continue implementation and verification on `Vorith03/VulkanMod` branch
`forge-1.20.1`. The mission remains an integrated GPU-first renderer
release: GPU-owned production results must replace eligible CPU graphics
calculations **and the replaced CPU calculations must stop executing**.
No queue-size/BufferBuilder micro-tuning, and do not declare the release
complete after one correctness-qualified subsystem.

## Recover current authoritative state

Follow `AGENTS.md` Section 3A against live Git/CI. Read
`ROADMAP.md`, `AGENT_STATUS.md`,
`docs/OPTIMIZATION_EVIDENCE_LEDGER.md`,
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`,
`docs/GPU_TEXTURE_SHARED_SCRATCH_2026-10-18.md`,
`docs/GPU_TEXTURE_MULTI_DISPATCH_2026-10-19.md`,
`docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`,
`docs/GPU_TERRAIN_INDIRECT_DRAW_HANDOFF_2026-09-15.md`,
and this handoff. Inspect the *delta* from the checkpoint, including
intervening executable commits and completed/pending/red CI, rather
than repeating settled analysis. Live Git and runtime evidence win.

## Latest fully qualified milestone

**Executable `c06f1cbc9c57a09b0df819edc639022bef049096`**,
full **#1053 / [run 39216055025](https://github.com/Vorith03/VulkanMod/actions/runs/39216055025) PASSED**,
Forge JAR artifact **12169313491**, smoke logs artifact **12169019219**.
Native screenshot validation emitted **`VULKANMOD_GPU_TEXTURE_MULTI_DISPATCH_OK`**
and prior **`VULKANMOD_GPU_TEXTURE_SHARED_SCRATCH_OK`**.
Public offline contracts, Forge package and both Vulkan client startups,
native texture/particle/terrain pixel+sync gates, screenshot,
Create Chronicles/Immersive Portals and Crash Assistant passed.
Private pack fixtures skipped; representative RX 6900 XT remains unmeasured.

Production implementation `cf3351f726ed143e1006b0504b92bb157020e322`,
fixes `4718529021079515bba58fcdef55ee004cbee3f4` (offline attribution
extractor), `c3b4a43d1f50db011b9e51156951a6fe231918b5` (native
metadata-vs-CPU-staging assertion), and `c06f1cbc` (unrelated
procfs child-exit race in CI planner).
Negative #1050, #1051 and #1052 remain meaningful and were
**not** native shader pixel/sync failures. Preserve earlier
#1047/#1048 extractor negatives and #1041/#1042 particle descriptor
negative evidence; do not repeat #1028 UUID `e05f2b31`.

Default-off `-Dvulkanmod.experimentalGpuAnimationMultiDispatch=true`
requires GPU animation interpolation, unified GPU texture uploads,
all-mip animation batch, pooled immutable sources, and shared scratch.
Accepted sprite interpolations stage one 48-byte immutable GPU job row;
up to 32 jobs on one page execute in a single workgroup-Z compute
dispatch at the atlas-copy drain. The exact Java-double RGB/current-alpha
mip pixel contract, scratch/lease lifetimes and ordering/fallback remain.
Native smoke proves two accepted distinct sprites -> one real dispatch,
exact pixels, repeated updates and later overlapping O3 overwrite.
Accepted GPU jobs still bypass duplicate CPU pixel interpolation.

## Next useful engineering boundary

The job metadata allocation is **append-only per 4 MiB shared scratch
page**, subject to a 128 MiB pending-aware physical limit. Sustained
high-rate animation can exhaust table headroom even without retiring
sprites, forcing fallback. Investigate and implement (if coherent)
**bounded reusable per-page job table slots** with explicit GPU
compute-read -> transfer-write ordering across copy epochs,
command buffers, submissions and safe retirement. Prefer a fixed
table/slab or justified frame-fence generation rather than assuming
a host copy queue flush means GPU completion. Verify immediate reuse,
long multi-sprite sequences, mixed O3/CPU uploads/overlapping atlas
destinations, mip/alpha, forced flush, scratch lifetimes and cancellation
of CPU mixing; require a native pixel+sync and sustained fallback oracle.
Preserve standalone and CPU fallback; do not trade correctness for
descriptor savings or attempt a hidden default enablement.

After texture integration is stable, advance the unfinished real
Immersive Portals/mod-injected particle lighting, translucency, reload,
frame-slot and resource pressure tests; and terrain generated REPLACE/
atomic APPEND candidate ABI integrated with GPU indirect selection.
Keep producer graph/direct provenance, queue containment, exact portal
volumes, listener detachment, stale owner isolation, readiness,
generations and lazy CPU fallback. Java graph traversal and active
candidate row serialization/hash still remain CPU-owned.

**Performance remains unknown.** Full #1053 is correctness evidence,
not measured RX adoption, CPU graphics time saved, new GPU/submission
cost or FPS/p95/p99. Collect matched representative hardware data only
after meaningful integrated qualification; no isolated owner benchmark.

## Mandatory rolling handoff forever

Before ending the next session, publish a coherent recoverable Git
milestone on `forge-1.20.1`; record the exact qualified executable,
full CI/run and Forge JAR/log artifact IDs (or red/pending blocker)
and update `AGENT_STATUS.md`, `ROADMAP.md`,
`docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, affected contracts and
`docs/NEXT_SESSION_PROMPT.md`. Include a **NEW copyable continuation
prompt in the final response**.

**That NEW prompt must explicitly instruct its successor to finish
with another freshly updated rolling handoff prompt. Every subsequent
session must preserve the same successor-handoff requirement.**
