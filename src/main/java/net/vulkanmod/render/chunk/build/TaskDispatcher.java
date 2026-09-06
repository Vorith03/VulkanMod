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
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();

    private int highPriorityQuota = 2;

    private final Queue<Runnable> toUpload = Queues.newLinkedBlockingDeque();
    public final ThreadBuilderPack fixedBuffers;

    private volatile boolean stopThreads;
    private Thread[] threads;
    private volatile int idleThreads;
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicInteger acceptedResults = new AtomicInteger();
    private final AtomicInteger droppedResults = new AtomicInteger();
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

            this.activeTasks.incrementAndGet();
            try {
                task.doTask(builderPack);
            } finally {
                this.activeTasks.decrementAndGet();
            }
        }
    }

    public void schedule(ChunkTask chunkTask) {
        if(chunkTask == null)
            return;

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
    private ChunkTask pollTask() {
        ChunkTask task = this.highPriorityTasks.poll();

        if(task == null)
            task = this.lowPriorityTasks.poll();

        return task;
    }

    public void stopThreads() {
        if(this.stopThreads)
            return;

        this.stopThreads = true;

        synchronized (this) {
            notifyAll();
        }

        for (Thread thread : this.threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

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
        this.toUpload.add(() -> {
            if(task.cancelled.get()) {
                releaseUploads(uploadBuffers);
                this.droppedResults.incrementAndGet();
                return;
            }

            this.doSectionUpdate(section, uploadBuffers);
            publishResult.run();
            this.acceptedResults.incrementAndGet();
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
        this.toUpload.add(() -> {
            if(task.cancelled.get()) {
                uploadBuffer.release();
                this.droppedResults.incrementAndGet();
                return;
            }

            this.doUploadChunkLayer(section, renderType, uploadBuffer);
            publishResult.run();
            this.acceptedResults.incrementAndGet();
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
        int outstandingTasks = this.activeTasks.get() + queuedTasks;
        return Math.max(0, workerCount * 2 - outstandingTasks);
    }

    public void clearBatchQueue() {
        while(!this.highPriorityTasks.isEmpty()) {
            ChunkTask chunkTask = this.highPriorityTasks.poll();
            if (chunkTask != null) {
                chunkTask.cancel();
            }
        }

        while(!this.lowPriorityTasks.isEmpty()) {
            ChunkTask chunkTask = this.lowPriorityTasks.poll();
            if (chunkTask != null) {
                chunkTask.cancel();
            }
        }

        this.acceptedResults.set(0);
        this.droppedResults.set(0);
    }

    public String getStats() {
        int queuedTasks = this.highPriorityTasks.size() + this.lowPriorityTasks.size();
        return String.format("iT:%d aT:%d qT:%d uQ:%d okR:%d dropR:%d",
                this.idleThreads, this.activeTasks.get(), queuedTasks, this.toUpload.size(),
                this.acceptedResults.get(), this.droppedResults.get());
    }

}
