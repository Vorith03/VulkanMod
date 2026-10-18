# RX RD32 #1025 BufferBuilder maintenance capture — 2026-10-17 / October 18 UTC

## Provenance and validity

- Owner supplied benchmark UUID `7b8d70be-4cc7-42c4-a583-19a38aac2d4c`, raw 180-second Java Flight Recorder capture, `latest(20261018-031729).log`, and `debug(20261018-031733).log`.
- Tested #1025 Forge build `0.3.2-forge.2-build.1025-g7bff3d7c`, qualified executable `7bff3d7c984ab4e29385c359967e3af9e41d3dbb`, full green CI [run #1025](https://github.com/Vorith03/VulkanMod/actions/runs/39091850361). Debug confirms `render.vertex.BufferBuilderM` and `render.FrustumInfiniteBoundsFastPathMixin` applied in production.
- `capture_complete reason=benchmark_complete`, **8,585 frames in 180.039 s** (~47.68 FPS). Initial-population settling 72.940 s; baseline was 71.881 s. World `VulkanMod_Benchmark`, spectator at (0,192,0), yaw -90°, pitch 30°, RD32 / simulation 12, fixed 2552×1374 framebuffer, Java 17.0.15, AMD RX 6900 XT RADV. Both #1024/#1025 logs record `fps_cap=260`, VSync false; no control change here. The input GPU texture flags and disabled GPU terrain mesher/bypass/handoff/hybrid remain nominally equal. Captures declare stable pose and no framebuffer changes; the owner's prior restored-world-snapshot assumption is retained, but exact snapshot bytes/JIT/server/thermal state are not independently controlled.
- GPU timestamps are valid for 8,585/8,585 main-graphics frames, 0 read failures; separate upload GPU batches 3,462/3,462, 0 read failures. JFR reports 23,212 Java execution and 17,523 native-boundary samples, 40,915 decoded events, 4 excluded outside capture. No VUID/device loss or relevant fatal Vulkan errors found in owner latest/debug. These facts do not certify visual correctness or modpack-wide compatibility.
- Raw fingerprints (outside Git): benchmark SHA256 `efad0f2f035826a9fdf908828a3734c8de4ff3941880f4d34b8a1819ffb435d8`; raw JFR `9b73e346879bb13feeff6a8420efc3929fc4db4b206c92190fae0ea62904baad`; latest `c2195c68bed5de97f0b7cbb2ee15f54698762d1911d697727529c2a0e46580b4`; debug `8d5627d29cfddd641151572cbc862152c487dd12624d5f1b933a518d1e94a40e`.

## Cross-executable screening against already received #1024

Both raw benchmark logs and both independently decoded JFRs were inspected, not the textual top-40 summary alone. #1024 is UUID `1851e3c8-ccef-42ed-9191-126d1d45ae77` at qualified executable `3f77cab2941f60a0a935b2f609f28317072b59f9`. Do **not** request #1024 or #1025 OFF recapture for this screening comparison.

| Metric | #1024 baseline | #1025 new | Observation |
| --- | ---: | ---: | --- |
| Frames / seconds | 9,167 / 180.022 | 8,585 / 180.039 | ~50.92 → ~47.68 FPS, **-6.4%** |
| Last ~60-s windows FPS (120–180 s) | 54.64 | 51.76 | Unfavorable after initial transients too |
| Client tick CPU ms/tick | 26.066 | 26.443 | +0.377 (+1.4%) |
| Texture CPU ms/tick | 14.095 | 14.491 | +0.396 (+2.8%) |
| Sprite-upload CPU ms/tick (nested) | 7.812 | 7.142 | -0.670 |
| Texture non-upload CPU ms/tick | 6.283 | 7.349 | +1.066 |
| Particle tick CPU ms/tick | 4.456 | 4.428 | -0.028 |
| Particle rendering CPU ms/frame | 3.776 | 3.892 | +0.116 (+3.1%) |
| Main graphics GPU ms/frame | 2.890 | 3.545 | +0.655 (+22.7%) |
| Texture upload GPU ms/batch (separate scope) | 2.914 | 3.215 | +0.301 (+10.3%) |
| Common texture copy flush CPU ms/tick (nested) | 8.084 | 8.188 | +0.104 |
| Texture flush epochs | 220,055 | 249,468 | Workload/queue behavior changed |
| World visible sections / retained dirty | 6,559 / 1,604 | 6,559 / 1,604 | Comparable steady section count, unresolved notices |

Do not add nested CPU scopes or the separate GPU scopes. Notably the +22.7% graphics GPU time is not mechanically explained by the small #1025 CPU-only code patch; it reveals cross-run execution/workload variation rather than proving a Vulkan GPU regression. More terrain maintenance occurred in #1025 (terminal scheduled 26,862 versus #1024 26,610). Campfire smoke still dominates with 29,128,357 / 29,781,730 particle tick calls (97.81%) versus #1024 29,471,618 / 30,127,224 (97.82%).

## Direct JFR evidence on the intended mechanism

All counts below are **Render-thread `jdk.ExecutionSample` events whose stacks include `ParticleEngine.render`**, not elapsed CPU milliseconds. #1024 has 3,138 such samples; #1025 has 3,051.

| JFR particle-render cohort | #1024 | #1025 | Meaning |
| --- | ---: | ---: | --- |
| `BufferBuilder.m_85722_` (`ensureCapacity`) sampled leaves | 953 | 909 | Essentially unchanged fraction of sampled rendering (~30.4% → 29.8%) |
| `ensureCapacity` anywhere in stack | 976 | 927 | Persistent hot path |
| `ensureCapacity` within `Tesselator.m_85914_` finalization | 937 | 901 | Dominates the targeted capacity stack |
| `BufferBuilder.m_5751_` vertex-element sampled leaves | 385 | 357 | Mild count drop; no demonstrated elapsed-time or net FPS saving |
| Modded `ClientLevel.handler$bpg000$onHasChunk` sampled leaves | 423 | 534 | Increased sampled leaf; mod ownership and semantics not established |
| `AABB.equals` in particle-render stack | 0 | 0 | Previous frustum fast-path elimination remains active |

The pointer-at-`ensureCapacity`-RETURN injection was removed, and #1025 successfully applied the new mixin. That verified code-path simplification **did not materially remove the sampled expensive capacity path**. JFR cannot tell whether `ensureCapacity` performed actual native resize/copy; do not equate an `ensureCapacity` sampled leaf with a reallocation. The modulo-to-index-comparison tweak also has no independently demonstrated time benefit. The nominally similar CPU workload and larger GPU time do not license attributing the entire -6.4% FPS difference to the patch.

## Disposition and actionable continuation

**#1025 is implemented, CI-qualified and RX-activated, but the FPS benefit is not demonstrated. Screening is unfavorable**, so do not promote it as a proven performance optimization or enable further similar BufferBuilder rewrites on this evidence. Retain as **ineffective (unfavorable cross-build screening; causality unproven)** in the ledger, not a formally isolated regression. No new JAR/test needed simply to reconfirm the historical baseline.

For the next incremental performance pass, first inspect actual Forge/vanilla `BufferBuilder.ensureCapacity` and the `Tesselator.end → storeRenderedBuffer` repeated-work mechanism (native growth event/bytes versus routine checks, index finalization, builder lifetime). A *single bounded* distinguishing resize counter/test is justified only if that will change the implementation decision. Separately, investigate the owner and correctness constraints of `ClientLevel.handler$bpg000$onHasChunk` via the real modded particle light-color call chain; do not suppress chunk checks or lighting globally. Texture ticker non-upload CPU (7.349 ms/tick) remains a large independent candidate. Preserve Forge/Immersive Portals and pixel/particle visual behavior. No default promotions, memory-budget increases or additional GPU mechanisms based only on these results.
