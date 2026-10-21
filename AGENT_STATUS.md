# VulkanMod Forge1.20.1 — continuation checkpoint

## Active gate

**Integrated GPU effort remains OPEN.** Preserve Minecraft1.20.1 / Forge47.3.0 /
Java17; experiments default-off. Follow AGENTS.md §3A and the single checklist in
GPU_FIRST_OWNERSHIP. Maintenance retained/deferred decisions stand. Profiler PR#12
was inspected (3db6d0d5); no recorder overlap or delegated agents.

## Current executable checkpoint

Source:e1abcf39f8e9e993d3b21e0fa50a4e6543137a47. Full CI will start after this
documentation checkpoint push; inspect live head/run before assuming completion.
No new qualified JAR yet. GitHub equivalents of the logical local commits:
- de608023: terrain upload range barriers, raw-image repeated-write ordering,
  swapchain acquire stage chain, selector atomic-read visibility, qualified template.
- 4ffe59e6: ready old REPLACE retains geometry for atomic APPEND rebuild; actual
  dispatch failure remains distinct and forces complete CPU recovery.
- 7446dce5: compiled CPU-complete empty layer admits staged mixed rebuilding;
  uncompiled/incomplete empties and pending positive CPU geometry reject.
- ce34d648: voxel/sparse-light/selector probe atomic headers, including persistent
  fixture scratch, use transfer-to-shader READ|WRITE dependencies.
- e1abcf39: same-saved-input loaded-world oracle and required full CI gate.

## Local evidence

Java17 classes and both existing Gradle tests passed. Complete indirect/native and
screenshot runs exit0 with ZERO Validation Error/SYNC-HAZARD on Ubuntu24
Lavapipe25.2.8 / validation1.3.275. Native tests cover both previous generated
ownership modes, stale atomic publication, failed-producer recovery and complete
empty CPU -> paired commit. Screenshot covers independent seeded AND production
mesher -> bridge completion/publication -> selector/original RegionDrawBatch pixels,
full-ring retirement reuse, textures and particles.

Actual loaded-world CPU/GPU controls pass SEVEN phases with zero hazards and exact
640x360 raw pixels: initial REPLACE/mixed APPEND, dirty glass conversion, complete
CPU recovery, readoption, camera reversal, resource reload, save/reentry. Real
workers/publication/native rendering are used; no vertex readback/patch/reupload
feeds rendering. Accepted GPU captures have owner1/commandUploads0, ordinary CPU
indices0 initially then84 exceptions, mixed84, CPU-complete=false. Recovery gives
ordinary984/CPU-complete=true, then readoption84/false. No representative RX result.

Sibling tooling evidence: indirect-final.complete.log, screenshot-final.complete.log,
cpu-camera-final.complete.log, gpu-camera-final.complete.log. Current
run/gpu-world-smoke contains seven paired PNG/raw captures. Final full CI/JAR
qualification is still required. Startup negatives75 ->1 atomic RAW hazards were
corrected; the later complete indirect run has0. Old #1084 failed upload-domain
fixture preparation; #1085 failed assumed stone eligibility. No qualified artifacts.

## Input and execution limits

Separate rapid fresh-world setups gave unequal client light inputs (below8 versus
server11, or above2 versus15). No GPU numerical defect was established. Prepare and
close ONE authoritative save, then load independent copies of the SAME bytes; actual
client/server probes agree and all pixels match. Dirty/recovery/reload/reentry
remain real. Initial rapid-mutation lighting is a fixture setup limitation, not
claimed fixed. No world light arrays or vertices were patched.

Active log views were stale; completed processes/readbacks did not establish a
hang. The speculative Java watchdog and vertex readback diagnostics were removed.
Wait/resume the exec session before reading complete outputs. Local launcher
creates a separate complete log from its own descriptor after process completion.

Tools/cache are outside Git under /workspace/scratch/34a9c7ba8b11/tooling: Gradle8.1.1,
Java17 javac-module wrapper, extracted official Ubuntu Vulkan/Xvfb and run-local.py.
Derive proxy ports inside the SAME exec invocation. Xvfb uses loopback TCP because
UNIX bind is unavailable. Root and /tmp caches can vanish between contexts. Avoid
concurrent cache/build-file mutation. A truncated generated client-extra local JAR
was rebuilt from verified client.jar/pinned mappings, all ZIP entries checked; no
project dependency upgraded. All24 offline contracts passed before the final slice;
rerun after final changes.

## Next independent work

Create a genuine same-dimension portal with the existing public IP3.0.7 /
Cloth11.1.136 fixture and saved-world oracle. Pinned Apache2.0 JAR APIs inspected:
PortalManipulation.createOrthodoxPortal and RenderStates.getRenderedPortalNum.
Require actual portal pixels and GPU adoption, not just shader/target fixtures.
Inspect one persistent selector output per region/layer and one in-flight parameter
set per frame before changing multi-view admission. Do not assume a fix before
actual evidence. Preserve exact frusta, bounds, fences and complete CPU fallback.

Accepted textures bypass Java interpolation, exact campfire billboards CPU expansion,
terrain workers eligible tessellation, GPU-selected indirect commands FrameBatch
construction/serialization/upload. Clocks/callbacks, world/light/halo capture,
connectivity, changed metadata, unsupported semantics and complete recovery remain
required CPU. No speedup/default promotion. Finish feasible internal work, then full
exact-executable CI/JAR verification and one updated owner package in
GPU_GENERATED_WORLD_DRAW_WITNESS. Private packs, representative owner scenes and
RX6900XT profitability remain distinct external evidence. No intermediate owner test.

## Historical rollback baseline

Last full qualified executable:9a6de8480c7ab46ccae7cdc06a652c108daa8078,
full#1082 /39551989165 SUCCESS attempt1. JAR12324685470,
VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1082-g9a6de848-all.jar,
11,074,747 bytes; SHA256 c97997f24572fce48abbe739659b3b69808bd5f24d5e61d9ffbf28dc30f1e64a;
logs12323799769; private packs skipped. This does not qualify current changes.

Every successor must update this handoff, relevant checklist/roadmap/ledger and
NEXT_SESSION_PROMPT, then generate its own NEW complete copyable continuation
prompt requiring the next successor to do the same. This is not a stopping rule.
