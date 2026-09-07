package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
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

        GraphicsPipeline pipeline = ((ShaderMixed)(RenderSystem.getShader())).getPipeline();
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
            Renderer renderer = Renderer.getInstance();
            GraphicsPipeline.requestPrimitiveMode(parameters.mode());
            renderer.bindGraphicsPipeline(pipeline);
            renderer.uploadAndBindUBOs(pipeline);
            Renderer.getDrawer().draw(buffer.vertexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
        }

        ci.cancel();
    }
}
