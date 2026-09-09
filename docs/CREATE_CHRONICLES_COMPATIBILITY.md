# Create Chronicles compatibility baseline

This document tracks Phase 4 compatibility evidence for the Forge 1.20.1 port. It is intentionally conservative: a mod being disabled in a known-good instance is **not** by itself evidence that the mod is incompatible.

## Target environment

- Minecraft 1.20.1
- Forge 47.3.0
- Arch Linux
- AMD Radeon RX 6900 XT (RADV)
- Prism Launcher
- Create Chronicles: Bosses and Beyond
- VulkanMod branch: `forge-1.20.1`

## Current Phase 4 retest artifact

Use the latest green source artifact from CI #289:

- source commit: `9917cacf69848f492c72ac06f2eb591633937a58`
- artifact: `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.289-g9917cacf-all.jar`
- required Forge setting: `config/fml.toml` -> `earlyWindowControl = false`

Later branch HEAD commits may be documentation-only; the artifact above is the latest CI-verified runtime source at the start of Phase 4.

## Historical full-pack evidence

A pre-Phase-4 full Create Chronicles run on build 226 established a useful historical baseline:

- the full modpack entered a world with Vulkan active;
- gameplay remained stable for several minutes;
- a Create water wheel rendered and spun correctly, exercising Create/Flywheel rendering;
- the game exited normally and the integrated server saved cleanly;
- no `VK_ERROR_DEVICE_LOST`, RADV command-stream rejection, JVM crash, system OOM, or VulkanMod memory-safety abort was observed in that successful run.

This evidence is valuable but does **not** close the current Phase 4 launch/contraption gates because substantial renderer-correctness work landed after build 226. Build 289 must be retested in the actual pack.

An uploaded full-pack log from the same development period also recorded Vulkan activation on the RX 6900 XT and confirms the renderer-mod composition used by the working Vulkan instance.

## Renderer-replacement baseline

| Component | State in known-good Vulkan instance | Evidence status | Phase 4 interpretation |
| --- | --- | --- | --- |
| VulkanMod | Enabled | Vulkan renderer reported active on RX 6900 XT | Required |
| Create 0.5.1.j | Enabled | Loaded in full pack | Required target workload |
| Flywheel 0.6.11-13 | Enabled | Loaded; CI startup gate also exists | Supported baseline; needs current gameplay retest |
| Crash Assistant 1.9.7 | Enabled | Loaded; CI startup gate exists | Supported baseline |
| Embeddium 0.3.31 | Disabled | Instance mod list | Disabled for the working baseline; incompatibility not yet individually proven |
| Oculus 1.8.0 | Disabled | Instance mod list | Disabled for the working baseline; shaderpack compatibility is out of initial Phase 4 scope |
| Oculus-Flywheel-Compat 2.0.3 | Disabled | Instance mod list | Disabled because Oculus is absent; no independent incompatibility claim |
| Rubidium Extra 0.5.4.4 | Disabled | Instance mod list | Disabled for the working baseline; independent incompatibility not yet proven |

Do not re-enable renderer replacements in bulk. If Phase 4 later tests them, add one renderer-changing component at a time so any failure mechanism is attributable.

## Mandatory current-artifact test sequence

Run these against build 289 in the real Create Chronicles instance, keeping the known-good renderer-replacement set disabled initially.

1. Launch to the title screen and confirm the log contains `Vulkan renderer active:` for the RX 6900 XT.
2. Enter the normal test world and inspect terrain, entities, GUI, particles and translucent blocks/liquids during ordinary movement.
3. Exercise a visible Create/Flywheel contraption (water wheel is sufficient for the first pass; a moving contraption is better for follow-up coverage).
4. Press `F3+T` and wait for resource reload to finish; verify rendering remains correct.
5. Exit to the title screen, re-enter the same world, and continue briefly.
6. Exit normally.

For the first retest, preserve the existing resource-pack workload if practical; it previously exercised a very large atlas and therefore remains a useful compatibility stress case.

## Evidence to retain after each run

Keep:

- `logs/latest.log` (and `debug.log` if a failure is not explained by `latest.log`);
- any crash report;
- screenshots of visible rendering defects;
- exact enabled/disabled state of renderer-changing mods;
- whether launch, world entry, Create/Flywheel rendering, `F3+T`, world re-entry and clean exit passed.

## Gate status at Phase 4 start

- Flywheel CI startup: **PASS**
- Crash Assistant CI startup: **PASS**
- current build-289 full-pack launch: **PENDING USER-MACHINE RETEST**
- current Create/Flywheel gameplay rendering: **PENDING USER-MACHINE RETEST**
- world enter/leave/re-enter + resource reload: **PENDING**
- representative particles/translucency/entities/GUI: **PENDING**
- minimized incompatible renderer-replacement set: **PARTIAL** — known-good disabled set is recorded, but individual incompatibility is not yet proven
- final concise compatibility/known-limitations matrix: **IN PROGRESS** — this file is the evidence ledger and will become the final matrix as gates close
