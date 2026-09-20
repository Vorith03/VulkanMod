package net.vulkanmod.mixin.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.ForgeHooksClient;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.shader.EffectUniformBindings;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.ShaderRenderState;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.Field;
import net.vulkanmod.vulkan.shader.parser.GlslConverter;
import net.vulkanmod.vulkan.util.MappedBuffer;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private boolean vulkanmod$refreshImmersivePortalsTerrainClipPlane;
    boolean isLegacy = false;


    public GraphicsPipeline getPipeline() {
        return pipeline;
    }

    @Inject(
            method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("RETURN")
    )
    private void create(ResourceProvider resourceProvider, ResourceLocation shaderLocation,
                        VertexFormat format, CallbackInfo ci) {
        String namespace = shaderLocation.getNamespace();
        String name = shaderLocation.getPath();

        // Only exact vanilla-namespaced built-ins may use VulkanMod's packaged
        // preconverted pipelines. Forge/mod namespaces must resolve their own
        // JSON/program resources through the normal namespaced resource provider.
        if(!"minecraft".equals(namespace)
                || ImmersivePortalsShaderCompat.shouldTransform(name)
                || Pipeline.class.getResourceAsStream("/assets/vulkanmod/shaders/minecraft/core/" + name + ".json") == null) {
            createLegacyShader(resourceProvider,
                    new ResourceLocation(namespace, "shaders/core/" + name + ".json"), format);
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
        this.vulkanmod$refreshImmersivePortalsTerrainClipPlane = false;
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

            if(this.vulkanmod$refreshImmersivePortalsTerrainClipPlane) {
                ImmersivePortalsShaderCompat.refreshTerrainClipPlane();
            }

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

    /**
     * Vanilla Program compilation expands #moj_import directives after mods such
     * as Immersive Portals have transformed the raw shader source. The Vulkan
     * legacy path bypasses Program.compileShaderInternal, so mirror that
     * preprocessing step before GlslConverter/shaderc sees the source.
     */
    private static String vulkanmod$preprocessCoreShader(
            ResourceProvider resourceProvider, ResourceLocation shaderResource, String source) {
        Set<ResourceLocation> imported = new HashSet<>();
        String resourcePath = shaderResource.getPath();
        int separator = resourcePath.lastIndexOf('/');
        String basePath = separator >= 0 ? resourcePath.substring(0, separator + 1) : "";

        GlslPreprocessor preprocessor = new GlslPreprocessor() {
            @Override
            public String applyImport(boolean inline, String name) {
                ResourceLocation importLocation =
                        ForgeHooksClient.getShaderImportLocation(basePath, inline, name);
                if(!imported.add(importLocation)) {
                    return "";
                }

                try {
                    Resource resource = resourceProvider.getResourceOrThrow(importLocation);
                    try (InputStream inputStream = resource.open()) {
                        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    }
                } catch(IOException e) {
                    throw new IllegalStateException(
                            "Failed to load shader include " + importLocation, e);
                }
            }
        };

        return String.join("", preprocessor.process(source));
    }

    private static ResourceLocation vulkanmod$coreProgramResource(String programName, String extension) {
        ResourceLocation programLocation = new ResourceLocation(programName);
        return new ResourceLocation(programLocation.getNamespace(),
                "shaders/core/" + programLocation.getPath() + extension);
    }

    private void createLegacyShader(ResourceProvider resourceProvider, ResourceLocation location, VertexFormat format) {
        boolean immersivePortalsClippingShader = false;
        String vertexName = "<unresolved>";
        String fragmentName = "<unresolved>";
        Resource vertexResource = null;
        Resource fragmentResource = null;

        try (Reader reader = resourceProvider.openAsReader(location)) {
            JsonObject jsonObject = GsonHelper.parse(reader);

            vertexName = GsonHelper.getAsString(jsonObject, "vertex");
            fragmentName = GsonHelper.getAsString(jsonObject, "fragment");

            String vshSrc;
            ResourceLocation vertexLocation = vulkanmod$coreProgramResource(vertexName, ".vsh");
            vertexResource = resourceProvider.getResourceOrThrow(vertexLocation);
            try (InputStream inputStream = vertexResource.open()) {
                vshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }
            immersivePortalsClippingShader = ImmersivePortalsShaderCompat.shouldTransform(vertexName);
            vshSrc = ImmersivePortalsShaderCompat.transform(Program.Type.VERTEX, vertexName, vshSrc);
            vshSrc = vulkanmod$preprocessCoreShader(resourceProvider, vertexLocation, vshSrc);
            if(immersivePortalsClippingShader
                    && (!vshSrc.contains("imm_ptl_ClippingEquation") || !vshSrc.contains("gl_ClipDistance[0]"))) {
                throw new IllegalStateException(
                        "Immersive Portals did not inject Vulkan clipping into shader " + vertexName);
            }

            String fshSrc;
            ResourceLocation fragmentLocation = vulkanmod$coreProgramResource(fragmentName, ".fsh");
            fragmentResource = resourceProvider.getResourceOrThrow(fragmentLocation);
            try (InputStream inputStream = fragmentResource.open()) {
                fshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }
            fshSrc = ImmersivePortalsShaderCompat.transform(Program.Type.FRAGMENT, fragmentName, fshSrc);
            fshSrc = vulkanmod$preprocessCoreShader(resourceProvider, fragmentLocation, fshSrc);

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
            Map<String, MappedBuffer> directBindings = Collections.emptyMap();
            if(immersivePortalsClippingShader && !bindingUniforms.containsKey("imm_ptl_ClippingEquation")) {
                // IP decides whether to create its ShaderInstance Uniform from the
                // ShaderInstance name, while Program transformation is keyed by the
                // underlying program name. Forge mods can therefore alias a vanilla
                // terrain program (Twilight Forest's red_thread -> rendertype_cutout)
                // without receiving IP's dynamic Uniform. VulkanMod already mirrors
                // the authoritative pre-model-view terrain clip equation for its
                // native terrain pipelines, so bind that same storage directly.
                // Keep every non-terrain transform fail-closed because IP uses a
                // different coordinate space for those shaders.
                if(!ImmersivePortalsShaderCompat.usesTerrainClipPlane(vertexName)) {
                    throw new IllegalStateException(
                            "Immersive Portals clipping uniform was not attached to shader " + vertexName);
                }

                directBindings = Collections.singletonMap(
                        "imm_ptl_ClippingEquation",
                        ImmersivePortalsShaderCompat.getTerrainClipPlane()
                );
                this.vulkanmod$refreshImmersivePortalsTerrainClipPlane = true;
            }
            this.vulkanmod$uniformBindings.bind(ubo, bindingUniforms, directBindings);

            builder.setUniforms(Collections.singletonList(ubo), converter.getSamplerList());
            builder.compileShaders(converter.getVshConverted(), converter.getFshConverted());

            this.pipeline = builder.createGraphicsPipeline();
            this.isLegacy = true;

            if(immersivePortalsClippingShader && this.pipeline == null) {
                throw new IllegalStateException(
                        "Immersive Portals clipping shader did not create a Vulkan pipeline: " + vertexName);
            }
            if(immersivePortalsClippingShader
                    && "rendertype_solid".equals(vertexName)
                    && Boolean.getBoolean("vulkanmod.ciImmersivePortalsSmoke")) {
                Initializer.LOGGER.info(
                        "VULKANMOD_IP_CLIPPING_SHADER_OK: rendertype_solid transformed source, live clipping uniform, Vulkan pipeline");
            }
            if(this.vulkanmod$refreshImmersivePortalsTerrainClipPlane
                    && Boolean.getBoolean("vulkanmod.ciImmersivePortalsSmoke")) {
                Initializer.LOGGER.info(
                        "VULKANMOD_IP_ALIASED_TERRAIN_CLIP_OK: {} -> {}",
                        location, vertexName
                );
            }

        } catch (Throwable throwable) {
            this.vulkanmod$uniformBindings.close();
            Initializer.LOGGER.error(
                    "Failed to build legacy Vulkan shader {} (vertex={} from {}, fragment={} from {})",
                    location,
                    vertexName,
                    vertexResource == null ? "<unresolved>" : vertexResource.sourcePackId(),
                    fragmentName,
                    fragmentResource == null ? "<unresolved>" : fragmentResource.sourcePackId(),
                    throwable
            );

            // A transformed IP shader without a usable Vulkan pipeline renders
            // portal geometry with incorrect clipping. Do not silently fall back
            // to a null pipeline for this compatibility path.
            if(immersivePortalsClippingShader) {
                if(throwable instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if(throwable instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Failed to build Immersive Portals clipping shader", throwable);
            }

        }
    }
}
