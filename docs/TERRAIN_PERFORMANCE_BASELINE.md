# Terrain performance baseline

This is the minimum repeatable benchmark contract for the Forge 1.20.1 terrain-performance work. It exists to keep optimization claims comparable without blocking renderer development on a large benchmark campaign.

## Benchmark policy

- Primary target: the user's actual Create Chronicles instance on the RX 6900 XT.
- Primary resolution: 2560x1440 windowed.
- Use the same Minecraft/Forge version, mod set, resource packs, render distance, simulation distance, graphics options, FOV, window size and frame-cap/vsync state for every A/B comparison.
- Keep Embeddium, Oculus and other renderer replacements in the same enabled/disabled state between comparison runs.
- Record exact settings with each result rather than silently changing them to improve a score.
- Do not call a performance change an improvement without a comparable before/after run. Correctness fixes and architectural groundwork may land before a full comparison baseline exists.

## A — stationary terrain frame case

Use the normal test world. Pick one camera position and orientation that shows a representative mix of opaque terrain, cutout foliage and distant chunks. Record the coordinates/yaw/pitch with the first baseline and reuse them thereafter.

After world entry and chunk settling:

1. remain stationary for at least 30 seconds;
2. capture average FPS plus hitch-sensitive/frame-time evidence available from the same run;
3. record the VulkanMod F3 terrain counters, especially region sections/calls, command rebuild bytes, mesh upload bytes and reuse/new/grow counts;
4. note visible correctness problems separately from performance.

## B — terrain traversal/chunk-churn case

Use a copy of the same test world so generation and exploration do not alter later comparisons.

1. begin from a recorded start coordinate and facing;
2. travel a fixed straight route of approximately 1024 blocks through representative terrain at the same movement mode/speed each run (spectator flight is acceptable and preferred for repeatability);
3. do not pause to inspect scenery during the timed traversal;
4. record average FPS, hitch/frame-time evidence, chunk build queue/build/handoff timings, upload readiness/bytes and region allocation reuse/new/grow counters;
5. note any missing/stale/corrupt terrain.

The exact route coordinates become fixed when the first baseline is recorded. Subsequent runs must reuse them.

## C — Create-heavy rendering case

Choose a stable camera overlooking a representative Create/Flywheel setup with at least one continuously moving contraption (the existing water-wheel test location is acceptable if convenient).

1. record camera coordinates/yaw/pitch;
2. run the contraption continuously for at least 30 seconds;
3. record the same FPS/frame-time evidence as case A;
4. confirm moving Create/Flywheel geometry remains visually correct.

## OpenGL comparison

The OpenGL control is the same Forge/modpack instance and benchmark settings with VulkanMod disabled and the known renderer-replacement set kept unchanged. Do not introduce Embeddium solely for the OpenGL comparison unless a separate explicitly labeled comparison is desired.

## Vulkan comparison

Use the exact VulkanMod build/commit under test, `earlyWindowControl = false`, and the same test settings/routes as the OpenGL control.

## Acceptance rule

A terrain optimization is kept when it satisfies all of the following relevant conditions:

- no new rendering/lifecycle corruption in CI or the RX 6900 XT test;
- no unbounded buffer/resource growth during the test;
- the metric it targets improves in an apples-to-apples run (for example fewer terrain submissions, less allocation churn, lower upload cost, better traversal frame time, or fewer hitches);
- regressions in another important metric are understood and acceptable.

An architectural change may remain as groundwork before complete OpenGL/Vulkan baseline numbers exist, but it must not be advertised as a performance win until this comparison is performed.
