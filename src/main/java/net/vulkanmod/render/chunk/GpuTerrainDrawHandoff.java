package net.vulkanmod.render.chunk;

import net.vulkanmod.render.vertex.TerrainRenderType;

/**
 * Generation-safe command selection for the production GPU-terrain draw handoff.
 *
 * <p>When an exact-generation GPU residency is valid, it may own the command even
 * when no CPU mesh exists yet. Missing/stale/unsupported GPU output still returns the
 * supplied CPU command unchanged, so rebuilds with retained CPU geometry continue to
 * fail closed while fresh GPU-first sections can become visible after publication.</p>
 */
final class GpuTerrainDrawHandoff {
    // Region terrain uses the renderer's uint16 auto-quad index buffer. A command's
    // indices are local to its vertexOffset, so one command may address at most
    // 65,536 vertices = 16,384 complete quads without wrapping an index.
    static final int MAX_AUTO_INDEX_FACES = (1 << 16) / GpuTerrainOutputStore.VERTICES_PER_FACE;

    private GpuTerrainDrawHandoff() {}

    static DrawCommand select(boolean enabled,
                              TerrainRenderType type,
                              long sectionGeneration,
                              GpuTerrainOutputStore.Residency residency,
                              int cpuIndexCount,
                              int cpuFirstIndex,
                              int cpuVertexOffset) {
        DrawCommand cpu = new DrawCommand(cpuIndexCount, cpuFirstIndex,
                cpuVertexOffset, false);
        if(!enabled || !supported(type) || residency == null
                || !residency.valid() || residency.generation() != sectionGeneration
                || residency.faceCount() <= 0
                || residency.faceCount() > GpuTerrainOutputStore.MAX_FACES
                || residency.faceCount() > MAX_AUTO_INDEX_FACES
                || residency.vertexOffset() < 0)
            return cpu;

        int indexCount;
        try {
            indexCount = Math.multiplyExact(residency.faceCount(), 6);
        } catch(ArithmeticException ignored) {
            return cpu;
        }
        if(indexCount <= 0)
            return cpu;

        // GPU section output is quads and therefore uses the renderer's auto-quad
        // index buffer from index zero. Capacity is ensured by the render-thread
        // consumer immediately before it records the substituted command.
        return new DrawCommand(indexCount, 0, residency.vertexOffset(), true);
    }

    private static boolean supported(TerrainRenderType type) {
        return type != null && type != TerrainRenderType.TRANSLUCENT
                && type != TerrainRenderType.TRIPWIRE;
    }

    record DrawCommand(int indexCount, int firstIndex, int vertexOffset,
                       boolean gpuResident) {}
}
