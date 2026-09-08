package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.shaders.EffectProgram;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.EffectRenderState;
import net.vulkanmod.vulkan.shader.EffectUniformBindings;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.Field;
import net.vulkanmod.vulkan.shader.parser.GlslConverter;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

@Mixin(EffectInstance.class)
public class EffectInstanceM {

    @Shadow @Final private Map<String, IntSupplier> samplerMap;
    @Shadow @Final private Map<String, Uniform> uniformMap;
    @Shadow @Final private List<Uniform> uniforms;

    private GraphicsPipeline pipeline;
    private String vulkanmod$vertexShader;
    private String vulkanmod$fragmentShader;
    private final EffectUniformBindings vulkanmod$uniformBindings = new EffectUniformBindings();

    /**
     * Mixin 0.8.5 only permits callback injection at a constructor's safe return
     * point. Capture the shader names in the existing getOrCreate redirect, then
     * build the Vulkan pipeline once EffectInstance construction has completed.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void vulkanmod$createShadersAfterInit(ResourceManager resourceManager, String string, CallbackInfo ci) {
        if(this.vulkanmod$vertexShader == null || this.vulkanmod$fragmentShader == null) {
            throw new IllegalStateException("EffectInstance shader names were not captured during construction");
        }

        createShaders(resourceManager, this.vulkanmod$vertexShader, this.vulkanmod$fragmentShader);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EffectInstance;getOrCreate(Lnet/minecraft/server/packs/resources/ResourceManager;Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;)Lcom/mojang/blaze3d/shaders/EffectProgram;"))
    private EffectProgram redirectShader(ResourceManager resourceManager, Program.Type type, String string) {
        if(type == Program.Type.VERTEX) {
            this.vulkanmod$vertexShader = string;
        } else if(type == Program.Type.FRAGMENT) {
            this.vulkanmod$fragmentShader = string;
        }

        return null;
    }

    /**
     * Vanilla EffectInstance.apply still owns blend/state bookkeeping and sampler
     * supplier setup. Once it completes, expose this instance's Vulkan pipeline
     * and sampler map to BufferUploader.draw for the fullscreen post pass.
     */
    @Inject(method = "apply", at = @At("RETURN"))
    private void vulkanmod$activateEffectPipeline(CallbackInfo ci) {
        EffectRenderState.activate(this.pipeline, this.samplerMap);
    }

    /** Clear the manual Vulkan effect context after PostPass finishes drawing. */
    @Inject(method = "clear", at = @At("RETURN"))
    private void vulkanmod$clearEffectPipeline(CallbackInfo ci) {
        EffectRenderState.clear(this.pipeline);
    }

    // apply() uses RenderSystem.activeTexture, but clear() bypasses it. With
    // more than one sampler vanilla changes GL's cached unit and calls native
    // glActiveTexture even though this window has no OpenGL context. Preserve
    // vanilla's remaining clear/bookkeeping and use the same Vulkan path as apply.
    @Redirect(method = "clear", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_activeTexture(I)V", remap = false))
    private void vulkanmod$clearSamplerUnit(int texture) {
        RenderSystem.activeTexture(texture);
    }

    /**
     * @author
     * @reason VulkanMod owns a Vulkan pipeline and fallback uniform storage for
     * post-processing effects instead of an OpenGL program object.
     */
    @Overwrite
    public void close() {
        EffectRenderState.clear(this.pipeline);

        if(this.pipeline != null) {
            // Post chains can be rebuilt or closed after this pipeline has already
            // been recorded into the current primary command buffer. Mirror the
            // core ShaderInstance reload path and retire its Vulkan handles only
            // after this frame slot's fence has completed.
            GraphicsPipeline retiredPipeline = this.pipeline;
            this.pipeline = null;
            MemoryManager.getInstance().addFrameOp(retiredPipeline::cleanUp);
        }

        this.vulkanmod$uniformBindings.close();

        for (Uniform uniform : this.uniforms) {
            uniform.close();
        }
    }

    private void createShaders(ResourceManager resourceManager, String vertexShader, String fragShader) {
        try {
            String[] vshPathInfo = this.decompose(vertexShader, ':');
            ResourceLocation vshLocation = new ResourceLocation(vshPathInfo[0], "shaders/program/" + vshPathInfo[1] + ".vsh");
            Resource vshResource = resourceManager.getResourceOrThrow(vshLocation);
            String vshSrc;
            try (InputStream inputStream = vshResource.open()) {
                vshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }

            String[] fshPathInfo = this.decompose(fragShader, ':');
            ResourceLocation fshLocation = new ResourceLocation(fshPathInfo[0], "shaders/program/" + fshPathInfo[1] + ".fsh");
            Resource fshResource = resourceManager.getResourceOrThrow(fshLocation);
            String fshSrc;
            try (InputStream inputStream = fshResource.open()) {
                fshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }

            // EffectInstance owns the uniforms declared by the effect JSON and
            // fills them dynamically for each post-processing pass. GLSL source
            // may also contain uniforms that are absent from that JSON. OpenGL
            // initializes those unmanaged uniforms to zero (and can optimize them
            // away entirely), so source-level Vulkan UBO construction must not
            // require every declaration to have an EffectInstance Uniform object.
            GlslConverter converter = new GlslConverter();
            Pipeline.Builder builder = new Pipeline.Builder(DefaultVertexFormat.POSITION_TEX_COLOR);

            try (Field.DefaultSupplierBindingScope ignored = Field.deferDefaultSupplierBinding()) {
                converter.process(DefaultVertexFormat.POSITION_TEX_COLOR, vshSrc, fshSrc);
            }

            UBO ubo = converter.getUBO();
            this.vulkanmod$uniformBindings.bind(ubo, this.uniformMap);

            builder.setUniforms(Collections.singletonList(ubo), converter.getSamplerList());
            builder.compileShaders(converter.getVshConverted(), converter.getFshConverted());

            this.pipeline = builder.createGraphicsPipeline();
        } catch (Throwable throwable) {
            this.vulkanmod$uniformBindings.close();

            if(throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if(throwable instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(throwable);
        }
    }

    private String[] decompose(String string, char c) {
        String[] strings = new String[]{"minecraft", string};
        int i = string.indexOf(c);
        if (i >= 0) {
            strings[1] = string.substring(i + 1);
            if (i >= 1) {
                strings[0] = string.substring(0, i);
            }
        }

        return strings;
    }
}
