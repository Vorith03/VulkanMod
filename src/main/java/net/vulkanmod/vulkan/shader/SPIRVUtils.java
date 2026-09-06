package net.vulkanmod.vulkan.shader;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.shaderc.Shaderc.*;

public class SPIRVUtils {
    private static final boolean DEBUG = true;
    private static final boolean OPTIMIZATIONS = false;
    private static final String MOD_RESOURCE_ROOT = "/assets/vulkanmod/";
    private static final String SHADER_RESOURCE_ROOT = "/assets/vulkanmod/shaders";

    private static long compiler;

    public static SPIRV compileShaderAbsoluteFile(String shaderFile, ShaderKind shaderKind) {
        // Pipeline historically turns a classpath shader URL into a String and
        // passes it here. Forge/SecureJarHandler represents that URL with its
        // union: filesystem, which cannot reliably be reopened via Paths.get().
        // Recover the classpath-relative path instead; true external file URLs
        // still use the original filesystem path below.
        int resourceStart = shaderFile.indexOf(MOD_RESOURCE_ROOT);
        if (resourceStart >= 0) {
            String resourcePath = shaderFile.substring(resourceStart);

            // SecureJar union directory URLs can lose the trailing slash from
            // /assets/vulkanmod/shaders/. Pipeline then appends the shader path
            // directly, producing e.g. "shadersbasic/..." or
            // "shadersminecraft/...". Restore that classpath boundary for any
            // shader subtree instead of special-casing individual directories.
            if (resourcePath.startsWith(SHADER_RESOURCE_ROOT)
                    && resourcePath.length() > SHADER_RESOURCE_ROOT.length()
                    && resourcePath.charAt(SHADER_RESOURCE_ROOT.length()) != '/') {
                resourcePath = SHADER_RESOURCE_ROOT + "/"
                        + resourcePath.substring(SHADER_RESOURCE_ROOT.length());
            }

            return compileShaderResource(resourcePath, shaderKind);
        }

        try {
            String source = Files.readString(Paths.get(new URI(shaderFile)), StandardCharsets.UTF_8);
            return compileShader(shaderFile, source, shaderKind);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to read shader file: " + shaderFile, e);
        }
    }

    /**
     * Compile a shader stored in the mod's classpath resources.
     *
     * Forge/SecureJarHandler exposes mod resources through a union: filesystem.
     * Reading through the class loader is portable across exploded dev resources,
     * ordinary JARs, and SecureJar union filesystems.
     */
    public static SPIRV compileShaderResource(String resourcePath, ShaderKind shaderKind) {
        try (InputStream stream = SPIRVUtils.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Shader resource not found: " + resourcePath);
            }

            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return compileShader(resourcePath, source, shaderKind);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader resource: " + resourcePath, e);
        }
    }

    public static SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {

        if(compiler == 0) compiler = shaderc_compiler_initialize();

        if(compiler == NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }

        long options = shaderc_compile_options_initialize();

        if(options == NULL) {
            throw new RuntimeException("Failed to create compiler options");
        }

        if(OPTIMIZATIONS)
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        if(DEBUG)
            shaderc_compile_options_set_generate_debug_info(options);

        long result = shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", options);

        if(result == NULL) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if(shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V:\n" + shaderc_result_get_error_message(result));
        }

        return new SPIRV(result, shaderc_result_get_bytes(result));
    }

    private static SPIRV readFromStream(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.position(0);

            return new SPIRV(MemoryUtil.memAddress(buffer), buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new RuntimeException("unable to read inputStream");
    }

    public enum ShaderKind {
        VERTEX_SHADER(shaderc_glsl_vertex_shader),
        GEOMETRY_SHADER(shaderc_glsl_geometry_shader),
        FRAGMENT_SHADER(shaderc_glsl_fragment_shader),
        COMPUTE_SHADER(shaderc_glsl_compute_shader);

        private final int kind;

        ShaderKind(int kind) {
            this.kind = kind;
        }
    }

    public static final class SPIRV implements NativeResource {

        private final long handle;
        private ByteBuffer bytecode;

        public SPIRV(long handle, ByteBuffer bytecode) {
            this.handle = handle;
            this.bytecode = bytecode;
        }

        public ByteBuffer bytecode() {
            return bytecode;
        }

        @Override
        public void free() {
//            shaderc_result_release(handle);
            bytecode = null; // Help the GC
        }
    }

}
