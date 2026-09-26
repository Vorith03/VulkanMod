# Create Chronicles RX visual retest — updated 2026-09-26

This is the current short-form hardware retest sheet. Historical compatibility evidence remains in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`; live Git/CI/runtime evidence wins if this sheet becomes stale.

## Artifact

Use **CI build #745**, executable commit:

`acb69d632a124b02d54f5ce6f42df84c33dc3d39`

Build #744 and earlier artifacts are superseded for this retest.

Automated evidence before this RX run:

- public CI #745 is fully green across the complete Forge/Vulkan compatibility matrix, including both Vulkan startup variants, post-chain/depth-post-chain execution, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and exact Create 0.5.1.j stencil coverage;
- #744 added a runtime oracle proving auxiliary `MainTarget`s receive independent Vulkan color/depth backing instead of aliasing Minecraft's swapchain target;
- #745 adds a runtime sampler-state oracle that deliberately corrupts the descriptor-facing Sampler0/light selectors, then proves ordinary pre-draw reconciliation restores the authoritative `RenderSystem` Sampler0 and Sampler2 images;
- the user's previous #728 RX 6900 XT/RADV run already confirmed both real PureBDcraft packs remain active through reload, Vulkan activates on RADV, and the experimental GPU-terrain path executes successfully/fail-closed on representative full-pack terrain.

The private workflow's host-memory override is only for the disposable 8 GiB GitHub runner. **Do not add a memory-safety override to the RX 6900 XT run.**

## What changed since the latest RX run

The #743 user run reached the world and Creative menu with terrain visible, but ordinary block/item icons and the rendered player were absent. Later, Advancement Plaques 1.6.9 / Iceberg 1.1.25 reached its custom item renderer and failed with:

`UnsupportedOperationException: Post effect cannot sample its own output attachment`

Two separate renderer-contract defects have now been repaired:

1. **Auxiliary MainTarget ownership (#744).** Iceberg creates `new MainTarget(96, 96)` for off-screen item rendering. VulkanMod previously treated every `MainTarget` as Minecraft's primary swapchain target, so Iceberg's off-screen target aliased the live output attachment. Only the actual primary target is swapchain-backed now; auxiliary MainTargets use normal Vulkan RenderTarget backing.
2. **Core item/entity sampler reconciliation (#745).** Vanilla RenderType setup records authoritative Sampler0/1/2 texture ids in `RenderSystem.shaderTextures`, but setup helpers can temporarily disturb the emulated active texture binding. OpenGL's `ShaderInstance.apply()` normally repairs those sampler bindings immediately before a draw. VulkanMod's preconverted ordinary draw path bypassed that GL apply step, so item/entity descriptors could see stale textures. #745 now reconciles the fixed core samplers before ordinary `BufferUploader` and VBO descriptor preparation and removes the extra unconditional Sampler0 overwrite from `AbstractTexture.bind()`.

#744 directly addresses the demonstrated Iceberg crash. #745 is directly on the ordinary batched item/entity render path and is the current candidate for the broader invisible Creative/player imagery. CI proves the contracts and safety paths; only the RX/full-pack run can close the visible gate.

## JVM flags

Keep the four established experimental terrain flags together:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Keep the established renderer-replacement baseline. Do not broadly re-enable Embeddium/Rubidium/Oculus-style renderer replacements for this test.

## Focused Phase 4 retest sequence

The previous RX runs already established pack retention, RADV Vulkan activation, real GPU-terrain execution, and world continuation past Distant Horizons' guarded AFTER_LEVEL callback. Do not spend this run re-investigating those paths unless they regress.

The user has deferred resource reload, forced dirty hybrid rebuild, and world re-entry for this pass. Their roadmap gates remain open.

1. Start Create Chronicles normally with both real PureBDcraft packs selected and enter the normal target world.
2. **Primary visual gate:** open Creative and check whether ordinary block/item icons are visible. Then check the character in third-person view or another representative entity. If either is still invisible, stop here, preserve one screenshot plus `latest.log`, and note whether icons, player/entities, or both failed.
3. If the primary visual gate passes, allow normal gameplay long enough for an Advancement Plaques/Iceberg item icon to render if one naturally appears. Confirm that the old self-sampling exception does not recur; there is no need to deliberately seek a particular advancement.
4. Inspect ordinary terrain, other entities, particles, liquids/translucency, animated textures, and a normal GUI while moving through the world.
5. Watch a moving Create contraption (a spinning water wheel or another visible kinetic machine is sufficient), then open a Create GUI/overlay. Record whether the moving parts, textures, and UI are visible and correct. The CI Flywheel smoke proves that its OpenGL backend stays off; it cannot prove the fallback visuals.
6. Look through a real Immersive Portals portal and check both the scene beyond it and the portal edge.
7. Exit normally.

## Stop condition / evidence

On the first new meaningful failure retain:

- `logs/latest.log`;
- `logs/debug.log` when `latest.log` does not explain the first failure;
- any crash report;
- one screenshot for a visible rendering defect;
- a short note identifying which numbered step failed.

If steps 1–7 complete cleanly, the current Phase 4 visual questions gain representative RX evidence. Reload, world re-entry, forced dirty hybrid replacement, renderer-replacement minimization, and performance measurements remain separate open gates.
