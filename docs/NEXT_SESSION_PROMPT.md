# VulkanMod — Implement Maintainability and Reuse Improvements

Work in `Vorith03/VulkanMod` on `forge-1.20.1`, preserving Minecraft 1.20.1, Forge 47.3.0 and Java 17.

Address the repository's reuse and maintainability reviews through implementation, verification and documented decisions. This is an implementation assignment: do not stop at another audit, a plan, documentation changes or one trivial cleanup while additional justified work remains feasible.

This assignment authorizes ordinary repository edits, logical commits and pushes to `forge-1.20.1`. Do not force-push, rewrite shared history, merge unrelated PRs, publish releases or deploy anything.

## 1. Recover live authoritative state

Follow current `AGENTS.md` Section 3A. Inspect live HEAD, intervening commits, CI and overlapping development before editing.

Read current repository copies of:

- `AGENTS.md`
- `ROADMAP.md`
- `AGENT_STATUS.md`
- `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`
- `docs/REUSE_AUDIT_2026-10-20.md`
- `docs/REUSE_AUDIT_INVENTORY_2026-10-20.tsv`
- `docs/MAINTAINABILITY_AND_REUSE_REVIEW_2026-10-20.md`
- Ownership and validation contracts relevant to each proposed change.

Both audits are tracked under `docs/` on this branch; read the live repository copies rather than requiring attachments or replaying historical audit commits. These are review findings, not implemented migrations or exhaustive correctness certification.

Reviewed executable: `682b0d6bb59b249c5be66517209f720e54ac6d1d`, full CI #1074 / run `39308824108` successful; JAR artifact `12214688900`, smoke logs `12214474322`. Private resource packs were skipped. Later audit/prompt documentation does not change the executable. Verify newer live evidence before relying on this checkpoint.

Inspect PR #12 and newer overlapping work before touching profiling; do not build a competing recorder.

## 2. Scope and tracking

The owner explicitly prioritizes this maintenance assignment. Preserve the standing GPU-first objective and unresolved actual-world/performance gates.

Create one concise tracking document covering every finding in both reviews. Combine overlaps. Record the concrete evidence, bounded action, disposition, commits, validation and remaining risk. Dispositions must distinguish implemented/validated, implemented/validation-pending, retained-after-evaluation, deferred-with-specific-reason and blocked-by-identified-dependency.

Verify each recommendation before acting. Retaining existing code after a concrete comparison is acceptable; an unevaluated “too risky” or “future work” label is not a completed assessment. Do not force every proposed refactor or dependency into the codebase.

## 3. Start with clear bounded improvements

1. Verify whether the old `render/chunk/build/LiquidRenderer` has supported consumers. If unused, remove it and its annotation-only Fabric compile dependency. Remove the Fabric Maven repository only if no consumer remains. Preserve the real Forge liquid path and vertex adaptation.
2. Replace applicable `VUtil.UNSAFE` operations in `MappedBuffer`, `VRenderSystem` and `Drawer` with pinned LWJGL memory accessors. Remove the reflective bootstrap after all consumers are handled. Preserve exact representation/address arithmetic; do not claim this alone adds lifetime or bounds safety.
3. Evaluate replacing `SortUtil` with existing fastutil primitive stable sorting. Preserve descending `Float.compare`, stable ties and translucent index order. Differentially verify empty/singleton, random, equal-distance, signed-zero, infinity and NaN cases against current behavior.
4. Verify and remove unused copy overloads, `memcpyImage`, unused `MemoryType.copyFromBuffer` and inactive duplicate code where justified. Check reflective registration, packaging and supported external consumers. Preserve live copy/reset semantics.
5. Correct stale shadow-only/production comments and clarify shared texture modes. Condense the living checkpoint while retaining historical evidence and negative results in linked archives/Git.

Finish and validate coherent cleanup groups before structural changes.

## 4. Address substantive maintenance risks

- **Native ownership:** clarify owned/borrowed wrappers, closed-state behavior and pointer exposure. Evaluate explicit retirement requirements instead of implicit concrete-type exceptions. Preserve upload completion, frame fences and deferred destruction. No Cleaner/finalizer substitutes for GPU synchronization.
- **Section/cache state:** document transitions before changing representation. Evaluate per-layer cache state and named producer modes. Preserve input versus visible generations, producer invalidation, exact provenance/frusta, CPU completeness, staged APPEND and its atomic transaction. Avoid per-frame/candidate allocation growth.
- **Java/GLSL ABI:** strengthen agreement checks for constants, versions, capacities, offsets and representations. Extend existing native/layout tests before adding generators or native reflection. Preserve independent expectations and round-trip coverage.
- **Feature admission:** make requested versus effective activation and rejected prerequisites clear. Share parsing without erasing feature-specific policy. Keep early mixin bootstrap independent of Vulkan class initialization and retain startup-only/default-off behavior.
- **Tests:** distinguish source/bytecode-shape guards from behavioral proof. Strengthen selected failure paths with failure injection, release accounting, stale-state rejection and no-partial-mutation checks. Keep independent oracles independent. A framework migration alone is not a testing improvement.
- **Optional integrations:** reduce duplicated discovery/exception mechanics where useful while retaining per-mod contracts. Distinguish absent mod, unsupported installed API and underlying integration failures. Preserve causes and optional class-loading isolation.
- **Cohesion/units:** extract one responsibility only when invariants become clearer. Keep command ordering and retirement visible. Name byte/word counts and offsets, document buffer mutation and verify nonzero positions/slices before changing copy APIs. Coordinate profiler changes with overlapping work.

## 5. Evaluate larger reuse candidates

Assess existing Vulkan pipeline-cache reuse for production compute; a small shared compute-construction/cleanup helper; VMA virtual allocation for generic free-range bookkeeping; an existing GLSL parser; Minecraft widget behavior; and remaining utility/query duplication.

Compare removed custom responsibility against added dependency, lifecycle, packaging and compatibility costs. Prefer existing dependencies. Verify exact versions and distribution/licensing compatibility before adoption; do not upgrade foundational components.

For compute caching, preserve device identity, initialization/teardown, cache limits, host synchronization and failure fallback. Measure cold/warm creation or reload before claiming benefit.

VMA does not replace readiness, ownership, stable reservations, growth or fence retirement. A GLSL parser does not automatically provide preprocessing, Minecraft binding semantics or arbitrary shaderpack support. Evaluate a representative shader corpus, generated bindings and native output. Do not introduce a general renderer framework.

## 6. Validate and continue

Follow current repository validation and CI policy: focused local checks, coherent milestones, batched pushes and required produced-JAR/Forge/native/compatibility gates for executable changes. Do not weaken assertions or remove coverage to obtain green CI. Diagnose and fix a failed change or revert that bounded change.

Preserve generation/provenance/readiness checks, precise portal frusta, atomic APPEND CPU exceptions, 512-section/1,024-command bounds, queue barriers, fence-safe retirement and complete CPU fallback. Keep all experiments default-off.

Do not claim CPU displacement, FPS gains or release completion from cleanup or synthetic fixtures. Do not repeat settled owner-machine tests. If hardware evidence is genuinely needed, request one narrow test with a discriminating observation. If validation is unavailable, state exactly what remains unverified and continue independent work.

Work through every finding. Do not stop after the easiest items; do not force all migrations into one commit or claim deferred work is implemented. A finding may be retained or deferred only with an inspected contract, concrete tradeoff or identified blocker. Continue other feasible work when one item is blocked.

Before ending, leave implementation/tracking recoverable; report changes separately from retained/deferred candidates and pending validation; give exact commits and CI evidence; update the durable checkpoint and this prompt. Preserve the unresolved real-world GPU/portal and RX profitability gates.

**Perpetual handoff:** finish with a NEW complete copyable continuation prompt based on actual remaining work. Explicitly command the successor to update the repository handoff and end with its own newly generated continuation prompt carrying this same requirement forward.
