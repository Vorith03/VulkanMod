package net.vulkanmod.render.chunk.build;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Vector3i;

import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real completed-result queue and worker backpressure, without a world/mesh build. */
public final class PublicationDrainSmokeTest {
    private PublicationDrainSmokeTest() {}

    public static void verify() {
        TaskDispatcher dispatcher = new TaskDispatcher();
        ChunkArea area = new ChunkArea(0, new Vector3i());
        RenderSection section = new RenderSection(0, 0, 0, 0);
        section.setChunkArea(area);
        CountDownLatch workerResumed = new CountDownLatch(1);
        try {
            int workerCount = Math.max((Runtime.getRuntime().availableProcessors() - 1) / 2, 1);
            // Fill the real result queue before starting workers. Cancelled results
            // must free backlog capacity too, without invoking their callbacks.
            for(int i = 0; i < workerCount * 2; i++) {
                ChunkTask cancelled = new ChunkTask(section);
                cancelled.cancel();
                dispatcher.scheduleSectionUpdate(cancelled, section, new EnumMap<>(TerrainRenderType.class),
                        () -> { throw new AssertionError("Cancelled result published"); });
            }
            dispatcher.scheduleSectionUpdate(new ChunkTask(section), section, new EnumMap<>(TerrainRenderType.class),
                    () -> {
                        try {
                            if(!workerResumed.await(2, TimeUnit.SECONDS))
                                throw new AssertionError("Worker remained blocked until end of result drain");
                        } catch(InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    });
            dispatcher.schedule(new ChunkTask(section) {
                @Override
                public CompletableFuture<Result> doTask(ThreadBuilderPack pack) {
                    workerResumed.countDown();
                    return CompletableFuture.completedFuture(Result.SUCCESSFUL);
                }
            });
            dispatcher.createThreads();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while(dispatcher.getPublicationWaitersCount() == 0 && System.nanoTime() - deadline < 0)
                Thread.yield();
            if(dispatcher.getPublicationWaitersCount() == 0)
                throw new AssertionError("Test worker never reached publication backpressure");
            if(workerResumed.getCount() != 1)
                throw new AssertionError("Worker escaped the full-result-queue guard");
            if(!dispatcher.uploadAllPendingUploads())
                throw new AssertionError("Nonempty drain must report publication activity");
            if(dispatcher.uploadAllPendingUploads())
                throw new AssertionError("Empty drain must report no publication activity");
            if(dispatcher.getDebugLines().stream().noneMatch(line -> line.endsWith("early wakes 1")))
                throw new AssertionError("Drain must record exactly one early wakeup");
            Initializer.LOGGER.info("Terrain publication drain smoke passed");
        } finally {
            dispatcher.stopThreads();
            dispatcher.fixedBuffers.freeAll();
            area.releaseBuffers();
        }
    }
}
