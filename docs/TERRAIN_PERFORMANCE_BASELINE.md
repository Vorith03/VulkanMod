# Terrain performance baseline

This is the minimum repeatable benchmark contract for the Forge 1.20.1 terrain-performance work. It exists to keep optimization claims comparable without blocking renderer development on a large benchmark campaign.

## Benchmark policy

- Primary target: the user's actual Create Chronicles instance on the RX 6900 XT.
- Primary resolution: 2560x1440 windowed.
- Use the same Minecraft/Forge version, mod set, resource packs, render distance, simulation distance, graphics options, FOV, window size and frame-cap/vsync state for every A/B comparison.
- Keep Embeddium, Oculus and other renderer replacements in the same enabled/disabled state between comparison runs.
- Record exact settings with each result rather than silently changing them to improve a score.
- Do not call a performance change an improvement without a comparable before/after run. Correctness fixes and architectural groundwork may land before a full comparison baseline exists.

## Fixed benchmark world and graphics profile

The terrain A/B cases now have a fixed definition independent of any one VulkanMod build. This closes the procedure-definition gate; it does **not** supply baseline numbers.

### World

Create a dedicated Create Chronicles world named `VulkanMod Benchmark` with:

- seed: `2026092601`;
- default Overworld world-generation settings for the installed Create Chronicles version;
- Creative mode with commands enabled;
- difficulty and gamerules left at the pack defaults;
- no manually placed blocks or machines in the terrain A/B route.

Keep one pristine copy after first generation/chunk settling. Clone that pristine directory for each OpenGL/Vulkan traversal run so exploration, entities and chunk-save state do not drift between comparisons.

### Graphics/options profile

Use this profile for the fixed terrain cases:

| Option | Fixed value |
| --- | --- |
| Window mode | Windowed |
| Client area | 2560x1440 |
| VSync | Off |
| Max framerate | Unlimited |
| FOV | 70 |
| Graphics | Fancy |
| Render distance | 16 chunks |
| Simulation distance | 12 chunks |
| Smooth lighting | Maximum |
| Biome blend | 5x5 |
| Mipmap levels | 4 |
| Clouds | Fancy |
| Particles | All |
| Entity shadows | On |
| Entity distance | 100% |
| GUI scale | 3 |
| Shaderpack | None |
| Renderer replacements | Embeddium/Oculus/Rubidium Extra/Oculus-Flywheel-Compat disabled |
| Resource packs | The same selected PureBDcraft base + Create Chronicles PureBDcraft pack used by the target instance, in the same order |

Do not alter mod-specific visual/performance settings between the A and B sides of a comparison. If a modpack update changes those settings or either selected resource pack, record the change and establish a new baseline instead of mixing the runs.

### Fixed stationary position

After entering the pristine benchmark world:

```text
/gamemode spectator
/tp @s 0 192 0 -90 30
```

Do not adjust spectator flight speed. Wait 60 seconds without moving the camera before starting the stationary capture. The fixed elevated/downward view is intentional: it avoids collision/terrain-height dependence while keeping a broad opaque/cutout/distant-chunk workload in view.

### Fixed traversal route

Start from:

```text
/gamemode spectator
/tp @s -512 192 0 -90 25
```

Do not adjust spectator flight speed. Hold **Sprint + Forward** continuously and keep the camera unchanged. Start the measured traversal as the player begins moving from X=-512 and end it at X=+512. This is a 1024-block straight eastbound route at fixed Y=192. If input is interrupted, discard that run rather than stitching samples together.

The route deliberately prioritizes repeatability and chunk churn over sightseeing. If the fixed seed later proves pathological because a modpack/worldgen update substantially changes the route, revise the seed/route here and establish a new baseline series; never silently substitute coordinates.

## A — stationary terrain frame case

Use the pristine fixed benchmark world/profile and the stationary position above.

After world entry and the 60-second settle period:

1. remain stationary for at least 30 measured seconds;
2. capture average FPS plus hitch-sensitive/frame-time evidence available from the same run;
3. record the VulkanMod F3 terrain counters, especially region sections/calls, command rebuild bytes, mesh upload bytes and reuse/new/grow counts;
4. note visible correctness problems separately from performance.

## B — terrain traversal/chunk-churn case

Use a fresh copy of the same pristine benchmark world for every comparison run.

1. use the fixed `-512 -> +512` spectator route above;
2. do not pause, turn, change altitude or change spectator flight speed during the timed traversal;
3. record average FPS, hitch/frame-time evidence, chunk build queue/build/handoff timings, upload readiness/bytes and region allocation reuse/new/grow counters;
4. note any missing/stale/corrupt terrain;
5. discard and repeat a run if movement/input is interrupted.

## C — Create-heavy rendering case

This remains a gameplay/mod-compatibility workload rather than part of the synthetic fixed terrain world. Choose a stable camera overlooking the established Create/Flywheel test setup with at least one continuously moving contraption (the existing water-wheel test location is acceptable if convenient).

1. record camera coordinates/yaw/pitch with the first numeric baseline and keep them fixed for that baseline series;
2. run the contraption continuously for at least 30 seconds;
3. record the same FPS/frame-time evidence as case A;
4. confirm moving Create/Flywheel geometry remains visually correct.

If the Create-heavy world/setup changes materially, start a new labeled benchmark series rather than comparing it directly to older numbers.

## OpenGL comparison

The OpenGL control is the same Forge/modpack instance and benchmark settings with VulkanMod disabled and the known renderer-replacement set kept unchanged. Do not introduce Embeddium solely for the OpenGL comparison unless a separate explicitly labeled comparison is desired.

## Vulkan comparison

Use the exact VulkanMod build/commit under test, `earlyWindowControl = false`, and the same test settings/routes as the OpenGL control.

## Result record

Every measured result should include:

- VulkanMod build/commit, or `OpenGL control`;
- Create Chronicles/modpack version;
- Java version and JVM memory arguments;
- selected resource-pack filenames/versions;
- whether experimental GPU-terrain flags were enabled and their exact values;
- A, B or C case identifier;
- average FPS;
- at least one hitch-sensitive/frame-time metric from the same capture;
- run duration;
- any visible correctness issue;
- relevant VulkanMod terrain counters for Vulkan runs.

A median of three valid runs is preferred once numeric baselines are being recorded. A single run may be used for diagnosis but must be labeled as such.

## Acceptance rule

A terrain optimization is kept when it satisfies all of the following relevant conditions:

- no new rendering/lifecycle corruption in CI or the RX 6900 XT test;
- no unbounded buffer/resource growth during the test;
- the metric it targets improves in an apples-to-apples run (for example fewer terrain submissions, less allocation churn, lower upload cost, better traversal frame time, or fewer hitches);
- regressions in another important metric are understood and acceptable.

An architectural change may remain as groundwork before complete OpenGL/Vulkan baseline numbers exist, but it must not be advertised as a performance win until this comparison is performed.