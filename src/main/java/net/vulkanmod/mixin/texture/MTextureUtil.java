package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(TextureUtil.class)
public class MTextureUtil {

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static int generateTextureId() {
        return GlStateManager._genTexture();
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void prepareImage(NativeImage.InternalGlFormat internalGlFormat, int id, int maxMipLevel, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlTexture.bindTexture(id);

        int mipLevels = maxMipLevel + 1;
        VulkanImage image = GlTexture.getVulkanImage(id);
        if (image == null || image.width != width || image.height != height || image.mipLevels != mipLevels) {
            if (image != null) {
                image.free();
            }

            image = new VulkanImage.Builder(width, height)
                    .setFormat(internalGlFormat)
                    .setMipLevels(mipLevels)
                    .createVulkanImage();
            GlTexture.setVulkanImage(id, image);
        }

        VTextureSelector.bindTexture(image);
    }
}
