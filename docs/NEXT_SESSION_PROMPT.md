# Continue VulkanMod integrated GPU implementation and qualification

Continue https://github.com/Vorith03/VulkanMod on forge-1.20.1. Preserve Minecraft
1.20.1, Forge47.3.0 and Java17. Ordinary edits, logical commits and pushes are
authorized; no force push/shared-history rewrite, unrelated merge, release or
deployment. Experiments remain default-off. No agents unless explicitly authorized.
Profiler PR#12 was inspected (3db6d0d5); check its current delta before recorder work.

The full GPU milestone is NOT COMPLETE. Continue autonomously until acceptance is
satisfied or a demonstrated external/execution blocker prevents necessary progress
and independent work is exhausted. A plan, passing fixture, intermediate green CI,
chat length or handoff is not a stopping condition. Do not request successive owner
tests while feasible internal work remains. This overrides discretionary rollover.

Follow current AGENTS.md §3A against live Git/CI. Read AGENT_STATUS.md and this prompt
in full, active ROADMAP/optimization ledger, maintenance tracker/both October20
audits and affected ownership contracts. Use deltas for unchanged evidence; preserve
settled maintenance dispositions. Maintain the ONE completion checklist in
GPU_FIRST_OWNERSHIP across textures, particles, terrain/lighting, culling, candidate/
indirect preparation, REPLACE and atomic APPEND. Do not relabel unfinished eligible
work retained. Inspect existing owners, partial implementations and pinned dependency
capabilities before substantial changes; record unmet need/alternatives/smallest
addition. Do not repeat settled migrations or add arbitrary mod/shaderpack support.

## Current checkpoint and evidence

New source checkpoint:e1abcf39f8e9e993d3b21e0fa50a4e6543137a47. Its tree matches the
locally tested03bec39 tree:f677307c9038e2ab39a7a344d9159638465f48f3. GitHub Git-data
replays of local logical commits are de608023 (native upload/acquire/selector fixes),
4ffe59e6 (ready REPLACE -> staged APPEND, explicit failure recovery),7446dce5
(complete empty CPU fallback admission),ce34d648 (atomic probe header visibility),
e1abcf39 (saved-world oracle/CI gate). Documentation checkpoint will start full CI;
inspect actual latest head/run/steps/logs rather than assuming a result. No new JAR
is qualified yet. Shell push lacks auth; GitHub Git-data tools with expected_sha are
available. Never overwrite intervening work; fetch/reconcile after authorized update.

Last full qualified rollback:9a6de8480c7ab46ccae7cdc06a652c108daa8078, full#1082 /
39551989165 SUCCESS attempt1, JAR12324685470:
VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1082-g9a6de848-all.jar,
11,074,747 bytes, SHA256 c97997f24572fce48abbe739659b3b69808bd5f24d5e61d9ffbf28dc30f1e64a;
logs12323799769, private packs skipped. This does not qualify current executable.
#1083 cancelled; #1084 failed fixture upload-domain entry; #1085 failed assumed stone
eligibility. Both corrected; these failures have no qualified artifacts.

Local Java17 classes and both Gradle tests passed. Complete native indirect and
screenshot suites have ZERO validation errors/hazards on Ubuntu24 Lavapipe25.2.8 /
validation1.3.275. Includes same-frame rejection/retired-slot reuse, real production
mesher -> bridge completion/publication -> original indirect raster, old REPLACE/
APPEND/empty-complete-CPU atomic transactions, stale discard and complete failure
recovery. Independent numerical/seeded oracles preserved. Initial startup negatives
75 then1 atomic RAW hazards were fixed, not suppressed; later full indirect has0.

Actual loaded-world controls pass SEVEN phases, every640x360 pixel exactly equal,
zero hazards: initial REPLACE/mixed APPEND, dirty glass conversion, complete CPU
recovery, GPU readoption, camera reversal, resource reload, save/reentry. Real workers,
queues and world rendering; no vertex payload readback/patch/reupload feeds rendering.
Accepted GPU owner1/commandUploads0, ordinary CPU0 initially then84 exceptions,
mixed84/CPU-complete=false; recovery ordinary984/true then readoption84/false.
Complete local logs: sibling tooling/{indirect-final,screenshot-final,
cpu-camera-final,gpu-camera-final}.complete.log. Paired PNG/raws in run/gpu-world-smoke.
This is controlled main-world evidence, not representative hardware adoption or FPS.

Rapid creation of separate new worlds gave unequal client light inputs (below8
versus authoritative11, or above2 versus15), not an established GPU numerical defect.
The test now prepares/closes ONE world and loads separate copies of SAME saved bytes;
client/server probes agree and pixels match. Dirty/recovery/reload/reentry still run.
No light-array injection. Initial rapid-mutation lighting is a setup limitation,
not claimed repaired. Active file excerpts were stale; completed results did not
establish a hang. Speculative watchdog/vertex diagnostics were removed. Resume/wait
the exec session before inspecting complete outputs; local launcher writes complete
output from its own descriptor at completion. Avoid concurrent cache/build mutation.

Tooling persists outside Git under /workspace/scratch/34a9c7ba8b11/tooling: Gradle8.1.1,
Java17 javac module wrapper, extracted official Ubuntu Vulkan/Xvfb, run-local.py.
Derive proxy ports inside each exec. TCP Xvfb is necessary; UNIX bind unavailable.
Root/tmp caches can disappear between contexts. A truncated generated client-extra
cache was rebuilt from verified client.jar/pinned mappings and all ZIP entries
checked; no project dependency changed. All24 offline contracts passed before final
slice; rerun after final edits. Use completed process logs, not partial live files.

## Continue independent work

A genuine public portal test is feasible: the pinned IP3.0.7 / Cloth11.1.136 runtime
already exists in scripts/ci/immersive-portals-smoke.sh. Pinned JAR downloaded under
tooling/immersive-portals-6368524.jar (Apache2.0); inspected APIs:
PortalManipulation.createOrthodoxPortal(EntityType,ServerLevel,ServerLevel,Direction,
AABB,Vec3), Portal.teleportable and RenderStates.getRenderedPortalNum. Entity key is
immersive_portals:portal. Reuse existing saved-world subscriber and optional API
patterns, not a new reflection/controller framework. A small same-dimension portal
can map the two existing platforms (offset16X), requiring no private scene/pack.
Assert a real rendered portal, inspect actual CPU-control pixels, then strict GPU
pixels and adoption/CPU bypass across camera/reload/reentry. Consider a camera return
before reload so portal remains visible in those lifecycle checks.

Inspect production selector ownership first: RegionDrawBatch has one shadow store
per region/layer; GpuSectionSelectionShadowStore has one persistent output and
in-flight per-frame parameter protection. Main and portal views may need distinct
live outputs in the same frame. Do not infer or implement that change before real
portal evidence. Reuse existing owners/global budget/frame retirement if a bounded
extension is needed; keep rejection, exact frusta and complete CPU fallback intact.
Do not silently call eligible portal adoption retained/unsupported just to finish.
Inspect actual CI metadata/logs during meaningful waits and continue this independent
work; a green main-world checkpoint is not full completion.

Accepted GPU results must reach production consumers and bypass redundant CPU work.
Required clocks/callbacks, world/light/halo capture, connectivity, changed metadata,
unsupported semantics and complete recovery remain CPU-owned. Preserve generation/
provenance/readiness, exact portal frusta, atomic APPEND exceptions,512 sections /
1024 commands, upload completion, barriers and fence retirement. No utilization or
placement-based speedup claims. Complete all available combined lifecycle/capacity/
ordering/synchronization tests, full exact-final-executable CI including public
compatibility and produced-JAR startup/packaging, and verify artifacts themselves.

Only then update GPU_GENERATED_WORLD_DRAW_WITNESS as one focused owner package:
exact JAR/commit/setup/current-code flags (integrated-gpu-flags.txt), same-JAR CPU
control, scenes/actions, expected observations/failures/logs. Separate visual,
adoption/CPU displacement and performance criteria. Private packs, actual owner
modpack scenes and RX6900XT net benefit remain external/untested, not proven speedup
or release completion. Do not ask for repeated intermediate owner tests.

Before ending, update AGENT_STATUS, relevant roadmap/ledger/checklist and this
handoff with actual changes, displaced/retained CPU work, validation/artifacts,
remaining risks and exact next action. Finish with a NEW COMPLETE COPYABLE
continuation prompt requiring its successor to update repository handoff and
produce its own NEW prompt carrying this requirement forward. This contingency
never substitutes for completing feasible work in the current session.
