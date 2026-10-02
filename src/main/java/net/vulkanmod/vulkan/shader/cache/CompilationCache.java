package net.vulkanmod.vulkan.shader.cache;

import net.minecraftforge.fml.loading.FMLPaths;
import net.vulkanmod.Initializer;
import org.lwjgl.util.shaderc.Shaderc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompilationCache {
    public static final int MAX_PIPELINE_BYTES = 32 * 1024 * 1024;
    private static final BoundedDiskCache SHADERS = new BoundedDiskCache(
            root().resolve("spirv-v1"), 4 * 1024 * 1024, 64L * 1024 * 1024, 2048);
    private static final BoundedDiskCache PIPELINES = new BoundedDiskCache(
            root().resolve("pipelines-v1"), MAX_PIPELINE_BYTES, 64L * 1024 * 1024, 16);
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static boolean fingerprintAttempted;
    private static byte[] compilerFingerprint;
    private static long hits, misses, writes;

    private CompilationCache() {}
    private static Path root() { return FMLPaths.GAMEDIR.get().resolve("cache/vulkanmod"); }
    public static boolean enabled() {
        return Initializer.CONFIG != null && Initializer.CONFIG.persistentCompilationCache;
    }

    public static synchronized byte[] shaderKey(String filename, String source, int stage,
                                               boolean debug, boolean optimize) {
        if(!enabled()) return null;
        if(!fingerprintAttempted) {
            fingerprintAttempted = true;
            String library = Shaderc.getLibrary().getPath();
            if(library != null) {
                try(var input = Files.newInputStream(Path.of(library))) {
                    MessageDigest hash = BoundedDiskCache.sha256();
                    byte[] block = new byte[16384];
                    int read;
                    while((read = input.read(block)) != -1) hash.update(block, 0, read);
                    compilerFingerprint = hash.digest();
                } catch(IOException | RuntimeException failure) { warn(failure); }
            }
        }
        // Unidentified native compiler: never reuse results across unknown toolchains.
        if(compilerFingerprint == null) return null;
        MessageDigest hash = BoundedDiskCache.sha256();
        hash.update(compilerFingerprint);
        for(String field : new String[] {"shaderc-options-v1", filename, source,
                Integer.toString(stage), Boolean.toString(debug), Boolean.toString(optimize), "main"}) {
            byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
            hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
            hash.update(bytes);
        }
        return hash.digest();
    }

    public static synchronized byte[] readShader(byte[] key) {
        if(key == null) return null;
        byte[] bytes = read(SHADERS, key);
        if(bytes == null || bytes.length < 20 || (bytes.length & 3) != 0
                || ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt() != 0x07230203) {
            misses++;
            return null;
        }
        hits++;
        return bytes;
    }

    public static synchronized void writeShader(byte[] key, ByteBuffer code) {
        if(key == null) return;
        ByteBuffer copy = code.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        if(write(SHADERS, key, bytes)) writes++;
    }
    public static byte[] readPipeline(byte[] key) { return enabled() ? read(PIPELINES, key) : null; }
    public static void writePipeline(byte[] key, byte[] bytes) {
        if(enabled()) write(PIPELINES, key, bytes);
    }
    private static byte[] read(BoundedDiskCache cache, byte[] key) {
        try { return cache.read(key); }
        catch(IOException | RuntimeException failure) { warn(failure); return null; }
    }
    private static boolean write(BoundedDiskCache cache, byte[] key, byte[] bytes) {
        try { return cache.write(key, bytes); }
        catch(IOException | RuntimeException failure) { warn(failure); return false; }
    }
    private static void warn(Exception failure) {
        if(WARNED.compareAndSet(false, true))
            Initializer.LOGGER.warn("Compilation cache unavailable; using normal compilation", failure);
    }
    public static synchronized void report() {
        Initializer.LOGGER.info("SPIR-V cache: hits={}, misses={}, writes={}", hits, misses, writes);
    }
    public static synchronized long hits() { return hits; }
}
