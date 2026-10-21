# Owner RX 6900 XT integrated GPU qualification — continue from first evidence

Continue https://github.com/Vorith03/VulkanMod on forge-1.20.1.
Preserve Minecraft 1.20.1, Forge 47.3.0, Java 17, default-off GPU experiments.
Follow AGENTS.md §3A live Git/CI/delta procedure; read current AGENT_STATUS.md,
ROADMAP.md, GPU_FIRST_OWNERSHIP completion checklist, this file,
docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md, profiler instructions,
optimization ledger and relevant ownership contracts. Do not redo settled audits.
No broad implementation without a concrete owner-capture defect. No agents
without authorization; no force push, unrelated release, new recorder, or default
promotion. Inspect still-open profiler PR #12 before any overlapping work.

## Verified October 21 package

- Qualifying executable commit: 95221419486931c295b83b1efe1c98ef3df4eef7.
- Full build #1088 / GitHub Actions run 39680381869: completed SUCCESS attempt 1.
- JAR artifact 12383727666:
  VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1088-g95221419-all.jar.
- JAR bytes 11,098,113, SHA256:
  09f491f063d5a3b0def066e029ae1805acaeaaf7fe9b4cd5eb8f2ec292bf01e8.
- CI capture artifact 12385056493. All public CI phases/gates passed, private
  fixtures skipped. The subsequent branch commits are documentation-only.
- Current flags: 19 lines in scripts/ci/integrated-gpu-flags.txt; use all true
  for GPU and all false for same-JAR CPU control. In both profiles use
  -Dvulkanmod.performanceProfiler=true,
  -Dvulkanmod.performanceProfiler.gpuTimestamps=true,
  -Dvulkanmod.debugGpuGeneratedWorldDraw=true.
- Required config: earlyWindowControl=false, regionBatching=true; Vulkan device
  multiDrawIndirect and drawIndirectFirstInstance support; no separate legacy
  indirectDraw requirement.

## Current owner-test status and required next action

Owner platform: Arch Linux, KDE Wayland, Ryzen 7 5800X, RX 6900 XT 16 GiB,
5120x1440 monitor but approximately half-sized Minecraft window. **Actual
framebuffer dimensions UNKNOWN** until startup/profiler logs, specifically
initial_framebuffer_px and framebuffer_first/last/ranges. **No owner-machine
run, owner UUID, screenshot comparison or FPS measured yet.** Historical #1028
UUID e05f2b31 is NOT the baseline for #1088. The 10–25% suggestion was speculative.

Await first CPU allfalse and GPU alltrue SAME-JAR launch, matched same-save
functional screenshot, latest.log, debug.log and manual vulkanmod-performance.log.
Keep identical modpack/settings/driver/resource packs/window dimensions, render/
simulation distance, FOV, VSync/FPS cap, pose/weather/time, and copy the clean
closed save into each profile. Exercise terrain with normal/mixed geometry,
animated textures, eligible smoke, camera motion, dirty rebuild, F3+T reload,
save/reentry; portal only if actual supported mod and scene. Stop on crashes,
wrong pixels, device loss, validation hazards; preserve CPU recovery evidence.
Verify GPU command witness, feature admission/fallback, CPU bypass and complete
fallback, NOT just flags or allocated GPU buffers.

Only after functional/visual correctness, guide ONE matched stationary comparison
using existing automated benchmark on the same world/pose: minimum settle 60 s,
10-second stable initial population, 180-second capture; controller auto-save/quit.
Use the exact options in witness package; avoid screens/movement and note auto
abort conditions. Collect both UUID benchmark logs, neighboring .cpu-graphics-*.jfr,
full latest/debug, configuration and screenshot pairs. Separate GPU-main frame
timings from upload-batch, CPU tick, frame, and GPU command timings. Interpret
frame avg/p50/p95/p99, stutter, CPU displacement, GPU work, sync/upload cost,
adoption numerator/denominator and reasons. Keep warmup separate, request extra
captures only for evidenced ambiguity. Avoid arbitrary mod or renderer expansion.

Do not silently promote to release or claim RX improvement without evidence.
Qualify any bounded code fix and exact changed executable; no extra owner test
until source/log analysis cannot resolve the question.

**Durable rolling handoff obligation:** Before ending every successor work session,
update AGENT_STATUS.md, the relevant witness/benchmark document and optimization
ledger, and docs/NEXT_SESSION_PROMPT.md with actual evidence, framebuffer,
exact JAR/commit/capture UUIDs, verdicts, uncertainty and next specific action.
Finish with a NEW complete copyable continuation prompt that explicitly requires
its own successor to update the repository handoff and create another NEW prompt.
