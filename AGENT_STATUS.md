# VulkanMod Forge 1.20.1 — Current continuation checkpoint

## Active gate

**Integrated implementation-readiness milestone complete; focused real-world
qualification is next.** Minecraft 1.20.1 / Forge 47.3.0 / Java 17 unchanged.
Follow AGENTS.md §3A and docs/NEXT_SESSION_PROMPT.md. The single completion
checklist is at the top of docs/GPU_FIRST_OWNERSHIP_2026-10-18.md. Actual-world/
release/default-promotion gates remain open; internal readiness is not speedup.

## Exact qualified executable and artifacts

- `9a6de8480c7ab46ccae7cdc06a652c108daa8078`, full
  [#1082 / 39551989165](https://github.com/Vorith03/VulkanMod/actions/runs/39551989165),
  **COMPLETED / SUCCESS, attempt 1**, checked against live run/job metadata/logs.
- [JAR artifact 12324685470](https://github.com/Vorith03/VulkanMod/actions/runs/39551989165/artifacts/12324685470):
  `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1082-g9a6de848-all.jar`.
  Downloaded bytes inspected: 11,074,747 bytes; SHA-256
  `c97997f24572fce48abbe739659b3b69808bd5f24d5e61d9ffbf28dc30f1e64a`;
  manifest/mods.toml version and new fixture class verified.
- [Logs artifact 12323799769](https://github.com/Vorith03/VulkanMod/actions/runs/39551989165/artifacts/12323799769).
  Forge compile/package/nested LWJGL linkage, all 24 offline contracts, baseline
  startup, combined GPU no-splash, native model/mesher/lighting/async publication,
  indirect/509-pair APPEND/REPLACE, post/depth, generated consumer/texture/particle
  pixels and synchronization, combined public Create Chronicles/IP and Crash
  Assistant passed. All three private-fixture steps skipped.
- Final documentation-only checkpoint reuses this executable; no renderer rerun
  is needed solely for updated documentation.

## Changes and causal evidence

- `1e049005`: shared 19-property integrated configuration in existing no-splash,
  screenshot and public compatibility clients; new CI-only production indirect
  draw raster edge. Default-off baseline startup retained.
- `eb6989a3`: explicit REPLACE-only test policy under APPEND-enabled clients;
  zero-exception APPEND, rejected-proof complete CPU recovery and re-adoption.
- `cfbd0437`: **production fix** for short traversal stamp -1 colliding with
  ChunkArea's absent-proof sentinel. WorldRenderer legitimately traverses every
  short value; graph visibility/copy/comparison/sparse masks previously suppressed
  a valid -1 traversal while accepting GPU command ownership. One explicit
  traversalFrameValid bit is established by addSection and cleared by resetQueue/
  clearVoxels. CPU traversal/stamps and native synchronization unchanged.
- `9a6de848`: test uses public releaseBuffers for teardown, retaining all assertions.

Actual log markers VULKANMOD_GPU_TRAVERSAL_STAMP_DOMAIN_OK and
VULKANMOD_GPU_GENERATED_INDIRECT_PIXELS_OK passed. Producer tests cover
-2/-1/0/MAX/MIN, off-frustum graph visits, direct seeds, reset and area clear.
The raster fixture deliberately uses -1 and the real queued candidate residency,
selector, RegionDrawBatch.draw, nonzero resident vertex offset and screenshot
readback. It proves REPLACE, ordered overlapping CPU/GPU APPEND, zero-exception
APPEND, frame-slot reuse, complete CPU fallback/re-adoption and no duplicate
accepted CPU command upload. **Resident vertices are independently seeded; this
is not a loaded-world mesher/portal pixel result.** Existing independent native
model/lighting/packed-vertex and publication/transaction oracles remain intact.

Local extracted actual readers passed all 65,536 valid stamps plus absent-proof
rejection; restoring the old sentinel failed specifically at -1. All 24 offline
contracts passed locally using the Java 17 compiler module. Gradle bootstrap was
network-blocked locally; only full CI supplies Forge/native qualification.

## Failed/cancelled attempts remain distinct

- #1079 / 39514023722 cancelled/superseded; not qualified.
- #1080 / 39514286089 on eb6989a3 failed actual generated-consumer pixels:
  expected ff00ff00, got ff000000 with GPU ownership accepted. Logs 12308050624;
  no qualified JAR. This exposed the production sentinel bug.
- #1081 / 39551568932 on cfbd0437 failed compileJava because the new test called
  private clearVoxels. Logs 12323488387; no native result or qualified JAR.
- Prior qualified baseline 6c65e00f / #1078 / 39509869276, JAR 12304984467,
  logs 12306121182 remains historical maintenance evidence; #1077 passed on
  c303cf26, #1076 failed compilation, #1075 cancelled.

## CPU ownership, safety and next action

Accepted GPU texture pixels bypass Java interpolation; exact campfire billboards
bypass CPU four-vertex expansion; qualified REPLACE/APPEND workers omit GPU-owned
ordinary tessellation; GPU selector ownership skips duplicate FrameBatch command
construction/serialization/upload. Unchanged candidate proofs are cached.
Clocks, Java/mod callbacks, sparse world/light/halo capture, graph connectivity,
changed candidate metadata, unsupported semantics and complete recovery remain
necessary CPU work, explicitly inventoried in the checklist. No new FPS claim.

Experiments remain default-off. Preserve generation/provenance/readiness, exact
portal frusta, 512 sections/1,024 commands, atomic APPEND CPU exceptions, queue
barriers, upload completion, fences and complete CPU fallback. The maintenance
tracker's retained/deferred decisions stand. PR #12 remains open at 3db6d0d5;
inspected, no recorder overlap or merge.

Use docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md as the **one focused test
package**, with scripts/ci/integrated-gpu-flags.txt, explicit same-JAR CPU-off
control, setup/scenes, separate visual/adoption/performance criteria and log paths.
No independently evidenced in-scope prerequisite remains for that package.
Actual loaded-world mesher/selector/portal pixels, private packs, real particle
callback/lighting/translucency/camera/reload/re-entry/sustained gameplay pressure,
and RX 6900 XT adoption/net CPU/GPU/FPS/p95/p99 remain unqualified. Obtain/analyze
that evidence next; do not manufacture an unrelated maintenance or optimization
slice to avoid the actual external boundary. Check existing supplied evidence
before requesting repetition. Fix any demonstrated defect before new owner tests.

Historical evidence is recoverable in Git and
[the archive](docs/AGENT_STATUS_ARCHIVE_2026-10-20.md), including older negative
#1067/#1060/#1064, cancelled #1071, #1070 attempt-1 failure versus attempt-2
success, #1054/#1050–1052/#1047–1048/#1041–1042 and #1028 UUID e05f2b31.

Every successor must update this handoff, relevant checklist/roadmap/ledger and
rolling prompt, then finish with a NEW complete copyable continuation prompt
requiring its own successor to do the same.
