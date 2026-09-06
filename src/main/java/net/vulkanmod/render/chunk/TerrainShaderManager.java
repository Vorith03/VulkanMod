package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class TerrainShaderManager {
    public static VertexFormat TERRAIN_VERTEX_FORMAT;

    public static void setTerrainVertexFormat(VertexFormat format) {
        TERRAIN_VERTEX_FORMAT = format;
    }

    static GraphicsPipeline terrainIndirectShader;
    public static GraphicsPipeline terrainDirectShader;
    private static GraphicsPipeline terrainRegionShader;

    private static Function<RenderType, GraphicsPipeline> shaderGetter;

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        createBasicPipelines();
        setDefaultShader();
    }

    public static void setDefaultShader() {
        setShaderGetter(renderType -> useRegionBatching(renderType) ? terrainRegionShader
                : useLegacyIndirect() ? terrainIndirectShader : terrainDirectShader);
    }

    public static boolean useRegionBatching(RenderType renderType) {
        return Initializer.CONFIG.regionBatching && terrainRegionShader != null
                && renderType != RenderType.translucent() && renderType != RenderType.tripwire();
    }

    public static boolean useLegacyIndirect() {
        return Initializer.CONFIG.indirectDraw && Vulkan.getDeviceInfo().isDrawIndirectSupported();
    }

    private static void createBasicPipelines() {
        terrainIndirectShader = createPipeline("terrain_indirect");
        terrainDirectShader = createPipeline("terrain_direct");
        if (Vulkan.getDeviceInfo().isRegionBatchingSupported()) {
            terrainRegionShader = createPipeline("terrain_region");
        }
        Initializer.LOGGER.info("Terrain region batching: {} (multiDrawIndirect + drawIndirectFirstInstance)",
                terrainRegionShader == null ? "unsupported; using fallback" :
                        Initializer.CONFIG.regionBatching ? "enabled" : "disabled in config");
    }

    private static GraphicsPipeline createPipeline(String name) {
        String path = String.format("basic/%s/%s", name, name);

        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, path);
        pipelineBuilder.parseBindingsJSON();
        pipelineBuilder.compileShaders();
        return pipelineBuilder.createGraphicsPipeline();
    }

    public static GraphicsPipeline getTerrainShader(RenderType renderType) {
        return shaderGetter.apply(renderType);
    }

    public static void setShaderGetter(Function<RenderType, GraphicsPipeline> consumer) {
        shaderGetter = consumer;
    }

    public static GraphicsPipeline getTerrainDirectShader(RenderType renderType) {
        return terrainDirectShader;
    }

    public static GraphicsPipeline getTerrainIndirectShader(RenderType renderType) {
        return terrainIndirectShader;
    }

    public static void destroyPipelines() {
        terrainIndirectShader.cleanUp();
        terrainDirectShader.cleanUp();
        if (terrainRegionShader != null) {
            terrainRegionShader.cleanUp();
            terrainRegionShader = null;
        }
    }
}
