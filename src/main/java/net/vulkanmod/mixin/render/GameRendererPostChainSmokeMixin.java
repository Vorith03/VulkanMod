package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.EffectRenderState;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import net.vulkanmod.vulkan.texture.ScreenshotReadbackSmokeTest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CI-only integration smoke for Minecraft's real post-processing and readback paths.
 *
 * <p>The ordinary Vulkan smoke already proves constructor-return is a fast,
 * deterministic point where the Vulkan renderer and MainTarget exist. Individual
 * probe properties remain available for focused diagnosis, while normal CI can
 * run all renderer-regression probes in one Minecraft process.</p>
 */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class GameRendererPostChainSmokeMixin {
    private static final String RENDERER_REGRESSION_SMOKE_PROPERTY = "vulkanmod.ciRendererRegressionSmoke";
    private static final String POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciPostChainSmoke";
    private static final String DEPTH_POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciDepthPostChainSmoke";
    private static final String SCREENSHOT_SMOKE_PROPERTY = "vulkanmod.ciScreenshotSmoke";
    private static final ResourceLocation CREEPER_POST_CHAIN = new ResourceLocation("shaders/post/creeper.json");
    private static final ResourceLocation TRANSPARENCY_POST_CHAIN = new ResourceLocation("shaders/post/transparency.json");

    @Shadow @Final private ReloadableResourceManager resourceManager;
    @Shadow @Final private TextureManager textureManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vulkanmod$executeVanillaPostChain(GameConfig gameConfig, CallbackInfo ci) {
        boolean combinedSmoke = Boolean.getBoolean(RENDERER_REGRESSION_SMOKE_PROPERTY);
        boolean colorSmoke = combinedSmoke || Boolean.getBoolean(POST_CHAIN_SMOKE_PROPERTY);
        boolean depthSmoke = combinedSmoke || Boolean.getBoolean(DEPTH_POST_CHAIN_SMOKE_PROPERTY);
        boolean screenshotSmoke = combinedSmoke || Boolean.getBoolean(SCREENSHOT_SMOKE_PROPERTY);
        if (!colorSmoke && !depthSmoke && !screenshotSmoke) {
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        try {
            if (colorSmoke) {
                vulkanmod$runPostChainSmoke(minecraft, false);
            }
            if (depthSmoke) {
                vulkanmod$runPostChainSmoke(minecraft, true);
            }
            if (screenshotSmoke) {
                ScreenshotReadbackSmokeTest.verify(minecraft);
            }
        } catch (Throwable failure) {
            Initializer.LOGGER.error("Vulkan renderer regression smoke failed", failure);
            System.exit(1);
            return;
        }

        System.exit(0);
    }

    @Unique
    private void vulkanmod$runPostChainSmoke(Minecraft minecraft, boolean depthSmoke) throws Exception {
        ResourceLocation postChain = depthSmoke ? TRANSPARENCY_POST_CHAIN : CREEPER_POST_CHAIN;
        String smokeName = depthSmoke ? "depth post-chain" : "post-chain";

        if (this.resourceManager.getResource(postChain).isEmpty()) {
            throw new AssertionError(
                    "Vulkan vanilla " + smokeName + " execution smoke cannot run: " + postChain
                            + " is not mounted at Minecraft constructor return");
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        try (PostChain chain = new PostChain(
                this.textureManager,
                this.resourceManager,
                mainTarget,
                postChain)) {
            chain.resize(mainTarget.width, mainTarget.height);

            Renderer renderer = Renderer.getInstance();
            long submissions = Synchronization.INSTANCE.getMainFrameSubmissionCount();
            int frames = depthSmoke ? 2 : 1;
            CompletableFuture<NativeImage> colorReadback = null;
            for (int frame = 0; frame < frames; frame++) {
                renderer.resetBuffers();
                renderer.beginFrame();
                // Repeat in the same frame as well: descriptor-set reuse must
                // still transition attachments written again between processes.
                for (int pass = 0; pass < (depthSmoke ? 2 : 1); pass++) {
                    if (depthSmoke) {
                        vulkanmod$initializeDepthInputs(chain, mainTarget);
                    } else {
                        vulkanmod$initializeCreeperInput(mainTarget);
                    }
                    chain.process(0.0F);
                    if (EffectRenderState.isActive()) {
                        throw new AssertionError("PostChain left an effect pipeline active after clear");
                    }
                }
                if (!depthSmoke) {
                    colorReadback = ScreenshotReadback.request(mainTarget);
                }
                renderer.endFrame();
                Vulkan.waitIdle();
            }
            if (Synchronization.INSTANCE.getMainFrameSubmissionCount() != submissions + frames) {
                throw new AssertionError("PostChain smoke did not submit every frame");
            }
            if (depthSmoke) {
                Initializer.LOGGER.info("Depth inputs initialized and copied; four PostChain processes submitted in two frames");
            } else {
                if (colorReadback == null) {
                    throw new AssertionError("Creeper PostChain did not schedule a pixel readback");
                }
                try (NativeImage image = colorReadback.get(10, TimeUnit.SECONDS)) {
                    vulkanmod$verifyCreeperPixel(image);
                }
                Initializer.LOGGER.info("Creeper PostChain pixel oracle passed: red input became green output");
            }

            Initializer.LOGGER.info("Vulkan vanilla {} execution smoke passed: {}", smokeName, chain.getName());
        }
    }

    @Unique
    private static void vulkanmod$initializeCreeperInput(RenderTarget mainTarget) {
        mainTarget.bindWrite(true);
        VRenderSystem.clearColor(1.0F, 0.0F, 0.0F, 1.0F);
        Renderer.clearAttachments(0x4000);
    }

    @Unique
    private static void vulkanmod$verifyCreeperPixel(NativeImage image) {
        int pixel = image.getPixelRGBA(image.getWidth() / 2, image.getHeight() / 2);
        int red = pixel & 0xFF;
        int green = pixel >>> 8 & 0xFF;
        int blue = pixel >>> 16 & 0xFF;
        int alpha = pixel >>> 24 & 0xFF;
        if (alpha != 0xFF || green < 16 || green <= red + 8 || green <= blue + 8) {
            throw new AssertionError("Creeper PostChain pixel was not green-dominant after red input: rgba="
                    + red + "," + green + "," + blue + "," + alpha);
        }
    }

    @Unique
    private static void vulkanmod$initializeDepthInputs(PostChain chain, RenderTarget mainTarget) {
        if (GlTexture.getVulkanImage(mainTarget.getDepthTextureId()) != Vulkan.getSwapChain().getDepthAttachment()) {
            throw new AssertionError("MainTarget depth supplier did not resolve the live swapchain depth image");
        }

        mainTarget.bindWrite(true);
        VRenderSystem.clearColor(0.25F, 0.5F, 0.75F, 1.0F);
        VRenderSystem.clearDepth = 0.625F;
        Renderer.clearAttachments(0x4100);

        // These targets are normally initialized by LevelRenderer. Constructor-
        // return CI has no world render, so supply defined color/depth contents.
        for (String name : new String[]{"translucent", "itemEntity", "particles", "clouds", "weather"}) {
            RenderTarget target = chain.getTempTarget(name);
            if (target == null || GlTexture.getVulkanImage(target.getDepthTextureId()) == null) {
                throw new AssertionError("Missing transparency depth target: " + name);
            }
            target.setClearColor(0.0625F, 0.125F, 0.1875F, 0.25F);
            target.clear(Minecraft.ON_OSX);
            target.copyDepthFrom(mainTarget);
        }
        // Exercise both MainTarget -> offscreen and offscreen -> offscreen copies.
        chain.getTempTarget("particles").copyDepthFrom(chain.getTempTarget("translucent"));
        mainTarget.bindWrite(true);
    }
}
