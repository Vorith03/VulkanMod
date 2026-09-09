package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.interfaces.VTextureAtlasI;
import net.vulkanmod.interfaces.VTextureManagerI;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(TextureManager.class)
public abstract class MTextureManager implements VTextureManagerI {

    @Shadow @Final private Set<Tickable> tickableTextures;
    @Shadow @Final private Map<ResourceLocation, AbstractTexture> byPath;

    @Shadow public abstract AbstractTexture getTexture(ResourceLocation resourceLocation, AbstractTexture abstractTexture);

    @Override
    public int vulkanmod$retireStaticAtlasCpuDataForReload() {
        Set<AbstractTexture> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int retiredStaticSprites = 0;

        for(AbstractTexture texture : this.byPath.values()) {
            if(texture == null || !seen.add(texture)) {
                continue;
            }

            if(texture instanceof TextureAtlas atlas && atlas instanceof VTextureAtlasI vulkanAtlas) {
                retiredStaticSprites += vulkanAtlas.vulkanmod$retireStaticSpriteCpuDataForReload();
            }
        }

        return retiredStaticSprites;
    }

    /**
     * @author
     */
    @Overwrite
    public void tick() {
        if(Renderer.skipRendering)
            return;

        // MinecraftMixin selects the one catch-up tick that is allowed to upload
        // animated sprites before Minecraft.tick() begins. Snapshot that decision so
        // the command-buffer batch has a symmetric start/end lifecycle.
        boolean uploadSprites = SpriteUtil.shouldUpload();
        if(uploadSprites)
            Device.getGraphicsQueue().startRecording();

        for (Tickable tickable : this.tickableTextures) {
            tickable.tick();
        }

        if(uploadSprites) {
            SpriteUtil.transitionLayouts(Device.getGraphicsQueue().getCommandBuffer());
            Device.getGraphicsQueue().endRecordingAndSubmit();
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void release(ResourceLocation id) {
        AbstractTexture abstractTexture = this.getTexture(id, MissingTextureAtlasSprite.getTexture());
        if (abstractTexture != MissingTextureAtlasSprite.getTexture()) {
            //TODO: delete
            abstractTexture.releaseId();
        }
    }
}
