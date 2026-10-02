package net.vulkanmod.vulkan.shader.cache;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;

public final class CompilationCacheSmokeTest {
    private static boolean complete;
    private CompilationCacheSmokeTest() {}

    public static void run() {
        if(complete || !Boolean.getBoolean("vulkanmod.smokeTest")) return;
        String source = "#version 450\nvoid main() { gl_Position = vec4(0.0, 0.0, 0.0, 1.0); }\n";
        byte[] reference;
        try(var shader = SPIRVUtils.compileShader("cache_oracle.vert", source, SPIRVUtils.ShaderKind.VERTEX_SHADER)) {
            reference = new byte[shader.bytecode().remaining()];
            shader.bytecode().duplicate().get(reference);
        }
        long hits = CompilationCache.hits();
        try(var shader = SPIRVUtils.compileShader("cache_oracle.vert", source, SPIRVUtils.ShaderKind.VERTEX_SHADER)) {
            byte[] restored = new byte[shader.bytecode().remaining()];
            shader.bytecode().duplicate().get(restored);
            if(!java.util.Arrays.equals(reference, restored) || CompilationCache.hits() != hits + 1)
                throw new IllegalStateException("SPIR-V cache did not preserve bytes or hit persisted result");
        }
        Pipeline.verifyPersistentCacheForSmoke();
        complete = true;
        Initializer.LOGGER.info("Compilation cache smoke passed (SPIR-V bytes, persisted hit, driver cache reload)");
    }
}
