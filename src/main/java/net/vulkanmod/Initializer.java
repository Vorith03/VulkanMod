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
    public static Config CONFIG;

    public Initializer() {
        initialize();
    }

    /**
     * Forge normally constructs mods before Minecraft creates its Window when
     * earlyWindowControl is disabled. Keep this method idempotent so mixins can
     * safely request configuration even if loader ordering changes.
     */
    public static synchronized void initialize() {
        if (CONFIG != null) {
            return;
        }

        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
                VERSION = container.getModInfo().getVersion().toString());

        LOGGER.info("== VulkanMod Forge ==");

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
