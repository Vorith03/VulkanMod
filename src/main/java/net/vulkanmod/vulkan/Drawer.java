package net.vulkanmod.vulkan;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.memory.*;
import net.vulkanmod.vulkan.util.VUtil;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.InstanceVertexFormat;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;

public class Drawer {
    private static final int INITIAL_VB_SIZE = 2000000;
    private static final int INITIAL_UB_SIZE = 200000;

    private static final LongBuffer buffers = MemoryUtil.memAllocLong(1);
    private static final LongBuffer offsets = MemoryUtil.memAllocLong(1);
    private static final long pBuffers = MemoryUtil.memAddress0(buffers);
    private static final long pOffsets = MemoryUtil.memAddress0(offsets);
    private static boolean nativeStateFreed;

    private int framesNum;
    private VertexBuffer[] vertexBuffers;
    private final AutoIndexBuffer quadsIndexBuffer;
    private final AutoIndexBuffer triangleFanIndexBuffer;
    private final AutoIndexBuffer triangleStripIndexBuffer;
    private UniformBuffers uniformBuffers;

    private int currentFrame;

    public Drawer() {
        //Index buffers
        quadsIndexBuffer = new AutoIndexBuffer(65536, AutoIndexBuffer.DrawType.QUADS);
        triangleFanIndexBuffer = new AutoIndexBuffer(1000, AutoIndexBuffer.DrawType.TRIANGLE_FAN);
        triangleStripIndexBuffer = new AutoIndexBuffer(1000, AutoIndexBuffer.DrawType.TRIANGLE_STRIP);
    }

    public void setCurrentFrame(int currentFrame) {
        this.currentFrame = currentFrame;
    }

    public void createResources(int framesNum) {
        this.framesNum = framesNum;

        if(vertexBuffers != null) {
            Arrays.stream(this.vertexBuffers).iterator().forEachRemaining(
                    Buffer::freeBuffer
            );
        }
        this.vertexBuffers = new VertexBuffer[framesNum];
        for (int i = 0; i < framesNum; ++i) {
            this.vertexBuffers[i] = new VertexBuffer(INITIAL_VB_SIZE, MemoryTypes.HOST_MEM);
        }

        if(this.uniformBuffers != null)
            this.uniformBuffers.free();
        this.uniformBuffers = new UniformBuffers(INITIAL_UB_SIZE);
    }

    public void resetBuffers(int currentFrame) {
        this.vertexBuffers[currentFrame].reset();
        this.uniformBuffers.reset();
    }

    public void draw(ByteBuffer buffer, VertexFormat.Mode mode, VertexFormat vertexFormat, int vertexCount)
    {
        AutoIndexBuffer autoIndexBuffer;
        int indexCount;

        VertexBuffer vertexBuffer = this.vertexBuffers[currentFrame];
        vertexBuffer.copyToVertexBuffer(vertexFormat.getVertexSize(), vertexCount, buffer);

        switch (mode) {
            case QUADS, LINES -> {
                autoIndexBuffer = this.quadsIndexBuffer;
                indexCount = vertexCount * 3 / 2;
            }
            case TRIANGLE_FAN -> {
                autoIndexBuffer = this.triangleFanIndexBuffer;
                indexCount = (vertexCount - 2) * 3;
            }
            case TRIANGLE_STRIP, LINE_STRIP -> {
                autoIndexBuffer = this.triangleStripIndexBuffer;
                indexCount = (vertexCount - 2) * 3;
            }
            case TRIANGLES, DEBUG_LINES, DEBUG_LINE_STRIP -> {
                draw(vertexBuffer, vertexCount);
                return;
            }
            default -> throw new RuntimeException(String.format("unknown drawMode: %s", mode));
        }

        autoIndexBuffer.checkCapacity(vertexCount);

        drawIndexed(vertexBuffer, autoIndexBuffer.getIndexBuffer(), indexCount, autoIndexBuffer.getVkIndexType());
    }

    public AutoIndexBuffer getQuadsIndexBuffer() {
        return quadsIndexBuffer;
    }

    public AutoIndexBuffer getTriangleFanIndexBuffer() {
        return triangleFanIndexBuffer;
    }

    public UniformBuffers getUniformBuffers() { return this.uniformBuffers; }

    public void drawIndexed(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int indexCount) {
        drawIndexed(vertexBuffer, indexBuffer, indexCount, VK_INDEX_TYPE_UINT16);
    }

    public void drawIndexed(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int indexCount, int indexType) {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        VUtil.UNSAFE.putLong(pBuffers, vertexBuffer.getId());
        VUtil.UNSAFE.putLong(pOffsets, vertexBuffer.getOffset());
        nvkCmdBindVertexBuffers(commandBuffer, 0, 1, pBuffers, pOffsets);

        vkCmdBindIndexBuffer(commandBuffer, indexBuffer.getId(), indexBuffer.getOffset(), indexType);
        vkCmdDrawIndexed(commandBuffer, indexCount, 1, 0, 0, 0);
    }

    public void draw(VertexBuffer vertexBuffer, int vertexCount) {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        VUtil.UNSAFE.putLong(pBuffers, vertexBuffer.getId());
        VUtil.UNSAFE.putLong(pOffsets, vertexBuffer.getOffset());
        nvkCmdBindVertexBuffers(commandBuffer, 0, 1, pBuffers, pOffsets);

        vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
    }

    /** Caller owns immutable model indices and fence-safe uploaded buffer slices. */
    public void drawIndexedInstanced(GraphicsPipeline pipeline, VertexBuffer model, VertexBuffer instances,
                                     IndexBuffer indices, int indexType, int vertexCount, int indexCount,
                                     int firstInstance, int instanceCount) {
        RenderSystem.assertOnRenderThread();
        InstanceVertexFormat format = pipeline.getInstanceFormat();
        if(format == null) throw new IllegalArgumentException("Pipeline has no instance binding");
        if(vertexCount < 0 || indexCount < 0)
            throw new IllegalArgumentException("Negative model draw count");
        int indexBytes = switch(indexType) {
            case VK_INDEX_TYPE_UINT16 -> 2;
            case VK_INDEX_TYPE_UINT32 -> 4;
            default -> throw new IllegalArgumentException("Unsupported instance index type");
        };
        validateSlice(model, (long)vertexCount * pipeline.getVertexStride(), 4);
        validateSlice(indices, (long)indexCount * indexBytes, indexBytes);
        validateSlice(instances, 0, 4);
        format.validateRange(instances.getUsedBytes() - instances.getOffset(), firstInstance, instanceCount);
        if(indexCount == 0 || instanceCount == 0) return;
        if(vertexCount == 0) throw new IllegalArgumentException("Nonempty draw without model vertices");
        Renderer renderer = Renderer.getInstance();
        if(!renderer.isRecordingFrame() || renderer.getBoundRenderPass() == null)
            throw new IllegalStateException("Instanced draw requires a recording render pass");
        GraphicsPipeline.requestPrimitiveMode(VertexFormat.Mode.TRIANGLES);
        renderer.bindGraphicsPipeline(pipeline);
        renderer.uploadAndBindUBOs(pipeline);
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(model.getId(), instances.getId()),
                    stack.longs(model.getOffset(), instances.getOffset()));
            vkCmdBindIndexBuffer(commandBuffer, indices.getId(), indices.getOffset(), indexType);
            vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, 0, 0, firstInstance);
        }
    }

    /** Append-only instance snapshot upload. The existing frame-slot fence owns reuse and resize retirement. */
    public void drawIndexedInstanced(GraphicsPipeline pipeline, VertexBuffer model, ByteBuffer data,
                                     IndexBuffer indices, int indexType, int vertexCount, int indexCount,
                                     int firstInstance, int instanceCount) {
        RenderSystem.assertOnRenderThread();
        InstanceVertexFormat format = pipeline.getInstanceFormat();
        if(format == null || !data.isDirect() || data.remaining() % format.stride() != 0
                || data.remaining() > net.vulkanmod.render.instancing.InstanceGroup.MAX_BYTES)
            throw new IllegalArgumentException("Invalid direct instance snapshot");
        if(vertexCount < 0 || indexCount < 0 || (indexType != VK_INDEX_TYPE_UINT16 && indexType != VK_INDEX_TYPE_UINT32))
            throw new IllegalArgumentException("Invalid instance model draw");
        format.validateRange(data.remaining(), firstInstance, instanceCount);
        if(!Renderer.getInstance().isRecordingFrame() || Renderer.getInstance().getBoundRenderPass() == null)
            throw new IllegalStateException("Instance upload requires a recording render pass");
        if(instanceCount == 0 || indexCount == 0) return;
        VertexBuffer uploaded = vertexBuffers[currentFrame];
        // Ordinary model formats can leave the shared cursor at a two-byte alignment.
        int padding = (int)((4 - (uploaded.getUsedBytes() & 3)) & 3);
        if(padding != 0) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                uploaded.copyToVertexBuffer(1, padding, stack.calloc(padding));
            }
        }
        // The existing copy helper normalizes its source cursor; a slice preserves caller position/limit.
        ByteBuffer source = data.slice();
        uploaded.copyToVertexBuffer(format.stride(), source.remaining() / format.stride(), source);
        drawIndexedInstanced(pipeline, model, uploaded, indices, indexType, vertexCount, indexCount,
                firstInstance, instanceCount);
    }

    private static void validateSlice(Buffer buffer, long bytes, int alignment) {
        long offset = buffer.getOffset();
        if(buffer.getId() == 0 || offset < 0 || offset % alignment != 0 || bytes < 0
                || offset > buffer.getUsedBytes() || bytes > buffer.getUsedBytes() - offset
                || buffer.getUsedBytes() > buffer.getBufferSize())
            throw new IllegalArgumentException("Draw exceeds uploaded buffer slice or has an unaligned offset");
    }

    public void bindAutoIndexBuffer(VkCommandBuffer commandBuffer, int drawMode) {
        AutoIndexBuffer autoIndexBuffer;
        switch (drawMode) {
            case 7 -> autoIndexBuffer = this.quadsIndexBuffer;
            case 6 -> autoIndexBuffer = this.triangleFanIndexBuffer;
            case 5 -> autoIndexBuffer = this.triangleStripIndexBuffer;
            default -> throw new RuntimeException("unknown drawType");
        }
        IndexBuffer indexBuffer = autoIndexBuffer.getIndexBuffer();

        vkCmdBindIndexBuffer(commandBuffer, indexBuffer.getId(), indexBuffer.getOffset(), autoIndexBuffer.getVkIndexType());
    }

    public static synchronized void destroyNativeState() {
        if(nativeStateFreed)
            return;
        MemoryUtil.memFree(buffers);
        MemoryUtil.memFree(offsets);
        nativeStateFreed = true;
    }

    public void cleanUpResources() {
        Buffer buffer;
        for (int i = 0; i < framesNum; ++i) {
            buffer = this.vertexBuffers[i];
            MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());

            buffer = this.uniformBuffers.getUniformBuffer(i);
            MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());

        }

        buffer = this.quadsIndexBuffer.getIndexBuffer();
        MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());
        buffer = this.triangleFanIndexBuffer.getIndexBuffer();
        MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());
        buffer = this.triangleStripIndexBuffer.getIndexBuffer();
        MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());
    }

}
