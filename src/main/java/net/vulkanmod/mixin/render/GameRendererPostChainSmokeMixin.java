package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
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
 * <p>The regular Vulkan smoke exits from the Minecraft constructor before the
 * asynchronous resource reload completes. This separate mode waits until
 * GameRenderer.reloadShaders(), where the vanilla resource stack is available,
 * and constructs a real built-in post chain without attempting to render a
 * frame from inside the reload callback.</p>
 */
@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class GameRendererPostChainSmokeMixin {
    private static final String POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciPostChainSmoke";
    private static final ResourceLocation CREEPER_POST_CHAIN = new ResourceLocation("shaders/post/creeper.json");

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "reloadShaders", at = @At("HEAD"))
    private void vulkanmod$constructVanillaPostChain(ResourceProvider provider, CallbackInfo ci) {
        if (!Boolean.getBoolean(POST_CHAIN_SMOKE_PROPERTY)) {
            return;
        }

        RenderTarget mainTarget = this.minecraft.getMainRenderTarget();

        try (PostChain chain = new PostChain(
                this.minecraft.getTextureManager(),
                this.minecraft.getResourceManager(),
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
