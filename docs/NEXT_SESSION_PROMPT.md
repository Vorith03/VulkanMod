# Owner RX 6900 XT: resolve integrated GPU adoption failures from real benchmark evidence

Continue https://github.com/Vorith03/VulkanMod on `forge-1.20.1`.
Preserve Minecraft 1.20.1 / Forge 47.3.0 / Java 17, experimental default-off,
user's Create Chronicles pack, and no unnecessary architecture. Follow current
AGENTS.md §3A, ROADMAP, AGENT_STATUS, affected ownership contracts, CI, latest Git
delta and [owner benchmark report](OWNER_RX_6900XT_GPU_BENCHMARK_2026-10-21.md).
Follow precise validation and Git handoff. Do NOT restart broad GPU roadmap.

## Live executable and qualification
- Last internally qualified executable `95221419486931c295b83b1efe1c98ef3df4eef7`,
  full #1088 run39680381869 successful, JAR artifact12383727666 SHA256
  `09f491f063d5a3b0def066e029ae1805acaeaaf7fe9b4cd5eb8f2ec292bf01e8`.
- 19 activation properties in scripts/ci/integrated-gpu-flags.txt;
  no source commits since this executable as of owner evidence.
- New **owner RX6900XT** 180-second captures both auto-completed on same #1088
  executable, fixed framebuffer2552x1374, D32/S12, VSync off, cap260,
  `VulkanMod_Benchmark` yaw-90/pitch30 xyz0/192/0:
  - partial GPU run UUID `20c49c53-5e7c-468d-ab90-817dddc1daa6`,
    10545 frames=58.60fps, final150s62.03fps; mesher/bypass flags false.
  - integrated19 UUID `221fd7a2-9d5b-4fe4-958b-0e13dac88d87`,
    12835 frames=71.34fps, final150s75.71fps; +21.7% descriptive.
  - Both owner latest/debug/benchmark/JFR uploaded in Project conversation;
    Git stores derived metrics, not private raw logs. They are not a true
    CPU allfalse baseline, multi-trial study, or visual equivalence proof.
- Integrated live GPU terrain owner witnesses: CPU bypass/mesher/publish,
  GPU-generated REPLACE/APPEND draw recorded, 727 published CPU-bypassed
  sections, 19 CPU recoveries, staging full and unsupported models; keep
  fail-closed semantics and all exact visual/portal contracts.

## Concrete unresolved GPU displacement defects: bounded engineering mission
1. GPU interpolation: admission active, 1510 selected/1505 resident,
   but `standard_interpolation_gpu_updates=0/2879091`,
   `interpolation_fallback_dispatch_failed=2810697`, no GPU dispatch.
   Trace SpriteAnimationState -> GpuAnimatedTextureResidency ->
   GpuTextureInterpolationCompute's first null/fail decision.
   Determine exact rejection without guessing; bounded reason telemetry if
   existing logs and tests cannot distinguish. Preserve CPU fallback,
   exact pixel/sprite/mip/timing and source mutation semantics.
2. GPU particle billboards: 126249312 eligible quads yet gpu_quads0,
   compute_batches0, capacity_rejected_batches15426. Diagnose which guard in
   ParticleBillboardCompute.expand is refusing batches (group count vs
   storage span/range and buffer used offset); do not relax Vulkan bounds
   or remove CPU fallback. Reuse existing batching and scratch contracts.
3. Terrain already engaged, but output reservation fails 10 and 19 CPU recovery
   requests; investigate only when relevant to correctness or after the two
   full non-adoption blockers. No user visual defect has been supplied.

Complete bounded fixes if grounded, prefer established primitives, add regression
witnesses, and qualify via affected native/oracle/CI tests. No speculative
performance claim or additional test request until a changed executable has
internally passed. Check open PR#12 before any recorder overlap. No default
promotion, force pushes or unrelated implementation.

## Owner follow-up once internally qualified
Provide exact new JAR/link/SHA/flags and a narrow same-save visual CPU-vs-GPU
comparison exercising opaque, glass/slabs, animated textures, eligible smoke,
camera motion/dirty/reload/reentry, portal only if available. Obtain actual
GPU adoption counts and GPU/CPU rendered equivalence evidence. Then use
matched 180-second runs on the **same new JAR** with all19 true vs all19
false, stable scene/framebuffer/world/modpack, repeat if variance merits.
Assess CPU ticks, render time, GPU graphics separately from upload batches,
frame pacing, CPU work removed, fallbacks, correctness and gameplay.
Do not ask for previously supplied logs again without new code or question.

**Rolling durability requirement**: At every successor boundary, update
AGENT_STATUS.md, ROADMAP and benchmark witness/optimization ledger,
plus this file with current Git/CI/runtime evidence, exact outstanding defects
and next action. End with a NEW complete copyable continuation prompt for
the next session, requiring it to do the same. No stale `owner unmeasured`
language may be treated as current.
