package net.vulkanmod.vulkan.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.GlTexture;

/**
 * Reconciles Minecraft's authoritative core-shader sampler ids with Vulkan's
 * descriptor-facing texture selector immediately before an ordinary draw.
 *
 * <p>Vanilla RenderType setup records Sampler0/1/2 in
 * {@code RenderSystem.shaderTextures}. Some setup helpers then bind textures
 * temporarily through the legacy active-texture path (for example
 * {@code LightTexture.turnOnLightLayer()} calls {@code bindForSetup()}). OpenGL
 * repairs those temporary binds when {@code ShaderInstance.apply()} binds the
 * recorded sampler ids. VulkanMod's preconverted core-shader draw path bypasses
 * that OpenGL apply step, so it must perform the same reconciliation itself.</p>
 */
public final class ShaderTextureState {
    private static final int CORE_FIXED_SAMPLER_COUNT = 3;

    private ShaderTextureState() {
    }

    public static void syncFixedSamplers() {
        RenderSystem.assertOnRenderThread();

        for(int slot = 0; slot < CORE_FIXED_SAMPLER_COUNT; ++slot) {
            int textureId = RenderSystem.getShaderTexture(slot);
            VTextureSelector.bindTexture(slot,
                    textureId == 0 ? null : GlTexture.getVulkanImage(textureId));
        }
    }
}
