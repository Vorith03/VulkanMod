package net.vulkanmod.vulkan.memory;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

/**
 * Immutable device-local source bytes for the experimental animated-texture
 * resident-copy path. Retirement uses MemoryManager's upload-safe queue because
 * helper graphics submissions are not necessarily covered by the current slot's
 * already-submitted frame fence.
 */
public final class TextureResidentBuffer extends Buffer {
    private boolean retirementScheduled;

    public TextureResidentBuffer(int size) {
        super(VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.GPU_MEM);
        if(size <= 0) {
            throw new IllegalArgumentException("Texture resident buffer size must be positive");
        }
        this.createBuffer(size);
    }

    public synchronized void retire(Runnable afterFree) {
        if(this.retirementScheduled) {
            return;
        }
        this.retirementScheduled = true;
        MemoryManager manager = MemoryManager.getInstance();
        manager.addToFreeable(this);
        if(afterFree != null) {
            manager.addFrameOp(afterFree);
        }
    }
}
