package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.vulkanmod.interfaces.SpriteAnimationTicker;
import net.vulkanmod.interfaces.VSpriteContentsI;
import net.vulkanmod.render.texture.SpriteAnimationState;
import net.vulkanmod.render.texture.CpuSpriteInterpolation;
import net.vulkanmod.render.texture.GpuAnimationAdmission;
import net.vulkanmod.render.texture.GpuAnimatedTextureResidency;
import net.vulkanmod.render.profiling.TextureTickAttribution;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.Ticker.class)
public abstract class SpriteTickerMixin implements SpriteAnimationTicker {
    @Shadow private int frame;
    @Shadow private int subFrame;
    @Shadow @Final private SpriteContents.AnimatedTexture animationInfo;
    @Shadow @Final private SpriteContents.InterpolationData interpolationData;
    @Unique private SpriteAnimationState vulkanmod$state;
    @Unique private SpriteContents vulkanmod$owner;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vulkanmod$attach(SpriteContents owner, SpriteContents.AnimatedTexture info,
                                  SpriteContents.InterpolationData interpolation, CallbackInfo ci) {
        // Subclasses/custom Forge loaders retain their original pixel/ticker behavior.
        if(owner.getClass() == SpriteContents.class && owner.forgeMeta == null) {
            vulkanmod$owner = owner;
            vulkanmod$state = ((VSpriteContentsI)owner).vulkanmod$animationState();
            vulkanmod$state.attach(this, owner.name().toString());
            if(GpuAnimationAdmission.enabled() && GpuAnimatedTextureResidency.enabled()) {
                var frames = ((SpriteAnimationInfoAccessor)info).vulkanmod$frames();
                long duration = 0, interiorTicks = 0;
                for(int i = 0; i < frames.size(); ++i) {
                    var current = (SpriteAnimationFrameAccessor)(Object)frames.get(i);
                    var next = (SpriteAnimationFrameAccessor)(Object)frames.get((i + 1) % frames.size());
                    duration += current.vulkanmod$frameTime();
                    if(interpolation != null && current.vulkanmod$frameIndex() != next.vulkanmod$frameIndex())
                        interiorTicks += Math.max(0, current.vulkanmod$frameTime() - 1);
                }
                long framePixels = 0;
                long sourceBytes = 0;
                for(int mip = 0; mip < owner.byMipLevel.length; ++mip) {
                    var image = owner.byMipLevel[mip];
                    sourceBytes += (long)image.getWidth() * image.getHeight() * 4L;
                    framePixels += (long)(owner.width() >> mip) * (owner.height() >> mip);
                }
                long charge = sourceBytes + (GpuAnimatedTextureResidency.interpolationEnabled() ? framePixels * 4L : 0);
                vulkanmod$state.registerAdmission(charge,
                        GpuAnimatedTextureResidency.interpolationEnabled() && duration > 0
                                ? framePixels * ((double)interiorTicks / duration) : 0);
            }
        }
    }
    @Inject(method = "tickAndUpload", at = @At("HEAD"))
    private void vulkanmod$beforeTick(int x, int y, CallbackInfo ci) {
        if(vulkanmod$state != null) vulkanmod$state.beginTick(x, y);
    }
    @Inject(method = "tickAndUpload", at = @At("RETURN"))
    private void vulkanmod$afterTick(int x, int y, CallbackInfo ci) {
        if(vulkanmod$state != null) vulkanmod$state.endTick();
    }
    @Override public boolean vulkanmod$materialize() {
        return vulkanmod$state == null || vulkanmod$state.materialize();
    }
    @Override public long vulkanmod$clock() { return ((long)frame << 32) | (subFrame & 0xffffffffL); }

    @Override
    public boolean vulkanmod$tryGpuInterpolation(int x, int y) {
        if(vulkanmod$state == null || interpolationData == null || subFrame <= 0) {
            return false;
        }

        SpriteAnimationInfoAccessor info = (SpriteAnimationInfoAccessor)animationInfo;
        java.util.List<SpriteContents.FrameInfo> frames = info.vulkanmod$frames();
        if(frames == null || frames.size() < 2 || frame < 0 || frame >= frames.size()) {
            return false;
        }

        SpriteAnimationFrameAccessor current =
                (SpriteAnimationFrameAccessor)(Object)frames.get(frame);
        SpriteAnimationFrameAccessor next =
                (SpriteAnimationFrameAccessor)(Object)frames.get((frame + 1) % frames.size());
        int currentIndex = current.vulkanmod$frameIndex();
        int nextIndex = next.vulkanmod$frameIndex();
        if(currentIndex == nextIndex) {
            return false;
        }

        boolean gpu = vulkanmod$state.tryGpuInterpolation(
                x, y, currentIndex, nextIndex,
                subFrame, current.vulkanmod$frameTime(),
                vulkanmod$owner.byMipLevel, vulkanmod$owner.width(), vulkanmod$owner.height());
        TextureTickAttribution.recordStandardInterpolationRoute(gpu);
        return gpu;
    }
    @Override
    public boolean vulkanmod$tryCpuInterpolation(int x, int y,
            NativeImage[] output) {
        if(!CpuSpriteInterpolation.enabled()
                || vulkanmod$owner == null || vulkanmod$state == null || interpolationData == null
                || !RenderSystem.isOnRenderThread()) return false;
        java.util.List<SpriteContents.FrameInfo> frames =
                ((SpriteAnimationInfoAccessor)animationInfo).vulkanmod$frames();
        if(frames == null || frames.size() < 2 || frame < 0 || frame >= frames.size()) return false;
        SpriteAnimationFrameAccessor current = (SpriteAnimationFrameAccessor)(Object)frames.get(frame);
        SpriteAnimationFrameAccessor next = (SpriteAnimationFrameAccessor)(Object)frames.get((frame + 1) % frames.size());
        var source = vulkanmod$owner.byMipLevel;
        int width = vulkanmod$owner.width(), height = vulkanmod$owner.height();
        if(source == null || source.length == 0 || source[0] == null || width <= 0
                || !CpuSpriteInterpolation.tryInterpolate(
                        source, output, width, height, ((SpriteAnimationInfoAccessor)animationInfo).vulkanmod$frameRowSize(),
                        current.vulkanmod$frameIndex(), next.vulkanmod$frameIndex(),
                        subFrame, current.vulkanmod$frameTime())) return false;
        ((SpriteContentsUploadInvoker)(Object)vulkanmod$owner).vulkanmod$uploadFramePixels(x, y, 0, 0, output);
        TextureTickAttribution.recordCpuBulkInterpolation();
        return true;
    }
    @Override public void vulkanmod$refreshFrame(int x, int y) {
        SpriteAnimationInfoAccessor info = (SpriteAnimationInfoAccessor)animationInfo;
        int index = ((SpriteAnimationFrameAccessor)(Object)info.vulkanmod$frames().get(frame)).vulkanmod$frameIndex();
        // Materialize discrete pixels first, including repeated-frame interpolation
        // which deliberately performs no upload. Invoke vanilla arithmetic unchanged.
        info.vulkanmod$uploadFrame(x, y, index);
        if(interpolationData != null && subFrame > 0)
            ((SpriteInterpolationInvoker)(Object)interpolationData).vulkanmod$interpolate(x, y, (SpriteContents.Ticker)(Object)this);
    }
}
