# CI execution and qualification

The owner requested aggressive speed/execution reductions on 2026-10-09,
while retaining robustness and accurate validation claims. This policy replaces
the earlier full-native-suite-on-every-executable-push rule with explicit scopes.

## What runs

| Scope | Offline contracts | Forge build / packaging / CPU checks | Native renderer and compatibility | Published JAR |
| --- | --- | --- | --- | --- |
| `full` | All 19 | All | All eight existing launches | Only after the entire suite passes |
| `build` | All 19 | All | Deferred | None |
| `contracts` | All 19 | Reused baseline; not rerun | Reused baseline; not rerun | None |
| `none` | None | Reused baseline | Reused baseline | None |

Production Java, shaders/resources, build/dependency configuration, native smoke
fixtures, packaging checks, CI-control changes and unknown files default to
`full`. An exact allowlist permits offline contracts/deployment-tool changes to
use `contracts`; test-source and local-check-wrapper changes use `build`.
Controller changes always force `full`, including changes to the controller's
own regression. No inferred Java dependency graph is used to omit native gates.

The planner compares **the whole Git diff from the last successful complete
native suite**, not just the most recent push. It verifies the successful build,
all native gate steps, animation/Immersive Portals packaging evidence,
log/JAR uploads and one of the two mutually exclusive Create Chronicles
fixtures in GitHub job metadata. A quick/tooling-only green run cannot become
that baseline. Renames include both removed and added paths. Missing evidence,
unrelated/rewritten history, unavailable APIs and exhausted bounded lookup all
fall back to `full`. Thus a tooling push after a deferred or failed renderer edit
still validates the outstanding renderer changes.

## Fewer expensive executions

Production pushes first use a single-CPU `ubuntu-slim` planner. A 45-second quiet
window, combined with workflow concurrency cancellation, coalesces rapid pushes
before allocating the four-CPU Forge/native runner. Only full push runs wait;
fast scopes and PR checks start without this intentional delay. The optional
repository variable `VULKANMOD_CI_SETTLE_SECONDS` overrides it, clamped to 0–120s.
This reduces expensive job starts in bursts, **not GitHub's count of push-event
workflow entries**. A lone full push incurs the quiet-window latency. Coalescing
does not modify Git history or claim that skipped revisions passed tests.

For intermediate changes needing remote CPU/compiler feedback, use this commit
trailer on its own line:

```text
CI-Scope: quick
```

This explicitly selects `build`, with native validation deferred and no JAR
publication. It requires a usable full baseline and cannot bypass CI-control
validation. The completed milestone omits that trailer and runs `full`. This is
an option for useful remote feedback, not a reason to push every micro-edit.

PRs target only the maintained `forge-1.20.1` branch. Draft PRs get `build`;
`ready_for_review` explicitly triggers the full merge-result suite. Ready PRs
always get `full`; push baseline reuse never substitutes for merge-result tests.
Documentation-only pushes/PRs remain excluded by existing path filters.

## Faster checks and reliable results

Independent CPU contracts run with at most four workers. Each retains its own
temporary JVM/compiler fixture, complete log, exit code and elapsed time. Failed
contracts cancel queued work; already-running contracts finish within a bounded
timeout. Timeout or runner cancellation terminates each subprocess group,
including nested `javac`/Java children. Missing commands/scripts and runner errors
are failures, not skipped successes. Stateful native launches remain isolated:
the previous combined renderer mode failed and was reverted.

Private-pack configuration/download now fail before compilation and native
startup. Native fixture checks, validation-layer error rejection, production
packaging verification, all Gradle regressions and both splash configurations
remain unchanged. Partial or failed runs retain logs and scope reports but cannot
publish a distributable as qualified. Missing qualified JARs fail artifact upload.
Logs retain seven days; passing distributables retain fourteen days. Artifact
retention reduces future storage, not previously accrued usage.

## Validation and limits

Final 19-contract comparison on the same workspace/toolchain: serial **15.140s**,
four workers **6.378s**, both successful (about **58% less wall time**). An earlier
implementation iteration measured 18.693s / 5.394s; timings vary with host load
and warm-up. These are local CPU-contract timings, not measured hosted-CI or
renderer improvements. Forge `classes testRegionBatchLayout
testSectionVoxelSnapshot` also passed (`BUILD SUCCESSFUL in 24s`, eight tasks).
Use `python3 scripts/ci/run-contracts.py --workers 1` or `--workers 4` to compare
locally with Java 17 on PATH.

Workflow steps decrease from **40 to 26**, including the two planner steps;
the eight full-scope Minecraft launches and required assertions are retained.
The controller regressions cover missing/skipped native, packaging and artifact
baseline gates, a sequence of seven partial successes, deferred runtime changes,
unknown paths, renames/deletions, quick controls, missing scripts, child-process
timeouts/cancellation and qualified-artifact publication guards. Official actionlint 1.7.12
validates the workflow. A public API integration check recognizes #1005/SHA
`50a92891b45121d73a9271138387e48effba12dc` as the complete baseline and correctly
requires `full` for the currently unvalidated audit/CI changes.

**The fork-level Actions block cleared.** Full hosted build **#1006**, commit
`084dc8183ab6612134ddff762fac2ea9b56bf8fd`, run `37997041921`,
completed green on 2026-10-09. It passed all 19 offline contracts, Forge
compilation and packaging, native screenshot/animation/legacy-FBO oracle,
Create Chronicles and Crash Assistant; the qualified JAR upload succeeded.
This closes the CI and audit validation previously blocked after #1005.

The #1006 job exposed an additional cache inefficiency: a **1.23 GB primary-key
`setup-java` Gradle cache hit** was restored, and the post action refused to
save changes. The combined Create Chronicles fixture then spent approximately
125 seconds with no Gradle progress output between configuration and tasks,
consistent with additional dependency resolution. The workflow now includes
that fixture and Gradle properties in `cache-dependency-path`, rotating the
immutable cache key once so a full run can seed the combined modpack
transforms. This is a hypothesis to benchmark against the next warm run, not a
proven 125-second saving; it also costs an initial cold cache fill. A thirty-run
baseline lookup prevents seven or more successful quick runs from needlessly
forcing another native job. Production and native gate selection are unchanged.
Public runner billing and fork usage protection remain separate issues.
