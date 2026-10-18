package net.vulkanmod.render.texture;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.profiling.TextureTickAttribution;
import net.vulkanmod.render.profiling.TextureTickAttribution.InterpolationFallback;
import net.vulkanmod.interfaces.SpriteAnimationTicker;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.queue.GraphicsQueue;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;

public final class SpriteAnimationState {
    private final AnimationVisibility visibility = new AnimationVisibility(System.nanoTime());
    private SpriteAnimationTicker ticker;
    private VulkanImage atlas;
    private int x, y;
    private boolean materialize = true;
    private boolean refreshing;
    private String spriteId;
    private GpuAnimationAdmission.Ticket admission;
    private boolean residentCandidate;
    private boolean residentAttempted;
    private boolean residentDisabled;
    private GpuAnimatedTextureResidency residentFrames;

    public void attach(SpriteAnimationTicker ticker, String spriteId) {
        this.ticker = ticker;
        this.spriteId = spriteId;
        this.residentCandidate = true;
    }
    public void registerAdmission(long bytes, double pixelsPerTick) {
        if(admission != null) admission.close();
        admission = GpuAnimationAdmission.register(spriteId, bytes, pixelsPerTick);
    }
    private boolean admissionAllowed() {
        return !GpuAnimationAdmission.enabled() || (admission != null && admission.selected());
    }
    private boolean createResidency(com.mojang.blaze3d.platform.NativeImage[] images,
                                    int frameWidth, int frameHeight) {
        if(residentFrames != null) return true;
        if(residentAttempted || !admissionAllowed()) return false;
        residentAttempted = true;
        residentFrames = GpuAnimatedTextureResidency.tryCreate(spriteId, images, frameWidth, frameHeight);
        if(residentFrames == null) {
            residentDisabled = true;
            if(admission != null) admission.failed();
            return false;
        }
        if(admission != null) admission.admitted();
        return true;
    }
    public boolean materialize() { return materialize; }
    public void beginTick(int x, int y) {
        if(!SpriteAnimationUsage.enabled()) { materialize = true; return; }
        this.x = x; this.y = y;
        this.atlas = VTextureSelector.getBoundTexture();
        long grace = Math.max(0, Math.min(5000, Initializer.CONFIG.animationVisibilityGraceMs)) * 1_000_000L;
        boolean always = false;
        String[] exclusions = Initializer.CONFIG.animationAlwaysActiveSprites;
        if(exclusions != null) for(String excluded : exclusions) if(java.util.Objects.equals(excluded, spriteId)) { always = true; break; }
        materialize = SpriteUtil.shouldUpload() && visibility.materialize(System.nanoTime(), grace, !always);
        if(!materialize) visibility.skipped();
    }
    public void endTick() {
        if(materialize && SpriteUtil.shouldUpload()) refreshIfNeeded();
    }
    public void uploaded() { visibility.refreshed(); }

    public boolean tryResidentUpload(int x, int y, int sourceX, int sourceY,
                                     com.mojang.blaze3d.platform.NativeImage[] images,
                                     int frameWidth, int frameHeight) {
        if(!residentCandidate || residentDisabled || !GpuAnimatedTextureResidency.enabled()
                || !Device.getGraphicsQueue().hasActiveUploadBatch()) {
            return false;
        }

        VulkanImage target = VTextureSelector.getBoundTexture();
        if(target == null) {
            return false;
        }

        if(!createResidency(images, frameWidth, frameHeight)) return false;

        if(!residentFrames.copyToAtlas(target, x, y, sourceX, sourceY, frameWidth, frameHeight, images)) {
            residentFrames.close();
            residentFrames = null;
            residentDisabled = true;
            if(admission != null) admission.failed();
            return false;
        }

        this.atlas = target;
        visibility.refreshed();
        return true;
    }

    public boolean tryGpuInterpolation(int x, int y,
                                       int currentIndex, int nextIndex,
                                       int subFrame, int duration,
                                       com.mojang.blaze3d.platform.NativeImage[] images,
                                       int frameWidth, int frameHeight) {
        if(!residentCandidate) return interpolationFallback(InterpolationFallback.INELIGIBLE);
        if(residentDisabled) return interpolationFallback(InterpolationFallback.RESIDENCY_DISABLED);
        if(!materialize) return interpolationFallback(InterpolationFallback.SUPPRESSED);
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())
            return interpolationFallback(InterpolationFallback.OFF_RENDER_THREAD);
        if(!GpuAnimatedTextureResidency.interpolationEnabled())
            return interpolationFallback(InterpolationFallback.GPU_UNAVAILABLE);
        if(!Device.getGraphicsQueue().hasActiveUploadBatch())
            return interpolationFallback(InterpolationFallback.NO_UPLOAD_BATCH);

        VulkanImage target = VTextureSelector.getBoundTexture();
        if(target == null) return interpolationFallback(InterpolationFallback.NO_ATLAS);

        if(residentFrames == null) {
            if(!GpuAnimationAdmission.enabled()) return interpolationFallback(InterpolationFallback.NO_RESIDENCY);
            if(!admissionAllowed()) return interpolationFallback(InterpolationFallback.ADMISSION_DEFERRED);
            if(!createResidency(images, frameWidth, frameHeight))
                return interpolationFallback(InterpolationFallback.NO_RESIDENCY);
        }
        if(!residentFrames.sourcesValid()) {
            residentFrames.noteSourceInvalidated();
            residentFrames.close();
            residentFrames = null;
            residentDisabled = true;
            if(admission != null) admission.failed();
            return interpolationFallback(InterpolationFallback.SOURCE_MUTATED);
        }

        if(!residentFrames.interpolateToAtlas(
                target, x, y, currentIndex, nextIndex, subFrame, duration)) {
            return interpolationFallback(InterpolationFallback.DISPATCH_FAILED);
        }

        SpriteUtil.addTransitionedLayout(target);
        this.atlas = target;
        visibility.refreshed();
        return true;
    }

    private static boolean interpolationFallback(InterpolationFallback reason) {
        TextureTickAttribution.recordStandardInterpolationFallback(reason);
        return false;
    }

    public void use() {
        visibility.use(System.nanoTime());
        refreshIfNeeded();
    }
    private void refreshIfNeeded() {
        if(refreshing || !visibility.needsRefresh() || atlas == null || ticker == null) return;
        refreshing = true;
        boolean oldMaterialize = materialize;
        boolean oldUpload = SpriteUtil.shouldUpload();
        VulkanImage previous = VTextureSelector.getBoundTexture();
        GraphicsQueue queue = Device.getGraphicsQueue();
        boolean owns = !queue.hasActiveUploadBatch();
        try {
            if(owns) queue.startRecording();
            materialize = true;
            SpriteUtil.setDoUpload(true);
            VTextureSelector.bindTexture(atlas);
            ticker.vulkanmod$refreshFrame(x, y);
            visibility.refreshed();
        } finally {
            try {
                if(owns) {
                    SpriteUtil.transitionLayouts(queue.getCommandBuffer());
                    queue.endRecordingAndSubmit();
                }
            } finally {
                VTextureSelector.bindTexture(previous);
                SpriteUtil.setDoUpload(oldUpload);
                materialize = oldMaterialize;
                refreshing = false;
            }
        }
    }
    public void close() {
        visibility.close();
        if(admission != null) { admission.close(); admission = null; }
        if(residentFrames != null) {
            residentFrames.close();
            residentFrames = null;
        }
        atlas = null;
        ticker = null;
    }
}
