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
    private static final int UNSET_TEXTURE_ID = Integer.MIN_VALUE;
    private static final int[] lastTextureIds = {
            UNSET_TEXTURE_ID, UNSET_TEXTURE_ID, UNSET_TEXTURE_ID
    };
    private static long lastSelectorMutationVersion = Long.MIN_VALUE;

    private ShaderTextureState() {
    }

    public static void syncFixedSamplers() {
        RenderSystem.assertOnRenderThread();

        int texture0 = RenderSystem.getShaderTexture(0);
        int texture1 = RenderSystem.getShaderTexture(1);
        int texture2 = RenderSystem.getShaderTexture(2);
        long selectorMutationVersion = VTextureSelector.getCoreSamplerMutationVersion();

        if(texture0 == lastTextureIds[0]
                && texture1 == lastTextureIds[1]
                && texture2 == lastTextureIds[2]
                && selectorMutationVersion == lastSelectorMutationVersion) {
            return;
        }

        reconcile(0, texture0);
        reconcile(1, texture1);
        reconcile(2, texture2);

        lastTextureIds[0] = texture0;
        lastTextureIds[1] = texture1;
        lastTextureIds[2] = texture2;
        // Reconciliation itself mutates selector state when repair is needed, so
        // cache the post-repair version rather than the version observed above.
        lastSelectorMutationVersion = VTextureSelector.getCoreSamplerMutationVersion();
    }

    private static void reconcile(int slot, int textureId) {
        VTextureSelector.bindTexture(slot,
                textureId == 0 ? null : GlTexture.getVulkanImage(textureId));
    }
}
