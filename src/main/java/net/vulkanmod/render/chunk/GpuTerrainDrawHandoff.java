package net.vulkanmod.render.chunk;

import net.vulkanmod.render.vertex.TerrainRenderType;

/**
 * Fail-closed command selection for the first production GPU-terrain draw handoff.
 *
 * <p>This helper deliberately does not mutate CPU {@link DrawBuffers.DrawParameters}.
 * Callers build the normal CPU command first and may substitute the returned GPU
 * command only when every generation/layer/residency check succeeds. Keeping this
 * policy separate from {@link RegionDrawBatch} makes stale/missing/unsupported
 * output mechanically fall back to the already-proven CPU geometry.</p>
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
        if(!enabled || cpuIndexCount <= 0 || !supported(type) || residency == null
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
