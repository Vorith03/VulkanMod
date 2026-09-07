package net.vulkanmod.mixin;

import net.vulkanmod.config.Config;
import net.vulkanmod.config.VideoResolution;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {

    private static final String POST_CHAIN_SMOKE_MIXIN =
            "net.vulkanmod.mixin.render.GameRendererPostChainSmokeMixin";
    private static Config config;

    @Override
    public void onLoad(String mixinPackage) {
        config = Config.load(new File("./config/vulkanmod_settings.json").toPath().toAbsolutePath());
        if(config == null) config = new Config();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {

        if(POST_CHAIN_SMOKE_MIXIN.equals(mixinClassName)
                && !Boolean.getBoolean("vulkanmod.ciPostChainSmoke")) {
            return false;
        }

        if(mixinClassName.startsWith("net.vulkanmod.mixin.gui") && !config.guiOptimizations) {
            return false;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
