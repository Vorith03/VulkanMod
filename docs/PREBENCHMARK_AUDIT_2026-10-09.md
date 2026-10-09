# Prebenchmark adversarial audit — 2026-10-09

User-requested detour before the next RD32 O3/O4 benchmark. Baseline is
`b5034ac` (documentation-only after executable `50a9289`, public CI #1005).
The audit preserves Minecraft 1.20.1, Forge 47.3.0, Java 17, renderer architecture,
O3/O4 defaults and initial-population convergence rules.

## Confirmed defects and fixes

| Area | Evidence | Result |
| --- | --- | --- |
| Transparency sorting | The 15-section limit counted opaque/empty sections; null transparency state still allocated/cancelled/scheduled a task that could not sort. | Count only admitted sorts; reject missing state before allocation/cancellation. Camera threshold and worker/publication ownership remain unchanged. |
| Camera profiling | The old `Profiler` accumulated camera histories; no live consumer read that profiler's results. | Remove its camera hooks and class; retain `Profiler2`, benchmark attribution and GPU timestamps. |
| Entity model normals | `new Vector3f(polygon.normal)` allocated for every polygon. | One invocation-local scratch vector, copying the source normal before the same matrix transform. Source normals and reentrant consumers remain safe. |
| Memory safety sampling | Healthy texture staging parsed full process status and meminfo maps every quarter second. | Read only MemTotal/MemAvailable, stopping after both. RSS is read only on pressure; diagnostic read failure cannot suppress confirmed pressure protection. Reserve policy and cadence remain unchanged. |
| Texture tick/atlas failures | A failing tickable or SpriteContents upload skips RETURN cleanup, stranding scopes/queue ownership or pending regions. | Finally cleanup restores original scope depth, drains copies before atlas transitions, submits only owned active queues and clears failed layout bookkeeping. Original tick failures retain cleanup failures as suppressed exceptions. |
| Legacy framebuffer shim | Status always returned COMPLETE, renderbuffers falsely succeeded, `Renderer.beginRendering` never began the Vulkan pass, deletion was not intercepted, and the color constructor allocated unrelated depth and owned borrowed texture storage. | Supported unified mip-0 color/depth texture attachments now start real LOAD passes, report incomplete/unsupported states and intercept deletion. Incomplete binds terminate the unrelated previous pass. Borrowed backing retires before owning texture views; resize/deletion never frees borrowed images. Unsupported renderbuffers, separate READ/DRAW bindings, extra attachments and nonzero mip attachments fail explicitly. |
| Dead code | Unused quicksort/swap/median code, null-backed `CircularIntList.restartIterator`, unused renderer stub pair and commented alternatives. | Remove only unreferenced paths; keep the active merge/insertion sorting and circular iterators. |

## Review scope and limits

Reviewed terrain scheduling/publication and transparency admission; camera/grid
work; model and particle submission; animation usage, GPU residency/interpolation,
staging/batch ownership; renderer synchronization, pipelines/descriptors; texture
names, framebuffers/render targets; memory safety; profiling and compatibility
activation. This is a scoped source audit, not a proof that every modpack path or
performance bottleneck is fixed. No hardware speedup is claimed.

Recurring already-compiled terrain maintenance must remain live and measured.
The audit does not freeze those callbacks, increase settling timeouts, enable
unqualified GPU paths, or remove intentional resource lifetime barriers.
Distant Horizons still suppresses unsupported OpenGL LOD drawing while retaining
maintenance/data work. Legacy Flywheel backend selection stays off with the
working Create vanilla fallback. Those limitations remain explicit; neither has
been promoted to a functioning automatic Vulkan renderer by this patch.

## Validation

Focused local contracts pass:

- `prebenchmark-audit-contract.py`: production-method sort quota/admission,
  memory parser (order/missing/malformed/overflow), normal allocation/source
  immutability/packed-fallback parity/reentrancy, successful/failed/nested texture
  cleanup, lost-queue restart, atlas draining and layout failures.
- `legacy-framebuffer-contract.py`: production shim status and real pass routing,
  LOAD backing reuse, incomplete isolation, borrowed lifetime/storage replacement,
  deletion, explicit unsupported calls and failed pass creation.
- Existing terrain-population, chunk-budget, upload-GPU ownership, animation
  numeric/visibility, attribution-capture, particle-attribution, benchmark HUD and GPU-HUD hook contracts pass.
- `git diff --check` passes. The original local attempt could not download Gradle.
  [The continuation recovered local Java 17/Gradle 8.1.1 validation](WORKSPACE_VALIDATION_2026-10-09.md),
  repaired a generated Minecraft archive, and caught a real cross-package
  transparency-state access error missed by the isolated harness. `cbb1983` adds
  the narrow admission accessor. Main/test Forge compilation, reobfuscated
  distributable verification and the Gradle-check regressions now pass locally;
  native rendering and full public CI remain unverified.

Both new CPU contracts are wired into the existing full build workflow. The
validation-enabled screenshot gate now includes a native pixel/lifetime oracle
through transformed GlStateManager methods: real color/depth attachments, LOAD
preservation, mismatched attachments, resize before submission, intercepted
idempotent deletion and subsequent owner-target use. Executable commit `2eb98ff` is published, but exact-SHA Actions queries return
zero runs/check suites after publication: full CI has not started and remains
unverified. No new-workflow dispatch capability is exposed; existing older-run
retries would not validate this commit. AGENT_STATUS records the blocking gate. Private pack and owner RD32 evidence remain
separate from software-Vulkan CI evidence.
