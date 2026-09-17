package net.vulkanmod.render.chunk;

import net.vulkanmod.render.vertex.TerrainRenderType;

/**
 * Generation-safe command planning for GPU-terrain draw handoff.
 *
 * <p>The established {@link Ownership#REPLACE} mode preserves today's fully-qualified
 * section behavior: exact GPU residency replaces the supplied CPU command, while any
 * missing/stale/unsupported result returns the CPU command unchanged. The bounded
 * {@link Ownership#APPEND} mode is groundwork for hybrid sections: when an exact GPU
 * result exists, the CPU exception command remains authoritative and the GPU quad
 * command is added as a second draw. No production caller enables APPEND yet.</p>
 */
final class GpuTerrainDrawHandoff {
    // Region terrain uses the renderer's uint16 auto-quad index buffer. A command's
    // indices are local to its vertexOffset, so one command may address at most
    // 65,536 vertices = 16,384 complete quads without wrapping an index.
    static final int MAX_AUTO_INDEX_FACES = (1 << 16) / GpuTerrainOutputStore.VERTICES_PER_FACE;

    enum Ownership {
        REPLACE,
        APPEND
    }

    private GpuTerrainDrawHandoff() {}

    /** Existing production compatibility entry point: exact GPU output replaces CPU. */
    static DrawCommand select(boolean enabled,
                              TerrainRenderType type,
                              long sectionGeneration,
                              GpuTerrainOutputStore.Residency residency,
                              int cpuIndexCount,
                              int cpuFirstIndex,
                              int cpuVertexOffset) {
        return plan(enabled, type, sectionGeneration, residency,
                cpuIndexCount, cpuFirstIndex, cpuVertexOffset, Ownership.REPLACE)
                .command(0);
    }

    static DrawPlan plan(boolean enabled,
                         TerrainRenderType type,
                         long sectionGeneration,
                         GpuTerrainOutputStore.Residency residency,
                         int cpuIndexCount,
                         int cpuFirstIndex,
                         int cpuVertexOffset,
                         Ownership ownership) {
        if(ownership == null)
            throw new IllegalArgumentException("GPU terrain draw ownership must be present");

        DrawCommand cpu = new DrawCommand(cpuIndexCount, cpuFirstIndex,
                cpuVertexOffset, false);
        DrawCommand gpu = gpuCommand(enabled, type, sectionGeneration, residency);
        if(gpu == null)
            return DrawPlan.cpuOnly(cpu);

        if(ownership == Ownership.REPLACE)
            return DrawPlan.gpuOnly(gpu);

        // Hybrid APPEND preserves the CPU exception command verbatim. A section with
        // no CPU exceptions naturally collapses to the same single GPU command rather
        // than emitting an empty indexed draw.
        if(cpu.indexCount() <= 0)
            return DrawPlan.gpuOnly(gpu);
        return DrawPlan.cpuThenGpu(cpu, gpu);
    }

    private static DrawCommand gpuCommand(boolean enabled,
                                          TerrainRenderType type,
                                          long sectionGeneration,
                                          GpuTerrainOutputStore.Residency residency) {
        if(!enabled || !supported(type) || residency == null
                || !residency.valid() || residency.generation() != sectionGeneration
                || residency.faceCount() <= 0
                || residency.faceCount() > GpuTerrainOutputStore.MAX_FACES
                || residency.faceCount() > MAX_AUTO_INDEX_FACES
                || residency.vertexOffset() < 0)
            return null;

        int indexCount;
        try {
            indexCount = Math.multiplyExact(residency.faceCount(), 6);
        } catch(ArithmeticException ignored) {
            return null;
        }
        if(indexCount <= 0)
            return null;

        // GPU section output is quads and therefore uses the renderer's auto-quad
        // index buffer from index zero. Capacity is ensured by the render-thread
        // consumer immediately before it records the substituted/appended command.
        return new DrawCommand(indexCount, 0, residency.vertexOffset(), true);
    }

    private static boolean supported(TerrainRenderType type) {
        return type != null && type != TerrainRenderType.TRANSLUCENT
                && type != TerrainRenderType.TRIPWIRE;
    }

    record DrawCommand(int indexCount, int firstIndex, int vertexOffset,
                       boolean gpuResident) {}

    static final class DrawPlan {
        private final DrawCommand first;
        private final DrawCommand second;
        private final int commandCount;
        private final int gpuDrawCount;

        private DrawPlan(DrawCommand first, DrawCommand second,
                         int commandCount, int gpuDrawCount) {
            this.first = first;
            this.second = second;
            this.commandCount = commandCount;
            this.gpuDrawCount = gpuDrawCount;
        }

        static DrawPlan cpuOnly(DrawCommand cpu) {
            return new DrawPlan(cpu, null, 1, 0);
        }

        static DrawPlan gpuOnly(DrawCommand gpu) {
            return new DrawPlan(gpu, null, 1, 1);
        }

        static DrawPlan cpuThenGpu(DrawCommand cpu, DrawCommand gpu) {
            return new DrawPlan(cpu, gpu, 2, 1);
        }

        int commandCount() {
            return commandCount;
        }

        int gpuDrawCount() {
            return gpuDrawCount;
        }

        DrawCommand command(int index) {
            if(index == 0)
                return first;
            if(index == 1 && commandCount == 2)
                return second;
            throw new IndexOutOfBoundsException("GPU terrain draw-plan command " + index);
        }
    }
}
