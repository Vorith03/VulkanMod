package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.vulkan.memory.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class AreaBuffer {
    private final MemoryType memoryType;
    private final int usage;

    private final List<Segment> freeSegments = new ArrayList<>();
    private final Reference2ReferenceOpenHashMap<Segment, Segment> usedSegments = new Reference2ReferenceOpenHashMap<>();

    private final int elementSize;

    private Buffer buffer;

    int size;
    int used;

    public AreaBuffer(int usage, int size, int elementSize) {

        this.usage = usage;
        this.elementSize = elementSize;
        this.memoryType = MemoryTypes.GPU_MEM;

        this.buffer = this.allocateBuffer(size);
        this.size = size;

        freeSegments.add(new Segment(0, size));
    }

    private Buffer allocateBuffer(int size) {
        if(this.usage == VK_BUFFER_USAGE_VERTEX_BUFFER_BIT) {
            return new VertexBuffer(size, memoryType);
        }
        if(this.usage == VK_BUFFER_USAGE_INDEX_BUFFER_BIT) {
            return new IndexBuffer(size, memoryType);
        }
        if(this.usage == VK_BUFFER_USAGE_STORAGE_BUFFER_BIT) {
            return new StorageBuffer(size, memoryType);
        }

        throw new IllegalArgumentException("Unsupported AreaBuffer usage: 0x" + Integer.toHexString(this.usage));
    }

    public synchronized void upload(ByteBuffer byteBuffer, Segment uploadSegment) {
        int uploadSize = byteBuffer.remaining();

        if(uploadSize % elementSize != 0)
            throw new RuntimeException("unaligned byteBuffer");

        Segment allocation = this.usedSegments.get(uploadSegment);
        boolean reused = allocation != null && allocation.size >= uploadSize;
        int dstOffset;

        if(reused) {
            // Keep the existing reservation stable when a rebuilt mesh still fits.
            // The logical payload may shrink or grow within that reservation, but
            // retaining its offset avoids free-list churn and keeps cached indirect
            // commands stable apart from the mesh revision that already invalidates
            // their index/vertex metadata.
            dstOffset = allocation.offset;
        } else {
            if(allocation != null) {
                this.setSegmentFree(uploadSegment);
            }

            Segment freeSegment = findSegment(uploadSize);
            dstOffset = freeSegment.offset;

            if(freeSegment.size - uploadSize > 0) {
                addFreeSegment(new Segment(freeSegment.offset + uploadSize, freeSegment.size - uploadSize));
            }

            this.usedSegments.put(uploadSegment, new Segment(dstOffset, uploadSize));
            this.used += uploadSize;
        }

        Buffer dst = this.buffer;
        AreaUploadManager.INSTANCE.uploadAsync(uploadSegment, dst.getId(), dstOffset, uploadSize, byteBuffer);

        uploadSegment.offset = dstOffset;
        uploadSegment.size = uploadSize;
        uploadSegment.status = Segment.PENDING_BIT;

        RegionBatchStats.recordMeshUpload(uploadSize, reused);
    }

    public Segment findSegment(int size) {
        Segment segment = null;
        int i = 0;
        int idx = 0;
        int t = Integer.MAX_VALUE;
        for(Segment segment1 : freeSegments) {

            if(segment1.size >= size && segment1.size < t) {
                segment = segment1;
                t = segment1.size;
                idx = i;
            }
            ++i;
        }

        if(segment == null) {
            return this.reallocate(size);
        }

        freeSegments.remove(idx);

        return segment;
    }

    public Segment reallocate(int uploadSize) {
        int oldSize = this.size;
        int offset = Util.align(oldSize, elementSize);

        long minimumSize = (long) offset + uploadSize;
        long grownSize = (long) oldSize + Math.max(oldSize >> 1, elementSize);
        long requestedSize = Math.max(minimumSize, grownSize);
        if(requestedSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("AreaBuffer exceeds maximum supported size");
        }

        int newSize = (int) requestedSize;
        Buffer buffer = this.allocateBuffer(newSize);

        // Growth must see every terrain copy recorded so far before cloning the old
        // allocation. Those copies and this copy now share the graphics queue, so
        // queue order also guarantees all older terrain draws have finished reading
        // the source before the synchronous growth copy executes.
        AreaUploadManager.INSTANCE.waitAllUploads();
        AreaUploadManager.INSTANCE.copyImmediate(this.buffer, buffer);
        this.buffer.freeBuffer();
        this.buffer = buffer;

        this.size = newSize;
        RegionBatchStats.recordBufferGrowth();

        return new Segment(offset, newSize - offset);
    }

    public synchronized void setSegmentFree(Segment uploadSegment) {
        Segment segment = usedSegments.remove(uploadSegment);

        if(segment == null)
            return;

        int freedSize = segment.size;
        addFreeSegment(segment);
        this.used -= freedSize;
    }

    private void addFreeSegment(Segment segment) {
        int insertIndex = 0;
        while(insertIndex < freeSegments.size() && freeSegments.get(insertIndex).offset < segment.offset) {
            insertIndex++;
        }

        freeSegments.add(insertIndex, segment);

        if(insertIndex > 0) {
            Segment previous = freeSegments.get(insertIndex - 1);
            if((long) previous.offset + previous.size >= segment.offset) {
                long mergedEnd = Math.max((long) previous.offset + previous.size, (long) segment.offset + segment.size);
                previous.size = (int) (mergedEnd - previous.offset);
                freeSegments.remove(insertIndex);
                segment = previous;
                insertIndex--;
            }
        }

        if(insertIndex + 1 < freeSegments.size()) {
            Segment next = freeSegments.get(insertIndex + 1);
            if((long) segment.offset + segment.size >= next.offset) {
                long mergedEnd = Math.max((long) segment.offset + segment.size, (long) next.offset + next.size);
                segment.size = (int) (mergedEnd - segment.offset);
                freeSegments.remove(insertIndex + 1);
            }
        }
    }

    public long getId() {
        return this.buffer.getId();
    }

    int getCapacityBytes() {
        return this.size;
    }

    int getUsedBytes() {
        return this.used;
    }

    public void freeBuffer() {
        this.buffer.freeBuffer();
//        this.globalBuffer.freeSubAllocation(subAllocation);
    }

    public static class Segment {
        public static final byte PENDING_BIT = 0x1;
        public static final byte READY_BIT = 0x2;

        int offset, size;
        byte status;

        public Segment() {
            reset();
        }

        private Segment(int offset, int size) {
            this.offset = offset;
            this.size = size;
            this.status = 0;
        }

        public void reset() {
            this.offset = -1;
            this.size = -1;
            this.status = 0;
        }

        public int getOffset() {
            return offset;
        }

        public int getSize() {
            return size;
        }

        void setPending() {
            this.status = PENDING_BIT;
        }

        public boolean isPending() {
            return (this.status & PENDING_BIT) != 0;
        }

        public void setReady() {
            this.status = READY_BIT;
        }

        public boolean isReady() {
            return (this.status & READY_BIT) != 0;
        }

    }

//    //Debug
//    public List<Segment> findConflicts(int offset) {
//        List<Segment> segments = new ArrayList<>();
//        Segment segment = this.usedSegments.get(offset);
//
//        for(Segment s : this.usedSegments.values()) {
//            if((s.offset >= segment.offset && s.offset < (segment.offset + segment.size))
//              || (segment.offset >= s.offset && segment.offset < (s.offset + s.size))) {
//                segments.add(s);
//            }
//        }
//
//        return segments;
//    }

    public static boolean checkRanges(Segment s1, Segment s2) {
        return (s1.offset >= s2.offset && s1.offset < (s2.offset + s2.size)) || (s2.offset >= s1.offset && s2.offset < (s1.offset + s1.size));
    }

    public Segment getSegment(int offset) {
        return this.usedSegments.get(offset);
    }
}
