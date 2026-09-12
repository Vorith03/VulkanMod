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

Verified on 2026-09-12: [CI #306](https://github.com/Vorith03/VulkanMod/actions/runs/34451137360) is green at source `18e9247e5c85fe2151424f7f1b29fd4fbe6fc0b7`.

- Download the `VulkanMod-Forge-build-306` artifact and install its `-all.jar`.
- Keep `config/fml.toml` -> `earlyWindowControl = false`.
- Disable PickupNotifier 8.0.0 for this retest; retain the existing renderer-replacement baseline below.
- Build #306 has the same runtime source as #304; intervening changes are documentation-only. An already installed build #304 is sufficient for this test.

### Current known limitations

| Path | Evidence | Required action / remaining gate |
| --- | --- | --- |
| PickupNotifier 8.0.0 transparency framebuffer | Build #303 launcher stack: `GL30C.glGetInteger` -> `TransparencyBuffer.prepareExtraFramebuffer`; LWJGL aborted because the Vulkan window has no OpenGL context | Disable PickupNotifier for the baseline. No compatible configuration or replacement implementation has been verified. |
| Full-pack resource reload | Build #303 completed reload and allocator purge, but later aborted in PickupNotifier; #304 without PickupNotifier hit the memory guard before reload apply | Repeat reload and sustained gameplay with sufficient host headroom; not yet a complete pass. |
| Heavy 16K atlas workload | #304 tripped the unchanged RSS guard at 12294 MiB RSS / 8144 MiB MemAvailable | Close memory-heavy applications before launch; aim for the previous approximately 13 GiB available during reload. This is a comparison target, not a guarantee. Preserve safety limits and resource-pack settings. |
| Animated atlas images | #303/#304 retirement preserved approximately 195 MiB of animated block-atlas CPU images | Verify animated textures continue after successful reload. |
| Create/Flywheel gameplay | Historical #226 water wheel rendered; #306 startup smoke passes | Current full-pack contraption visual test remains pending. |

These findings are recorded in the dated #303/#304 runtime sections of `AGENT_STATUS.md`. CI startup coverage does not close full-pack gameplay gates.

## Build 289 full-pack launch — 2026-09-09

The first Phase 4 user-machine run establishes the current full-pack launch gate:

- Prism loaded the target ~300-mod Create Chronicles instance on Forge 47.3.0 / Java 17;
- Forge early splash was disabled and VulkanMod created its `GLFW_NO_API` window;
- terrain region batching initialized;
- the client completed its initial resource load and logged `Vulkan renderer active: AMD Radeon RX 6900 XT (RADV NAVI21)`;
- the normal test world reached in-game and the integrated server began normal save/pause activity.

Therefore the Phase 4 gate **current distributable launches the target Create Chronicles instance with Vulkan active** is **PASS** for build 289.

The run did not reach the remaining gameplay checks. Immediately after first world entry, VulkanMod's existing host-memory safety guard deliberately stopped further texture staging when Linux `MemAvailable` fell just below the configured 4096 MiB no-swap floor. The fatal snapshot reported approximately:

- process RSS: 11823 MiB;
- live NativeImages: 1436 MiB;
- estimated live Vulkan images: 1989 MiB;
- VMA live image allocations: 2257 MiB;
- system `MemAvailable`: 4080 MiB;
- swap free: 0 MiB;
- AMD VRAM used: 9188 / 16368 MiB.

This is **not currently classified as a build-289 VulkanMod memory regression**. The same machine had materially less headroom before this run than during the earlier successful heavy-pack baseline:

| Measurement | Earlier heavy-pack run | Build 289 Phase 4 run | Difference |
| --- | ---: | ---: | ---: |
| RAM available before Minecraft launch | 22697 MiB | 16740 MiB | -5957 MiB |
| 16K atlas, process RSS before allocation | 11982 MiB | 11230 MiB | -752 MiB |
| 16K atlas, live NativeImages before allocation | 1830 MiB | 1414 MiB | -416 MiB |
| 16K atlas, estimated Vulkan images before allocation | 543 MiB | 71 MiB | -472 MiB |
| 16K atlas, system available before allocation | 10426 MiB | 5694 MiB | -4732 MiB |
| 16K atlas, AMD VRAM already used before allocation | 2433 MiB | 6785 MiB | +4352 MiB |

Build 289 was therefore using less VulkanMod-tracked memory at the comparable 16K-atlas point, while the host started with roughly 6 GiB less available RAM and roughly 4.3 GiB more VRAM already occupied. The current evidence points to external host/GPU memory pressure rather than a newly introduced VulkanMod retention defect.

Do **not** lower the 4096 MiB hard system-memory safety floor merely to make this test pass. This machine has previously suffered system-wide OOMs under the same large resource-pack workload and has no swap. For the next Phase 4 run, first restore approximately the earlier launch headroom (close memory/GPU-heavy applications, or otherwise provide swap/headroom), then rerun the same artifact and test sequence.

## Historical full-pack evidence

A pre-Phase-4 full Create Chronicles run on build 226 established a useful historical baseline:

- the full modpack entered a world with Vulkan active;
- gameplay remained stable for several minutes;
- a Create water wheel rendered and spun correctly, exercising Create/Flywheel rendering;
- the game exited normally and the integrated server saved cleanly;
- no `VK_ERROR_DEVICE_LOST`, RADV command-stream rejection, JVM crash, system OOM, or VulkanMod memory-safety abort was observed in that successful run.

This evidence remains useful context, but the current Phase 4 gameplay gates require build 289 (or a later green artifact) to survive the same paths after the substantial renderer-correctness work that followed build 226.

## Renderer-replacement baseline

| Component | State in known-good Vulkan instance | Evidence status | Phase 4 interpretation |
| --- | --- | --- | --- |
| VulkanMod | Enabled | Build 289 Vulkan renderer active on RX 6900 XT | Required; current full-pack launch PASS |
| Create 0.5.1.j | Enabled | Loaded in full pack | Required target workload |
| Flywheel 0.6.11-13 | Enabled | Loaded; CI startup gate also exists | Supported baseline; needs current gameplay retest |
| Crash Assistant 1.9.7 | Enabled | Loaded; CI startup gate exists | Supported baseline |
| Embeddium 0.3.31 | Disabled | Instance mod list | Disabled for the working baseline; incompatibility not yet individually proven |
| Oculus 1.8.0 | Disabled | Instance mod list | Disabled for the working baseline; shaderpack compatibility is out of initial Phase 4 scope |
| Oculus-Flywheel-Compat 2.0.3 | Disabled | Instance mod list | Disabled because Oculus is absent; no independent incompatibility claim |
| Rubidium Extra 0.5.4.4 | Disabled | Instance mod list | Disabled for the working baseline; independent incompatibility not yet proven |

Do not re-enable renderer replacements in bulk. If Phase 4 later tests them, add one renderer-changing component at a time so any failure mechanism is attributable.

## Mandatory current-artifact test sequence

Run these against build #306 (or the runtime-equivalent #304) in the real Create Chronicles instance. Disable PickupNotifier 8.0.0 and keep the known-good renderer-replacement set disabled initially. Record `free -m` before launch; do not use diagnostic memory-safety overrides.

1. Launch to the title screen and confirm the log contains `Vulkan renderer active:` for the RX 6900 XT. **PASS on 2026-09-09.**
2. Enter the normal test world and inspect terrain, entities, GUI, particles and translucent blocks/liquids during ordinary movement. **Current representative visual pass still pending.**
3. Exercise a visible Create/Flywheel contraption (water wheel is sufficient for the first pass; a moving contraption is better for follow-up coverage).
4. Press `F3+T` and wait for resource reload to finish; verify rendering remains correct.
5. Exit to the title screen, re-enter the same world, and play for at least two minutes, exceeding the previous approximately 82-second post-reload crash window. Check item pickups, animated textures and the same Create contraption again.
6. Exit normally.

For the next retest, preserve the existing resource-pack workload if practical because it exercises the 16K block atlas, but restore host-memory headroom before launch.

## Evidence to retain after each run

Keep:

- `logs/latest.log` (and `debug.log` if a failure is not explained by `latest.log`);
- any crash report;
- screenshots of visible rendering defects;
- exact enabled/disabled state of renderer-changing mods;
- host `MemAvailable` before launch for the heavy resource-pack test;
- whether launch, world entry, Create/Flywheel rendering, `F3+T`, world re-entry and clean exit passed.

## Current gate status

- Flywheel CI startup: **PASS**
- Crash Assistant CI startup: **PASS**
- current build-289 full-pack launch with Vulkan active: **PASS**
- current Create/Flywheel gameplay rendering: **PENDING RETEST WITH ADEQUATE HOST MEMORY HEADROOM**
- world enter/leave/re-enter + resource reload: **PENDING**
- representative particles/translucency/entities/GUI: **PENDING**
- minimized incompatible renderer-replacement set: **PARTIAL** — known-good disabled set is recorded, but individual incompatibility is not yet proven
- final concise compatibility/known-limitations matrix: **IN PROGRESS** — this file is the evidence ledger and will become the final matrix as gates close
