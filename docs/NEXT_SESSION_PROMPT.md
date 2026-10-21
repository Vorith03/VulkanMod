# VulkanMod — Complete integrated GPU implementation and qualification

Continue https://github.com/Vorith03/VulkanMod on forge-1.20.1, preserving
Minecraft 1.20.1, Forge 47.3.0 and Java 17. Ordinary edits, logical commits and
pushes are authorized. No force push/history rewrite, unrelated merge, release
publication or deployment. Keep experiments default-off and preserve all native
ownership/generation/readiness/provenance, exact portal frusta, atomic APPEND,
512-section/1,024-command bounds, barriers/upload completion/fence retirement and
complete CPU recovery. Inspect PR #12 before overlapping recorder work.

Read this prompt in full and follow current AGENTS.md §3A against live Git/CI and
overlapping work. Recover deltas from AGENT_STATUS.md. Read active roadmap,
optimization ledger, maintenance tracker and the two October 20 audits; retain
settled maintenance dispositions. Read affected ownership contracts in full.
The single completion checklist is now at the top of
GPU_FIRST_OWNERSHIP_2026-10-18.md, superseding stale historical next-step text.

Owner objective: finish supported GPU-first production consumption and corresponding
redundant CPU bypass, then deliver one internally qualified build ready for focused
real-world testing. Continue through implementation/debugging/commit/validation
cycles; an intermediate fixture, green step, handoff or session duration is not a
stopping condition. Compaction resumes from durable state. Complete independent
work before citing external hardware/private-world gaps. Readiness does not mean
release completion, actual-world pixels or proven RX profitability.

New implementation `1e049005ffe9225f9eae43644e4a850b0c85966f` adds seeded resident
REPLACE/APPEND pixels through actual RegionDrawBatch.draw and queued GPU selection,
plus one shared integrated 19-property configuration in no-splash, screenshot and
public compatibility clients. Full #1079 / run 39514023722 was cancelled/superseded.
Local follow-up strengthens zero-exception APPEND, complete CPU recovery and
re-adoption, and makes old REPLACE-only oracle policy explicit under APPEND-enabled
clients. Inspect live commits/files for its publication and validation state.
No seeded fixture is a real loaded-world mesher or genuine portal result.

Last verified executable remains 6c65e00f6212a7e2d52571bb7a279ec943efd19a,
full #1078 / run 39509869276 SUCCESS, JAR 12304984467, logs 12306121182,
private pack steps skipped. Local 24 offline contracts passed; Gradle distribution
bootstrap is network-blocked. Do not reuse #1078 as qualification of new code.

Full #1080 / run 39514286089 on eb6989a3 FAILED the screenshot raster oracle
(expected green ff00ff00, got black ff000000) after all earlier gates passed.
The fixture exposed a real production short-domain collision: WorldRenderer's
valid wrapping stamp -1 was treated as no traversal proof. ChunkArea now uses
explicit traversalFrameValid across all four readers, set at addSection and
cleared at resetQueue/clearVoxels. Actual-reader local checks pass all 65536 values;
restored old-sentinel logic fails at -1. Native producer boundary/reset/clear tests
and actual pixels at -1 must qualify this fix in a new full run.

Next: finish/review the correction and full exact-executable CI; fix actual failures
without weakening assertions. Exercise combined configuration/native consumers and
pixels plus available publication/lifecycle/recovery cases. Extend existing
mechanisms and pinned dependencies before adding machinery; record unmet need,
alternatives and smallest addition for substantial new mechanisms. Preserve
independent numerical oracles and no speculative performance claims.

Resolve all in-scope implementation gaps or justify concrete unsupported/necessary
CPU semantics without relabelling unfinished eligible work. Only after exact final
full CI and artifact verification, prepare one test package with commit/JAR/setup,
activation flags from current code, matched CPU baseline and integrated GPU run,
scenes/actions, expected visual/adoption/performance evidence, failure signals and
log paths. Keep real world/portal/private packs and RX net benefit explicitly
untested until observed. Do not request repeated intermediate owner tests.

Stop only at full internal acceptance, a demonstrated external blocker after all
independent work, or an actual execution limit. Never label interrupted/blocked
work complete. Update AGENT_STATUS.md, roadmap/ledger/checklist and this handoff
with actual commits, displaced/retained CPU work, validation/artifacts, risks and
next actions. Finish with a NEW complete copyable continuation prompt that requires
its successor to update the repository handoff and generate its own NEW complete
prompt carrying this same requirement forward. If internally complete, advance to
real-world qualification; otherwise resume the exact unfinished work. This
contingency does not replace finishing feasible work now.
