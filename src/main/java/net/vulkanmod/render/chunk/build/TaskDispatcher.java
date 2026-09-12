package net.vulkanmod.render.chunk.build;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.util.thread.ProcessorMailbox;
import net.vulkanmod.render.chunk.*;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TaskDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HIGH_PRIORITY_QUOTA = 2;
    // Queued ChunkTasks are cheap references/snapshots compared with completed
    // UploadBuffers. Keep a useful ready backlog so workers do not starve between
    // render-thread publication passes, while bounding the native mesh results
    // separately below.
    private static final int QUEUED_TASKS_PER_WORKER = 8;
    private static final int COMPLETED_RESULTS_PER_WORKER = 2;

    private final Queue<Runnable> toUpload = Queues.newLinkedBlockingDeque();
    public final ThreadBuilderPack fixedBuffers;

    private volatile boolean stopThreads;
    private Thread[] threads;
    private volatile int idleThreads;
    private volatile int publicationWaiters;
    private int highPriorityQuota = HIGH_PRIORITY_QUOTA;
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicInteger acceptedResults = new AtomicInteger();
    private final AtomicInteger droppedResults = new AtomicInteger();
    private final AtomicInteger completedBuilds = new AtomicInteger();
    private final AtomicInteger publishedBuilds = new AtomicInteger();
    private final AtomicLong buildQueueNanos = new AtomicLong();
    private final AtomicLong buildNanos = new AtomicLong();
    private final AtomicLong handoffNanos = new AtomicLong();
    private final ConcurrentMap<ChunkTask, Long> scheduledAt = new ConcurrentHashMap<>();
    private final Set<UploadBuffer> pendingUploadBuffers = ConcurrentHashMap.newKeySet();
    private final Queue<ChunkTask> highPriorityTasks = Queues.newConcurrentLinkedQueue();
    private final Queue<ChunkTask> lowPriorityTasks = Queues.newConcurrentLinkedQueue();

    public TaskDispatcher() {
        this.fixedBuffers = new ThreadBuilderPack();

        this.stopThreads = true;
    }

    public void createThreads() {
        if(!this.stopThreads)
            return;

        this.stopThreads = false;

        int j = Math.max((Runtime.getRuntime().availableProcessors() - 1) / 2, 1);

        this.threads = new Thread[j];

        for (int i = 0; i < j; i++) {
            ThreadBuilderPack builderPack = new ThreadBuilderPack();
            Thread thread = new Thread(
                    () -> runTaskThread(builderPack));

            this.threads[i] = thread;
            thread.start();
        }
    }

    private void runTaskThread(ThreadBuilderPack builderPack) {
        try {
            this.runTaskLoop(builderPack);
        } finally {
            builderPack.freeAll();
        }
    }

    private void runTaskLoop(ThreadBuilderPack builderPack) {
        while(!this.stopThreads) {
            // Completed UploadBuffers own native mesh copies and are the expensive
            // part of outstanding terrain work. If publication falls behind, pause
            // before starting more builds while leaving the cheap task queue intact.
            if(!this.waitForPublicationCapacity())
                return;

            ChunkTask task = this.pollTask();

            if(task == null) {
                synchronized (this) {
                    // Recheck while holding the same monitor used by schedule(). This
                    // closes the poll-before-wait race where a notification could be
                    // delivered before the worker actually began waiting.
                    task = this.pollTask();
                    if(task == null && !this.stopThreads) {
                        this.idleThreads++;
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        } finally {
                            this.idleThreads--;
                        }
                    }
                }
            }

            if(task == null)
                continue;

            Long scheduledNanos = this.scheduledAt.remove(task);
            long startNanos = System.nanoTime();
            long queueNanos = scheduledNanos == null ? 0L : Math.max(0L, startNanos - scheduledNanos);

            this.activeTasks.incrementAndGet();
            try {
                CompletableFuture<ChunkTask.Result> result = task.doTask(builderPack);
                long elapsedNanos = System.nanoTime() - startNanos;
                if(task instanceof ChunkTask.BuildTask && result != null && result.isDone()
                        && result.getNow(ChunkTask.Result.CANCELLED) == ChunkTask.Result.SUCCESSFUL) {
                    this.completedBuilds.incrementAndGet();
                    this.buildQueueNanos.addAndGet(queueNanos);
                    this.buildNanos.addAndGet(elapsedNanos);
                }
            } catch (Throwable throwable) {
                try {
                    builderPack.discardAll();
                } catch (Throwable cleanupFailure) {
                    throwable.addSuppressed(cleanupFailure);
                }
                Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Batching chunks"));
                return;
            } finally {
                this.activeTasks.decrementAndGet();
            }
        }
    }

    private boolean waitForPublicationCapacity() {
        synchronized (this) {
            int limit = this.getPublicationBacklogLimit();
            if(this.toUpload.size() < limit)
                return !this.stopThreads;

            this.publicationWaiters++;
            try {
                while(!this.stopThreads && this.toUpload.size() >= limit) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            } finally {
                this.publicationWaiters--;
            }
            return !this.stopThreads;
        }
    }

    private int getPublicationBacklogLimit() {
        int workerCount = this.threads == null ? 0 : this.threads.length;
        return Math.max(1, workerCount * COMPLETED_RESULTS_PER_WORKER);
    }

    public void schedule(ChunkTask chunkTask) {
        if(chunkTask == null)
            return;

        this.scheduledAt.put(chunkTask, System.nanoTime());

        if (chunkTask.highPriority) {
                this.highPriorityTasks.offer(chunkTask);
            } else {
                this.lowPriorityTasks.offer(chunkTask);
            }

        synchronized (this) {
            notify();
        }
    }

    @Nullable
    private synchronized ChunkTask pollTask() {
        if(this.highPriorityQuota <= 0) {
            ChunkTask lowPriorityTask = this.lowPriorityTasks.poll();
            if(lowPriorityTask != null) {
                this.highPriorityQuota = HIGH_PRIORITY_QUOTA;
                return lowPriorityTask;
            }
        }

        ChunkTask highPriorityTask = this.highPriorityTasks.poll();
        if(highPriorityTask != null) {
            this.highPriorityQuota--;
            return highPriorityTask;
        }

        this.highPriorityQuota = HIGH_PRIORITY_QUOTA;
        return this.lowPriorityTasks.poll();
    }

    public void stopThreads() {
        if(!this.stopThreads) {
            this.stopThreads = true;

            synchronized (this) {
                notifyAll();
            }

            for (Thread thread : this.threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }

        this.clearBatchQueue();
        this.discardPendingResults();
        this.scheduledAt.clear();
        this.threads = null;
    }

    private void discardPendingResults() {
        this.toUpload.clear();
        this.pendingUploadBuffers.forEach(UploadBuffer::release);
        this.pendingUploadBuffers.clear();
    }

    public boolean uploadAllPendingUploads() {

        Runnable runnable;
        boolean flag = false;
        while((runnable = this.toUpload.poll()) != null) {
            flag = true;
            runnable.run();
        }

        if(flag) {
            synchronized (this) {
                // Wake workers paused only because completed native mesh results
                // were waiting for this render-thread publication pass.
                notifyAll();
            }
        }

        AreaUploadManager.INSTANCE.submitUploads();

        return flag;
    }

    public void scheduleSectionUpdate(ChunkTask task, RenderSection section,
                                      EnumMap<TerrainRenderType, UploadBuffer> uploadBuffers,
                                      Runnable publishResult) {
        long queuedAt = System.nanoTime();
        this.pendingUploadBuffers.addAll(uploadBuffers.values());
        this.toUpload.add(() -> {
            try {
                if(task.cancelled.get()) {
                    this.droppedResults.incrementAndGet();
                    return;
                }

                this.doSectionUpdate(section, uploadBuffers);
                publishResult.run();
                this.acceptedResults.incrementAndGet();
                this.publishedBuilds.incrementAndGet();
                this.handoffNanos.addAndGet(Math.max(0L, System.nanoTime() - queuedAt));
            } finally {
                releaseUploads(uploadBuffers);
                this.pendingUploadBuffers.removeAll(uploadBuffers.values());
            }
        });
    }

    private static void releaseUploads(EnumMap<TerrainRenderType, UploadBuffer> uploadBuffers) {
        uploadBuffers.values().forEach(UploadBuffer::release);
    }

    private void doSectionUpdate(RenderSection section, EnumMap<TerrainRenderType, UploadBuffer> uploadBuffers) {
        ChunkArea renderArea = section.getChunkArea();
        DrawBuffers drawBuffers = renderArea.getDrawBuffers();

        for(TerrainRenderType renderType : TerrainRenderType.VALUES) {
            UploadBuffer uploadBuffer = uploadBuffers.get(renderType);

            if(uploadBuffer != null) {
                drawBuffers.upload(uploadBuffer, section.getDrawParameters(renderType));
            } else {
                section.getDrawParameters(renderType).reset(renderArea);
            }
        }
    }

    public void scheduleUploadChunkLayer(ChunkTask task, RenderSection section,
                                         TerrainRenderType renderType, UploadBuffer uploadBuffer,
                                         Runnable publishResult) {
        this.pendingUploadBuffers.add(uploadBuffer);
        this.toUpload.add(() -> {
            try {
                if(task.cancelled.get()) {
                    this.droppedResults.incrementAndGet();
                    return;
                }

                this.doUploadChunkLayer(section, renderType, uploadBuffer);
                publishResult.run();
                this.acceptedResults.incrementAndGet();
            } finally {
                uploadBuffer.release();
                this.pendingUploadBuffers.remove(uploadBuffer);
            }
        });
    }

    private void doUploadChunkLayer(RenderSection section, TerrainRenderType renderType, UploadBuffer uploadBuffer) {
        ChunkArea renderArea = section.getChunkArea();
        DrawBuffers drawBuffers = renderArea.getDrawBuffers();

        drawBuffers.upload(uploadBuffer, section.getDrawParameters(renderType));
    }

    public int getIdleThreadsCount() {
        return this.idleThreads;
    }

    public int getBuildSchedulingCapacity() {
        int workerCount = this.threads == null ? 0 : this.threads.length;
        if(workerCount == 0)
            return 0;

        // Do not charge completed publication results against scheduling capacity:
        // workers independently stop at the native-result backlog limit. Keeping a
        // deeper cheap task queue prevents the three-worker RX 6900 XT test case
        // from oscillating between active workers and an empty ready queue.
        int queuedAndActive = this.activeTasks.get()
                + this.highPriorityTasks.size() + this.lowPriorityTasks.size();
        return Math.max(0, workerCount * QUEUED_TASKS_PER_WORKER - queuedAndActive);
    }

    public void clearBatchQueue() {
        while(!this.highPriorityTasks.isEmpty()) {
            ChunkTask chunkTask = this.highPriorityTasks.poll();
            if (chunkTask != null) {
                this.scheduledAt.remove(chunkTask);
                chunkTask.cancel();
            }
        }

        while(!this.lowPriorityTasks.isEmpty()) {
            ChunkTask chunkTask = this.lowPriorityTasks.poll();
            if (chunkTask != null) {
                this.scheduledAt.remove(chunkTask);
                chunkTask.cancel();
            }
        }

        synchronized(this) {
            this.highPriorityQuota = HIGH_PRIORITY_QUOTA;
        }

        this.acceptedResults.set(0);
        this.droppedResults.set(0);
        this.completedBuilds.set(0);
        this.publishedBuilds.set(0);
        this.buildQueueNanos.set(0L);
        this.buildNanos.set(0L);
        this.handoffNanos.set(0L);
        UploadBuffer.resetCopyStats();
        if(AreaUploadManager.INSTANCE != null)
            AreaUploadManager.INSTANCE.resetCopyStats();
    }

    private static double averageMillis(long nanos, int samples) {
        return samples == 0 ? 0.0D : (nanos / 1_000_000.0D) / samples;
    }

    public List<String> getDebugLines() {
        int highQueued = this.highPriorityTasks.size();
        int lowQueued = this.lowPriorityTasks.size();
        int buildSamples = this.completedBuilds.get();
        int publishSamples = this.publishedBuilds.get();

        List<String> lines = new ArrayList<>(3);
        lines.add(String.format(Locale.ROOT,
                "Terrain workers: idle %d active %d pubWait %d | queue H/L %d/%d | publish %d/%d",
                this.idleThreads, this.activeTasks.get(), this.publicationWaiters,
                highQueued, lowQueued, this.toUpload.size(), this.getPublicationBacklogLimit()));
        lines.add(String.format(Locale.ROOT,
                "Terrain build: ok/drop %d/%d | ms queue/build/handoff %.1f/%.1f/%.1f | %s",
                this.acceptedResults.get(), this.droppedResults.get(),
                averageMillis(this.buildQueueNanos.get(), buildSamples),
                averageMillis(this.buildNanos.get(), buildSamples),
                averageMillis(this.handoffNanos.get(), publishSamples),
                UploadBuffer.getCopyStats()));
        if(AreaUploadManager.INSTANCE != null)
            lines.add("Terrain upload: " + AreaUploadManager.INSTANCE.getStats());
        if(net.vulkanmod.render.chunk.voxel.RegionVoxelStore.ENABLED)
            lines.add(net.vulkanmod.render.chunk.voxel.RegionVoxelStore.describe());
        return lines;
    }

    public String getStats() {
        return String.join(" | ", this.getDebugLines());
    }

}
