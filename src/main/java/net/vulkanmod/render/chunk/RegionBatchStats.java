package net.vulkanmod.render.chunk;

/** Render-thread counters, exposed through the existing F3 chunk statistics. */
final class RegionBatchStats {
    static int sections, calls;
    static int commandUploads, commandBytes;
    static int meshUploads, reusedSlices, allocatedSlices, bufferGrowths;
    static long meshBytes;

    static void reset() {
        sections = calls = 0;
        commandUploads = commandBytes = 0;
        meshUploads = reusedSlices = allocatedSlices = bufferGrowths = 0;
        meshBytes = 0L;
    }

    static void recordMeshUpload(int bytes, boolean reused) {
        meshUploads++;
        meshBytes += Math.max(0, bytes);
        if(reused) {
            reusedSlices++;
        } else {
            allocatedSlices++;
        }
    }

    static void recordBufferGrowth() {
        bufferGrowths++;
    }

    static String describe() {
        return String.format(
                " Region: %d sections/%d calls cmd:%d/%dB mesh:%d/%.1fKiB reuse/new/grow:%d/%d/%d",
                sections, calls,
                commandUploads, commandBytes,
                meshUploads, meshBytes / 1024.0D,
                reusedSlices, allocatedSlices, bufferGrowths);
    }
}
