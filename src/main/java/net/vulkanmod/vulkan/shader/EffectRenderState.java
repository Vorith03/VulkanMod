package net.vulkanmod.vulkan.shader;

import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.util.Collections;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Bridges Minecraft's EffectInstance apply/clear lifetime to Vulkan's manual
 * post-processing draw path. Effect samplers are named by shader JSON/GLSL
 * (for example DiffuseSampler), so they cannot be resolved through the fixed
 * core-shader Sampler0/Sampler1 selector slots.
 */
public final class EffectRenderState {
    private static GraphicsPipeline activePipeline;
    private static Map<String, IntSupplier> activeSamplers = Collections.emptyMap();

    private EffectRenderState() {}

    public static void activate(GraphicsPipeline pipeline, Map<String, IntSupplier> samplers) {
        if(pipeline == null)
            throw new IllegalStateException("Cannot activate an effect without a Vulkan pipeline");

        activePipeline = pipeline;
        activeSamplers = samplers != null ? samplers : Collections.emptyMap();
    }

    public static void clear(GraphicsPipeline pipeline) {
        if(activePipeline != pipeline)
            return;

        activePipeline = null;
        activeSamplers = Collections.emptyMap();
    }

    public static GraphicsPipeline getActivePipeline() {
        return activePipeline;
    }

    public static boolean isActive() {
        return activePipeline != null;
    }

    public static VulkanImage resolveTexture(String samplerName) {
        IntSupplier supplier = activeSamplers.get(samplerName);
        if(supplier == null)
            return null;

        int textureId = supplier.getAsInt();
        if(textureId <= 0)
            return null;

        return GlTexture.getVulkanImage(textureId);
    }
}
