# VulkanMod Forge1.20.1 — continuation checkpoint

## Active gate

**Final integrated qualification pending.** Preserve Minecraft1.20.1 / Forge47.3.0 /
Java17 and default-off experiments. Follow AGENTS.md §3A and the ONE completion
checklist in GPU_FIRST_OWNERSHIP. Maintenance retained/deferred decisions stand.
No agents; profiler PR#12 inspected at3db6d0d5 and recorder untouched.

## Source and CI

Pushed executable **95221419486931c295b83b1efe1c98ef3df4eef7**, source tree
**e84ec04f30ebded967129afccf27eea1c0d4095a** (local6bdbf47 tree identical).
Full **#1088 /39680381869** is RUNNING; last observed required build passed,
startup in progress. Inspect actual metadata/failed step before claiming readiness.
No qualified #1088 artifact yet. Documentation-only commits do not change this
executable. GitHub commits fe87d991/3d1ddd73/95221419 correspond to local
ad567ae/4b2f647/6bdbf47. Shell push has no auth; Git-data tools/expected_sha work.

Previous full **#1087 /39677628455 SUCCESS** on3d1ddd73 passed eight-phase
main/public portal pixels and all required gates; private packs skipped. JAR
artifact12382993099, logs12381744299. Its bytes were not independently downloaded.
A local timing-dependent re-entry adoption failure and deterministic turnover
negative exposed a remaining CPU-recovery -> GPU readmission gap after that green
run; #1087 is not final acceptance of the current milestone.

## Changes and CPU displacement

- RegionDrawBatch submits pending exact candidate input once, then republishes/
  revalidates original proofs. Changing main/portal views no longer starves readiness.
- Existing selector owner retains distinct live view slots under unchanged16MiB
  quota; exact cached output selected first and cached reads claim frame ownership.
  Root closes all borrowed slots. Allocation rejection expires only on actual
  native quota retirement, not constructor rollback. Direct same-frame rejection,
  exact frusta,512 sections/1024 commands, barriers/fences/fallback remain.
- Non-dispatch incomplete-producer turnover first restores complete CPU geometry,
  then queues ONE readmission after that upload is ready. Generation/area/dirty/
  recovery/readiness guards revoke stale callbacks. Actual failed-dispatch recovery
  is captured separately and never automatically retried through this branch.
- CI-only per-frame portal witness requires actually recorded generated GPU-owned
  draws at every front-facing capture, rather than only a once-per-mode marker.
  Public portal gate preserves vanilla captures/logs and uses pinned IP3.0.7 /
  Cloth11.1.136 runtime. No new dependency/allocator/scheduler or production wait.

Accepted textures replace Java interpolation, exact supported smoke replaces CPU
billboard expansion, terrain replaces eligible tessellation, GPU indirect ownership
bypasses FrameBatch construction/serialization/upload. Simulation/callbacks,
world/light/halo capture, connectivity, changed metadata, unsupported semantics and
complete CPU recovery remain required CPU. Original particle upload size retained.
No utilization/placement based speedup, default promotion or release claim.

## Final-source local evidence

All24 offline contracts pass. Java17 classes/testRegionBatchLayout/
testSectionVoxelSnapshot and combined native indirect-readmission pass, exit0 with
ZERO Validation Error/SYNC-HAZARD. Native coverage includes overlapping outputs,
cached-read protection, retired-slot reuse, global capacity rejection/readmission,
atomic APPEND, real pending CPU upload, exactly-once callback and stale/recycled/
failed-producer revocation. Independent numerical/seeded oracles remain intact.

Same authoritative saved input, separate CPU/GPU copies: **all NINE640x360 public
portal phase pixels exactly match**, both complete runs exit0 with ZERO hazards.
Phases: initial,dirty,recovery,readopt,camera,return,reload,reentry,turnover. Each
front-facing GPU capture has portalViews1/portalGpuOwners1, main owner1 and zero
CPU command uploads. Initial ordinary CPU indices0; mixed/converted exceptions84.
Deliberate actual failure gives ordinary984/CPU-complete=true until explicit
readoption; turnover logs complete CPU upload readmission then returns84/false.
Actual aperture visually inspected; translated destination slab is visible.

Evidence: sibling tooling/indirect-readmission.complete.log,
portal-{gpu,cpu}-readmission.complete.log and portal-readmission-pair.log;
run/gpu-world-smoke/{cpu,gpu} holds9 PNG/raw pairs. The explicit pre-fix
portal-turnover-negative.complete.log exits1 at phase8 timeout: CPU1008/invalid
GPU output, recovery=true/dispatchRecovery=false/no ready old fallback. Temporary
worker diagnostics were removed. No vertex payload readback/patch/reupload or
light-array injection feeds rendering. Same-save inputs avoid unequal rapid fresh
client light fields; initial rapid-mutation lighting is not claimed repaired.

## Remaining action

Observe #1088 through final required native/world/public compatibility/package
steps. On failure inspect exact log region and fix; do not weaken checks. On success
download/inspect actual produced JAR and logs (tool .zip names can contain raw JAR
bytes), verify manifest/version/CRC/hash and exact-run public nine-phase evidence.
Finish the sole current package in GPU_GENERATED_WORLD_DRAW_WITNESS with artifact
links/size/hash and readiness. Update checklist/roadmap/ledger/status/prompt with
final evidence. Then advance to ONE focused owner qualification package, separating
visual, adoption/CPU displacement and performance. RX6900XT/RADV, private packs,
representative owner/cross-dimension/nested portals and long gameplay remain external.

## Environment and rollback

Workspace /workspace/scratch/34a9c7ba8b11/VulkanMod. An execution snapshot reverted
to90ba/old dirty files; preserved in git stash snapshot-recovery and sibling patch,
then restored remote32247 and reconstructed work. DO NOT pop that old stash onto
current source. Scratch/tools can disappear; remote Git is durable. Current local
6bdbf47 is tree-equivalent to remote95221419; reconcile via fetch before ordinary
push, preserving any intervening work/uncommitted docs.

Sibling tooling contains Gradle8.1.1/cache, Java17 javac-module wrapper, extracted
Ubuntu Vulkan/Xvfb, gradle-local.py/run-local.py/run-portal.py/pair-final.py. Derive
proxy host/port INSIDE same exec. Xvfb uses loopback TCP. Public launcher snapshots/
restores build.gradle; never commit its temporary pinned runtime diff. No runtime
is active now. Completed descriptor-backed logs are authoritative over active stale
file excerpts. Cold corrupt generated client-extra and deobf Cloth caches were
reconstructed/regenerated from pinned verified inputs; no dependency changed.

Verified earlier rollback32247a7/#1086: actual artifact12365557695 JAR
VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1086-g32247a7d-all.jar,
11,091,626 bytes, SHA256 a27588e6ffd31fe7d1406d1527818d9ca9c73614dd96877649bae9c3376a63c9;
CRC/manifest/version/ranges inspected; logs12365557689. This does not qualify952.

Every successor must update repository handoff and generate its own NEW complete
copyable continuation prompt requiring the next successor to do the same. This
contingency never substitutes for completing feasible work in this session.
