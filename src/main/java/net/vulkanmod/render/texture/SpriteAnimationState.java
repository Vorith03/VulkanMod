package net.vulkanmod.render.texture;

import net.vulkanmod.Initializer;
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

    public void attach(SpriteAnimationTicker ticker, String spriteId) { this.ticker = ticker; this.spriteId = spriteId; }
    public boolean materialize() { return materialize; }
    public void beginTick(int x, int y) {
        if(!SpriteAnimationUsage.enabled()) { materialize = true; return; }
        this.x = x; this.y = y;
        this.atlas = VTextureSelector.getBoundTexture();
        long grace = Math.max(0, Math.min(5000, Initializer.CONFIG.animationVisibilityGraceMs)) * 1_000_000L;
        boolean always = false;
        String[] exclusions = Initializer.CONFIG.animationAlwaysActiveSprites;
        if(exclusions != null) for(String excluded : exclusions) if(excluded.equals(spriteId)) { always = true; break; }
        materialize = SpriteUtil.shouldUpload() && visibility.materialize(System.nanoTime(), grace, !always);
        if(!materialize) visibility.skipped();
    }
    public void endTick() {
        if(materialize && SpriteUtil.shouldUpload()) refreshIfNeeded();
    }
    public void uploaded() { visibility.refreshed(); }
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
    public void close() { visibility.close(); atlas = null; ticker = null; }
}
