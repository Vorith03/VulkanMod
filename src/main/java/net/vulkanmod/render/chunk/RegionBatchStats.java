package net.vulkanmod.render.chunk;

/** Render-thread counters, exposed through the existing F3 chunk statistics. */
final class RegionBatchStats {
    static int sections, calls;
    static int commandUploads, commandBytes;
    static int gpuCommandOwnerAttempts, gpuCommandCandidateSlots;
    static final int[] gpuCommandOwnerReasons = new int[7];
    static int meshUploads, reusedSlices, allocatedSlices, bufferGrowths;
    static long meshBytes;
    // Lifetime values survive renderer-update reset for benchmark window deltas.
    static long gpuCandidateAttempts, gpuCandidateDrawReuses, gpuCandidateTraversalReuses;
    static long gpuCandidateRebuilds, gpuCandidatePreparedSlots, gpuCandidatePublications;
    static long gpuCandidatePublishedSlots, gpuCandidateProducerMarks;
    static long gpuCandidatePreparationNanos;
    static long regionBufferReuses, regionBufferFallbacks;

    static void reset() {
        sections = calls = 0;
        commandUploads = commandBytes = 0;
        gpuCommandOwnerAttempts = gpuCommandCandidateSlots = 0;
        java.util.Arrays.fill(gpuCommandOwnerReasons, 0);
        meshUploads = reusedSlices = allocatedSlices = bufferGrowths = 0;
        meshBytes = 0L;
        // Region-ring recycling only occurs occasionally and reset() runs every
        // renderer update, so keep these two counters cumulative for F3 evidence.
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

    static void recordRegionBufferReuse() {
        regionBufferReuses++;
    }

    static void recordRegionBufferFallback() {
        regionBufferFallbacks++;
    }

    static String describe() {
        String drawStats = String.format(
                " R:%d/%d cmd:%d/%dB mesh:%d/%.0fK r/n/g:%d/%d/%d ar/f:%d/%d",
                sections, calls,
                commandUploads, commandBytes,
                meshUploads, meshBytes / 1024.0D,
                reusedSlices, allocatedSlices, bufferGrowths,
                regionBufferReuses, regionBufferFallbacks);
        if(gpuCommandOwnerAttempts > 0) drawStats += String.format(
                " gpuCmdBypass:%d/%d slots:%d fallbackDisabled/hybrid/superset/volume/output/cap:%d/%d/%d/%d/%d/%d",
                gpuCommandOwnerReasons[0], gpuCommandOwnerAttempts, gpuCommandCandidateSlots,
                gpuCommandOwnerReasons[1], gpuCommandOwnerReasons[2], gpuCommandOwnerReasons[3],
                gpuCommandOwnerReasons[4], gpuCommandOwnerReasons[5], gpuCommandOwnerReasons[6]);
        if(gpuCandidateAttempts > 0) drawStats += String.format(
                " gpuCandidateLifetime:drawReuse/traversalReuse/rebuild/attempt:%d/%d/%d/%d prepared/publishedSlots:%d/%d publishes:%d marks:%d prepMs:%.3f",
                gpuCandidateDrawReuses, gpuCandidateTraversalReuses, gpuCandidateRebuilds,
                gpuCandidateAttempts, gpuCandidatePreparedSlots, gpuCandidatePublishedSlots,
                gpuCandidatePublications, gpuCandidateProducerMarks,
                gpuCandidatePreparationNanos / 1_000_000.0D);
        return drawStats + " " + WorldRenderer.getInstance().getChunkAreaManager().getStorageStats();
    }
}
