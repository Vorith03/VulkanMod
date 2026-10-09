package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.interfaces.VTextureAtlasI;
import net.vulkanmod.interfaces.VTextureManagerI;
import net.vulkanmod.render.profiling.TextureTickAttribution;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(TextureManager.class)
public abstract class MTextureManager implements VTextureManagerI {

    @Shadow @Final private Set<Tickable> tickableTextures;
    @Shadow @Final private Map<ResourceLocation, AbstractTexture> byPath;

    @Shadow
    private void safeClose(ResourceLocation id, AbstractTexture texture) {
        throw new AssertionError();
    }

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
        long attributionTickStart = TextureTickAttribution.beginTick();
        if(Renderer.skipRendering) {
            TextureTickAttribution.endTick(attributionTickStart);
            return;
        }

        // MinecraftMixin selects the one catch-up tick that is allowed to upload
        // animated sprites before Minecraft.tick() begins. Snapshot that decision so
        // the command-buffer batch has a symmetric start/end lifecycle.
        boolean uploadSprites = SpriteUtil.shouldUpload();
        boolean ownsQueue = uploadSprites && !Device.getGraphicsQueue().hasActiveUploadBatch();
        int originalDepth = VTextureSelector.spriteUploadBatchDepth();
        Throwable tickFailure = null;
        try {
            if(uploadSprites) {
                long phaseStart = TextureTickAttribution.begin(TextureTickAttribution.Phase.BATCH_START);
                try {
                    if(ownsQueue) Device.getGraphicsQueue().startRecording();
                    VTextureSelector.beginSpriteUploadBatch();
                } finally {
                    TextureTickAttribution.end(TextureTickAttribution.Phase.BATCH_START, phaseStart);
                }
            }

            long phaseStart = TextureTickAttribution.begin(TextureTickAttribution.Phase.TICKABLE_LOOP);
            try {
                for(Tickable tickable : this.tickableTextures) tickable.tick();
            } finally {
                TextureTickAttribution.end(TextureTickAttribution.Phase.TICKABLE_LOOP, phaseStart);
            }
        } catch(RuntimeException | Error failure) {
            tickFailure = failure;
            throw failure;
        } finally {
            try {
                if(uploadSprites)
                    this.vulkanmod$finishTickUpload(ownsQueue, originalDepth);
            } catch(RuntimeException | Error cleanupFailure) {
                if(tickFailure != null) tickFailure.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            } finally {
                TextureTickAttribution.endTick(attributionTickStart);
            }
        }
    }

    @Unique
    private void vulkanmod$finishTickUpload(boolean ownsQueue, int originalDepth) {
        try {
            long phaseStart = TextureTickAttribution.begin(TextureTickAttribution.Phase.BATCH_DRAIN);
            try {
                // Unwind a leaf SpriteContents.upload whose RETURN hook did not run.
                VTextureSelector.endSpriteUploadBatchesTo(originalDepth);
            } finally {
                TextureTickAttribution.end(TextureTickAttribution.Phase.BATCH_DRAIN, phaseStart);
            }
        } finally {
            // Only the queue owner may transition the complete outer tick and submit.
            // A nested tick leaves accumulated layouts/copies for its original owner.
            if(ownsQueue && Device.getGraphicsQueue().hasActiveUploadBatch()) {
                try {
                    long phaseStart = TextureTickAttribution.begin(TextureTickAttribution.Phase.LAYOUT_TRANSITIONS);
                    try {
                        SpriteUtil.transitionLayouts(Device.getGraphicsQueue().getCommandBuffer());
                    } finally {
                        TextureTickAttribution.end(TextureTickAttribution.Phase.LAYOUT_TRANSITIONS, phaseStart);
                    }
                } finally {
                    long phaseStart = TextureTickAttribution.begin(TextureTickAttribution.Phase.QUEUE_SUBMIT);
                    try {
                        Device.getGraphicsQueue().endRecordingAndSubmit();
                    } finally {
                        TextureTickAttribution.end(TextureTickAttribution.Phase.QUEUE_SUBMIT, phaseStart);
                    }
                }
            } else if(ownsQueue) {
                // A staging-cap submission/restart can fail after releasing the
                // queue. Do not create an unowned helper just to clean bookkeeping.
                SpriteUtil.clearTransitionedLayouts();
            }
        }
    }

    /**
     * Restore vanilla/Forge ownership semantics. release() removes the registry
     * entry and routes through TextureManager.safeClose(), which removes tickable
     * ownership, invokes texture-specific close(), and releases the texture id.
     * The previous Vulkan overwrite only called releaseId(), leaving stale map and
     * CPU/ticker ownership behind.
     *
     * @author
     */
    @Overwrite
    public void release(ResourceLocation id) {
        AbstractTexture texture = this.byPath.remove(id);
        if(texture != null) {
            this.safeClose(id, texture);
        }
    }
}
