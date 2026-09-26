package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.shader.EffectRenderState;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.ShaderRenderState;
import net.vulkanmod.vulkan.texture.ShaderTextureState;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Mixin(BufferUploader.class)
public class BufferUploaderM {
    private static final int VULKANMOD_NEW_ENTITY_TRACE_LIMIT = 48;
    private static boolean vulkanmod$warnedMissingNewEntityShader;
    private static final Set<String> vulkanmod$tracedNewEntityStates = new HashSet<>();

    /**
     * @author
     */
    @Overwrite
    public static void reset() {}

    /**
     * @author
     */
    @Overwrite
    public static void drawWithShader(BufferBuilder.RenderedBuffer buffer) {
        RenderSystem.assertOnRenderThread();
        buffer.release();

        BufferBuilder.DrawState parameters = buffer.drawState();

        Renderer renderer = Renderer.getInstance();

        if(parameters.vertexCount() <= 0)
            return;

        ShaderInstance shader = RenderSystem.getShader();
        if(shader == null && parameters.format() == DefaultVertexFormat.NEW_ENTITY) {
            // Some Forge mods register custom core shaders through RegisterShadersEvent.
            // VulkanMod's legacy Forge reload bridge does not yet retain every custom
            // shader supplier, so a null supplier previously crashed BufferUploader.
            // NEW_ENTITY is the common item/entity layout and has a compatible
            // Vulkan-backed vanilla shader. Keep this fallback deliberately narrow:
            // unknown vertex formats still fail instead of silently using the wrong
            // pipeline. Create 0.5.1's glowing Worldshaper item exercises this path.
            shader = GameRenderer.getRendertypeEntityTranslucentShader();
            if(!vulkanmod$warnedMissingNewEntityShader) {
                vulkanmod$warnedMissingNewEntityShader = true;
                Initializer.LOGGER.warn(
                        "RenderType supplied no ShaderInstance for NEW_ENTITY draw; using Vulkan entity-translucent compatibility shader");
            }
        }

        if(shader == null) {
            throw new IllegalStateException("RenderType supplied no ShaderInstance for Vulkan draw format " + parameters.format());
        }

        GraphicsPipeline pipeline = ((ShaderMixed)shader).getPipeline();
        if(pipeline == null) {
            throw new IllegalStateException("ShaderInstance has no Vulkan pipeline: " + shader.getName());
        }
        // Vanilla ShaderInstance.apply() rebinds the sampler ids recorded by
        // RenderType setup after helpers such as LightTexture.bindForSetup()
        // temporarily disturb the active GL texture binding. The preconverted
        // Vulkan draw bypasses that GL apply step, so restore the authoritative
        // fixed sampler state before resolving attachment layouts/descriptors.
        ShaderTextureState.syncFixedSamplers();

        // GUI/item draws can read an off-screen RenderTarget without first
        // calling bindRead(). Resolve their samplers before binding the pipeline:
        // transitioning an attachment must end and resume the current pass on
        // this frame's command buffer, never on the helper upload command buffer.
        RenderTargetManager.preparePipelineTextures(pipeline);

        if(parameters.format() == DefaultVertexFormat.NEW_ENTITY) {
            vulkanmod$traceNewEntityDraw(buffer, parameters, shader, pipeline);
        }

        GraphicsPipeline.requestPrimitiveMode(parameters.mode());
        renderer.bindGraphicsPipeline(pipeline);
        renderer.uploadAndBindUBOs(pipeline);
        Renderer.getDrawer().draw(buffer.vertexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
    }

    private static void vulkanmod$traceNewEntityDraw(
            BufferBuilder.RenderedBuffer buffer,
            BufferBuilder.DrawState parameters,
            ShaderInstance shader,
            GraphicsPipeline pipeline) {
        if(vulkanmod$tracedNewEntityStates.size() >= VULKANMOD_NEW_ENTITY_TRACE_LIMIT)
            return;

        int projectionHash = RenderSystem.getProjectionMatrix().hashCode();
        String stateKey = shader.getName() + '|' + projectionHash + '|' + VRenderSystem.depthTest + '|'
                + VRenderSystem.depthMask + '|' + VRenderSystem.depthFun + '|' + VRenderSystem.cull;
        if(!vulkanmod$tracedNewEntityStates.add(stateKey))
            return;

        float[] shaderColor = RenderSystem.getShaderColor();
        ByteBuffer mirroredColor = VRenderSystem.getShaderColor().buffer;
        ByteBuffer vertexBytes = buffer.vertexBuffer();
        long expectedBytes = (long)parameters.vertexCount() * DefaultVertexFormat.NEW_ENTITY.getVertexSize();

        Initializer.LOGGER.info(
                "NEW_ENTITY draw trace {}/{} shader={} pipeline={} mode={} vertices={} bytes={}/{} projectionHash={} "
                        + "depth=[test={},write={},func={}] cull={} colorMask=0x{} "
                        + "shaderColor=[{},{},{},{}] mirroredColor=[{},{},{},{}] "
                        + "samplers=[0:{},1:{},2:{}] firstVertex={}",
                vulkanmod$tracedNewEntityStates.size(), VULKANMOD_NEW_ENTITY_TRACE_LIMIT,
                shader.getName(), pipeline.name, parameters.mode(), parameters.vertexCount(),
                vertexBytes.remaining(), expectedBytes, projectionHash,
                VRenderSystem.depthTest, VRenderSystem.depthMask, VRenderSystem.depthFun,
                VRenderSystem.cull, Integer.toHexString(VRenderSystem.getColorMask()),
                shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3],
                mirroredColor.getFloat(0), mirroredColor.getFloat(4),
                mirroredColor.getFloat(8), mirroredColor.getFloat(12),
                vulkanmod$imageSummary(VTextureSelector.getBoundTexture()),
                vulkanmod$imageSummary(VTextureSelector.getOverlayTexture()),
                vulkanmod$imageSummary(VTextureSelector.getLightTexture()),
                vulkanmod$firstNewEntityVertex(vertexBytes));
    }

    private static String vulkanmod$imageSummary(VulkanImage image) {
        if(image == null)
            return "null";

        String source = image == VTextureSelector.getWhiteTexture() ? "white" : "image";
        return source + "#" + image.getId() + ':' + image.width + 'x' + image.height + "/fmt=" + image.format;
    }

    private static String vulkanmod$firstNewEntityVertex(ByteBuffer source) {
        ByteBuffer bytes = source.duplicate().order(source.order());
        int base = bytes.position();
        if(bytes.remaining() < DefaultVertexFormat.NEW_ENTITY.getVertexSize())
            return "<short-buffer:" + bytes.remaining() + ">";

        return String.format(Locale.ROOT,
                "pos=(%.3f,%.3f,%.3f),color=%08x,uv=(%.4f,%.4f),overlay=%08x,light=%08x,normal=%08x",
                bytes.getFloat(base), bytes.getFloat(base + 4), bytes.getFloat(base + 8),
                bytes.getInt(base + 12), bytes.getFloat(base + 16), bytes.getFloat(base + 20),
                bytes.getInt(base + 24), bytes.getInt(base + 28), bytes.getInt(base + 32));
    }

    /**
     * EffectInstance and converted legacy ShaderInstance both use
     * BufferUploader.draw after explicitly applying a shader. OpenGL would have
     * consumed their named sampler maps during apply(); Vulkan carries that
     * state explicitly and records the manual draw here instead.
     */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$drawManualPipeline(BufferBuilder.RenderedBuffer buffer, CallbackInfo ci) {
        GraphicsPipeline pipeline = EffectRenderState.getActivePipeline();
        boolean effectPipeline = pipeline != null;
        if(!effectPipeline) {
            pipeline = ShaderRenderState.getActivePipeline();
        }
        if(pipeline == null)
            return;

        RenderSystem.assertOnRenderThread();
        buffer.release();

        BufferBuilder.DrawState parameters = buffer.drawState();
        if(parameters.vertexCount() > 0) {
            if(effectPipeline) {
                EffectRenderState.prepareTextures();
            } else {
                ShaderRenderState.prepareTextures();
            }

            Renderer renderer = Renderer.getInstance();
            GraphicsPipeline.requestPrimitiveMode(parameters.mode());
            renderer.bindGraphicsPipeline(pipeline);
            renderer.uploadAndBindUBOs(pipeline);
            Renderer.getDrawer().draw(buffer.vertexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
        }

        ci.cancel();
    }
}
