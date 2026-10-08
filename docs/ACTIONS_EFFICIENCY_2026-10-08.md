# Actions efficiency — 2026-10-08

Vorith03/VulkanMod is public. The forge-1.20.1 workflow uses standard ubuntu-22.04 hosted runners. Current GitHub billing documentation says standard hosted execution in public repositories and self-hosted execution are free of runner-minute charges. Artifact/cache storage and larger runners have separate rules. VeLLM is private; do not attribute its exhausted private-runner allowance to VulkanMod's public Ubuntu jobs.

Source: https://docs.github.com/en/billing/concepts/product-billing/github-actions

## Observed execution samples

| Run | Build job | Elapsed execution |
| --- | --- | ---: |
| 37600141289 (#1005) | 112722310635 | 7.7 min |
| 37454136840 (#1002) | 112237469104 | 5.6 min |
| 37453506436 (#1001) | 112235430128 | 4.6 min |

These durations exclude queue time and are not billed private minutes or a monthly total. In #1005, the distributable build took about 1.3 minutes and Create compatibility about 2.9 minutes. Preserve the renderer/compatibility suite: it catches real regressions and does not consume the private hosted-minute allowance. The most recent 50 runs contained 22 successes, 18 failures and 10 cancellations, a partial history rather than proof every failure was avoidable waste.

## Iteration policy

The existing workflow already ignores Markdown/docs-only push and PR changes, caches Gradle dependencies, and cancels superseded work. No workflow downgrade is introduced for this request.

1. Inspect the live forge-1.20.1 branch, current checkpoint and latest applicable CI evidence.
2. Batch related fixes; run scripts/ci/agent-check.sh and focused local contracts where supported.
3. Push one coherent executable milestone and inspect one meaningful full result. Read the actual failed step before changing code.
4. Reuse that result for later docs-only checkpoints. Cancellation is a backstop, not a refund for time already spent.
5. Do not repeatedly rerun an unchanged job while a known external quota/payment/runner block remains. Do useful bounded local work and record pending gates.
6. Poll according to observed time-to-gate, doing adjacent work during the wait. Status reads do not start Actions jobs or consume runner execution minutes.

Keep production JAR and renderer validation evidence distinct from local contracts, compilation and hardware results. Do not enable O3/O4, relax the strict O5 settling/capture gate, or claim RX hardware qualification from this policy update. The current #1005 executable remains the applicable public-CI evidence; this documentation-only commit adds no runtime result.

A self-hosted runner is unnecessary to solve private-minute exhaustion for this already-public repository. It may eventually help hardware qualification, but owner-machine provisioning, isolation and benchmark reproducibility are separate decisions. Repository visibility and billing settings are unchanged.
