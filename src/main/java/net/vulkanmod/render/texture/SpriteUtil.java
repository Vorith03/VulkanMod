package net.vulkanmod.render.texture;

import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.util.HashSet;
import java.util.Set;

public abstract class SpriteUtil {

    private static boolean doUpload = false;

    private static final Set<VulkanImage> transitionedLayouts = new HashSet<>();
    private static VulkanImage lastTransitionedLayout;

    public static void setDoUpload(boolean b) {
        doUpload = b;
    }

    public static boolean shouldUpload() {
        return doUpload;
    }

    public static void addTransitionedLayout(VulkanImage image) {
        // Animated sprites are normally visited atlas-by-atlas. Avoid repeating a
        // HashSet lookup for every sprite when the previous sprite already marked
        // the same Vulkan image; the set still handles non-consecutive duplicates.
        if(image != lastTransitionedLayout) {
            transitionedLayouts.add(image);
            lastTransitionedLayout = image;
        }
    }

    public static void transitionLayouts(CommandPool.CommandBuffer commandBuffer) {
        try {
            transitionedLayouts.forEach(image -> image.readOnlyLayout(commandBuffer));
        } finally {
            clearTransitionedLayouts();
        }
    }

    public static void clearTransitionedLayouts() {
        transitionedLayouts.clear();
        lastTransitionedLayout = null;
    }
}
