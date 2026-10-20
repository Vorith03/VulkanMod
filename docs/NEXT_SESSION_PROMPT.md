# VulkanMod — Qualify and Finish Maintenance Improvements

Continue implementation in https://github.com/Vorith03/VulkanMod on
`forge-1.20.1`, preserving Minecraft 1.20.1, Forge 47.3.0 and Java 17.
Ordinary edits, logical commits and pushes are authorized. Do not force-push,
rewrite shared history, merge unrelated PRs, publish releases or deploy.

Follow current AGENTS.md §3A against live Git, overlapping work and CI. Read this
prompt in full, current ROADMAP.md, AGENT_STATUS.md,
docs/MAINTENANCE_TRACKER_2026-10-20.md, docs/OPTIMIZATION_EVIDENCE_LEDGER.md,
both October 20 review documents and affected ownership contracts. The inventory
is the original audit baseline, not an assertion that current files are unchanged.
Inspect intervening changes; live evidence supersedes every checkpoint here.

## Current implementation and pending qualification

Pushed maintenance milestones:
- `a15ce968e5b7078790a9a097602480564a6f25da`: obsolete liquid/Fabric bridge,
  unused copy/readback APIs, LWJGL scalar access, primitive stable sort and comments.
- `0b50f449e8f937b932feb3d30a5b72b4a541dbc8`: checked mapped lifetime,
  explicit retirement policy, partial uniform-binding cleanup, per-layer cache
  state/named modes, candidate ABI and behavioral tests, IP cause-unwrapping reuse.
- `27a14fca0dfea9f1b824239138c04fcbd9f623aa`: shared compute factory,
  texture configuration admission, device/family-keyed capability query, abstract
  retirement requirements, texture ABI checks and verified utility removals.

Full #1076 / run `39507963173` on `27a14fca` FAILED compileJava:
seven remaining direct accesses to private MappedBuffer.buffer in BufferUploaderM
and RenderSystemMixin. Both now call byteBuffer(); a fresh full run must qualify
the correction. Failed smoke-log artifact `12304163339`; no native result/JAR.
First inspect the latest correction run and exact failed step/logs if red. Fix or narrowly
revert a failed change; do not weaken tests. Preserve the required produced-JAR,
Forge startup, native rendering/synchronization and public compatibility gates.
Full #1075 / `39506746716` was CANCELLED/SUPERSEDED during public modpack
validation, after build/startups/indirect/post/depth/screenshot steps passed.
It is not a successful full suite. Last fully qualified baseline remains
`682b0d6bb59b249c5be66517209f720e54ac6d1d`, full #1074 / `39308824108`,
JAR `12214688900`, logs `12214474322`, private packs skipped.

Locally, 24 offline contracts passed twice. Actual LWJGL 3.3.1/fastutil 8.5.9
focused tests passed for native mapped ownership/copy state/alignment, stable
sort differential and independent order, and candidate/texture ABI shape plus
serialization/pair-capacity behavior. Pure optional invocation and all 16 texture
configuration combinations passed without renderer initialization. Mutants for a
closed-address escape and wrong numeric sort order were rejected. Direct Gradle
bootstrap is network-blocked; Java 17 includes the compiler module despite no
javac launcher. Use current environment evidence rather than assuming these
transient tools/caches still exist.

## Finish remaining useful work

Use the tracker as the single combined finding register. Do not repeat the audits
or call every deferred candidate implemented. Resolve pending validation first,
then continue justified bounded changes while useful independent work is feasible.

1. Confirm the added actual uniform failure test runs and catches release leaks,
   double release and stale/partial writes. Demonstrate mutation sensitivity for
   the cleanup paths, supplementing rather than removing existing shape guards.
2. Exercise ComputePipelineFactory compile/module/pipeline creation failure paths
   with a bounded injectable seam or native failure fixture. Verify exact temporary
   SPIR-V/module release and failed partial-pipeline cleanup. Avoid a general native
   mocking framework or obscuring subsystem dispatch/barrier/retirement ownership.
3. Review new cache-holder invalidation/publication against the transition table
   and the existing producer-revocation/stale-publication/native APPEND tests.
   Extend a missing behavioral case only for a specific uncovered transition.
   Do not merge input and visible generations or add per-candidate allocations.
4. ABI constants/text checks supplement native output, not prove it. Confirm the
   1,024-command atomic-pair/version/representation and texture native pixel gates
   still pass; extend independent expectations where a concrete field remains
   unchecked rather than introducing a generator/reflection dependency.
5. Larger reuse candidates have inspected decisions in the tracker. Compute cache
   reuse requires an explicit device-scoped service replacing Pipeline's static
   capture, synchronized host access, safe teardown/identity/corrupt-cache fallback
   and cold/warm measurements. Parser migration requires a pinned distribution-
   compatible version/license and representative shader corpus plus binding/native
   output/packaging/reload comparisons. VMA remains retained due stable Segment,
   growth/offset, arbitrary-stride/readiness policy versus added native lifetime;
   reconsider only against a discriminating trace/cost comparison. Widget migration
   needs staged apply/cancel plus keyboard/narration/drag/scroll/resize tests on a
   real screen. Do not force these migrations to close checklist rows. Profiler
   PR #12 overlaps recorder work; inspect it before touching profiling.

Keep implemented/validated, validation-pending, retained-after-evaluation,
specifically deferred and identified blockers distinct. Record exact commits,
commands, CI/artifacts, failures, decisions and residual risk. The old status is
preserved verbatim in docs/AGENT_STATUS_ARCHIVE_2026-10-20.md; use current status
for sequencing, not historical “latest” headings.

## Preserved renderer and evidence boundaries

All experiments stay default-off. Preserve generation/provenance/readiness,
512-section/1,024-command bounds, exact portal frusta, atomic APPEND CPU exception
pairs, queue barriers, upload completion, frame fences and complete CPU fallback.
Maintenance and fixtures establish no FPS gain, CPU displacement or release
completion. Actual-world generated REPLACE/APPEND nonzero selected commands,
matched main-world/genuine-portal CPU/GPU pixels, private packs, real particle
callback/lighting/translucency/camera/reload/frame-pressure and RX 6900 XT
adoption/net CPU/GPU/FPS/p95/p99 remain open. Use the existing generated-world
witness contract when its inputs are available; do not repeat settled owner tests
or ask for an isolated benchmark. Preserve historical failed/cancelled evidence.

Before ending, update the tracker, durable AGENT_STATUS.md and this repository
handoff with actual outcomes and remaining work. Finish with a NEW complete
copyable continuation prompt. Explicitly instruct its successor to update the
repository handoff and finish with its own newly generated continuation prompt
carrying this same requirement forward perpetually.
