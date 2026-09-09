package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractTexture.class)
public abstract class MAbstractTexture implements VAbstractTextureI {
    private static final long LARGE_REPLACEMENT_BYTES = 64L * 1024L * 1024L;

    @Shadow protected boolean blur;
    @Shadow protected boolean mipmap;
    @Shadow protected int id;

    @Shadow public abstract int getId();

    protected VulkanImage vulkanImage;

    /**
     * @author
     */
    @Overwrite
    public void bind() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(this::bindTexture);
        } else {
            this.bindTexture();
        }
    }

    /**
     * Release the resource state exactly once and immediately mark this texture as
     * unassigned. The previous Vulkan overwrite removed the synthetic GL mapping but
     * left id unchanged, so reusing the same AbstractTexture could bind a stale id.
     * Capture the resources before scheduling render-thread destruction so a later
     * reuse of this object cannot make the queued callback free its replacement.
     *
     * @author
     */
    @Overwrite
    public void releaseId() {
        VulkanImage imageToRelease = this.vulkanImage;
        int idToRelease = this.id;

        this.vulkanImage = null;
        this.id = -1;

        if(imageToRelease == null && idToRelease == -1) {
            return;
        }

        Runnable release = () -> {
            if(imageToRelease != null) {
                imageToRelease.free();
            }
            if(idToRelease != -1) {
                TextureUtil.releaseTextureId(idToRelease);
            }
        };

        if(!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(release::run);
        } else {
            release.run();
        }
    }

    public void setId(int i) {
        this.id = i;
    }

    /**
     * @author
     */
    @Overwrite
    public void setFilter(boolean blur, boolean mipmap) {
        if(blur != this.blur || mipmap != this.mipmap) {
            this.blur = blur;
            this.mipmap = mipmap;

            vulkanImage.updateTextureSampler(this.blur, false, this.mipmap);
        }
    }

    @Override
    public void bindTexture() {
        GlTexture.bindTexture(this.id);

        if (vulkanImage != null)
            VTextureSelector.bindTexture(vulkanImage);
        else
            VTextureSelector.bindTexture(VTextureSelector.getWhiteTexture());
    }

    public VulkanImage getVulkanImage() {
        return vulkanImage;
    }

    public void setVulkanImage(VulkanImage image) {
        VulkanImage previous = this.vulkanImage;

        if(previous != null && previous != image) {
            long estimatedBytes = previous.getEstimatedSizeBytes();

            if(estimatedBytes >= LARGE_REPLACEMENT_BYTES) {
                long estimatedMiB = estimatedBytes / (1024L * 1024L);
                Initializer.LOGGER.info(
                        "Retiring large Vulkan texture {}x{} mips={} (~{} MiB) before replacement",
                        previous.width, previous.height, previous.mipLevels, estimatedMiB);

                // Initial/resource-pack reloads can replace a very large atlas before
                // another rendered frame gets a chance to drain frame-deferred frees.
                // Wait for prior GPU users, then retire the old image immediately so
                // the old and replacement 16K atlases do not coexist for the reload.
                Vulkan.waitIdle();
                previous.doFree();
            } else {
                previous.free();
            }
        }

        this.vulkanImage = image;

        if(this.id == -1)
            this.getId();
        GlTexture.setVulkanImage(this.id, this.vulkanImage);
    }
}
