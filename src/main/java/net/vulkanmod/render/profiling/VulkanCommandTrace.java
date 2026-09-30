package net.vulkanmod.render.profiling;

import net.vulkanmod.Initializer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderingInfo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-overhead query-later capture of Vulkan commands recorded by VulkanMod.
 *
 * <p>The JFR flight recorder deliberately stays coarse at the Vulkan boundary: one
 * event per submission/fence/API duration. Emitting a JFR event for every vkCmd*
 * call would allocate metadata and optionally collect stacks in the hottest render
 * path. This companion stream instead writes fixed-width primitive records into
 * preallocated per-thread chunks. A daemon writer drains full chunks to disk; the
 * recording thread never waits for disk I/O. If the bounded pool is exhausted the
 * trace increments an explicit loss counter rather than blocking or silently
 * pretending the capture is complete.</p>
 *
 * <p>Each command-buffer recording gets a monotonically increasing recording id.
 * The SUBMIT record maps that id to the JFR VulkanSubmission sequence through the
 * existing {@link GpuTimestampRecorder#submitted(long, long, long, long)} boundary.
 * This lets offline analysis join command order/work quantities, CPU/JVM samples,
 * submission causality and whole-command-buffer GPU timestamps after the run.</p>
 */
public final class VulkanCommandTrace {
    private static final String ENABLE_PROPERTY = "vulkanmod.performanceProfiler.commandTrace";
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 64;
    public static final int RECORD_BYTES = 64;

    public static final int OP_BEGIN = 1;
    public static final int OP_END = 2;
    public static final int OP_SUBMIT = 3;
    public static final int OP_BIND_PIPELINE = 10;
    public static final int OP_BIND_VERTEX_BUFFER = 11;
    public static final int OP_BIND_INDEX_BUFFER = 12;
    public static final int OP_BIND_DESCRIPTOR_SET = 13;
    public static final int OP_PUSH_CONSTANTS = 14;
    public static final int OP_DRAW = 20;
    public static final int OP_DRAW_INDEXED = 21;
    public static final int OP_DRAW_INDEXED_INDIRECT = 22;
    public static final int OP_DISPATCH = 23;
    public static final int OP_COPY_BUFFER = 30;
    public static final int OP_COPY_BUFFER_TO_IMAGE = 31;
    public static final int OP_COPY_IMAGE_TO_BUFFER = 32;
    public static final int OP_COPY_IMAGE = 33;
    public static final int OP_FILL_BUFFER = 34;
    public static final int OP_BARRIER = 40;
    public static final int OP_BUFFER_BARRIER = 41;
    public static final int OP_IMAGE_BARRIER = 42;
    public static final int OP_BEGIN_RENDER_PASS = 50;
    public static final int OP_END_RENDER_PASS = 51;
    public static final int OP_BEGIN_RENDERING = 52;
    public static final int OP_END_RENDERING = 53;
    public static final int OP_CAPTURE_END = 255;

    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "true"));
    private static final int CHUNK_BYTES = alignedChunkBytes(intProperty(
            "vulkanmod.performanceProfiler.commandTraceChunkKiB", 64, 16, 1024) * 1024);
    private static final int BUFFER_COUNT = intProperty(
            "vulkanmod.performanceProfiler.commandTraceChunks", 256, 16, 4096);

    private static final ConcurrentHashMap<Long, ActiveRecording> ACTIVE = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<ByteBuffer> FREE = new ConcurrentLinkedQueue<>();
    private static final ArrayBlockingQueue<ByteBuffer> PENDING = new ArrayBlockingQueue<>(BUFFER_COUNT);
    private static final ConcurrentLinkedQueue<TraceState> STATES = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<TraceState> STATE = ThreadLocal.withInitial(() -> {
        TraceState state = new TraceState();
        STATES.add(state);
        return state;
    });
    private static final ConcurrentHashMap<Long, ObjectMetadata> PIPELINES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ObjectMetadata> IMAGES = new ConcurrentHashMap<>();

    private static final AtomicLong RECORDING_SEQUENCE = new AtomicLong();
    private static final AtomicLong TOTAL_RECORDS = new AtomicLong();
    private static final AtomicLong DROPPED_RECORDS = new AtomicLong();
    private static final AtomicLong DROPPED_CHUNKS = new AtomicLong();
    private static final AtomicBoolean SHUTDOWN_HOOK = new AtomicBoolean();

    private static volatile boolean capturing;
    private static volatile boolean running;
    private static volatile boolean failed;
    private static volatile FileChannel channel;
    private static volatile Thread writerThread;
    private static volatile Path outputPath;
    private static volatile Path metadataPath;

    private VulkanCommandTrace() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static Path outputPath() {
        return outputPath;
    }

    /** Begin a fresh logical recording of one native VkCommandBuffer handle. */
    public static void begin(VkCommandBuffer commandBuffer, int queueFamily) {
        if(commandBuffer == null || !ensureStarted()) return;
        long handle = commandBuffer.address();
        ActiveRecording active = new ActiveRecording(RECORDING_SEQUENCE.incrementAndGet(), handle);
        ActiveRecording stale = ACTIVE.put(handle, active);
        TraceState state = STATE.get();
        if(stale != null) {
            write(state, OP_END, stale.recordingId, ++stale.ordinal,
                    stale.commandBuffer, -1L, 0L, 0L, 0L, 0L);
        }
        state.lastCommandBuffer = handle;
        state.lastActive = active;
        write(state, OP_BEGIN, active.recordingId, 0,
                handle, queueFamily, Thread.currentThread().getId(), 0L, 0L, 0L);
    }

    public static void end(VkCommandBuffer commandBuffer) {
        if(commandBuffer == null || !capturing) return;
        record(commandBuffer, OP_END, commandBuffer.address(), 0L, 0L, 0L, 0L, 0L);
    }

    /** Map a completed vkQueueSubmit to the JFR submission sequence. */
    public static void submitted(long commandBuffer, long submissionSequence, long queue, long fence) {
        if(!capturing || commandBuffer == 0L) return;
        TraceState state = STATE.get();
        ActiveRecording active = activeFor(state, commandBuffer);
        if(active == null) return;
        write(state, OP_SUBMIT, active.recordingId, ++active.ordinal,
                submissionSequence, queue, fence, commandBuffer, 0L, 0L);
        ACTIVE.remove(commandBuffer, active);
        if(state.lastActive == active) {
            state.lastActive = null;
            state.lastCommandBuffer = 0L;
        }
    }

    public static void bindPipeline(VkCommandBuffer commandBuffer, int bindPoint, long pipeline) {
        record(commandBuffer, OP_BIND_PIPELINE, bindPoint, pipeline, 0L, 0L, 0L, 0L);
    }

    public static void bindVertexBuffersNative(VkCommandBuffer commandBuffer, int firstBinding,
                                               int bindingCount, long pBuffers, long pOffsets) {
        if(!capturing || commandBuffer == null || pBuffers == 0L) return;
        try {
            for(int index = 0; index < bindingCount; ++index) {
                long buffer = MemoryUtil.memGetLong(pBuffers + index * Long.BYTES);
                long offset = pOffsets == 0L ? 0L : MemoryUtil.memGetLong(pOffsets + index * Long.BYTES);
                record(commandBuffer, OP_BIND_VERTEX_BUFFER,
                        firstBinding + index, buffer, offset, 0L, 0L, 0L);
            }
        } catch(RuntimeException failure) {
            DROPPED_RECORDS.incrementAndGet();
        }
    }

    public static void bindVertexBuffers(VkCommandBuffer commandBuffer, int firstBinding,
                                         LongBuffer buffers, LongBuffer offsets) {
        if(!capturing || commandBuffer == null || buffers == null) return;
        try {
            int position = buffers.position();
            int offsetPosition = offsets == null ? 0 : offsets.position();
            int count = buffers.remaining();
            for(int index = 0; index < count; ++index) {
                long offset = offsets == null || index >= offsets.remaining()
                        ? 0L : offsets.get(offsetPosition + index);
                record(commandBuffer, OP_BIND_VERTEX_BUFFER,
                        firstBinding + index, buffers.get(position + index), offset, 0L, 0L, 0L);
            }
        } catch(RuntimeException failure) {
            DROPPED_RECORDS.incrementAndGet();
        }
    }

    public static void bindIndexBuffer(VkCommandBuffer commandBuffer, long buffer,
                                       long offset, int indexType) {
        record(commandBuffer, OP_BIND_INDEX_BUFFER, buffer, offset, indexType, 0L, 0L, 0L);
    }

    public static void bindDescriptorSets(VkCommandBuffer commandBuffer, int bindPoint,
                                          long layout, int firstSet, LongBuffer sets,
                                          java.nio.IntBuffer dynamicOffsets) {
        if(!capturing || commandBuffer == null || sets == null) return;
        try {
            int position = sets.position();
            int count = sets.remaining();
            long dynamic0 = dynamicOffsets == null || !dynamicOffsets.hasRemaining()
                    ? -1L : Integer.toUnsignedLong(dynamicOffsets.get(dynamicOffsets.position()));
            for(int index = 0; index < count; ++index) {
                record(commandBuffer, OP_BIND_DESCRIPTOR_SET,
                        bindPoint, layout, firstSet + index, sets.get(position + index),
                        dynamicOffsets == null ? 0L : dynamicOffsets.remaining(), dynamic0);
            }
        } catch(RuntimeException failure) {
            DROPPED_RECORDS.incrementAndGet();
        }
    }

    public static void pushConstants(VkCommandBuffer commandBuffer, long layout,
                                     int stageFlags, int offset, int size) {
        record(commandBuffer, OP_PUSH_CONSTANTS, layout, stageFlags, offset, size, 0L, 0L);
    }

    public static void draw(VkCommandBuffer commandBuffer, int vertexCount,
                            int instanceCount, int firstVertex, int firstInstance) {
        record(commandBuffer, OP_DRAW,
                vertexCount, instanceCount, firstVertex, firstInstance, 0L, 0L);
    }

    public static void drawIndexed(VkCommandBuffer commandBuffer, int indexCount,
                                   int instanceCount, int firstIndex,
                                   int vertexOffset, int firstInstance) {
        record(commandBuffer, OP_DRAW_INDEXED,
                indexCount, instanceCount, firstIndex, vertexOffset, firstInstance, 0L);
    }

    public static void drawIndexedIndirect(VkCommandBuffer commandBuffer, long buffer,
                                           long offset, int drawCount, int stride) {
        record(commandBuffer, OP_DRAW_INDEXED_INDIRECT,
                buffer, offset, drawCount, stride, 0L, 0L);
    }

    public static void dispatch(VkCommandBuffer commandBuffer, int x, int y, int z) {
        record(commandBuffer, OP_DISPATCH, x, y, z, 0L, 0L, 0L);
    }

    public static void fillBuffer(VkCommandBuffer commandBuffer, long buffer,
                                  long offset, long size, int data) {
        record(commandBuffer, OP_FILL_BUFFER, buffer, offset, size,
                Integer.toUnsignedLong(data), 0L, 0L);
    }

    public static void copyBuffer(VkCommandBuffer commandBuffer, long srcBuffer,
                                  long dstBuffer, VkBufferCopy.Buffer regions) {
        if(!capturing || commandBuffer == null || regions == null) return;
        try {
            for(int index = regions.position(); index < regions.limit(); ++index) {
                VkBufferCopy region = regions.get(index);
                record(commandBuffer, OP_COPY_BUFFER, srcBuffer, dstBuffer,
                        region.srcOffset(), region.dstOffset(), region.size(), 0L);
            }
        } catch(RuntimeException failure) {
            DROPPED_RECORDS.incrementAndGet();
        }
    }

    public static void copyBufferToImage(VkCommandBuffer commandBuffer, long buffer,
                                         long image, int layout,
                                         VkBufferImageCopy.Buffer regions) {
        if(!capturing || commandBuffer == null || regions == null) return;
        try {
            for(int index = regions.position(); index < regions.limit(); ++index) {
                VkBufferImageCopy region = regions.get(index);
                long layoutAndMip = packInts(layout, region.imageSubresource().mipLevel());
                long extent = packInts(region.imageExtent().width(), region.imageExtent().height());
                long xy = packInts(region.imageOffset().x(), region.imageOffset().y());
                record(commandBuffer, OP_COPY_BUFFER_TO_IMAGE,
                        buffer, image, layoutAndMip, region.bufferOffset(), extent, xy);
            }
        } catch(RuntimeException failure) {
            DROPPED_RECORDS.incrementAndGet();
        }
    }

    public static void pipelineBarrier(VkCommandBuffer commandBuffer,
                                       int srcStage, int dstStage, int dependencyFlags,
                                       VkMemoryBarrier.Buffer memoryBarriers,
                                       VkBufferMemoryBarrier.Buffer bufferBarriers,
                                       VkImageMemoryBarrier.Buffer imageBarriers) {
        if(!capturing || commandBuffer == null) return;
        int memoryCount = memoryBarriers == null ? 0 : memoryBarriers.remaining();
        int bufferCount = bufferBarriers == null ? 0 : bufferBarriers.remaining();
        int imageCount = imageBarriers == null ? 0 : imageBarriers.remaining();
        record(commandBuffer, OP_BARRIER, srcStage, dstStage, dependencyFlags,
                memoryCount, bufferCount, imageCount);
        try {
            if(bufferBarriers != null) {
                for(int index = bufferBarriers.position(); index < bufferBarriers.limit(); ++index) {
                    VkBufferMemoryBarrier barrier = bufferBarriers.get(index);
                    record(commandBuffer, OP_BUFFER_BARRIER,
                            srcStage, dstStage, barrier.buffer(), barrier.offset(), barrier.size(),
                            packInts(barrier.srcAccessMask(), barrier.dstAccessMask()));
                }
            }
            if(imageBarriers != null) {
                for(int index = imageBarriers.position(); index < imageBarriers.limit(); ++index) {
                    VkImageMemoryBarrier barrier = imageBarriers.get(index);
                    record(commandBuffer, OP_IMAGE_BARRIER,
                            srcStage, dstStage, barrier.image(),
                            packInts(barrier.oldLayout(), barrier.newLayout()),
                            packInts(barrier.srcAccessMask(), barrier.dstAccessMask()),
                            packInts(barrier.subresourceRange().aspectMask(),
                                    barrier.subresourceRange().levelCount()));
                }
            }
        } catch(RuntimeException failure) {
            DROPPED_RECORDS.incrementAndGet();
        }
    }

    public static void beginRenderPass(VkCommandBuffer commandBuffer, VkRenderPassBeginInfo info) {
        if(info == null) return;
        record(commandBuffer, OP_BEGIN_RENDER_PASS,
                info.renderPass(), info.framebuffer(),
                info.renderArea().extent().width(), info.renderArea().extent().height(),
                info.clearValueCount(), 0L);
    }

    public static void endRenderPass(VkCommandBuffer commandBuffer) {
        record(commandBuffer, OP_END_RENDER_PASS, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public static void beginRendering(VkCommandBuffer commandBuffer, VkRenderingInfo info) {
        if(info == null) return;
        record(commandBuffer, OP_BEGIN_RENDERING,
                info.renderArea().extent().width(), info.renderArea().extent().height(),
                info.colorAttachmentCount(), info.pDepthAttachment() == null ? 0L : 1L,
                info.pStencilAttachment() == null ? 0L : 1L, 0L);
    }

    public static void endRendering(VkCommandBuffer commandBuffer) {
        record(commandBuffer, OP_END_RENDERING, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public static void registerPipeline(long handle, String name, long layout) {
        if(!shouldCollectMetadata() || handle == 0L) return;
        PIPELINES.putIfAbsent(handle, new ObjectMetadata("pipeline", safeName(name), layout, 0L, 0L));
    }

    public static void registerImage(long handle, int width, int height, int format, int mipLevels) {
        if(!shouldCollectMetadata() || handle == 0L) return;
        IMAGES.putIfAbsent(handle, new ObjectMetadata("image", "", packInts(width, height),
                packInts(format, mipLevels), 0L));
    }

    /** Stop after the measured frame has closed; no command producer should be active here. */
    public static synchronized boolean stop(String reason) {
        if(!ENABLED) return true;
        if(!capturing && channel == null) return !failed;
        capturing = false;
        ACTIVE.clear();
        for(TraceState state : STATES) {
            state.lastActive = null;
            state.lastCommandBuffer = 0L;
            publish(state);
        }
        running = false;

        Thread writer = writerThread;
        if(writer != null && writer != Thread.currentThread()) {
            try {
                writer.join(5000L);
                if(writer.isAlive()) {
                    writer.interrupt();
                    writer.join(1000L);
                }
            } catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failed = true;
            }
        }

        FileChannel current = channel;
        if(current != null) {
            try {
                writeDirectRecord(current, OP_CAPTURE_END, 0L, 0,
                        TOTAL_RECORDS.get(), DROPPED_RECORDS.get(), DROPPED_CHUNKS.get(),
                        reason == null ? 0L : Integer.toUnsignedLong(reason.hashCode()), 0L, 0L);
                current.force(false);
            } catch(IOException io) {
                failed = true;
                Initializer.LOGGER.error("VulkanMod command trace could not write its footer", io);
            } finally {
                try {
                    current.close();
                } catch(IOException io) {
                    failed = true;
                }
            }
        }

        writeMetadata();
        if(outputPath != null) {
            Initializer.LOGGER.info(
                    "VulkanMod Vulkan command trace saved: {} (records={}, droppedRecords={}, droppedChunks={}, reason={})",
                    outputPath.toAbsolutePath(), TOTAL_RECORDS.get(), DROPPED_RECORDS.get(),
                    DROPPED_CHUNKS.get(), reason);
        }

        channel = null;
        writerThread = null;
        outputPath = null;
        metadataPath = null;
        FREE.clear();
        PENDING.clear();
        PIPELINES.clear();
        IMAGES.clear();
        return !failed;
    }

    private static void record(VkCommandBuffer commandBuffer, int opcode,
                               long a, long b, long c, long d, long e, long f) {
        if(!capturing || commandBuffer == null) return;
        long handle = commandBuffer.address();
        TraceState state = STATE.get();
        ActiveRecording active = activeFor(state, handle);
        if(active == null) return;
        write(state, opcode, active.recordingId, ++active.ordinal, a, b, c, d, e, f);
    }

    private static ActiveRecording activeFor(TraceState state, long commandBuffer) {
        if(state.lastCommandBuffer == commandBuffer && state.lastActive != null)
            return state.lastActive;
        ActiveRecording active = ACTIVE.get(commandBuffer);
        state.lastCommandBuffer = commandBuffer;
        state.lastActive = active;
        return active;
    }

    private static void write(TraceState state, int opcode, long recordingId, int ordinal,
                              long a, long b, long c, long d, long e, long f) {
        if(!capturing) return;
        ByteBuffer buffer = state.buffer;
        if(buffer == null || buffer.remaining() < RECORD_BYTES) {
            if(buffer != null) publish(state);
            buffer = FREE.poll();
            if(buffer == null) {
                DROPPED_RECORDS.incrementAndGet();
                return;
            }
            state.buffer = buffer;
        }
        buffer.putInt(opcode).putInt(ordinal).putLong(recordingId)
                .putLong(a).putLong(b).putLong(c).putLong(d).putLong(e).putLong(f);
        TOTAL_RECORDS.incrementAndGet();
    }

    private static void publish(TraceState state) {
        ByteBuffer buffer = state.buffer;
        state.buffer = null;
        if(buffer == null) return;
        if(buffer.position() == 0) {
            buffer.clear();
            FREE.offer(buffer);
            return;
        }
        buffer.flip();
        if(!PENDING.offer(buffer)) {
            DROPPED_CHUNKS.incrementAndGet();
            DROPPED_RECORDS.addAndGet(buffer.remaining() / RECORD_BYTES);
            buffer.clear();
            FREE.offer(buffer);
        }
    }

    private static synchronized boolean ensureStarted() {
        if(!ENABLED || failed) return false;
        if(capturing) return true;
        if(!FlightRecorderCapture.isCapturing()) return false;
        try {
            Path trace = resolveOutputPath();
            Path parent = trace.getParent();
            if(parent != null) Files.createDirectories(parent);
            FileChannel next = FileChannel.open(trace,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            writeHeader(next);

            FREE.clear();
            PENDING.clear();
            ACTIVE.clear();
            PIPELINES.clear();
            IMAGES.clear();
            for(int index = 0; index < BUFFER_COUNT; ++index)
                FREE.offer(ByteBuffer.allocate(CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN));
            for(TraceState state : STATES) {
                state.buffer = null;
                state.lastActive = null;
                state.lastCommandBuffer = 0L;
            }
            RECORDING_SEQUENCE.set(0L);
            TOTAL_RECORDS.set(0L);
            DROPPED_RECORDS.set(0L);
            DROPPED_CHUNKS.set(0L);

            outputPath = trace;
            metadataPath = trace.resolveSibling(trace.getFileName().toString() + ".meta.jsonl");
            channel = next;
            running = true;
            capturing = true;
            Thread writer = new Thread(VulkanCommandTrace::writerLoop,
                    "VulkanMod command trace writer");
            writer.setDaemon(true);
            writerThread = writer;
            writer.start();
            installShutdownHook();
            Initializer.LOGGER.info(
                    "VulkanMod Vulkan command trace enabled: {} (chunk={} KiB, buffers={}, boundedMemory={} MiB)",
                    trace.toAbsolutePath(), CHUNK_BYTES / 1024, BUFFER_COUNT,
                    ((long)CHUNK_BYTES * BUFFER_COUNT) / (1024L * 1024L));
            return true;
        } catch(IOException | RuntimeException failure) {
            failed = true;
            capturing = false;
            running = false;
            Initializer.LOGGER.error("VulkanMod could not start Vulkan command tracing", failure);
            return false;
        }
    }

    private static void writerLoop() {
        try {
            while(running || !PENDING.isEmpty()) {
                ByteBuffer buffer = PENDING.poll();
                if(buffer == null) {
                    try {
                        Thread.sleep(2L);
                    } catch(InterruptedException interrupted) {
                        if(!running) break;
                    }
                    continue;
                }
                FileChannel current = channel;
                if(current == null) break;
                writeFully(current, buffer);
                buffer.clear();
                FREE.offer(buffer);
            }
        } catch(IOException | RuntimeException failure) {
            failed = true;
            capturing = false;
            running = false;
            Initializer.LOGGER.error("VulkanMod command trace writer failed", failure);
        }
    }

    private static void writeHeader(FileChannel target) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.put("VMVKCMD1".getBytes(StandardCharsets.US_ASCII));
        header.putInt(VERSION);
        header.putInt(RECORD_BYTES);
        header.putLong(System.nanoTime());
        header.putLong(CHUNK_BYTES);
        header.putLong(BUFFER_COUNT);
        while(header.position() < HEADER_BYTES) header.put((byte)0);
        header.flip();
        writeFully(target, header);
    }

    private static void writeDirectRecord(FileChannel target, int opcode, long recordingId, int ordinal,
                                          long a, long b, long c, long d, long e, long f) throws IOException {
        ByteBuffer record = ByteBuffer.allocate(RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        record.putInt(opcode).putInt(ordinal).putLong(recordingId)
                .putLong(a).putLong(b).putLong(c).putLong(d).putLong(e).putLong(f).flip();
        writeFully(target, record);
    }

    private static void writeFully(FileChannel target, ByteBuffer buffer) throws IOException {
        while(buffer.hasRemaining()) target.write(buffer);
    }

    private static Path resolveOutputPath() {
        Path jfr = FlightRecorderCapture.outputPath();
        if(jfr == null)
            return Path.of("logs", "vulkanmod-performance-command.vkcmd").toAbsolutePath().normalize();
        String name = jfr.getFileName().toString();
        if(name.endsWith(".jfr")) name = name.substring(0, name.length() - 4);
        Path parent = jfr.getParent();
        return (parent == null ? Path.of(name + ".vkcmd") : parent.resolve(name + ".vkcmd"))
                .toAbsolutePath().normalize();
    }

    private static void writeMetadata() {
        Path path = metadataPath;
        if(path == null) return;
        ArrayList<Map.Entry<Long, ObjectMetadata>> entries = new ArrayList<>();
        entries.addAll(PIPELINES.entrySet());
        entries.addAll(IMAGES.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<Long, ObjectMetadata> entry) -> entry.getValue().type)
                .thenComparingLong(Map.Entry::getKey));
        try(BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            for(Map.Entry<Long, ObjectMetadata> entry : entries) {
                ObjectMetadata metadata = entry.getValue();
                writer.write("{\"type\":\"");
                writer.write(jsonEscape(metadata.type));
                writer.write("\",\"handle\":\"0x");
                writer.write(Long.toUnsignedString(entry.getKey(), 16));
                writer.write("\",\"name\":\"");
                writer.write(jsonEscape(metadata.name));
                writer.write("\",\"a\":");
                writer.write(Long.toString(metadata.a));
                writer.write(",\"b\":");
                writer.write(Long.toString(metadata.b));
                writer.write(",\"c\":");
                writer.write(Long.toString(metadata.c));
                writer.write("}\n");
            }
        } catch(IOException failure) {
            failed = true;
            Initializer.LOGGER.error("VulkanMod command trace could not save object metadata", failure);
        }
    }

    private static boolean shouldCollectMetadata() {
        return ENABLED && (capturing || FlightRecorderCapture.isCapturing());
    }

    private static void installShutdownHook() {
        if(!SHUTDOWN_HOOK.compareAndSet(false, true)) return;
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> stop("jvm_shutdown"), "VulkanMod command trace shutdown"));
    }

    private static long packInts(int low, int high) {
        return Integer.toUnsignedLong(low) | (Integer.toUnsignedLong(high) << 32);
    }

    private static String safeName(String value) {
        return value == null ? "" : value;
    }

    private static String jsonEscape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for(int index = 0; index < value.length(); ++index) {
            char c = value.charAt(index);
            switch(c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if(c < 0x20) result.append(String.format("\\u%04x", (int)c));
                    else result.append(c);
                }
            }
        }
        return result.toString();
    }

    private static int alignedChunkBytes(int bytes) {
        int aligned = bytes - bytes % RECORD_BYTES;
        return Math.max(RECORD_BYTES, aligned);
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(System.getProperty(name, Integer.toString(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch(NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class ActiveRecording {
        final long recordingId;
        final long commandBuffer;
        int ordinal;

        ActiveRecording(long recordingId, long commandBuffer) {
            this.recordingId = recordingId;
            this.commandBuffer = commandBuffer;
        }
    }

    private static final class TraceState {
        ByteBuffer buffer;
        long lastCommandBuffer;
        ActiveRecording lastActive;
    }

    private record ObjectMetadata(String type, String name, long a, long b, long c) {
    }
}
