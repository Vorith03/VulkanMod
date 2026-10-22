# VulkanMod — Continue Owner-Evidenced GPU Adoption Repairs

Continue autonomous engineering in https://github.com/Vorith03/VulkanMod on `forge-1.20.1`. Preserve Minecraft 1.20.1, Forge 47.3.0, Java 17, compatibility, all fail-closed fallbacks and default-off pilots. Follow `AGENTS.md` §3A, current `ROADMAP.md`, `AGENT_STATUS.md`, optimization ledger, owner RX report, relevant texture/particle contracts, and live Git/CI. Work through multiple coherent code/test/CI cycles without waiting for a repeated "continue"; end with another complete rolling handoff.

## Current checkpoint and active validation

Historical last FULL qualified executable: `95221419486931c295b83b1efe1c98ef3df4eef7`, CI #1088 SUCCESS, artifact 12383727666, JAR SHA256 `09f491f063d5a3b0def066e029ae1805acaeaaf7fe9b4cd5eb8f2ec292bf01e8`. This does NOT qualify later code. Documentation checkpoint before the current slice was `b4af7071e38932e0e3511fb63bb9feef0ec40159`.

Particle fix on branch: `550a510c6f2ed833eb80e8e03a881ab708faf270` (`fix: window particle compute storage descriptors within Vulkan range`). Full CI **#1089 / run 39713898920** was IN PROGRESS at the previous session's last inspection, already through compilation/startup and entering native tests. **FIRST inspect this run and its exact failure/success; do not claim it passed without checking.** A documentation-only successor commit may follow. Avoid force pushes, PR#12 profiler overlap, and stale assumptions about branch HEAD.

## Owner benchmark baseline (do not re-request unchanged evidence)

Owner #1088 RX 6900XT/RADV / Ryzen 5800X / Create Chronicles ~302 mods / 2552×1374 / D32/S12, 180s automated fixed spectator scene:
- partial GPU UUID `20c49c53-5e7c-468d-ab90-817dddc1daa6`: 58.60 FPS, final150s 62.03.
- integrated19 UUID `221fd7a2-9d5b-4fe4-958b-0e13dac88d87`: 71.34 FPS, final150s 75.71.
- +21.7% one-trial descriptive comparison only. No same-JAR all19-false CPU control or owner visual pairs.
- Terrain mesher and CPU bypass active, 727 GPU-published sections, REPLACE/APPEND indirect recorded; 19 recoveries and 10 output reservation failures. Do not reopen settled terrain work absent new correctness evidence.
- Texture: 1510 admissions selected, 1505 resident; **0 / 2,879,091** GPU interpolation; 2,810,697 DISPATCH_FAILED fallbacks, 68,394 NO_UPLOAD_BATCH.
- Particles: **0 / 126,249,312** GPU quads; 15,426 capacity-rejected batches, no unavailable/descriptor counters.

## Current particle slice: exact scope

`550a510c` uses an aligned `VkDescriptorBufferInfo.offset` window into the *same* frame-slot particle vertex buffer (no new allocator). Compact metadata/vertices remain uploaded at the original absolute buffer offsets and drawn from the original absolute vertex offset; shader push offsets become descriptor-window relative, while host-write and compute/vertex barriers remain absolute. Descriptor cache keys on buffer plus window/range; changing windows allocates a distinct descriptor and fence-retires the old set rather than mutating a recorded set. Existing 64 live/pending descriptor bound, original CPU fallback and buffer resize retirement remain. New counters distinguish workgroup-range versus span rejection and count nonzero-window dispatches.

The native `ParticleBillboardSmokeTest` first pass now appends a 32768-byte prior vertex span and clamps the *fixture-only* storage range to 32768; accepted GPU work therefore requires a nonzero descriptor window and still runs the production uploader/pixel-oracle path. **Do not assume this test passed until #1089 reports success.** Inspect native particle oracle and any validation/synchronization errors. If failure, fix smallest evidenced issue, requalify. On success, this demonstrates production-style small-batch GPU path under an artificial range; owner RX re-adoption still requires a changed owner test.

## NEXT PRIORITY: isolate and repair the remaining texture-zero-adoption path

Inspect `SpriteAnimationState.tryGpuInterpolation` -> `GpuAnimatedTextureResidency.interpolateToAtlas` -> `GpuTextureInterpolationCompute.createBinding` / `Binding.dispatch`. Present `DISPATCH_FAILED` folds together:
- resident guard and one-shot `interpolationBindingAttempted` latch;
- source-offset alignment, source/scratch/mip table or descriptor allocation rejection;
- dispatch preflight (no upload batch, invalid mip/atlas/source bounds);
- multi-job staging rejection / fallback to fused/legacy.

The #1088 attribution records neither lifetime binding failures nor precise rejection reasons, so absence of `gpu_multi_eligible` alone does NOT identify source failure. Compare relevant #1028 successful interpolation (106,483) to #1088 implementation and all 19 flags, but do not assume source regression. Existing #1088 captures' merged `DISPATCH_FAILED` is insufficient to choose an algorithmic fix. Add bounded, low-overhead lifetime rejection reasons **only where discriminating evidence is missing**; surface them in existing profiler summary (no new profiling system). Ensure repeated failures after a latched binding rejection do not obscure the first root cause. Obtain independent test reproducer and implement minimal safe production correction. Prove exact mip atlas pixels, CPU bypass on accepted work, generation mutation, reload, range/capacity/unavailable paths and CPU fallback. Prefer existing scratch/source/admission infrastructure. Only then change owner-facing flags/test package.

## Qualification and handoff

After correct texture plus particle integration: focused contracts/native compute, transformed real bridge/atlas/draw pixels, synchronization/fence/reentry/reload/failure oracles, Forge 47.3.0 Java17 startup/package, public Create Chronicles/Immersive Portals compatibility and required full CI. One qualified executable must have SHA, run ID, artifact, SHA256, real tested GPU acceptance/CPU bypass, skipped gates recorded. Preserve CPU control and default-off. Do not claim owner verification, causal speedup, release readiness or exact owner adoption from synthetic CI.

Only after internal qualification, ask for one targeted RX owner visual/adoption check in same saved world then matched all19-on vs all19-off 180s benchmark with same new JAR/framebuffer. No repeat of prior raw logs without new question.

Update `AGENT_STATUS.md`, `ROADMAP.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`, affected contracts and `docs/NEXT_SESSION_PROMPT.md` when evidence materially changes. Finish the session with a NEW full copyable continuation prompt that also mandates its successor preserve this rolling handoff.