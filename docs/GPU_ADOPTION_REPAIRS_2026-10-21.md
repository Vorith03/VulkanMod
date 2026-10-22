# GPU texture and particle adoption repairs — October 21, 2026

## Final internal qualification — full #1092 SUCCESS

**Executable:** `272b7f81f09a05e4796e008d64a33c73a5270505`. **Workflow:** `.github/workflows/build.yml`,
[full #1092 /39716738464](https://github.com/Vorith03/VulkanMod/actions/runs/39716738464),
attempt1, SUCCESS. Documentation-only descendants preserve this executable.
**JAR artifact:**[12400315181](https://github.com/Vorith03/VulkanMod/actions/runs/39716738464/artifacts/12400315181),
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1092-g272b7f81-all.jar`.
Downloaded runtime JAR **11,103,766 bytes**, SHA256
`2eedcf35295de61e4ecac2fad97d19b84c5813f2734838d7a8db884079fc6e88`.
The download payload itself is the runtime JAR (META-INF/mods.toml at its root),
not a wrapper containing a single mod JAR. Native logs artifact12399703822,
SHA256 `d28a0e712dd723c67630885aee9d810bc6e1deee9211c55075d552c343fe7041`.
JAR and nested lwjgl-vulkan CRCs, manifest1092-g272b7f81, Java17 major61,
MC[1.20.1,1.20.2)/Forge[47.3.0,48), Mixins, Linux/Windows VMA+shaderc
native entries independently checked. All24 CI/offline contracts, build/package,
both Forge startups, native compute/indirect, post/depth/readback, world/portal,
public Create Chronicles and CrashAssistant compatibility gates PASSED.
**Three private fixture steps skipped**; no private resource-pack coverage claim.

| Native evidence | Result on the final executable |
| --- | --- |
| Texture interpolation | Six transformed-ticker cases with successful GPU calls and exact all-mip atlas pixels; original clocks/alpha/fallback preserved |
| Shared texture resources | SHARED_SCRATCH and MULTI_DISPATCH markers present under0xffffffff host limits |
| Multi-job acceptance | Existing native assertion enforces at least322 accepted jobs, two initial jobs in one dispatch, >=9 table wraps and no stress fallback; exact final atlas pixels |
| CPU texture displacement | Real transformed ticker selects GPU and cancels original CPU pixel path; native admission/custom/mutation/temporary CPU fallback oracles passed |
| Particle production result | `VULKANMOD_GPU_PARTICLE_WINDOW_PRODUCTION_OK`:87 GPU quads in actual uploader buffer, forced nonzero32KiB descriptor window, actual drawn slice matches geometry/UV/color/light oracle |
| Particle lifetime fixture counts |647 GPU quads,131 compute batches,2 nonzero-window batches,267 deliberate CPU-recovery quads,2 deliberate descriptor rejections; zero capacity/workgroup/range rejections |
| Particle CPU displacement | Successful Drawer branch draws the compute-written original buffer; native GPU delta proves uploader acceptance without CPU expansion; sorting/callback/failure recovery preserved |
| Terrain preservation |9/9 vanilla plus9/9 public portal phase pairs independently recompared, each854x480, exact CPU/GPU words; GPU owner/cpuCommandUploads0 and portal ownership witnesses present |
| Synchronization |All14 archived top-level Vulkan logs scanned:0 Validation Error and0 SYNC-HAZARD matches |

Counts are **native fixture evidence**, not owner modpack adoption fractions or
measured CPU milliseconds saved. The final candidate is IMPLEMENTED and
INTERNALLY QUALIFIED, ready for owner testing. OWNER VERIFIED, PERFORMANCE
QUALIFIED and RELEASE READY remain OPEN. There is no remaining known actionable
internal failure in this repair scope. The only next evidence-changing step is
the targeted owner package below; do not manufacture unrelated terrain/refactor work.

## Evidence and corrected root-cause model

Owner #1088 integrated19 UUID `221fd7a2-9d5b-4fe4-958b-0e13dac88d87`
recorded zero texture interpolation and zero particle expansion. Texture scratch
was physically allocated (4879.230 KiB); 1505 resident sources and 2810697
DISPATCH_FAILED returns exclude a simple global-disabled explanation. Old
counters did not distinguish individual dispatch preflight failures. Raw owner
logs remain private and are not committed.

A shared **unsigned Vulkan limit read as signed Java int** defect exists in:

- Particle `groups > maxComputeWorkGroupCount(0)`.
- Texture per-mip dispatch and multi-job admission workgroup guards.
- Texture source/scratch page range admission.

Mesa RADV source at commit `4cfe08e583893bed58879450d98d8627be78868f`
sets `maxComputeWorkGroupCount = {4294967295,65535,65535}` and
`maxStorageBufferRange = UINT32_MAX`:
https://chromium.googlesource.com/external/gitlab.freedesktop.org/mesa/mesa/+/4cfe08e583893bed58879450d98d8627be78868f/src/amd/vulkan/radv_physical_device.c

LWJGL exposes each uint32 as Java int; `0xffffffff` becomes -1. Consequently
`1 > -1` rejects even a one-group dispatch. Shared scratch admission also rejects
all positive pages against -1, explaining standalone scratch allocations. The
old assertion that <=1024 groups rules out the workgroup branch was incorrect:
it assumed a correctly interpreted unsigned limit. This is a concrete source
and reproducible host-admission defect matching both owner symptoms, **not a
readback of the owner's installed driver limits**. New lifetime summary prints
unsigned storage range, workgroup X limit and descriptor alignment, plus bounded
per-reason rejection counters, to distinguish any remaining owner failure.

## Implementation and safety

`550a510c6f2ed833eb80e8e03a881ab708faf270` already survived interruption.
It binds aligned storage descriptor windows in the existing particle vertex
buffer. Shader list/vertex indices are relative to the descriptor base; uploaded
bytes, draw offsets and barrier intervals stay absolute. Total end is checked
against Integer.MAX_VALUE before reservation/narrowing. Descriptor windows are
not updated in place after recording: changed windows allocate another set;
old sets are charged against the existing 64-set bound until frame retirement.
Resize invalidation and original CPU recovery remain.

`b767223cac780e7753305747cb85cffe45aa6f3b` widens the affected uint32 limits
with Integer.toUnsignedLong. It adds missing standalone texture descriptor-range
preflight, retains source offset/alignment and mip/atlas bounds, and adds ten
lifetime rejection reasons (first reason logged once; counters in the existing
texture profiler summary). No shader, allocator, scheduler or timing semantics
were replaced. All experiments remain default OFF. Terrain code is unchanged.

## Review of production consumption and ownership

`GpuAnimatedTextureResidency.interpolateToAtlas` returns success only after its
binding accepts the compute/copy operation; SpriteAnimationState propagates that
success through the transformed SpriteTicker. SpriteInterpolationMixin cancels
the original uploadInterpolatedFrame on GPU success before the CPU bulk or
vanilla pixel loop. Rejection still executes those CPU paths. Native atlas reads
compare exact mips to the independent Java-double/alpha/frame oracle; grouped
job acceptance is committed only after metadata staging and ends in queued atlas
consumption on the same graphics queue. Scratch/source leases and queued reads
retain their established upload-completion retirement ordering.

`Drawer.drawParticles` calls expand on the original shared frame-slot vertex
buffer. Success draws that same buffer using its absolute getOffset; only the
false branch calls batch.expandCpu and ordinary draw. The descriptor base changes
compute-relative addressing only. Host-write→compute and compute→vertex barriers
retain absolute touched spans. Existing tests exercise compact+ordinary mixed
streams, sorted CPU materialization, callback mutation, nonunit orientation,
packed light/color, pool exhaustion, actual frame-fence retirement, device-idle
teardown and buffer generation changes. No additional production wait/readback
or mutation of a descriptor already recorded was introduced.

The shared root cause is unsigned host admission, corrected directly with
Integer.toUnsignedLong at existing ownership sites. The particle descriptor-window
fix is a separate real range-coverage improvement. No new shared allocation
architecture is justified. Existing terrain geometry/indirect/CPU recovery code
is untouched; its integrated gates still have to pass on the same new executable.

## Regression and qualification evidence

- Local Java17/offline: all 24 contracts passed, including atlas transfer order,
  source lease/admission, interpolation oracle, fallback and attribution.
- Full #1089 / run39713898920 **SUCCESS** on 550a510c. Downloaded/CRC-inspected
  logs show 647 native GPU particle quads, 131 compute batches, one nonzero-window
  batch, two deliberately descriptor-rejected batches and existing CPU recovery.
  Shared texture and multi-dispatch markers pass. Top-level logs contain zero
  Validation Error/SYNC-HAZARD matches. This qualifies the preserved particle
  window slice, not the successor executable.
- Successor native texture oracle models uint32 limits 0xffffffff, executes tiny
  real dispatches and compares actual atlas mip pixels through transformed Forge
  tickers, CPU interpolation bypass and original mutation/custom/fallback cases.
  Texture tests restore the actual reported limits in finally. These workloads
  remain below Vulkan-required minimum device capabilities.
- Successor particle first pass combines 0xffffffff group limit with an artificial
  32 KiB storage window and a 32 KiB prefix. The old signed guard would reject;
  a zero-offset descriptor also cannot cover the draw. The test compares native
  output and production BufferUploader consumption, then restores limits.
- Full #1092 / run39716738464 on `272b7f81f09a05e4796e008d64a33c73a5270505` **PASSED**. #1091 was superseded/cancelled; use the final qualification table above. Private fixtures remain
  external gates. Local Gradle currently lacks a complete cached dependency set.

The first successor attempt #1090 /39715651273 failed compilation because
LWJGL3.3.1 has no scalar/array struct setters. Fixture correction `c9b6ef70`
uses MemoryUtil.memPutInt plus the writable IntBuffer view and still restores
values in finally. An independent Java17 probe compiled and ran against exact
LWJGL3.3.1 jars: values65535, INT_MAX,0x80000000,0xffffffff round-trip through
actual VkPhysicalDeviceLimits memory; the old signed comparison rejects both
high-bit values, while unsigned admission accepts1024. This probe does not
substitute for native compute/pixel qualification.

`272b7f81` closes a validation gap in the interrupted test: the nonzero-window
result is now also forced in BufferUploader's own existing frame-slot buffer,
with a successful GPU/window counter delta and a geometry/UV/color/light readback
of the absolute slice used by the actual draw. Expected marker:
`VULKANMOD_GPU_PARTICLE_WINDOW_PRODUCTION_OK`. The standalone direct-compute
oracle remains too. Tests access the private Drawer buffer only via fixture-local
reflection; production gains only an existing-pattern package-private smoke
counter accessor. Focused javac --release17 compilation of all five affected
renderer/profiler classes passed against exact LWJGL3.3.1 and mapped Forge1.20.1
classes (30 missing Dist annotation warnings on the reduced local classpath).
The full Gradle dependency bootstrap exceeded its240s bound locally; no full
local build/native pass is claimed. Full CI remains mandatory.

## Owner package after successor full qualification

Use the ONE build1092 JAR identified by the completed qualification record above,
Java17 / Forge47.3.0, earlyWindowControl=false, regionBatching=true. Replace the
old VulkanMod JAR; do not install two copies. Required properties are in
[OWNER_GPU_ADOPTION_ON.jvmargs](OWNER_GPU_ADOPTION_ON.jvmargs); the matched control
is [OWNER_GPU_ADOPTION_OFF.jvmargs](OWNER_GPU_ADOPTION_OFF.jvmargs). Those files
have exactly the current 19 feature flags, all true or all false, with identical
profiler/controller properties. Do not append contradictory old flags.

Keep framebuffer **2552x1374**, render32/simulation12, VSync OFF, cap260, the same
modpack/save snapshot and fixed spectator scene in `VulkanMod_Benchmark`.
The controller waits >=60s, requires 10s quiet terrain, captures180s and exits.
Do not move/open screens during the measured interval.

For the functional launch only, change `-Dvulkanmod.performanceProfiler.autoBenchmark=true`
to `-Dvulkanmod.performanceProfiler.autoBenchmark=false` in the ON profile.
Use that launch for a targeted functional check: watch interpolated
animated textures (e.g. flowing lava/water) and eligible campfire smoke, before
and after F3+T reload and world exit/re-entry. Check no corruption, missing smoke,
wrong rotation, color/UV/light errors or broken timing. Use the actual benchmark
scene for the automated capture; if visuals fail, stop before performance pairing
and retain latest/debug/benchmark logs. A small dedicated campfire scene is needed
only if the existing scene has no eligible particles; the old owner run already
had millions of eligible quads. Record which reload/reentry observations occurred.

Required changed-build adoption evidence:

- `standard_interpolation_gpu_updates > 0`, `resident_interpolation_dispatches > 0`;
  exact ticker cancellation is internally tested; owner CPU texture tick cost is
  separately measured rather than inferred from dispatch count.
- `gpu_multi_accepted_total > 0` when suitable shared jobs occur; inspect unsigned
  limit and `gpu_interpolation_reject_*_total` if interpolation remains zero.
- `particle_billboard_lifetime gpu_quads > 0`, `compute_batches > 0`, supported
  packed work consumed by production draws; inspect workgroup/range/descriptor
  and CPU recovery counts. Windowed batches may be zero on unconstrained hardware.
- Preserve unsupported/custom simulation, callbacks and sorting CPU boundaries.

After functional success, restore `autoBenchmark=true` and capture ON. Check
the adoption criteria in that automated capture; if either feature is still at
zero, return its new logs before spending time on the control. After adoption
succeeds, run the matched OFF profile on the SAME JAR
and identical save snapshot, scene, settings and profiler. Compare average/final
windows, >25ms frames and frame pacing, CPU world/texture/particle cost, separately
scoped GPU and upload costs, adoption and recoveries. One pair is screening;
repeat only if variance or a regression needs discrimination. There is no causal
speedup, owner verification, performance qualification or release/default-enable
claim from synthetic CI. Supply the new capture logs and visual observations;
no unchanged old logs or broad terrain retest is requested.

## Status vocabulary

Implemented: eligible production GPU output replaces corresponding CPU graphics.
Internally qualified: required automated compatibility/native/correctness passed
for an exact executable. Owner verified: actual RX modpack visuals/adoption passed.
Performance qualified: controlled measurements establish acceptable benefit.
Release ready: separate long-term compatibility/stability/default gates satisfied.
