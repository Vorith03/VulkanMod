package net.vulkanmod.vulkan.shader;

import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.util.Collections;
import java.util.Map;
import java.util.function.IntSupplier;

/** Bridges converted legacy/mod ShaderInstance samplers into Vulkan draws. */
public final class ShaderRenderState {
    private static GraphicsPipeline activePipeline;
    private static Map<String, IntSupplier> activeSamplers = Collections.emptyMap();

    private ShaderRenderState() {}

    public static void activate(GraphicsPipeline pipeline, Map<String, IntSupplier> samplers) {
        if(pipeline == null) {
            throw new IllegalStateException("Cannot activate a legacy shader without a Vulkan pipeline");
        }
        activePipeline = pipeline;
        activeSamplers = samplers != null ? samplers : Collections.emptyMap();
    }

    public static void clear(GraphicsPipeline pipeline) {
        if(activePipeline != pipeline) return;
        activePipeline = null;
        activeSamplers = Collections.emptyMap();
    }

    public static GraphicsPipeline getActivePipeline() {
        return activePipeline;
    }

    public static boolean isActive() {
        return activePipeline != null;
    }

    public static void prepareTextures() {
        VulkanImage[] textures = activePipeline.images.stream()
                .map(image -> VTextureSelector.getTexture(image.name))
                .toArray(VulkanImage[]::new);
        RenderTargetManager.prepareSampledImages(textures);
    }

    public static VulkanImage resolveTexture(String samplerName) {
        IntSupplier supplier = activeSamplers.get(samplerName);
        if(supplier == null) return null;
        int textureId = supplier.getAsInt();
        return textureId > 0 ? GlTexture.getVulkanImage(textureId) : null;
    }
}
