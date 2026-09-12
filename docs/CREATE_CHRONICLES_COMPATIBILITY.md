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

Verified on 2026-09-12: [CI #310](https://github.com/Vorith03/VulkanMod/actions/runs/34680822349) passed build and all configured smoke-test gates at source `1b6cf44284f4b82e680e75aaea5d43753fb3c800`.

- Download the `VulkanMod-Forge-build-310` artifact and install its `-all.jar`.
- Keep `config/fml.toml` -> `earlyWindowControl = false`.
- Disable PickupNotifier 8.0.0 for this retest; retain the existing renderer-replacement baseline below.
- Build #308's packed texture staging remains in #310 and was verified in the real pack: large upload batches staged only requested texels instead of source-row gaps.
- Build #310 additionally purges allocator-cached pages immediately after old static atlas CPU pixels are closed and before replacement resource decoding starts. The existing post-success purge remains in place.
- Build #310 also logs this process' DRM-client resident VRAM/GTT alongside the existing device-wide AMDGPU counters so a reload-time GTT surge can be attributed instead of guessed at.
- No process/system memory safety limit was weakened.

### Build #308 full-pack reload evidence — 2026-09-12

Build #308 (`29df210a6d73141e069e7aa90cdddc4a4506b146`) was tested in the target instance with PickupNotifier disabled. The machine reported approximately 19.4 GiB available before Minecraft launch, so the result is no longer explained solely by unusually poor launch headroom.

The world loaded successfully and `F3+T` began an in-world resource reload. Before replacement decoding, VulkanMod retired the current terrain buffers and 49,294 static atlas sprite images. NativeImage accounting fell from approximately 1205 MiB to 282 MiB, a logical release of approximately 922 MiB. Process RSS, however, fell only from approximately 11,681 MiB to 11,486 MiB, showing that much of the freed native allocation remained resident/reusable in allocator caches.

Replacement resource preparation then rebuilt CPU-side image memory quickly. About seven seconds after reload began, live NativeImages had risen to approximately 1537 MiB while staging usage was only 9 MiB. The reload eventually hit the unchanged safety guard at approximately:

- Java heap used: 7362 / 8192 MiB;
- process RSS: 12049 MiB;
- live NativeImages: 1656 MiB;
- estimated live Vulkan images: 2010 MiB;
- texture staging used: 22 MiB;
- system `MemAvailable`: 4095 MiB;
- swap free: 0 MiB.

This rules out the previously fixed oversized texture-staging spans as the dominant blocker in this failure. The guard happened to be checked by an animated sprite upload, but staging was small at the trip and the replacement atlas apply had not yet begun.

A second signal is now under investigation: between the 00:09:00 diagnostic snapshot and the safety trip, process RSS was essentially flat (about 12092 -> 12049 MiB) while system `MemAvailable` fell by about 1.3 GiB and device-wide AMDGPU GTT usage rose from about 2152 -> 3659 MiB. Device-wide GTT is not process attribution, so build #310 adds per-process DRM-client resident VRAM/GTT accounting before any source change is made on that basis.

### Current known limitations

| Path | Evidence | Required action / remaining gate |
| --- | --- | --- |
| PickupNotifier 8.0.0 transparency framebuffer | Build #303 launcher stack: `GL30C.glGetInteger` -> `TransparencyBuffer.prepareExtraFramebuffer`; LWJGL aborted because the Vulkan window has no OpenGL context | Disable PickupNotifier for the baseline. No compatible configuration or replacement implementation has been verified. |
| Full-pack resource reload | #308 reaches in-world `F3+T`, retires 922 MiB of old static NativeImages, then hits the unchanged system-memory guard during replacement preparation | Retest #310. Compare the pre-decode allocator-purge RSS/MemAvailable result and process DRM-client GTT trajectory. Do not lower the guard. |
| Heavy 16K atlas workload | #308 verifies packed upload staging in the real pack; reload failure occurs with only 22 MiB staging in use | Preserve the same resource-pack workload for comparison. Staging is no longer the leading memory suspect. |
| Animated atlas images | Reload retirement preserves animated sprite source/mip data; #308 failure occurs before reload completion | Verify animated textures continue after a successful reload. |
| Create/Flywheel gameplay | Historical #226 water wheel rendered; #310 Flywheel startup smoke passes | Current full-pack contraption visual test remains pending. |

These findings are evidence for Phase 4 compatibility only. CI startup coverage does not close full-pack gameplay gates.

## Build 289 full-pack launch — 2026-09-09

The first Phase 4 user-machine run establishes the current full-pack launch gate:

- Prism loaded the target ~300-mod Create Chronicles instance on Forge 47.3.0 / Java 17;
- Forge early splash was disabled and VulkanMod created its `GLFW_NO_API` window;
- terrain region batching initialized;
- the client completed its initial resource load and logged `Vulkan renderer active: AMD Radeon RX 6900 XT (RADV NAVI21)`;
- the normal test world reached in-game and the integrated server began normal save/pause activity.

Therefore the Phase 4 gate **current distributable launches the target Create Chronicles instance with Vulkan active** is **PASS** for build 289 and remains demonstrated by later full-pack runs.

The build-289 run did not reach the remaining gameplay checks. Immediately after first world entry, VulkanMod's existing host-memory safety guard deliberately stopped further texture staging when Linux `MemAvailable` fell just below the configured 4096 MiB no-swap floor. The fatal snapshot reported approximately:

- process RSS: 11823 MiB;
- live NativeImages: 1436 MiB;
- estimated live Vulkan images: 1989 MiB;
- VMA live image allocations: 2257 MiB;
- system `MemAvailable`: 4080 MiB;
- swap free: 0 MiB;
- AMD VRAM used: 9188 / 16368 MiB.

At that time this was not classified as a build-289 VulkanMod memory regression because the machine had materially less headroom than during the earlier successful heavy-pack baseline:

| Measurement | Earlier heavy-pack run | Build 289 Phase 4 run | Difference |
| --- | ---: | ---: | ---: |
| RAM available before Minecraft launch | 22697 MiB | 16740 MiB | -5957 MiB |
| 16K atlas, process RSS before allocation | 11982 MiB | 11230 MiB | -752 MiB |
| 16K atlas, live NativeImages before allocation | 1830 MiB | 1414 MiB | -416 MiB |
| 16K atlas, estimated Vulkan images before allocation | 543 MiB | 71 MiB | -472 MiB |
| 16K atlas, system available before allocation | 10426 MiB | 5694 MiB | -4732 MiB |
| 16K atlas, AMD VRAM already used before allocation | 2433 MiB | 6785 MiB | +4352 MiB |

Build 289 was therefore using less VulkanMod-tracked memory at the comparable 16K-atlas point, while the host started with roughly 6 GiB less available RAM and roughly 4.3 GiB more VRAM already occupied. The later #308 run started with substantially more host headroom yet still failed during `F3+T`, so current work now focuses on the reload peak itself rather than treating launch headroom as the sole explanation.

Do **not** lower the 4096 MiB hard system-memory safety floor merely to make this test pass. This machine has previously suffered system-wide OOMs under the same large resource-pack workload and has no swap.

## Historical full-pack evidence

A pre-Phase-4 full Create Chronicles run on build 226 established a useful historical baseline:

- the full modpack entered a world with Vulkan active;
- gameplay remained stable for several minutes;
- a Create water wheel rendered and spun correctly, exercising Create/Flywheel rendering;
- the game exited normally and the integrated server saved cleanly;
- no `VK_ERROR_DEVICE_LOST`, RADV command-stream rejection, JVM crash, system OOM, or VulkanMod memory-safety abort was observed in that successful run.

This evidence remains useful context, but the current Phase 4 gameplay gates require the current artifact to survive the same paths after the substantial renderer-correctness work that followed build 226.

## Renderer-replacement baseline

| Component | State in known-good Vulkan instance | Evidence status | Phase 4 interpretation |
| --- | --- | --- | --- |
| VulkanMod | Enabled | Current full-pack Vulkan renderer active on RX 6900 XT | Required; full-pack launch PASS |
| Create 0.5.1.j | Enabled | Loaded in full pack | Required target workload |
| Flywheel 0.6.11-13 | Enabled | Loaded; CI startup gate also exists | Supported baseline; needs current gameplay retest |
| Crash Assistant 1.9.7 | Enabled | Loaded; CI startup gate exists | Supported baseline |
| Embeddium 0.3.31 | Disabled | Instance mod list | Disabled for the working baseline; incompatibility not yet individually proven |
| Oculus 1.8.0 | Disabled | Instance mod list | Disabled for the working baseline; shaderpack compatibility is out of initial Phase 4 scope |
| Oculus-Flywheel-Compat 2.0.3 | Disabled | Disabled because Oculus is absent | No independent incompatibility claim |
| Rubidium Extra 0.5.4.4 | Disabled | Instance mod list | Disabled for the working baseline; independent incompatibility not yet proven |

Do not re-enable renderer replacements in bulk. If Phase 4 later tests them, add one renderer-changing component at a time so any failure mechanism is attributable.

## Mandatory current-artifact test sequence

Run these against build #310 in the real Create Chronicles instance. Disable PickupNotifier 8.0.0 and keep the known-good renderer-replacement set disabled initially. Preserve the same resource-pack workload and do not use diagnostic memory-safety overrides.

1. Launch to the title screen and confirm the log contains `Vulkan renderer active:` for the RX 6900 XT.
2. Enter the normal test world and inspect terrain, entities, GUI, particles and translucent blocks/liquids during ordinary movement.
3. Exercise a visible Create/Flywheel contraption (water wheel is sufficient for the first pass; a moving contraption is better for follow-up coverage).
4. Press `F3+T` and wait for resource reload to finish. In the log, retain the `Native allocator purge` line and the adjacent `resource reload after pre-decode native allocator purge` snapshot; the new snapshots should also include `process DRM clients` and resident VRAM/GTT.
5. If reload succeeds, verify rendering remains correct, exit to the title screen, re-enter the same world, and play for at least two minutes. Check item pickups, animated textures and the same Create contraption again.
6. Exit normally.

If reload trips the memory guard again, stop there and retain `logs/latest.log`; the pre-decode purge and per-process DRM fields are specifically intended to make that single failure sufficient to choose the next source-level target.

## Evidence to retain after each run

Keep:

- `logs/latest.log` (and `debug.log` if a failure is not explained by `latest.log`);
- any crash report;
- screenshots of visible rendering defects;
- exact enabled/disabled state of renderer-changing mods;
- whether launch, world entry, Create/Flywheel rendering, `F3+T`, world re-entry and clean exit passed;
- the build-310 pre-decode allocator-purge before/after RSS and `MemAvailable` values;
- the build-310 process DRM-client resident VRAM/GTT values around reload growth and any safety trip.

## Current gate status

- Flywheel CI startup: **PASS**
- Crash Assistant CI startup: **PASS**
- current full-pack launch with Vulkan active: **PASS**
- current Create/Flywheel gameplay rendering: **PENDING CURRENT-ARTIFACT RETEST**
- world enter/leave/re-enter + resource reload: **PENDING** — #308 failed during reload preparation; #310 is the current retest artifact
- representative particles/translucency/entities/GUI: **PENDING**
- minimized incompatible renderer-replacement set: **PARTIAL** — known-good disabled set is recorded, but individual incompatibility is not yet proven
- final concise compatibility/known-limitations matrix: **IN PROGRESS** — this file is the evidence ledger and will become the final matrix as gates close
