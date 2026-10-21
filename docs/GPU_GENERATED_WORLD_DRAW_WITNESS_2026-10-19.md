# Integrated GPU owner qualification package

**Candidate awaiting final full CI:** `95221419486931c295b83b1efe1c98ef3df4eef7`,
full [#1088 /39680381869](https://github.com/Vorith03/VulkanMod/actions/runs/39680381869).
Artifact verification and readiness declaration are pending. Do not use an older
JAR to qualify this source. This is the sole current test package; the historical
sections below preserve evidence and do not supply current activation instructions.

## Build and internal evidence

Final artifact/size/SHA-256/log links will be recorded here after full qualification.
Previous full #1087 on3d1ddd73 passed eight-phase main/public-portal controls,
but a separate local adoption failure exposed producer-turnover readmission.
The new explicit turnover negative remained CPU1008/invalid GPU output and timed
out; the repair reuses the existing post-submit queue to reattempt only after
complete CPU upload. Actual failed-dispatch recovery is excluded from automatic
retry. Native pending-upload/exactly-once/stale/recycled/failure tests pass.

The integrated internal configuration exercises actual transformed production
mesher -> asynchronous publication -> native selector -> original region raster.
The nine same-save phases are initial REPLACE/mixed APPEND, dirty glass conversion,
actual failed-producer complete CPU recovery, readoption, camera reversal, camera
return, resource reload, save/re-entry and producer revocation/readmission. Public
IP3.0.7 uses a real same-dimension portal, not a synthetic clip-only target. Each
front-facing GPU capture requires an actually recorded portal generated GPU-owned
draw in that frame, alongside main-view adoption and zero CPU command uploads.

All internal oracles keep independent numerical/native output/pixel expectations.
There is no vertex readback/patch/reupload or light-array injection feeding rendering.
The scene is prepared/closed once, then copied for CPU/GPU to preserve identical
serialized inputs. Rapid independent fresh-world setup can produce different client
light inputs; that setup boundary is not claimed repaired. Software Vulkan coverage
is controlled correctness/adoption evidence, not representative RX performance.

## Setup and configurations

Use Minecraft **1.20.1**, Forge **47.3.0**, **Java17**, one exact VulkanMod JAR and
the same known supported modpack in both profiles. Keep `earlyWindowControl = false`
in `config/fml.toml` and `regionBatching: true` in `config/vulkanmod_settings.json`.
Native multiDrawIndirect and drawIndirectFirstInstance support is required for this
region path. The separate legacy `indirectDraw` setting is not an extra prerequisite.
Keep resolution, distances, mipmaps, packs, drivers and CPU fallback options matched.
Use the established compatible renderer-mod set; do not add another renderer replacement.

For **integrated GPU**, copy these current restart-time JVM properties, also kept in
[scripts/ci/integrated-gpu-flags.txt](../scripts/ci/integrated-gpu-flags.txt):

```text
-Dvulkanmod.gpuAnimatedTextureCopies=true
-Dvulkanmod.gpuAnimatedTextureInterpolation=true
-Dvulkanmod.experimentalUnifiedTextureUploads=true
-Dvulkanmod.experimentalGpuTextureSourcePool=true
-Dvulkanmod.experimentalGpuAnimationAdmission=true
-Dvulkanmod.experimentalGpuAnimationBatch=true
-Dvulkanmod.experimentalGpuAnimationSharedScratch=true
-Dvulkanmod.experimentalGpuAnimationMultiDispatch=true
-Dvulkanmod.experimentalGpuParticleBillboards=true
-Dvulkanmod.experimentalSectionVoxels=true
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainHybrid=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
-Dvulkanmod.experimentalGpuIndirectCpuBypass=true
-Dvulkanmod.experimentalGpuGeneratedIndirectReplace=true
-Dvulkanmod.experimentalGpuGeneratedIndirectAppend=true
```

For **CPU baseline**, use the same JAR and all19 lines with every `=true` replaced
by `=false`. Remove competing duplicate properties. Do not copy smoke/CI properties.
Experiments remain default-off in the shipped build. In both functional profiles add:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
-Dvulkanmod.debugGpuGeneratedWorldDraw=true
```

Bounded terrain diagnostics are enabled with the experimental mesher. Requested
flags are not admission or consumption evidence; preserve fallback reasons and
numerators/denominators. Required simulation, callbacks, world/light/halo capture,
connectivity, changed-input metadata, unsupported semantics and complete recovery
remain CPU-owned. Accepted texture interpolation, exact supported smoke expansion,
qualified terrain tessellation and indirect command construction/serialization/upload
bypass their corresponding CPU work. Original particle upload size is retained.

## One focused functional comparison

Start each profile from a separate copy of the SAME pre-run save. Record dimension,
position/yaw/pitch/FOV, time/weather and settings. Let terrain/light converge, then:

1. Capture matched F2 screenshots with opaque full cubes, mixed slabs/glass/fluids/
   block entities, animated textures and campfire smoke. Compare geometry, AO/light,
   color/UV/mips, animations, particle orientation and translucency ordering.
2. Repeat through an existing visible Immersive Portals aperture with eligible
   terrain and smoke. Compare both main and destination pixels. Record the portal
   type/dimensions; a scene absent from the owner's world is untested, not passed.
3. Move/turn across chunk and region boundaries and back. If convenient, briefly
   visit above build height in spectator. Break/replace cubes next to exceptions.
   Watch for stale faces, one-half APPEND flicker and incomplete CPU recovery.
4. F3+T reload, leave/re-enter the world, return to the same camera and repeat the
   portal/terrain/smoke observations. Keep logs before the next launch overwrites them.

On device loss/crash or persistent corruption, stop the GPU run and keep CPU as
recovery. Preserve both the failing scene/camera and complete logs; do not suppress
assertions or change several flags to make the observation disappear.

## Three independent verdicts

| Verdict | Evidence | Failure or missing evidence |
| --- | --- | --- |
| Visual correctness | Matched main/portal screenshots; geometry, exceptions, lighting, textures, smoke, dirty/reload/re-entry observations. | Reproducible missing/duplicated/stale geometry, wrong order/light/UV, validation error or device loss. Missing environment remains untested. |
| Adoption / CPU displacement | CPU_BYPASS_ACTIVE and success samples with cpuBypassed=true; COMMAND_OWNERSHIP_ACTIVE; GENERATED_WORLD_INDIRECT_RECORDED for relevant main/portal REPLACE/APPEND; texture/particle counters and fallback denominators. | Requested flags alone, empty scenes, zero eligible work or command markers without correct nonempty pixels. CPU recovery is judged by reason/completeness, not treated as automatic failure. |
| Performance | Same-JAR matched workload after convergence; CPU graphics time, separate upload/main GPU scopes and submission cost; render-only/tick-bearing FPS/p95/p99. | Different inputs/settings/convergence, overlapping timestamp sums or adoption used as a speedup claim. Regression and uncertainty are valid findings. |

The once-per-mode recording marker reports candidate inventory, not a GPU count
readback or selected-command count. The CI-only per-capture portal witness is not
an owner activation flag. Keep real pixels and consumption evidence together.

## Matched performance screening after functional correctness

For both profiles, use the same save snapshot and add the existing controller:

```text
-Dvulkanmod.performanceProfiler.autoBenchmark=true
-Dvulkanmod.performanceProfiler.benchmarkWorld="VulkanMod Benchmark"
-Dvulkanmod.performanceProfiler.benchmarkX=0
-Dvulkanmod.performanceProfiler.benchmarkY=192
-Dvulkanmod.performanceProfiler.benchmarkZ=0
-Dvulkanmod.performanceProfiler.benchmarkYaw=-90
-Dvulkanmod.performanceProfiler.benchmarkPitch=30
-Dvulkanmod.performanceProfiler.benchmarkSettleSeconds=60
-Dvulkanmod.performanceProfiler.durationSeconds=180
-Dvulkanmod.performanceProfiler.benchmarkAfterStagingCapSeconds=60
-Dvulkanmod.performanceProfiler.maxSamples=4096
-Dvulkanmod.performanceProfiler.slowFrameMs=25
```

Replace the existing default world/pose with the SAME representative world/pose
in both profiles; use eligible terrain/animations/smoke. Manually enter the named
singleplayer world. The controller teleports only within that named world and
checks convergence; keep screens closed and do not move. It saves/quits on success.
An aborted/nonconverged run is incomplete. This one paired screening does not
satisfy repeatability/default-promotion/release gates or prove RX6900XT net benefit.

Compare `standard_interpolation_gpu_updates`/`standard_interpolation_updates`,
`gpu_multi_*_total`, `particle_billboard_lifetime`, `gpuCandidateLifetime`, terrain
bypass/recovery/backlog and separate CPU/GPU frame classes. Difference lifetime
totals across the measurement window; they include warmup. Do not sum overlapping
GPU scopes. Keep the CPU and GPU UUID captures and record regression/uncertainty.

## Logs and untested boundaries

Preserve instance `logs/latest.log`, `logs/debug.log`, `screenshots/`, functional
`logs/vulkanmod-performance.log`, and both automated
`logs/vulkanmod-performance-benchmark-<UUID>.log` plus neighboring raw JFR/capture
files. Include exact JAR/setup, same-save confirmation and short visual observations.
A useful extraction, alongside complete logs:

```bash
rg 'VULKANMOD_GPU_(TERRAIN_(CPU_BYPASS_ACTIVE|SUCCESS_SAMPLE|FALLBACK_SAMPLE|CPU_RECOVERY)|COMMAND_OWNERSHIP_ACTIVE|GENERATED_WORLD_INDIRECT_RECORDED)|particle_billboard_lifetime|gpu_multi_|Validation Error|SYNC-HAZARD|device lost' logs/latest.log
```

RX6900XT/RADV behavior and net benefit, private resource packs, representative
owner modpack/cross-dimension or nested portal scenes, mod-injected particle/custom
callbacks and long gameplay churn are untested. Public compatibility and controlled
same-dimension software portal pixels do not certify arbitrary mod/shaderpack support.
Internal readiness is neither release completion, default promotion nor a speedup.

## Historical evidence (archived instructions; use the package above)

Earlier #1082/9a6de848, #1086/32247a7 and #1087/3d1ddd73 qualifications and negatives
remain in the ownership contract, optimization ledger, status/archive and Git history.
The older October19 record below is retained verbatim for attribution; its “next”,
“latest”, pending states and flag lists are historical and superseded.

## October 19 — reject stale queued REPLACE publication; full #1073 PASSED

**Latest qualified executable:** `85037cfdb2779294676f33ba3b951791d66dec10`; [full #1073 / run 39304235826](https://github.com/Vorith03/VulkanMod/actions/runs/39304235826) **COMPLETED / SUCCESS, attempt 1** on that executable. Forge distributable JAR artifact **12213616043**; smoke logs **12213091579**. Offline contracts, Forge compile/package, both client startup modes (including the production-queue publication smoke), native indirect/terrain/texture/particle/screenshot/synchronization/post/depth, public Create Chronicles/Immersive Portals and Crash Assistant gates passed. Private resource packs were not exercised.

**Production correctness fix:** `TaskDispatcher.scheduleSectionUpdate` previously rejected stale section generations only for staged APPEND (`stagePreservedLayer`). A completed ordinary REPLACE build could remain in the publication queue while `RenderSection.setDirty` advanced its voxel generation, without its already-queued result yet being cancelled. The accepted old upload could overwrite CPU draw parameters and compiled/visibility state ahead of the next build. All **nonnegative generation-bearing** queued section updates now reject a mismatched live generation before touching geometry or invoking the completion callback; legacy unversioned (negative-generation) consumers remain unchanged. The existing publication drain smoke now exercises the actual completed-result queue: stale REPLACE rejection, current-generation REPLACE acceptance and unversioned compatibility. Both client startup smoke modes passed on the exact executable.

**Audit boundary:** This is a bounded async-publication correctness guard, **not** elimination of CPU planning or a measured FPS improvement. The separate GPU mesher dispatch/completion paths were inspected for section/area/generation/preflight validation; no new defect was established there and their locks, queue ordering, GPU residency/retirement and atomic APPEND transaction were not modified. It is not exhaustive proof of all producer or async transitions. The real loaded-world REPLACE/APPEND consumption marker, nonzero post-cull selection, matched main-world and genuine Immersive Portals GPU/CPU pixels, RX 6900 XT adoption, displaced CPU milliseconds, added GPU costs and FPS/p95/p99 remain **unmeasured/unavailable**. Preserve all pilots default-off, 512-section/1,024-command limits, exact generation/queue/provenance/frustum, APPEND CPU exceptions, same-queue barriers, fence-safe retirement and complete CPU recovery.

**Earlier evidence stays distinct:** #1072 passed (executable `47f00dff1c8349133948dc0dd257d5771ac8217a`, JAR **12207901585**, logs **12207722559**); #1071 was cancelled; #1070 passed only on attempt 2 after attempt-1 FMLConfig null-Boolean startup failure (successful JAR **12206524656**, logs **12208005267**, failed logs **12206393088**). Preserve #1067, #1060, #1064 and older negatives including analyzed #1028 UUID `e05f2b31`. Full #1073 does not establish private-pack or user-world gates.

**Next gate:** Obtain a real generated-world witness and matched CPU/GPU main-world and genuine portal screenshots per `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`. A recorded command is not proof of selected nonzero GPU commands or correct pixels. If private worlds/RX client remain unavailable, retain that blocker and continue independently safe ownership/invalidation/readiness/fallback work only when a specific evidenced hole is found; avoid repeating settled audits or requesting isolated benchmarks. Update this repository handoff and finish each session with a **NEW complete copyable rolling handoff prompt instructing its own successor to do the same perpetually**.


## October 19 — cached producer proof revocation; full #1072 PASSED

**Qualified executable:** `47f00dff1c8349133948dc0dd257d5771ac8217a` (implementation `5308522c67c4310c4bd20b3cb0a590c4aff2834b`), [full #1072 / run 39295126051](https://github.com/Vorith03/VulkanMod/actions/runs/39295126051) **COMPLETED / SUCCESS, attempt 1**. Forge JAR artifact **12207901585**, smoke-log artifact **12207722559**. Offline contracts, Forge packaging/both startups, native indirect/cache/terrain/texture/particle/screenshot/synchronization, post/depth, public Create Chronicles/Immersive Portals and Crash Assistant passed. Private-pack steps were skipped. #1071 / run 39295004950 was **cancelled**, superseded by strengthening the smoke to require a populated cache; it is not qualified.

**Verified behavior:** changing a queued section's direct-seed status in either direction now revokes its area's producer queue proof; transferring a section to another area notifies the departing owner and advances its ownership/input revisions. The next candidate publication clears the cached table and denies GPU ownership until a fresh traversal proves the queue again. Stable direction status and same-area assignment preserve the proof. The native smoke starts with a nonempty initialized generated-mode table, verifies both direction revocations, recovery and departing/destination independence. This closes bypasses of per-slot direction/identity checks by cache fast paths; it adds no new CPU-displacement mechanism or measured speedup. Local 24 offline contracts and an extracted-production-method direction/owner check passed. Local `agent-check.sh` could not download Gradle (`Network is unreachable`); full CI supplies Forge compilation/runtime qualification.

**Preserved intervening evidence:** previous `a93d5472fdebd3576b80b8e21ef62bddccb65500`, full #1070 / run 39293185968 **PASSED ON ATTEMPT 2**; JAR **12206524656**, successful logs **12208005267**. Attempt 1 failed screenshot-client startup in `FMLConfig.getBoolConfigValue -> ImmediateWindowHandler.setupMinecraftWindow` with a null Boolean before pixel assertions (failed logs **12206393088**); same executable passed on one bounded retry. Its residency fingerprint fix additionally keys representability/GPU vertex bound so identical numeric CPU geometry cannot retain retired generated command state. Keep the failed attempt, cancelled #1071, #1067/#1060/#1064 and older documented negatives distinct from successful qualification.

**Next gate / limits:** no actual-world generated APPEND/REPLACE witness, matched main-world/genuine-portal CPU/GPU screenshots, representative RX adoption, eliminated CPU milliseconds, added GPU/dispatch/submission costs or FPS/p95/p99 benefit. Private packs and outstanding real particle portal/callback/lighting/translucency/camera/reload/frame-pressure gates remain unavailable/unqualified. All experiments remain default-off; bounds, atomic APPEND, exact provenance/frustum, queue synchronization, fence retirement and complete CPU fallback are unchanged. Follow the actual-world witness contract when evidence is available; otherwise explicitly retain the blocker and audit newly evidenced producer transitions before extending reuse. Do not repeat settled #1028 UUID `e05f2b31` tests. Every successor must update the repository handoff and finish with its own NEW copyable rolling handoff perpetuating that requirement.


## October 19 — #1069 producer reuse qualification does not close the world witness gate

Executable `41ba122c03509fbbe7d19b67d60dda48735a6723`, full #1069 (run 39291766470) passed, with JAR artifact 12205981816 and smoke-log artifact 12206581647. Its producer-state revision and same-provenance reuse skip repeated generated per-candidate numeric planning for *unchanged* regions; the default-off actual-world `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` instrumentation and all witness requirements below are unchanged. No world marker, main-world CPU/GPU reference screenshot or real portal-view pair was acquired. Do **not** interpret the native selector fixture or the producer cache smoke as actual-world draw/pixel equivalence. Continue the scene checklist below when real-world evidence is available; keep 512-section/1,024-command bounds and atomic APPEND safety.


## October 19 — generated-world draw witness, full #1068 PASSED

**Current fully qualified executable:** `0f76caf7e5377ce27edebbfc23235c5daaa0168d`; full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) **COMPLETED / SUCCESS** on that exact executable (not the later documentation HEAD). Distributable Forge JAR artifact **12203627510**, smoke-log artifact **12202694551**. Successful full-validation steps included offline contracts, Forge compilation/package, both client startup modes, native GPU indirect/terrain (including `VULKANMOD_GPU_GENERATED_APPEND_INDIRECT_OK` 509-pair selector proof and `VULKANMOD_GPU_GENERATED_REPLACE_CANDIDATE_OK`), texture multi-dispatch, particle, screenshot, synchronization/post/depth chain, public Create Chronicles with Immersive Portals, and Crash Assistant. Private resource-pack fixture coverage remains unavailable (token absent); no actual-world generated-terrain/portal pixel validation was performed by this run. Previous qualification #1066 `360434e664c13050e2eab1174673ed19525ed813` remains the regression baseline (JAR 12196457613, smoke logs 12196891834).

**Actual-world observability introduced, NOT an observed world result:** opt-in `-Dvulkanmod.debugGpuGeneratedWorldDraw=true` adds `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` only after a live loaded-world GPU-owned `vkCmdDrawIndexedIndirect` recording from current generated candidates; REPLACE, atomic APPEND pair and APPEND with no CPU exceptions are separately inventoried, including optional real Immersive Portals portal-view context. Native fixtures do not produce this marker; it does not prove the GPU selected a nonzero command after culling, actual geometry/pixels, or CPU/GPU visual equivalence. **No real-world marker/screenshot exists in this session.** The same executable skips temporary CPU `DrawCommand`/`DrawPlan` construction for missing/stale/wrong-generation GPU output during generated candidate preparation, retaining unchanged CPU completeness and fallback. Active CPU graph traversal, hashing, admitted per-candidate command planning, CPU recovery and queue/fence-safe retirement remain. Net CPU/GPU costs, adoption and RX FPS/p95/p99 are **unmeasured**; all pilots stay default-off.

**Preserved negative evidence:** full #1067 / run 39286363394 failed in `compileJava` due a newly introduced local `generatedReplace` count colliding with the method's boolean parameter; no #1067 JAR, smoke-log artifact 12204160640, offline contracts passed but all native/Forge runtime tests were skipped. Exact rename in `0f76caf7e5377ce27edebbfc23235c5daaa0168d` restored compilation; #1068 subsequently passed. Keep #1060 (obsolete 512 assertion), #1064 (bounded-overflow shader regression), older #1054/#1050–#1052/#1047/#1048/#1041/#1042 and analyzed #1028 UUID `e05f2b31`. They must not be rebranded as passed.

**Next action:** obtain actual generated-world REPLACE and APPEND production-consumption witness and controlled main-world/Immersive Portals CPU-vs-GPU scene screenshots using `docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md`, or explicitly retain the external world/AMD hardware gap. Do not demand isolated hardware owner benchmark before integrated verification. After real-world geometry and portal proof, safely eliminate more repeated numeric CPU planning using a complete producer invalidation contract; preserve atomic APPEND fallback and actual-world particle portal/mod callbacks/translucency/lighting/camera/reload/frame-slot gates. Integrated release remains **incomplete**. Every following work session must refresh `docs/NEXT_SESSION_PROMPT.md` and finish with its own new copyable rolling handoff prompt perpetuating that requirement.


## Updated validation checkpoint: #1067 failed; #1068 pending (October 19)

Full [#1067 / run 39286363394](https://github.com/Vorith03/VulkanMod/actions/runs/39286363394) **FAILED before native/Forge runtime**: public offline contracts passed, but `compileJava` rejected the newly added local `int generatedReplace` because that publisher method already has a boolean `generatedReplace` parameter. This is a **Java source naming error, not a negative GPU shader/pixel/synchronization result**. Artifact **12204160640** is failed-run smoke logs; **no #1067 JAR exists**. The targeted, behavior-preserving rename landed in corrected executable `0f76caf7e5377ce27edebbfc23235c5daaa0168d`. Full [#1068 / run 39287370625](https://github.com/Vorith03/VulkanMod/actions/runs/39287370625) was **queued/in progress** at this documentation checkpoint, with no passed result or verified JAR/smoke artifacts yet. Until full #1068 succeeds, the last qualified executable remains `360434e664c13050e2eab1174673ed19525ed813` in full #1066 (JAR 12196457613; logs 12196891834). **Next: check #1068 conclusion and exact artifacts before claiming qualification; fix any subsequent actual failure step.** All runtime generated-world/portal pixels and integrated profitability remain open.


## What was implemented, and what was not proved

Executable `0f76caf7e5377ce27edebbfc23235c5daaa0168d` (initial witness `2f8a45397639d8a616db3b0e08adc915a08a50b8` failed #1067 Java compilation; corrected full #1068 / run 39287370625 pending) extends the *existing* candidate/selector/indirect draw path, not a parallel renderer. It adds an opt-in runtime witness:

```text
-Dvulkanmod.debugGpuGeneratedWorldDraw=true
VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED
```

The marker is emitted **only after** a Vulkan indexed-indirect call has been recorded in an actual loaded `WorldRenderer` with accepted GPU command ownership and at least one current, ready, exact-generation GPU-generated candidate. It is never emitted by startup/planner/native metadata fixtures alone. It reports region, terrain layer, candidate generation, bounded command count, resident generated REPLACE count, atomic APPEND CPU+GPU pair count, zero-exception APPEND count, whether the optional Immersive Portals render-context API is available and whether it currently reports a portal-world view. A process-lifetime bitmask limits reports to at most one per mode per main/portal context. The three mode bits refer to **candidate inventory**, not GPU-frustum-selected command counts. Existing CPU graph traversal and per-candidate planning remain.

A record proves a producer-backed candidate set reached the **actual-world Vulkan draw recorder** with current GPU-owned indirect output. It **does not** prove any particular command was nonzero after compute culling, indexed vertex fetch, raster output, visual correctness or portal pixel equivalence. The GPU selector is asynchronous; this diagnostic adds no readback, queue idle, new descriptor, or synchronization barrier. The optional diagnostic must stay off by default.

`RegionBatchSmokeTest.verifyGeneratedWorldWitnessGate` checks no-world, CPU-fallback, unrecorded and ordinary-only suppression plus distinct REPLACE, paired APPEND and zero-exception APPEND bits. This remains a narrow predicate fixture, *not* a generated-world run.

## Real-world integration qualification still required

Use a pinned, fully verified Forge 1.20.1 JAR and known world/camera scene. Select a representative Create Chronicles location containing qualified generated full cubes, mixed CPU exceptions, regions crossing the camera/frustum boundary, and a genuinely visible Immersive Portals view of terrain. Do not invent a mixed/portal sample if the world lacks one. Preserve a fixed world/save, client resolution, graphics settings, resource packs, FOV, camera coordinates/yaw/pitch and stable weather/time as closely as practical across restarts.

The GPU pilot requires relevant gates (in addition to modpack settings):

```text
-Dvulkanmod.experimentalSectionVoxels=true
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainHybrid=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
-Dvulkanmod.experimentalGpuIndirectCommands=true
-Dvulkanmod.experimentalGpuIndirectDraw=true
-Dvulkanmod.experimentalGpuIndirectCpuBypass=true
-Dvulkanmod.experimentalGpuGeneratedIndirectReplace=true
-Dvulkanmod.experimentalGpuGeneratedIndirectAppend=true
-Dvulkanmod.debugGpuGeneratedWorldDraw=true
```

Keep all pilots **default-off** in released builds. Compare a CPU reference launch with all GPU owner pilots off to a GPU owner launch with flags above, from the same camera/main-world view and, separately, the exact portal scene. Take comparable screenshots and save the complete `latest.log`; require actual markers for the targeted mode and context *in the GPU run*. Example focused extraction:

```bash
grep -E 'VULKANMOD_GPU_(TERRAIN_MESHER_ACTIVE|COMMAND_OWNERSHIP_ACTIVE|GENERATED_WORLD_INDIRECT_RECORDED|TERRAIN_CPU_RECOVERY)|Validation Error|SYNC-HAZARD' latest.log
```

Compare missing/duplicated faces, CPU exception ordering, flicker during rebuild and rapid region/portal view changes, stale generation fallback, unloaded/reloaded chunk behavior and resource reload/re-entry. If the portal-specific marker is absent, classify portal consumption **not exercised**, not passed. An ordinary world marker cannot certify a portal view. If CPU/GPU screenshots differ, preserve camera and log evidence; never suppress a mismatch. If screenshots appear identical, visual correctness is a narrow observed scene verdict, not exhaustive GPU count equivalence.

Do **not** demand a standalone owner benchmark before this meaningful real-world gate. Once real generated mode/pixel coverage exists, compare matched integrated CPU graphics/admission, extra compute and submission cost, FPS/p95/p99 and sustained fallback distributions. Do not repeat the analyzed #1028 UUID `e05f2b31`.

## Preserved safety and remaining engineering work

Missing/stale/unready GPU output, incomplete APPEND CPU exceptions, untracked queue identity, wrong graph/direct provenance, changed exact portal frustum, unsupported layers, bounded overflow, synchronization and retirement remain fail-closed. No change in this slice permits CPU half-only APPEND or incomplete REPLACE drawing. `RegionDrawBatch.indirectCandidate` now skips creating temporary CPU `DrawCommand`/`DrawPlan` when residency is missing, invalid or different-generation; the effective CPU-only result is unchanged, but candidate walking, hashing and numeric planning for **admitted** GPU output still occur.

Unresolved: actual world GPU-selected command count and portal pixel equivalence, GPU producer invalidation contract sufficient to eliminate recurrent candidate planning, particle portal/mod callbacks/translucency/light/camera/reload/descriptor pressure, private pack fixtures and integrated RX 6900 XT CPU/GPU/frame-time profitability. Previous #1066 passed and earlier #1060/#1064 negative fixture evidence remain preserved.

Every future work session must refresh `docs/NEXT_SESSION_PROMPT.md` and end with a new copyable handoff repeating the same perpetual requirement.
