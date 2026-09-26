# Create Chronicles renderer-replacement compatibility — 2026-09-26

This document closes the Phase 4 question of which renderer-replacement mods must remain disabled in the known-good VulkanMod Forge 1.20.1 instance. It does **not** claim shaderpack compatibility and does not expand Phase 4 scope to implementing an Iris/Oculus-on-Vulkan bridge.

## VulkanMod ownership boundary

VulkanMod replaces Minecraft's OpenGL renderer and creates the real game window with `GLFW_CLIENT_API = GLFW_NO_API`. There is therefore no OpenGL context available for a second OpenGL rendering backend to own. The established Forge workaround `config/fml.toml -> earlyWindowControl = false` exists specifically so Forge's early OpenGL splash does not conflict with that ownership model.

The compatibility question is consequently not whether two ordinary client mods can coexist. It is whether another renderer stack requires an OpenGL device/context that VulkanMod intentionally does not provide.

## Exact Create Chronicles renderer stack

### Embeddium 0.3.31 — must remain disabled

The upstream Embeddium `20.1/forge` branch identifies itself as:

- Minecraft `1.20.1`;
- Forge `47.3.0`;
- Embeddium `0.3.31`.

That exact branch's `GLRenderDevice` imports `org.lwjgl.opengl.*`, returns `GL.getCapabilities()`, and issues direct OpenGL calls including `GL20C.glBufferData`, `GL30C.glBindVertexArray`, and `GL31C.glCopyBufferSubData`.

Upstream source:

- <https://github.com/FiniteReality/embeddium/blob/20.1/forge/gradle.properties>
- <https://github.com/FiniteReality/embeddium/blob/20.1/forge/src/main/java/me/jellysquid/mods/sodium/client/gl/device/GLRenderDevice.java>

Embeddium also describes its scope as a rewritten terrain renderer plus immediate-mode rendering optimizations for entities, GUIs and block entities, which overlaps the rendering paths VulkanMod itself replaces.

**Conclusion:** Embeddium 0.3.31 is not a candidate to leave enabled in VulkanMod's `GLFW_NO_API` baseline. Supporting it would require a separate architectural compatibility effort, not a configuration tweak. Its disabled state is evidence-backed and required.

### Oculus 1.8.0 — must remain disabled with Embeddium

Oculus 1.20.1-1.8.0 is an Iris-derived shaderpack renderer. Its exact 1.20.1 metadata declares Embeddium as required content:

- <https://modrinth.com/mod/oculus/version/1.20.1-1.8.0>

Because the required Embeddium OpenGL backend cannot own VulkanMod's no-OpenGL-context game window, Oculus cannot be part of the current Vulkan baseline either. Full Iris/Oculus-style shaderpack support is already outside the initial Phase 4 release blocker scope.

**Conclusion:** keep Oculus disabled. This is dependency/architecture driven, not an unexplained modpack workaround.

### Embeddium (Rubidium) Extra 0.5.4.4 — must remain disabled with Embeddium

The exact 1.20.1 `0.5.4.4+mc1.20.1-build.131` metadata declares Embeddium as required content and Oculus as optional:

- <https://modrinth.com/mod/rubidium-extra/version/0.5.4.4%2Bmc1.20.1-build.131>

The project describes itself as a Sodium Extra port for Embeddium/Rubidium. With Embeddium intentionally absent, there is no supported independent renderer role for this addon in the Vulkan baseline.

**Conclusion:** keep Rubidium Extra disabled as a consequence of removing its required renderer backend; no separate Vulkan incompatibility mechanism needs to be invented.

### Iris & Oculus Flywheel Compat 2.0.3 — must remain disabled with Oculus

The exact Forge 1.20.1 `2.0.3` metadata declares Oculus as required content and describes the mod as enabling Flywheel optimizations when using shaderpacks:

- <https://modrinth.com/mod/iris-flw-compat/version/dE1A45cG>

With Oculus intentionally absent, this bridge has no valid role in the Vulkan baseline. Flywheel itself remains enabled and has its own positive VulkanMod compatibility gate.

**Conclusion:** keep Oculus-Flywheel-Compat disabled because its required shaderpack renderer is absent; do **not** disable Flywheel itself.

## Minimal renderer-replacement baseline

| Component | Baseline state | Why |
| --- | --- | --- |
| VulkanMod | **Enabled** | Authoritative renderer for this project. |
| Create 0.5.1.j | **Enabled** | Required target workload. |
| Flywheel 0.6.11-13 | **Enabled** | Positive CI gate exists; Create fallback rendering is part of Phase 4. |
| Embeddium 0.3.31 | **Disabled** | Exact version is an OpenGL rendering backend; incompatible with VulkanMod's no-OpenGL-context ownership model without a new compatibility architecture. |
| Oculus 1.8.0 | **Disabled** | Requires Embeddium and provides the Iris-derived shaderpack renderer; shaderpack bridge is out of initial scope. |
| Rubidium Extra 0.5.4.4 | **Disabled** | Requires Embeddium; no independent role remains once Embeddium is removed. |
| Oculus-Flywheel-Compat 2.0.3 | **Disabled** | Requires Oculus; no role remains once Oculus is removed. |

This is the minimum renderer-replacement removal set identified in the target instance: the four disabled entries form one dependency/renderer stack. No evidence supports disabling additional ordinary gameplay/compatibility mods merely because VulkanMod is present.

## Gate interpretation

The Phase 4 renderer-replacement gate is satisfied for the current target scope:

- every renderer-changing component disabled from the baseline has an explicit architecture or dependency reason;
- no unrelated mod is included in the disabled set;
- Embeddium/Oculus compatibility is not being claimed or silently approximated;
- Flywheel remains enabled rather than being removed together with its Oculus-specific bridge;
- a future Vulkan-aware Embeddium/Oculus compatibility layer would be a new scoped feature, not unfinished baseline minimization.

This conclusion does not depend on the pending #745 RX visual check. That test remains necessary for the separate ordinary item/entity and representative-gameplay gates.
