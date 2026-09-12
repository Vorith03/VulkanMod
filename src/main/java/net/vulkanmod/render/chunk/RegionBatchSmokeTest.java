package net.vulkanmod.render.chunk;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

/** Exercise the real cache and mapped command buffers during opt-in CI startup. */
public final class RegionBatchSmokeTest {
    private RegionBatchSmokeTest() {}

    public static void verify() {
        ChunkArea area = new ChunkArea(0, new Vector3i(-128, -128, 128));
        DrawBuffers buffers = area.drawBuffers;
        var first = new RegionDrawBatch.FrameBatch();
        var second = new RegionDrawBatch.FrameBatch();
        AreaBuffer persistentBuffer = null;
        try {
            require(!TerrainShaderManager.useRegionBatching(RenderType.translucent()), "Water must retain its renderer");
            require(!TerrainShaderManager.useRegionBatching(RenderType.tripwire()), "Tripwire must retain its renderer");
            require(new ChunkAreaManager(1, 24, -64).ySize == 3, "Overworld area rows must match section count");
            require(new ChunkAreaManager(1, 16, 0).ySize == 2, "Zero-based area rows must match section count");

            // Exercise the production region suballocator with a real GPU buffer.
            // A rebuild that still fits its old reservation must keep the same
            // address instead of returning the slice to the free list. A larger
            // rebuild still takes the established growth/relocation path.
            persistentBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 256, Integer.BYTES);
            AreaBuffer.Segment persistentSegment = new AreaBuffer.Segment();
            try(MemoryStack stack = MemoryStack.stackPush()) {
                persistentBuffer.upload(stack.malloc(64), persistentSegment);
                AreaUploadManager.INSTANCE.waitAllUploads();
                int originalOffset = persistentSegment.getOffset();
                int reservedBytes = persistentBuffer.getUsedBytes();

                persistentBuffer.upload(stack.malloc(32), persistentSegment);
                AreaUploadManager.INSTANCE.waitAllUploads();
                require(persistentSegment.getOffset() == originalOffset,
                        "Smaller terrain rebuild must reuse its persistent GPU slice");
                require(persistentBuffer.getUsedBytes() == reservedBytes,
                        "In-place rebuild must preserve reserved-byte accounting");

                persistentBuffer.upload(stack.malloc(320), persistentSegment);
                AreaUploadManager.INSTANCE.waitAllUploads();
                require(persistentSegment.getOffset() != originalOffset,
                        "Oversized terrain rebuild must relocate to a larger reservation");
                require(persistentBuffer.getCapacityBytes() > 256,
                        "Oversized terrain rebuild must retain the buffer growth fallback");
            }

            RenderSection section = new RenderSection(0, -16, -96, 176);
            var parameters = section.getDrawParameters(TerrainRenderType.CUTOUT_MIPPED);
            parameters.indexCount = 6;
            parameters.vertexOffset = 24;
            parameters.vertexBufferSegment.setPending();
            area.addSection(section);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Initial cache build");
            require(first.drawCount == 0 && first.pendingUploads, "Pending vertices must not draw");
            parameters.vertexBufferSegment.setReady();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Upload completion must retry");
            require(first.drawCount == 1 && !first.pendingUploads, "Ready geometry must draw");
            require(first.commands.getByteBuffer().getInt(16) == (7 | (2 << 3) | (3 << 6)), "GPU section coordinates");
            require(!first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Unchanged cache must not upload");

            area.resetQueue();
            area.addSection(section);
            require(!first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED),
                    "Equivalent visibility rebuild must preserve cached commands");

            second.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED);
            require(first.commands.getId() != second.commands.getId(), "Frames must own distinct buffers");
            parameters.indexCount = 12;
            buffers.meshRevision++;
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Mesh edit invalidation");
            require(first.commands.getByteBuffer().getInt(0) == 12, "Edited count");
            require(second.commands.getByteBuffer().getInt(0) == 6, "Other frame must remain untouched");

            area.resetQueue();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 0,
                    "Visibility removal must clear draws");
            area.resetQueue();
            area.addSection(section);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 1,
                    "Visibility restoration must rebuild draws");

            parameters.reset(area);
            require(!parameters.ready, "Reset parameters must not retain upload readiness");
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 0,
                    "Section reset must invalidate cached geometry");
            Initializer.LOGGER.info("Terrain region cache smoke test passed");
        } finally {
            if (persistentBuffer != null) persistentBuffer.freeBuffer();
            if (first.commands != null) first.commands.freeBuffer();
            if (second.commands != null) second.commands.freeBuffer();
            RegionBatchStats.reset();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
