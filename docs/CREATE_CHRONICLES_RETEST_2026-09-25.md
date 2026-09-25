# Create Chronicles RX retest — 2026-09-25

This is the current short-form hardware retest sheet. Historical compatibility evidence remains in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`; live Git/CI/runtime evidence wins if this sheet becomes stale.

## Artifact

Use **CI build #728**, executable commit:

`5e04d1e12c14f24da9a816ddc5beebc27a789642`

Do not use #723 or an earlier artifact for this retest.

Automated evidence before this RX run:

- public CI #728 is fully green across the normal Forge/Vulkan compatibility matrix;
- private `Vorith03/storage` real-resource-pack smoke #8 is green against the same executable commit with both real PureBDcraft ZIPs selected together and Vulkan validation enabled;
- the real smoke reached the 16384x8192 atlas, completed batched upload, retained both selected packs, logged `Vulkan smoke test passed`, and produced no `Validation Error` / `SYNC-HAZARD` failure;
- the older storage #7 workflow rerun with its 1 GiB CI reserve also passes against the current executable commit.

The private workflow's reduced host-memory minimum is only for the disposable 8 GiB GitHub runner. **Do not add a memory-safety override to the RX 6900 XT run.**

## JVM flags

Keep the four established experimental terrain flags together:

```text
-Dvulkanmod.experimentalGpuTerrainMesher=true
-Dvulkanmod.experimentalGpuTerrainCpuBypass=true
-Dvulkanmod.experimentalGpuTerrainDrawHandoff=true
-Dvulkanmod.experimentalGpuTerrainHybrid=true
```

Keep the established renderer-replacement baseline. Do not broadly re-enable Embeddium/Rubidium/Oculus-style renderer replacements for this test.

## Test sequence

1. Start Create Chronicles with both real PureBDcraft packs selected.
2. Wait for the initial resource reload to finish before entering the world.
3. Confirm the game still shows both packs selected. In the log, `Reloading ResourceManager:` should include both packs and there must be no `Caught error loading resourcepacks, removing all selected resourcepacks` rollback.
4. Confirm Vulkan is active on the RX 6900 XT / RADV and there is no Vulkan validation/device-loss failure.
5. Enter the normal target world.
6. Check ordinary terrain, entities, GUI, particles, liquids/translucency, and animated textures for obvious regressions.
7. Exercise a visible Create/Flywheel contraption and at least one Create GUI/overlay path.
8. Look through a real Immersive Portals portal.
9. Force at least one dirty mixed-section terrain rebuild while the four experimental flags are active; complete geometry should remain visible rather than blinking/disappearing during replacement.
10. Press `F3+T` and wait for the reload to complete. Verify both packs remain selected and rendering remains correct.
11. Exit to title, re-enter the same world, and play for at least two minutes. Recheck the same Create contraption, portal rendering, item pickups, and animated textures.
12. Exit normally.

## Stop condition / evidence

If a new blocker appears, stop at the **first** meaningful failure rather than continuing through cascading symptoms.

Retain:

- `logs/latest.log`;
- `logs/debug.log` when the first failure is not sufficiently explained by `latest.log`;
- any crash report;
- a screenshot only when the failure is visual;
- a short note identifying which numbered step failed.

The most useful success report is simply that steps 1–12 completed cleanly, plus any visible anomaly you noticed. Do not repeat already-settled memory or sparse-lighting telemetry unless the current failure specifically makes it relevant.

## What this run closes

A clean run would provide the missing hardware/full-pack evidence that CI cannot supply: RX 6900 XT/RADV, the full ~300-mod environment, real world entry, Create/Flywheel visuals, Immersive Portals visuals, hybrid dirty rebuild behavior, in-world resource reload, and world re-entry.

It would **not** by itself prove a performance improvement. Comparable Phase 5/6 frame-time A/B evidence is still required before any speedup or default-path claim.
