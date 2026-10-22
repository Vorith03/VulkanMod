package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Bounded, opt-in observations of the ACTUAL indirect buffers recorded for drawing.
 * Copies are appended outside the render pass to the same main command buffer;
 * CPU inspection happens at its existing frame-fence retirement, never by waiting.
 * This witnesses selected command consumption, not pixels or presentation time.
 */
public final class TerrainDrawReadback {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.debugTerrainDrawReadback");
    private static final int MAX_REQUESTS = 128;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TOTAL_REQUESTS = 32768;
    private static final long DURATION_NANOS = 30_000_000_000L;
    private static final List<Request> pending = new ArrayList<>();
    private static final List<Batch> outstanding = new ArrayList<>();
    private static Consumer<Observation> ciObserver;
    private static long started, sequence;
    private static int pendingBytes, totalRequests;
    private static boolean invalid;

    private TerrainDrawReadback() {}

    public record Observation(long frame, long recordedNanos, long confirmedNanos,
                              int regionX, int regionY, int regionZ, int layer,
                              boolean gpuCommands, int[] words) {}

    private record Request(long frame, long recorded, int x, int y, int z, int layer,
                           boolean gpu, long source, long offset, int bytes) {}

    private static final class Batch {
        final long buffer, allocation;
        final List<Request> requests;
        boolean finished;
        Batch(long buffer, long allocation, List<Request> requests) {
            this.buffer = buffer; this.allocation = allocation; this.requests = requests;
        }
    }

    /** Called only after vkCmdDrawIndexedIndirect has been recorded. No renderer ownership changes. */
    static void request(ChunkArea area, int layer, long source, long offset, int commands, boolean gpu) {
        if(!ENABLED && ciObserver == null) return;
        long now = System.nanoTime();
        if(started == 0) started = now;
        if(ciObserver == null && now - started > DURATION_NANOS) return;
        long bytes = (long)commands * RegionBatchLayout.STRIDE;
        if(commands <= 0 || commands > RegionBatchLayout.MAX_DRAW_COMMANDS
                || source == 0 || offset < 0 || offset % 4 != 0
                || offset > Long.MAX_VALUE - bytes) {
            invalidate("invalid_draw_range"); return;
        }
        if(pending.size() >= MAX_REQUESTS || bytes > MAX_BYTES - pendingBytes
                || totalRequests >= MAX_TOTAL_REQUESTS) {
            invalidate("bounded_capture_exhausted"); return;
        }
        totalRequests++;
        pending.add(new Request(sequence, now, area.position.x, area.position.y, area.position.z,
                layer, gpu, source, offset, (int)bytes));
        pendingBytes += (int)bytes;
    }

    private static void invalidate(String reason) {
        if(!invalid) Initializer.LOGGER.warn("VULKANMOD_TERRAIN_DRAW_READBACK_INCOMPLETE: {}", reason);
        invalid = true;
    }

    /** Renderer calls this after ending the final render pass and before submitting the frame. */
    public static void recordPending(VkCommandBuffer commandBuffer) {
        if(!ENABLED && ciObserver == null && pending.isEmpty()) return;
        sequence++;
        if(pending.isEmpty()) return;
        List<Request> requests = List.copyOf(pending);
        int bytes = pendingBytes;
        pending.clear(); pendingBytes = 0;
        MemoryManager manager = MemoryManager.getInstance();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            var pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            manager.createBuffer(bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer, pAllocation);
            Batch batch = new Batch(pBuffer.get(0), pAllocation.get(0), requests);
            outstanding.add(batch);
            // Register ownership before recording copies, including partial-recording failures.
            manager.addFrameOp(() -> finish(batch, !invalid));
            int destination = 0;
            for(Request request : requests) {
                var sourceBarrier = VkBufferMemoryBarrier.calloc(1, stack).sType$Default()
                        .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_INDIRECT_COMMAND_READ_BIT
                                | VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(request.source).offset(request.offset).size(request.bytes);
                vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
                                | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_HOST_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, sourceBarrier, null);
                var copy = VkBufferCopy.calloc(1, stack).srcOffset(request.offset)
                        .dstOffset(destination).size(request.bytes);
                vkCmdCopyBuffer(commandBuffer, request.source, batch.buffer, copy);
                destination += request.bytes;
            }
            var hostBarrier = VkBufferMemoryBarrier.calloc(1, stack).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(batch.buffer).offset(0).size(bytes);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_HOST_BIT, 0, null, hostBarrier, null);
        } catch(RuntimeException failure) {
            invalidate("record_failed_" + failure.getClass().getSimpleName());
            if(ciObserver != null) throw failure;
        }
    }

    private static void finish(Batch batch, boolean successful) {
        if(batch.finished) return;
        batch.finished = true;
        outstanding.remove(batch);
        try {
            if(!successful) return;
            int length = batch.requests.stream().mapToInt(Request::bytes).sum();
            MemoryManager.getInstance().MapAndCopy(batch.allocation, length, pointer -> {
                var bytes = pointer.getByteBuffer(0, length).order(ByteOrder.nativeOrder());
                for(Request request : batch.requests) {
                    int[] words = new int[request.bytes / Integer.BYTES];
                    for(int i = 0; i < words.length; i++) words[i] = bytes.getInt();
                    Observation observation = new Observation(request.frame, request.recorded, System.nanoTime(),
                            request.x, request.y, request.z, request.layer, request.gpu, words);
                    if(ciObserver != null) ciObserver.accept(observation);
                    else log(observation);
                }
            });
        } catch(RuntimeException failure) {
            invalidate("inspection_failed_" + failure.getClass().getSimpleName());
            if(ciObserver != null) throw failure;
        } finally {
            // Existing frame fence (or explicit device-idle hook) owns physical destruction.
            MemoryManager.freeBuffer(batch.buffer, batch.allocation);
        }
    }

    private static void log(Observation observation) {
        StringBuilder selected = new StringBuilder();
        for(int i = 0; i < observation.words.length; i += 5) {
            if(observation.words[i] == 0 || observation.words[i + 1] == 0) continue;
            if(selected.length() > 0) selected.append(',');
            selected.append(Integer.toUnsignedString(observation.words[i + 4]));
        }
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_DRAW_CONSUMED: frame={} recorded_ns={} confirmed_ns={} region=({},{},{}) layer={} gpuCommands={} selectedPacked={} incomplete={}; actual indirect bytes after frame fence, NOT pixel/presentation timing",
                observation.frame, observation.recordedNanos, observation.confirmedNanos,
                observation.regionX, observation.regionY, observation.regionZ, observation.layer,
                observation.gpuCommands, selected, invalid);
    }

    /** Submission failure: caller MUST establish device idle before cancelling recorded transfers. */
    public static void cancelAfterDeviceIdle() {
        pending.clear(); pendingBytes = 0;
        for(Batch batch : List.copyOf(outstanding)) finish(batch, false);
    }

    /** Existing native raster oracle owns this explicit test observer, never normal gameplay. */
    static void beginCiProbe(Consumer<Observation> observer) {
        if(ciObserver != null || !pending.isEmpty() || !outstanding.isEmpty())
            throw new IllegalStateException("Indirect readback observer already owns work");
        ciObserver = observer; invalid = false; started = 0; totalRequests = 0;
    }

    /** Fixture has already waited device idle; consume remaining fenced bytes before removing observer. */
    static void endCiProbeAfterDeviceIdle() {
        if(ciObserver == null) return;
        try { for(Batch batch : List.copyOf(outstanding)) finish(batch, !invalid); }
        finally { ciObserver = null; pending.clear(); pendingBytes = 0; }
        if(invalid) throw new AssertionError("Native indirect readback capture was incomplete");
    }
}
