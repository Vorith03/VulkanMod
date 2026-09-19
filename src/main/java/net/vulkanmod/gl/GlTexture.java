package net.vulkanmod.gl;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;

public class GlTexture {
    // OpenGL reserves object name 0 as the default/unbound texture. Synthetic
    // texture names therefore start at 1 and binding 0 must clear VulkanMod's
    // emulated binding instead of aliasing the first allocated texture.
    private static int ID_COUNT = 1;
    private static final Int2ReferenceOpenHashMap<GlTexture> map = new Int2ReferenceOpenHashMap<>();
    private static final int[] boundTextureIds =
            new int[VTextureSelector.MAX_LEGACY_TEXTURE_UNITS];
    private static final GlTexture[] boundTextures =
            new GlTexture[VTextureSelector.MAX_LEGACY_TEXTURE_UNITS];

    public static int genTextureId() {
        int id = ID_COUNT;
        map.put(id, new GlTexture(id));
        ID_COUNT++;
        return id;
    }

    public static void bindTexture(int i) {
        int unit = VTextureSelector.getActiveTextureUnit();
        boundTextureIds[unit] = i;

        if(i == 0) {
            boundTextures[unit] = null;
            VTextureSelector.bindActiveTexture(null);
            return;
        }

        GlTexture texture = map.get(i);
        if(texture == null)
            throw new NullPointerException("bound texture is null");

        boundTextures[unit] = texture;
        VTextureSelector.bindActiveTexture(texture.vulkanImage);
    }

    public static void glDeleteTextures(int i) {
        GlTexture texture = map.remove(i);

        for(int unit = 0; unit < boundTextureIds.length; ++unit) {
            if(boundTextureIds[unit] == i) {
                boundTextureIds[unit] = 0;
                boundTextures[unit] = null;
                VTextureSelector.bindLegacyTextureUnit(unit, null);
            }
        }

        if(texture != null && texture.vulkanImage != null)
            texture.vulkanImage.free();
    }

    public static GlTexture getTexture(int id) {
        return map.get(id);
    }

    /** Resolve a synthetic GL texture name without mutating the emulated binding. */
    public static VulkanImage getVulkanImage(int id) {
        GlTexture texture = map.get(id);
        return texture != null ? texture.vulkanImage : null;
    }

    public static void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
        if(width == 0 || height == 0)
            return;

        GlTexture boundTexture = getActiveBoundTexture();
        if(boundTexture == null)
            throw new IllegalStateException("No texture bound for glTexImage2D");

        if(boundTexture.vulkanImage == null || width != boundTexture.vulkanImage.width || height != boundTexture.vulkanImage.height || vulkanFormat(format, type) != boundTexture.vulkanImage.format) {
            boundTexture.allocateVulkanImage(width, height);
        }

        boundTexture.uploadImage(pixels);
    }


    public static void texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, @Nullable ByteBuffer pixels) {
        if(width == 0 || height == 0)
            return;

        if(getActiveBoundTexture() == null)
            throw new IllegalStateException("No texture bound for glTexSubImage2D");

        VTextureSelector.uploadSubTexture(level, width, height, xOffset, yOffset,0, 0, width, pixels);
    }

    public static void setVulkanImage(int id, VulkanImage vulkanImage) {
        GlTexture texture = map.get(id);
        if(texture == null)
            throw new IllegalArgumentException("Unknown texture id: " + id);

        texture.vulkanImage = vulkanImage;
        for(int unit = 0; unit < boundTextureIds.length; ++unit) {
            if(boundTextureIds[unit] == id)
                VTextureSelector.bindLegacyTextureUnit(unit, vulkanImage);
        }
    }

    private static GlTexture getActiveBoundTexture() {
        return boundTextures[VTextureSelector.getActiveTextureUnit()];
    }

    final int id;
    VulkanImage vulkanImage;

    public GlTexture(int id) {
        this.id = id;
    }

    private void allocateVulkanImage(int width, int height) {
        if(this.vulkanImage != null)
            this.vulkanImage.free();

        this.vulkanImage = new VulkanImage.Builder(width, height).createVulkanImage();
        VTextureSelector.bindActiveTexture(this.vulkanImage);
    }

    private void uploadImage(@Nullable ByteBuffer pixels) {
        int width = this.vulkanImage.width;
        int height = this.vulkanImage.height;

        if(pixels != null) {
//            if(pixels.remaining() != width * height * 4)
//                throw new IllegalArgumentException("buffer size does not match image size");

            this.vulkanImage.uploadSubTextureAsync(0, width, height, 0, 0, 0, 0, 0, pixels);
        }
        else {
            pixels = MemoryUtil.memCalloc(width * height * 4);
            this.vulkanImage.uploadSubTextureAsync(0, width, height, 0, 0, 0, 0, 0, pixels);
            MemoryUtil.memFree(pixels);
        }
    }

    private static int vulkanFormat(int glFormat, int type) {
        return switch (glFormat) {
            case 6408 ->
                    switch (type) {
                        case 5121 -> VK_FORMAT_R8G8B8A8_UNORM;
                        default -> throw new IllegalStateException("Unexpected value: " + type);
                    };

            default -> throw new IllegalStateException("Unexpected value: " + glFormat);
        };
    }

}
