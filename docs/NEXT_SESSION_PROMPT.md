# VulkanMod — Qualify the integrated GPU build in real-world use

Continue https://github.com/Vorith03/VulkanMod on forge-1.20.1. Preserve Minecraft
1.20.1, Forge 47.3.0 and Java 17. Ordinary edits, logical commits and pushes are
authorized. No force push/history rewrite, unrelated merge, release or deployment.
Keep experiments default-off. Inspect profiler PR #12 before overlapping recorder
work. Prefer existing repository mechanisms and pinned dependencies; record the
unmet need, alternatives and smallest addition before substantial new machinery.

Follow current AGENTS.md §3A against live Git/CI and overlapping work. Read this
prompt in full, AGENT_STATUS.md, active roadmap/ledger and the single completion
checklist at the top of docs/GPU_FIRST_OWNERSHIP_2026-10-18.md. Read affected
ownership contracts when investigating/changing them. Retain settled maintenance
tracker/audit dispositions; use deltas instead of repeating unchanged archaeology.

The internal implementation-readiness milestone is complete on exact executable
9a6de8480c7ab46ccae7cdc06a652c108daa8078, full #1082 / run 39551989165 SUCCESS,
attempt 1. JAR artifact 12324685470:
VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1082-g9a6de848-all.jar.
Downloaded 11,074,747-byte JAR manifest/version inspected; SHA-256
c97997f24572fce48abbe739659b3b69808bd5f24d5e61d9ffbf28dc30f1e64a.
Smoke logs 12323799769. All public full gates passed; private packs skipped.
Documentation-only follow-ups reuse this qualification unless executable/config
changes invalidate it. Internal readiness is not release completion or RX speedup.

New combined native consumer pixels exposed a production short-stamp collision:
WorldRenderer legitimately reaches -1, previously mistaken for absent graph proof.
ChunkArea now has explicit traversalFrameValid, established by addSection and
cleared by resetQueue/clearVoxels across visibility/provenance/sparse readers.
Native -2/-1/0/MAX/MIN/reset/clear and real production RegionDrawBatch.draw pixels
at -1 passed. Seeded resident vertices prove nonzero-offset REPLACE, ordered
APPEND, zero-exception APPEND, frame-slot reuse, rejected-proof complete CPU
fallback/re-adoption and accepted CPU command-upload bypass; they are NOT actual
loaded-world mesher or genuine portal pixels. Preserve independent numerical,
publication/transaction and native oracles. #1080 failed pixels; #1081 failed new
test private access before native checks; #1079 cancelled. They are not qualified.

Next use docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md as the one integrated
real-world test package, with scripts/ci/integrated-gpu-flags.txt and its explicit
same-JAR CPU-off control. Check any already supplied project/log evidence before
requesting repetition. Assess separately:
1. Main-world/genuine-portal visuals, mixed exceptions, particle lighting/
   translucency/callbacks, dirty rebuild, resource reload/re-entry and churn.
2. Actual consumption/adoption and corresponding CPU bypass, fallback reasons
   and denominators. Requested flags or recorded candidate inventory alone do
   not prove selected nonzero commands or pixels.
3. Matched converged RX 6900 XT CPU/GPU/submit cost, throughput and render-only/
   tick-bearing p95/p99. Do not sum overlapping GPU scopes or infer net benefit
   from code location. Retain untested private packs/hardware/scene boundaries.

Fix demonstrated defects through focused implementation and appropriate local/
full exact-executable validation; verify new artifacts before additional owner
tests. Keep generation/provenance/readiness, exact portal frusta, atomic APPEND
CPU exceptions, 512-section/1,024-command bounds, upload completion, barriers,
fence retirement and complete CPU recovery. Java clocks/callbacks/world-light
capture/graph connectivity/changed metadata/unsupported semantics remain required
CPU work; do not claim all CPU graphics vanished or silently broaden eligibility.

Continue autonomously while independent useful work remains. Do not stop at a
plan, audit, fixture, intermediate success, context rollover or speculative credit
concern. If necessary real-world inputs are absent and no independently evidenced
repairable prerequisite remains, identify that exact external blocker rather than
manufacturing churn or claiming qualification. Enable no accelerated default or
release until the actual-world and repeatable profitability gates are satisfied.

Before ending, update AGENT_STATUS.md, relevant checklist/roadmap/ledger and this
repository handoff with actual changes, CPU displacement/retained work, evidence,
artifacts, risks and next actions. Finish with a NEW complete copyable continuation
prompt requiring its successor to update the repository handoff and generate its
own NEW prompt carrying this same requirement forward. That contingency never
substitutes for completing feasible work in the current session.
