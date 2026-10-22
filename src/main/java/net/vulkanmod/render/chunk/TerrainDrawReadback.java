package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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
    private static final long DURATION_NANOS = 60_000_000_000L;
    private static final List<Request> pending = new ArrayList<>();
    private static final List<Runnable> cpuDraws = new ArrayList<>();
    private static final Map<String, Boolean> consumed = new HashMap<>();
    private static final List<Batch> outstanding = new ArrayList<>();
    private static Consumer<Observation> ciObserver;
    private static final int MAX_SOURCE_KEYS = 4096;
    private static final Map<SourceKey, Revisions> captured = new HashMap<>();
    record Revisions(long candidateGeneration, long visibility, long mesh,
                     long generated, long readiness, int commands) {}
    private record SourceKey(long epoch, int x, int y, int z, int layer,
                             boolean gpu, long source, long offset) {}
    static boolean active() { return ENABLED || ciObserver != null; }
    private static long started, sequence, lastWorldEpoch, cancellation;
    private static int pendingBytes, totalRequests;
    private static boolean invalid;
    private static long referenceEpoch;

    private TerrainDrawReadback() {}

    public record Observation(long worldEpoch, long frame, long recordedNanos, long confirmedNanos,
                              int regionX, int regionY, int regionZ, int layer,
                              boolean gpuCommands, int[] words) {}

    private record Request(long worldEpoch, long frame, long recorded, int x, int y, int z, int layer,
                           boolean gpu, long source, long offset, int bytes, List<TerrainDrawProvenance.Geometry> geometry) {}

    private static final class Batch {
        final long buffer, allocation;
        final List<Request> requests;
        boolean finished;
        Batch(long buffer, long allocation, List<Request> requests) {
            this.buffer = buffer; this.allocation = allocation; this.requests = requests;
        }
    }

    /** Called only after vkCmdDrawIndexedIndirect has been recorded. No renderer ownership changes. */
    static void request(ChunkArea area, int layer, long source, long offset, int commands, boolean gpu, Revisions revisions) {
        if(!ENABLED && ciObserver == null) return;
        long now = System.nanoTime();
        long worldEpoch = TerrainAppearanceEvents.regionEpoch(area);
        if(!TerrainAppearanceEvents.withinWindow(worldEpoch)) return;
        observeEpoch(worldEpoch, now);
        if(ciObserver == null && now - started > DURATION_NANOS) return;
        long bytes = (long)commands * RegionBatchLayout.STRIDE;
        if(commands <= 0 || commands > RegionBatchLayout.MAX_DRAW_COMMANDS
                || source == 0 || offset < 0 || offset % 4 != 0
                || offset > Long.MAX_VALUE - bytes) {
            invalidate("invalid_draw_range"); return;
        }
        SourceKey key = new SourceKey(worldEpoch, area.position.x, area.position.y, area.position.z,
                layer, gpu, source, offset);
        if(revisions.equals(captured.get(key))) return;
        if(!captured.containsKey(key) && captured.size() >= MAX_SOURCE_KEYS) {
            invalidate("source_key_cap_exhausted"); return;
        }
        if(pending.size() >= MAX_REQUESTS || bytes > MAX_BYTES - pendingBytes
                || totalRequests >= MAX_TOTAL_REQUESTS) {
            invalidate("bounded_capture_exhausted"); return;
        }
        captured.put(key, revisions);
        totalRequests++;
        pending.add(new Request(worldEpoch, sequence, now, area.position.x, area.position.y, area.position.z,
                layer, gpu, source, offset, (int)bytes, captureGeometry(area, layer)));
        pendingBytes += (int)bytes;
    }

    /** One late census forces the SAME production sources to be observed again; no geometry preload. */
    static void referenceIfDue(long epoch, long sinceEntry) {
        if(!ENABLED || epoch == 0 || epoch == referenceEpoch || sinceEntry < 55_000_000_000L) return;
        observeEpoch(epoch, System.nanoTime());
        referenceEpoch = epoch; captured.clear(); consumed.clear();
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_REFERENCE_BEGIN: epoch={} observed_ns={}; production command census, convergence must be independently checked", epoch, System.nanoTime());
    }

    private static void observeEpoch(long worldEpoch, long now) {
        if(worldEpoch != 0 && worldEpoch != lastWorldEpoch) {
            lastWorldEpoch = worldEpoch; started = now; totalRequests = 0;
            captured.clear(); consumed.clear();
        }
        if(started == 0) started = now;
    }

    private static List<TerrainDrawProvenance.Geometry> captureGeometry(ChunkArea area, int layer) {
        if(!ENABLED) return List.of(); // Native observer keeps its existing oracle.
        List<TerrainDrawProvenance.Geometry> geometry = new ArrayList<>();
        TerrainRenderType type = TerrainRenderType.VALUES[layer];
        for(int slot = 0; slot < RegionBatchLayout.MAX_SECTIONS; slot++) {
            RenderSection section = area.getOwnedSection(slot);
            if(section == null) continue;
            var cpu = section.getDrawParameters(type);
            boolean append = cpu.indexCount != 0 && type == RenderSection.gpuTerrainOutputLayer()
                    && section.gpuTerrainDrawState().ownership() == GpuTerrainDrawHandoff.Ownership.APPEND;
            if(cpu.indexCount != 0) geometry.add(new TerrainDrawProvenance.Geometry(slot,
                    cpu.indexCount, cpu.firstIndex, cpu.vertexOffset, cpu.traceGeneration,
                    section.getVoxelGeneration(), append ? "cpu_append" : "cpu"));
            var gpu = area.getGpuTerrainOutputResidency(section.xOffset(), section.yOffset(), section.zOffset(), type);
            if(gpu != null && gpu.valid()) geometry.add(new TerrainDrawProvenance.Geometry(slot,
                    gpu.faceCount() * 6, 0, gpu.vertexOffset(), gpu.generation(),
                    section.getVoxelGeneration(), append ? "gpu_append" : "gpu"));
        }
        return List.copyOf(geometry);
    }

    /** Capture numeric provenance BEFORE a direct/legacy draw; enqueue only AFTER recording it. */
    static Runnable captureCpu(RenderSection section, TerrainRenderType type, String path) {
        var ticket = TerrainAppearanceEvents.capture(section);
        var cpu = section.getDrawParameters(type);
        long generation = cpu.traceGeneration, recorded = System.nanoTime(), frame = sequence;
        int x = section.xOffset(), y = section.yOffset(), z = section.zOffset(), layer = type.ordinal();
        return () -> {
            if(ticket == null || !TerrainAppearanceEvents.withinWindow(ticket.epoch())) return;
            long now = System.nanoTime();
            observeEpoch(ticket.epoch(), now);
            if(now - started > DURATION_NANOS) return;
            String key = ticket.epoch() + ":" + x + ":" + y + ":" + z + ":" + layer + ":" + generation + ":" + path;
            if(consumed.containsKey(key)) return;
            if(consumed.size() >= MAX_TOTAL_REQUESTS || cpuDraws.size() >= 4096) {
                invalidate("cpu_geometry_cap_exhausted"); return;
            }
            consumed.put(key, true);
            cpuDraws.add(() -> logGeometry(ticket.epoch(), frame, recorded, System.nanoTime(), x, y, z,
                    layer, generation, ticket.generation(), "cpu", path));
        };
    }

    private static void logGeometry(long epoch, long frame, long recorded, long confirmed,
                                    int x, int y, int z, int layer, long generation,
                                    long inputGeneration, String producer, String path) {
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_GEOMETRY_CONSUMED: epoch={} frame={} section=({},{},{}) layer={} generation={} input_generation={} producer={} path={} recorded_ns={} confirmed_ns={} incomplete={}; actual submitted command, NOT pixels/presentation",
                epoch, frame, x, y, z, layer, generation, inputGeneration, producer, path, recorded, confirmed, invalid);
    }

    private static void invalidate(String reason) {
        if(!invalid) Initializer.LOGGER.warn("VULKANMOD_TERRAIN_DRAW_READBACK_INCOMPLETE: {}", reason);
        invalid = true;
    }

    /** Renderer calls this after ending the final render pass and before submitting the frame. */
    public static void recordPending(VkCommandBuffer commandBuffer) {
        if(!ENABLED && ciObserver == null && pending.isEmpty()) return;
        sequence++;
        if(!cpuDraws.isEmpty()) {
            var draws = List.copyOf(cpuDraws); cpuDraws.clear();
            long token = cancellation;
            MemoryManager.getInstance().addFrameOp(() -> {
                if(!invalid && token == cancellation) draws.forEach(Runnable::run);
            });
        }
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
                // CPU-only diagnostics must not require a compute-capable graphics queue.
                int sourceAccess = VK_ACCESS_INDIRECT_COMMAND_READ_BIT | (request.gpu
                        ? VK_ACCESS_SHADER_WRITE_BIT : VK_ACCESS_HOST_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
                int sourceStages = VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | (request.gpu
                        ? VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT : VK_PIPELINE_STAGE_HOST_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT);
                var sourceBarrier = VkBufferMemoryBarrier.calloc(1, stack).sType$Default()
                        .srcAccessMask(sourceAccess)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(request.source).offset(request.offset).size(request.bytes);
                vkCmdPipelineBarrier(commandBuffer, sourceStages,
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
                    Observation observation = new Observation(request.worldEpoch, request.frame, request.recorded, System.nanoTime(),
                            request.x, request.y, request.z, request.layer, request.gpu, words);
                    if(ciObserver != null) ciObserver.accept(observation);
                    else {
                        log(observation);
                        for(int i = 0; request.worldEpoch != 0 && i < words.length; i += 5) {
                            if(words[i] == 0 || words[i + 1] == 0) continue;
                            var geometry = TerrainDrawProvenance.match(request.geometry, words, i);
                            if(geometry == null) {
                                invalidate("unresolved_geometry_provenance"); continue;
                            }
                            int slot = geometry.slot();
                            int x = request.x + (slot & 7) * 16;
                            int y = request.y + ((slot >>> 3) & 7) * 16;
                            int z = request.z + ((slot >>> 6) & 7) * 16;
                            logGeometry(request.worldEpoch, request.frame, request.recorded, observation.confirmedNanos,
                                    x, y, z, request.layer, geometry.generation(), geometry.inputGeneration(),
                                    geometry.producer(), "region_indirect");
                        }
                    }
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
        Initializer.LOGGER.info("VULKANMOD_TERRAIN_DRAW_CONSUMED: epoch={} frame={} recorded_ns={} confirmed_ns={} region=({},{},{}) layer={} gpuCommands={} selectedPacked={} incomplete={}; actual indirect bytes after frame fence, NOT pixel/presentation timing",
                observation.worldEpoch, observation.frame, observation.recordedNanos, observation.confirmedNanos,
                observation.regionX, observation.regionY, observation.regionZ, observation.layer,
                observation.gpuCommands, selected, invalid);
    }

    /** Submission failure: caller MUST establish device idle before cancelling recorded transfers. */
    public static void cancelAfterDeviceIdle() {
        cancellation++;
        pending.clear(); pendingBytes = 0; captured.clear(); cpuDraws.clear(); consumed.clear();
        for(Batch batch : List.copyOf(outstanding)) finish(batch, false);
    }

    /** Existing native raster oracle owns this explicit test observer, never normal gameplay. */
    static void beginCiProbe(Consumer<Observation> observer) {
        if(ciObserver != null || !pending.isEmpty() || !outstanding.isEmpty())
            throw new IllegalStateException("Indirect readback observer already owns work");
        ciObserver = observer; invalid = false; started = 0; totalRequests = 0; captured.clear(); consumed.clear();
    }

    /** Fixture has already waited device idle; consume remaining fenced bytes before removing observer. */
    static void endCiProbeAfterDeviceIdle() {
        if(ciObserver == null) return;
        try { for(Batch batch : List.copyOf(outstanding)) finish(batch, !invalid); }
        finally { ciObserver = null; pending.clear(); pendingBytes = 0; captured.clear(); cpuDraws.clear(); consumed.clear(); }
        if(invalid) throw new AssertionError("Native indirect readback capture was incomplete");
    }
}
