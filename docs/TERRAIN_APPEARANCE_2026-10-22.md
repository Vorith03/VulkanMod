# Terrain appearance: staging turnover candidate — October 22, 2026

## Scope and disposition

Owner-directed initial-population work supersedes the previous owner-test-only
next action. Preserve the internally qualified texture/particle executable
272b7f81 / full1092 as the comparison baseline. All experiments remain OFF by
default. This report does not establish the 2–3s nearby / 5s intended-view target.
The complete appearance mission remains OPEN.

## Recovered evidence

Branch checkpoint a9e7362 was clean and its executable ancestor272b7f81 passed
full1092 /39716738464 (live API conclusion checked). PR12 is the separate profiler
branch and is untouched. No overlapping terrain PR was present.

Existing private owner UUID221fd7a2 evidence was read locally; raw logs are not
committed. Its existing report remains authoritative for the qualified caveats.
During population the first staging-full sample precedes later CPU snapshot-store
and GPU input-upload rejections. In the stationary capture, staging is2048/2048,
22149/32768KiB with7392 initial lifetime rejections; initial scheduled26552,
published26544. Mean build queue/build/publication handoff64.7/5.6/23.2ms are
cumulative diagnostics, NOT first-draw latency or per-stage critical-path shares.
The benchmark waits at least60s and a10s quiet window. Its ~71s settling value
must not be presented as terrain appearance duration.

A server join timestamp, client position packet, first GPU publication, benchmark
terrain-visible notice and ModernFix in-game notice are different events. Their
ordering cannot supply the requested world-entry origin or coverage denominator.
No old owner log supplies matched first-consumption coverage milestones, section
input-ready timestamps, first usable frame, peak initial memory, or worst initial
stalls. Repeating analysis cannot recover events that were never recorded.

## Three workstreams and critical-path assessment

| Workstream | Concrete evidence and action | Limit |
| --- | --- | --- |
| Staging / publication | CPU snapshots remain until section invalidation despite having a one-shot dispatch consumer. Snapshot cap visibly saturated. Return consumed snapshots by identity; failures also return their staging. | Contribution to end-to-end appearance is unmeasured. Existing bounded worker backlog, publication budget and early wakeups are retained; no budget increase. |
| GPU meshing adoption | Worker staging preflight rejects at the cap. Existing dispatch reads CPU snapshot synchronously; async completion retains numeric plans/output reservations, not the snapshot. Retire matching GPU input slices through existing frame fences after dispatch attempt. | Fixed region GPU pages remain allocated for reuse under the unchanged32MiB budget; global distribution/output reservations and unsupported models can still limit adoption. No general model expansion without timing/semantic evidence. |
| CPU visibility / draw prep | Existing cached graph dirty scheduling, coarse-frustum reuse, producer geometry revisions, cached generated candidates, GPU selection and CPU command bypass already exist and are preserved. | No first-population evidence isolates this stage as material. Do not duplicate those systems or infer its cost from aggregate world-render CPU time. |

This identifies an avoidable lifetime blockage, not the largest overall delay.
The missing input-to-draw trace prevents a defensible overall ranking. World
creation/delivery/lighting are not modified.

## Ownership correction

The post-submit bridge dispatch attempt consumes the CPU snapshot synchronously.
Its finally path removes that exact snapshot only; a replacement survives a delayed
consumer. Matching-generation GPU voxel/lighting records are revoked immediately,
but physical slices remain allocated until the EXISTING MemoryManager frame
retirement callback. Graphics queue ordering is upload -> helper compute -> main
frame submission/fence -> reuse. Generated output, visible handoff, APPEND CPU
exceptions, model resources and complete CPU recovery remain independent.

Input publication failures never enter dispatch; their existing deferred recovery
callback returns captured staging by identity even for complete-CPU/shadow builds
that do not need recovery. No new allocator/scheduler, no raised byte/entry/page
caps, no visibility/default/config changes, no blocking production fence wait.
Non-mesher/debug voxel residency keeps its existing behavior.

The native raster fixture now stages its worker preflight BEFORE submitting input
uploads. Automatic post-submit dispatch is the production path; a second manual
dispatch is removed. It requires CPU staging gone and GPU input records revoked
while the original mesher-output -> indirect production pixels/CPU-bypass and
full-ring reuse/recovery oracles remain intact.

## Validation and pending qualification

Implementation a4dcf440 is in full1093 /39809021545. Follow-up failure-path cleanup
and GPU generation/lifetime assertions are being batched for final qualification.
Do not distribute1093 as the final executable until its exact descendant passes.

Local Java17 extracted PRODUCTION RegionVoxelStore/SectionVoxelSnapshot/layout
with configuration-only stubs passed4096 turnovers under a1-entry budget,
capacity rejection while occupied, immediate CPU capacity return, idempotent
release, delayed-consumer replacement protection and zero residual bytes/entries.
All24 existing offline contracts passed. These are bounded CPU lifetime tests,
not actual-world appearance results. Local Gradle bootstrap was recovered using
proxy-downloaded tooling, but ForgeGradle plugin resolution failed; no local full
Forge/native build is claimed. Remote full CI is required.

## Measurement contract before any speed claim

Use identical immutable save snapshots and fixed camera/projection/settings for
previous qualified272b7f81 and the final candidate. Compare with identical feature
properties (use OWNER_GPU_ADOPTION_ON.jvmargs in BOTH; do not compare19-off with19-on
to attribute this patch). Keep2552x1374,D32/S12,VSyncOFF,cap260 and same modpack.
Initial fresh process, world reentry and warmed filesystem/driver caches are
separate strata. Never drop privileged caches or preload candidate geometry.
Existing saves and newly generated terrain need separate trials/denominators.

Define nearby as section centers within64 horizontal blocks of the fixed camera,
intersected with its EXACT converged main-view reference section set. Intended
view is that complete reference set atD32, full world height, fixed projection,
existing graph/portal/frustum rules. Reference membership must come from nonempty
geometry actually consumed in an untimed CPU correctness reference; empty
sections never contribute progress. Fully occluded/excluded sections are inventoried
separately. Unsupported GPU sections stay in the denominator via CPU geometry.
Unavailable reference inputs stay outstanding; do not shrink the denominator to
delivered/eligible/published sections. Freeze/persist reference section identities
and generations with the save/camera and report exclusions and unresolved holes.

Origin A is first client frame rendering the joined world (not server join, task
admission, first nonempty section, or benchmark warmup). Record first usable frame
separately, with a successful presented world image and responsiveness observation.
For each reference section, origin B is first availability of all required world,
neighbor/halo and lighting inputs. Record admission, preparation, queue/service,
upload-submit, dispatch, ready/publication and first actual production consumption.
An indirect recording alone does not prove nonzero GPU selection or raster output.
Coverage50/90/99 timestamps use first correct consumption and retain hole inventory;
99% is not complete. Report both timelines; missing input timestamps stay unknown.
For generation changes discard stale results and account for the replacement.

This contract is specified, but its full input/draw event collection is NOT yet
implemented or exercised. The old TerrainPopulationTracker ends at publication
and cannot supply it. Do not relabel existing counters as this measurement.

## Next gates

Finish final native/Forge/public compatibility qualification and inspect actual
artifact bytes. Keep failed/skipped/private checks explicit. Then implement and
exercise the missing bounded input-to-consumption collection against this frozen
reference before requesting the full owner timing comparison. Exact owner save /
302-mod runtime and RX performance are unavailable here. The requested appearance
before/after, peak initial memory/responsiveness comparisons, and minimal fully
specified appearance-test package remain OPEN; a new stationary FPS run alone
will not close them. No promise of2–3s/5s or release readiness.

## Provisional owner package: staging/visual smoke, not performance qualification

Use only after final full qualification succeeds and exact artifact/hash is filled
in below. This is a narrow changed-build hardware check, not a substitute for the
missing section-event coverage comparison.

1. Use candidate e6b9b35b's final qualified JAR; previous272b7f81/full1092 is the
   comparison executable. Never install both. Keep config/fml.toml
   earlyWindowControl=false and the existing VulkanMod config/modpack unchanged.
2. Both executables use identical OWNER_TERRAIN_INITIAL.jvmargs. It keeps all19
   feature flags and profiling identical, disables stationary automation, removes
   capture duration expiry, and emits1s windows. Restart Prism's game process for
   each run. Keep the window focused and avoid settings/reloads during timing.
3. Restore the SAME saved VulkanMod_Benchmark snapshot with spectator at0/192/0,
   yaw-90,pitch30 before each process. Do not teleport after entry or preload the
   candidate. Keep2552x1374,D32/S12,VSyncOFF,cap260. Record natural warm-cache
   state/order; do not drop caches. New generation and world reentry are separate
   tests, not pooled with this existing-save initial-entry pair.
4. Record the screen before joining through30s after first rendered world frame;
   keep the camera fixed. Capture a converged reference image around60s. Note holes,
   incorrect geometry, severe stalls or loss of responsiveness. This video can
   establish visible first-frame/nearby impressions and frame timestamps; it cannot
   establish the missing per-section denominator/input-ready latency by itself.
5. Leave60s for staging/retirement turnover, then exit normally. Return new
   latest.log, debug.log, vulkanmod-performance.log (or actual configured profiler
   output), video/reference image, trial order and observed defects. Check new
   staging/cpu_input_released summaries, input/upload/output rejection reasons,
   staging occupancy, actual GPU publication/draw markers and CPU bypass. A freed
   cap with rising output failures or worse frame pacing is not a proven win.

Do not repeat old stationary captures as the appearance test. A single pair is a
smoke check; repeat/order-balance only if observed variance affects the conclusion.
The full appearance qualification still requires the event collection described
above. No owner check is claimed performed here.
