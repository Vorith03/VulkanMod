package net.vulkanmod;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.VideoResolution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

@Mod(Initializer.MOD_ID)
public class Initializer {
    public static final String MOD_ID = "vulkanmod";
    public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

    private static String VERSION = "unknown";
    private static volatile long FORGE_EARLY_WINDOW;
    public static Config CONFIG;

    public Initializer() {
        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
                VERSION = container.getModInfo().getVersion().toString());
        LOGGER.info("== VulkanMod Forge ==");
    }

    /**
     * VulkanMod's video-mode discovery touches GLFW and must run on Minecraft's
     * render thread. Forge constructs @Mod classes on a loading worker, so the
     * Window mixin invokes this immediately before the real game window is
     * created instead of doing it from the mod constructor.
     */
    public static synchronized void initialize() {
        if (CONFIG != null) {
            return;
        }

        VideoResolution.init();

        Path configPath = FMLPaths.CONFIGDIR.get().resolve("vulkanmod_settings.json");
        CONFIG = loadConfig(configPath);
    }

    public static Config getConfig() {
        if (CONFIG == null) {
            initialize();
        }
        return CONFIG;
    }

    public static void retainForgeEarlyWindow(long window) {
        FORGE_EARLY_WINDOW = window;
    }

    public static long takeForgeEarlyWindow() {
        long window = FORGE_EARLY_WINDOW;
        FORGE_EARLY_WINDOW = 0L;
        return window;
    }

    private static Config loadConfig(Path path) {
        Config config = Config.load(path);
        if (config == null) {
            config = new Config();
            config.write();
        }
        return config;
    }

    public static String getVersion() {
        return VERSION;
    }
}
