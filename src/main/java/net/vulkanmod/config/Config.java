package net.vulkanmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.vulkanmod.Initializer;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Config {

    public int frameQueueSize = 2;
    public VideoResolution resolution = VideoResolution.getFirstAvailable();
    public boolean windowedFullscreen = false;
    public boolean guiOptimizations = false;
    public int advCulling = 2;
    public boolean indirectDraw = false;
    public boolean regionBatching = true;
    public boolean experimentalGpuTerrain = false;
    public boolean uniqueOpaqueLayer = true;
    public boolean entityCulling = true;

    private static Path path;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static Config load(Path path) {
        Config.path = path.toAbsolutePath();

        if(!Files.exists(Config.path) || Files.isDirectory(Config.path))
            return null;

        try(FileReader fileReader = new FileReader(Config.path.toFile())) {
            return GSON.fromJson(fileReader, Config.class);
        } catch(JsonParseException malformed) {
            Path backup = Config.path.resolveSibling(Config.path.getFileName() + ".broken");
            try {
                Files.move(Config.path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch(IOException backupFailure) {
                throw new RuntimeException(
                        "Malformed VulkanMod config could not be quarantined: " + Config.path,
                        backupFailure);
            }

            Initializer.LOGGER.warn(
                    "Malformed VulkanMod config moved to {}; recreating defaults",
                    backup, malformed);
            return null;
        } catch(IOException ioFailure) {
            throw new RuntimeException(
                    "Failed to read VulkanMod config " + Config.path, ioFailure);
        }
    }

    public void write() {
        if(path == null)
            throw new IllegalStateException("VulkanMod config path has not been initialized");

        Path target = path.toAbsolutePath();
        Path parent = target.getParent();
        if(parent == null)
            throw new IllegalStateException("VulkanMod config path has no parent: " + target);

        try {
            Files.createDirectories(parent);
        } catch(IOException e) {
            throw new RuntimeException("Failed to create VulkanMod config directory", e);
        }

        Path temp = null;
        try {
            temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            Files.writeString(
                    temp, GSON.toJson(this), StandardCharsets.UTF_8);

            try {
                Files.move(
                        temp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch(AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch(IOException e) {
            throw new RuntimeException("Failed to write VulkanMod config", e);
        } finally {
            if(temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch(IOException cleanupFailure) {
                    Initializer.LOGGER.warn(
                            "Failed to delete temporary VulkanMod config {}", temp,
                            cleanupFailure);
                }
            }
        }
    }
}
