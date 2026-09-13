package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Generation-owned device-local residency for one packed GPU terrain model table.
 *
 * <p>Uploads use the existing graphics-queue ordered terrain staging path. A new
 * resource generation revokes discoverable old residency immediately and only
 * publishes the replacement after its copy command has been submitted. CPU terrain
 * remains the fallback when allocation/upload is unavailable.</p>
 */
public final class GpuTerrainModelGpuStore implements AutoCloseable {
    private long generation = -1L;
    private Pending pending;
    private Resident resident;
    private boolean closed;

    public synchronized boolean upload(GpuTerrainModelTable table) {
        if(table == null)
            throw new IllegalArgumentException("GPU terrain model table must be present");
        if(table.generation() < generation)
            return false;

        generation = table.generation();
        pending = null;
        discardResident();

        if(closed || AreaUploadManager.INSTANCE == null
                || MemoryManager.getInstance() == null || MemoryTypes.GPU_MEM == null)
            return false;

        StorageBuffer buffer;
        try {
            buffer = new StorageBuffer(table.byteSize(), MemoryTypes.GPU_MEM);
        } catch(RuntimeException error) {
            // Device-local residency is only an optimization. Preserve the CPU
            // renderer under ordinary allocation pressure, matching voxel pages.
            if(error.getMessage() != null && error.getMessage().startsWith("Failed to create buffer:"))
                return false;
            throw error;
        }

        Pending token = new Pending(buffer, table.generation(), table.byteSize());
        pending = token;
        ByteBuffer bytes = MemoryUtil.memAlloc(table.byteSize());
        try {
            table.writeTo(bytes);
            bytes.flip();
            AreaUploadManager.INSTANCE.uploadStorageAsync(buffer, 0L, bytes,
                    () -> completeUpload(token));
        } catch(RuntimeException | Error error) {
            if(pending == token)
                pending = null;
            buffer.freeBuffer();
            throw error;
        } finally {
            MemoryUtil.memFree(bytes);
        }
        return true;
    }

    public synchronized Residency getResidency() {
        if(closed || resident == null || resident.generation != generation)
            return Residency.invalid(generation);
        return new Residency(resident.buffer, resident.generation, resident.byteLength, true);
    }

    public synchronized void invalidate(long generation) {
        if(generation < this.generation)
            return;
        this.generation = generation;
        this.pending = null;
        discardResident();
    }

    @Override
    public synchronized void close() {
        if(closed)
            return;
        closed = true;
        pending = null;
        discardResident();
    }

    private synchronized void completeUpload(Pending token) {
        if(closed || pending != token || generation != token.generation) {
            token.buffer.freeBuffer();
            return;
        }

        pending = null;
        Resident previous = resident;
        resident = new Resident(token.buffer, token.generation, token.byteLength);
        if(previous != null)
            previous.buffer.freeBuffer();
    }

    private void discardResident() {
        Resident previous = resident;
        resident = null;
        if(previous != null)
            previous.buffer.freeBuffer();
    }

    private record Pending(StorageBuffer buffer, long generation, int byteLength) {}
    private record Resident(StorageBuffer buffer, long generation, int byteLength) {}

    public record Residency(StorageBuffer buffer, long generation, int byteLength, boolean valid) {
        static Residency invalid(long generation) {
            return new Residency(null, generation, 0, false);
        }
    }
}
