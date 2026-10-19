# Actual-world generated indirect recording witness — October 19, 2026

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
