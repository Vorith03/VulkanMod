package net.vulkanmod.render.chunk;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static net.vulkanmod.render.chunk.RegionBatchLayout.*;
import static org.lwjgl.vulkan.VK10.*;

/** Opaque commands survive camera movement; each frame owns its writable copy. */
final class RegionDrawBatch {
    private FrameBatch[][] batches;

    void draw(DrawBuffers buffers, ChunkArea area, Pipeline pipeline, RenderType renderType,
              double camX, double camY, double camZ) {
        int frames = Renderer.getFramesNum();
        if (batches == null || batches[0].length != frames) {
            free();
            batches = new FrameBatch[TerrainRenderType.VALUES.length][frames];
        }
        TerrainRenderType type = TerrainRenderType.get(renderType);
        int frame = Renderer.getCurrentFrame();
        FrameBatch batch = batches[type.ordinal()][frame];
        if (batch == null) batches[type.ordinal()][frame] = batch = new FrameBatch();

        // Renderer.beginFrame has waited this frame's fence. Other frames' command
        // buffers remain untouched, even during edits or visibility changes.
        batch.update(buffers, area, type);
        if (batch.drawCount == 0) return;
        RegionBatchStats.sections += batch.drawCount;

        type.setCutoutUniform();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var commandBuffer = Renderer.getCommandBuffer();
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(buffers.vertexBuffer.getId()), stack.longs(0));
            // Subtract in double precision before conversion, preserving precision
            // in distant and negative-coordinate regions.
            vkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0,
                    stack.floats((float) (area.position.x - camX), (float) (area.position.y - camY),
                            (float) (area.position.z - camZ)));
            pipeline.bindDescriptorSets(commandBuffer, frame);
            int limit = drawLimit(Device.deviceProperties.limits().maxDrawIndirectCount());
            for (int first = 0; first < batch.drawCount; first += limit) {
                RegionBatchStats.calls++;
                vkCmdDrawIndexedIndirect(commandBuffer, batch.commands.getId(), (long) first * STRIDE,
                        Math.min(limit, batch.drawCount - first), STRIDE);
            }
        }
    }

    void free() {
        if (batches == null) return;
        for (FrameBatch[] layer : batches) {
            for (FrameBatch batch : layer) {
                if (batch != null && batch.commands != null) batch.commands.freeBuffer();
            }
        }
        batches = null;
    }

    static final class FrameBatch {
        RegionCommands commands;
        long visibilityRevision = -1;
        long meshRevision = -1;
        boolean pendingUploads;
        int drawCount;

        boolean update(DrawBuffers buffers, ChunkArea area, TerrainRenderType type) {
            long currentVisibilityRevision = area.getVisibilityRevision();
            if (visibilityRevision == currentVisibilityRevision && meshRevision == buffers.meshRevision
                    && !pendingUploads) return false;
            rebuild(buffers, area, type, currentVisibilityRevision);
            return true;
        }

        void rebuild(DrawBuffers buffers, ChunkArea area, TerrainRenderType type, long currentVisibilityRevision) {
            if (area.sectionQueue.size() > MAX_SECTIONS) {
                throw new IllegalStateException("Region contains more than 512 sections");
            }
            pendingUploads = false;
            drawCount = 0;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.malloc(area.sectionQueue.size() * STRIDE);
                var iterator = area.sectionQueue.iterator(false);
                while (iterator.hasNext()) {
                    RenderSection section = iterator.next();
                    DrawBuffers.DrawParameters parameters = section.getDrawParameters(type);
                    if (parameters.indexCount == 0) continue;
                    // Do not cache the absence of a new upload indefinitely. Retry
                    // until AreaUploadManager has observed its completion fence.
                    if (!parameters.vertexBufferSegment.isReady()) {
                        pendingUploads = true;
                        continue;
                    }
                    putCommand(data, parameters.indexCount, parameters.firstIndex, parameters.vertexOffset,
                            packSection(section.xOffset - area.position.x, section.yOffset - area.position.y,
                                    section.zOffset - area.position.z));
                    drawCount++;
                }
                if (drawCount != 0) {
                    if (commands == null) commands = new RegionCommands();
                    data.flip();
                    commands.reset();
                    commands.recordCopyCmd(data);
                    RegionBatchStats.uploads++;
                    RegionBatchStats.bytes += drawCount * STRIDE;
                }
            }
            visibilityRevision = currentVisibilityRevision;
            meshRevision = buffers.meshRevision;
        }
    }

    static final class RegionCommands extends IndirectBuffer {
        RegionCommands() {
            super(MAX_SECTIONS * STRIDE, MemoryTypes.HOST_MEM);
        }
    }
}
