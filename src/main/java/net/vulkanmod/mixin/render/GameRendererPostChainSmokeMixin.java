package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CI-only integration smoke for Minecraft's real post-processing loader.
 *
 * <p>The ordinary Vulkan smoke already proves constructor-return is a fast,
 * deterministic point where the Vulkan renderer and MainTarget exist. Keep the
 * post-chain probe there as a separate mode, but fail immediately if vanilla's
 * built-in assets are not mounted yet instead of waiting indefinitely for a
 * later asynchronous resource-reload callback.</p>
 */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class GameRendererPostChainSmokeMixin {
    private static final String POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciPostChainSmoke";
    private static final ResourceLocation CREEPER_POST_CHAIN = new ResourceLocation("shaders/post/creeper.json");

    @Shadow @Final private ReloadableResourceManager resourceManager;
    @Shadow @Final private TextureManager textureManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vulkanmod$constructVanillaPostChain(GameConfig gameConfig, CallbackInfo ci) {
        if (!Boolean.getBoolean(POST_CHAIN_SMOKE_PROPERTY)) {
            return;
        }

        if (this.resourceManager.getResource(CREEPER_POST_CHAIN).isEmpty()) {
            Initializer.LOGGER.error(
                    "Vulkan vanilla post-chain construction smoke cannot run: {} is not mounted at Minecraft constructor return",
                    CREEPER_POST_CHAIN);
            System.exit(1);
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        RenderTarget mainTarget = minecraft.getMainRenderTarget();

        try (PostChain chain = new PostChain(
                this.textureManager,
                this.resourceManager,
                mainTarget,
                CREEPER_POST_CHAIN)) {
            chain.resize(mainTarget.width, mainTarget.height);
            Initializer.LOGGER.info("Vulkan vanilla post-chain construction smoke passed: {}", chain.getName());
        } catch (Throwable throwable) {
            Initializer.LOGGER.error("Vulkan vanilla post-chain construction smoke failed", throwable);
            System.exit(1);
            return;
        }

        System.exit(0);
    }
}
