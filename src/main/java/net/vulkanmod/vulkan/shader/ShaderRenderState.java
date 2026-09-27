package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.util.Collections;
import java.util.Map;
import java.util.function.IntSupplier;

/** Bridges converted legacy/mod ShaderInstance samplers into Vulkan draws. */
public final class ShaderRenderState {
    private static GraphicsPipeline activePipeline;
    private static Map<String, Object> activeSamplers = Collections.emptyMap();

    private ShaderRenderState() {}

    public static void activate(GraphicsPipeline pipeline, Map<String, Object> samplers) {
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
        // Let RenderTargetManager scan the pipeline first and allocate a sampled
        // image array only when an attachment transition is actually required.
        // The previous stream/toArray path allocated on every converted draw even
        // when every image was already shader-readable.
        RenderTargetManager.preparePipelineTextures(activePipeline);
    }

    public static VulkanImage resolveTexture(String samplerName) {
        Object sampler = activeSamplers.get(samplerName);
        if(sampler == null) return null;

        int textureId;
        if(sampler instanceof Integer id) {
            textureId = id;
        } else if(sampler instanceof RenderTarget renderTarget) {
            textureId = renderTarget.getColorTextureId();
        } else if(sampler instanceof AbstractTexture texture) {
            textureId = texture.getId();
        } else if(sampler instanceof IntSupplier supplier) {
            textureId = supplier.getAsInt();
        } else {
            return null;
        }

        return textureId > 0 ? GlTexture.getVulkanImage(textureId) : null;
    }
}
