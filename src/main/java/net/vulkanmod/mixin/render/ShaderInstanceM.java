package net.vulkanmod.mixin.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.shader.EffectUniformBindings;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.ShaderRenderState;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.Field;
import net.vulkanmod.vulkan.shader.parser.GlslConverter;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ShaderInstance.class)
public class ShaderInstanceM implements ShaderMixed {

    @Shadow @Final private Map<String, Object> samplerMap;
    @Shadow @Final private Map<String, Uniform> uniformMap;
    @Shadow @Final private List<Uniform> uniforms;

    @Shadow @Final @Nullable public Uniform MODEL_VIEW_MATRIX;
    @Shadow @Final @Nullable public Uniform PROJECTION_MATRIX;
    @Shadow @Final @Nullable public Uniform COLOR_MODULATOR;
    @Shadow @Final @Nullable public Uniform LINE_WIDTH;
    private GraphicsPipeline pipeline;
    private final EffectUniformBindings vulkanmod$uniformBindings = new EffectUniformBindings();
    boolean isLegacy = false;


    public GraphicsPipeline getPipeline() {
        return pipeline;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void create(ResourceProvider resourceProvider, String name, VertexFormat format, CallbackInfo ci) {
        if(Pipeline.class.getResourceAsStream("/assets/vulkanmod/shaders/minecraft/core/" + name + ".json") == null) {
            createLegacyShader(resourceProvider, new ResourceLocation("shaders/core/" + name + ".json"), format);
            return;
        }

        String path = "minecraft/core/" + name;
        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(format, path);
        pipelineBuilder.parseBindingsJSON();
        pipelineBuilder.compileShaders();
        this.pipeline = pipelineBuilder.createGraphicsPipeline();
    }

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true)
    private static void loadProgram(ResourceProvider factory, Program.Type type, String name, CallbackInfoReturnable<Program> cir) {
        cir.setReturnValue(null);
        cir.cancel();
    }

    // Forge may transform ShaderInstance's constructor before application. If the
    // vanilla OpenGL attribute-binding call remains, suppress it; if Forge has
    // already removed/replaced it there is nothing VulkanMod needs to do here.
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;glBindAttribLocation(IILjava/lang/CharSequence;)V"), require = 0)
    private void bindAttr(int program, int index, CharSequence name) {}

    /**
     * @author
     */
    @Overwrite
    public void close() {
        ShaderRenderState.clear(this.pipeline);
        if(this.pipeline != null) {
            this.pipeline.cleanUp();
            this.pipeline = null;
        }
        this.vulkanmod$uniformBindings.close();
    }

    /**
     * @author
     */
    @Overwrite
    public void apply() {
        RenderSystem.setShader(() -> (ShaderInstance)(Object)this);

        if(this.isLegacy) {
            if (this.MODEL_VIEW_MATRIX != null) {
                this.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
            }

            if (this.PROJECTION_MATRIX != null) {
                this.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
            }

            if (this.COLOR_MODULATOR != null) {
                this.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
            }

//            if (shaderInstance.SCREEN_SIZE != null) {
//                Window window = Minecraft.getInstance().getWindow();
//                shaderInstance.SCREEN_SIZE.set((float)window.getWidth(), (float)window.getHeight());
//            }

//            if (this.LINE_WIDTH != null) {
//                this.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
//            }

            // Mod shaders commonly bind RenderTarget, AbstractTexture, or direct
            // texture ids under arbitrary JSON sampler names and then use
            // BufferUploader.draw(). Preserve those vanilla sampler objects for
            // Vulkan resolution while core shaders keep the fixed SamplerN path.
            ShaderRenderState.activate(this.pipeline, this.samplerMap);
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void clear() {
        ShaderRenderState.clear(this.pipeline);
    }

    private void createLegacyShader(ResourceProvider resourceProvider, ResourceLocation location, VertexFormat format) {
        try (Reader reader = resourceProvider.openAsReader(location)) {
            JsonObject jsonObject = GsonHelper.parse(reader);

            String vertexName = GsonHelper.getAsString(jsonObject, "vertex");
            String fragmentName = GsonHelper.getAsString(jsonObject, "fragment");

            String vshSrc;
            Resource vertexResource = resourceProvider.getResourceOrThrow(new ResourceLocation("shaders/core/" + vertexName + ".vsh"));
            try (InputStream inputStream = vertexResource.open()) {
                vshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }

            String fshSrc;
            Resource fragmentResource = resourceProvider.getResourceOrThrow(new ResourceLocation("shaders/core/" + fragmentName + ".fsh"));
            try (InputStream inputStream = fragmentResource.open()) {
                fshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }

            GlslConverter converter = new GlslConverter();
            Pipeline.Builder builder = new Pipeline.Builder(format);

            // Legacy/mod core shaders can contain source uniforms that have no
            // JSON-managed Uniform object (for example because OpenGL would link
            // them away). Defer global binding during conversion and then use the
            // same zero-backed fallback semantics as EffectInstance.
            try (Field.DefaultSupplierBindingScope ignored = Field.deferDefaultSupplierBinding()) {
                converter.process(format, vshSrc, fshSrc);
            }
            UBO ubo = converter.getUBO();

            // Other renderer mods may add Uniform objects dynamically during
            // ShaderInstance.updateLocations() instead of declaring them in the
            // shader JSON. Include those objects when resolving converted GLSL
            // fields so their live values are not replaced by zero fallbacks.
            Map<String, Uniform> bindingUniforms = new HashMap<>(this.uniformMap);
            for(Uniform uniform : this.uniforms) {
                bindingUniforms.putIfAbsent(uniform.getName(), uniform);
            }
            this.vulkanmod$uniformBindings.bind(ubo, bindingUniforms);

            builder.setUniforms(Collections.singletonList(ubo), converter.getSamplerList());
            builder.compileShaders(converter.getVshConverted(), converter.getFshConverted());

            this.pipeline = builder.createGraphicsPipeline();
            this.isLegacy = true;

        } catch (Throwable throwable) {
            this.vulkanmod$uniformBindings.close();
            throwable.printStackTrace();
        }
    }
}
