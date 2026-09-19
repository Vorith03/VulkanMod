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

    public static synchronized SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {

        if(compiler == 0) compiler = shaderc_compiler_initialize();

        if(compiler == NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }

        long options = shaderc_compile_options_initialize();
        if(options == NULL) {
            throw new RuntimeException("Failed to create compiler options");
        }

        long result = NULL;
        boolean resultTransferred = false;
        try {
            if(OPTIMIZATIONS)
                shaderc_compile_options_set_optimization_level(
                        options, shaderc_optimization_level_performance);

            if(DEBUG)
                shaderc_compile_options_set_generate_debug_info(options);

            result = shaderc_compile_into_spv(
                    compiler, source, shaderKind.kind, filename, "main", options);
            if(result == NULL) {
                throw new RuntimeException(
                        "Failed to compile shader " + filename + " into SPIR-V");
            }

            if(shaderc_result_get_compilation_status(result)
                    != shaderc_compilation_status_success) {
                throw new RuntimeException(
                        "Failed to compile shader " + filename + " into SPIR-V:\n"
                                + shaderc_result_get_error_message(result));
            }

            SPIRV spirv = SPIRV.shadercResult(result, shaderc_result_get_bytes(result));
            resultTransferred = true;
            return spirv;
        } finally {
            if(result != NULL && !resultTransferred)
                shaderc_result_release(result);
            shaderc_compile_options_release(options);
        }
    }

    private static SPIRV readFromStream(InputStream inputStream) {
        ByteBuffer buffer = null;
        boolean transferred = false;
        try {
            byte[] bytes = inputStream.readAllBytes();
            buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            SPIRV spirv = SPIRV.nativeBuffer(buffer);
            transferred = true;
            return spirv;
        } catch(IOException failure) {
            throw new RuntimeException("Unable to read SPIR-V input stream", failure);
        } finally {
            if(buffer != null && !transferred)
                MemoryUtil.memFree(buffer);
        }
    }

    public static synchronized void destroyCompiler() {
        if(compiler == NULL)
            return;

        shaderc_compiler_release(compiler);
        compiler = NULL;
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
        private enum Origin {
            SHADERC_RESULT,
            NATIVE_BUFFER
        }

        private final long handle;
        private final Origin origin;
        private ByteBuffer bytecode;
        private boolean freed;

        private SPIRV(long handle, ByteBuffer bytecode, Origin origin) {
            this.handle = handle;
            this.bytecode = bytecode;
            this.origin = origin;
        }

        static SPIRV shadercResult(long handle, ByteBuffer bytecode) {
            return new SPIRV(handle, bytecode, Origin.SHADERC_RESULT);
        }

        static SPIRV nativeBuffer(ByteBuffer bytecode) {
            return new SPIRV(NULL, bytecode, Origin.NATIVE_BUFFER);
        }

        public synchronized ByteBuffer bytecode() {
            if(this.freed || this.bytecode == null)
                throw new IllegalStateException("SPIR-V bytecode has already been released");
            return this.bytecode;
        }

        @Override
        public synchronized void free() {
            if(this.freed)
                return;

            if(this.origin == Origin.SHADERC_RESULT) {
                shaderc_result_release(this.handle);
            } else if(this.bytecode != null) {
                MemoryUtil.memFree(this.bytecode);
            }

            this.bytecode = null;
            this.freed = true;
        }
    }
}
