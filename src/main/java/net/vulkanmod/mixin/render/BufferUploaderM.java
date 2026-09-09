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
import net.vulkanmod.vulkan.shader.EffectRenderState;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public class BufferUploaderM {
    private static boolean vulkanmod$warnedMissingNewEntityShader;

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
        GraphicsPipeline.requestPrimitiveMode(parameters.mode());
        renderer.bindGraphicsPipeline(pipeline);
        renderer.uploadAndBindUBOs(pipeline);
        Renderer.getDrawer().draw(buffer.vertexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
    }

    /**
     * PostPass uses BufferUploader.draw after EffectInstance.apply(), not the
     * RenderSystem ShaderInstance path used by drawWithShader. Intercept only
     * while a Vulkan effect pipeline is active so unrelated manual-buffer draws
     * retain their existing behavior.
     */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$drawEffect(BufferBuilder.RenderedBuffer buffer, CallbackInfo ci) {
        GraphicsPipeline pipeline = EffectRenderState.getActivePipeline();
        if(pipeline == null)
            return;

        RenderSystem.assertOnRenderThread();
        buffer.release();

        BufferBuilder.DrawState parameters = buffer.drawState();
        if(parameters.vertexCount() > 0) {
            EffectRenderState.prepareTextures();
            Renderer renderer = Renderer.getInstance();
            GraphicsPipeline.requestPrimitiveMode(parameters.mode());
            renderer.bindGraphicsPipeline(pipeline);
            renderer.uploadAndBindUBOs(pipeline);
            Renderer.getDrawer().draw(buffer.vertexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
        }

        ci.cancel();
    }
}
