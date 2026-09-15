# FTB Chunks runtime compatibility checkpoint — 2026-09-15

This note records RX 6900 XT / Create Chronicles runtime evidence and the follow-up compatibility fix. It is a compatibility detour; Phase 7 GPU-terrain sequencing remains otherwise unchanged.

## User runtime evidence from build 440

- FTB Chunks large map opened, and the minimap was present, but both terrain images were entirely black.
- `F3+T` resource reload completed normally.
- Two world leave/re-enter cycles completed normally.
- The previously reported large screen/world visual artifact appeared, but disappeared immediately when the FTB Chunks death marker was removed.
- Resource-lifecycle telemetry during the same validation showed old atlas retirement freeing roughly 922 MiB of `NativeImage` backing, a pre-decode allocator purge reducing RSS by roughly 1.16 GiB, and a post-apply purge returning roughly another 395 MiB. Treat these as one observed runtime sample, not a general memory benchmark.

The reload/re-entry observations supersede the prior status that those paths were untested for the integrated build. They do not close all Phase 4 compatibility gates.

## Black map root cause and fix

FTB Chunks 1.20.1 allocates its map/minimap textures through raw Minecraft texture IDs rather than `DynamicTexture`: it calls `TextureUtil.generateTextureId()`, `TextureUtil.prepareImage(...)`, and then uploads the generated `NativeImage`.

VulkanMod's `MTextureUtil.prepareImage(...)` overwrite was empty. A generated synthetic GL texture name therefore had no Vulkan image backing for the subsequent `NativeImage.upload()` path. Binding such an ID could also leave the previously selected Vulkan texture active.

Commit `2dc0b3bf9be491f55e4fa9ee041d778d35d9dc86` (`fix: allocate Vulkan backing for raw texture ids`) makes `prepareImage(...)` allocate and bind a format/size/mip-correct `VulkanImage` for the synthetic ID and adds a Vulkan-validation smoke assertion for that raw-ID path.

Adversarial review found that raw texture deletion removed the synthetic GL name without retiring the newly associated Vulkan image. Commit `5704ae14d250332d6ba6e10b866fb2694ffcee43` (`fix: retire raw Vulkan texture backing safely`) retires that backing through the existing frame-deferred `VulkanImage.free()` mechanism.

CI #441 failed only because the new smoke helper itself called immediate `doFree()` without first establishing GPU idleness; Vulkan validation reported `VUID-vkDestroyImage-image-01000`. The screenshot/readback smoke itself still reached its success marker. Commit `5704ae14` fixes the deterministic test cleanup by waiting for idle before forced destruction while production deletion remains deferred.

CI #442, run `34924939987`, job `104240880928`, is fully green at `5704ae14`: build/distributable verification, both Vulkan startup modes, persistent GPU indirect shadow commands, vanilla post-chain, depth post-chain, screenshot/readback, FTB Library 2001.2.13, Crash Assistant 1.9.7, Chat Heads 0.13.18, Flywheel 0.6, logs, and artifacts all passed.

Build artifact: `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.442-g5704ae14-all.jar`

SHA-256: `804a85586026762c0843bf86f70ce6c64d8a7af9fe31de1b10af9c3eafda0291`

## Remaining runtime checks

The next RX 6900 XT check for build 442 is intentionally narrow:

1. Open the FTB Chunks large map and verify whether terrain pixels now render instead of a black map.
2. Verify whether the minimap terrain also renders.

Do not repeat `F3+T` or world re-entry solely for this raw-texture fix; those paths already passed on the immediately preceding integrated build and this patch does not change their lifecycle code.

The death-marker artifact remains a separate open issue. Current evidence strongly localizes it to FTB Chunks' in-world waypoint/icon path: death points become ordinary waypoint icons projected from world position into screen coordinates and drawn through FTB Library. Removing the death marker removing the artifact is inconsistent with the earlier leading compressed-terrain-vertex hypothesis for this specific artifact. Do not claim that hypothesis is fully disproven for unrelated geometry defects, but do not use it as the primary explanation for this death-marker-correlated artifact.

Do not mix a waypoint-rendering fix into the map-texture patch. Reproduce/diagnose the in-world death-marker draw path separately before changing renderer state or icon transforms.
