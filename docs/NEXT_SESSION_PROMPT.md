# VulkanMod — Continue From Qualified Maintenance

Continue https://github.com/Vorith03/VulkanMod on `forge-1.20.1`, preserving
Minecraft 1.20.1, Forge 47.3.0 and Java 17. Ordinary edits, logical commits and
pushes are authorized. Do not force-push, rewrite shared history, merge unrelated
PRs, publish releases or deploy.

Read this prompt in full. Follow current AGENTS.md §3A against live Git, CI and
overlapping work. Read AGENT_STATUS.md, the active ROADMAP.md heading,
docs/MAINTENANCE_TRACKER_2026-10-20.md, docs/OPTIMIZATION_EVIDENCE_LEDGER.md,
both October 20 audits and affected ownership contracts. Inspect intervening
changes; live evidence overrides every checkpoint here. Use the tracker as the
single finding register, retaining validated, pending, retained, deferred and
blocked distinctions. Do not repeat the audits or reopen settled decisions.

## Recover actual qualification first

Full #1077 / run **39508596394** completed **SUCCESS**, attempt 1, on
`c303cf26f53887964e9e45a2cc156c21a55143fb`. JAR **12305230721**, smoke logs
**12305300783**. It qualifies the prior maintenance milestones. Actual uniform
failure/ABI tests, Forge packaging/both startups, native indirect/terrain/
texture/particle/pixel/synchronization, screenshot/post/depth, public Create
Chronicles/Immersive Portals and Crash Assistant passed; private packs skipped.
#1076 failed compilation, #1075 was cancelled; neither is qualified. Previous
baseline `682b0d6b` / #1074 remains historical evidence.

Follow-up **`6c65e00f6212a7e2d52571bb7a279ec943efd19a`** is pushed and
**full #1078 / run 39509869276 PASSED**, attempt 1, on that exact executable.
Forge JAR **12304984467**, smoke logs **12306121182**. This is now the qualified
maintenance executable. Compute failure, vec4/mat3 uniform and ABI tests, both
startups, new native cache transitions, indirect APPEND/REPLACE, texture shared/
multi-dispatch pixels, particle/synchronization/post/depth/screenshot and public
modpack/IP/Crash Assistant gates passed. All private-fixture steps skipped.
The bounded maintenance assignment is complete; no code from it awaits CI.
Inspect any intervening executable change before reusing this qualification.

## What the follow-up changes

1. Uniform failure test checks immediate exception cleanup before caller close,
   then repeated close and stale vec4/mat3 updates without destination mutation.
   New mat3 coverage reproduced zeroing before closed-source validation; the
   source address now validates before writes. This is per-field failure safety,
   not an arbitrary multi-field transaction or concurrent-lifetime guarantee.
2. ComputePipelineFactory construction-only Calls seam runs actual coordinator
   and LWJGL structs for compiler exception, module failure, pipeline failure
   (zero/partial handle), thrown call and success. Test exact original failure,
   cleanup count/order/handle, ABI fields, released real SPIR-V and successful
   ownership transfer. testComputePipelineFactoryFailure is part of Gradle check.
   Native driver success remains independently exercised by subsystem fixtures.
3. Native RegionBatchSmokeTest adds generated -> ordinary -> generated mode
   transitions with unchanged producer revisions (rescan plus serialized geometry),
   and clearing staged APPEND preflight (rescan plus rejected-cache recovery).
   Existing dirty/input-vs-visible, identity/direction, exact frustum,
   readiness/retirement, stale publication and atomic APPEND coverage remains.
4. Candidate/texture constants checks remain supplemental. #1077 observed actual
   509 ordered CPU/GPU APPEND pairs, mixed rows, malformed/unready rejection,
   maximum capacity/zero tail, and shared/multi-dispatch texture pixel markers.

24 local offline contracts passed. Focused tests used actual factory,
SPIRVUtils/native storage, binder/layout/UBO and LWJGL 3.3.1, isolating only
unrelated globals/game/cache dependencies. Six single-change mutants were
rejected: delayed uniform cleanup, double release, partial stale mat3 write,
SPIR-V leak, module leak and partial-pipeline leak. Restored code passed.
Tracker records exact failure signals and full-checkout reproduction commands.
Local Gradle bootstrap was network-blocked; local focused checks do not replace
Forge/native CI. Do not repeat those settled mutations absent a relevant change.

## Continue only justified work

Tracker dispositions now reflect #1078 qualification. Resume the first available
unsatisfied Phase 7 gate from current evidence. Fix a specific newly evidenced
hole or missing prerequisite while feasible; do not create maintenance churn
when remaining progress needs actual-world inputs.
Do not force retained/deferred migrations to manufacture progress:
compute caching needs an explicit device service/host synchronization/teardown/
identity/corrupt fallback and measurements; parser migration needs pinned
compatible license/version and actual shader corpus/output/packaging/reload
comparison; retain allocation policies absent a discriminating trace; widget
migration needs live keyboard/narration/drag/scroll/resize/apply/cancel tests.
Profiler PR #12 remains separate; inspect overlap before any recorder work.

All experiments stay default-off. Preserve generation/provenance/readiness,
512-section/1,024-command bounds, exact portal frusta, atomic APPEND CPU exception
pairs, queue barriers, upload completion, frame fences and complete CPU fallback.
Maintenance establishes no FPS gain or release completion. Actual loaded-world
REPLACE/APPEND consumption and nonzero selected commands, matched main-world/
genuine-portal CPU/GPU pixels, private packs, real particle callbacks/lighting/
translucency/camera/reload/frame pressure and RX adoption/net CPU/GPU/FPS/p95/p99
remain open. Use docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md when inputs
exist. Do not repeat settled owner tests or request an isolated benchmark; retain
an exact external blocker if the actual-world inputs remain unavailable.

Before ending, update the tracker, AGENT_STATUS.md and this repository handoff
with actual outcomes, commits, CI/artifacts, residual risk and next action.
Finish with a NEW complete copyable continuation prompt explicitly commanding
its own successor to update the repository handoff and generate its own NEW
continuation prompt carrying this same requirement forward perpetually.
