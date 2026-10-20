# VulkanMod Forge 1.20.1 — Current continuation checkpoint

## Active work

Owner-directed maintainability/reuse qualification detour from Phase 7. Follow
AGENTS.md §3A and [the rolling assignment](docs/NEXT_SESSION_PROMPT.md).
[The combined tracker](docs/MAINTENANCE_TRACKER_2026-10-20.md) owns dispositions,
transition coverage, local mutation evidence and retained/deferred prerequisites.
Both audits are baseline findings, not current source state.

## Qualified executable evidence

- **Full #1077 / [39508596394](https://github.com/Vorith03/VulkanMod/actions/runs/39508596394)
  PASSED**, attempt 1, on `c303cf26f53887964e9e45a2cc156c21a55143fb`.
  Forge JAR **12305230721**, logs **12305300783**. Actual uniform/ABI tests,
  offline contracts, package/both startups, native indirect/terrain/texture/
  particle/synchronization, screenshot/post/depth, public Create Chronicles/
  Immersive Portals and Crash Assistant passed. Private-pack steps skipped.
  This qualified the earlier maintenance milestones; #1078 now qualifies the follow-up.
- **Follow-up executable `6c65e00f6212a7e2d52571bb7a279ec943efd19a`** is pushed.
  Full #1078 / [39509869276](https://github.com/Vorith03/VulkanMod/actions/runs/39509869276)
  **PASSED**, attempt 1, on that exact executable. Forge JAR **12304984467**,
  logs **12306121182**. Actual compute failure, vec4/mat3 uniform and ABI tests
  ran; both startups, new native cache transitions, selector APPEND/REPLACE,
  texture/particle/pixel/synchronization and public compatibility gates passed.
  All three private-fixture steps skipped. This is the current qualified executable.
- #1076 / 39507963173 FAILED compileJava: seven private MappedBuffer.buffer
  accesses in two mixins, corrected by `c303cf26`. Failed logs **12304163339**;
  no qualified JAR/native result. #1075 / 39506746716 was cancelled/superseded,
  not qualified. Earlier #1074 JAR **12214688900**, logs **12214474322** retained.

## Follow-up implementation and local evidence

Uniform failure tests now require cleanup immediately after failed bind, before
caller close, and cover vec4/mat3 stale updates. The new mat3 case exposed a real
partial destination write: zeroing happened before closed-source validation.
Field.Mat3f now validates the source address first. No arbitrary whole-UBO update
transaction or concurrent-lifetime guarantee is introduced.

ComputePipelineFactory has a package-scoped construction-only injection seam,
no global override. Actual factory control flow/native structs test compile,
module, pipeline, failed partial-pipeline, thrown-call and success paths with
exact cleanup order/count/identity and real released SPIR-V storage. Production
still owns only temporary compilation/module and failed-pipeline cleanup.

Native startup cache tests now cover ordinary/generated mode round trips with
unchanged producer revisions and clearing staged APPEND preflight. Existing
producer revocation, stale queued publication, readiness/retirement, exact portal
frusta and atomic APPEND tests remain. Candidate/texture ABI guards remain
supplementary to actual native selector commands and texture pixel gates.

Locally 24 offline contracts passed. Focused actual LWJGL 3.3.1/native-storage
checks passed; only unrelated globals/game/disk-cache dependencies were isolated.
All six deliberate mutants failed: immediate uniform leak, double release,
partial stale mat3 write, leaked SPIR-V/module/partial pipeline. Restored tests
passed. Full local Gradle remains unavailable: bootstrap network unreachable;
full Forge/native qualification belongs to CI. Exact recipes/limits in tracker.

## Retained boundaries and next action

The requested bounded maintenance implementation and qualification is complete;
remaining tracker items are specifically retained, deferred or blocked. Resume
the first available unsatisfied Phase 7 actual-world gate from live evidence.
If its world/portal/hardware inputs remain unavailable, preserve that blocker;
only implement a new slice for a concrete evidenced defect or missing prerequisite. Do not force compute cache, parser, VMA or widget migrations;
the tracker records concrete service/lifecycle, license/corpus, policy and live-UI
prerequisites. Profiler PR #12 at `3db6d0d5` remains separate and untouched.

Maintenance does not establish actual loaded-world REPLACE/APPEND selection,
nonzero GPU post-cull commands, matched CPU/GPU main-world/genuine-portal pixels,
private packs, real particle callbacks/lighting/translucency/camera/reload/frame
pressure, or RX 6900 XT adoption/displaced CPU/GPU cost/FPS/p95/p99 benefit.
Follow [the world witness contract](docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md)
when inputs are available; do not repeat settled user tests or ask for an isolated
benchmark. Experiments stay default-off. Preserve 512 sections/1,024 commands,
generation/provenance/readiness, exact frusta, atomic APPEND CPU exceptions,
queue barriers, upload completion, fences and complete CPU fallback.

## Historical evidence

The previous 511-line checkpoint remains verbatim in
[the archive](docs/AGENT_STATUS_ARCHIVE_2026-10-20.md) and Git. Keep failed
#1067/#1060/#1064, cancelled #1071, #1070 attempt-1 failure versus attempt-2
success, older #1054/#1050–1052/#1047–1048/#1041–1042 and analyzed #1028 UUID
`e05f2b31` distinct from qualification. Historical “latest” headings do not
supersede this checkpoint or newer live Git/CI.

Every successor must update the tracker, this repository handoff and rolling
prompt, then finish with a NEW complete copyable continuation prompt explicitly
requiring its successor to do the same and carry that requirement forward.
