package net.vulkanmod.render.texture;

import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.memory.TextureResidentBuffer;
import net.vulkanmod.vulkan.texture.VTextureSelector;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared immutable source storage for generic GPU texture animation operations.
 *
 * Allocations are append-only within each page: one sprite cannot overwrite
 * another's source while submitted graphics/compute commands are in flight.
 * A page is retired only when every lease has closed, through the existing
 * TextureResidentBuffer upload-safe retirement queue. Slot reuse within a
 * live page is intentionally deferred pending a proven GPU-completion token.
 */
public final class GpuAnimationSourcePool {
    private static final boolean REQUESTED =
            Boolean.getBoolean("vulkanmod.experimentalGpuTextureSourcePool");
    private static final int PAGE_BYTES = 4 * 1024 * 1024;
    private static final long MAX_ALLOCATED_BYTES = 128L * 1024L * 1024L;
    private static final List<Page> PAGES = new ArrayList<>();
    private static long allocatedBytes;

    private GpuAnimationSourcePool() {}

    public static boolean enabled() { return REQUESTED; }

    public static synchronized Lease acquire(ByteBuffer source) {
        if(!REQUESTED || source == null || source.remaining() <= 0
                || !Device.getGraphicsQueue().hasActiveUploadBatch()) {
            return null;
        }
        int bytes = source.remaining();
        long deviceAlignment = Device.deviceProperties.limits().minStorageBufferOffsetAlignment();
        if(deviceAlignment > Integer.MAX_VALUE || deviceAlignment <= 0L) return null;
        // Vulkan descriptors require each per-lease source offset to respect
        // the actual physical device's storage-buffer offset alignment.
        int alignment = (int)Math.max(4L, deviceAlignment);
        if((alignment & (alignment - 1)) != 0) return null;

        Page page = null;
        int start = -1;
        for(Page candidate : PAGES) {
            start = candidate.layout.reserve(bytes);
            if(start >= 0) {
                page = candidate;
                break;
            }
        }
        if(page == null) {
            long desired = Math.max(PAGE_BYTES, ((long)bytes + 4095L) & ~4095L);
            if(desired > Integer.MAX_VALUE || allocatedBytes > MAX_ALLOCATED_BYTES - desired) {
                return null;
            }
            TextureResidentBuffer buffer = new TextureResidentBuffer((int)desired);
            page = new Page(buffer, (int)desired, alignment);
            allocatedBytes += desired;
            start = page.layout.reserve(bytes);
            if(start < 0) throw new IllegalStateException("New GPU source page cannot fit its first lease");
            PAGES.add(page);
        }
        Lease lease = new Lease(page, start, bytes);
        boolean success = false;
        try {
            success = VTextureSelector.uploadTextureResidentSource(page.buffer, start, source);
            return success ? lease : null;
        } finally {
            if(!success) lease.retire(null);
        }
    }

    private static synchronized void release(Page page, Runnable afterGpuSafe) {
        if(afterGpuSafe != null) page.callbacks.add(afterGpuSafe);
        page.layout.release();
        if(!page.layout.empty()) return;

        PAGES.remove(page);
        final int capacity = page.layout.capacity();
        page.buffer.retire(() -> {
            synchronized(GpuAnimationSourcePool.class) {
                allocatedBytes -= capacity;
                if(allocatedBytes < 0L)
                    throw new IllegalStateException("GPU texture pool allocation underflow");
            }
            for(Runnable callback : page.callbacks) callback.run();
            page.callbacks.clear();
        });
    }

    public static synchronized long allocatedBytes() { return allocatedBytes; }

    private static final class Page {
        final TextureResidentBuffer buffer;
        final GpuSourcePageLayout layout;
        final List<Runnable> callbacks = new ArrayList<>();
        Page(TextureResidentBuffer buffer, int capacity, int alignment) {
            this.buffer = buffer;
            this.layout = new GpuSourcePageLayout(capacity, alignment);
        }
    }

    public static final class Lease {
        private final Page page;
        private final int offset;
        private final int bytes;
        private boolean retired;

        private Lease(Page page, int offset, int bytes) {
            this.page = page;
            this.offset = offset;
            this.bytes = bytes;
        }
        public TextureResidentBuffer buffer() { return page.buffer; }
        public int offset() { return offset; }
        public int bytes() { return bytes; }

        public synchronized void retire(Runnable afterGpuSafe) {
            if(retired) return;
            retired = true;
            GpuAnimationSourcePool.release(page, afterGpuSafe);
        }
    }
}
