package net.vulkanmod.vulkan.framebuffer;

import com.mojang.blaze3d.pipeline.MainTarget;

/**
 * Identifies Minecraft's one swapchain-backed {@link MainTarget} without
 * assuming that every MainTarget instance is the window target.
 *
 * <p>Vanilla constructs the window MainTarget immediately after
 * RenderSystem.initRenderer(), before client resources begin loading. Forge mods
 * may later construct MainTarget directly for ordinary off-screen rendering
 * (Iceberg does this for item icons). The first MainTarget that reaches its
 * framebuffer allocation is therefore the primary swapchain target; later
 * instances must retain normal RenderTarget semantics.</p>
 */
public final class MainTargetIdentity {
    private static MainTarget primary;

    private MainTargetIdentity() {
    }

    /** Claim the first constructed MainTarget as Minecraft's primary target. */
    public static synchronized boolean claimPrimary(MainTarget target) {
        if(target == null)
            throw new IllegalArgumentException("MainTarget cannot be null");

        if(primary == null)
            primary = target;
        return primary == target;
    }

    /** Return whether this exact MainTarget owns the swapchain. */
    public static synchronized boolean isPrimary(MainTarget target) {
        return target != null && primary == target;
    }
}
