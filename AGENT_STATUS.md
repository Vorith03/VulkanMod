# VulkanMod Forge1.20.1 — qualified integrated GPU checkpoint

## October 21 — two owner RX6900XT captures received; adoption gaps are actionable

See [owner GPU benchmark evidence](docs/OWNER_RX_6900XT_GPU_BENCHMARK_2026-10-21.md).
Same #1088 JAR, 2552x1374 D32/S12, 180s automated stationary: partial GPU
20c49c53:10545 frames/58.60fps; integrated19 221fd7a2:12835/71.34fps
(+21.7%). Final150s:62.03 ->75.71fps. Both completed; no true CPU
allfalse run, screenshots, or multi-trial causality. Real terrain GPU CPU-bypass,
mesher and REPLACE/APPEND indirect draw recorded; 727 GPU terrain publishes,
19 CPU recovery requests, staging and unsupported-model fallbacks preserved.

**Concrete gaps:** integrated GPU interpolation 0/2879091 despite admission
1510/1505 resident; 2810697 dispatch_failed returns. Particle billboard
GPU=0/126249312 eligible; 15426 capacity-rejected compute batches. CPU fallback
dominates both. Do NOT call the GPU milestone performance-complete or release
qualified. Trace rejection sites, preserve fallback, bounded instrumentation/tests.
Relevant JFR and full owner logs exist as conversation uploads, not in Git.
Next: targeted source investigation/repair of two adoption blockers, internal
qualification, then matched functional and CPU-allfalse vs integrated testing.
Documentation-only HEAD; qualified executable remains #1088.


## October 21 owner-machine benchmark kickoff (pending evidence)

Current session verified live branch head 3c7b6ef714a2 and its two documentation-only
changes since executable 95221419, exact #1088 SUCCESS run 39680381869,
JAR artifact 12383727666 (available), exact SHA256 and all full-CI jobs.
The 19 integrated properties are unchanged in scripts/ci/integrated-gpu-flags.txt;
owner control uses the SAME JAR with all 19 set false. Profiler uses
-Dvulkanmod.performanceProfiler=true, .gpuTimestamps=true and
-Dvulkanmod.debugGpuGeneratedWorldDraw=true (the debug key is independent).
Current code's profiler records initial_framebuffer_px and world-window framebuffer
ranges; DO NOT infer the RX owner's actual resolution from the 5120x1440 display.
Profiler PR #12 remains open/unmerged and is not part of the executable.

**No owner-machine launch, screenshots, measured framebuffer, capture UUID,
feature-admission verdict or RX FPS result has yet been received.** Initial owner
stage: install exact JAR in matched Prism/Forge Java17 profiles, confirm
config/fml.toml earlyWindowControl=false and regionBatching=true, capture CPU
allfalse and integrated19 true functional same-save screenshots and logs.
Use the witness package below as the sole test procedure. After judging visual
correctness and admission, advance to one matched 60 s min warmup + 10 s stable
initial terrain + 180 s stationary automated benchmark on the same saved input.
No performance conclusion, new recorder, implementation work or repeated owner
request is justified before inspecting the first evidence. If defects appear,
preserve logs and isolate only the evidenced failure. Keep default-off modes.

Next successor must update this handoff, witness/ledger and NEXT_SESSION_PROMPT with
actual captures (or explicitly still pending), then generate its own NEW complete
copyable continuation prompt preserving that perpetual handoff requirement.

## Current milestone

**The requested integrated GPU implementation milestone is COMPLETE and internally
qualified, ready for ONE focused real-world qualification package.** This is not
release completion/default promotion or proven RX speedup. No known independently
repairable in-scope defect or prerequisite remains. Follow AGENTS.md §3A and the
single completion checklist in GPU_FIRST_OWNERSHIP; older pending/next paragraphs
are historical. Preserve Minecraft1.20.1 /Forge47.3.0 /Java17/default-off experiments.
Maintenance retained/deferred decisions unchanged. No agents; PR#12 recorder untouched.

## Exact executable and artifacts

- Executable **95221419486931c295b83b1efe1c98ef3df4eef7**.
- Full **#1088 /39680381869 SUCCESS, attempt1** on that exact source/configuration.
- JAR **12383727666**: VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1088-g95221419-all.jar.
- Actual downloaded JAR **11,098,113 bytes**; SHA256
  **09f491f063d5a3b0def066e029ae1805acaeaaf7fe9b4cd5eb8f2ec292bf01e8**.
- Logs/captures **12385056493**, downloaded/CRC-verified.

Actual manifest/version, MC/Forge ranges, Java17 class major61, all JAR/embedded
Vulkan CRCs, VMA/shaderc/pinned LWJGL packaging inspected. Required full Forge/
distribution/startups/native/screenshot/post/depth/main/public portal/public Create
Chronicles/CrashAssistant gates all passed. All3 private-fixture steps skipped.
Documentation-only commits after95221419 do not change this executable.

Downloaded CI captures independently recompared: **9/9 main-world plus9/9 public
portal phases match every854x480 pixel**. All14 archived top-level Vulkan logs have
ZERO Validation Error/SYNC-HAZARD. Local same-save public9 phases match640x360,
both controls exit0/zero hazards. All24 contracts and Java17/two Gradle tests pass.
Independent numerical/seeded/native/atomic APPEND oracles preserved.

## Changes, ownership and CPU displacement

GitHub commits fe87d991/3d1ddd73/95221419 extend existing mechanisms:

- One bounded candidate submission plus original proof revalidation removes
  changing-view readiness starvation.
- Existing selector root retains distinct bounded live-view output/parameter/
  descriptor slots, exact cache first. Cached reads also claim actual frame use.
  Same16MiB quota; root closes all slots; rejection readmits only at actual native
  retirement revision, not constructor rollback. Exact frusta/bounds/fences intact.
- Non-dispatch incomplete-producer turnover first completes CPU geometry, then
  queues ONE reattempt after that CPU upload is ready. Exact generation/area/dirty/
  recovery/readiness guards reject stale callbacks. Actual dispatch failure is
  captured separately and never automatically retried through this branch.
- Required public IP3.0.7/Cloth11.1.136 real portal gate, per-frame/per-capture actual
  generated GPU-owned draw witness, preserved vanilla logs/captures. No new production
  wait, allocator, scheduler, dependency, GPU readback feed or approximate frustum.

Accepted textures replace Java interpolation; supported exact smoke replaces CPU
billboard expansion; qualified terrain replaces tessellation; accepted indirect
ownership bypasses FrameBatch construction/serialization/upload. Required simulation,
callbacks, world/light/halo capture, connectivity, changed metadata, unsupported
semantics/capacity/readiness fallback and complete recovery remain CPU-owned.
Original particle upload size retained. See the per-path scope/prerequisite table.

All9 phases: initial,dirty,recovery,readopt,camera,return,reload,reentry,turnover.
Every front-facing GPU portal capture has portalViews1/portalGpuOwners1; main owner1
and CPU commandUploads0. Ordinary CPU0 initially, mixed/converted exceptions84.
Actual failed-producer recovery ordinary984/CPU-complete=true until explicit
readoption. Turnover restores complete CPU1008, then readmits to84/false. Native
pending-upload/exactly-once/stale/recycled/failure, cached-read/overlapping-view,
capacity/rejection/fenced-readmission cases pass. Actual aperture visually inspected.

## Negative evidence and limits

Earlier #1087/3d1ddd73 passed8 phases but a local timing-dependent re-entry failure
exposed missing readmission. The explicit pre-fix turnover native negative timed
out at phase8, CPU1008/invalid GPU output, recovery=true/dispatchRecovery=false/no
ready fallback. Fixed and qualified in #1088; no assertion suppressed. Previous
#1086/32247a7 and #1082/9a6de848 qualifications remain historical. Probe atomic RAW
hazards75 ->1 ->0 were corrected earlier, not ignored. Temporary worker diagnostics
removed. Separate rapid fresh-world creation can have unequal client light inputs;
same authoritative closed-save copies avoid that setup boundary without light-array
or vertex payload injection. Initial rapid-mutation lighting is not claimed repaired.

RX6900XT/RADV net benefit/driver behavior, private packs, representative owner
modpack/cross-dimension/nested portals, custom callbacks and long gameplay remain
untested. Controlled software qualification is not general mod/shaderpack support.

## Next action and continuity

Execute **docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md**, the sole current
owner package: exact JAR/setup,19 current GPU properties versus same-JAR allfalse
CPU control, matched scenes/reload/re-entry, visual/adoption/performance criteria
and log paths. Do not ask for successive isolated tests or invent more optimization
work before evidence. If feedback reveals a defect, recover live Git/CI and fix it;
requalify any changed executable/configuration. No owner test has been run yet.

Workspace /workspace/scratch/34a9c7ba8b11/VulkanMod. Source-backed work is pushed;
local history reconciled to equivalent GitHub commits. Ignore untracked generated
logs. An old snapshot was saved in stash snapshot-recovery/sibling patch; NEVER
pop it onto current source. Scratch/tools may vanish; remote Git is durable.
Sibling tooling/final1088 holds verified JAR/logs/verification.json; local completed
logs include indirect-readmission, portal-{gpu,cpu}-readmission and portal pair.
No runtime active. Proxy ports must be derived inside each exec; TCP Xvfb needed.
Public launcher restores temporary build.gradle; no dependency changes committed.
Git-data pushes require expected_sha and force:false; never overwrite new work.

Before ending any successor must update repository handoff and generate a NEW
complete copyable continuation prompt requiring its successor to do the same.
That contingency never replaces feasible implementation/verification work.
