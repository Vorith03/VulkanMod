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

    private final Queue<Runnable> toUpload = Queues.newLinkedBlockingDeque();
    public final ThreadBuilderPack fixedBuffers;

    private volatile boolean stopThreads;
    private Thread[] threads;
    private volatile int idleThreads;
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

        int queuedTasks = this.highPriorityTasks.size() + this.lowPriorityTasks.size();
        int outstandingTasks = this.activeTasks.get() + queuedTasks + this.toUpload.size();
        return Math.max(0, workerCount * 2 - outstandingTasks);
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
    }

    private static double averageMillis(long nanos, int samples) {
        return samples == 0 ? 0.0D : (nanos / 1_000_000.0D) / samples;
    }

    public String getStats() {
        int highQueued = this.highPriorityTasks.size();
        int lowQueued = this.lowPriorityTasks.size();
        int buildSamples = this.completedBuilds.get();
        int publishSamples = this.publishedBuilds.get();
        String stats = String.format(Locale.ROOT,
                "iT:%d aT:%d qH:%d qL:%d uQ:%d okR:%d dropR:%d lat(q/b/h):%.1f/%.1f/%.1fms",
                this.idleThreads, this.activeTasks.get(), highQueued, lowQueued, this.toUpload.size(),
                this.acceptedResults.get(), this.droppedResults.get(),
                averageMillis(this.buildQueueNanos.get(), buildSamples),
                averageMillis(this.buildNanos.get(), buildSamples),
                averageMillis(this.handoffNanos.get(), publishSamples));

        if(AreaUploadManager.INSTANCE != null)
            stats += " " + AreaUploadManager.INSTANCE.getStats();
        return stats;
    }

}
