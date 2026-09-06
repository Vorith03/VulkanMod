package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(TextureManager.class)
public abstract class MTextureManager {


    @Shadow @Final private Set<Tickable> tickableTextures;


    @Shadow public abstract AbstractTexture getTexture(ResourceLocation resourceLocation, AbstractTexture abstractTexture);

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
