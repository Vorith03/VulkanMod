package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostPass;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkViewport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

import static org.lwjgl.vulkan.VK10.vkCmdSetViewport;

/**
 * Adapts vanilla's fullscreen PostPass draw to Vulkan's viewport/winding rules.
 *
 * <p>Vanilla's OpenGL path can draw the post quad with the ordinary viewport
 * and inherited cull state. VulkanMod normally uses a negative-height viewport
 * to match Minecraft's framebuffer coordinates, which reverses the fullscreen
 * quad's winding. If culling remains enabled, the whole post pass is discarded
 * before fragment shading. The original 1.20.x Vulkan implementation handled
 * this explicitly; keep that state translation here while the target/layout
 * plumbing remains owned by RenderTargetManager.</p>
 */
@Mixin(PostPass.class)
public class PostPassM {

    @Shadow @Final public RenderTarget inTarget;
    @Shadow @Final public RenderTarget outTarget;
    @Shadow @Final private EffectInstance effect;
    @Shadow @Final private List<IntSupplier> auxAssets;
    @Shadow @Final private List<String> auxNames;
    @Shadow @Final private List<Integer> auxWidths;
    @Shadow @Final private List<Integer> auxHeights;
    @Shadow private Matrix4f shaderOrthoMatrix;

    /**
     * @author VulkanMod contributors
     * @reason Establish Vulkan-correct fullscreen post-pass state.
     */
    @Overwrite
    public void process(float partialTicks) {
        this.inTarget.unbindWrite();
        float outWidth = (float)this.outTarget.width;
        float outHeight = (float)this.outTarget.height;
        RenderSystem.viewport(0, 0, (int)outWidth, (int)outHeight);

        Objects.requireNonNull(this.inTarget);
        this.effect.setSampler("DiffuseSampler", this.inTarget::getColorTextureId);

        // MainTarget is the rotating swapchain image rather than a fixed
        // off-screen framebuffer. Explicitly resolve/transition the acquired
        // image before it is sampled by the effect descriptor.
        if(this.inTarget instanceof MainTarget)
            this.inTarget.bindRead();

        for(int i = 0; i < this.auxAssets.size(); ++i) {
            this.effect.setSampler(this.auxNames.get(i), this.auxAssets.get(i));
            this.effect.safeGetUniform("AuxSize" + i).set(
                    (float)this.auxWidths.get(i), (float)this.auxHeights.get(i));
        }

        this.effect.safeGetUniform("ProjMat").set(this.shaderOrthoMatrix);
        this.effect.safeGetUniform("InSize").set((float)this.inTarget.width, (float)this.inTarget.height);
        this.effect.safeGetUniform("OutSize").set(outWidth, outHeight);
        this.effect.safeGetUniform("Time").set(partialTicks);
        Minecraft minecraft = Minecraft.getInstance();
        this.effect.safeGetUniform("ScreenSize").set(
                (float)minecraft.getWindow().getWidth(), (float)minecraft.getWindow().getHeight());

        this.outTarget.clear(Minecraft.ON_OSX);
        this.outTarget.bindWrite(false);

        boolean cullWasEnabled = VRenderSystem.cull;
        VRenderSystem.disableCull();
        RenderSystem.depthFunc(519);

        // PostPass's orthographic projection already has the screen-space
        // orientation expected by the shader. Use a positive Vulkan viewport
        // directly instead of passing a negative height through Renderer.setViewport:
        // that helper also applies the supplied height to VkRect2D.extent, where
        // negative extents are invalid. The output pass already owns the correct
        // full-target scissor, so restore it after changing only the viewport.
        vulkanmod$setUprightViewport(this.outTarget.width, this.outTarget.height);

        this.effect.apply();

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.vertex(0.0, 0.0, 500.0).endVertex();
        bufferBuilder.vertex(outWidth, 0.0, 500.0).endVertex();
        bufferBuilder.vertex(outWidth, outHeight, 500.0).endVertex();
        bufferBuilder.vertex(0.0, outHeight, 500.0).endVertex();
        BufferUploader.draw(bufferBuilder.end());

        RenderSystem.depthFunc(515);
        this.effect.clear();
        this.outTarget.unbindWrite();
        this.inTarget.unbindRead();

        if(cullWasEnabled)
            VRenderSystem.enableCull();
        else
            VRenderSystem.disableCull();
    }

    private static void vulkanmod$setUprightViewport(int width, int height) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.x(0.0f);
            viewport.y(0.0f);
            viewport.width((float)width);
            viewport.height((float)height);
            viewport.minDepth(0.0f);
            viewport.maxDepth(1.0f);
            vkCmdSetViewport(Renderer.getCommandBuffer(), 0, viewport);
        }

        Renderer.resetScissor();
    }
}
