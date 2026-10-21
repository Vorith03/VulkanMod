# Actual-world generated indirect recording witness — October 19, 2026

## October21 — real loaded-world main-view qualification

Source e1abcf39f8e9e993d3b21e0fa50a4e6543137a47 locally passes complete native
indirect/screenshot suites with zero validation errors/hazards. SAME-save CPU/GPU
controls match every pixel in seven640x360 phases: initial REPLACE/mixed APPEND,
dirty conversion, complete CPU recovery, GPU readoption, camera reversal, resource
reload and save/reentry. Real workers/publication/native draw produce owner1 /
commandUploads0; ordinary CPU indices0 initially,84 exceptions after conversion,
mixed84; ordinary984/CPU-complete=true during recovery then84/false after readoption.
No GPU vertex payload readback/patch/reupload feeds rendering. This is controlled
loaded-world correctness/adoption, not representative RX coverage/performance or
actual portal/private-pack qualification. Final full CI/artifacts remain required.

Production fixes reuse existing owners: fence-owned selector retirement, swapchain
acquire chain, terrain upload range dependencies, repeated image WAW dependency,
atomic READ|WRITE headers, ready REPLACE/APPEND and empty-complete-CPU staged rebuild
fallback. Actual failed producers still force full CPU recovery. Native startup
negative sequence75 ->1 ->0 RAW hazards is preserved; assertions remain intact.

Separate rapid fresh-world mutation gave unequal client light fields (below8 versus
server11 or above2 versus15); no GPU numerical defect established. Existing
WorldOpenFlows/save/load and standard copy now provide identical serialized scene
input to both controls. Per-capture client/server probes, all exact pixels and zero
hazard gates remain. No light-array injection. Initial rapid-mutation light state
is an unqualified setup boundary, not reported repaired. Active file views were
stale; no deadlock established. Temporary watchdog/vertex diagnostics removed.
Inspect completed outputs: sibling tooling/{indirect-final,screenshot-final,
cpu-camera-final,gpu-camera-final}.complete.log.

Next feasible internal coverage: actual public IP3.0.7 portal pixels/adoption using
its pinned creation/count APIs and the same oracle. Inspect selector output/parameter
ownership for overlapping main/portal views before extending it. Preserve exact
frusta and complete fallback; do not request owner testing before this work finishes.

## Historical rollback package — internally qualified #1082

Use this exact internally qualified executable and artifact:

- Commit: `9a6de8480c7ab46ccae7cdc06a652c108daa8078`.
- Full [#1082 / run 39551989165](https://github.com/Vorith03/VulkanMod/actions/runs/39551989165): **SUCCESS, attempt 1**.
- [JAR artifact 12324685470](https://github.com/Vorith03/VulkanMod/actions/runs/39551989165/artifacts/12324685470): `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1082-g9a6de848-all.jar`.
- JAR: **11,074,747 bytes**, SHA-256 `c97997f24572fce48abbe739659b3b69808bd5f24d5e61d9ffbf28dc30f1e64a` (downloaded bytes/manifest inspected).
- [Smoke logs 12323799769](https://github.com/Vorith03/VulkanMod/actions/runs/39551989165/artifacts/12323799769).

Combined no-splash, native generated consumer pixels/texture/particle/synchronization
and public Create Chronicles/IP gates passed. Private resource packs skipped.
This was the October20 checkpoint. October21 executable changes require a new
qualified artifact before owner testing. Keep this as rollback evidence; it does
not qualify the current GPU milestone.

This package replaces the historical terrain-only flag list below. Do not combine
an old JAR with current flags. One package covers functional qualification and a
matched performance screening, rather than successive isolated owner tests.

### Setup and the two configurations

Use Minecraft 1.20.1 / Forge 47.3.0 / Java 17 and one VulkanMod JAR only. Keep
`regionBatching: true` in `config/vulkanmod_settings.json` in both runs (default
true); region batching also requires native multiDrawIndirect and
drawIndirectFirstInstance support. `indirectDraw` is the separate legacy option,
not an extra prerequisite for the region path. In `config/fml.toml`, retain
`earlyWindowControl = false`. Preserve the known supported modpack configuration,
resolution, render/simulation distance, mipmaps, packs, drivers and existing CPU
fallback settings in both runs. Do not enable another renderer replacement.

The authoritative integrated feature list is
[scripts/ci/integrated-gpu-flags.txt](../scripts/ci/integrated-gpu-flags.txt),
19 restart-time JVM properties checked against the current gates. Copy its lines
into the launcher's Java arguments for **GPU**. For **CPU baseline**, use the same
19 lines with every `=true` changed to `=false`; remove competing duplicate
properties so launch order cannot choose a different value. All shipped defaults
remain off. Do not copy `ciScreenshotSmoke`, `smokeTest` or CI-only properties.

For the functional runs add these common observability properties:

```text
-Dvulkanmod.performanceProfiler=true
-Dvulkanmod.performanceProfiler.gpuTimestamps=true
-Dvulkanmod.debugGpuGeneratedWorldDraw=true
```

The mesher already enables bounded GPU-terrain diagnostics by default; do not
suppress them. Flags are requests, not evidence of admission. CPU baseline must
show no enabled GPU ownership in its environment/adoption evidence. CPU bulk
interpolation and unrelated fast paths must have identical settings in both runs.

### Functional comparison (same saved scene for CPU and GPU)

1. Start from the same backup copy of the world for each configuration. Record
   world/dimension, position, yaw/pitch, FOV, time/weather, packs and video settings.
   Let initial terrain populate before comparing. Preserve the whole log before
   each next launch overwrites it.
2. Capture an F2 main-world screenshot with opaque ordinary full cubes, mixed
   unsupported geometry/fluids/block entities and campfire smoke visible. Repeat
   the exact camera pose in GPU; compare missing/duplicated faces, AO/light/color,
   texture animation/mips, particle orientation, light and translucency order.
3. In a genuinely existing Immersive Portals scene, repeat matched main/portal
   screenshots. Include generated opaque terrain and smoke through the portal
   where available. If no appropriate portal exists, record **not exercised**;
   do not substitute a constructor or clip-shader test for portal pixels.
4. Rotate/move across region and chunk boundaries, briefly visit above build
   height in spectator if available, and return. Break/replace an ordinary cube
   next to a slab/fluid/unsupported block to exercise dirty mixed APPEND. Watch
   for both-half flicker, stale geometry, missing exceptions and failed recovery.
5. Perform F3+T resource reload, leave/re-enter the world, then repeat the main
   scene and a brief movement/smoke pass. Preserve failures and the last good
   camera position; stop the GPU run on device loss/crash or persistent visual
   corruption and retain CPU as recovery. Do not disable assertions to continue.

### Assess three separate outcomes

| Outcome | Required observation | Failure / missing evidence |
| --- | --- | --- |
| Visual correctness | Matched CPU/GPU main and genuine-portal pixels, exception/order/lighting/animation/smoke checks, rebuild/reload/re-entry observations. | Any reproducible mismatch, missing/duplicated geometry, stale half, validation/synchronization error or device loss is a defect. Missing scene is untested, not passed. |
| Adoption and CPU displacement | `VULKANMOD_GPU_TERRAIN_CPU_BYPASS_ACTIVE` plus success samples `cpuBypassed=true`, `VULKANMOD_GPU_COMMAND_OWNERSHIP_ACTIVE`, and `VULKANMOD_GPU_GENERATED_WORLD_INDIRECT_RECORDED` inventory for REPLACE/paired APPEND/GPU-only APPEND in relevant main/portal contexts. Benchmark counters below must show actual GPU texture/particle work and explain fallback denominators. | Requested properties alone, zero eligible work, or recording markers without nonempty correct pixels cannot certify a path. A terrain recovery marker is not automatically a failure; inspect its reason and completeness. |
| Performance | Matched same-JAR CPU/GPU fixed-scene captures after convergence, with CPU graphics time, separate upload/main GPU scopes, submission cost, throughput and render-only/tick-bearing p95/p99. | One different world/camera/convergence state is not a causal comparison. Do not sum overlapping GPU scopes or infer speedup from adoption; regressions and uncertainty remain valid results. |

A recording witness has no GPU-count readback and reports candidate inventory.
Nonzero native selector tests plus seeded resident pixels close an internal edge;
they do not establish representative loaded-world selected-command counts. Correct
visible scene pixels and actual-mode adoption are both required in the owner run.

### Matched performance screening after functional correctness

Use the same pre-run world snapshot for both configurations. Add to both profiles:

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

These are the existing benchmark-world defaults; use the actual representative
world name/pose in **both** profiles if different. Do not use a scene with no
eligible geometry/smoke/animations merely to produce a clean number. Manually enter that named singleplayer world. The existing
controller teleports within its explicitly named world and checks convergence; keep screens closed and do not move during capture. It saves and
quits after successful completion. An aborted/nonconverged run is incomplete.
One new same-JAR pair screens the integrated candidate; default promotion still
requires the roadmap's repeatability and formal evidence gates, not this one pair.

Keep the CPU and GPU UUID captures. Compare `standard_interpolation_gpu_updates`
against `standard_interpolation_updates`; deltas of `gpu_multi_*_total` (jobs per
native dispatch, fallback and table wraps); `particle_billboard_lifetime`
GPU/packed/original-CPU/recovery and descriptor counters; `gpuCandidateLifetime`
reuse/prepared slots and preparation time; terrain-window bypass/recovery/backlog;
separate CPU and GPU/frame classes. Lifetime totals include warmup and must be
differenced, not called measurement-window rates. Simulation, Java callbacks,
changed input metadata and graph traversal are retained CPU work.

### Evidence to return / known untested environments

Preserve the complete instance `logs/latest.log` and `logs/debug.log` after each
functional run, screenshots under `screenshots/`, profiler
`logs/vulkanmod-performance.log` for functional runs, and both
`logs/vulkanmod-performance-benchmark-<UUID>.log` plus neighboring raw JFR/capture
files for automated runs. Include the manifest/setup, snapshot confirmation and
short visual observations; complete logs retain fallback attribution.

Useful narrow extraction (send complete logs as well):

```bash
rg 'VULKANMOD_GPU_(TERRAIN_(CPU_BYPASS_ACTIVE|SUCCESS_SAMPLE|FALLBACK_SAMPLE|CPU_RECOVERY)|COMMAND_OWNERSHIP_ACTIVE|GENERATED_WORLD_INDIRECT_RECORDED)|particle_billboard_lifetime|gpu_multi_|Validation Error|SYNC-HAZARD|device lost' logs/latest.log
```

Untested until this package is executed: RX 6900 XT/RADV net benefit and driver
behavior, actual generated-world/portal raster equivalence, private resource packs,
real mod-injected callbacks and long gameplay churn. CI public compatibility and
software Vulkan fixtures do not close those external gates. No release/default
promotion or 144-FPS promise follows from internal readiness.

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
