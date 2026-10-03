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
import java.util.concurrent.atomic.AtomicInteger;

/** Real queue ownership and synchronized worker permits under adaptive admission. */
public final class AdaptiveChunkSchedulingSmokeTest {
    private AdaptiveChunkSchedulingSmokeTest() {}

    public static void verify() {
        var config = Initializer.CONFIG;
        boolean oldEnabled = config.adaptiveChunkScheduling;
        int oldWorkers = config.chunkWorkerThreads, oldCount = config.chunkPublicationsPerFrame;
        double oldBudget = config.chunkPublicationBudgetMs, oldTarget = config.chunkTargetFrameMs;
        TaskDispatcher dispatcher = new TaskDispatcher();
        ChunkArea area = new ChunkArea(0, new Vector3i());
        RenderSection section = new RenderSection(0, 0, 0, 0);
        section.setChunkArea(area);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        try {
            config.adaptiveChunkScheduling = true;
            config.chunkWorkerThreads = 2;
            config.chunkPublicationsPerFrame = 1;
            config.chunkPublicationBudgetMs = 20;
            config.chunkTargetFrameMs = 4;
            AtomicInteger publications = new AtomicInteger();
            dispatcher.scheduleSectionUpdate(new ChunkTask(section, dispatcher), section,
                    new EnumMap<>(TerrainRenderType.class), publications::incrementAndGet);
            ChunkTask cancelled = new ChunkTask(section, dispatcher);
            cancelled.cancel();
            dispatcher.scheduleSectionUpdate(cancelled, section, new EnumMap<>(TerrainRenderType.class),
                    () -> { throw new AssertionError("Cancelled adaptive publication escaped"); });
            dispatcher.scheduleSectionUpdate(new ChunkTask(section, dispatcher), section,
                    new EnumMap<>(TerrainRenderType.class), publications::incrementAndGet);
            ChunkFrameTiming.begin();
            if(!dispatcher.uploadAllPendingUploads() || publications.get() != 1)
                throw new AssertionError("First bounded publication failed");
            if(dispatcher.uploadAllPendingUploads())
                throw new AssertionError("Repeated view reset publication budget");
            ChunkFrameTiming.begin();
            if(!dispatcher.uploadAllPendingUploads() || publications.get() != 1)
                throw new AssertionError("Cancelled result must consume and release a slot");
            ChunkFrameTiming.begin();
            if(!dispatcher.uploadAllPendingUploads() || publications.get() != 2)
                throw new AssertionError("Next frame did not resume publication");

            AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(2);
            for(int i=0; i<2; i++) dispatcher.schedule(new ChunkTask(section, dispatcher) {
                @Override public CompletableFuture<Result> doTask(ThreadBuilderPack pack) {
                    peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                    started.countDown();
                    try {
                        if(!releaseWorkers.await(2, TimeUnit.SECONDS))
                            throw new AssertionError("Adaptive worker test timed out");
                        return CompletableFuture.completedFuture(Result.SUCCESSFUL);
                    } catch(InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    } finally { running.decrementAndGet(); finished.countDown(); }
                }
            });
            dispatcher.createThreads();
            if(!started.await(2, TimeUnit.SECONDS)) throw new AssertionError("No adaptive worker started");
            releaseWorkers.countDown();
            if(!finished.await(2, TimeUnit.SECONDS) || peak.get() != 1)
                throw new AssertionError("Long-frame worker permits were exceeded or never released");
            dispatcher.scheduleSectionUpdate(new ChunkTask(section, dispatcher), section,
                    new EnumMap<>(TerrainRenderType.class),
                    () -> { throw new AssertionError("Shutdown published a deferred result"); });
            dispatcher.stopThreads();
            ChunkFrameTiming.begin();
            if(dispatcher.uploadAllPendingUploads()) throw new AssertionError("Shutdown retained a result");
            Initializer.LOGGER.info("Adaptive chunk scheduling smoke passed");
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } finally {
            releaseWorkers.countDown();
            dispatcher.stopThreads();
            dispatcher.fixedBuffers.freeAll();
            area.releaseBuffers();
            config.adaptiveChunkScheduling = oldEnabled;
            config.chunkWorkerThreads = oldWorkers;
            config.chunkPublicationsPerFrame = oldCount;
            config.chunkPublicationBudgetMs = oldBudget;
            config.chunkTargetFrameMs = oldTarget;
        }
    }
}
