# Create Chronicles RX visual retest — updated 2026-09-26

This is the current short-form hardware retest sheet. Historical compatibility evidence remains in `docs/CREATE_CHRONICLES_COMPATIBILITY.md`; live Git/CI/runtime evidence wins if this sheet becomes stale.

## Artifact

Use **CI build #742**, executable commit:

`185cf90672dea41abf97eab8c2d9dba8fa0f260e`

Build #741 and earlier artifacts are superseded.

Automated evidence before this RX run:

- public CI #742 is fully green across the complete Forge/Vulkan compatibility matrix, including the direct-seed GPU selection fixture, Distant Horizons 3.2.0-b, Crash Assistant, Chat Heads, Flywheel 0.6, and exact Create 0.5.1.j stencil coverage; its only executable change since #741 retries rejected uploads in the separate default-off GPU candidate-selection path;
- the DH fixture verifies the exact Forge `afterLevelRenderEvent` target without prematurely initializing DH's dependency-injected proxy, and the normal terrain model table still builds afterward;
- the private `Vorith03/storage` two-PureBDcraft-pack workload passed against the then-current public branch with its host-memory threshold unchanged; the later direct-seed selection changes have not had a separate private-pack run;
- the user's previous #728 RX 6900 XT/RADV run already confirmed both real packs remain active through reload, Vulkan activates on RADV, and the experimental GPU-terrain path executes successfully/fail-closed on representative full-pack terrain.

The private workflow's host-memory override is only for the disposable 8 GiB GitHub runner. **Do not add a memory-safety override to the RX 6900 XT run.**

## What changed since the last RX run

The #728 user run moved past the old resource-pack blockers and entered world rendering, then hard-aborted when Distant Horizons 3.2.0-b executed:

`ForgeClientProxy.afterLevelRenderEvent() -> GL11.glGetInteger(GL_FRAMEBUFFER_BINDING)`

VulkanMod uses a `GLFW_NO_API` window, so no OpenGL context exists. DH's native OpenGL LOD renderer was already suppressed; this Forge callback only cached the currently bound OpenGL framebuffer for that renderer.

The current build cancels only this OpenGL-only DH callback at entry. DH's chunk/data/network/input lifecycle remains intact. This is a fail-closed compatibility fix; Distant Horizons LOD rendering itself is still unavailable under Vulkan.

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

The previous RX run already established pack retention, RADV Vulkan activation, and real GPU-terrain execution. A new launch inherently exercises those paths again, but do not spend the run re-investigating them unless they regress.

The user has deferred resource reload, forced dirty hybrid rebuild, and world re-entry for this pass. Their roadmap gates remain open.

1. Start Create Chronicles normally with both real PureBDcraft packs selected and enter the normal target world.
2. **Primary gate:** verify world rendering continues past the point where #728 immediately aborted in Distant Horizons' AFTER_LEVEL framebuffer query. If a new first blocker appears, stop there.
3. If stable, inspect ordinary terrain, entities, particles, liquids/translucency, animated textures, and a normal GUI for obvious regressions while moving through the world.
4. Watch a moving Create contraption (a spinning water wheel or another visible kinetic machine is sufficient), then open a Create GUI/overlay. Record whether the moving parts, textures, and UI are visible and correct. The CI Flywheel smoke only proves that its OpenGL backend stays off; it cannot prove the fallback visuals.
5. Look through a real Immersive Portals portal and check both the scene beyond it and the portal edge.
6. Exit normally.

## Stop condition / evidence

On the first new meaningful failure retain:

- `logs/latest.log`;
- `logs/debug.log` when `latest.log` does not explain the first failure;
- any crash report;
- a screenshot only for a visible rendering defect;
- a short note identifying which numbered step failed.

If steps 1–6 complete cleanly, the current Phase 4 visual questions gain representative RX evidence. Reload, world re-entry, forced dirty hybrid replacement, renderer-replacement minimization, and performance measurements remain separate open gates.
