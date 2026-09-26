# Create Chronicles RX visual retest — updated 2026-09-26

This is the current short-form hardware retest sheet. Historical compatibility evidence remains in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`; live Git/CI/runtime evidence wins if this sheet becomes stale.

## Artifact

Use **CI build #747**, executable commit:

`5d164d2773b01fc89bef173facd82dbe6cc3b462`

Build #746 and earlier artifacts are superseded for this retest.

Automated evidence before this RX run:

- public CI #747 is fully green across the complete Forge/Vulkan compatibility matrix, including both Vulkan startup variants, post-chain/depth-post-chain execution, screenshot readback, FTB Library, Pick Up Notifier, Immersive Portals 3.0.7, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and exact Create 0.5.1.j stencil coverage;
- #744 repairs auxiliary `MainTarget` ownership so Iceberg-style off-screen targets no longer alias Minecraft's swapchain target;
- #745 restores fixed Sampler0/1/2 reconciliation before ordinary draws, but the user's RX 6900 XT retest proved that change **did not restore** the missing Creative imagery or player/entity models;
- #746 adds bounded `NEW_ENTITY` draw diagnostics for shader/pipeline identity, vertex bytes, depth/cull/color state, actual descriptor-facing Sampler0/1/2 images, and representative first-vertex data;
- #747 restores the vanilla `ShaderInstance.apply() -> draw -> clear()` lifecycle around ordinary Vulkan draws. This matters for Immersive Portals-transformed item/entity shaders, which use VulkanMod's converted legacy-shader path and source their live model-view matrix, projection matrix, color, clipping equation, and named sampler state from `ShaderInstance.apply()`;
- the user's previous RX 6900 XT/RADV runs already confirmed both real PureBDcraft packs remain active, Vulkan activates on RADV, and the experimental GPU-terrain path executes successfully/fail-closed on representative full-pack terrain.

The private workflow's host-memory override is only for the disposable 8 GiB GitHub runner. **Do not add a memory-safety override to the RX 6900 XT run.**

## Why #747 is the current icon/model candidate

The #745 RX run reached the world and Creative menu, but ordinary block/item imagery and the rendered player/entity models remained absent. That hardware result disproved fixed sampler reconciliation as the broad visual fix.

Static tracing then found a stronger shared contract failure:

1. Immersive Portals transforms vanilla item/entity programs such as `rendertype_entity_*` and `rendertype_item_entity_translucent_cull`.
2. VulkanMod therefore routes those shaders through its converted legacy `ShaderInstance` path instead of the packaged preconverted core path.
3. The converted path binds Vulkan UBO fields directly to Minecraft `Uniform` storage. `ShaderInstance.apply()` is responsible for copying the current model-view matrix, projection matrix, shader color, active IP clipping equation, and named sampler state into that storage/state bridge.
4. VulkanMod's overwritten `BufferUploader.drawWithShader()` was binding and drawing the Vulkan pipeline without calling `ShaderInstance.apply()` or `clear()`.
5. A valid pipeline could therefore draw with stale/default converted-shader matrices and state. Identity/stale projection/model-view data is sufficient to clip ordinary GUI item models and world entities completely while terrain and non-model GUI chrome continue to render.

#747 restores that lifecycle before descriptor preparation and UBO upload, then clears it after the draw. The existing #746 diagnostics remain present, so a failing RX run should contain enough `NEW_ENTITY draw trace` evidence to distinguish a remaining item/entity state problem without another speculative patch.

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

Resource reload and world re-entry remain explicitly deferred for this pass.

1. Start Create Chronicles normally with both real PureBDcraft packs selected and enter the normal target world.
2. **Primary icon gate:** open Creative and check whether ordinary block/item imagery is visible.
3. **Primary model gate:** switch to third-person view and check the player model; if convenient, also look at one nearby entity.
4. If either primary gate still fails, stop there and preserve `latest.log` plus one screenshot. Note whether Creative imagery, player/entities, or both failed. The log should contain bounded `NEW_ENTITY draw trace` lines from #746/#747; do not repeat #745 or #746 separately.
5. Only if both primary gates pass, continue with the remaining Phase 4 visual checks: a moving Create contraption plus Create GUI/overlay, representative particles/liquids/translucency, and a real Immersive Portals portal view. Advancement Plaques/Iceberg may be observed naturally, but do not seek it out for this focused test.
6. Exit normally. Reload and world re-entry remain deferred.

## Stop condition / evidence

On the first new meaningful failure retain:

- `logs/latest.log`;
- one screenshot for a visible rendering defect;
- `logs/debug.log` only when `latest.log` does not explain the first failure;
- any crash report if a crash occurs;
- a short note saying whether Creative imagery, player/entities, or both were affected.

If the Creative imagery and player/entity gates pass on #747, that materially advances Phase 4. The broader Create/Flywheel/particles/translucency/portal checks can then continue; reload and world re-entry remain separate deferred gates.
