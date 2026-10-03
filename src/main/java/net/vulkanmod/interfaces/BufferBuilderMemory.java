package net.vulkanmod.interfaces;

/** Explicit native lifetime for privately owned builders, after all batch consumers have finished. */
public interface BufferBuilderMemory {
    long vulkanmod$retainedBytes();
    /** Discards pending batches and frees the current (possibly grown) allocation once. */
    void vulkanmod$releaseMemory();
}
