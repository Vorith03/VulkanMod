package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CI-only integration smoke for Minecraft's real post-processing path.
 *
 * <p>The ordinary Vulkan smoke already proves constructor-return is a fast,
 * deterministic point where the Vulkan renderer and MainTarget exist. Keep the
 * post-chain probes there as separate modes, but fail immediately if vanilla's
 * built-in assets are not mounted yet instead of waiting indefinitely for a
 * later asynchronous resource-reload callback.</p>
 */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class GameRendererPostChainSmokeMixin {
    private static final String POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciPostChainSmoke";
    private static final String DEPTH_POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciDepthPostChainSmoke";
    private static final ResourceLocation CREEPER_POST_CHAIN = new ResourceLocation("shaders/post/creeper.json");
    private static final ResourceLocation TRANSPARENCY_POST_CHAIN = new ResourceLocation("shaders/post/transparency.json");

    @Shadow @Final private ReloadableResourceManager resourceManager;
    @Shadow @Final private TextureManager textureManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vulkanmod$executeVanillaPostChain(GameConfig gameConfig, CallbackInfo ci) {
        boolean depthSmoke = Boolean.getBoolean(DEPTH_POST_CHAIN_SMOKE_PROPERTY);
        boolean colorSmoke = Boolean.getBoolean(POST_CHAIN_SMOKE_PROPERTY);
        if (!depthSmoke && !colorSmoke) {
            return;
        }

        ResourceLocation postChain = depthSmoke ? TRANSPARENCY_POST_CHAIN : CREEPER_POST_CHAIN;
        String smokeName = depthSmoke ? "depth post-chain" : "post-chain";

        if (this.resourceManager.getResource(postChain).isEmpty()) {
            Initializer.LOGGER.error(
                    "Vulkan vanilla {} execution smoke cannot run: {} is not mounted at Minecraft constructor return",
                    smokeName, postChain);
            System.exit(1);
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        RenderTarget mainTarget = minecraft.getMainRenderTarget();

        try (PostChain chain = new PostChain(
                this.textureManager,
                this.resourceManager,
                mainTarget,
                postChain)) {
            chain.resize(mainTarget.width, mainTarget.height);

            Renderer renderer = Renderer.getInstance();
            renderer.resetBuffers();
            renderer.beginFrame();
            chain.process(0.0F);
            renderer.endFrame();
            Vulkan.waitIdle();

            Initializer.LOGGER.info("Vulkan vanilla {} execution smoke passed: {}", smokeName, chain.getName());
        } catch (Throwable throwable) {
            Initializer.LOGGER.error("Vulkan vanilla {} execution smoke failed", smokeName, throwable);
            System.exit(1);
            return;
        }

        System.exit(0);
    }
}
