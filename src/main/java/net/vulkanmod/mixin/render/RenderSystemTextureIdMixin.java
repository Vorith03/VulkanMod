package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors RenderSystem's integer texture-id binding path into VulkanMod's
 * texture selector. Vanilla keeps only the synthetic GL name in
 * RenderSystem.shaderTextures; under VulkanMod there is no later OpenGL shader
 * apply step that can turn that name into the backing Vulkan image.
 *
 * <p>Several mods use this overload after obtaining an AbstractTexture id
 * directly. FTB Library's ImageIcon path is one such caller. Leaving the
 * selector untouched makes the subsequent draw sample whatever texture was
 * previously bound, producing unrelated icons/glyphs instead of the requested
 * image.</p>
 */
@Mixin(RenderSystem.class)
public abstract class RenderSystemTextureIdMixin {
    @Shadow @Final private static int[] shaderTextures;

    @Inject(method = "_setShaderTexture(II)V", at = @At("TAIL"))
    private static void vulkanmod$bindShaderTextureId(int slot, int textureId, CallbackInfo ci) {
        if (slot < 0 || slot >= shaderTextures.length) {
            return;
        }

        // Object name 0 is the GL unbound/default texture. Unknown synthetic
        // names also resolve to null, which deliberately clears VulkanMod's
        // selector instead of retaining and sampling a stale texture.
        VTextureSelector.bindTexture(slot,
                textureId == 0 ? null : GlTexture.getVulkanImage(textureId));
    }
}
