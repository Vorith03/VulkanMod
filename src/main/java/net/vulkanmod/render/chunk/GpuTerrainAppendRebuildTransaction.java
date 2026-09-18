package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.render.vertex.TerrainRenderType;

/**
 * Render-thread coordinator for one future-generation mixed APPEND replacement.
 *
 * <p>Both halves remain non-visible while they are staged. commit() first validates
 * every section/generation/ownership invariant; after that point its mutations are
 * deliberately no-fail render-thread operations. No frame recording can interleave
 * the GPU residency switch, CPU draw-parameter swap, and final visible-generation
 * handoff.</p>
 */
final class GpuTerrainAppendRebuildTransaction {
    private final RenderSection section;
    private final long generation;
    private final DrawBuffers drawBuffers;
    private final DrawBuffers.StagedDrawParameters cpu;
    private final GpuTerrainOutputStore.StagedReservation gpu;
    private boolean finished;

    GpuTerrainAppendRebuildTransaction(RenderSection section, long generation,
                                       DrawBuffers drawBuffers,
                                       DrawBuffers.StagedDrawParameters cpu,
                                       GpuTerrainOutputStore.StagedReservation gpu) {
        if(section == null || drawBuffers == null || cpu == null || gpu == null)
            throw new IllegalArgumentException("GPU APPEND rebuild transaction requires both staged halves");
        this.section = section;
        this.generation = generation;
        this.drawBuffers = drawBuffers;
        this.cpu = cpu;
        this.gpu = gpu;
    }

    boolean ready() {
        if(finished)
            return false;
        TerrainRenderType type = gpu.type();
        return generation >= 0L
                && section.getVoxelGeneration() == generation
                && section.getChunkArea() != null
                && section.getChunkArea().drawBuffers == drawBuffers
                && cpu.section == section
                && cpu.generation() == generation
                && cpu.renderType == type
                && type == RenderSection.gpuTerrainOutputLayer()
                && gpu.generation() == generation
                && cpu.ready()
                && gpu.ready()
                && gpu.canCommit()
                && section.canCommitGpuTerrainAppendRebuild(generation, gpu.faceCapacity())
                && drawBuffers.canCommitStaged(section.getDrawParameters(type), cpu);
    }

    boolean commit() {
        RenderSystem.assertOnRenderThread();
        if(!ready())
            return false;

        TerrainRenderType type = gpu.type();
        DrawBuffers.DrawParameters target = section.getDrawParameters(type);

        // Every expected stale/failure condition is rejected by ready(). From here
        // there must be no recoverable branch: rendering cannot interleave this
        // render-thread sequence, so the three ownership stores become visible as
        // one transaction at the next batch recording point.
        if(!gpu.commit())
            throw new IllegalStateException("Prevalidated staged GPU terrain commit failed");
        if(!drawBuffers.commitStaged(target, cpu))
            throw new IllegalStateException("Prevalidated staged CPU terrain commit failed");
        section.commitGpuTerrainAppendRebuildHandoff(generation, gpu.faceCapacity());

        finished = true;
        return true;
    }

    void discard() {
        RenderSystem.assertOnRenderThread();
        if(finished)
            return;
        drawBuffers.discardStaged(cpu);
        gpu.discard();
        finished = true;
    }
}
