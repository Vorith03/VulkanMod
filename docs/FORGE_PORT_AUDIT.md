# VulkanMod Forge 1.20.1 Port Audit

## Scope

Target: Minecraft 1.20.1, Forge 47.3.0, Java 17.

This document records the current state of the Forge port after the first successful minimal-Forge world session, separates proven behavior from inferred behavior, and defines the next validation and compatibility work in evidence-driven order.

## Current proven state

### Build / packaging

- ForgeGradle build is green on the `forge-1.20.1` branch.
- The production JAR contains Forge metadata, Mixin config/refmap, the Forge access transformer, Vulkan/VMA/shaderc code, and required Linux/Windows VMA/shaderc natives.
- The shaded JAR does not contain a duplicate LWJGL core/system package.
- The production JAR does not contain Fabric runtime classes.
- MixinGradle and the Mixin annotation processor are configured for a production refmap.

### Runtime

- Forge 47.3.0 can start with the mod as the only gameplay mod.
- Forge early-window/splash handling was disabled for the test instance (`earlyWindowControl=false`).
- The Forge-specific `ImmediateWindowHandler.setupMinecraftWindow(...)` constructor handoff is now intercepted with a constructor-safe `@Redirect`.
- Minecraft reached the title/resource-loading path, created an integrated server, entered a single-player world, rendered a playable session, saved, disconnected, and exited with code 0.
- Therefore the Forge loader/build/Mixin port is no longer failing during basic startup or a minimal world session.

## Current checkpoint — 2026-09-06

- User confirmed real Vulkan gameplay on AMD Radeon RX 6900 XT (RADV NAVI21), with good performance and successful world load/save/exit. Vulkan activation is now proven; the earlier `radeonsi` investigation is superseded.
- Highest verified milestone: playable minimal-Forge Vulkan world (milestone 6), with the water texture fix now visually confirmed by the user. This does not establish full rendering correctness or Create Chronicles compatibility.
- `019747cc` fixes Forge's `LiquidBlockRenderer.vertex` semantic float order: red, green, blue, **alpha, U, V**. A wrong permutation still has the same JVM descriptor, so Mixin application alone cannot catch it.
- `bff88648` adds liquid target loading to startup smoke coverage. CI #60 / run `34026156239` passed distribution verification and Vulkan startup with early splash enabled and disabled.
- `0076a41c` extends the smoke test to invoke the transformed Forge liquid method with distinct alpha/U/V values and check the full emitted vertex, including packed light and cancellation. It checks translucent and opaque alpha. CI #61 / run `34026506701` passed the production build/distribution checks and both Vulkan startup probes, including the new semantic assertions. Runtime artifact: `VulkanMod-Forge-build` from that run.
- These are development `runClient` probes. They do not replace testing the production JAR on the user's GPU or visually checking water.

### Next unresolved gate

Water is confirmed fixed. The user requested terrain batching/performance work next; see `docs/TERRAIN_BATCHING.md`. For further rendering validation, use the latest green **`-all.jar`** in the same minimal Forge 47.3.0 instance, preserving `earlyWindowControl=false`. Check still and flowing water, water sides, underwater view, and lava. Then exercise resource reload (F3+T), window resizing, and world re-entry. Report any remaining defect and provide the instance's `.minecraft/logs/latest.log` (plus screenshot for visual issues).

Do not begin renderer optimization or infer full modpack compatibility from the successful minimal session. If this check passes, continue the existing Phase 3 minimal-rendering matrix, then Phase 4 compatibility triage. The Fabric compile-only annotation bridge remains documented mechanical cleanup.

## Architecture review

### Loader / lifecycle boundary

`Initializer` is now a Forge `@Mod` entrypoint. Its constructor intentionally performs only loader-safe metadata/log work. GLFW-dependent video-mode discovery and config loading are deferred to `Initializer.initialize()`, called from the Window mixin on the render thread.

This is the correct general direction for Forge because Forge constructs mod classes on loading workers.

Risk: `Initializer.getConfig()` can still call `initialize()` from any caller if `CONFIG` is null. Any future early caller could reintroduce a render-thread violation. Long-term, either make the initialization phase explicit/fail-fast or split config-file loading from GLFW video-mode discovery.

### Window handoff

Forge patches the vanilla Window constructor so the actual window comes from `ImmediateWindowHandler.setupMinecraftWindow(...)`. The port redirects this call, sets `GLFW_CLIENT_API = GLFW_NO_API`, delegates back to Forge, then records the returned Window handle through the constructor-return injection.

This is the correct Forge abstraction point discovered from Forge's own Window patch.

Risks:

- Forge's early window must remain disabled unless VulkanMod gains explicit support for the early-window provider.
- Any mod/coremod that transforms the same constructor call is a high-risk compatibility point.
- Window hints are global GLFW state. Instrument the effective handoff and returned handle so it is obvious whether the no-API hint is really being honored.

### RenderSystem takeover

`RenderSystemMixin` overwrites `RenderSystem.initRenderer(...)` and dispatches to `VRenderSystem.initRenderer()`. `VRenderSystem.initRenderer()` calls `Vulkan.initVulkan(window)`.

This is the critical renderer-activation chain. It deserves explicit phase logging at every handoff because a successful Minecraft session did not produce convincing Vulkan evidence in the prior log.

Add/retain high-signal logs for:

- Window handoff intercepted.
- GLFW no-API hint set.
- Forge returned window handle.
- `VRenderSystem.setWindow(handle)` invoked.
- VulkanMod `RenderSystem.initRenderer` overwrite invoked.
- `Vulkan.initVulkan(handle)` entered.
- Vulkan instance created.
- Window surface created.
- Physical device selected: name/vendor/device/driver/API.
- Logical device created.
- Swapchain created: format, extent, image count, present mode.
- Renderer initialized.

A single `Vulkan renderer active` line at the end is useful but not sufficient for diagnosing an intermediate failure.

### F3 / debug identification

`GlDebugInfoM` overwrites `GlUtil.getVendor()`, `getRenderer()`, and `getOpenGLVersion()` to return Vulkan device information when available.

The user seeing `radeonsi` means at least one of these is true:

- `GlDebugInfoM` was not applied to the production class,
- F3 obtains the displayed string through a different path in this Forge runtime,
- another transformer overwrote the method after VulkanMod,
- or Vulkan device info was never populated and a different code path supplied the OpenGL string.

The next runtime test should include both explicit Vulkan logs and F3 output. Do not infer renderer ownership from only one of them.

## Compatibility surface review

The largest long-term risk is not the Forge entrypoint. It is VulkanMod's compatibility emulation of Minecraft/OpenGL state.

### High-risk GL compatibility no-ops/TODOs

`GlStateManagerM` and `RenderSystemMixin` intentionally no-op or incompletely emulate several OpenGL operations, including examples such as:

- texture sub-image upload path(s),
- texture parameter changes,
- pixel-store state,
- GL program binding,
- parts of renderbuffer/framebuffer behavior,
- blend equation,
- generated GL buffer/VAO compatibility calls,
- active texture compatibility state,
- generic texture parameter entrypoints.

Vanilla may avoid or tolerate many of these. Modded renderers, custom block/entity renderers, GUI libraries, map mods, shader-adjacent mods, framebuffer effects, and dynamic texture mods may not.

Action rule: do not implement every TODO speculatively. For each Create Chronicles failure, identify the exact GL/RenderSystem call the mod expects and implement the smallest semantically correct Vulkan compatibility behavior.

### Mixins are the main collision surface

The mod replaces or redirects behavior across:

- Window / RenderSystem / GlStateManager,
- Minecraft frame lifecycle,
- RenderTarget/MainTarget,
- shader/program/uniform classes,
- textures and NativeImage,
- BufferBuilder/VertexBuffer/VertexFormat,
- chunk and level rendering,
- entity/model rendering,
- screenshots,
- GUI and debug rendering.

Any mod that overwrites, redirects, or makes assumptions about the same methods can conflict even if it does not advertise itself as a renderer mod.

For Create Chronicles, compatibility triage should be evidence-based and ordered by transformer overlap, not by the vague rule "anything that renders conflicts."

## Forge-specific cleanup debt

### Temporary Fabric compile-only bridge

`build.gradle` still includes the Fabric Maven repository and `compileOnly net.fabricmc:fabric-loader:0.14.21` solely because `LiquidRenderer` retains `@Environment(EnvType.CLIENT)` imports/annotation.

The dependency is not packaged at runtime, so this is not currently a functional blocker. Remove the annotation/imports and then remove the Fabric repository/compileOnly dependency before calling the Forge branch clean.

### Config initialization duplication

`MixinPlugin` loads `./config/vulkanmod_settings.json` independently from `Initializer`, while `Initializer` loads from Forge's `FMLPaths.CONFIGDIR` and writes defaults when necessary.

These happen for different purposes, but there are two Config instances and two path-resolution mechanisms. Normalize later so Mixin gating and runtime settings cannot silently diverge.

### Remapping audit

Several mixins use `@Overwrite(remap = false)` on Mojang/Minecraft or external-style methods. Some are correct because the target is external/unmapped; others may be historical Fabric-era assumptions.

Perform a method-by-method remap audit rather than bulk-changing them. For each target classify:

1. Minecraft mapped method -> generally allow refmap/remap.
2. LWJGL/Forge/external method -> `remap=false` is appropriate.
3. Synthetic/Forge-patched target -> inspect transformed signature and production refmap behavior.

The current green build proves syntax and mappings compile, not that every overwrite is definitely landing in production.

## Device / Vulkan implementation review

### Physical-device selection

The current selector favors the first suitable discrete GPU, then integrated GPU, then other suitable device. This is reasonable for the user's single-discrete-GPU test machine, but future robustness should expose/log every candidate and why it was accepted/rejected.

### Dynamic rendering feature chain

`Vulkan.DYNAMIC_RENDERING` is currently false, but logical-device creation still constructs and enables `VkPhysicalDeviceDynamicRenderingFeaturesKHR` in the pNext chain.

This should be reviewed. Enabling a feature that the renderer is configured not to use is unnecessary, and the code should verify feature/extension availability before requesting optional Vulkan features. On the RX 6900 XT this is unlikely to be the immediate blocker, but it is a portability smell.

### Vulkan error handling

Several Vulkan calls are checked, but others are not consistently checked. Before performance work, centralize/check return codes for instance/device/swapchain/queue/command-buffer/fence paths that can fail. Diagnostics should include the VkResult symbolic value and phase.

### Cleanup / lifecycle

`MinecraftMixin.close` unconditionally calls `Vulkan.waitIdle()` and later `Vulkan.cleanUp()`. If Vulkan initialization is partial or fails after some resources are created, shutdown safety should be verified. A staged initialization state or null-safe cleanup path would make failures easier to recover/report.

## Action plan

### Phase 0 — Freeze the known-good baseline

Goal: preserve the current minimal world-session success.

- Keep `b975f4ce...` as the known runtime-success baseline.
- Treat later diagnostic/cleanup commits independently.
- Do not mix Create Chronicles fixes into renderer-activation diagnostics.

Exit condition: baseline commit remains identifiable and buildable.

### Phase 1 — Prove or disprove Vulkan activation

Priority: highest.

1. Add phase logging across Window -> VRenderSystem -> Vulkan -> Device -> SwapChain.
2. Build in CI.
3. On next local test, collect `latest.log` plus F3 renderer text.
4. Interpret outcomes:
   - Vulkan phase logs + selected RX 6900 XT + Vulkan-style F3 device string: activation proven.
   - No `RenderSystem.initRenderer` Vulkan log: production RenderSystem mixin is not landing; audit remap/refmap target.
   - Vulkan init begins but fails: patch exact Vk/GLFW phase.
   - Vulkan logs succeed but F3 still says radeonsi: renderer may be Vulkan while debug-display mixin/path is wrong; inspect Forge F3 call path.
   - No Vulkan logs but game still renders: OpenGL path remains active; identify why Window/RenderSystem takeover is incomplete.

Exit condition: selected Vulkan device and initialized swapchain are directly present in runtime evidence.

### Phase 2 — Remove loader-port scaffolding

1. Remove Fabric `@Environment` annotation/imports from `LiquidRenderer`.
2. Remove Fabric Maven repository and compile-only loader dependency.
3. Normalize config-path/config-instance handling where safe.
4. Audit `remap=false` targets.
5. Keep CI green after each logical change.

Exit condition: Forge source/build has no Fabric loader dependency and production mixin targeting is understood.

### Phase 3 — Minimal Vulkan gameplay validation

Once Vulkan activation is proven, exercise renderer behavior deliberately:

1. Title/options screens.
2. Vulkan video options UI.
3. Window resize.
4. Windowed/fullscreen/windowed-fullscreen transitions.
5. VSync toggle.
6. Resource reload (F3+T).
7. Texture-heavy terrain.
8. Water/lava rendering.
9. Entities and block entities.
10. Particles/transparency.
11. GUI/container screens.
12. Screenshot.
13. Dimension change.
14. Save/quit and clean shutdown.

Exit condition: no renderer corruption/crash in the above minimal-Forge matrix.

### Phase 4 — Create Chronicles compatibility triage

Do not start with the entire pack as an undifferentiated crash target if the first full-pack run is noisy.

Order of attack:

1. Launch full Create Chronicles with known renderer replacements disabled.
2. Capture earliest meaningful Mixin/modloading/render failure.
3. Categorize by:
   - hard renderer replacement,
   - Mixin collision,
   - GL compatibility call expectation,
   - custom shader/framebuffer path,
   - texture upload path,
   - chunk/vertex format path,
   - harmless unrelated mod failure.
4. Patch smallest compatible behavior.
5. Retest full pack.
6. If failure chains are too coupled, binary-search mod groups by renderer-risk category rather than arbitrary halves.

Likely first exclusions for the Vulkan test profile are other primary renderer/shader replacements (for example Embeddium/Rubidium/Oculus-class mods). Do not assume every visual or rendering mod is incompatible; test concrete overlap.

Exit condition: Create Chronicles reaches a world with Vulkan activation proven.

### Phase 5 — Compatibility hardening

Use failures from the pack to fill only required GL-emulation gaps.

Prioritize:

- texture sub-image uploads,
- texture parameters/filter/wrap state,
- framebuffer/renderbuffer semantics,
- shader/program compatibility entrypoints,
- pixel-store behavior for uploads,
- blend equation/state,
- dynamic textures and atlases,
- custom vertex formats,
- modded RenderType behavior.

Add small regression probes/log assertions where practical.

Exit condition: common pack gameplay paths no longer depend on silent no-op behavior that breaks mods.

### Phase 6 — Performance work

Only after correctness and pack compatibility are stable:

- profile CPU submission/threading,
- profile chunk rebuild/upload pipeline,
- measure staging-buffer behavior,
- inspect descriptor/pipeline churn,
- inspect synchronization and queue usage,
- reduce unnecessary allocations,
- evaluate indirect draws only with feature checks,
- evaluate newer VulkanMod optimizations selectively against the 1.20.1 renderer architecture.

Do not mix speculative performance rewrites into compatibility debugging.

## Next local test checklist

When local testing is available again:

1. Use the diagnostic build produced after this audit.
2. Keep Forge early-window control disabled.
3. Use minimal Forge first, not Create Chronicles.
4. Start game and wait for title screen.
5. Open F3 and record renderer/vendor/version strings.
6. Enter the test world and move/render for at least a short session.
7. Resize the window once.
8. Exit normally.
9. Provide `latest.log`.

The log should let us prove the exact renderer activation stage without requiring visual guesswork.

## Decision gates

- Green CI != Vulkan works.
- World session != Vulkan works.
- F3 alone != complete Vulkan proof.
- Vulkan instance/device/swapchain logs + functioning frame loop = renderer activation proof.
- Minimal Vulkan proof comes before Create Chronicles.
- Create Chronicles world entry comes before performance optimization.

## Current priority summary

P0: Validate the requested terrain batching implementation against the confirmed RX 6900 XT water/gameplay baseline.

P1: Remove Fabric compile-only bridge and normalize Forge targeting/config debt.

P2: Validate minimal renderer behavior systematically.

P3: Bring up Create Chronicles and fix evidence-backed compatibility failures.

P4: Harden GL compatibility surface based on real mod usage.

P5: Optimize only after correctness is stable.
